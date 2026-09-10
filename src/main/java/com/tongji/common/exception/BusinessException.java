package com.tongji.common.exception;

import lombok.Getter;

@Getter // 自动生成getter方法
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码，用于前端/调用方做稳定的错误分支处理。
     */
    private final ErrorCode errorCode;


    /**
     * 使用错误码的默认文案构造异常。
     *
     * @param errorCode 错误码（必填）
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }


    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
