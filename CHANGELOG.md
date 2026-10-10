# 更新紀錄

記錄 Spring Boot 版後端（`livefit/`）從 `springboot-backend` 分支之後的功能更新。最新的放最上面。

Node 版（`backend/`）的作業內容不在此記錄範圍；Spring Boot 版最初的移植過程見 `docs/springboot-migration-plan.md`。

---

## 2026-10-10 — Spring Boot 版專用的 Docker Compose

分支：`springboot-backend`

Node 版（`backend/`）與根目錄的 `docker-compose.yml` 沒有任何改動。

### 新增

- `compose.livefit.yml`：只含前端、Swagger、PostgreSQL 與 `livefit`，不含 Node 版後端。project name `livefit-final`，有自己的 volume，`livefit` 資料庫自動建立。
- `livefit/Dockerfile`、`livefit/.dockerignore`：多階段建置，JRE 21，非 root 使用者。

### 用法

`up -d` 啟動整套（含後端容器）。開發期改用 `up -d postgres frontend swagger` 只起其他三個服務，後端在本機跑。每個指令都要帶 `--env-file livefit/.env`。

一開始把 `livefit` 服務放在 `app` profile、預設不啟動，但 Docker Desktop 的啟動按鈕不帶 `--profile`，後端容器停掉後從介面上起不來，當天改掉。完整指令在 `README.md` 延伸章節，設計理由在 `docs/springboot-migration-plan.md` §12。

### 注意

- 與根目錄 `docker-compose.yml` 佔用相同的 port，兩組不能同時啟動。
- 新的 volume 是空的，原本在 `fitness` 那個 volume 裡的 `livefit` 資料庫不會自動搬過來。

### 驗證

68 項合約測試對容器版全數通過；`mvn test` 33 項通過。瀏覽器操作與 3000 的第三方登入 callback 尚未實測。

---

## 2026-10-10 — 擋住反方向的帳號接管

分支：`fix/social-prehijack`（疊在 `fix/jwt-default-secret` 之上，預計 merge 回 `springboot-backend`）

Node 版（`backend/`）沒有任何改動。`frontend/` 與 `docs/openapi.yaml` 的改動不要 merge 回 `main`。

### 要解決的問題

用沒驗證過的信箱（密碼註冊或 Facebook）先建立帳號，之後信箱本人用 Google 或 GitHub 登入時會被自動綁進那個帳號，而先建帳號的人仍然進得來。10-05 記為已知限制，這次採用當時列的做法 B。設計與理由在 `docs/springboot-migration-plan.md` §11.8。

### 行為差異

| 情況 | 之前 | 現在 |
|---|---|---|
| 密碼註冊後，第一次用同 email 的 Google / GitHub 登入 | 綁定，密碼保留 | 綁定，**密碼清除**，之前簽發的 token 失效 |
| Facebook 建立帳號後，第一次用同 email 的 Google / GitHub 登入 | 綁定，Facebook 仍可登入 | 綁定，**Facebook 的綁定刪除**（再登入回 409），之前簽發的 token 失效 |
| Google / GitHub 建立帳號後，再用另一個驗證過的平台登入 | 綁定 | 不變 |
| 沒有密碼的帳號打 `PUT /api/users/password` | 400 `此帳號使用第三方登入，無法修改密碼` | 不需舊密碼直接設定；這次登入超過 5 分鐘回 400 `為了確認是本人操作，請重新登入後再設定密碼` |
| `GET /api/users/profile` | `data.user` 為 `{ name, email }` | 多一個 `has_password` |
| JWT payload | `{ id, role, iat, exp }` | 多一個 `ver` |
| 帶著已失效的 token 開啟前端 | 每次換頁都丟例外 | 清掉登入狀態，當成未登入 |

### 改了什麼

| 檔案 | 內容 |
|---|---|
| `entity/User` | 新增 `email_verified`、`token_version` |
| `repository/UserRepository`、`UserIdentityRepository` | 會鎖住該列的查詢；一句 DELETE 刪除使用者的所有綁定 |
| `security/JwtTokenProvider`、`JwtAuthenticationFilter`、`AuthUser` | token 帶 `ver` 並在每次請求比對 |
| `service/UserService` | `takeOver`、`lockCurrentUser`、`setFirstPassword` |
| `service/AdminCoachService` | 升級教練改為鎖住該列再寫入 |
| `dto/user/ProfileResponse`、`common/ErrorMessages`、`controller/UserController` | 配合上面的調整 |
| `frontend/src/pages/user/ProfileView.vue` | 沒有密碼時隱藏舊密碼欄位、改稱「設定密碼」；成功後清空表單並重新取得資料 |
| `frontend/src/router/index.js` | profile 回 401 時清掉 cookie 與 store |
| `frontend/src/utils/swalHandler.js` | 新增 `密碼已設定` 的成功訊息 |
| `docs/openapi.yaml` | 更新 `/api/users/profile` 與 `/api/users/password` |
| `src/test/` | `UserServiceSocialLoginTest` 12 → 18 項；`JwtTokenProviderTest` 3 → 4 項 |

### 升級既有環境

1. 啟動一次，讓 `ddl-auto=update` 補上兩個欄位，用 `\d users` 確認。
2. 對外服務之前執行 `docs/springboot-migration-plan.md` §11.8「既有資料庫升級」的 SQL。它會把已綁 Google / GitHub 的舊帳號比照接管處理：清密碼、刪 Facebook 綁定、讓舊 token 失效。本機的 `livefit` 已於 2026-10-10 執行。

