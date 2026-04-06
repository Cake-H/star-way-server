package com.smartapply.ida.common.domain.user.form;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserForm {
    //用户主键@TableId
    private Long id;

    //登录手机号（建议加密）
    private String phone;

    //1=正常 0=禁用
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
