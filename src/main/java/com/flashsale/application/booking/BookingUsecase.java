package com.flashsale.application.booking;

import com.flashsale.application.booking.dto.BookingAcceptedResult;
import com.flashsale.application.booking.dto.BookingCommand;
import com.flashsale.application.booking.dto.BookingStatusResult;

public interface BookingUsecase {

    BookingAcceptedResult book(BookingCommand cmd);

    BookingStatusResult getBookingStatus(Long productId, String ticketId);
}
