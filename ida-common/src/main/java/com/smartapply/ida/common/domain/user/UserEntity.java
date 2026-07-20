package com.smartapply.ida.common.domain.user;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 对应库表 {@code ida_user}，与短信登录、简历归属关联。
 * <p>
 * {@link #phone} 入库前应由服务端手机号加解密组件（与 {@code ida.crypto.aes-field} 配置一致）处理，查询条件须使用相同变换。
 */
@SuppressWarnings("serial")
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_user")
public class UserEntity {

    /** 自增主键 */
    private Long id;

    /** 登录手机号（密文或明文取决于 {@code ida.crypto.aes-field.enabled}） */
    private String phone;

    /** 1=正常 0=禁用，禁用用户不可登录 */
    private Integer status;

    /** AI 额度余额（点），见 {@code ida_v5_billing.sql} */
    private Integer balance;

    /** 累计发放额度（点），运营统计用 */
    private Integer totalQuota;

    /** 会员等级：FREE/PRO...（预留，M4 会员体系启用） */
    private String memberLevel;

    /** 最近一次每日免费额度发放日期（懒发放去重依据） */
    private LocalDate lastDailyGrantDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
