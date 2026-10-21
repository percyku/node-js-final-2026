# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 這個 repo 是什麼

六角學院「健身房網站後端」最終作業（fork 自 `hexschool/node-js-final-2026`）。前端、Swagger 文件、驗收測試都是題目給定的，**要寫的是後端**。

目前有**兩套功能等價的後端實作**：

| 目錄 | 技術 | 狀態 |
|---|---|---|
| `backend/` | Node.js + Express 5 + TypeORM | 正式繳交用，GitHub Actions 驗收的對象 |
| `livefit/` | Spring Boot 3.5 + Spring Data JPA + Spring Security | `springboot-backend` 分支上的移植版，不列入作業驗收 |

兩者實作同一份 API 規格，差異記錄在 `docs/springboot-migration-plan.md` §8。Spring Boot 版另有 Postman Collection 與 Java 測試，分工見下方「測試的分工」。`feature/admin-role` 起 Spring Boot 版再多了 **`ADMIN` 角色與 M7 管理員後台**（同文件 §13）。`feature/social-login` 起 Spring Boot 版另外多了 **Google、GitHub、Facebook 登入**並改用**獨立資料庫**（同文件 §11），這些 Node 版沒有。

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
cd livefit && mvn test                # 全部 Java 測試，需要 postgres 與 livefit 資料庫在線，不需要啟動後端
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

### Postman Collection（僅 `livefit/`）

`postman/` 是針對 Spring Boot 版的端到端測試，涵蓋 M0 ~ M7 與第三方登入的失敗案例（根目錄的 jest 測試沒有 M7）。Node 版不適用。

```bash
npm run postman:build    # 產生 collection 與 environment；兩個 JSON 不進版本庫，剛 clone 下來要先跑這個
npm run postman:test     # 用 newman 整份執行，需要 postgres 與 livefit 在線
npm run postman:test -- --env-var baseUrl=http://localhost:8085   # 換受測位址
```

在 Postman 裡：Import `postman/livefit.postman_collection.json` → 在 collection 上按 Run。不需要手動登入或填 token。

- **兩個 JSON 都是 `postman/build-collection.js` 產生的，列在 `.gitignore`、不進版本庫**。不要直接改 JSON，改腳本後跑 `npm run postman:build`；`postman:test` 不會自動產生，檔案不存在時要先跑 build。
- **斷言有兩層**。流程斷言寫在腳本裡，照 livefit 的實際行為檢查狀態碼、訊息與資料；名稱以 `[openapi]` 開頭的是 collection 層級的 script，拿每支回應比對 `docs/openapi.yaml`。
- **以下情況要重新產生**：改了 `docs/openapi.yaml`（openapi 驗證是從它讀出來的）；改了 `ErrorMessages.java` 的訊息（要同步改腳本開頭的 `MSG`）；新增端點（在腳本對應的資料夾加請求）。
- **要整份依序執行**，請求之間靠 collection 變數串接 token 與 id，只跑中間某個資料夾會拿到上一輪的舊值。
- **每跑一輪會在 `livefit` 資料庫留下資料**：6 個帳號（含 1 個管理員）、1 個技能、1 個方案、3 堂課，Email 都是 `pm_*@example.com`。使用者與課程沒有刪除 API，要清空就重建資料庫。
- M7 用預設管理員登入，帳密是 collection 變數 `adminEmail` / `adminPassword`；改過預設管理員的密碼要跟著改，否則 M7 整段失敗。`livefit.postman_environment.json` 是選用的，用來覆蓋這些值。
- openapi 的回應 schema 沒有寫 `required`，約三分之一的回應只有範例沒有 schema。腳本因此把文件列出的欄位都當成必須出現，沒有 schema 的從範例推結構。在 openapi 補上 schema 後驗證會自動變嚴。
- 腳本讀 YAML 用的 `js-yaml` 是 jest 帶進來的間接相依，沒有寫進 `package.json`。
- 在 sandbox 裡連不到 localhost，檢查連接埠會誤判成後端沒開；執行 `postman:test` 要在 sandbox 外。

