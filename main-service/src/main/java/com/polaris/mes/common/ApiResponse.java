package com.polaris.mes.common;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, String message, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "ok", Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }

    public static <T> ApiResponse<T> fail(T data, String message) {
        return new ApiResponse<>(false, data, message, Instant.now());
    }
}
