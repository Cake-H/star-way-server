package com.smartapply.ida.module.plugin.health;

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
 * 插件端探活与后续「简历同步、字段映射上报」等接口的占位入口。
 */
@NoNeedLogin
@Tag(name = "插件端")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_PLUGIN)
public class PluginHealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public ResponseDTO<Map<String, String>> health() {
        return ResponseDTO.ok(Map.of(
                "channel", "plugin",
                "status", "ok"
        ));
    }
}
