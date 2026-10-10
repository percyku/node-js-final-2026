# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 這個 repo 是什麼

六角學院「健身房網站後端」最終作業（fork 自 `hexschool/node-js-final-2026`）。前端、Swagger 文件、驗收測試都是題目給定的，**要寫的是後端**。

目前有**兩套功能等價的後端實作**：

| 目錄 | 技術 | 狀態 |
|---|---|---|
| `backend/` | Node.js + Express 5 + TypeORM | 正式繳交用，GitHub Actions 驗收的對象 |
| `livefit/` | Spring Boot 3.5 + Spring Data JPA + Spring Security | `springboot-backend` 分支上的移植版，不列入作業驗收 |

兩者實作同一份 API 規格，差異記錄在 `docs/springboot-migration-plan.md` §8。`feature/social-login` 起 Spring Boot 版另外多了 **Google、GitHub、Facebook 登入**並改用**獨立資料庫**（同文件 §11），這些 Node 版沒有。

## 規格的唯一來源

**`docs/openapi.yaml` 是 API 規格的唯一權威來源**，不是 `backend/` 的現有程式碼。Node 版有數個已知瑕疵（`purchase_at` 變數名打錯、死路由、回傳 password hash 等），Spring Boot 版刻意不照抄 —— 差異表在 `docs/springboot-migration-plan.md` §8。改動任何行為前先查 openapi。

### 跨實作的共通契約

- 回應格式：成功 `{"status":"success","data":...}`；可預期失敗 `{"status":"failed","message":"..."}`。驗收只看 2xx/4xx + `status` 欄位，200 與 201 可互換
- **四句錯誤訊息必須一字不差**（前端用文字判斷跳哪個視窗）：`已經報名過此課程`、`已無可使用堂數`、`已達最大參加人數，無法參加`、`請先登入`
- JWT payload 必須含 `{id, role, exp}`，前端會自行 decode
- `GET /healthcheck` 回**純文字** `OK`（唯一不包 `{status, data}` 外殼的端點）
- 取消報名是軟刪除（只寫 `cancelled_at`），因此取消過的課不能再報名；剩餘堂數沒有欄位，是「購買總堂數 − 未取消報名數」算出來的

## 常用指令

### 環境（所有開發都需要）

```bash
docker compose up -d              # frontend:3000, swagger:8081, postgres:5432, backend:8080
docker compose up -d postgres     # 只要資料庫
npm run db:reset                  # down -v && up -d，清空資料庫重來
```

### Node 後端（`backend/`）

```bash
cd backend && npm run dev         # nodemon，監聽 8080
cd backend && npm start           # CI 驗收用的啟動方式
```

