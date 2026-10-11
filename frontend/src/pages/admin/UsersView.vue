<template>
  <div class="max-w-6xl mx-auto">
    <div class="mb-8">
      <h3 class="text-4xl font-bold text-primary-0">會員角色管理</h3>
      <p class="mt-2 text-lg text-primary-300 leading-relaxed">
        檢視所有會員，並將一般會員升級為教練
      </p>
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
      <p class="text-primary-300">共 {{ pagination.total }} 位會員</p>
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
  </div>
</template>

<script setup>
import { ref, onMounted, getCurrentInstance } from "vue";
import { getAdminUsers, postAdminPromoteUser } from "../../api/index.js";
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
const isLoading = ref(true);

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

onMounted(() => {
  getUserList(1);
});
</script>
