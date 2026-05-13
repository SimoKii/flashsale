package com.flashsale.application.booking.port;

public interface StockPort {

    /**
     * 재고를 점유한다. 성공 시 남은 재고 수, 매진이면 -1 반환.
     *
     * @param ticketId 큐 티켓 ID — Redis 어댑터에서 중복 점유 방지에 사용
     * @param userId   사용자 ID — Redis 어댑터에서 holders 관리에 사용
     */
    int reserve(final Long productId, final String ticketId, final Long userId);

    /**
     * 점유 취소 (결제 실패·만료 시).
     */
    void restore(final Long productId, final String ticketId);

    /**
     * 점유 → 판매 확정 (결제 성공 시).
     */
    void confirm(final Long productId, final String ticketId);

    /**
     * 현재 잔여 재고.
     */
    int remaining(final Long productId);
}
