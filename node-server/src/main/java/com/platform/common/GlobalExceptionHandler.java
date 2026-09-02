package com.platform.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 *
 * <p>关键约定（与 Agent 配套）：
 *   BizException(UNAUTHORIZED)        → HTTP 401
 *   BizException(INSTANCE_NOT_FOUND)  → HTTP 404
 *   BizException(INSTANCE_DEREGISTERED)→ HTTP 410
 *   其他                                → HTTP 200 + code != 0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        HttpStatus status;
        if (e.getCode() == ResultCode.UNAUTHORIZED.getCode()
                || e.getCode() == ResultCode.TOKEN_DISABLED.getCode()) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (e.getCode() == ResultCode.INSTANCE_NOT_FOUND.getCode()) {
            status = HttpStatus.NOT_FOUND;
        } else if (e.getCode() == ResultCode.INSTANCE_DEREGISTERED.getCode()) {
            status = HttpStatus.GONE;
        } else {
            status = HttpStatus.OK;
        }
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, msg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, msg));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ResultCode.INSTANCE_NOT_FOUND, "接口不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAny(Exception e) {
        e.printStackTrace();
        return ResponseEntity.ok(Result.fail(ResultCode.INTERNAL_ERROR, e.getMessage()));
    }
}