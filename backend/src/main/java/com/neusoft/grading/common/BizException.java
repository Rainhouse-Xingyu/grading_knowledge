package com.neusoft.grading.common;

import lombok.Getter;

/**
 * 业务异常：用于认证授权、参数校验等业务层抛出，
 * 由 GlobalExceptionHandler 统一捕获并转换为 Result 响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 快捷构造 401 未授权异常 */
    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }
}
