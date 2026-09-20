package com.percyku.livefit.common;

import java.util.regex.Pattern;

/**
 * 對應 Node 版 backend/utils/validUtils.js，驗證規則逐條保持一致。
 */
public final class ValidUtils {

    /** 至少一個小寫、一個大寫、一個數字，長度 8～16 */
    private static final Pattern PASSWORD =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,16}$");

    private ValidUtils() {
    }

    /** isValidString：非 null 且去除空白後不為空字串 */
    public static boolean isValidString(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** isInteger：Node 版要求 JSON 中必須是數字型別的整數，字串數字視為不合法 */
    public static boolean isInteger(Integer value) {
        return value != null;
    }

    public static boolean isValidPassword(String value) {
        return value != null && PASSWORD.matcher(value).matches();
    }

    /** 教練頭像與會議連結都要求 https 開頭 */
    public static boolean isHttpsUrl(String value) {
        return isValidString(value) && value.startsWith("https");
    }
}
