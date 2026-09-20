package com.percyku.livefit.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdatePasswordRequest(
        String password,
        @JsonProperty("new_password") String newPassword,
        @JsonProperty("confirm_new_password") String confirmNewPassword) {
}
