package com.smartapply.ida.module.app.applysite.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartapply.ida.common.domain.applysite.ApplySite;
import com.smartapply.ida.common.domain.applysite.vo.AppApplySiteVO;
import com.smartapply.ida.module.app.applysite.mapper.AppApplySiteMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * C 端网申站点服务单测：分类去重保序、空分类过滤、VO 字段映射、无匹配返回空列表。
 * <p>
 * Mapper 层（status=1 过滤、sortOrder 排序、category trim 匹配）由 MyBatis-Plus
 * LambdaQueryWrapper 承载，属 SQL 侧行为，不在本单测范围内。
 */
@ExtendWith(MockitoExtension.class)
class AppApplySiteServiceImplTest {

    @Mock
    private AppApplySiteMapper appApplySiteMapper;
    @InjectMocks
    private AppApplySiteServiceImpl service;

    /**
     * {@code LambdaQueryWrapper.select(...)} 会在调用时立即解析 lambda 列名，
     * 纯单测无 SqlSession 上下文，需手工初始化实体 TableInfo 缓存。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApplySite.class);
    }

    private ApplySite site(long id, String name, String category) {
        ApplySite site = new ApplySite();
        site.setId(id);
        site.setName(name);
        site.setLogoUrl("https://cdn.example.com/logo/" + id + ".png");
        site.setUrl("https://example.com/" + id);
        site.setCategory(category);
        site.setSupportAutofill(1);
        site.setSortOrder((int) id);
        site.setStatus(ApplySite.STATUS_ON);
        site.setRemark("内部备注");
        return site;
    }

    @Test
    void listCategories_dedupKeepFirstOccurrenceOrder() {
        // Mapper 返回的是按 sortOrder 升序的行；同名分类多次出现时应只保留首次出现位置
        when(appApplySiteMapper.selectList(any())).thenReturn(List.of(
                site(1, "北森校招", "ATS系统"),
                site(2, "智联招聘", "综合招聘"),
                site(3, "腾讯校招", "互联网"),
                site(4, "阿里校招", "互联网"),
                site(5, "Moka", "ATS系统")
        ));

        List<String> categories = service.listCategories();

        assertThat(categories).containsExactly("ATS系统", "综合招聘", "互联网");
    }

    @Test
    void listCategories_filterBlankCategory() {
        ApplySite blank = site(2, "未分类站点", "  ");
        ApplySite nullCat = site(3, "空分类站点", null);
        when(appApplySiteMapper.selectList(any())).thenReturn(List.of(
                site(1, "腾讯校招", "互联网"),
                blank,
                nullCat
        ));

        List<String> categories = service.listCategories();

        assertThat(categories).containsExactly("互联网");
    }

    @Test
    void listSites_withoutCategory_returnsMappedVos() {
        when(appApplySiteMapper.selectList(any())).thenReturn(List.of(
                site(1, "北森校招", "ATS系统"),
                site(2, "腾讯校招", "互联网")
        ));

        List<AppApplySiteVO> sites = service.listSites(null);

        assertThat(sites).hasSize(2);
        AppApplySiteVO first = sites.get(0);
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(first.getName()).isEqualTo("北森校招");
        assertThat(first.getLogoUrl()).isEqualTo("https://cdn.example.com/logo/1.png");
        assertThat(first.getUrl()).isEqualTo("https://example.com/1");
        assertThat(first.getCategory()).isEqualTo("ATS系统");
        assertThat(first.getSupportAutofill()).isEqualTo(1);
    }

    @Test
    void listSites_blankCategory_treatedAsNoFilter() {
        // 空串/纯空格 category 等价于不传，不得抛异常、按全部处理
        when(appApplySiteMapper.selectList(any())).thenReturn(List.of(site(1, "北森校招", "ATS系统")));

        assertThat(service.listSites("")).hasSize(1);
        assertThat(service.listSites("   ")).hasSize(1);
    }

    @Test
    void listSites_noMatch_returnsEmptyList() {
        // category 无匹配站点时返回空数组而非报错
        when(appApplySiteMapper.selectList(any())).thenReturn(List.of());

        List<AppApplySiteVO> sites = service.listSites("不存在的分类");

        assertThat(sites).isEmpty();
    }
}
