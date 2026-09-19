package com.smartcane.backend.exception;

/**
 * 越权访问（例如监护人请求未绑定设备的数据）。
 * 由 GlobalExceptionHandler 统一转成 Result.error(403, ...)。
 */
public class NoPermissionException extends RuntimeException {

    public NoPermissionException(String message) {
        super(message);
    }
}
