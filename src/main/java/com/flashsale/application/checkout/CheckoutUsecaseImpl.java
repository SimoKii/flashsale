package com.flashsale.application.checkout;

import com.flashsale.application.checkout.dto.CheckoutQuery;
import com.flashsale.application.checkout.dto.CheckoutResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckoutUsecaseImpl implements CheckoutUsecase {

    private final CheckoutService checkoutService;

    @Override
    public CheckoutResult query(final CheckoutQuery query) {
        return checkoutService.query(query);
    }
}