### 驗證

- `mvn test` 33 項通過，連跑 5 次。新增的情境：密碼帳號與 Facebook 帳號被接管、已驗證帳號再綁平台時密碼保留、舊 token 被 filter 拒絕、無密碼帳號設定密碼（含逾時）、接管前通過驗證的請求不能在接管後設密碼或改名，以及三個併發情境（同帳號同時接管、Google 與 GitHub 同時接管、接管同時改名改密碼升級教練）。
- 根目錄 68 項合約測試對 Spring Boot 版（port 8085）全數通過。
- `vite build` 通過。
- 升級 SQL 在本機 `livefit` 執行：兩個已綁 Google / GitHub 的帳號被標成已驗證、`token_version` 變成 1；當時資料庫裡沒有 Facebook 綁定，這兩個帳號也都沒有密碼，所以沒有東西被清掉。再執行一次影響 0 筆。
- 用真實帳號在瀏覽器跑了兩輪「先 Facebook、再同 email 的 Google」，以後端 log 與資料庫確認：Facebook 建立帳號 → Google 登入時 log 出現 `已清除原本的登入方式` → 帳號只剩 Google 綁定、`email_verified=true`、`token_version=1` → 再用 Facebook 登入回 409 `此 Email 已註冊，請改用原本的方式登入`。
- 用無頭 Chromium 實際操作前端 22 項全數通過（測試帳號用完即刪）：
  - 已登入的視窗在帳號被接管後站內換頁 → 導回登入頁、token cookie 被清掉、沒有未捕捉的例外，之後開公開頁面正常；用原本的密碼登入顯示 `使用者不存在或密碼輸入錯誤`。
  - 無密碼帳號的密碼分頁顯示「設定密碼」、沒有舊密碼欄位、有 5 分鐘的說明；兩次輸入不一致被擋下；設定成功後分頁變回「修改密碼」、表單清空，另一個視窗可用新密碼登入。
  - 登入超過 5 分鐘後設定密碼被拒絕，顯示 `為了確認是本人操作，請重新登入後再設定密碼`，資料庫仍沒有密碼。
- 這輪實測前發現並修正一個前端問題：設定成功時用的訊息代碼 `密碼已設定` 沒有登記在 `utils/swalHandler.js`，會跳出標題為「錯誤」的視窗。已補上。
- 無頭瀏覽器那一輪的接管狀態是用 SQL 直接製造的。需要真實帳號的兩項由使用者在瀏覽器手動確認（2026-10-10）：由 Google / GitHub 登入實際接管密碼註冊的帳號，以及接管後再用 Facebook 登入時畫面顯示 409 的訊息。

### 尚未驗證
- 升級 SQL「清密碼、刪 Facebook 綁定」那兩個動作沒有實際資料可以驗到。
- 兩個時間差的修正是用「拿接管前的版本號直接呼叫 service」與併發測試驗證的，沒有做到在兩個交易之間精確插入的測試。

### 注意事項

- 代價與限制（誠實使用者的密碼被清、Facebook 登不回來、帳號裡的資料不清等）列在 §11.8 最後。
- 密碼註冊仍不驗證信箱，冒用者還是能先佔位，只是本人一登入就收回。

---

## 2026-10-10 — 移除 JWT 預設密鑰

分支：`fix/jwt-default-secret`（預計 merge 回 `springboot-backend`）

Node 版（`backend/`）沒有任何改動。

### 改了什麼

`application.properties` 原本是 `jwt.secret=${JWT_SECRET:一段寫死的字串}`。沒有 `livefit/.env` 時會用那段字串簽 token，而它就在版控裡：看得到 repo 的人可以自己簽出任何使用者（含教練）的 JWT。

| 檔案 | 內容 |
|---|---|
| `livefit/src/main/resources/application.properties` | `jwt.secret=${JWT_SECRET:}`，不再有預設值 |
| `security/JwtTokenProvider` | 留空時啟動失敗，訊息為 `尚未設定 JWT_SECRET…`；長度不足的檢查與訊息保留 |
| `livefit/.env.example` | `JWT_SECRET=` 改為空白，註解附上產生指令 |
| `docs/springboot-migration-plan.md` §2 | 新增「產生 `JWT_SECRET`」：產生、設定、確認的步驟與注意事項 |
| `src/test/` | 新增 `JwtTokenProviderTest`（3 項，不需要資料庫） |

### 行為差異

- **沒有設定 `JWT_SECRET` 的環境升級後會無法啟動**，這是刻意的。照 `docs/springboot-migration-plan.md` §2 產生一把填進 `livefit/.env` 即可。
- 已經在 `.env` 設定過 `JWT_SECRET` 的環境不受影響，既有 token 照常有效。
- 原本靠預設密鑰簽發的 token 在換上新密鑰後全部失效，使用者要重新登入。

### 驗證

- 不需要資料庫的 14 項單元測試通過：`JwtTokenProviderTest` 3 項（沒設定、空字串、少於 32 個位元組都拋錯；64 個字元的密鑰可簽發並驗證）、`GithubOAuthClientTest` 6 項、`FacebookOAuthClientTest` 5 項。
- 文件裡的三個產生指令與 `sed` 一行設定在 macOS 上實測，輸出都是 64 個字元。

