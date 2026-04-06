package com.smartapply.ida.common.domain.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短信登录成功响应：客户端保存 {@link #token}，后续接口在 {@code Authorization} 头携带 {@code Bearer} 前缀。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsLoginResultVO {

    /** 会话令牌，对应 Redis 中的登录态 */
    private String token;

    /** {@code ida_user.id} */
    private Long userId;

    /** 本次流程是否首次创建用户（用于前端引导完善资料等） */
    private boolean newUser;

    /** 与 {@code ida.auth.session.ttl} 等配置一致的剩余秒数，便于客户端刷新提示 */
    private long expiresInSeconds;
}
