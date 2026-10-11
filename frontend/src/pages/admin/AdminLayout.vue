<template>
  <div class="flex flex-col lg:flex-row min-h-screen gap-0 lg:gap-8 bg-primary-900">
    <aside
      class="bg-primary-800 border-b lg:border-b-0 lg:border-r border-primary-700 p-4 lg:p-8 w-full lg:w-64 lg:min-w-64"
    >
      <nav>
        <h3
          class="text-3xl font-bold text-secondary-800 lg:w-full lg:text-center mb-3 lg:mb-6"
        >
          後台管理系統
        </h3>
        <ul
          class="flex flex-row lg:flex-col gap-1 lg:gap-2 overflow-x-auto lg:overflow-x-visible"
        >
          <li
            v-for="item in sidebar"
            :key="item.title"
            class="flex-shrink-0 lg:flex-shrink w-auto lg:w-full"
          >
            <router-link
              :to="item.path"
              class="block px-4 py-3.5 text-primary-300 text-base font-medium rounded-lg transition-all duration-300 ease-in-out whitespace-nowrap lg:whitespace-normal hover:bg-secondary-800/10 hover:text-secondary-800 hover:translate-x-0 lg:hover:translate-x-1.5 hover:-translate-y-0.5 lg:hover:translate-y-0 active:scale-95 [&.router-link-active]:bg-secondary-800 [&.router-link-active]:text-primary-900 [&.router-link-active]:font-semibold [&.router-link-active]:shadow-[0_2px_8px_rgba(243,106,14,0.3)] [&.router-link-active]:hover:bg-secondary-800/90"
            >
              {{ item.title }}
            </router-link>
          </li>
        </ul>
      </nav>
    </aside>

    <main class="flex-1 p-4 lg:p-8 overflow-y-auto bg-primary-900">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useUserStore } from "../../stores/user.js";

const { role } = storeToRefs(useUserStore());

const sidebar = computed(() => {
  const items = [
    {
      title: "數據儀表板",
      path: "/admin/dashboard",
    },
    {
      title: "教練技能管理",
      path: "/admin/skills",
    },
  ];

  if (role.value !== "ADMIN") {
    // 替自己升級成教練；管理員不能兼任教練，所以不顯示
    items.push({
      title: "教練資格管理",
      path: "/admin/promote-trainer",
    });
  }

  // 只有管理員看得到；路由與後端 API 也各自檢查角色
  if (role.value === "ADMIN") {
    items.push(
      {
        title: "會員角色管理",
        path: "/admin/users",
      },
      {
        title: "個人資料與密碼",
        path: "/admin/profile",
      }
    );
  }

  return items;
});
</script>
