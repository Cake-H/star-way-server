package com.smartapply.ida.module.admin.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.admin.vo.AdminUserListItemVO;
import com.smartapply.ida.common.domain.admin.vo.QuotaAdjustResultVO;
import com.smartapply.ida.common.domain.billing.QuotaTransaction;
import com.smartapply.ida.common.domain.billing.vo.UsageUserRankVO;
import com.smartapply.ida.common.domain.user.UserEntity;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.admin.usage.mapper.UsageStatisticsMapper;
import com.smartapply.ida.module.app.billing.mapper.QuotaTransactionMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeMapper;
import com.smartapply.ida.module.app.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理后台用户服务单测：分页脱敏、手动调额（校验/正向/负向/流水审计）。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    /** 纯单测环境手动初始化实体 TableInfo，供 Service 内 LambdaWrapper 解析列名 */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
        TableInfoHelper.initTableInfo(assistant, com.smartapply.ida.common.domain.resume.Resume.class);
    }

    @Mock
    private UserMapper userMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private QuotaTransactionMapper quotaTransactionMapper;
    @Mock
    private UsageStatisticsMapper usageStatisticsMapper;
    @InjectMocks
    private AdminUserServiceImpl service;

    private UserEntity userWithBalance(int balance) {
        UserEntity user = new UserEntity();
        user.setId(9L);
        user.setPhone("13812345678");
        user.setStatus(1);
        user.setBalance(balance);
        user.setTotalQuota(100);
        user.setMemberLevel("FREE");
        return user;
    }

    @Test
    void pageUsers_shouldMaskPhone() {
        Page<UserEntity> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(userWithBalance(35)));
        when(userMapper.selectPage(any(), any())).thenReturn(page);

        PageVO<AdminUserListItemVO> result = service.pageUsers(1, 20, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        // 手机号必须脱敏：前 3 后 4，不出现完整号码
        assertThat(result.getRecords().get(0).getPhone()).isEqualTo("138****5678");
        assertThat(result.getRecords().get(0).getBalance()).isEqualTo(35);
    }

    @Test
    void adjustQuota_zeroPoints_shouldThrow() {
        assertThatThrownBy(() -> service.adjustQuota(1L, 9L, 0, "test"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为 0");
    }

    @Test
    void adjustQuota_userNotFound_shouldThrow() {
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.adjustQuota(1L, 9L, 50, "test"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void adjustQuota_positive_shouldIncreaseTotalQuotaAndWriteAuditTransaction() {
        when(userMapper.selectById(9L)).thenReturn(userWithBalance(35));

        QuotaAdjustResultVO result = service.adjustQuota(1L, 9L, 50, "种子用户赠送");

        assertThat(result.getBalanceAfter()).isEqualTo(35);
        // 正向调整：setSql 应同时累加 total_quota
        ArgumentCaptor<LambdaUpdateWrapper> uwCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), uwCaptor.capture());
        assertThat(uwCaptor.getValue().getSqlSet()).contains("total_quota");

        // 流水：admin_adjust 类型，备注含原因与操作管理员 ID
        ArgumentCaptor<QuotaTransaction> txCaptor = ArgumentCaptor.forClass(QuotaTransaction.class);
        verify(quotaTransactionMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(QuotaTransaction.TYPE_ADMIN_ADJUST);
        assertThat(txCaptor.getValue().getPoints()).isEqualTo(50);
        assertThat(txCaptor.getValue().getRemark()).contains("种子用户赠送").contains("1");
    }

    @Test
    void adjustQuota_negative_shouldNotTouchTotalQuota() {
        when(userMapper.selectById(9L)).thenReturn(userWithBalance(35));

        service.adjustQuota(1L, 9L, -10, "误发回收");

        ArgumentCaptor<LambdaUpdateWrapper> uwCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), uwCaptor.capture());
        // 负向扣减只动 balance，不动累计发放额度
        assertThat(uwCaptor.getValue().getSqlSet()).doesNotContain("total_quota");
    }

    @Test
    void getUserDetail_shouldAggregateResumeAndUsage() {
        when(userMapper.selectById(9L)).thenReturn(userWithBalance(35));
        when(resumeMapper.selectCount(any())).thenReturn(3L);
        when(usageStatisticsMapper.selectUserSummary(any(), any())).thenReturn(
                UsageUserRankVO.builder().userId(9L).callCount(12L).totalTokens(34567L).costPoints(35L).build());

        var detail = service.getUserDetail(9L);

        assertThat(detail.getPhone()).isEqualTo("138****5678");
        assertThat(detail.getResumeCount()).isEqualTo(3L);
        assertThat(detail.getRecent30dCallCount()).isEqualTo(12L);
        assertThat(detail.getRecent30dCostPoints()).isEqualTo(35L);
    }
}