### 測試的分工（僅 `livefit/`）

三套測試測的層面不同，**不要追求每一套都涵蓋全部**。同一件事寫三遍不會變三倍保險，只會讓改一句錯誤訊息要改三個地方。

| 測試 | 位置 | 負責 | 需要 |
|---|---|---|---|
| jest 黑箱測試 | 根目錄 `test/` | M1 ~ M6 的作業驗收合約。題目給的，不動 | 後端在線 |
| Postman Collection | `postman/` | M0 ~ M7 串成一條流程、回應是否符合 `docs/openapi.yaml` | 後端與資料庫在線 |
| Java 測試 | `livefit/src/test/` | 黑箱測不到的：資料庫裡實際寫了什麼、同時送出的請求、時間邊界、計算的邊界；以及 M7、第三方登入與帳號接管 | 多數要資料庫在線，不需要啟動後端 |

- **不要在 Java 用 MockMvc 把 M1 ~ M6 的「打 API 看狀態碼與訊息」再寫一遍**，那和 jest、Postman 重複。
- **不要在根目錄 `test/` 加 M7 的 jest 測試**：Node 版沒有 M7，加了之後 `npm test` 對 Node 後端會失敗。
- M7 有兩個地方驗：`AdminUserApiTest`（測完會清資料）與 Postman 的 M7 資料夾（資料會留下）。改 M7 的邏輯時兩個都跑；只跑一個就選 `mvn test`。

寫 Java 測試要注意的事：

- **連的是開發用的 `livefit` 資料庫，不是另外的測試資料庫。** 每個測試都要在 `@AfterEach` 呼叫 `fixtures.cleanUp()`；新增會建資料的 helper 時，要把 id 記進 `BookingFixtures` 的清單並在 `cleanUp` 依外鍵順序刪。測試建的資料 Email 是 `svc-test-*@example.com`、名稱以「測試」開頭，中斷後有殘留可以用這個找。
- **後端沒有可替換的時鐘，都是直接呼叫 `Instant.now()`。** 要測「報名發生在某個時間點」只能事後用 SQL 改 `course_booking.created_at`。欄位是 `timestamp without time zone`、內容是 UTC，要用 `CAST(? AS timestamp)` 搭配字串，不要傳 `Timestamp` 物件（JDBC 會依 JVM 時區換算）。
- **營收金額不要連著真的資料庫測。** 均價是資料庫裡「所有」方案的 Σprice ÷ Σcredit_amount，別人建的方案也會算進去，所以金額只在 `CoachRevenueCalculationTest` 用假的 repository 驗。
- 併發測試用 `@RepeatedTest`，只跑一次可能剛好通過。改 `book` 之後若偶爾失敗，那就是真的有競爭，不要當成不穩定的測試而忽略。

### CI（僅 `livefit/`）

`.github/workflows/livefit-test.yml` 在 GitHub Actions 上跑 `mvn test`。只跑 Java 測試、不啟動後端，jest 與 Postman 不在 CI 上。

- 觸發條件：push 到 `springboot-backend`，或開 PR 到 `springboot-backend`，而且改到 `livefit/**` 或這個 workflow 檔。只改文件不會跑。沒有手動觸發，因為 `workflow_dispatch` 要求 workflow 檔在預設分支 `main` 上。
- **這個檔案不要 merge 回 `main`**：`.github/` 是 README 宣告不可修改的目錄。`main` 上的 `test.yml` 是題目給的 Node 版驗收，不要動它。
- CI 上沒有 `livefit/.env`。資料庫連線寫在 workflow 的 `env`，`JWT_SECRET` 每次執行用 `openssl rand -hex 32` 產生。**`application.properties` 新增沒有預設值的必填設定時，要同步加進 workflow**，否則 Spring context 起不來，連資料庫的測試會全部失敗。
- 每次都是全新的空資料庫，表由 `ddl-auto=update` 建立，裡面只會有 `AdminSeeder` 建的預設管理員。新寫的測試要自己建資料，不能假設資料庫裡原本就有東西。
- 失敗時 surefire 報告會上傳成 artifact `surefire-reports`。

