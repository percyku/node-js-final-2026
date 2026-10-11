<template>
  <div class="min-h-screen w-full py-8 bg-primary-900">
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 pb-12">
      <div class="mb-8">
        <h3 class="text-4xl font-bold">
          <span class="text-secondary-800">編輯</span>
          <span class="text-primary-0">教練資料</span>
        </h3>
        <p class="mt-2 text-lg text-primary-300 leading-relaxed">
          管理您的教練檔案與專業資訊
        </p>
      </div>

      <div class="flex flex-col lg:flex-row gap-8">
        <div class="lg:w-1/3">
          <div class="flex flex-col items-center">
            <div class="mb-4">
              <label class="block text-lg font-semibold text-primary-0 mb-3">
                教練照片網址 <span class="text-red-400">*</span>
              </label>
              <input
                v-model="profileData.profile_image_url"
                type="text"
                placeholder="請輸入圖片網址 (例如：https://example.com/image.jpg)"
                class="w-full px-4 py-3 border-2 border-primary-600 bg-primary-700 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800 transition-all duration-300 text-lg mb-4 placeholder-primary-400"
              />
              <div
                v-if="profileData.profile_image_url"
                class="w-48 h-48 mx-auto rounded-full overflow-hidden bg-primary-700 border-4 border-secondary-800/20"
              >
                <img
                  :src="profileData.profile_image_url"
                  alt="教練照片預覽"
                  class="w-full h-full object-cover"
                  @error="handleImageError"
                />
              </div>
              <div
                v-else
                class="w-48 h-48 mx-auto rounded-full bg-primary-700 border-4 border-secondary-800/20 flex items-center justify-center text-primary-400"
              >
                <svg class="w-20 h-20" fill="currentColor" viewBox="0 0 20 20">
                  <path
                    fill-rule="evenodd"
                    d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z"
                    clip-rule="evenodd"
                  />
                </svg>
              </div>
            </div>
          </div>
        </div>

        <div class="lg:w-2/5">
          <div class="space-y-6">
            <div>
              <p class="text-3xl font-bold text-primary-0">{{ name }} 教練</p>
            </div>

            <div>
              <label
                for="experience"
                class="block text-lg font-semibold text-primary-0 mb-2"
              >
                教練資歷（年）<span class="text-red-400">*</span>
              </label>
              <input
                id="experience"
                v-model.number="profileData.experience_years"
                type="number"
                min="0"
                class="w-full px-4 py-3 border-2 border-primary-600 bg-primary-700 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800 transition-all duration-300 text-lg placeholder-primary-400"
                placeholder="請輸入資歷年數"
              />
            </div>
            <div>
              <label
                for="bio"
                class="block text-lg font-semibold text-primary-0 mb-2"
              >
                教練簡介 <span class="text-red-400">*</span>
              </label>
              <textarea
                id="bio"
                v-model="profileData.description"
                rows="5"
                class="w-full px-4 py-3 border-2 border-primary-600 bg-primary-700 text-primary-0 rounded-lg focus:outline-none focus:border-secondary-800 transition-all duration-300 text-lg resize-none placeholder-primary-400"
                placeholder="請介紹您的專業背景與教學理念"
              ></textarea>
            </div>

            <div>
              <label class="block text-lg font-semibold text-primary-0 mb-2">
                專長
              </label>
              <div class="space-y-3">
                <div v-if="skillList.length > 0" class="flex flex-wrap gap-4">
                  <label
                    v-for="skill in skillList"
                    :key="skill.id"
                    class="flex items-center gap-3 px-4 py-3 border-2 rounded-lg cursor-pointer transition-all duration-200"
                    :class="
                      profileData.skill_ids.includes(skill.id)
                        ? 'border-secondary-800 bg-secondary-800/10'
                        : 'border-primary-600 hover:border-secondary-800/50 hover:bg-primary-700'
                    "
                  >
                    <input
                      type="checkbox"
                      :value="skill.id"
                      :checked="profileData.skill_ids.includes(skill.id)"
                      @change="functionToggleSkill(skill.id)"
                      class="w-5 h-5 accent-secondary-800 bg-primary-700 border-0 rounded focus:ring-0 focus:ring-offset-0 outline-none"
                    />
                    <span
                      class="text-base font-medium text-primary-0 whitespace-nowrap"
                    >
                      {{ skill.name }}
                    </span>
                  </label>
                </div>
                <div
                  v-else
                  class="text-center py-8 text-primary-400 bg-primary-700 rounded-lg"
                >
                  載入專長選項中...
                </div>
              </div>
            </div>

            <div class="pt-4">
              <button
                @click="updateCoachProfile"
                class="w-full px-8 py-4 bg-secondary-800 text-primary-900 text-lg font-medium rounded-lg hover:bg-secondary-700 active:bg-secondary-600 focus:outline-none focus:ring-4 focus:ring-secondary-800/30 transition-all duration-300 shadow-lg hover:shadow-xl"
              >
                儲存
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, getCurrentInstance } from "vue";
import { getCoach, getSkills, putCoach } from "../../api/index.js";
import { storeToRefs } from "pinia";
import { useUserStore } from "../../stores/user.js";
import swalHandler from "../../utils/swalHandler.js";

