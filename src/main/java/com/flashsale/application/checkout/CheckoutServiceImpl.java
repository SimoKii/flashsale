package com.flashsale.application.checkout;

import com.flashsale.application.booking.port.ProductRepository;
import com.flashsale.application.booking.port.StockPort;
import com.flashsale.application.checkout.dto.CheckoutQuery;
import com.flashsale.application.checkout.dto.CheckoutResult;
import com.flashsale.application.point.PointService;
import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.product.Product;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ProductRepository productRepository;
    private final PointService pointService;
    private final StockPort stockPort;

    public CheckoutServiceImpl(
            final ProductRepository productRepository,
            final PointService pointService,
            @Qualifier("databaseStockAdapter") final StockPort stockPort
    ) {
        this.productRepository = productRepository;
        this.pointService = pointService;
        this.stockPort = stockPort;
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutResult query(final CheckoutQuery query) {
        Product product = productRepository.findById(query.productId())
                .orElseThrow(() -> new DomainException("Product not found: " + query.productId()));
        long balance = pointService.findBalance(query.userId());
        int remaining = stockPort.remaining(query.productId());

        return new CheckoutResult(
                new CheckoutResult.ProductInfo(
                        product.getId(),
                        product.getName(),
                        product.getPrice().amount(),
                        product.getCheckinAt().format(FORMATTER),
                        product.getCheckoutAt().format(FORMATTER),
                        product.isOnSale(LocalDateTime.now())
                ),
                new CheckoutResult.UserPointInfo(balance),
                new CheckoutResult.StockInfo(remaining)
        );
    }
}