### Subagent（`.claude/agents/`，僅 `livefit/`）

三個專案層級的 subagent，為了 CHANGELOG 2026-10-21 CI 那筆「還沒做的」三項（CI 第二階段、Jackson 型別轉換、選用的架構調整）而建，檢查清單都是針對 `livefit/` 寫的。

| Subagent | 負責 | 工具 | 呼叫時要給 |
|---|---|---|---|
| `security-reviewer` | 權限、身分驗證、輸入驗證、CI 機密 | `Read, Grep, Glob` | `git diff` 的內容或變更檔案清單 |
| `code-reviewer` | 業務邏輯與邊界、是否符合 `docs/openapi.yaml`、測試有沒有被刪除 / 跳過 / 放寬 | `Read, Grep, Glob` | `git diff` 的內容或變更檔案清單，以及改動的目的（例如「重構，不應改變行為」） |
| `test-runner` | 執行 `mvn test`、jest、Postman，只回報失敗項目、位置與可能原因 | `Bash, Read`，`model: haiku` | 要跑哪一套，或哪一個測試 |

- 建議的順序：改完程式 → `test-runner` → 通過後把 diff 交給兩個 reviewer。兩個 reviewer 的範圍不重疊，可以同時跑。
- **兩個 reviewer 自己跑不了 `git diff`**，沒附 diff 或檔案清單時會直接回報「缺少審查範圍」。不要為了讓它們能跑 git 而加上 `Bash`：本機的 Claude Code 在 macOS 上預設沒有 `Grep`、`Glob`，搜尋走 Bash；subagent 的 `tools` 列了這兩個而沒列 `Bash` 時才會單獨拿到它們。加上 `Bash` 之後這兩個工具會消失，reviewer 也就能透過 shell 改檔案了。
- `test-runner` 有 Bash，「不改檔案、不起停後端與資料庫」是寫在 prompt 裡的規則，不是工具限制。前置條件不滿足時它只回報，不會自己處理。
- `test-runner` 執行前會打 `GET /api/admin/users` 確認 8080 上是哪個後端：livefit 回 401 `請先登入`，Node 版沒有這條路由、回 404 `無此路由`。
- 檢查清單裡的規則是從本檔抄過去的（四句錯誤訊息、上鎖順序、營收算法、測試的分工、帳號接管等）。**改了本檔的這些規則時，要同步改 `.claude/agents/` 裡對應的那一行**，否則 reviewer 會拿舊規則審查。
- 改了 agent 檔案，下一次呼叫就會用新的內容，不需要重開 session。

### 報名的鎖與月營收的算法（僅 `livefit/`）

- `CourseService.book` 進入後先鎖使用者、再鎖課程（`findByIdForUpdate`）。鎖使用者擋重複報名與堂數超扣，鎖課程擋超過名額。**順序固定是先使用者、後課程**，之後若有別的地方也要同時鎖這兩種資料列，必須用同樣的順序，否則會死結。
- `book` 寫入要用 `saveAndFlush`，不要改回 `save`：`save` 的 INSERT 拖到交易提交才送出，唯一約束的例外會接不到而變成 500。
- 取消報名沒有上鎖。同一筆同時取消兩次都會回成功，結果仍是已取消，資料不會錯。
- 月營收（`AdminCoachService.getRevenue`）**一定要先乘筆數再除總堂數**，不要為了可讀性改回先算均價：1000 元 3 堂、3 筆報名先除會算成 999，照 openapi 的公式應該是 1000。

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
- `ddl-auto=update` 只增不改：新增欄位會自動補上，但改欄位型別、長度、nullable 不會套用到既有的表，需要手動 `ALTER TABLE`。有名稱的唯一約束（`@Table(uniqueConstraints=...)`）會補到既有的表。

