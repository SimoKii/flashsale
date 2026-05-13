package com.flashsale.application.checkout;

import com.flashsale.application.checkout.dto.CheckoutQuery;
import com.flashsale.application.checkout.dto.CheckoutResult;

public interface CheckoutUsecase {

    CheckoutResult query(CheckoutQuery query);
}
