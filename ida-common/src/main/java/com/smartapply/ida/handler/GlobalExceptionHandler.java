package com.smartapply.ida.handler;

import com.smartapply.ida.common.code.SystemErrorCode;
import com.smartapply.ida.common.code.UserErrorCode;
import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.SystemEnvironment;
import com.smartapply.ida.common.exception.BusinessException;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理：保证插件端与正式端接口均返回统一的 {@link ResponseDTO} JSON。
 * <p>
 * 已去除原模板中的 Sa-Token 权限异常；登录鉴权接入后可在本类中增加对应 {@code @ExceptionHandler}。
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @Resource
    private SystemEnvironment systemEnvironment;

    /** 请求体非合法 JSON（Content-Type 与 body 不匹配、截断等） */
    @ResponseBody
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseDTO<?> jsonFormatException(HttpMessageNotReadableException e) {
        if (!systemEnvironment.isProd()) {
            log.warn("请求体 JSON 无法解析, url={}", currentRequestUri(), e);
        }
        return ResponseDTO.userErrorParam("请求体格式错误或缺少 JSON 内容");
    }

    /** Bean Validation（{@code @Valid}）与表单绑定失败，聚合字段错误信息返回 */
    @ResponseBody
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseDTO<?> validationException(Exception e) {
        // @RequestBody + @Valid：取各字段 defaultMessage 拼接，直接给用户看「手机号不能为空」等
        if (e instanceof MethodArgumentNotValidException ex) {
            List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
            String joined = fieldErrors.stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            return ResponseDTO.userErrorParam(joined.isEmpty() ? UserErrorCode.PARAM_ERROR.getMsg() : joined);
        }
        // 传统表单绑定失败：附带字段名与拒绝值，便于排查非 JSON 请求
        if (e instanceof BindException ex) {
            List<FieldError> fieldErrors = ex.getFieldErrors();
            String joined = fieldErrors.stream()
                    .map(f -> f.getField() + ":" + f.getRejectedValue())
                    .collect(Collectors.joining(", "));
            return ResponseDTO.userErrorParam(UserErrorCode.PARAM_ERROR.getMsg() + ":" + joined);
        }
        return ResponseDTO.userErrorParam();
    }

    /** {@code @Validated} + {@code @RequestParam} 等方法级校验失败 */
    @ResponseBody
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseDTO<?> constraintViolation(ConstraintViolationException e) {
        String joined = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return ResponseDTO.userErrorParam(joined.isEmpty() ? UserErrorCode.PARAM_ERROR.getMsg() : joined);
    }

    /** 业务层显式抛出的可预期失败，不向客户端暴露堆栈 */
    @ResponseBody
    @ExceptionHandler(BusinessException.class)
    public ResponseDTO<?> businessException(BusinessException e) {
        if (!systemEnvironment.isProd()) {
            log.info("业务异常, url={} msg={}", currentRequestUri(), e.getMessage());
        }
        return ResponseDTO.error(UserErrorCode.OPERATION_FAILED, e.getMessage());
    }

    /** 兜底：非预期异常记 error 日志；生产环境不返回异常细节防信息泄露 */
    @ResponseBody
    @ExceptionHandler(Throwable.class)
    public ResponseDTO<?> anyThrowable(Throwable e) {
        log.error("未处理异常, url={}", currentRequestUri(), e);
        String detail = systemEnvironment.isProd() ? null : e.toString();
        return ResponseDTO.error(SystemErrorCode.SYSTEM_ERROR, detail);
    }

    private String currentRequestUri() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRequestURI();
        }
        return null;
    }
}