容易踩到的 schema 細節：`course` 是**單數**表名（其他多為複數）、`Course.user_id` 指向 **`User.id` 而非 `Coach.id`**、`credit_purchase.price_paid` 是 `numeric(10,2)` 而 `credit_packages.price` 是 `integer`、`course_booking` 的 `booking_at` 與 `created_at` 兩個都是建立時間。`livefit` 的 `course_booking` 另有 `(user_id, course_id)` 的唯一約束（已取消的也算），它只是 `CourseService.book` 上鎖之外的第二道防線，名額與堂數仍然靠鎖；既有環境的升級方式在 `docs/springboot-migration-plan.md` §14。`livefit` 的 `users.password` 可為 null、多了 `email_verified` 與 `token_version` 兩個欄位，並多一張 `user_identities` 表記錄第三方登入的綁定（`provider` + `provider_user_id`）。舊的 `users.google_sub` 欄位已從本機資料庫刪除；較早建立的其他環境若還有，照 `docs/springboot-migration-plan.md` §11.7 處理。

### 兩套後端的分層差異

`backend/` 把 controller 與業務邏輯寫在一起（`backend/controllers/*.js` 同時做驗證、DB 查詢、組回應），路由在 `backend/routes/`，驗證靠 `middlewares/isAuth.js` + `isCoach.js`。

`livefit/` 刻意拆開：controller 只做路由對映與 `ApiResponse` 包裝，**所有驗證與業務邏輯都在 service**，錯誤一律丟 `ApiException` 由 `GlobalExceptionHandler` 轉成統一格式。Security 規則集中在 `config/SecurityConfig.java`。

### Spring Security 的兩個非直覺設計

1. **`anyRequest()` 用 `permitAll`**，需登入的端點在 `SecurityConfig` 逐條明列。這是為了讓未定義路徑落到 `NoHandlerFoundException` 回 404 `無此路由`，與 Node 逐路由掛 middleware 的行為一致。**新增需登入的端點時必須同步加進 matcher 清單**，否則會變成公開的。
2. **matcher 順序有陷阱**：`POST /api/admin/coaches/courses`（建立課程，需教練）與 `POST /api/admin/coaches/{userId}`（升級教練，免登入）路徑形狀相同，前者必須排在前面。Controller 端由 Spring MVC 字面路徑優先解析，不受影響，但 Security 是依序比對。

其他對齊 Node 行為的地方：教練端點權限不足回 **401 而非 403**（`使用者尚未成為教練`）；JWT 驗證後仍回 DB 撈 user，**角色以 DB 當下的 `role` 為準**，不信任 token 內的 role。

### ADMIN 角色與 M7 管理員後台（僅 `livefit/`）

需要 `ADMIN` 角色的端點：`GET /api/admin/users`（使用者分頁列表，`keyword` 可搜尋名稱或 Email）、`POST /api/admin/users/{userId}/coach`（升級為教練）、`POST /api/admin/users/admins`（建立管理員）、`POST /api/admin/users/{userId}/reset-password`（重設管理員密碼）。M3 公開的 `POST /api/admin/coaches/{userId}` 暫時保留。設計理由在 `docs/springboot-migration-plan.md` §13。

