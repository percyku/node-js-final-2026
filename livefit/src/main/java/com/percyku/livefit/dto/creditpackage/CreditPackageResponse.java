package com.percyku.livefit.dto.creditpackage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.CreditPackage;

import java.util.UUID;

/** GET /api/credit-package 的單筆資料 */
public record CreditPackageResponse(
        UUID id,
        String name,
        @JsonProperty("credit_amount") Integer creditAmount,
        Integer price) {

    public static CreditPackageResponse from(CreditPackage entity) {
        return new CreditPackageResponse(
                entity.getId(), entity.getName(), entity.getCreditAmount(), entity.getPrice());
    }
}
