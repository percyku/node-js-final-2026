<template>
  <div class="flex min-h-screen items-center justify-center bg-primary-900">
    <p class="text-xl text-primary-300">登入中，請稍候…</p>
  </div>
</template>

<script setup>
import { onMounted, getCurrentInstance } from "vue";
import { useRoute, useRouter } from "vue-router";
import swalHandler from "../../../utils/swalHandler.js";
import { handleLoginSuccess } from "../../../utils/loginHandler.js";
import {
  OAUTH_PROVIDERS,
  getRedirectUri,
  getStateKey,
} from "../../../config/oauthProviders.js";

const { proxy } = getCurrentInstance();
const route = useRoute();
const router = useRouter();

function backToLogin(message) {
  if (message) {
    swalHandler(proxy.$swal, message);
  }
  router.replace("/login");
}

onMounted(async () => {
  const { provider } = route.params;
  const { code, state, error } = route.query;
  const config = OAUTH_PROVIDERS[provider];

  if (!config) {
    backToLogin();
    return;
  }

  // 授權碼只能用一次，所以 state 取出後立刻移除：重新整理或按上一頁回到這頁時不會重送
  const stateKey = getStateKey(provider);
  const expectedState = sessionStorage.getItem(stateKey);
  sessionStorage.removeItem(stateKey);

  if (!expectedState) {
    backToLogin();
    return;
  }

  // 使用者在授權頁按了取消
  if (error) {
    backToLogin(`已取消 ${config.label} 登入`);
    return;
  }

  // state 對不上代表這次跳轉不是從本站的登入按鈕發起的
  if (!code || state !== expectedState) {
    backToLogin(`${config.label} 登入驗證失敗，請重新登入`);
    return;
  }

  try {
    const { data } = await config.login({
      code,
      redirect_uri: getRedirectUri(provider),
    });
    handleLoginSuccess(data, router);
  } catch (err) {
    backToLogin(
      err.response?.data?.message ?? `${config.label} 登入失敗，請稍後再試`,
    );
  }
});
</script>
