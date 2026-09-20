package com.percyku.livefit.dto.creditpackage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.CreditPackage;

import java.time.Instant;
import java.util.UUID;

/** POST /api/credit-package 的回應，openapi 的欄位名是 camelCase 的 createdAt */
public record CreditPackageCreatedResponse(
        UUID id,
        String name,
        @JsonProperty("credit_amount") Integer creditAmount,
        Integer price,
        Instant createdAt) {

    public static CreditPackageCreatedResponse from(CreditPackage entity) {
        return new CreditPackageCreatedResponse(entity.getId(), entity.getName(),
                entity.getCreditAmount(), entity.getPrice(), entity.getCreatedAt());
    }
}
