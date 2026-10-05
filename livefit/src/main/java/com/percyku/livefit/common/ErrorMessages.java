package com.percyku.livefit.common;

/**
 * 錯誤訊息常數。
 * FIXED_* 開頭的四句是 openapi.yaml 明訂「前端會逐字比對」的固定訊息，不可更動一字。
 */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    // ===== openapi 明訂的四句固定訊息 =====
    public static final String FIXED_ALREADY_BOOKED = "已經報名過此課程";
    public static final String FIXED_NO_CREDIT = "已無可使用堂數";
    public static final String FIXED_FULL = "已達最大參加人數，無法參加";
    public static final String FIXED_NOT_LOGIN = "請先登入";

    // ===== 一般訊息 =====
    public static final String INVALID_FIELDS = "欄位未填寫正確";
    public static final String INVALID_ID = "ID錯誤";
    public static final String DUPLICATED = "資料重複";
    public static final String TOKEN_EXPIRED = "Token 已過期";
    public static final String TOKEN_INVALID = "無效的 token";
    public static final String NOT_COACH = "使用者尚未成為教練";
    public static final String ROUTE_NOT_FOUND = "無此路由";
    public static final String SERVER_ERROR = "伺服器錯誤";

    public static final String PASSWORD_RULE =
            "密碼不符合規則，需要包含英文數字大小寫，最短8個字，最長16個字";

    public static final String EMAIL_TAKEN = "Email 已被使用";
    public static final String LOGIN_FAILED = "使用者不存在或密碼輸入錯誤";
    public static final String UPDATE_FAILED = "更新失敗";
    public static final String NAME_NOT_CHANGED = "使用者名稱未變更";
    public static final String UPDATE_USER_PROFILE_FAILED = "更新使用者資料失敗";
    public static final String PASSWORD_SAME_AS_OLD = "新密碼不能與舊密碼相同";
    public static final String PASSWORD_CONFIRM_MISMATCH = "新密碼與驗證新密碼不一致";
    public static final String PASSWORD_WRONG = "密碼輸入錯誤";

    public static final String GOOGLE_VERIFY_FAILED = "Google 登入驗證失敗";
    public static final String GOOGLE_NOT_CONFIGURED = "尚未設定 Google 登入";
    public static final String GITHUB_VERIFY_FAILED = "GitHub 登入驗證失敗";
    public static final String GITHUB_NOT_CONFIGURED = "尚未設定 GitHub 登入";
    public static final String GITHUB_NO_VERIFIED_EMAIL = "此 GitHub 帳號沒有已驗證的 Email，無法登入";
    public static final String SOCIAL_ACCOUNT_NO_PASSWORD = "此帳號使用第三方登入，無法修改密碼";

    public static final String USER_NOT_FOUND = "使用者不存在";
    public static final String ALREADY_COACH = "使用者已經是教練";
    public static final String UPDATE_USER_FAILED = "更新使用者失敗";
    public static final String COACH_NOT_FOUND = "找不到該教練";
    public static final String COURSE_NOT_FOUND = "課程不存在";
    public static final String UPDATE_COURSE_FAILED = "更新課程失敗";
}
