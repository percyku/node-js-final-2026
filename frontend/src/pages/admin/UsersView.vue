<template>
  <div class="max-w-6xl mx-auto">
    <div class="mb-8">
      <h3 class="text-4xl font-bold text-primary-0">會員角色管理</h3>
      <p class="mt-2 text-lg text-primary-300 leading-relaxed">
        檢視與搜尋所有會員、將一般會員升級為教練，並管理管理員帳號
      </p>
    </div>

    <div class="mb-4 flex flex-wrap items-center gap-3">
      <input
        v-model="keywordInput"
        type="search"
        maxlength="100"
        placeholder="搜尋名稱或 Email"
        aria-label="搜尋名稱或 Email"
        class="flex-1 min-w-48 px-4 py-2 border-2 border-primary-600 rounded-lg outline-none focus:border-secondary-800 bg-primary-800 text-primary-0 placeholder-primary-400"
        @keyup.enter="search"
      />
      <button
        @click="search"
        class="px-6 py-2 bg-secondary-800 text-primary-900 font-medium rounded-lg hover:opacity-90 transition-colors"
      >
        搜尋
      </button>
      <button
        v-if="keyword"
        @click="clearSearch"
        class="px-4 py-2 border-2 border-primary-600 text-primary-0 rounded-lg hover:border-primary-400 transition-colors"
      >
        清除
      </button>
      <button
        @click="openCreateAdminModal"
        class="px-6 py-2 border-2 border-secondary-800 text-secondary-800 font-medium rounded-lg hover:bg-secondary-800 hover:text-primary-900 transition-colors sm:ml-auto"
      >
        新增管理員
      </button>
    </div>

    <div class="mb-6 flex flex-wrap items-center gap-4 justify-between">
      <div class="flex items-center gap-3">
        <label for="roleFilter" class="text-primary-300 font-medium">
          角色
        </label>
        <select
          id="roleFilter"
          v-model="roleFilter"
          class="px-4 py-2 border-2 border-primary-600 rounded-lg outline-none focus:border-secondary-800 bg-primary-800 text-primary-0"
          @change="changeFilter"
        >
          <option value="">全部</option>
          <option v-for="(label, value) in ROLE_LABELS" :key="value" :value="value">
            {{ label }}
          </option>
        </select>
      </div>
      <p class="text-primary-300">
        <template v-if="keyword">「{{ keyword }}」的搜尋結果：</template>共
        {{ pagination.total }} 位會員
      </p>
    </div>

    <div
      class="bg-primary-800 rounded-lg shadow-lg overflow-x-auto border border-primary-600"
    >
      <div
        v-if="!isLoading && userList.length === 0"
        class="text-center py-12 text-primary-300"
      >
        <p class="text-lg">沒有符合條件的會員</p>
      </div>

      <table v-else class="min-w-full divide-y divide-primary-600">
        <thead class="bg-primary-700">
          <tr>
            <th
              v-for="title in ['名稱', 'Email', '角色', '註冊時間', '操作']"
              :key="title"
              scope="col"
              class="px-3 py-4 text-left text-lg font-semibold text-primary-300 whitespace-nowrap"
            >
              {{ title }}
            </th>
          </tr>
        </thead>
        <tbody class="bg-primary-800 divide-y divide-primary-600">
          <tr
            v-for="user in userList"
            :key="user.id"
            class="hover:bg-primary-700 transition-colors"
          >
            <td class="px-3 py-4 text-lg font-medium text-primary-0">
              {{ user.name }}
            </td>
            <td class="px-3 py-4 text-primary-300 break-all">
              {{ user.email }}
            </td>
            <td class="px-3 py-4 whitespace-nowrap">
              <span
                class="inline-block px-3 py-1 rounded-full text-sm font-semibold"
                :class="
                  user.role === 'USER'
                    ? 'bg-primary-600 text-primary-0'
                    : 'bg-secondary-800 text-primary-900'
                "
              >
                {{ ROLE_LABELS[user.role] ?? user.role }}
              </span>
            </td>
            <td class="px-3 py-4 text-primary-300 whitespace-nowrap">
              {{ formatLocalDateTime(user.created_at) }}
            </td>
            <td class="px-3 py-4 whitespace-nowrap">
              <button
                v-if="user.role === 'USER'"
                @click="openPromoteModal(user)"
                class="px-4 py-2 bg-secondary-800 text-primary-900 font-medium rounded-lg hover:opacity-90 transition-colors"
              >
                升級為教練
              </button>
              <button
                v-else-if="user.role === 'ADMIN' && user.id !== currentUserId"
                @click="resetPassword(user)"
                class="px-4 py-2 border-2 border-red-400 text-red-400 font-medium rounded-lg hover:bg-red-400 hover:text-primary-900 transition-colors"
              >
                重設密碼
              </button>
              <span v-else-if="user.id === currentUserId" class="text-primary-400">
                目前登入的帳號
              </span>
              <span v-else class="text-primary-400">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-if="pagination.total_pages > 1"
      class="mt-6 flex items-center justify-center gap-4"
    >
      <button
        @click="changePage(pagination.page - 1)"
        :disabled="pagination.page <= 1"
        class="px-4 py-2 border-2 border-primary-600 text-primary-0 rounded-lg hover:border-secondary-800 transition-colors disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:border-primary-600"
      >
        上一頁
      </button>
      <span class="text-primary-300">
        第 {{ pagination.page }} / {{ pagination.total_pages }} 頁
      </span>
      <button
        @click="changePage(pagination.page + 1)"
        :disabled="pagination.page >= pagination.total_pages"
        class="px-4 py-2 border-2 border-primary-600 text-primary-0 rounded-lg hover:border-secondary-800 transition-colors disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:border-primary-600"
      >
        下一頁
      </button>
    </div>

    <div
      v-if="promoteTarget"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in bg-modal-overlay"
      @click="closePromoteModal"
    >
      <div
        class="bg-primary-900 rounded-2xl shadow-2xl max-w-xl w-full p-8 relative animate-scale-in max-h-[90vh] overflow-y-auto"
        @click.stop
      >
        <div class="mb-6">
          <h3 class="text-2xl font-bold text-primary-0">升級為教練</h3>
          <p class="mt-2 text-primary-300 break-all">
            {{ promoteTarget.name }}（{{ promoteTarget.email }}）
          </p>
          <p class="mt-1 text-sm text-primary-400">
            以下資料可以先用預設值，之後由教練本人在教練後台修改。升級後對方需要重新登入。
          </p>
        </div>

        <div class="space-y-6">
          <div>
            <label
              for="promoteExperience"
              class="block text-sm font-semibold text-primary-0 mb-2"
            >
              教學經驗年資
            </label>
            <input
              id="promoteExperience"
              v-model.number="formData.experience_years"
              type="number"
              min="0"
              step="1"
              class="w-full px-4 py-2 border-2 border-primary-600 bg-primary-800 text-primary-0 rounded-lg outline-none focus:border-secondary-800 placeholder:text-primary-400"
            />
          </div>

          <div>
            <label
              for="promoteDescription"
              class="block text-sm font-semibold text-primary-0 mb-2"
            >
              教練簡介
            </label>
            <textarea
              id="promoteDescription"
              v-model="formData.description"
              rows="4"
              placeholder="專業背景、教學特色（可留空）"
              class="w-full px-4 py-2 border-2 border-primary-600 bg-primary-800 text-primary-0 rounded-lg outline-none focus:border-secondary-800 resize-none placeholder:text-primary-400"
            ></textarea>
          </div>

          <div>
            <label
              for="promoteImage"
              class="block text-sm font-semibold text-primary-0 mb-2"
            >
              教練照片網址
            </label>
            <input
              id="promoteImage"
              v-model="formData.profile_image_url"
              type="text"
              placeholder="https:// 開頭（可留空）"
              class="w-full px-4 py-2 border-2 border-primary-600 bg-primary-800 text-primary-0 rounded-lg outline-none focus:border-secondary-800 placeholder:text-primary-400"
            />
          </div>
        </div>

        <div class="mt-8 flex justify-end gap-4">
          <button
            @click="closePromoteModal"
            :disabled="isSubmitting"
            class="px-6 py-2 border-2 border-primary-600 text-primary-0 rounded-lg hover:border-primary-400 transition-colors disabled:opacity-40"
          >
            取消
          </button>
          <button
            @click="promoteUser"
            :disabled="isSubmitting"
            class="px-6 py-2 bg-secondary-800 text-primary-900 font-semibold rounded-lg hover:opacity-90 transition-colors disabled:bg-primary-600 disabled:text-primary-0 disabled:cursor-not-allowed"
          >
            確認升級
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="isCreateAdminOpen"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in bg-modal-overlay"
      @click="closeCreateAdminModal"
    >
      <div
        class="bg-primary-900 rounded-2xl shadow-2xl max-w-xl w-full p-8 relative animate-scale-in max-h-[90vh] overflow-y-auto"
        @click.stop
      >
        <div class="mb-6">
          <h3 class="text-2xl font-bold text-primary-0">新增管理員</h3>
          <p class="mt-2 text-sm text-primary-400">
            管理員只能用 Email 與密碼登入，不能使用 Google、GitHub、Facebook 登入。
          </p>
        </div>

        <form class="space-y-6" @submit.prevent="createAdmin">
          <div>
            <label
              for="adminName"
              class="block text-sm font-semibold text-primary-0 mb-2"
            >
              名稱 <span class="text-red-400">*</span>
            </label>
            <input
              id="adminName"
              v-model="adminForm.name"
              type="text"
              maxlength="50"
              autocomplete="off"
              class="w-full px-4 py-2 border-2 border-primary-600 bg-primary-800 text-primary-0 rounded-lg outline-none focus:border-secondary-800"
            />
          </div>

          <div>
            <label
              for="adminEmail"
              class="block text-sm font-semibold text-primary-0 mb-2"
            >
              Email <span class="text-red-400">*</span>
            </label>
            <input
              id="adminEmail"
              v-model="adminForm.email"
              type="email"
              autocomplete="off"
              class="w-full px-4 py-2 border-2 border-primary-600 bg-primary-800 text-primary-0 rounded-lg outline-none focus:border-secondary-800"
            />
          </div>

          <div>
            <label
              for="adminPassword"
              class="block text-sm font-semibold text-primary-0 mb-2"
            >
              密碼 <span class="text-red-400">*</span>
            </label>
            <input
              id="adminPassword"
              v-model="adminForm.password"
              type="password"
              autocomplete="new-password"
              placeholder="英文大小寫加數字，8～16 個字"
              class="w-full px-4 py-2 border-2 border-primary-600 bg-primary-800 text-primary-0 rounded-lg outline-none focus:border-secondary-800 placeholder:text-primary-400"
            />
          </div>

          <div class="flex justify-end gap-4 pt-2">
            <button
              type="button"
              @click="closeCreateAdminModal"
              :disabled="isSubmitting"
              class="px-6 py-2 border-2 border-primary-600 text-primary-0 rounded-lg hover:border-primary-400 transition-colors disabled:opacity-40"
            >
              取消
            </button>
            <button
              type="submit"
              :disabled="isSubmitting"
              class="px-6 py-2 bg-secondary-800 text-primary-900 font-semibold rounded-lg hover:opacity-90 transition-colors disabled:bg-primary-600 disabled:text-primary-0 disabled:cursor-not-allowed"
            >
              建立
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, getCurrentInstance } from "vue";
import {
  getAdminUsers,
  postAdminPromoteUser,
  postAdminCreateAdmin,
  postAdminResetPassword,
} from "../../api/index.js";
import { jwtDecode } from "jwt-decode";
import { getDataFromCookieByKey } from "../../utils/cookie.js";
import { formatLocalDateTime } from "../../utils/formatDateTime.js";
import swalHandler from "../../utils/swalHandler.js";

