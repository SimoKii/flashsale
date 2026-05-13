package com.flashsale.interfaces.checkout;

import com.flashsale.application.checkout.CheckoutUsecase;
import com.flashsale.application.checkout.dto.CheckoutQuery;
import com.flashsale.application.checkout.dto.CheckoutResult;
import com.flashsale.application.checkout.dto.CheckoutResult.ProductInfo;
import com.flashsale.application.checkout.dto.CheckoutResult.StockInfo;
import com.flashsale.application.checkout.dto.CheckoutResult.UserPointInfo;
import com.flashsale.common.exception.DomainException;
import com.flashsale.interfaces.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CheckoutController.class, GlobalExceptionHandler.class})
@DisplayName("CheckoutController")
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckoutUsecase checkoutUsecase;

    private CheckoutResult validResult() {
        return new CheckoutResult(
                new ProductInfo(
                        1L,
                        "제주 특가 숙소",
                        50000L,
                        "2026-06-01T15:00:00",
                        "2026-06-03T11:00:00",
                        true
                ),
                new UserPointInfo(100000L),
                new StockInfo(7)
        );
    }

    @Nested
    @DisplayName("주문서 조회 성공")
    class Success {

        @BeforeEach
        void setUp() {
            when(checkoutUsecase.query(any(CheckoutQuery.class)))
                    .thenReturn(validResult());
        }

        @Test
        @DisplayName("유효한 요청은 200과 SUCCESS 코드를 반환한다")
        void validRequest_returns200WithSuccessCode() throws Exception {
            mockMvc.perform(get("/api/v1/checkout")
                            .param("productId", "1")
                            .header("X-User-Id", "42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"));
        }

        @Test
        @DisplayName("응답에 상품 정보가 포함된다")
        void response_containsProductInfo() throws Exception {
            mockMvc.perform(get("/api/v1/checkout")
                            .param("productId", "1")
                            .header("X-User-Id", "42"))
                    .andExpect(jsonPath("$.data.product.id").value(1))
                    .andExpect(jsonPath("$.data.product.name").value("제주 특가 숙소"))
                    .andExpect(jsonPath("$.data.product.price").value(50000))
                    .andExpect(jsonPath("$.data.product.checkinAt").value("2026-06-01T15:00:00"))
                    .andExpect(jsonPath("$.data.product.checkoutAt").value("2026-06-03T11:00:00"))
                    .andExpect(jsonPath("$.data.product.onSale").value(true));
        }

        @Test
        @DisplayName("응답에 사용자 포인트 정보가 포함된다")
        void response_containsUserPointInfo() throws Exception {
            mockMvc.perform(get("/api/v1/checkout")
                            .param("productId", "1")
                            .header("X-User-Id", "42"))
                    .andExpect(jsonPath("$.data.userPoint.balance").value(100000));
        }

        @Test
        @DisplayName("응답에 재고 정보가 포함된다")
        void response_containsStockInfo() throws Exception {
            mockMvc.perform(get("/api/v1/checkout")
                            .param("productId", "1")
                            .header("X-User-Id", "42"))
                    .andExpect(jsonPath("$.data.stock.remaining").value(7));
        }
    }

    @Nested
    @DisplayName("요청 검증")
    class RequestValidation {

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
        void missingUserIdHeader_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/checkout")
                            .param("productId", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("productId 파라미터가 없으면 400을 반환한다")
        void missingProductId_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/checkout")
                            .header("X-User-Id", "42"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("productId가 숫자가 아니면 400을 반환한다")
        void nonNumericProductId_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/checkout")
                            .param("productId", "abc")
                            .header("X-User-Id", "42"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("X-User-Id가 숫자가 아니면 400을 반환한다")
        void nonNumericUserId_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/checkout")
                            .param("productId", "1")
                            .header("X-User-Id", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("도메인 예외 처리")
    class DomainExceptionHandling {

        @Test
        @DisplayName("존재하지 않는 상품 조회 시 도메인 예외가 응답으로 변환된다")
        void unknownProduct_returnsErrorResponse() throws Exception {
            when(checkoutUsecase.query(any(CheckoutQuery.class)))
                    .thenThrow(new DomainException("Product not found: 999"));

            mockMvc.perform(get("/api/v1/checkout")
                            .param("productId", "999")
                            .header("X-User-Id", "42"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("DOMAIN_ERROR"))
                    .andExpect(jsonPath("$.message").value("Product not found: 999"));
        }
    }
}