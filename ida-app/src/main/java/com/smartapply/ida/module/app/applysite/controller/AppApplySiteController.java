package com.smartapply.ida.module.app.applysite.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.applysite.vo.AppApplySiteVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.NoNeedLogin;
import com.smartapply.ida.module.app.applysite.service.AppApplySiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 正式客户端 — 网申网站列表 HTTP 入口（前缀 {@code /api/v1/app/apply-sites}）。
 * <p>
 * 站点目录由管理后台维护（{@code /api/v1/admin/apply-sites}），本模块只读上架数据，
 * 供用户快捷跳转各网申入口；支持插件一键填表的站点前端可引导安装浏览器扩展。
 */
@Tag(name = "正式客户端-网申网站")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/apply-sites")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AppApplySiteController {

    private final AppApplySiteService appApplySiteService;

    /**
     * 分类列表（用于分类 Tab），按站点排序值首次出现顺序返回。
     */
    @NoNeedLogin
    @Operation(summary = "站点分类列表（Tab 用）")
    @GetMapping("/categories")
    public ResponseDTO<List<String>> listCategories() {
        log.debug("收到网申站点分类列表申请");
        return ResponseDTO.ok(appApplySiteService.listCategories());
    }

    /**
     * 上架站点列表。
     *
     * @param category 分类筛选，不传返回全部
     */
    @NoNeedLogin
    @Operation(summary = "网申站点列表（按分类筛选）")
    @GetMapping
    public ResponseDTO<List<AppApplySiteVO>> listSites(
            @RequestParam(value = "category", required = false) String category) {
        log.debug("收到网申站点列表申请 category={}", category);
        return ResponseDTO.ok(appApplySiteService.listSites(category));
    }
}
