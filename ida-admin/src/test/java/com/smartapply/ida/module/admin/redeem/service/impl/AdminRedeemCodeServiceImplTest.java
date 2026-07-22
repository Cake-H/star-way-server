package com.smartapply.ida.module.admin.redeem.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartapply.ida.common.domain.billing.RedeemCode;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.billing.mapper.RedeemCodeMapper;
import com.smartapply.ida.module.app.billing.service.RedeemCodeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理后台兑换码服务单测：禁用条件更新（仅 ACTIVE 可禁用）。
 */
@ExtendWith(MockitoExtension.class)
class AdminRedeemCodeServiceImplTest {

    /** 纯单测环境手动初始化实体 TableInfo，供 Service 内 LambdaWrapper 解析列名 */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, RedeemCode.class);
    }

    @Mock
    private RedeemCodeMapper redeemCodeMapper;
    @Mock
    private RedeemCodeService redeemCodeService;
    @InjectMocks
    private AdminRedeemCodeServiceImpl service;

    @Test
    void disableCode_activeCode_shouldSucceed() {
        when(redeemCodeMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.disableCode(1L, 100L);

        verify(redeemCodeMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void disableCode_alreadyUsedOrMissing_shouldThrow() {
        // 条件更新命中 0 行：码不存在或已被使用/禁用
        when(redeemCodeMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.disableCode(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被使用/禁用");
    }
}
