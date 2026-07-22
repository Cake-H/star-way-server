package com.smartapply.ida.module.admin.health;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.NoNeedLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台探活入口（匿名可访问，用于部署健康检查）。
 */
@NoNeedLogin
@Tag(name = "管理后台-健康检查")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_ADMIN)
public class AdminHealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public ResponseDTO<Map<String, String>> health() {
        return ResponseDTO.ok(Map.of(
                "channel", "admin",
                "status", "ok"
        ));
    }
}