### Spring Boot 後端（`livefit/`）

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home
cd livefit && mvn spring-boot:run      # 必須在 livefit/ 下執行，否則讀不到 .env
cd livefit && mvn -DskipTests compile
cd livefit && mvn test                # 第三方登入的整合測試，需要 postgres 與 livefit 資料庫在線
```

> **本機 JDK 陷阱**：`/Library/Java/JavaVirtualMachines/` 下的 JDK 都是 x86 版，在 Apple Silicon 上會回 `bad CPU type`。只有 `~/Library/Java/JavaVirtualMachines/temurin-21.0.11` 可用。`JAVA_HOME` 每開一個新終端機都要重新 export，或直接寫進 `~/.zshrc`。
>
> **本機沒有安裝系統層級的 `mvn`**：直接打 `mvn` 會回 `zsh: command not found: mvn`。上面指令中的 `mvn` 請用下列任一方式取得：
>
> 1. 直接用 wrapper 快取裡的 Maven 3.9.16（與 `.mvn/wrapper/maven-wrapper.properties` 指定的版本相同，已驗證可編譯與啟動）：
>    ```bash
>    ~/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn spring-boot:run
>    ```
> 2. 在 `~/.zshrc` 加別名，之後就能直接打 `mvn`：
>    ```bash
>    alias mvn=~/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn
>    ```
> 3. `brew install maven`。
>
> **`./mvnw` 在本機會失敗**：macOS 的 `mktemp -d` 忽略 `TMPDIR`，wrapper 下載 Maven 時會回 `cannot create temp dir`，所以不要用它，改用上面三種方式之一。

### Spring Boot 版專用的 Compose（`compose.livefit.yml`）

不含 Node 版後端，project name 是 `livefit-final`，有自己的 `pgData` volume，postgres 直接建立 `livefit` 資料庫。變數插值讀 `livefit/.env`，**每個指令都要帶 `--env-file livefit/.env`**。

```bash
# 整套：livefit 也用容器跑
docker compose -f compose.livefit.yml --env-file livefit/.env up -d --build
# 開發期：只起其他三個服務，livefit 在本機用 mvn spring-boot:run 跑
docker compose -f compose.livefit.yml --env-file livefit/.env up -d postgres frontend swagger
# 清空重來
docker compose -f compose.livefit.yml --env-file livefit/.env down -v
```

- 與根目錄 `docker-compose.yml` 佔用相同的 port（3000 / 8081 / 5432 / 8080），**兩組不能同時啟動**，切換前先 `docker compose stop` 另一組。
- 前端的 `VITE_GOOGLE_CLIENT_ID` 等三個 build arg 直接取 `livefit/.env` 的 `GOOGLE_CLIENT_ID`、`GITHUB_CLIENT_ID`、`FACEBOOK_APP_ID`；改了這些值要加 `--build` 重建前端。
- `livefit` 容器的密鑰由 `env_file: ./livefit/.env` 帶入，`PORT`、`DB_HOST`、`DB_PORT`、`DB_DATABASE` 由 compose 覆蓋。`livefit/.env` 的值若含 `$` 要寫成 `$$`。
- **不要用 `profiles` 把 `livefit` 服務設成選用**：Docker Desktop 的啟動按鈕與不帶 `--profile` 的 `docker compose start` 都會跳過它，容器停掉後從介面上起不來（2026-10-10 實際踩到）。開發期不想起後端就在指令列出其他三個服務。
- project name 不要改成 `livefit`：本機已有同名的 compose project（別的練習專案），會共用到它的 volume。

### 測試

```bash
npm test              # 全部 68 項
npm run test:m1       # 單一里程碑，m1 ~ m6
npm run test:smoke    # 容器化階段用
npx jest test/m5.test.js -t "報名"   # 跑單一測試
```

測試是**黑箱合約測試**（supertest + HTTP），只打 API、不碰資料庫。base URL 由 `test/helpers.js` 的 `API_BASE_URL` 環境變數決定，預設 `http://localhost:8080` —— 所以同一套測試可以拿來驗 Spring Boot 版：

```bash
API_BASE_URL=http://localhost:8085 npm run test:m1
```

## 架構要點

### 兩套後端各用各的資料庫

專案內**沒有任何 SQL 或 migration 檔**，兩邊都靠 ORM 自動建表，但連的是同一個 postgres 容器裡的**不同資料庫**：

| 後端 | 資料庫 | 建表方式 |
|---|---|---|
| `backend/` | `fitness` | TypeORM `synchronize: true`（`DB_SYNCHRONIZE=true`） |
| `livefit/` | `livefit` | Hibernate `ddl-auto=update`（`.env` 的 `DDL_AUTO`） |

- 上表是用根目錄 `docker-compose.yml` 的情況。改用 `compose.livefit.yml` 時是另一個 postgres 容器與 volume，裡面只有 `livefit` 資料庫且會自動建立，下面這一點不適用。
- **`livefit` 資料庫不會自動建立**：postgres 容器只建 `fitness`。`pgData` volume 清空後（例如 `npm run db:reset`）要重新執行 `docker compose exec postgres psql -U student -d fitness -c "CREATE DATABASE livefit"`，否則 Spring Boot 啟動會失敗。
- **不要把 `livefit/.env` 的 `DB_DATABASE` 指回 `fitness`**：`ddl-auto=update` 會去改 Node 版的 schema。
- 兩個資料庫的資料不互通，帳號要各自註冊。
- `ddl-auto=update` 只增不改：新增欄位會自動補上，但改欄位型別、長度、nullable 不會套用到既有的表，需要手動 `ALTER TABLE`。

