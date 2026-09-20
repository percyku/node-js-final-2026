package com.percyku.livefit.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.CreditPurchase;

import java.time.Instant;

/**
 * GET /api/users/credit-package 的一筆購買紀錄。
 * openapi 明訂 price_paid 回數字型別（Node 版的 purchase_at 因變數名打錯永遠是 undefined，此處修正）。
 */
public record CreditPurchaseResponse(
        String name,
        @JsonProperty("purchased_credits") Integer purchasedCredits,
        @JsonProperty("price_paid") Integer pricePaid,
        @JsonProperty("purchase_at") Instant purchaseAt) {

    public static CreditPurchaseResponse from(CreditPurchase purchase) {
        return new CreditPurchaseResponse(
                purchase.getCreditPackage().getName(),
                purchase.getPurchasedCredits(),
                purchase.getPricePaid() == null ? null : purchase.getPricePaid().intValue(),
                purchase.getPurchaseAt());
    }
}