- 整合測試、實際啟動與根目錄 68 項合約測試是連同上面的 `fix/social-prehijack` 一起跑的（見該段），都通過；這個分支沒有單獨跑過。

### 尚未驗證

- 「沒有設定 `JWT_SECRET` 時整個應用程式啟動失敗」只有單元測試驗到 `JwtTokenProvider` 會拋錯，沒有實際拿掉 `.env` 啟動一次。

---

## 2026-10-05 — Facebook 登入

分支：`feature/facebook-login`（預計 merge 回 `springboot-backend`）

### 概述

Spring Boot 版新增 Facebook 登入，流程與 GitHub 相同（authorization code），共用 callback 頁、按鈕元件與 `redirect_uri` 白名單。最大的差別是 Facebook 不提供 email 是否驗證過的旗標，所以**不會自動綁定既有帳號**。`backend/`（Node 版）沒有改動。

### 行為規則

`POST /api/users/facebook`，body 為 `{ code, redirect_uri }`，成功回 201，內容與一般登入相同。

| 情況 | 回應 |
|---|---|
| 這個 Facebook 帳號登入過 | 201，登入原帳號 |
| 沒登入過，email 也沒人用 | 201，建立無密碼的新帳號 |
| 沒登入過，但 email 已有帳號（密碼、Google、GitHub 建立的都算） | 409 `此 Email 已註冊，請改用原本的方式登入`，不綁定 |
| `code` 缺漏，或 `redirect_uri` 缺漏／不在白名單內 | 400 `欄位未填寫正確` |
| 後端沒設定 `FACEBOOK_APP_ID` 或 `FACEBOOK_APP_SECRET` | 400 `尚未設定 Facebook 登入` |
| `code` 無效、用過、過期，或連不上 Facebook | 400 `Facebook 登入驗證失敗` |
| Facebook 帳號沒有 email，或使用者沒同意提供 | 400 `此 Facebook 帳號沒有提供 Email，無法登入` |

### 後端（`livefit/`）

| 檔案 | 改動 |
|---|---|
| `security/FacebookOAuthClient.java` | 新增。換 token、取 `/me`；一律回傳 `emailVerified=false` |
| `security/OAuthRestClients.java` | 新增。從 `GithubOAuthClient` 抽出的逾時設定，兩個 client 共用 |
| `security/GithubOAuthClient.java` | 改用 `OAuthRestClients`，行為不變 |
| `config/FacebookProperties.java` | 新增 |
| `service/UserService.java` | 新增 `facebookLogin`；`githubLogin` 的內容抽成兩者共用的 `oauthCodeLogin`；未驗證 email 撞到既有帳號時的訊息改為專用的一句 |
| `controller/UserController.java` | 新增 `POST /api/users/facebook` |
| `config/SecurityConfig.java` | 註冊 `FacebookProperties`（matcher 沒改） |
| `entity/UserIdentity.java`、`common/ErrorMessages.java` | 新增 `PROVIDER_FACEBOOK` 與四句訊息 |
| `application.properties`、`.env.example` | 新增 `FACEBOOK_APP_ID`、`FACEBOOK_APP_SECRET`；`OAUTH_REDIRECT_URIS` 預設值加入 Facebook 的 callback |
| `src/test/` | 新增 `FacebookOAuthClientTest`（5 項）；`UserServiceSocialLoginTest` 增加 2 項 |

### 前端（`frontend/`）

| 檔案 | 改動 |
|---|---|
| `config/oauthProviders.js` | 新增 `facebook` 一筆 |
| `components/SocialLoginButtons.vue` | 新增 Facebook 按鈕 |
| `api/users.js`、`api/index.js`、`config/routeTable.js` | 新增 `postFacebookLogin`；白名單加 `/facebook` |
| `.env.example`、`Dockerfile`、根目錄 `docker-compose.yml` | 新增 `VITE_FACEBOOK_APP_ID` |

callback 頁與按鈕元件沒有改，直接沿用 GitHub 那一版。

### 環境設定

1. 到 Meta for Developers 建立應用程式，加入 Facebook 登入與 `email` 權限，「有效的 OAuth 重新導向 URI」填 `http://localhost:5173/oauth/callback/facebook`。
2. `livefit/.env`：`FACEBOOK_APP_ID`、`FACEBOOK_APP_SECRET`。
3. `frontend/.env`：`VITE_FACEBOOK_APP_ID`（與上面的編號相同）。

**如果 `livefit/.env` 裡自己設過 `OAUTH_REDIRECT_URIS`，要手動把 Facebook 的 callback 加進去**；沒設的話預設值已經包含。

### 驗證結果

已驗證：

- `mvn test` 23 項通過。
- 根目錄 68 項合約測試對 Spring Boot 版全數通過。
- `vite build` 通過。
- 用 curl 打實際啟動的後端：空 body、`redirect_uri` 不在白名單 → 400 `欄位未填寫正確`；填假的應用程式編號與密鑰、亂填 `code`（真的連到 Facebook）→ 400 `Facebook 登入驗證失敗`，耗時約 0.2 秒；後端 log 中沒有出現密鑰。

- 以真實的 Facebook App（開發模式）在瀏覽器（`http://localhost:5173`）登入：email 與既有帳號不同 → 建立新帳號，名稱取自 Facebook，沒有密碼；`users` 與 `user_identities` 在同一個交易內寫入；既有的 GitHub + Google 帳號沒有被動到。
- 三個平台的主流程都驗證過後，刪除本機 `livefit` 資料庫的舊欄位 `users.google_sub`：刪除前確認該欄位全為 null，刪除後 `users` 筆數不變，三筆綁定都還在。

