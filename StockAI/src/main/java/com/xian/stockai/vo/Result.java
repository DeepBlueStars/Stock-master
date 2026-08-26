package com.xian.stockai.vo;

import lombok.*;
import lombok.experimental.Accessors;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Accessors(chain = true)
public class Result<T> {
    public static final int STATE_SUCCESS = 0;
    public static final int STATE_FAIL = 1;
    public static final int STATE_TOKEN_EXPIRED = 2;

    private int state;
    private String message;
    private T data;

    public boolean checkSuccess() {
        return this.state == STATE_SUCCESS;
    }

    static public <T> Result<T> success() {
        return new Result<>(STATE_SUCCESS, null, null);
    }

    static public <T> Result<T> success(T data) {
        return new Result<>(STATE_SUCCESS, null, data);
    }

    static public <T> Result<T> success(String message, T data) {
        return new Result<>(STATE_SUCCESS, message, data);
    }

    static public <T> Result<T> successIfNotNull(T data) {
        return successIfNotNull(data, "SERVER_UNKNOWN_ERROR");
    }

    static public <T> Result<T> successIfNotNull(T data, String failMsg) {
        if (data != null) {
            return new Result<>(STATE_SUCCESS, null, data);
        } else {
            return new Result<>(STATE_FAIL, failMsg, null);
        }
    }

    static public <T> Result<T> fail() {
        return new Result<>(STATE_FAIL, "SERVER_UNKNOWN_ERROR", null);
    }

    static public <T> Result<T> fail(String message) {
        return new Result<>(STATE_FAIL, message, null);
    }

    static public <T> Result<T> fail(String message, T data) {
        return new Result<>(STATE_FAIL, message, data);
    }

    static public <T> Result<T> fail(Integer state, String message, T data) {
        return new Result<>(state, message, data);
    }

    static public <T> Result<T> expire(String message) {
        return new Result<>(STATE_TOKEN_EXPIRED, message, null);
    }
}