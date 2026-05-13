package com.flashsale.application.booking;

import com.flashsale.application.booking.dto.BookingStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingUsecase bookingUsecase;

    @Override
    public BookingStatusResult getBookingStatus(
            final Long productId,
            final String ticketId
    ) {
        return bookingUsecase.getBookingStatus(productId, ticketId);
    }
}