const { proxy } = getCurrentInstance();
const { name } = storeToRefs(useUserStore());

const profileData = ref({
  id: "",
  profile_image_url: "",
  experience_years: 0,
  description: "",
  skill_ids: [],
});

const skillList = ref([]);

async function getCoachProfile() {
  try {
    const { data } = await getCoach();

    profileData.value = data;
  } catch (error) {
    let msg = error.message;

    if (Object.hasOwn(error.response, "data")) {
      const { message } = error.response.data;
      msg = message;
    }

    throw new Error(`[getCoachProfile] error : ${msg}`);
  }
}

async function getSkillList() {
  try {
    const { data } = await getSkills();
    skillList.value = data;
  } catch (error) {
    let msg = error.message;

    if (Object.hasOwn(error.response, "data")) {
      const { message } = error.response.data;
      msg = message;
    }

    throw new Error(`[getSkillList] error : ${msg}`);
  }
}

function functionToggleSkill(skillId) {
  const index = profileData.value.skill_ids.indexOf(skillId);
  if (index > -1) {
    profileData.value.skill_ids.splice(index, 1);
  } else {
    profileData.value.skill_ids.push(skillId);
  }
}

function handleImageError() {
  swalHandler(proxy.$swal, "圖片載入失敗，請檢查網址是否正確");
}

// 後端對這四個欄位只回一句「欄位未填寫正確」，送出前先指出是哪一欄。
// 由管理員升級的教練，簡介、照片、技能一開始都是空的
function findInvalidField() {
  const { profile_image_url, experience_years, description, skill_ids } =
    profileData.value;

  if (!profile_image_url || !profile_image_url.startsWith("https")) {
    return "請填寫 https 開頭的教練照片網址";
  }
  if (!Number.isInteger(experience_years) || experience_years < 0) {
    return "教練資歷請填 0 以上的整數";
  }
  if (!description || !description.trim()) {
    return "請填寫教練簡介";
  }
  if (!Array.isArray(skill_ids) || skill_ids.length === 0) {
    return "請至少選擇一項專長";
  }
  return "";
}

// 儲存教練資料
async function updateCoachProfile() {
  const invalidMessage = findInvalidField();
  if (invalidMessage) {
    swalHandler(proxy.$swal, invalidMessage);
    return;
  }

  try {
    const { status, data } = await putCoach(profileData.value);
    if (status === "success") {
      profileData.value = data;
      swalHandler(proxy.$swal, "資料已更新");
    }
  } catch (error) {
    let msg = error.message;

    if (Object.hasOwn(error.response, "data")) {
      // 這裡原本少解構 status，判斷永遠不成立，儲存失敗時畫面上沒有任何提示
      const { status, message } = error.response.data;
      msg = message;

      if (status === "failed") {
        swalHandler(proxy.$swal, message);
        return;
      }
    }

    throw new Error(`[updateCoachProfile] error : ${msg}`);
  }
}

onMounted(() => {
  getSkillList();
  getCoachProfile();
});
</script>
