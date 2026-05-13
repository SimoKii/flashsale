package com.flashsale.application.booking;

import com.flashsale.application.booking.dto.BookingStatusResult;

public interface BookingService {

    BookingStatusResult getBookingStatus(Long productId, String ticketId);
}
