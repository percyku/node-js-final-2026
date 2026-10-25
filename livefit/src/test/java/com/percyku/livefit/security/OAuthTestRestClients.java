package com.percyku.livefit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OAuth client 的測試共用：讓 MockRestServiceServer 的假回應用正式環境同一個 ObjectMapper 解析 */
final class OAuthTestRestClients {

    private OAuthTestRestClients() {
    }

    /**
     * RestClient.builder() 預設的 ObjectMapper 不讀 application.properties，換成 Spring Boot 建好的那一個
     * （正式環境注入的 RestClient.Builder 用的就是它），record 的欄位型別與嚴格的 Jackson 設定不相容時測試才會失敗。
     */
    static RestClient.Builder builder(ObjectMapper objectMapper) {
        // JacksonConfig 不在 @JsonTest 載入的範圍內，測試類別忘了 @Import 的話，注入的 ObjectMapper 少了字串欄位的嚴格設定，
        // 測試會照樣通過，卻沒有驗到正式環境的解析規則
        assertThatThrownBy(() -> objectMapper.readValue("123", String.class))
                .as("測試類別要加 @Import(JacksonConfig.class)")
                .isInstanceOf(MismatchedInputException.class);
        return RestClient.builder().messageConverters(converters -> {
            converters.replaceAll(converter -> converter instanceof MappingJackson2HttpMessageConverter
                    ? new MappingJackson2HttpMessageConverter(objectMapper)
                    : converter);
            // 沒換到的話（例如升級後 JSON 改由別的 converter 先處理），測試會退回預設的 ObjectMapper 而照樣通過
            HttpMessageConverter<?> json = converters.stream()
                    .filter(converter -> converter.canRead(Object.class, MediaType.APPLICATION_JSON))
                    .findFirst()
                    .orElseThrow();
            assertThat(json).isInstanceOfSatisfying(MappingJackson2HttpMessageConverter.class,
                    jackson -> assertThat(jackson.getObjectMapper()).isSameAs(objectMapper));
        });
    }
}
