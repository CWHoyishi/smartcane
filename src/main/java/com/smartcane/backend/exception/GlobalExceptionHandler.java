package com.smartcane.backend.exception;

import com.smartcane.backend.entity.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 *
 * 沿用项目既有约定：HTTP 状态码固定 200，业务码放在 body 的 code 字段，
 * 前端统一判断 res.code。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoPermissionException.class)
    public Result<Void> handleNoPermission(NoPermissionException e) {
        log.warn("[越权拦截] {}", e.getMessage());
        return Result.error(403, e.getMessage());
    }
}
