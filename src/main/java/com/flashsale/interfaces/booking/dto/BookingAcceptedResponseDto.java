package com.flashsale.interfaces.booking.dto;

import com.flashsale.application.booking.dto.BookingAcceptedResult;

public record BookingAcceptedResponseDto(
        String ticketId,
        long queuePosition
) {

    public static BookingAcceptedResponseDto from(
            BookingAcceptedResult result
    ) {
        return new BookingAcceptedResponseDto(
                result.ticketId(),
                result.queuePosition()
        );
    }
}
