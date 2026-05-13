package com.flashsale.interfaces.common;

public record CommonResponseDto<T>(
        String code,
        String message,
        T data
) {

    public static <T> CommonResponseDto<T> success(final T data) {
        return new CommonResponseDto<>("SUCCESS", "OK", data);
    }

    public static <T> CommonResponseDto<T> success(
            final String message,
            final T data
    ) {
        return new CommonResponseDto<>("SUCCESS", message, data);
    }

    public static CommonResponseDto<Void> error(
            final String code,
            final String message
    ) {
        return new CommonResponseDto<>(code, message, null);
    }
}
