package com.smartapply.ida.common.domain.user;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartapply.ida.common.mybatis.IdaTableLogic;

/**
 * 对应库表 {@code ida_user}，与短信登录、简历归属关联。
 * <p>
 * {@link #phone} 入库前应由服务端手机号加解密组件（与 {@code ida.crypto.aes-field} 配置一致）处理，查询条件须使用相同变换。
 * {@link #phoneTail} 为盲索引（后 4 位明文），用于管理后台在密文之上的尾号检索——
 * 任意子串模糊匹配在加密数据上不可行，业界通行做法即"全号精确 / 后 4 位 / 用户 ID"。
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

    /** 手机号后 4 位（盲索引，供管理端尾号搜索；注销时清空） */
    private String phoneTail;

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

    /** 逻辑删除时间（账号注销） */
    @TableLogic(value = IdaTableLogic.NOT_DELETED, delval = IdaTableLogic.DELETED)
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
