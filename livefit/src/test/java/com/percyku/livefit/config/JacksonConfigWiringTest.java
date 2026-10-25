package com.percyku.livefit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jackson 的型別轉換設定有沒有套用到 Spring MVC 解析 request body 實際用的 ObjectMapper。
 * JacksonCoercionTest 是 @JsonTest，JacksonConfig 要 @Import 才會載入，驗不到正式環境靠 component scan 載入這件事：
 * 拿掉 @Configuration、或把類別搬出 com.percyku.livefit，那邊照樣通過，這裡會失敗。
 * mapper 從 MVC 的 converter 清單取，不直接注入 converter bean：加上 @EnableWebMvc 或覆寫 configureMessageConverters 時，
 * MVC 改用不讀 spring.jackson.* 的預設 mapper，bean 卻還在。
 * 不打 API、不比對錯誤訊息，HTTP 層回 400 由 Postman 驗。
 */
@SpringBootTest
class JacksonConfigWiringTest {

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Test
    void MVC用的ObjectMapper有套用型別轉換設定() {
        // MVC 由前往後找，第一個能讀 JSON 的 converter 就是解析 request body 的那一個
        HttpMessageConverter<?> json = handlerAdapter.getMessageConverters().stream()
                .filter(converter -> converter.canRead(Object.class, MediaType.APPLICATION_JSON))
                .findFirst()
                .orElseThrow();
        assertThat(json).isInstanceOf(MappingJackson2HttpMessageConverter.class);
        ObjectMapper objectMapper = ((MappingJackson2HttpMessageConverter) json).getObjectMapper();

        // JacksonConfig
        assertThatThrownBy(() -> objectMapper.readValue("123", String.class))
                .isInstanceOf(MismatchedInputException.class);
        // application.properties 的 allow-coercion-of-scalars、accept-float-as-int
        assertThatThrownBy(() -> objectMapper.readValue("\"10\"", Integer.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> objectMapper.readValue("10.9", Integer.class))
                .isInstanceOf(MismatchedInputException.class);
    }
}
