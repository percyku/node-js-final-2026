<template>
  <!-- 沒設定 VITE_GOOGLE_CLIENT_ID 時整塊不顯示 -->
  <div v-if="clientId" class="space-y-4">
    <div class="flex items-center gap-3 text-sm text-primary-400">
      <span class="h-px flex-1 bg-primary-600"></span>
      <span>或</span>
      <span class="h-px flex-1 bg-primary-600"></span>
    </div>
    <div ref="buttonContainer" class="flex justify-center min-h-11"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, getCurrentInstance } from "vue";
import { useRouter } from "vue-router";
import { postGoogleLogin } from "../api/index.js";
import swalHandler from "../utils/swalHandler.js";
import { handleLoginSuccess } from "../utils/loginHandler.js";

const GSI_SRC = "https://accounts.google.com/gsi/client";
// Google 按鈕寬度只接受 200 ~ 400 px
const MIN_WIDTH = 200;
const MAX_WIDTH = 400;

const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

const { proxy } = getCurrentInstance();
const router = useRouter();
const buttonContainer = ref(null);

// 登入頁與註冊頁共用同一份 script，只載入一次
let gsiPromise;
function loadGsi() {
  if (window.google?.accounts?.id) {
    return Promise.resolve();
  }
  if (!gsiPromise) {
    gsiPromise = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = GSI_SRC;
      script.async = true;
      script.defer = true;
      script.onload = resolve;
      script.onerror = () => {
        gsiPromise = undefined;
        reject(new Error("Google 登入元件載入失敗"));
      };
      document.head.appendChild(script);
    });
  }
  return gsiPromise;
}

// Google 回傳 ID token 後，交給後端驗證並換成本站的 JWT
async function onCredential({ credential }) {
  try {
    const { data } = await postGoogleLogin({ credential });
    handleLoginSuccess(data, router);
  } catch (error) {
    const message = error.response?.data?.message ?? "Google 登入失敗，請稍後再試";
    swalHandler(proxy.$swal, message);
  }
}

onMounted(async () => {
  if (!clientId) {
    return;
  }

  try {
    await loadGsi();
  } catch (error) {
    console.error(error.message);
    return;
  }

  // 等待載入期間使用者可能已經離開頁面
  if (!buttonContainer.value) {
    return;
  }

  window.google.accounts.id.initialize({
    client_id: clientId,
    callback: onCredential,
  });

  const width = Math.min(
    MAX_WIDTH,
    Math.max(MIN_WIDTH, buttonContainer.value.offsetWidth),
  );
  window.google.accounts.id.renderButton(buttonContainer.value, {
    type: "standard",
    theme: "filled_black",
    size: "large",
    text: "continue_with",
    shape: "rectangular",
    logo_alignment: "center",
    locale: "zh_TW",
    width,
  });
});
</script>
