# 更新紀錄

記錄 Spring Boot 版後端（`livefit/`）從 `springboot-backend` 分支之後的功能更新。最新的放最上面。

Node 版（`backend/`）的作業內容不在此記錄範圍；Spring Boot 版最初的移植過程見 `docs/springboot-migration-plan.md`。

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
