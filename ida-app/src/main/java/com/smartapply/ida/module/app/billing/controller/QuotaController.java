package com.smartapply.ida.module.app.billing.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.billing.dto.RedeemCodeRequest;
import com.smartapply.ida.common.domain.billing.vo.QuotaBalanceVO;
import com.smartapply.ida.common.domain.billing.vo.QuotaTransactionVO;
import com.smartapply.ida.common.domain.billing.vo.RedeemResultVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.module.app.billing.service.RedeemCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 正式客户端 — AI 额度 HTTP 入口（前缀 {@code /api/v1/app/quota}）。
 * <p>
 * <b>路由分层：</b>
 * <ul>
 *   <li>{@code GET /balance}：当前额度总览（余额、累计额度、会员等级、每日发放日期）。</li>
 *   <li>{@code GET /transactions}：额度流水（充值/消耗/兑换等）。</li>
 *   <li>{@code POST /redeem}：兑换码充值（测试期过渡方案，M4 将接入真实支付）。</li>
 * </ul>
 */
@Tag(name = "正式客户端-AI 额度")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/quota")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class QuotaController {

    private final BillingService billingService;
    private final RedeemCodeService redeemCodeService;

    /**
     * 查询当前额度总览。
     *
     * @param userId 当前用户（鉴权注入）
     * @return 余额、累计额度、会员等级等
     */
    @Operation(summary = "查询当前额度总览")
    @GetMapping("/balance")
    public ResponseDTO<QuotaBalanceVO> getBalance(@RequestUserId(required = true) Long userId) {
        log.debug("收到查询额度申请，/balance userId={}", userId);
        return ResponseDTO.ok(billingService.getBalanceVO(userId));
    }

    /**
     * 查询额度流水。
     *
     * @param userId 当前用户
     * @param limit  最大返回条数，默认 20，上限 100
     * @return 按时间倒序的流水列表
     */
    @Operation(summary = "查询额度流水")
    @GetMapping("/transactions")
    public ResponseDTO<List<QuotaTransactionVO>> listTransactions(
            @RequestUserId(required = true) Long userId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.debug("收到查询额度流水申请，/transactions userId={} limit={}", userId, limit);
        return ResponseDTO.ok(billingService.listTransactions(userId, limit));
    }

    /**
     * 兑换码充值。
     *
     * @param userId 当前用户
     * @param body   兑换码
     * @return 到账点数与兑换后余额
     */
    @Operation(summary = "兑换码充值")
    @PostMapping("/redeem")
    public ResponseDTO<RedeemResultVO> redeem(
            @RequestUserId(required = true) Long userId,
            @Valid @RequestBody RedeemCodeRequest body) {
        log.info("收到兑换码充值申请，/redeem userId={} code={}", userId, body != null ? body.getCode() : null);
        return ResponseDTO.ok(redeemCodeService.redeem(userId, body != null ? body.getCode() : null));
    }
}
