# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 這個 repo 是什麼

六角學院「健身房網站後端」最終作業（fork 自 `hexschool/node-js-final-2026`）。前端、Swagger 文件、驗收測試都是題目給定的，**要寫的是後端**。

目前有**兩套功能等價的後端實作**：

| 目錄 | 技術 | 狀態 |
|---|---|---|
| `backend/` | Node.js + Express 5 + TypeORM | 正式繳交用，GitHub Actions 驗收的對象 |
| `livefit/` | Spring Boot 3.5 + Spring Data JPA + Spring Security | `springboot-backend` 分支上的移植版，不列入作業驗收 |

兩者實作同一份 API 規格，差異記錄在 `docs/springboot-migration-plan.md` §8。`feature/social-login` 起 Spring Boot 版另外多了 **Google 登入**並改用**獨立資料庫**（同文件 §11），這兩項 Node 版沒有。

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

- **`livefit` 資料庫不會自動建立**：postgres 容器只建 `fitness`。`pgData` volume 清空後（例如 `npm run db:reset`）要重新執行 `docker compose exec postgres psql -U student -d fitness -c "CREATE DATABASE livefit"`，否則 Spring Boot 啟動會失敗。
- **不要把 `livefit/.env` 的 `DB_DATABASE` 指回 `fitness`**：`ddl-auto=update` 會去改 Node 版的 schema。
- 兩個資料庫的資料不互通，帳號要各自註冊。
- `ddl-auto=update` 只增不改：新增欄位會自動補上，但改欄位型別、長度、nullable 不會套用到既有的表，需要手動 `ALTER TABLE`。

容易踩到的 schema 細節：`course` 是**單數**表名（其他多為複數）、`Course.user_id` 指向 **`User.id` 而非 `Coach.id`**、`credit_purchase.price_paid` 是 `numeric(10,2)` 而 `credit_packages.price` 是 `integer`、`course_booking` 的 `booking_at` 與 `created_at` 兩個都是建立時間。`livefit` 的 `users` 表比 Node 版多一個 `google_sub`，且 `password` 可為 null。

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

- **各帳號能做什麼只看 `users.password` 是否為 null**，與有沒有綁 Google 無關。先用密碼註冊、之後用同 email 的 Google 登入會自動綁定（只寫入 `google_sub`，密碼保留），兩種登入與修改密碼都照常可用。純 Google 建立的帳號 `password` 為 null，密碼登入與修改密碼都回 400。**新增任何會讀 `user.getPassword()` 的邏輯時要處理 null**。
- 綁定既有帳號的前提是 Google 回傳 `email_verified=true`，這個檢查不能拿掉，否則能用未驗證的信箱接管別人的帳號。
- `GOOGLE_CLIENT_ID`（後端）與 `VITE_GOOGLE_CLIENT_ID`（前端）必須是**同一個值**；後端留空時端點回 400 `尚未設定 Google 登入`，前端留空時不顯示 Google 按鈕。
- 前端的 `VITE_*` 是 **build-time** 變數：本機 `npm run dev` 讀 `frontend/.env`；容器化的前端要靠 `docker-compose.yml` 的 build arg 並重新 build。
- Google Cloud Console 的 Authorized JavaScript origins 要登記實際開啟頁面的 origin（`http://localhost:5173`、`http://localhost:3000`；`localhost` 與 `127.0.0.1` 視為不同 origin）。

### 密碼雜湊格式相容，但資料與 token 都不互通

Node 用 `bcryptjs`（cost 10），Spring 用 `BCryptPasswordEncoder(10)`，雜湊格式相容。但兩邊連的是不同資料庫，**使用者要各自註冊**；JWT secret 也不同，**token 不能跨後端使用**。

## 環境變數

| 位置 | 讀取方式 |
|---|---|
| `backend/.env` | `dotenv`，範本在根目錄 `.env.example` |
| `livefit/.env` | Spring Boot 原生 `spring.config.import=optional:file:.env[.properties]`，範本 `livefit/.env.example` |
| `frontend/.env` | Vite（僅本機 `npm run dev` / `npm run build`），範本 `frontend/.env.example` |

`livefit/.env` 是 **Java properties 格式**：`KEY=value`，不加引號、不寫 `export`（與根目錄那份給 `dotenv` 用的規則不同）。`JWT_SECRET` 必須 **≥32 個位元組**，否則 `JwtTokenProvider` 會在啟動時拋錯。

## 注意事項

- **`backend/` 的 port 固定 8080，不可更動** —— 前端寫死 `http://127.0.0.1:8080/api/`，Swagger 的 Try it out 與 CI 驗收也都用這個 port。`livefit/` 的 port 由 `.env` 的 `PORT` 控制；兩者若都設 8080 就不能同時啟動
- **README 宣告不可修改**：`frontend/`、`docs/`、`test/`、`.github/`、根目錄 `package.json` 與 `package-lock.json`。`docker-compose.yml` 是例外（容器化階段要加 backend 服務）。這條規則是針對 `main` 上的 Node 作業驗收；Spring Boot 分支為了 Google 登入已改過 `frontend/` 與 `docs/openapi.yaml`，**這些改動不要 merge 回 `main`**
- `POST /api/upload` 是 openapi 標明的加分題，不列入驗收，兩套後端都未實作