- 兩種順序都在瀏覽器以同一個 email 的真實帳號測過：
  - 先 Google 建立帳號、再用 Facebook 登入 → 回到登入頁並顯示「此 Email 已註冊，請改用原本的方式登入」，沒有登入；資料庫中該帳號仍只有 `GOOGLE` 一筆綁定。
  - 先 Facebook 建立帳號、再用 Google 登入 → Google 綁進 Facebook 建立的帳號。這符合目前的規則，同時也就是 `docs/springboot-migration-plan.md` §11.7 記錄的已知限制實際發生的樣子。

尚未驗證：

- 「Facebook 沒有提供 email」只在單元測試用假造的回應驗過。
- 授權頁按取消、登入後按上一頁：與 GitHub 共用同一段程式，GitHub 已驗證，Facebook 沒有另外測。
- 容器版前端（3000 port）沒有測。

### 注意事項

- **Facebook 不會自動綁定既有帳號**，這是刻意的，理由與反方向的已知限制見 `docs/springboot-migration-plan.md` §11.7。
- Facebook App 在開發模式下只有具應用程式角色的帳號能登入。
- `FACEBOOK_APP_SECRET` 只放 `livefit/.env`，不要放進任何 `VITE_*` 變數。
- Graph API 版本 `v26.0` 寫在後端與前端各一處，升級時要一起改。
- **其他既有環境要自己刪 `users.google_sub`**：這個舊欄位只存在於「第三方登入共用層」之前就建好的資料庫，SQL 見 `docs/springboot-migration-plan.md` §11.7。刪除前先確認搬資料的 SQL（§11.5）跑過；刪除後就不能 revert 回共用層之前的程式碼。全新建立的資料庫本來就沒有這個欄位。
- `frontend/` 與 `docs/` 的改動同樣**不要 merge 回 `main`**。

---

## 2026-10-05 — GitHub 登入

分支：`feature/github-login`（預計 merge 回 `springboot-backend`）

### 概述

Spring Boot 版新增 GitHub 登入。GitHub 不提供可離線驗簽的 ID token，所以走 authorization code 流程：前端導向 GitHub 授權頁，拿到一次性的 `code` 後交給後端，後端用 `code` + client secret 向 GitHub 取得使用者資料，再交給上一版抽出的 `socialLogin` 對應帳號。`backend/`（Node 版）沒有改動。

### 行為規則

`POST /api/users/github`，body 為 `{ code, redirect_uri }`，成功回 201，內容與一般登入相同。

帳號對應與 Google 登入共用同一套規則：GitHub 帳號登入過 → 直接登入；同 email 已有帳號 → 自動綁定（密碼與其他登入方式保留）；都沒有 → 建立無密碼的新帳號。同一個帳號可以同時綁 Google 與 GitHub。

| 情況 | 回應 |
|---|---|
| `code` 缺漏，或 `redirect_uri` 缺漏／不在 `OAUTH_REDIRECT_URIS` 白名單內 | 400 `欄位未填寫正確` |
| 後端沒設定 `GITHUB_CLIENT_ID` 或 `GITHUB_CLIENT_SECRET` | 400 `尚未設定 GitHub 登入` |
| `code` 無效、用過、過期，或連不上 GitHub | 400 `GitHub 登入驗證失敗` |
| GitHub 帳號沒有主要且驗證過的 email | 400 `此 GitHub 帳號沒有已驗證的 Email，無法登入` |
| 該 email 的帳號已綁定另一個 GitHub 帳號 | 409 `Email 已被使用` |

### 後端（`livefit/`）

| 檔案 | 改動 |
|---|---|
| `security/GithubOAuthClient.java` | 新增。換 token、取 `/user`、取 `/user/emails`；連線逾時 2 秒、讀取逾時 3 秒 |
| `config/GithubProperties.java`、`config/OAuthProperties.java` | 新增 |
| `dto/user/OAuthCodeLoginRequest.java` | 新增 |
| `service/UserService.java` | 新增 `githubLogin` |
| `controller/UserController.java` | 新增 `POST /api/users/github` |
| `config/SecurityConfig.java` | 註冊兩個新的 Properties（matcher 沒改） |
| `entity/UserIdentity.java`、`common/ErrorMessages.java` | 新增 `PROVIDER_GITHUB` 與三句訊息 |
| `application.properties`、`.env.example` | 新增 `GITHUB_CLIENT_ID`、`GITHUB_CLIENT_SECRET`、`OAUTH_REDIRECT_URIS` |
| `src/test/` | 新增 `GithubOAuthClientTest`（6 項）；`UserServiceSocialLoginTest` 增加 3 項 |

### 前端（`frontend/`）

