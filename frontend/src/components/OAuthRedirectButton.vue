<template>
  <!-- 沒設定該平台的 client id 時不顯示 -->
  <button
    v-if="config?.clientId"
    type="button"
    class="w-full max-w-[400px] mx-auto h-10 flex items-center justify-center gap-3 rounded bg-[#202124] text-sm font-medium text-white border border-primary-600 hover:bg-[#303134] transition-colors"
    @click="redirectToProvider"
  >
    <slot name="icon"></slot>
    <span>使用 {{ config.label }} 帳號繼續</span>
  </button>
</template>

<script setup>
import {
  OAUTH_PROVIDERS,
  getRedirectUri,
  getStateKey,
} from "../config/oauthProviders.js";

const props = defineProps({
  provider: {
    type: String,
    required: true,
  },
});

const config = OAUTH_PROVIDERS[props.provider];

function redirectToProvider() {
  // state 用來確認跳回來的請求是這個瀏覽器自己發起的，callback 頁會比對
  const state = crypto.randomUUID();
  sessionStorage.setItem(getStateKey(props.provider), state);

  const params = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: getRedirectUri(props.provider),
    scope: config.scope,
    state,
  });
  window.location.assign(`${config.authorizeUrl}?${params}`);
}
</script>
