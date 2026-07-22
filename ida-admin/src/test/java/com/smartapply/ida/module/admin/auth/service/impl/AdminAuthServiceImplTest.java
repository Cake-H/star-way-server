package com.smartapply.ida.module.admin.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartapply.ida.common.domain.admin.AdminAccount;
import com.smartapply.ida.common.domain.admin.vo.AdminLoginResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.props.admin.AdminAuthProperties;
import com.smartapply.ida.config.props.auth.AuthSessionProperties;
import com.smartapply.ida.config.props.http.HttpIdentityProperties;
import com.smartapply.ida.module.admin.auth.mapper.AdminAccountMapper;
import com.smartapply.ida.module.admin.auth.service.AdminSessionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员认证服务单测：登录成功/失败计数/锁定/禁用/防用户名枚举。
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    @Mock
    private AdminAccountMapper adminAccountMapper;
    @Mock
    private AdminSessionService adminSessionService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private AdminAuthServiceImpl service;

    /** 纯单测环境手动初始化实体 TableInfo，供 Service 内 LambdaWrapper 解析列名 */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AdminAccount.class);
    }

    @BeforeEach
    void setUp() {
        // 配置类使用真实实例，显式设定阈值，避免依赖默认值变化
        AdminAuthProperties adminAuthProperties = new AdminAuthProperties();
        adminAuthProperties.setLoginMaxFailCount(5);
        adminAuthProperties.setLoginLockSeconds(600);
        service = new AdminAuthServiceImpl(
                adminAccountMapper, adminSessionService, adminAuthProperties,
                new AuthSessionProperties(), new HttpIdentityProperties(),
                stringRedisTemplate, encoder);
    }

    private AdminAccount normalAccount() {
        AdminAccount account = new AdminAccount();
        account.setId(1L);
        account.setUsername("admin");
        account.setPasswordHash(encoder.encode("Admin@123456"));
        account.setStatus(AdminAccount.STATUS_NORMAL);
        account.setRole(AdminAccount.ROLE_ADMIN);
        return account;
    }

    @Test
    void login_success_shouldIssueTokenAndClearFailCount() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(adminAccountMapper.selectOne(any())).thenReturn(normalAccount());
        when(adminSessionService.issueToken(1L)).thenReturn("token-abc");

        AdminLoginResultVO result = service.login("admin", "Admin@123456");

        assertThat(result.getToken()).isEqualTo("token-abc");
        assertThat(result.getRole()).isEqualTo(AdminAccount.ROLE_ADMIN);
        // 成功登录后清空失败计数
        verify(stringRedisTemplate).delete(anyString());
    }

    @Test
    void login_wrongPassword_shouldThrowAndRecordFail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(adminAccountMapper.selectOne(any())).thenReturn(normalAccount());

        assertThatThrownBy(() -> service.login("admin", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
        // 失败计数 +1
        verify(valueOperations).increment(anyString());
        verify(adminSessionService, never()).issueToken(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void login_userNotFound_shouldThrowSameMessageAndRecordFail() {
        // 防用户名枚举：账号不存在与密码错误提示一致，且同样计失败次数
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(adminAccountMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.login("ghost", "whatever"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
        verify(valueOperations).increment(anyString());
    }

    @Test
    void login_lockedAccount_shouldRejectBeforeQuery() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("5");

        assertThatThrownBy(() -> service.login("admin", "Admin@123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("锁定");
        // 锁定期间不再查询账号、不再校验密码
        verify(adminAccountMapper, never()).selectOne(any());
    }

    @Test
    void login_disabledAccount_shouldThrow() {
        AdminAccount disabled = normalAccount();
        disabled.setStatus(AdminAccount.STATUS_DISABLED);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(adminAccountMapper.selectOne(any())).thenReturn(disabled);

        assertThatThrownBy(() -> service.login("admin", "Admin@123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("禁用");
        verify(adminSessionService, never()).issueToken(org.mockito.ArgumentMatchers.anyLong());
    }
}