const ROLE_LABELS = {
  USER: "一般會員",
  COACH: "教練",
  ADMIN: "管理員",
};
const PER_PAGE = 10;

const { proxy } = getCurrentInstance();

const userList = ref([]);
const pagination = ref({ page: 1, per: PER_PAGE, total: 0, total_pages: 0 });
const roleFilter = ref("");
// keywordInput 是輸入框的內容，按下搜尋才會變成實際查詢用的 keyword
const keywordInput = ref("");
const keyword = ref("");
const isLoading = ref(true);

// 自己那一列不顯示重設密碼（後端也會擋）
const currentUserId = getCurrentUserId();

const isCreateAdminOpen = ref(false);
const adminForm = ref(defaultAdminForm());

const promoteTarget = ref(null);
const isSubmitting = ref(false);
const formData = ref(defaultForm());

function defaultForm() {
  return {
    experience_years: 0,
    description: "",
    profile_image_url: "",
  };
}

function defaultAdminForm() {
  return {
    name: "",
    email: "",
    password: "",
  };
}

function getCurrentUserId() {
  try {
    return jwtDecode(getDataFromCookieByKey("token")).id;
  } catch {
    return "";
  }
}

// 後端回的可預期錯誤（status: failed）直接顯示訊息，回傳 true 代表已處理
function showFailedMessage(error) {
  const data = error.response?.data;
  if (data?.status === "failed") {
    swalHandler(proxy.$swal, data.message);
    return true;
  }
  return false;
}