| 檔案 | 改動 |
|---|---|
| `config/oauthProviders.js` | 新增。各平台的授權網址、scope、API 函式 |
| `components/OAuthRedirectButton.vue` | 新增。導向授權頁的按鈕 |
| `components/SocialLoginButtons.vue` | 新增。「或」分隔線 + Google 按鈕 + GitHub 按鈕 |
| `components/GoogleLoginButton.vue` | 「或」分隔線移到 `SocialLoginButtons.vue`，其餘不變 |
| `pages/public/auth/OAuthCallbackView.vue` | 新增。比對 `state`、把 `code` 交給後端 |
| `pages/public/auth/LoginView.vue`、`SignupView.vue` | `<GoogleLoginButton />` 換成 `<SocialLoginButtons />` |
| `router/index.js` | 新增 `/oauth/callback/:provider` |
| `api/users.js`、`api/index.js`、`config/routeTable.js` | 新增 `postGithubLogin`；白名單加 `/github` |
| `.env.example`、`Dockerfile`、根目錄 `docker-compose.yml` | 新增 `VITE_GITHUB_CLIENT_ID` |

### 環境設定

1. GitHub → Settings → Developer settings → OAuth Apps 建立 App，callback URL 填 `http://localhost:5173/oauth/callback/github`。
2. `livefit/.env`：`GITHUB_CLIENT_ID`、`GITHUB_CLIENT_SECRET`。
3. `frontend/.env`：`VITE_GITHUB_CLIENT_ID`（與上面的 Client ID 相同）。

`OAUTH_REDIRECT_URIS` 的預設值已包含 5173 與 3000 兩個 GitHub callback，本機開發不必另外設定。

### 驗證結果

已驗證：

- `mvn test` 16 項通過。
- 根目錄 68 項合約測試對 Spring Boot 版全數通過。
- `vite build` 通過。
- 用 curl 打實際啟動的後端：空 body、缺 `redirect_uri`、`redirect_uri` 不在白名單 → 400 `欄位未填寫正確`；未設定 client id → 400 `尚未設定 GitHub 登入`；填假的 client id 與 secret、亂填 `code`（真的連到 GitHub）→ 400 `GitHub 登入驗證失敗`，耗時約 0.4 秒。

- 以真實的 GitHub OAuth App 在瀏覽器（`http://localhost:5173`）走完整流程：
  - 沒有帳號時用 GitHub 登入 → 建立新帳號，名稱取自 GitHub 的顯示名稱，email 是 GitHub 上主要且驗證過的那筆，沒有密碼；`users` 與 `user_identities` 在同一個交易內寫入。
  - 接著用同 email 的 Google 登入 → 綁到同一個帳號（`user_identities` 多一筆 `GOOGLE`，指向同一個 `user_id`），沒有另建帳號，名稱沒有被覆蓋。

  - 在 GitHub 撤銷授權後重新登入，於授權頁按取消 → 回到登入頁並顯示「已取消 GitHub 登入」，沒有登入。
  - 重新授權並登入 → 仍是同一個帳號，沒有新增帳號或綁定；先前在會員頁改過的名稱沒有被 GitHub 的名稱蓋回去。
  - 登入成功後按瀏覽器的上一頁 → 回到登入頁，沒有錯誤視窗，授權碼沒有被重送。

尚未驗證：

- 容器版前端（3000 port）。
- 「GitHub 回 HTTP 200 加 `error`」只在單元測試用假造的回應驗過。上面那次真實連線用的是不存在的 client id，GitHub 回的是 404，走的是另一條路徑。
- 反方向的綁定（先有密碼或 Google 帳號，再用 GitHub 登入）只在整合測試驗過，沒有在瀏覽器測。

### 注意事項

- **容器版前端（3000）能否共用同一個 OAuth App 尚未確認**。callback URL 登記的是 5173；GitHub 文件說 loopback 位址的 `redirect_uri` 不必與登記的 port 相同，但只明確提到 `127.0.0.1`，`localhost` 是否適用沒有實測。不行的話就為 3000 另建一個 App。
- `GITHUB_CLIENT_SECRET` 只放 `livefit/.env`，不要放進任何 `VITE_*` 變數，那些會被打包進前端的 JS。
- 容器版前端要重新 build 才會帶入 `VITE_GITHUB_CLIENT_ID`。
- `frontend/` 與 `docs/` 的改動同樣**不要 merge 回 `main`**。

---

## 2026-10-05 — 第三方登入共用層

分支：`refactor/social-identity`（預計 merge 回 `springboot-backend`）

### 概述

為了之後加 GitHub、Facebook 登入，把 Google 專用的帳號對應抽成各平台共用：綁定資料從 `users.google_sub` 搬到新表 `user_identities`，`linkOrCreateGoogleUser` 改成通用的 `socialLogin`。這次是重構，Google 登入的行為不變。`backend/`（Node 版）與 `frontend/` 沒有改動。

### 行為變更

| 端點 | 情況 | 修改前 | 修改後 |
|---|---|---|---|
| `PUT /api/users/password` | 純第三方登入建立、沒有密碼的帳號 | 400 `此帳號使用 Google 登入，無法修改密碼` | 400 `此帳號使用第三方登入，無法修改密碼` |

前端沒有比對這句文字，只是原樣顯示。

### 修改的檔案

| 檔案 | 改動 |
|---|---|
| `livefit/.../entity/UserIdentity.java` | 新增，table `user_identities` |
| `livefit/.../repository/UserIdentityRepository.java` | 新增 |
| `livefit/.../security/SocialProfile.java` | 新增，取代 `GoogleIdTokenVerifier.GoogleProfile` |
| `livefit/.../entity/User.java`、`repository/UserRepository.java` | 移除 `googleSub`、`findByGoogleSub` |
| `livefit/.../service/UserService.java` | `linkOrCreateGoogleUser` → `socialLogin` / `linkOrCreateSocialUser`；寫入改用 `TransactionTemplate` |
| `livefit/.../common/ErrorMessages.java` | `GOOGLE_ACCOUNT_NO_PASSWORD` → `SOCIAL_ACCOUNT_NO_PASSWORD` |
| `livefit/pom.xml`、`livefit/src/test/.../UserServiceSocialLoginTest.java` | 新增 `spring-boot-starter-test` 與 7 項整合測試 |
| `docs/openapi.yaml` | `PUT /api/users/password` 的訊息文字 |
| `docs/springboot-migration-plan.md` | 新增 §11.5 |
| `CLAUDE.md`、`CHANGELOG.md` | 同步更新 |

