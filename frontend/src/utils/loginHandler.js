import { jwtDecode } from "jwt-decode";
import { setKeyFromCookie } from "./cookie.js";
import { useUserStore } from "../stores/user.js";

// 登入成功後的共用處理：存 token、更新使用者狀態、依角色導頁
// 密碼登入與各種第三方登入的回應格式相同（data.token、data.user.name），所以共用這一段
export function handleLoginSuccess(data, router) {
  const { role, exp } = jwtDecode(data.token);

  setKeyFromCookie("token", data.token, exp);

  const { setCurrentUser } = useUserStore();
  setCurrentUser({
    name: data.user.name,
    role,
  });

  // 根據角色導向不同頁面
  if (role === "COACH") {
    router.push("/coach/profile");
  } else if (role === "USER") {
    router.push("/user/dashboard");
  }
}
