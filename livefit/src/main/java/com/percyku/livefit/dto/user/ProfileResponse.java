package com.percyku.livefit.dto.user;

/** GET /api/users/profile → data: { user: { name, email } } */
public record ProfileResponse(ProfileUser user) {

    public record ProfileUser(String name, String email) {
    }

    public static ProfileResponse of(String name, String email) {
        return new ProfileResponse(new ProfileUser(name, email));
    }
}
