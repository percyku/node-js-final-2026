package com.percyku.livefit.dto.user;

/** POST /api/users/login → data: { token, user: { name } } */
public record LoginResponse(String token, LoginUser user) {

    public record LoginUser(String name) {
    }

    public static LoginResponse of(String token, String name) {
        return new LoginResponse(token, new LoginUser(name));
    }
}