async function getUserList(page = pagination.value.page) {
  isLoading.value = true;
  try {
    const { data } = await getAdminUsers({
      page,
      per: PER_PAGE,
      role: roleFilter.value,
      keyword: keyword.value,
    });

    // 升級後這一頁可能空了（例如篩選「一般會員」的最後一頁），退回最後一頁重查
    if (data.users.length === 0 && page > 1) {
      await getUserList(Math.max(data.pagination.total_pages, 1));
      return;
    }

    userList.value = data.users;
    pagination.value = data.pagination;
  } catch (error) {
    if (showFailedMessage(error)) {
      return;
    }
    throw new Error(`[getUserList] error : ${error.message}`);
  } finally {
    isLoading.value = false;
  }
}

function changeFilter() {
  getUserList(1);
}

function search() {
  keyword.value = keywordInput.value.trim();
  getUserList(1);
}

function clearSearch() {
  keywordInput.value = "";
  keyword.value = "";
  getUserList(1);
}

function changePage(page) {
  if (page < 1 || page > pagination.value.total_pages) {
    return;
  }
  getUserList(page);
}

function openPromoteModal(user) {
  formData.value = defaultForm();
  promoteTarget.value = user;
}

function closePromoteModal() {
  if (isSubmitting.value) {
    return;
  }
  promoteTarget.value = null;
}

