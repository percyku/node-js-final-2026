package com.percyku.livefit.dto.creditpackage;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/credit-package 的 request body */
public record CreditPackageRequest(
        String name,
        @JsonProperty("credit_amount") Integer creditAmount,
        Integer price) {
}
