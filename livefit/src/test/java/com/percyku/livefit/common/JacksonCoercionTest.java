package com.percyku.livefit.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * application.properties 的兩個 Jackson 設定有沒有生效：整數欄位不接受字串與小數。
 * HTTP 層回 400 欄位未填寫正確由 Postman 驗；這裡是讓只跑 mvn test 的 CI 也抓得到設定被拿掉，
 * 或升級 Spring Boot 後屬性名稱改了而被默默忽略（那時字串與小數又會被轉成整數收下，不會報錯）。
 */
@JsonTest
class JacksonCoercionTest {

    @Autowired
    private ObjectMapper objectMapper;

    private record Body(Integer value) {
    }

    @Test
    void 字串不能轉成整數() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":\"10\"}", Body.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void 小數不能轉成整數() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":10.9}", Body.class))
                .isInstanceOf(MismatchedInputException.class);
        // Node 版會收 10.0 與 1e1（JS 解析後與 10 相同），這裡會擋，差異記在 migration plan §8
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":10.0}", Body.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":1e1}", Body.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void 數字型別的整數照常接受() throws Exception {
        assertThat(objectMapper.readValue("{\"value\":10}", Body.class).value()).isEqualTo(10);
    }
}
