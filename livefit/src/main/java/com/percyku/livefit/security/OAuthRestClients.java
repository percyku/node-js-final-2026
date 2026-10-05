package com.percyku.livefit.security;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** 第三方登入向平台取使用者資料時共用的 RestClient 設定 */
final class OAuthRestClients {

    // 前端 axios 的逾時是 10 秒，一次登入最多連平台三次，逾時加起來不能超過，
    // 否則前端先斷線、後端卻已建好帳號。讀取逾時是「等不到資料」的上限，正常情況每次呼叫遠低於此
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private OAuthRestClients() {
    }

    static RestClient withTimeouts(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return builder.requestFactory(factory).build();
    }
}