容易踩到的 schema 細節：`course` 是**單數**表名（其他多為複數）、`Course.user_id` 指向 **`User.id` 而非 `Coach.id`**、`credit_purchase.price_paid` 是 `numeric(10,2)` 而 `credit_packages.price` 是 `integer`、`course_booking` 的 `booking_at` 與 `created_at` 兩個都是建立時間。`livefit` 的 `users.password` 可為 null、多了 `email_verified` 與 `token_version` 兩個欄位，並多一張 `user_identities` 表記錄第三方登入的綁定（`provider` + `provider_user_id`）。舊的 `users.google_sub` 欄位已從本機資料庫刪除；較早建立的其他環境若還有，照 `docs/springboot-migration-plan.md` §11.7 處理。

### 兩套後端的分層差異

`backend/` 把 controller 與業務邏輯寫在一起（`backend/controllers/*.js` 同時做驗證、DB 查詢、組回應），路由在 `backend/routes/`，驗證靠 `middlewares/isAuth.js` + `isCoach.js`。

`livefit/` 刻意拆開：controller 只做路由對映與 `ApiResponse` 包裝，**所有驗證與業務邏輯都在 service**，錯誤一律丟 `ApiException` 由 `GlobalExceptionHandler` 轉成統一格式。Security 規則集中在 `config/SecurityConfig.java`。

### Spring Security 的兩個非直覺設計

1. **`anyRequest()` 用 `permitAll`**，需登入的端點在 `SecurityConfig` 逐條明列。這是為了讓未定義路徑落到 `NoHandlerFoundException` 回 404 `無此路由`，與 Node 逐路由掛 middleware 的行為一致。**新增需登入的端點時必須同步加進 matcher 清單**，否則會變成公開的。
2. **matcher 順序有陷阱**：`POST /api/admin/coaches/courses`（建立課程，需教練）與 `POST /api/admin/coaches/{userId}`（升級教練，免登入）路徑形狀相同，前者必須排在前面。Controller 端由 Spring MVC 字面路徑優先解析，不受影響，但 Security 是依序比對。

其他對齊 Node 行為的地方：權限不足回 **401 而非 403**（`使用者尚未成為教練`）；JWT 驗證後仍回 DB 撈 user，**角色以 DB 當下的 `role` 為準**，不信任 token 內的 role。

### 尾斜線必須可比對

Spring Framework 6（Boot 3）起改用 `PathPatternParser`，**預設不再把 `/api/coaches` 與 `/api/coaches/` 視為等價**，但 Express 預設兩者皆可，而前端依賴這個行為 —— `frontend/src/api/coaches.js` 的教練列表打的是 `coaches/?per=&page=`（帶尾斜線）。

`livefit/config/WebMvcConfig.java` 用 `setUseTrailingSlashMatch(true)` 補回這個行為。該 API 在 Spring 6 已 deprecated、預計 Spring 7 移除，**升級到 Boot 4 時要改成在各 `@RequestMapping` 明列 `{"", "/"}`，或在反向代理做 301 轉址**。

### Google 登入（僅 `livefit/`）

`POST /api/users/google` 收前端 Google Identity Services 給的 ID token（`credential`），由 `security/GoogleIdTokenVerifier` 用 Google 公鑰驗簽後，簽發與一般登入**相同格式**的自家 JWT。後端維持 STATELESS，沒有 redirect / session 流程，也不需要 client secret。

