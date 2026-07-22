package com.smartapply.ida.module.admin.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartapply.ida.common.domain.admin.AdminAccount;
import com.smartapply.ida.common.domain.admin.vo.AdminLoginResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.props.admin.AdminAuthProperties;
import com.smartapply.ida.config.props.auth.AuthSessionProperties;
import com.smartapply.ida.config.props.http.HttpIdentityProperties;
import com.smartapply.ida.module.admin.auth.mapper.AdminAccountMapper;
import com.smartapply.ida.module.admin.auth.service.AdminAuthService;
import com.smartapply.ida.module.admin.auth.service.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 管理员认证服务实现。
 * <p>
 * <b>安全要点：</b>
 * <ul>
 *   <li>密码只做 BCrypt 比对，不落日志、不出参；</li>
 *   <li>登录失败统一提示「用户名或密码错误」，不区分账号是否存在，防止用户名枚举；</li>
 *   <li>同账号连续失败达到上限后锁定（Redis 计数 + TTL），防暴力破解；</li>
 *   <li>会话 Token 使用独立 Redis 前缀，与 C 端互不通兑。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 登录失败计数的 Redis key 前缀 */
    private static final String LOGIN_FAIL_KEY_PREFIX = "ida:admin:login-fail:";

    private final AdminAccountMapper adminAccountMapper;
    private final AdminSessionService adminSessionService;
    private final AdminAuthProperties adminAuthProperties;
    private final AuthSessionProperties authSessionProperties;
    private final HttpIdentityProperties identityProperties;
    private final StringRedisTemplate stringRedisTemplate;

    private final PasswordEncoder passwordEncoder;

    @Override
    public AdminLoginResultVO login(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || password == null || password.isEmpty()) {
            throw new BusinessException("用户名或密码错误");
        }

        // 防爆破：先查锁定状态，锁定期间拒绝一切登录尝试（包括密码正确的情况）
        String failKey = LOGIN_FAIL_KEY_PREFIX + name;
        String failCount = stringRedisTemplate.opsForValue().get(failKey);
        if (failCount != null && parseIntSafe(failCount) >= adminAuthProperties.getLoginMaxFailCount()) {
            log.warn("[管理员登录] 账号已锁定 username={}", name);
            throw new BusinessException("登录失败次数过多，账号已临时锁定，请稍后再试");
        }

        AdminAccount account = adminAccountMapper.selectOne(
                new LambdaQueryWrapper<AdminAccount>().eq(AdminAccount::getUsername, name));

        // 账号不存在与密码错误走同一提示，防用户名枚举；
        // 账号不存在时仍执行一次 BCrypt 比对，拉平响应时间，防时序侧信道探测
        String hashToCheck = account != null ? account.getPasswordHash()
                : "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5sVdCqi6RY3wDUvPXpYj2mF5yUmKK";
        boolean passwordMatch = passwordEncoder.matches(password, hashToCheck);

        if (account == null || !passwordMatch) {
            recordLoginFail(failKey);
            log.warn("[管理员登录] 登录失败 username={}", name);
            throw new BusinessException("用户名或密码错误");
        }
        if (account.getStatus() == null || account.getStatus() != AdminAccount.STATUS_NORMAL) {
            log.warn("[管理员登录] 账号已禁用 username={}", name);
            throw new BusinessException("账号已被禁用，请联系超级管理员");
        }

        // 登录成功：清空失败计数、刷新最近登录时间、签发 Token
        stringRedisTemplate.delete(failKey);
        AdminAccount update = new AdminAccount();
        update.setId(account.getId());
        update.setLastLoginAt(LocalDateTime.now(ZONE));
        adminAccountMapper.updateById(update);

        String token = adminSessionService.issueToken(account.getId());
        log.info("[管理员登录] 登录成功 username={} adminId={}", name, account.getId());
        return AdminLoginResultVO.builder()
                .token(token)
                .name(account.getName())
                .role(account.getRole())
                .build();
    }

    @Override
    public void logout(HttpServletRequest request) {
        String authHeader = request.getHeader(identityProperties.getAuthorizationHeader());
        String prefix = authSessionProperties.getBearerPrefix();
        if (authHeader != null && authHeader.startsWith(prefix)) {
            adminSessionService.revokeToken(authHeader.substring(prefix.length()).trim());
        }
    }

    /** 记录一次登录失败：计数 +1 并设置锁定时长，达到上限即锁定 */
    private void recordLoginFail(String failKey) {
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failKey, Duration.ofSeconds(adminAuthProperties.getLoginLockSeconds()));
        }
    }

    /** Redis 值被污染时按 0 处理，避免 NumberFormatException 导致 500 */
    private int parseIntSafe(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
