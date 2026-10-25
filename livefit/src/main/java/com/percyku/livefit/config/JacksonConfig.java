package com.percyku.livefit.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 字串欄位不接受數字與布林：{"name": 123} 要回 400 欄位未填寫正確，不能被當成 "123" 收下。
 *
 * Node 版的 isValidString 要求 typeof === "string"；Jackson 預設會把數字、布林轉成字串，
 * 比 service 的 ValidUtils.isValidString 還早，service 擋不到。
 * application.properties 那兩個 Jackson 設定只管「轉成數字」，管不到「轉成字串」，也沒有對應的 properties，
 * 所以在這裡設定。會套用到所有 request body，以及 GitHub、Facebook 回應的解析（RestClient.Builder 是 Spring 注入的）。
 *
 * 注意：@JsonTest 不會載入這個類別，用到 ObjectMapper 的 @JsonTest 要 @Import(JacksonConfig.class)。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer rejectScalarToStringCoercion() {
        return builder -> builder.postConfigurer(objectMapper -> objectMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
