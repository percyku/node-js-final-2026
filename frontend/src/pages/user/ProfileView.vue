<template>
  <div class="min-h-screen w-full py-8 bg-primary-900">
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 pb-12">
      <div class="mb-8">
        <h3 class="text-4xl font-bold">
          <span class="text-secondary-800">帳戶</span>
          <span class="text-primary-0">設定</span>
        </h3>
        <p class="mt-2 text-lg text-primary-300 leading-relaxed">
          管理您的個人資料與密碼設定
        </p>
      </div>

      <div
        class="bg-primary-800 rounded-lg shadow-lg border border-primary-600"
      >
        <div class="border-b border-primary-600 bg-primary-800 rounded-t-lg">
          <nav class="flex -mb-px">
            <button
              @click="activeTab = 'profile'"
              :class="[
                'px-6 py-4 text-base font-medium border-b-2 transition-colors duration-200 rounded-tl-lg',
                activeTab === 'profile'
                  ? 'border-secondary-800 text-secondary-800'
                  : 'border-transparent text-primary-300 hover:text-primary-0 hover:border-primary-400',
              ]"
            >
              <div class="flex items-center space-x-2">
                <svg
                  class="w-5 h-5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                  />
                </svg>
                <span>個人資料</span>
              </div>
            </button>
            <button
              @click="activeTab = 'password'"
              :class="[
                'px-6 py-4 text-base font-medium border-b-2 transition-colors duration-200',
                activeTab === 'password'
                  ? 'border-secondary-800 text-secondary-800'
                  : 'border-transparent text-primary-300 hover:text-primary-0 hover:border-primary-400',
              ]"
            >
              <div class="flex items-center space-x-2">
                <svg
                  class="w-5 h-5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
                  />
                </svg>
                <span>{{ hasPassword ? "修改密碼" : "設定密碼" }}</span>
              </div>
            </button>
          </nav>
        </div>

        <div class="p-6">
          <div v-show="activeTab === 'profile'" class="space-y-6">
            <div>
              <label
                for="email"
                class="block text-base font-medium text-primary-0 mb-2"
              >
                Email (無法修改)
              </label>
              <input
                type="email"
                id="email"
                v-model="profileForm.email"
                readonly
                disabled
                class="w-full px-4 py-2 border border-primary-600 rounded-lg bg-primary-700 text-primary-400 cursor-not-allowed pointer-events-none"
                placeholder="請輸入電子郵件"
              />
            </div>

            <div>
              <label
                for="name"
                class="block text-base font-medium text-primary-0 mb-2"
              >
                暱稱 <span class="text-red-400">*</span>
              </label>
              <input
                type="text"
                id="name"
                v-model="profileForm.name"
                required
                class="w-full px-4 py-2 border border-primary-600 bg-primary-700 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800 transition duration-200 placeholder-primary-400"
                placeholder="請輸入暱稱"
              />
            </div>

            <div class="flex justify-end pt-4">
              <button
                @click="updateProfile"
                class="px-8 py-3 bg-secondary-800 text-primary-900 font-medium rounded-lg hover:bg-secondary-700 active:bg-secondary-600 shadow-md hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-secondary-800 focus:ring-offset-2 focus:ring-offset-primary-900 transition-all duration-200"
              >
                儲存變更
              </button>
            </div>
          </div>

          <div v-show="activeTab === 'password'" class="space-y-6">
            <p v-if="!hasPassword" class="text-base text-primary-300">
              這個帳號目前沒有密碼，設定後就能用 Email
              與密碼登入。為了確認是本人操作，需要在登入後 5
              分鐘內完成，逾時請重新登入。
            </p>
            <div v-if="hasPassword">
              <label
                for="password"
                class="block text-base font-medium text-primary-0 mb-2"
              >
                請輸入舊密碼 <span class="text-red-400">*</span>
              </label>
              <input
                type="password"
                id="password"
                v-model="passwordForm.password"
                required
                class="w-full px-4 py-2 border border-primary-600 bg-primary-700 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800 transition duration-200 placeholder-primary-400"
                placeholder="請輸入舊密碼"
              />
            </div>

            <div>
              <label
                for="new_password"
                class="block text-base font-medium text-primary-0 mb-2"
              >
                新密碼 <span class="text-red-400">*</span>
              </label>
              <input
                type="password"
                id="new_password"
                v-model="passwordForm.new_password"
                required
                class="w-full px-4 py-2 border border-primary-600 bg-primary-700 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800 transition duration-200 placeholder-primary-400"
                placeholder="請輸入新密碼"
              />
            </div>

            <div>
              <label
                for="confirm_new_password"
                class="block text-base font-medium text-primary-0 mb-2"
              >
                再次輸入新密碼 <span class="text-red-400">*</span>
              </label>
              <input
                type="password"
                id="confirm_new_password"
                v-model="passwordForm.confirm_new_password"
                required
                class="w-full px-4 py-2 border border-primary-600 bg-primary-700 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800 transition duration-200 placeholder-primary-400"
                placeholder="請再次輸入新密碼"
              />
            </div>

            <div class="flex justify-end pt-4">
              <button
                @click="updatePassword"
                class="px-8 py-3 bg-secondary-800 text-primary-900 font-medium rounded-lg hover:bg-secondary-700 active:bg-secondary-600 shadow-md hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-secondary-800 focus:ring-offset-2 focus:ring-offset-primary-900 transition-all duration-200"
              >
                {{ hasPassword ? "修改密碼" : "設定密碼" }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, getCurrentInstance } from "vue";
import { useUserStore } from "../../stores/user.js";
const { setUserName } = useUserStore();
import {
  getUserProfile,
  putUserProfile,
  putUserPassword,
} from "../../api/index.js";
import swalHandler from "../../utils/swalHandler.js";

const { proxy } = getCurrentInstance();

const activeTab = ref("profile");
const profileForm = ref({
  email: "",
  name: "",
});
const passwordForm = ref({
  password: "",
  new_password: "",
  confirm_new_password: "",
});
// 第三方登入建立的帳號沒有密碼，這時不需要舊密碼。
// Node 後端的回應沒有 has_password，一律當成有密碼
const hasPassword = computed(() => profileForm.value.has_password !== false);

async function updateProfile() {
  try {
    const { status } = await putUserProfile({
      name: profileForm.value.name,
    });
    if (status === "success") {
      getProfile();
      swalHandler(proxy.$swal, "資料已更新");
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

    throw new Error(`[updateProfile] error : ${msg}`);
  }
}

async function updatePassword() {
  try {
    const { password, ...newPasswords } = passwordForm.value;
    const { status } = await putUserPassword(
      hasPassword.value ? { password, ...newPasswords } : newPasswords
    );
    if (status === "success") {
      swalHandler(proxy.$swal, hasPassword.value ? "密碼已更新" : "密碼已設定");
      passwordForm.value = {
        password: "",
        new_password: "",
        confirm_new_password: "",
      };
      // 設定完成後帳號就有密碼了，重新取得才會顯示舊密碼欄位
      getProfile();
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

    throw new Error(`[updatePassword] error : ${msg}`);
  }
}

async function getProfile() {
  try {
    const { data } = await getUserProfile();
    profileForm.value = data.user;
    setUserName(data.user.name);
  } catch (error) {
    let msg = error.message;

    if (Object.hasOwn(error.response, "data")) {
      const { message } = error.response.data;
      msg = message;
    }

    throw new Error(`[getProfile] error : ${msg}`);
  }
}

onMounted(() => {
  getProfile();
});
</script>
