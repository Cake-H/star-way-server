package com.smartapply.ida.module.admin.applysite.service.impl;

import com.smartapply.ida.common.domain.applysite.ApplySite;
import com.smartapply.ida.common.domain.applysite.dto.ApplySiteUpsertRequest;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.admin.applysite.mapper.ApplySiteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理后台网申站点服务单测：上下架切换、更新不清状态、新建默认上架。
 */
@ExtendWith(MockitoExtension.class)
class AdminApplySiteServiceImplTest {

    @Mock
    private ApplySiteMapper applySiteMapper;
    @InjectMocks
    private AdminApplySiteServiceImpl service;

    private ApplySite siteWithStatus(int status) {
        ApplySite site = new ApplySite();
        site.setId(1L);
        site.setName("北森校招");
        site.setUrl("https://www.beisen.com");
        site.setCategory("ATS系统");
        site.setStatus(status);
        return site;
    }

    @Test
    void toggleSite_onToOff() {
        when(applySiteMapper.selectById(1L)).thenReturn(siteWithStatus(ApplySite.STATUS_ON));

        service.toggleSite(1L);

        ArgumentCaptor<ApplySite> captor = ArgumentCaptor.forClass(ApplySite.class);
        verify(applySiteMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplySite.STATUS_OFF);
    }

    @Test
    void toggleSite_offToOn() {
        when(applySiteMapper.selectById(1L)).thenReturn(siteWithStatus(ApplySite.STATUS_OFF));

        service.toggleSite(1L);

        ArgumentCaptor<ApplySite> captor = ArgumentCaptor.forClass(ApplySite.class);
        verify(applySiteMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplySite.STATUS_ON);
    }

    @Test
    void toggleSite_notFound_shouldThrow() {
        when(applySiteMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.toggleSite(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("站点不存在");
    }

    @Test
    void updateSite_shouldNotOverrideStatus() {
        when(applySiteMapper.selectById(1L)).thenReturn(siteWithStatus(ApplySite.STATUS_OFF));
        ApplySiteUpsertRequest request = new ApplySiteUpsertRequest();
        request.setName("北森校招（新）");
        request.setUrl("https://www.beisen.com");
        request.setCategory("ATS系统");
        request.setSupportAutofill(1);
        request.setSortOrder(1);

        service.updateSite(1L, request);

        ArgumentCaptor<ApplySite> captor = ArgumentCaptor.forClass(ApplySite.class);
        verify(applySiteMapper).updateById(captor.capture());
        // 编辑接口不得变更上下架状态（防止误操作覆盖），status 必须为 null 不参与更新
        assertThat(captor.getValue().getStatus()).isNull();
        assertThat(captor.getValue().getName()).isEqualTo("北森校招（新）");
    }

    @Test
    void createSite_shouldDefaultToOnShelf() {
        ApplySiteUpsertRequest request = new ApplySiteUpsertRequest();
        request.setName("新站点");
        request.setUrl("https://example.com");
        request.setCategory("互联网");
        request.setSupportAutofill(0);
        request.setSortOrder(99);

        service.createSite(request);

        ArgumentCaptor<ApplySite> captor = ArgumentCaptor.forClass(ApplySite.class);
        verify(applySiteMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplySite.STATUS_ON);
    }
}