- **能不能用密碼登入只看 `users.password` 是否為 null**，與有沒有綁 Google 無關。純第三方登入建立的帳號、以及被接管過的帳號（見下方「帳號接管」）`password` 為 null：密碼登入回 400，`PUT /api/users/password` 變成不需舊密碼的「設定密碼」。**新增任何會讀 `user.getPassword()` 的邏輯時要處理 null**。
- 帳號對應邏輯在 `UserService.socialLogin`，各平台共用：驗證完組成 `SocialProfile` 交給它即可。`provider` 用 `UserIdentity.PROVIDER_*` 字串常數，**不要改成 enum**（Hibernate 會建 check 約束，`ddl-auto=update` 不會更新它）。
- 綁定既有帳號的前提是 Google 回傳 `email_verified=true`，這個檢查不能拿掉，否則能用未驗證的信箱接管別人的帳號。
- `GOOGLE_CLIENT_ID`（後端）與 `VITE_GOOGLE_CLIENT_ID`（前端）必須是**同一個值**；後端留空時端點回 400 `尚未設定 Google 登入`，前端留空時不顯示 Google 按鈕。
- 前端的 `VITE_*` 是 **build-time** 變數：本機 `npm run dev` 讀 `frontend/.env`；容器化的前端要靠 `docker-compose.yml` 的 build arg 並重新 build。
- Google Cloud Console 的 Authorized JavaScript origins 要登記實際開啟頁面的 origin（`http://localhost:5173`、`http://localhost:3000`；`localhost` 與 `127.0.0.1` 視為不同 origin）。

### 帳號接管（僅 `livefit/`）

`users.email_verified` 記錄信箱是否確認過屬於本人：Google、GitHub 建立的帳號為 true，密碼註冊與 Facebook 建立的為 false。Google 或 GitHub 登入以 email 找到 **未驗證** 的帳號時，`UserService.takeOver` 會清掉密碼、刪除既有的所有綁定、`token_version` 加一（舊 JWT 全部失效）、標成已驗證，然後才綁定；找到已驗證的帳號則只新增綁定、密碼保留。設計理由與已知代價在 `docs/springboot-migration-plan.md` §11.8。

- **會修改 `User` 再存回去的程式一律用 `findByIdForUpdate` / `findByEmailForUpdate`**（要在交易內）。Hibernate 是整列寫回，沒鎖的話會把剛被接管清掉的密碼與舊的 `token_version` 寫回去。
- **登入後的寫入要經過 `UserService.lockCurrentUser`**，它在鎖住該列後再比對一次 `token_version`。filter 的檢查與 service 的寫入不在同一個交易，只靠 filter 擋不住接管當下還在路上的請求。
- 簽 token 一律用 `JwtTokenProvider.createToken(User)`，payload 的 `ver` 就是 `token_version`。沒有 `ver` 的舊 token 視為 0。
- 無密碼帳號設定密碼要求這次登入在 5 分鐘內（看 token 的 `iat`），這是取代「驗舊密碼」的把關，不要拿掉。
- `email_verified` 只能由驗證過的平台登入設成 true。不要在密碼註冊、Facebook 登入、或「這個平台帳號登入過」的快速路徑把它設成 true。
- 既有資料庫升級要跑 §11.8 的 SQL。

### GitHub 登入（僅 `livefit/`）

`POST /api/users/github` 收 `{ code, redirect_uri }`。GitHub 沒有可離線驗簽的 ID token，所以 `security/GithubOAuthClient` 要拿 code + client secret 連 GitHub 三次（換 token、`/user`、`/user/emails`），再把結果交給與 Google 共用的 `UserService.socialLogin`。後端一樣是 STATELESS，`state` 由前端存在 `sessionStorage` 並在 `/oauth/callback/:provider` 自行比對。

- **GitHub 換 token 失敗時回 HTTP 200**，錯誤在 body 的 `error` 欄位。判斷依據是有沒有 `access_token`，不要改成只看狀態碼。
- **只採用 `primary && verified` 的 email**。這是能綁定既有帳號的前提，理由與 Google 的 `email_verified` 相同，不能放寬成「任一個驗證過的 email」以外的條件，更不能用 `/user` 回傳的公開 email（那個沒有驗證旗標）。
- `GITHUB_CLIENT_ID`（後端）與 `VITE_GITHUB_CLIENT_ID`（前端）必須相同；`GITHUB_CLIENT_SECRET` **只放後端**。後端任一留空時端點回 400 `尚未設定 GitHub 登入`，前端留空時不顯示按鈕。
- 前端送來的 `redirect_uri` 必須在 `OAUTH_REDIRECT_URIS` 白名單內，且與 GitHub OAuth App 登記的 callback URL 完全相同（`http://localhost:5173/oauth/callback/github`）。容器版前端（3000）能否共用同一個 OAuth App 尚未實測，不行就另建一個。
- `GithubOAuthClient` 的逾時（連線 2 秒、讀取 3 秒）是配合前端 axios 的 10 秒設的，調高前先算三次呼叫的總和。
- 加新的 authorization code 平台：後端加一個 `XxxOAuthClient` 回傳 `SocialProfile` 與一個 `UserIdentity.PROVIDER_*` 常數；前端在 `config/oauthProviders.js` 加一筆、在 `SocialLoginButtons.vue` 加一個按鈕、在 `routeTable.js` 的白名單加路徑。

