<template>
  <!-- 一個第三方登入都沒設定時整塊不顯示（搭配 Node 版後端時就是這樣） -->
  <div v-if="hasAnyProvider" class="space-y-4">
    <div class="flex items-center gap-3 text-sm text-primary-400">
      <span class="h-px flex-1 bg-primary-600"></span>
      <span>或</span>
      <span class="h-px flex-1 bg-primary-600"></span>
    </div>
    <GoogleLoginButton />
    <OAuthRedirectButton provider="github">
      <template #icon>
        <svg
          class="size-5"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <path
            d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56 0-.27-.01-1.17-.02-2.12-3.2.7-3.87-1.36-3.87-1.36-.52-1.33-1.28-1.68-1.28-1.68-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.18 1.76 1.18 1.03 1.76 2.69 1.25 3.35.96.1-.74.4-1.25.73-1.54-2.55-.29-5.24-1.28-5.24-5.68 0-1.26.45-2.28 1.18-3.09-.12-.29-.51-1.46.11-3.04 0 0 .97-.31 3.16 1.18a10.9 10.9 0 0 1 5.76 0c2.19-1.49 3.16-1.18 3.16-1.18.62 1.58.23 2.75.11 3.04.74.81 1.18 1.83 1.18 3.09 0 4.41-2.69 5.38-5.25 5.67.41.36.78 1.06.78 2.14 0 1.54-.01 2.79-.01 3.17 0 .31.21.67.8.56A11.5 11.5 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5Z"
          />
        </svg>
      </template>
    </OAuthRedirectButton>
    <OAuthRedirectButton provider="facebook">
      <template #icon>
        <svg
          class="size-5"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <path
            d="M24 12.07C24 5.4 18.63 0 12 0S0 5.4 0 12.07C0 18.1 4.39 23.1 10.13 24v-8.44H7.08v-3.49h3.05V9.41c0-3.02 1.79-4.69 4.53-4.69 1.31 0 2.69.24 2.69.24v2.97h-1.52c-1.49 0-1.96.93-1.96 1.89v2.25h3.33l-.53 3.49h-2.8V24C19.61 23.1 24 18.1 24 12.07Z"
          />
        </svg>
      </template>
    </OAuthRedirectButton>
  </div>
</template>

<script setup>
import GoogleLoginButton from "./GoogleLoginButton.vue";
import OAuthRedirectButton from "./OAuthRedirectButton.vue";
import { OAUTH_PROVIDERS } from "../config/oauthProviders.js";

const hasAnyProvider =
  Boolean(import.meta.env.VITE_GOOGLE_CLIENT_ID) ||
  Object.values(OAUTH_PROVIDERS).some((provider) => provider.clientId);
</script>