- **ADMIN 只有兩個來源**：`config/AdminSeeder` 在啟動時建立的預設管理員，以及既有管理員用 `POST /api/admin/users/admins` 建立的。沒有把既有帳號升級成 ADMIN 的 API，也不要加：管理員不能用第三方登入，沒有密碼的帳號升級後會進不來。預設管理員的信箱與密碼來自 `ADMIN_EMAIL` / `ADMIN_PASSWORD`，預設 `admin@gmail.com` / `Admin123`（email 存小寫）。帳號已存在時 seeder 完全不動，所以**改 `.env` 不會更新既有管理員的密碼**。
- **權限不足回 403** `需要管理員權限`，與教練端點的 401 不同。`CoachAccessDeniedHandler` 是用路徑前綴 `/api/admin/users` 判斷的，**新增需要 ADMIN 的端點要放在這個前綴底下**，並加進 `SecurityConfig` 的 matcher 清單。
- **管理員不能兼任教練**，一個帳號只有一種角色，這是刻意的（理由在 §13.6）；同一個人需要兩種身分時用兩個不同 Email 的帳號。前端 `/coach/*` 只有教練能進，`/admin/promote-trainer` 對管理員隱藏（路由 `meta.excludedRole`）。
- **兩條升級路徑都只接受 `role == USER`**（`AdminCoachService.promote`）。這個檢查不能拿掉：M3 那支不用登入，少了它任何人都能把管理員改成教練。
- M7 的欄位規則比 M3 寬（年資可為 0、簡介可空白），且升級後會把對方的 `token_version` 加一讓他重新登入；M3 升級教練的行為沒有變。
- 用預設值升級的教練，簡介、照片、技能都是空的，而 `PUT /api/admin/coaches` 這三項必填（年資依 openapi 可為 0）。後端只回 `欄位未填寫正確`，所以 `pages/coach/ProfileView.vue` 在送出前自己檢查並指出缺哪一欄；改這支的驗證規則時兩邊要一起改。
- **ADMIN 不能用第三方登入**（`UserService.linkOrCreateSocialUser` 回 409）。預設管理員的信箱不保證是自己的、又是 `email_verified=false`，不擋的話信箱主人用 Google 登入就會經由「帳號接管」拿到管理員權限。
- **重設密碼固定設成 `Admin123`**（`AdminUserService.RESET_PASSWORD`）並把對方的 `token_version` 加一。只能對其他管理員用，不能對自己、也**不要開放到 USER / COACH**（那等於管理員能登入任何人的帳號）。固定密碼只適合本機，理由與唯一管理員忘記密碼的處理方式在 §13.5。
- 前端頁面是 `/admin/users`（`pages/admin/UsersView.vue`）；管理員改自己密碼的入口是 `/admin/profile`，重用會員中心的 `pages/user/ProfileView.vue`。兩個路由都是 `meta.requiredRole = "ADMIN"`。`/admin` 底下原有的三個頁面仍是登入即可進入。
- 根目錄的黑箱測試沒有 ADMIN 的案例，M7 的測試在 `livefit/src/test/.../service/AdminUserApiTest.java`。

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

`livefit/.env` 是 **Java properties 格式**：`KEY=value`，不加引號、不寫 `export`（與根目錄那份給 `dotenv` 用的規則不同）。`ADMIN_EMAIL` / `ADMIN_PASSWORD` 選填，不寫時用 `application.properties` 的預設值。`JWT_SECRET` **必填且沒有預設值**，留空或少於 32 個位元組時 `JwtTokenProvider` 會在啟動時拋錯；用 `openssl rand -hex 32` 產生（說明在 `docs/springboot-migration-plan.md` §2）。**不要在 `application.properties` 加回預設密鑰**。

## 注意事項

- **`backend/` 的 port 固定 8080，不可更動** —— 前端寫死 `http://127.0.0.1:8080/api/`，Swagger 的 Try it out 與 CI 驗收也都用這個 port。`livefit/` 的 port 由 `.env` 的 `PORT` 控制；兩者若都設 8080 就不能同時啟動
- **README 宣告不可修改**：`frontend/`、`docs/`、`test/`、`.github/`、根目錄 `package.json` 與 `package-lock.json`。`docker-compose.yml` 是例外（容器化階段要加 backend 服務）。這條規則是針對 `main` 上的 Node 作業驗收；Spring Boot 分支為了 Google 登入已改過 `frontend/` 與 `docs/openapi.yaml`，`feature/postman-collection` 起根目錄 `package.json` 也多了 `postman:*` 兩個指令，**這些改動不要 merge 回 `main`**
- `POST /api/upload` 是 openapi 標明的加分題，不列入驗收，兩套後端都未實作
