package com.percyku.livefit.dto.common;

import java.util.List;

/** 對應 TypeORM DeleteResult 的形狀：{"raw": [], "affected": 1}（openapi 註明不列入驗收） */
public record DeleteResult(List<Object> raw, int affected) {

    public static DeleteResult of(int affected) {
        return new DeleteResult(List.of(), affected);
    }
}
