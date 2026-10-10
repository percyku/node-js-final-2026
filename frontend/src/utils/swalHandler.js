const swalCodes = {
  密碼不一致: {
    title: "密碼不一致",
    text: "請再次輸入密碼",
    state: "error",
  },
  註冊成功: {
    title: "註冊成功",
    text: "歡迎加入我們的健身行列，請至登入頁面登入或等3秒自動轉跳至登入頁面",
    state: "success",
  },
  課程取消成功: {
    title: "成功",
    text: "已成功取消課程",
    state: "success",
  },
  資料已更新: {
    title: "成功",
    text: "已成功更新資料",
    state: "success",
  },
  密碼已更新: {
    title: "成功",
    text: "已成功更新密碼",
    state: "success",
  },
  密碼已設定: {
    title: "成功",
    text: "已成功設定密碼，之後可以用 Email 與密碼登入",
    state: "success",
  },
  請選擇圖片檔案: {
    title: "錯誤",
    text: "請選擇圖片檔案",
    state: "error",
  },
  圖片太大: {
    title: "錯誤",
    text: "圖片大小不能超過 5MB",
    state: "error",
  },
  新增課程成功: {
    title: "成功",
    text: "已成功新增課程",
    state: "success",
  },
  更新課程成功: {
    title: "成功",
    text: "已成功更新課程",
    state: "success",
  },
  新增技能成功: {
    title: "成功",
    text: "已成功新增技能",
    state: "success",
  },
  刪除技能成功: {
    title: "成功",
    text: "已成功刪除技能",
    state: "success",
  },
  升級教練成功: {
    title: "成功",
    text: "恭喜！您已成功升級為教練，請重新登入獲得更多教練權限服務或等3秒自動轉跳至登入頁面",
    state: "success",
  },
  圖片載入失敗: {
    title: "錯誤",
    text: "圖片載入失敗，請檢查網址是否正確",
    state: "error",
  },
};

export default (swal, errorCode) => {
  if (swalCodes[errorCode]) {
    swal.fire({
      title: swalCodes[errorCode].title,
      text: swalCodes[errorCode].text,
      icon: swalCodes[errorCode].state,
    });
  } else {
    swal.fire({
      title: "錯誤",
      text: errorCode,
      icon: "error",
    });
  }
};
