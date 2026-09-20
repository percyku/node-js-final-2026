package com.percyku.livefit.dto.user;

import java.util.UUID;

/** POST /api/users/signup → data: { user: { id, name } } */
public record SignupResponse(SignupUser user) {

    public record SignupUser(UUID id, String name) {
    }

    public static SignupResponse of(UUID id, String name) {
        return new SignupResponse(new SignupUser(id, name));
    }
}
