package com.percyku.livefit.dto.user;

/** PUT /api/users/profile → data: { user: { name } } */
public record UpdateNameResponse(NameOnly user) {

    public record NameOnly(String name) {
    }

    public static UpdateNameResponse of(String name) {
        return new UpdateNameResponse(new NameOnly(name));
    }
}
