package com.smartapply.ida.common.domain;

import com.smartapply.ida.common.code.ErrorCode;
import com.smartapply.ida.common.code.UserErrorCode;
import com.smartapply.ida.common.enumeration.DataTypeEnum;
import lombok.Data;

/**
 * 统一 API 响应体（与 SmartAdmin 模板语义一致：code / ok / msg / data）。
 * <p>
 * 成功：code = 0；失败：code 为各错误枚举中的数值。
 */
@Data
public class ResponseDTO<T> {

    public static final int OK_CODE = 0;
    public static final String OK_MSG = "操作成功";

    private Integer code;
    /** 错误等级：system / user / unexpected，成功时可为 null */
    private String level;
    private Boolean ok;
    private String msg;
    private T data;
    /** 预留字段，与模板 DataType 一致 */
    private Integer dataType;

    public ResponseDTO(Integer code, String level, boolean ok, String msg, T data) {
        this.code = code;
        this.level = level;
        this.ok = ok;
        this.msg = msg;
        this.data = data;
        this.dataType = DataTypeEnum.NORMAL.getValue();
    }

    public ResponseDTO(ErrorCode errorCode, boolean ok, String msg, T data) {
        this.code = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.ok = ok;
        this.msg = (msg != null && !msg.isBlank()) ? msg : errorCode.getMsg();
        this.data = data;
        this.dataType = DataTypeEnum.NORMAL.getValue();
    }

    /** 无载荷成功 */
    public static <T> ResponseDTO<T> ok() {
        return new ResponseDTO<>(OK_CODE, null, true, OK_MSG, null);
    }

    /** 带业务数据成功 */
    public static <T> ResponseDTO<T> ok(T data) {
        return new ResponseDTO<>(OK_CODE, null, true, OK_MSG, data);
    }

    /** 成功但仅自定义提示文案（无 data） */
    public static <T> ResponseDTO<T> okMsg(String msg) {
        return new ResponseDTO<>(OK_CODE, null, true, msg, null);
    }

    /** 参数类用户错误，默认文案 */
    public static <T> ResponseDTO<T> userErrorParam() {
        return new ResponseDTO<>(UserErrorCode.PARAM_ERROR, false, null, null);
    }

    /** 参数类用户错误，自定义 msg */
    public static <T> ResponseDTO<T> userErrorParam(String msg) {
        return new ResponseDTO<>(UserErrorCode.PARAM_ERROR, false, msg, null);
    }

    /** 按错误码失败，无附加说明与 data */
    public static <T> ResponseDTO<T> error(ErrorCode errorCode) {
        return new ResponseDTO<>(errorCode, false, null, null);
    }

    /** 按错误码失败，覆盖或补充 msg */
    public static <T> ResponseDTO<T> error(ErrorCode errorCode, String msg) {
        return new ResponseDTO<>(errorCode, false, msg, null);
    }

    /** 失败时仍返回结构化 data（如校验结果列表） */
    public static <T> ResponseDTO<T> errorData(ErrorCode errorCode, T data) {
        return new ResponseDTO<>(errorCode, false, null, data);
    }
}
