package com.percyku.livefit.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.percyku.livefit.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jackson 的型別轉換設定有沒有生效：整數欄位不接受字串與小數（application.properties 的兩個設定），
 * 字串欄位不接受數字與布林（JacksonConfig）。
 * HTTP 層回 400 欄位未填寫正確由 Postman 驗；這裡是讓只跑 mvn test 的 CI 也抓得到設定被拿掉，
 * 或升級 Spring Boot 後屬性名稱改了而被默默忽略（那時會又把不同型別的值轉過去收下，不會報錯）。
 * JacksonConfig 不在 @JsonTest 載入的範圍內，要自己 @Import；正式環境靠 component scan 載入。
 */
@JsonTest
@Import(JacksonConfig.class)
class JacksonCoercionTest {

    @Autowired
    private ObjectMapper objectMapper;

    private record IntegerBody(Integer value) {
    }

    private record StringBody(String value) {
    }

    @Test
    void 字串不能轉成整數() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":\"10\"}", IntegerBody.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void 小數不能轉成整數() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":10.9}", IntegerBody.class))
                .isInstanceOf(MismatchedInputException.class);
        // Node 版會收 10.0 與 1e1（JS 解析後與 10 相同），這裡會擋，差異記在 migration plan §8
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":10.0}", IntegerBody.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":1e1}", IntegerBody.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void 數字型別的整數照常接受() throws Exception {
        assertThat(objectMapper.readValue("{\"value\":10}", IntegerBody.class).value()).isEqualTo(10);
    }

    @Test
    void 數字與布林不能轉成字串() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":123}", StringBody.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":3.14}", StringBody.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":true}", StringBody.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void 字串與null照常接受() throws Exception {
        assertThat(objectMapper.readValue("{\"value\":\"123\"}", StringBody.class).value()).isEqualTo("123");
        // 缺欄位與 null 交給 service 的 isValidString 回 400，Jackson 這一層不擋
        assertThat(objectMapper.readValue("{\"value\":null}", StringBody.class).value()).isNull();
    }
}
