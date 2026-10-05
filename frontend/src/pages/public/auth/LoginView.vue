<template>
  <div class="flex min-h-screen bg-primary-900">
    <div class="flex w-full md:w-1/2 flex-col justify-center px-8 py-12">
      <div class="mx-auto w-full max-w-md space-y-6">
        <h6 class="text-4xl font-black text-primary-0 text-center">登入</h6>
        <p class="text-xl text-primary-300 text-center">請出入您的帳號和密碼</p>
        <form @submit.prevent="login" class="space-y-4">
          <div class="flex flex-col gap-2">
            <label for="email" class="text-lg font-medium text-primary-300"
              >電子郵件 <span class="text-secondary-800">*</span></label
            >
            <input
              id="email"
              type="email"
              v-model="user.email"
              class="px-4 py-2 bg-primary-800 border border-primary-600 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800"
            />
          </div>

          <div class="flex flex-col gap-2">
            <label for="password" class="text-lg font-medium text-primary-300"
              >密碼 <span class="text-secondary-800">*</span></label
            >
            <input
              id="password"
              type="password"
              v-model="user.password"
              class="px-4 py-2 bg-primary-800 border border-primary-600 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800"
            />
          </div>

          <button
            type="submit"
            class="w-full bg-secondary-800 text-primary-900 text-lg py-2 mt-2 rounded-lg font-medium hover:opacity-90 transition-opacity"
          >
            登入
          </button>

          <SocialLoginButtons />

          <p class="text-center text-sm text-primary-400">
            還沒有帳號?
            <router-link
              to="/signup"
              class="text-secondary-800 font-medium hover:underline"
              >立即註冊</router-link
            >
          </p>
        </form>
      </div>
    </div>

    <div class="hidden md:flex md:w-1/2 md:py-16 md:pr-8">
      <img
        src="https://images.unsplash.com/photo-1593079831268-3381b0db4a77?q=80&w=1200&auto=format&fit=crop"
        alt="健身房環境"
        class="w-full object-cover rounded-lg"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance } from "vue";
import { useRouter } from "vue-router";
import { postLogin } from "../../../api/index.js";
import swalHandler from "../../../utils/swalHandler.js";
import { handleLoginSuccess } from "../../../utils/loginHandler.js";
import SocialLoginButtons from "../../../components/SocialLoginButtons.vue";

const { proxy } = getCurrentInstance();
const router = useRouter();

const user = ref({
  email: "",
  password: "",
});

async function login() {
  try {
    const { data } = await postLogin(user.value);

    handleLoginSuccess(data, router);
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
