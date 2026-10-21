package com.smartapply.ida.common.domain.feedback;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartapply.ida.common.mybatis.IdaTableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈表 {@code ida_app_feedback}，一行对应一次反馈提交（APP 内反馈或邮件渠道汇总）。
 * <p>
 * 附件不落库内二进制，{@code attachments} 存 OSS 对象键数组 JSON：
 * {@code [{objectKey,fileName,mimeType,size,mediaType}]}，读取时由应用层换成预签名 URL。
 */
@Data
@TableName("ida_app_feedback")
public class AppFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String source;
    private String contactEmail;
    private String content;
    /** 附件数组 JSON（见类注释），无附件时为 null */
    private String attachments;
    private String status;
    private String adminNote;

    @TableLogic(value = IdaTableLogic.NOT_DELETED, delval = IdaTableLogic.DELETED)
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
