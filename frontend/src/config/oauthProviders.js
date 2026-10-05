import { postGithubLogin, postFacebookLogin } from "../api/index.js";

// 走 authorization code 流程的第三方登入：按鈕整頁導向 authorizeUrl，
// 平台授權後帶著 code 跳回 /oauth/callback/:provider，再由 login 把 code 交給後端。
// 沒設定 clientId 的平台不會顯示按鈕。
export const OAUTH_PROVIDERS = {
  github: {
    label: "GitHub",
    clientId: import.meta.env.VITE_GITHUB_CLIENT_ID,
    authorizeUrl: "https://github.com/login/oauth/authorize",
    scope: "read:user user:email",
    login: postGithubLogin,
  },
  facebook: {
    label: "Facebook",
    clientId: import.meta.env.VITE_FACEBOOK_APP_ID,
    // Graph API 版本與後端 FacebookOAuthClient 相同
    authorizeUrl: "https://www.facebook.com/v26.0/dialog/oauth",
    scope: "public_profile,email",
    login: postFacebookLogin,
  },
};

// 授權後跳回來的網址，必須與平台後台登記的 callback URL、後端 OAUTH_REDIRECT_URIS 完全相同
export function getRedirectUri(provider) {
  return `${window.location.origin}/oauth/callback/${provider}`;
}

// 導向授權頁前存入、跳回來時比對的 state 所用的 sessionStorage key
export function getStateKey(provider) {
  return `oauth_state_${provider}`;
}
