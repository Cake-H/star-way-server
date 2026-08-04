package com.smartapply.ida.module.app.agreement;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.NoNeedLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 隐私政策 / 用户协议（匿名可访问，供注册页勾选前查看）。
 * <p>
 * 内容以 markdown 存放于 classpath {@code agreement/} 下，与 {@code docs/privacy-policy.md}、
 * {@code docs/user-agreement.md} 保持一致；首次读取后缓存于内存。
 */
@Tag(name = "正式客户端-协议")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/agreement")
@Slf4j
public class AgreementController {

    /** type → classpath 资源路径 */
    private static final Map<String, String> RESOURCES = Map.of(
            "privacy", "agreement/privacy.md",
            "terms", "agreement/terms.md");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 获取协议全文（markdown）。
     *
     * @param type {@code privacy} 隐私政策 / {@code terms} 用户协议
     */
    @Operation(summary = "获取隐私政策/用户协议（markdown，匿名可访问）")
    @NoNeedLogin
    @GetMapping("/{type}")
    public ResponseDTO<String> getAgreement(@PathVariable String type) {
        String path = RESOURCES.get(type);
        if (path == null) {
            throw new BusinessException("未知协议类型，仅支持 privacy / terms");
        }
        // computeIfAbsent 把"读-判空-加载-写回"做成了原子操作
        return ResponseDTO.ok(cache.computeIfAbsent(type, t -> load(path)));
    }

    private static String load(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("协议文件读取失败 path={}", path, e);
            throw new BusinessException("协议内容暂不可用");
        }
    }
}
