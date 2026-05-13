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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StockExhaustedException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleSoldOut(final StockExhaustedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(CommonResponseDto.error("SOLD_OUT", e.getMessage()));
    }

    @ExceptionHandler(DuplicateBookingException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleDuplicate(final DuplicateBookingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(CommonResponseDto.error("DUPLICATE_BOOKING", e.getMessage()));
    }

    @ExceptionHandler(InvalidPaymentCompositionException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleInvalidComposition(final InvalidPaymentCompositionException e) {
        return ResponseEntity.badRequest()
                .body(CommonResponseDto.error("INVALID_PAYMENT_COMPOSITION", e.getMessage()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleUnavailable(final ServiceUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(CommonResponseDto.error("SERVICE_UNAVAILABLE", e.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleDomain(final DomainException e) {
        return ResponseEntity.badRequest()
                .body(CommonResponseDto.error("DOMAIN_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleValidation(final MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(CommonResponseDto.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleMissingHeader(final MissingRequestHeaderException e) {
        return ResponseEntity.badRequest()
                .body(CommonResponseDto.error("MISSING_HEADER", "필수 헤더가 누락되었습니다"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleMissingParameter(final MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(CommonResponseDto.error("MISSING_PARAMETER", "필수 파라미터가 누락되었습니다"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResponseDto<Void>> handleTypeMismatch(final MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(CommonResponseDto.error("INVALID_PARAMETER_TYPE", "파라미터 형식이 올바르지 않습니다"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseDto<Void>> handleUnknown(final Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
                .body(CommonResponseDto.error("INTERNAL_ERROR", "내부 서버 오류"));
    }
}
