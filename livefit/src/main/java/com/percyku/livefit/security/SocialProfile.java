package com.percyku.livefit.security;

/**
 * 第三方平台驗證通過後取得的使用者資料，各平台統一成這個形狀再交給 UserService 對應帳號。
 *
 * @param provider      平台代碼，見 UserIdentity.PROVIDER_*
 * @param subject       該平台的使用者唯一識別碼
 * @param emailVerified 平台是否保證這個 email 屬於該使用者；為 false 時不能拿來綁定既有帳號
 */
public record SocialProfile(String provider, String subject, String email, boolean emailVerified, String name) {
}
