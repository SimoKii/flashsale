package com.flashsale.interfaces.common;

import com.flashsale.application.booking.exception.DuplicateBookingException;
import com.flashsale.application.booking.exception.InvalidPaymentCompositionException;
import com.flashsale.application.booking.exception.ServiceUnavailableException;
import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.stock.StockExhaustedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StockExhaustedException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleStockExhausted(final StockExhaustedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(CommonResponseDto.error("SOLD_OUT", e.getMessage()));
    }

    @ExceptionHandler(DuplicateBookingException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleDuplicateBooking(final DuplicateBookingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(CommonResponseDto.error("DUPLICATE_BOOKING", e.getMessage()));
    }

    @ExceptionHandler(InvalidPaymentCompositionException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleInvalidPaymentComposition(
            final InvalidPaymentCompositionException e
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponseDto.error("INVALID_PAYMENT_COMPOSITION", e.getMessage()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleServiceUnavailable(final ServiceUnavailableException e) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "5");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .headers(headers)
                .body(CommonResponseDto.error("SERVICE_UNAVAILABLE", e.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleDomain(final DomainException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponseDto.error("DOMAIN_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleMissingHeader(
            final MissingRequestHeaderException e
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponseDto.error(
                        "MISSING_HEADER",
                        "필수 헤더가 누락되었습니다: " + e.getHeaderName()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleMissingParameter(
            final MissingServletRequestParameterException e
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponseDto.error(
                        "MISSING_PARAMETER",
                        "필수 파라미터가 누락되었습니다: " + e.getParameterName()
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleTypeMismatch(
            final MethodArgumentTypeMismatchException e
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponseDto.error(
                        "INVALID_PARAMETER_TYPE",
                        "파라미터 형식이 올바르지 않습니다: " + e.getName()
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleNotReadable(
            final HttpMessageNotReadableException e
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponseDto.error(
                        "INVALID_REQUEST_BODY",
                        "요청 본문이 올바르지 않습니다"
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleMediaTypeNotSupported(
            final HttpMediaTypeNotSupportedException e
    ) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(CommonResponseDto.error(
                        "UNSUPPORTED_MEDIA_TYPE",
                        "지원하지 않는 Content-Type입니다"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseDto<Void>> handleUnexpected(final Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponseDto.error("INTERNAL_ERROR", "내부 서버 오류"));
    }
}
