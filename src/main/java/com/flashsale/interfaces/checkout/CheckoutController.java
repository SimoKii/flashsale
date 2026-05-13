package com.flashsale.interfaces.checkout;

import com.flashsale.application.checkout.CheckoutUsecase;
import com.flashsale.application.checkout.dto.CheckoutQuery;
import com.flashsale.application.checkout.dto.CheckoutResult;
import com.flashsale.interfaces.checkout.dto.CheckoutResponseDto;
import com.flashsale.interfaces.common.CommonResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutUsecase checkoutUsecase;

    @GetMapping
    public ResponseEntity<CommonResponseDto<CheckoutResponseDto>> checkout(
            @RequestParam final Long productId,
            @RequestHeader("X-User-Id") final Long userId
    ) {
        CheckoutResult result = checkoutUsecase.query(
                new CheckoutQuery(
                        productId,
                        userId
                )
        );
        return ResponseEntity.ok(CommonResponseDto.success(CheckoutResponseDto.from(result)));
    }
}