### 資料搬移（既有環境要手動執行一次）

新版啟動一次讓 Hibernate 建出 `user_identities` 後執行，SQL 見 `docs/springboot-migration-plan.md` §11.5。`users.google_sub` 欄位這次**不刪**，留到其他平台登入完成後再處理。

沒有執行的話，既有 Google 使用者下次登入會經由 email 重新綁定，不會登不進去；但不要依賴這一點。

### 驗證結果

已驗證：

- 根目錄 68 項合約測試對 Spring Boot 版全數通過。
- `mvn test` 7 項通過（連跑 5 次）：首次登入建立帳號、再次登入同一帳號、綁定既有密碼帳號且密碼保留、已綁定另一個 Google 帳號回 409、未驗證 email 回 400、無密碼帳號改密碼回 400，以及「同時首次登入」「同時綁定」兩種併發（各 8 個執行緒，全部成功且只產生一筆綁定）。
- 本機 `livefit` 資料庫原有的 1 筆 Google 綁定已搬到 `user_identities`，`provider_user_id` 與原 `google_sub` 相同、指向同一個使用者。
- `POST /api/users/google`：空 body → 400 `欄位未填寫正確`；格式錯誤的 token → 400 `Google 登入驗證失敗`。

- 合併前以真實 Google 帳號在瀏覽器登入，進到原本的帳號。

### 注意事項

- `mvn test` 連的是真的 `livefit` 資料庫，postgres 沒開會失敗。平常編譯仍用 `mvn -DskipTests compile`。
- 併發測試第一次跑就抓到一個問題：同時綁定時，後到的請求在交易內看到「這個帳號已綁過 Google」就回了 409，但綁上去的其實就是同一個 Google 帳號。現在會再查一次確認，是同一個就直接登入。
- `docs/` 的改動同樣**不要 merge 回 `main`**。

---

## 2026-10-04 — 註冊與 Google 登入的錯誤回應修正

分支：`fix/user-write-errors`（預計 merge 回 `springboot-backend`）

### 概述

Google 登入上線後檢查出幾個「資料庫約束擋下寫入、但前端收到 500 `伺服器錯誤`」的情況，這次改成回可預期的狀態碼。資料正確性原本就由約束保證，這次只改回應。`backend/`（Node 版）沒有改動，以下情況在 Node 版仍回 500。

### 行為變更

| 端點 | 情況 | 修正前 | 修正後 |
|---|---|---|---|
| `POST /api/users/google` | 同一個 Google 帳號首次登入，兩個請求同時到 | 後者 500 | 改用已建好的帳號照常登入，201 |
| `POST /api/users/google` | 同一瞬間有人用同 email 走密碼註冊 | 500 | 409 `Email 已被使用`（再登入一次會走綁定流程） |
| `POST /api/users/signup` | 兩個同 email 的註冊同時到 | 後者 500 | 409 `Email 已被使用` |
| `POST /api/users/signup` | `name` 超過 50 字 | 500 | 400 `欄位未填寫正確` |
| `PUT /api/users/profile` | `name` 超過 50 字 | 500 | 400 `欄位未填寫正確` |

名稱長度以去除前後空白後的字元數計算（一個 emoji 算一個字），與 `users.name` 的 `varchar(50)` 一致。

### 修改的檔案

| 檔案 | 改動 |
|---|---|
| `livefit/.../service/UserService.java` | `signup` 與 `linkOrCreateGoogleUser` 接住 unique 約束衝突後重查；`signup` 拿掉 `@Transactional`；新增 `isValidName` 並套用到註冊與修改名稱 |
| `docs/springboot-migration-plan.md` | §8 差異表新增第 11、12 列 |
| `CHANGELOG.md` | 本段 |

### 驗證結果

已驗證：

- `mvn compile` 通過。
- `npm run test:m2`（註冊、登入、會員資料）對 Spring Boot 版通過。

尚未驗證：

- 併發情境沒有實際重現，修正是依程式邏輯推得。要重現需同時送出兩個相同 email 的註冊，且兩者都要先通過「email 是否已存在」的檢查。
- 名稱超過 50 字的 400 回應沒有實際打 API 確認。

### 注意事項

- `signup` 與 `googleLogin` 都**刻意不加 `@Transactional`**：重查依賴 `save` 自成一個交易，例外才會在當下丟出。包進外層交易後，例外要到 commit 才出現，而且 postgres 的交易已作廢、無法再查，會退回 500。
- `docs/` 的改動同樣**不要 merge 回 `main`**。

---

## 2026-10-04 — Google 第三方登入與獨立資料庫

分支：`feature/social-login`（預計 merge 回 `springboot-backend`）

### 概述

