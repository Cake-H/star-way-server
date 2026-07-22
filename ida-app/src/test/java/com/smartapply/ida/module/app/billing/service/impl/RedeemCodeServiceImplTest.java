package com.smartapply.ida.module.app.billing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartapply.ida.common.code.UserErrorCode;
import com.smartapply.ida.common.domain.billing.QuotaTransaction;
import com.smartapply.ida.common.domain.billing.RedeemCode;
import com.smartapply.ida.common.domain.billing.vo.RedeemResultVO;
import com.smartapply.ida.common.domain.user.UserEntity;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.billing.mapper.QuotaTransactionMapper;
import com.smartapply.ida.module.app.billing.mapper.RedeemCodeMapper;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.module.app.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 兑换码充值服务单测：兑换成功路径、各类无效码、并发防重。
 */
@ExtendWith(MockitoExtension.class)
class RedeemCodeServiceImplTest {

    /**
     * 纯单测环境没有 Spring/MyBatis 启动过程，需手动初始化实体 TableInfo，
     * 否则 Service 内使用 LambdaWrapper 时会报「can not find lambda cache」。
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, RedeemCode.class);
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
    }

    @Mock
    private RedeemCodeMapper redeemCodeMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private QuotaTransactionMapper quotaTransactionMapper;
    @Mock
    private BillingService billingService;
    @InjectMocks
    private RedeemCodeServiceImpl service;

    /**
     * 构造一张可用兑换码
     */
    private RedeemCode activeCode() {
        return RedeemCode.builder()
                .id(1L).code("ABC123").points(100)
                .status(RedeemCode.STATUS_ACTIVE)
                .expireTime(LocalDateTime.now().plusDays(7))
                .build();
    }

    @Test
    void redeem_success_shouldAddQuotaAndWriteTransaction() {
        when(redeemCodeMapper.selectOne(any())).thenReturn(activeCode());
        when(redeemCodeMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(billingService.selectBalance(9L)).thenReturn(110);

        RedeemResultVO result = service.redeem(9L, "ABC123");

        assertThat(result.getPoints()).isEqualTo(100);
        assertThat(result.getBalanceAfter()).isEqualTo(110);

        // 验证流水：类型 redeem、点数 +100、备注含兑换码
        ArgumentCaptor<QuotaTransaction> txCaptor = ArgumentCaptor.forClass(QuotaTransaction.class);
        verify(quotaTransactionMapper).insert(txCaptor.capture());
        QuotaTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(QuotaTransaction.TYPE_REDEEM);
        assertThat(tx.getPoints()).isEqualTo(100);
        assertThat(tx.getUserId()).isEqualTo(9L);
        assertThat(tx.getRemark()).contains("ABC123");

        // 验证给用户加了额度
        verify(userMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void redeem_codeNotFound_shouldThrow30201() {
        when(redeemCodeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.redeem(9L, "NO_SUCH"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.REDEEM_CODE_INVALID));
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void redeem_blankCode_shouldThrow30201() {
        assertThatThrownBy(() -> service.redeem(9L, "  "))
                .isInstanceOf(BusinessException.class);
        verify(redeemCodeMapper, never()).selectOne(any());
    }

    @Test
    void redeem_usedCode_shouldThrow() {
        RedeemCode used = activeCode();
        used.setStatus(RedeemCode.STATUS_USED);
        when(redeemCodeMapper.selectOne(any())).thenReturn(used);

        assertThatThrownBy(() -> service.redeem(9L, "ABC123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已使用或已禁用");
    }

    @Test
    void redeem_expiredCode_shouldThrow() {
        RedeemCode expired = activeCode();
        expired.setExpireTime(LocalDateTime.now().minusDays(1));
        when(redeemCodeMapper.selectOne(any())).thenReturn(expired);

        assertThatThrownBy(() -> service.redeem(9L, "ABC123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    void redeem_concurrentUpdateReturnsZero_shouldThrow() {
        // 模拟并发：前置校验通过，但原子置为已使用时已被他人兑换
        when(redeemCodeMapper.selectOne(any())).thenReturn(activeCode());
        when(redeemCodeMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.redeem(9L, "ABC123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被使用");
        verify(userMapper, never()).update(any(), any());
        verify(quotaTransactionMapper, never()).insert(org.mockito.ArgumentMatchers.<QuotaTransaction>any());
    }

    @Test
    void generateCodes_invalidParams_shouldThrow() {
        assertThatThrownBy(() -> service.generateCodes(0, 100, null, null, "admin:1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生成数量");
        assertThatThrownBy(() -> service.generateCodes(10, 0, null, null, "admin:1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("兑换点数");
    }

    @Test
    void generateCodes_success_shouldInsertAll() {
        when(redeemCodeMapper.insert(org.mockito.ArgumentMatchers.<RedeemCode>any())).thenReturn(1);

        var codes = service.generateCodes(3, 50, null, "batch-1", "admin:1");

        assertThat(codes).hasSize(3);
        assertThat(codes).allSatisfy(c -> {
            assertThat(c.getCode()).hasSize(32);
            assertThat(c.getPoints()).isEqualTo(50);
            assertThat(c.getStatus()).isEqualTo(RedeemCode.STATUS_ACTIVE);
        });
        verify(redeemCodeMapper, times(3)).insert(org.mockito.ArgumentMatchers.<RedeemCode>any());
    }
}
