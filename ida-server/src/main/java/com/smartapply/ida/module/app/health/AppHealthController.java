package com.smartapply.ida.module.app.health;

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
 * 正式客户端探活与后续「简历编辑、AI 优化、账号体系」等接口的占位入口。
 */
@NoNeedLogin
@Tag(name = "正式客户端")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP)
public class AppHealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public ResponseDTO<Map<String, String>> health() {
        return ResponseDTO.ok(Map.of(
                "channel", "app",
                "status", "ok"
        ));
    }
}