### Facebook 登入（僅 `livefit/`）

`POST /api/users/facebook`，流程與 GitHub 相同，`UserService` 裡兩者共用 `oauthCodeLogin`，只差在 `security/FacebookOAuthClient`（連 Facebook 兩次：換 token、`/me`）。

- **Facebook 不提供 email 是否驗證過的旗標**，`FacebookOAuthClient` 一律回 `emailVerified=false`。結果是：登入過的 Facebook 帳號照常登入、全新的 email 會建立帳號，但 **email 已有帳號時回 409 `此 Email 已註冊，請改用原本的方式登入`，不自動綁定**。不要為了方便把它改成 true，那等於讓人用未驗證的信箱接管帳號。
- 反方向的接管（先用未驗證信箱建帳號，等本人用 Google/GitHub 登入後被綁進來）由上方「帳號接管」處理：本人登入時 Facebook 的綁定會被刪掉。
- Facebook 帳號可能沒有 email，這時回 400；`users.email` 不可為 null。
- 換 token 是 GET、**密鑰在 query string**，不要把完整網址寫進 log。
- Graph API 版本 `v26.0` 寫在兩處（`FacebookOAuthClient` 與前端 `config/oauthProviders.js`），要一起改。
- `FACEBOOK_APP_ID`（後端）與 `VITE_FACEBOOK_APP_ID`（前端）必須相同；`FACEBOOK_APP_SECRET` 只放後端。
- Facebook App 在開發模式下只有具應用程式角色的帳號能登入。

### 密碼雜湊格式相容，但資料與 token 都不互通

Node 用 `bcryptjs`（cost 10），Spring 用 `BCryptPasswordEncoder(10)`，雜湊格式相容。但兩邊連的是不同資料庫，**使用者要各自註冊**；JWT secret 也不同，**token 不能跨後端使用**。

## 環境變數

| 位置 | 讀取方式 |
|---|---|
| `backend/.env` | `dotenv`，範本在根目錄 `.env.example` |
| `livefit/.env` | Spring Boot 原生 `spring.config.import=optional:file:.env[.properties]`，範本 `livefit/.env.example` |
| `frontend/.env` | Vite（僅本機 `npm run dev` / `npm run build`），範本 `frontend/.env.example` |

`livefit/.env` 是 **Java properties 格式**：`KEY=value`，不加引號、不寫 `export`（與根目錄那份給 `dotenv` 用的規則不同）。`JWT_SECRET` **必填且沒有預設值**，留空或少於 32 個位元組時 `JwtTokenProvider` 會在啟動時拋錯；用 `openssl rand -hex 32` 產生（說明在 `docs/springboot-migration-plan.md` §2）。**不要在 `application.properties` 加回預設密鑰**。

## 注意事項

- **`backend/` 的 port 固定 8080，不可更動** —— 前端寫死 `http://127.0.0.1:8080/api/`，Swagger 的 Try it out 與 CI 驗收也都用這個 port。`livefit/` 的 port 由 `.env` 的 `PORT` 控制；兩者若都設 8080 就不能同時啟動
- **README 宣告不可修改**：`frontend/`、`docs/`、`test/`、`.github/`、根目錄 `package.json` 與 `package-lock.json`。`docker-compose.yml` 是例外（容器化階段要加 backend 服務）。這條規則是針對 `main` 上的 Node 作業驗收；Spring Boot 分支為了 Google 登入已改過 `frontend/` 與 `docs/openapi.yaml`，**這些改動不要 merge 回 `main`**
- `POST /api/upload` 是 openapi 標明的加分題，不列入驗收，兩套後端都未實作