1. **新增 Google 登入**：前端用 Google Identity Services 取得 ID token，後端驗證後簽發與一般登入相同格式的 JWT。登入頁與註冊頁都多了一顆 Google 按鈕。
2. **Spring Boot 版改用獨立資料庫 `livefit`**：開啟 Hibernate `ddl-auto=update` 自動建表，不再依賴 Node 版建好的 `fitness`。原本的 `fitness` 完全沒有被更動。
3. `backend/`（Node 版）沒有任何改動，也沒有 Google 登入功能。

### 行為規則

**Google 登入時的帳號對應**（依序判斷）：

1. 這個 Google 帳號登入過 → 直接登入原帳號。
2. 沒登入過，但 Google 的 email 已有密碼註冊的帳號 → 自動綁定到該帳號（只寫入 `google_sub`，**原密碼保留**）。
3. 都沒有 → 建立新帳號，沒有密碼，角色為 `USER`。

**各帳號型態可用的功能**（只取決於帳號有沒有密碼）：

| 帳號型態 | 密碼登入 | Google 登入 | 修改密碼 |
|---|---|---|---|
| 密碼註冊、未綁 Google | ✅ | 首次使用時自動綁定 | ✅ |
| 密碼註冊、已綁 Google | ✅ | ✅ | ✅ |
| 純 Google 建立（無密碼） | ❌ | ✅ | ❌ |

**新增的 API 與錯誤訊息**：

| 項目 | 內容 |
|---|---|
| 端點 | `POST /api/users/google`，body `{ "credential": "<Google ID token>" }`，免登入 |
| 成功 | 201，`{ status, data: { token, user: { name } } }`，與 `POST /api/users/login` 相同 |
| 400 | `欄位未填寫正確`、`Google 登入驗證失敗`、`尚未設定 Google 登入` |
| 409 | `Email 已被使用`（該 email 的帳號已綁定另一個 Google 帳號） |
| `PUT /api/users/password` 新增 400 | `此帳號使用 Google 登入，無法修改密碼` |

### 後端（`livefit/`）

新增：

| 檔案 | 用途 |
|---|---|
| `security/GoogleIdTokenVerifier.java` | 用 Google 公鑰驗證 ID token（簽章、發行者、對象、期限） |
| `config/GoogleProperties.java` | 讀取 `GOOGLE_CLIENT_ID` |
| `dto/user/GoogleLoginRequest.java` | 請求 body `{ credential }` |

修改：

| 檔案 | 改動 |
|---|---|
| `pom.xml` | 加入 `spring-security-oauth2-jose` |
| `application.properties` | 預設資料庫改 `livefit`、`ddl-auto` 改 `update`、時間欄位固定為不帶時區、加入 `google.client-id` |
| `entity/User.java` | `password` 改為可 null；新增 `google_sub`（unique） |
| `repository/UserRepository.java` | 新增 `findByGoogleSub` |
| `service/UserService.java` | 新增 `googleLogin`；密碼登入與修改密碼處理無密碼帳號 |
| `controller/UserController.java` | 新增 `POST /api/users/google` |
| `common/ErrorMessages.java` | 新增三句 Google 相關錯誤訊息 |
| `config/SecurityConfig.java` | 註冊 `GoogleProperties` |
| `.env.example` | `DB_DATABASE=livefit`，新增 `DDL_AUTO`、`GOOGLE_CLIENT_ID` |

### 前端（`frontend/`）

新增：

| 檔案 | 用途 |
|---|---|
| `src/components/GoogleLoginButton.vue` | Google 登入按鈕；未設定 Client ID 時不顯示 |
| `src/utils/loginHandler.js` | 登入成功後的共用處理（存 token、更新狀態、依角色導頁） |

修改：

| 檔案 | 改動 |
|---|---|
| `src/api/users.js`、`src/api/index.js` | 新增 `postGoogleLogin` |
| `src/config/routeTable.js` | 免帶 token 白名單加入 `/google` |
| `src/pages/public/auth/LoginView.vue` | 加入 Google 按鈕；登入成功邏輯改用共用函式 |
| `src/pages/public/auth/SignupView.vue` | 加入 Google 按鈕 |
| `.env.example` | 新增 `VITE_GOOGLE_CLIENT_ID` |
| `Dockerfile` | 新增 `VITE_GOOGLE_CLIENT_ID` build arg |

### 文件（`docs/`）

| 檔案 | 改動 |
|---|---|
| `openapi.yaml` | 新增 `POST /api/users/google`；`PUT /api/users/password` 補上無密碼帳號的錯誤訊息；`bearerAuth` 說明補上 Google 登入 |
| `springboot-migration-plan.md` | 新增 §11，記錄獨立資料庫與 Google 登入的設計；背景段落加註原「不得更動 schema」的限制已不適用 |

### 其他

| 檔案 | 改動 |
|---|---|
| `CLAUDE.md` | 資料庫段落改寫為「兩套後端各用各的資料庫」；新增 Google 登入段落；補充本機沒有 `mvn` 時的三種做法；環境變數表加入 `frontend/.env` |
| `docker-compose.yml` | 前端 build args 加入 `VITE_GOOGLE_CLIENT_ID`（預設空值，不影響 Node 版驗收） |
| `README.md` | Spring Boot 段落更新：建立 `livefit` 資料庫的步驟、不再需要先跑 Node 版、啟用 Google 登入的設定、已知限制 |
| `CHANGELOG.md` | 本檔案 |

### 環境設定

新增的環境變數：

