<template>
  <div class="flex min-h-screen bg-primary-900">
    <div class="flex w-full md:w-1/2 flex-col justify-center px-8 py-12">
      <div class="mx-auto w-full max-w-md space-y-6">
        <h6 class="text-4xl font-black text-primary-0 text-center">註冊</h6>
        <p class="text-xl text-center text-primary-300">還差最後一步！加入我們健身行列</p>
        <div class="space-y-4">
          <div class="flex flex-col gap-2">
            <label for="email" class="text-lg font-medium text-primary-0"
              >電子郵件 <span class="text-secondary-800">*</span></label
            >
            <input
              id="email"
              type="email"
              v-model="user.email"
              @keyup.enter="signup"
              class="px-4 py-2 bg-primary-800 border border-primary-600 rounded-lg focus:outline-none focus:border-secondary-800 text-primary-0"
            />
          </div>

          <div class="flex flex-col gap-2">
            <label for="password" class="text-lg font-medium text-primary-0"
              >密碼 <span class="text-secondary-800">*</span></label
            >
            <input
              id="password"
              type="password"
              v-model="user.password"
              @keyup.enter="signup"
              class="px-4 py-2 bg-primary-800 border border-primary-600 rounded-lg focus:outline-none focus:border-secondary-800 text-primary-0"
            />
          </div>

          <div class="flex flex-col gap-2">
            <label
              for="confirmPassword"
              class="text-lg font-medium text-primary-0"
              >再次輸入密碼 <span class="text-secondary-800">*</span></label
            >
            <input
              id="confirmPassword"
              type="password"
              v-model="user.confirmPassword"
              @keyup.enter="signup"
              class="px-4 py-2 bg-primary-800 border border-primary-600 rounded-lg focus:outline-none focus:border-secondary-800 text-primary-0"
            />
          </div>

          <div class="flex flex-col gap-2">
            <label for="nickname" class="text-lg font-medium text-primary-0"
              >暱稱 <span class="text-secondary-800">*</span></label
            >
            <input
              id="nickname"
              type="text"
              v-model="user.name"
              @keyup.enter="signup"
              class="px-4 py-2 bg-primary-800 border border-primary-600 rounded-lg focus:outline-none focus:border-secondary-800 text-primary-0"
            />
          </div>

          <button
            @click="signup"
            class="w-full bg-secondary-800 text-primary-900 text-lg py-2 mt-2 rounded-lg font-medium hover:opacity-90 transition-opacity"
          >
            註冊
          </button>

          <GoogleLoginButton />

          <p class="text-center text-sm text-primary-400">
            已有會員?
            <router-link
              to="/login"
              class="text-secondary-800 font-medium hover:underline"
              >前往登入</router-link
            >
          </p>
        </div>
      </div>
    </div>

    <div class="hidden md:flex md:w-1/2 md:py-16 md:pr-8">
      <img
        src="https://images.unsplash.com/photo-1571902943202-507ec2618e8f?q=80&w=1200&auto=format&fit=crop"
        alt="進入健身房"
        class="w-full object-cover rounded-lg"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance } from "vue";
import { useRouter } from "vue-router";
import { postSignup } from "../../../api/index.js";
import swalHandler from "../../../utils/swalHandler.js";
import GoogleLoginButton from "../../../components/GoogleLoginButton.vue";

const { proxy } = getCurrentInstance();
const router = useRouter();

const user = ref({
  email: "",
  password: "",
  confirmPassword: "",
  name: "",
});

// 驗證密碼是否一致
function validatePasswordMatch() {
  if (user.value.password !== user.value.confirmPassword) {
    swalHandler(proxy.$swal, "密碼不一致");
    return false;
  }
  return true;
}

async function signup() {
  // 驗證密碼是否一致
  if (!validatePasswordMatch()) {
    return;
  }

  try {
    const { status } = await postSignup(user.value);
    if (status === "success") {
      swalHandler(proxy.$swal, "註冊成功");

      setTimeout(() => {
        proxy.$swal.close();
        router.push("/login");
      }, 3000);
    }
  } catch (error) {
    let msg = error.message;

    if (Object.hasOwn(error.response, "data")) {
      const { status, message } = error.response.data;
      msg = message;

      if (status === "failed") {
        swalHandler(proxy.$swal, message);
        return;
      }
    }

    throw new Error(`[getCoachCourseList] error : ${msg}`);
  }
}
</script>
