package com.flashsale.interfaces.booking;

import com.flashsale.application.booking.BookingUsecase;
import com.flashsale.application.booking.dto.BookingAcceptedResult;
import com.flashsale.application.booking.dto.BookingCommand;
import com.flashsale.application.booking.dto.BookingStatusResult;
import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.domain.order.PaymentMethodCode;
import com.flashsale.interfaces.booking.dto.BookingAcceptedResponseDto;
import com.flashsale.interfaces.booking.dto.BookingRequestDto;
import com.flashsale.interfaces.booking.dto.BookingStatusResponseDto;
import com.flashsale.interfaces.common.CommonResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingUsecase bookingUsecase;

    @PostMapping
    public ResponseEntity<CommonResponseDto<BookingAcceptedResponseDto>> book(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody BookingRequestDto request
    ) {
        BookingCommand cmd = new BookingCommand(
                request.productId(),
                userId,
                idempotencyKey,
                request.totalAmount(),
                request.paymentLines().stream()
                        .map(l -> new PaymentLineCommand(
                                l.sequence(),
                                PaymentMethodCode.valueOf(l.method()),
                                l.amount(),
                                l.cardNumber(),
                                l.idempotencyKey(),
                                userId
                        ))
                        .toList()
        );
        BookingAcceptedResult result = bookingUsecase.book(cmd);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(CommonResponseDto.success(BookingAcceptedResponseDto.from(result)));
    }

    @GetMapping("/status")
    public ResponseEntity<CommonResponseDto<BookingStatusResponseDto>> getStatus(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long productId,
            @RequestParam String ticketId
    ) {
        BookingStatusResult result = bookingUsecase.getBookingStatus(productId, ticketId);
        return ResponseEntity.ok(CommonResponseDto.success(BookingStatusResponseDto.from(result)));
    }
}