| 檔案 | 變數 | 說明 |
|---|---|---|
| `livefit/.env` | `DB_DATABASE=livefit` | 原本是 `fitness`。**不要改回去**，否則 `ddl-auto=update` 會改到 Node 版的 schema |
| `livefit/.env` | `DDL_AUTO=update` | 不想讓 Hibernate 動 schema 時改成 `none` |
| `livefit/.env` | `GOOGLE_CLIENT_ID` | Google OAuth 2.0 Client ID |
| `frontend/.env` | `VITE_GOOGLE_CLIENT_ID` | 必須與後端的 `GOOGLE_CLIENT_ID` 相同 |

新環境的一次性設定：

1. 建立資料庫（postgres 容器只會自動建 `fitness`；`npm run db:reset` 之後也要重做）：
   ```bash
   docker compose exec postgres psql -U student -d fitness -c "CREATE DATABASE livefit"
   ```
2. 到 Google Cloud Console 建立 OAuth 2.0 Client ID（Web application），Authorized JavaScript origins 加入 `http://localhost:5173` 與 `http://localhost:3000`。
3. 把 Client ID 填入上表的兩個 `.env`。

### 啟動方式

```bash
# 1. 資料庫（只啟動 postgres，不要啟動 Node 版 backend 容器，會佔用 8080）
docker compose up -d postgres

# 2. 後端（必須在 livefit/ 下執行；本機沒有 mvn 時的做法見 CLAUDE.md）
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home
cd livefit && mvn spring-boot:run

# 3. 前端
cd frontend && npm run dev
```

瀏覽器開 `http://localhost:5173/login`。請用 `localhost` 而不是 `127.0.0.1`，Google 把兩者視為不同來源。

### 驗證結果

已驗證：

- 根目錄 68 項合約測試對新資料庫 `livefit` 全數通過。
- `livefit` 的 8 張表由 Hibernate 建立；`users.password` 可為 null、`google_sub` 有 unique 約束、時間欄位為 `timestamp without time zone`。`fitness.users` 未被更動。
- `POST /api/users/google` 的失敗路徑：空 body、未設定 Client ID、偽造簽章的 token、格式錯誤的 token，都回預期的 400。
- 無密碼帳號走密碼登入回 400，不會造成 500。
- 前端 ESLint 與 `vite build` 通過。

尚未驗證（需要真實 Google 帳號，在瀏覽器手動測試）：

- 新 Google 帳號登入並導向 `/user/dashboard`。
- 先用密碼註冊、再用同 email 的 Google 登入，確認綁定到同一個帳號且仍可修改密碼。
- 純 Google 帳號修改密碼時顯示 `此帳號使用 Google 登入，無法修改密碼`。

### 注意事項

- `frontend/` 與 `docs/openapi.yaml` 的改動**不要 merge 回 `main`**：README 規定這兩個目錄在 Node 作業驗收中不可修改。
- `fitness` 與 `livefit` 兩個資料庫的資料不互通，帳號要各自註冊。
- `ddl-auto=update` 只會新增表與欄位；修改既有欄位的型別、長度或 nullable 不會自動套用，需要手動 `ALTER TABLE`。

### 分支狀態

`feature/social-login` 已於 2026-10-04 以 `--no-ff` 合併進 `springboot-backend` 並推上遠端。下圖是合併完成當下的狀態，三個分支的本機與遠端皆同步。

```
                                                                        feature/social-login
                                                                        （已合併，分支保留）
                                                                                 │
                                              6dad47b ──── 4c02531 ──── 22d85fb ─┤
                                              後端          前端          文件     │
                                             ╱                                    ╲
  ···── 90c7392 ── fba93b9 ── d3e913c ── 039a7d4 ── 6e77bf9 ── 4292ba9 ─────────── d94a1fe
           │       Spring Boot  移植文件    .gitignore  .claudeignore  README       Merge commit
           │       後端         CLAUDE.md                                              │
           │                                                                           │
          main                                                                springboot-backend
   Node 版作業，驗收對象                                                        Spring Boot 版
```

| 分支 | commit | 內容 |
|---|---|---|
| `main` | `90c7392` | Node 版作業，GitHub Actions 驗收的對象，這次沒有變動 |
| `springboot-backend` | `d94a1fe` | Spring Boot 版，已含 Google 登入與獨立資料庫 |
| `feature/social-login` | `22d85fb` | 這次的三個 commit（後端、前端、文件），合併後保留不刪 |

分支之間的關係：

- `springboot-backend` 比 `main` 多 9 個 commit：5 個是 Spring Boot 移植，3 個是這次的 Google 登入，1 個是 merge commit。
- `main` 是 `springboot-backend` 的祖先，兩者沒有分岔。**因此 `springboot-backend` 一旦 merge 回 `main` 會直接 fast-forward**，`main` 會整個變成 Spring Boot 版的內容，請勿這樣做。
- `feature/social-login` 的內容已全部在 `springboot-backend` 裡，兩者只差 merge commit `d94a1fe`。

要撤回這次合併：

```bash
git checkout springboot-backend
git revert -m 1 d94a1fe
git push origin springboot-backend
```

撤回後 `feature/social-login` 上的實作不受影響。日後若想重新合併，要先把該 revert commit 再 revert 一次，否則 Git 會認為這些 commit 已經合併過而略過。另外，撤回程式碼不會還原資料庫，`livefit` 裡的 `google_sub` 欄位與已建立的 Google 帳號會留著。
