package com.smartapply.ida.module.plugin.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.common.domain.auth.vo.SmsLoginResultVO;
import com.smartapply.ida.common.domain.user.UserEntity;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.props.auth.AuthSessionProperties;
import com.smartapply.ida.config.props.http.HttpIdentityProperties;
import com.smartapply.ida.module.app.user.mapper.UserMapper;
import com.smartapply.ida.module.plugin.auth.service.LoginSessionService;
import com.smartapply.ida.module.plugin.auth.service.SmsVerificationService;
import com.smartapply.ida.module.plugin.auth.service.UserAuthService;
import com.smartapply.ida.security.crypto.phone.PhoneCrypto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 短信登录实现：发送阶段捕获非业务异常并统一提示；登录阶段在单事务内完成「验码 → 查/注册用户 → 发 Token」。
 */
@Slf4j
@Service("pluginUserAuthServiceImpl")
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final SmsVerificationService smsVerificationService;
    private final UserMapper userMapper;
    private final PhoneCrypto phoneCrypto;
    private final LoginSessionService loginSessionService;
    private final AuthSessionProperties authSessionProperties;
    private final HttpIdentityProperties httpIdentityProperties;

    @Override
    public void sendLoginCode(String phone) {
        try {
            smsVerificationService.sendCode(phone);
        } catch (BusinessException e) {
            throw e; // 频控等业务提示原样返回给前端
        } catch (Exception e) {
            // 阿里云/网络等：不向外抛栈信息，统一兜底文案
            log.warn("发送验证码异常", e);
            throw new BusinessException("短信发送失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmsLoginResultVO loginBySms(String phone, String code) {
        // 验证码校验成功会删除 Redis 中的码，防止重放
        if (!smsVerificationService.verifyAndConsume(phone, code)) {
            throw new BusinessException("验证码错误或已过期");
        }
        String normalized = phone.trim();
        // 库中 phone 与查询条件必须使用同一套加密（或明文），否则永远查不到用户
        String encPhone = phoneCrypto.encrypt(normalized);
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getPhone, encPhone));
        boolean isNew = false;
        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            // 首次登录：插入用户，phone 存密文（或 NoOp 明文，取决于 ida.crypto.aes-field）
            user = new UserEntity();
            user.setPhone(encPhone);
            // 盲索引：后 4 位明文，供管理端在密文之上做尾号检索
            user.setPhoneTail(tail4(normalized));
            user.setStatus(1);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            userMapper.insert(user);
            isNew = true;
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException("账号已禁用");
            }
            // 存量用户懒回填尾号盲索引
            if (!StringUtils.hasText(user.getPhoneTail())) {
                user.setPhoneTail(tail4(normalized));
            }
            // 已存在用户：轻量 touch updatedAt，便于运营按活跃排序（可按需去掉）
            user.setUpdatedAt(now);
            userMapper.updateById(user);
        }
        // 登录成功即发新 token；旧 token 未在此撤销，多设备并存直到各自 TTL（若需单点可在此 revoke 其它 key）
        String token = loginSessionService.issueToken(user.getId());
        return SmsLoginResultVO.builder()
                .token(token)
                .userId(user.getId())
                .newUser(isNew)
                .expiresInSeconds(authSessionProperties.getTokenTtlSeconds())
                .build();
    }

    @Override
    public void logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (!StringUtils.hasText(token)) {
            return;
        }
        loginSessionService.revokeToken(token.trim());
    }

    private String extractBearerToken(HttpServletRequest request) {
        String raw = request.getHeader(httpIdentityProperties.getAuthorizationHeader());
        String prefix = authSessionProperties.getBearerPrefix();
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(prefix) || !raw.startsWith(prefix)) {
            return null;
        }
        return raw.substring(prefix.length()).trim();
    }

    /** 手机号后 4 位（盲索引）；不足 4 位原样返回 */
    private static String tail4(String phone) {
        return phone.length() >= 4 ? phone.substring(phone.length() - 4) : phone;
    }
}