async function promoteUser() {
  // 清空數字欄位時 v-model.number 會給空字串，後端只收整數
  if (!Number.isInteger(formData.value.experience_years)) {
    swalHandler(proxy.$swal, "教學經驗年資請填 0 以上的整數");
    return;
  }

  isSubmitting.value = true;
  try {
    const { status } = await postAdminPromoteUser(
      promoteTarget.value.id,
      formData.value
    );

    if (status === "success") {
      promoteTarget.value = null;
      swalHandler(proxy.$swal, "會員升級成功");
      getUserList();
    }
  } catch (error) {
    if (showFailedMessage(error)) {
      // 例如另一位管理員已經先升級了，列表重新整理成最新狀態
      if (error.response.status === 409) {
        promoteTarget.value = null;
        getUserList();
      }
      return;
    }
    throw new Error(`[promoteUser] error : ${error.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function openCreateAdminModal() {
  adminForm.value = defaultAdminForm();
  isCreateAdminOpen.value = true;
}

function closeCreateAdminModal() {
  if (isSubmitting.value) {
    return;
  }
  isCreateAdminOpen.value = false;
}

async function createAdmin() {
  isSubmitting.value = true;
  try {
    const { status } = await postAdminCreateAdmin(adminForm.value);

    if (status === "success") {
      isCreateAdminOpen.value = false;
      swalHandler(proxy.$swal, "管理員建立成功");
      getUserList(1);
    }
  } catch (error) {
    if (showFailedMessage(error)) {
      return;
    }
    throw new Error(`[createAdmin] error : ${error.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

async function resetPassword(user) {
  const { isConfirmed } = await proxy.$swal.fire({
    title: "重設密碼",
    text: `確定要把「${user.name}」（${user.email}）的密碼重設為 Admin123 嗎？對方會被登出。`,
    icon: "warning",
    showCancelButton: true,
    confirmButtonText: "確定重設",
    cancelButtonText: "取消",
  });
  if (!isConfirmed) {
    return;
  }

  try {
    const { status } = await postAdminResetPassword(user.id);

    if (status === "success") {
      swalHandler(proxy.$swal, "密碼重設成功");
    }
  } catch (error) {
    if (showFailedMessage(error)) {
      return;
    }
    throw new Error(`[resetPassword] error : ${error.message}`);
  }
}

onMounted(() => {
  getUserList(1);
});
</script>
