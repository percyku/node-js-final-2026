# LiveFit Spring Boot 移植 — 工作紀錄

> 這份是「**做了什麼**」的變更紀錄。
> 架構設計、API 規格、Entity 對映等細節請看 [`springboot-migration-plan.md`](./springboot-migration-plan.md)。

- **日期**：2026-09-20
- **分支**：`springboot-backend`
- **目標**：在 `./livefit` 從零建一套 Spring Boot 後端，完整複製 Node.js 後端（`./backend`）的業務邏輯，改成 MVC 三層分離架構 + Spring Security + JWT

---

## 一、開工前的調查

逐檔讀完 `./backend` 的 `app.js`、6 支 controller、6 個 route、8 個 entity、2 個 middleware、`config/` 與 `utils/`，並解析 `docs/openapi.yaml`（2917 行）。

確認的關鍵事實：

- 28 支 API 的完整規格（路徑、權限、request/response 欄位、錯誤訊息與狀態碼）
- **四句不可更動一字的固定錯誤訊息**：`已經報名過此課程`、`已無可使用堂數`、`已達最大參加人數，無法參加`、`請先登入`
- JWT payload 必須含 `{id, role, exp}`（前端會自行 decode）
- 8 張資料表的實際結構（`course` 是單數表名、`credit_purchase.price_paid` 是 `numeric(10,2)`、所有時間欄位為 `timestamp without time zone`）
- 資料表是由 Node 端 TypeORM `synchronize: true` 建立的，專案內**沒有任何 SQL / migration 檔**

### 開工前確認的四個決策

| 項目 | 決定 |
|---|---|
| 框架版本 | Spring Boot 3.5.x LTS（而非原本的 4.1.1），Java 21 |
| Base package | 沿用 `com.percyku.livefit`（groupId `com.percyku`） |
| Port | 原定維持 8085（後續調整，見文末） |
| 行為基準 | 以 `docs/openapi.yaml` 為準，修正 Node 版瑕疵 |

---

## 二、修正既有設定

這部分原本**會直接導致啟動失敗**，不只是「可優化」。

### `livefit/pom.xml`

| 問題 | 處置 |
|---|---|
| `spring-boot-starter-webmvc`、`spring-boot-starter-{data-jpa,security,webmvc}-test` | 這四個是 **Boot 4 專屬 artifact，Boot 3 BOM 裡不存在** → 改用 `spring-boot-starter-web`，移除 test starter |
| `spring-boot-starter-parent` 4.1.1 | → **3.5.16**（Maven Central 上最新的 3.5.x LTS） |
| 缺 Bean Validation | 新增 `spring-boot-starter-validation` |
| `postgresql` 寫死 42.7.3 | 移除 `<version>`，交給 Boot BOM 管理 |
| jjwt 0.13.0 | **查證後 0.13.0 確實存在**（0.12.x 系列 API），保留原版本；三個 artifact 版本以 `${jjwt.version}` 統一 |
| 明寫 `maven-compiler-plugin` | 移除，`<java.version>21</java.version>` 已足夠 |
| 空的 `<name/>`、`<licenses/>`、`<developers/>`、`<scm/>` | 填入專案名稱／移除空區塊 |
| 編碼未指定 | 補 `project.build.sourceEncoding` = UTF-8 |

不需要額外的 BCrypt 套件 —— `spring-boot-starter-security` 內建的 `BCryptPasswordEncoder`（cost 10）與 Node 的 `bcryptjs` 雜湊完全相容，**既有使用者密碼可直接驗證登入**。

### `livefit/src/main/resources/application.properties`

| 問題 | 處置 |
|---|---|
| `spring.jpa.database-platform=...PostgreSQL10Dialect` | **Hibernate 6 已移除此類別，會啟動失敗** → 整行刪除，讓 Hibernate 自動偵測 |
| `ddl-auto=validate` | → **`none`**。表是 TypeORM 建的，`validate` 容易因型別精度細節誤判而擋住啟動；需求也禁止動 schema |
| DB 帳密硬寫在檔案 | 改成 `${VAR:預設值}` 佔位，實際值移到 `.env` |
| 無 JWT 設定 | 新增 `jwt.secret` / `jwt.expires-day`，綁定到 `JwtProperties` |
| 無時區設定 | 新增 `hibernate.jdbc.time_zone=UTC`、`spring.jackson.time-zone=UTC`、`spring.jpa.open-in-view=false` |
| 找不到路由不會回 404 | 新增 `spring.mvc.throw-exception-if-no-handler-found=true`、`spring.web.resources.add-mappings=false` |

### 刪除

- `livefit/src/test/` —— Spring Initializr 的測試骨架。需求不需要單元測試，且它依賴已移除的 test starter
- `livefit/target/` —— 舊的建置產物

---

## 三、實作程式碼：77 個 Java 檔

全部在 `livefit/src/main/java/com/percyku/livefit/` 下。

| 套件 | 檔數 | 內容 |
|---|---:|---|
| `common/` | 7 | `ApiResponse`、`ApiException`、`ErrorResponse`、`ErrorMessages`、`GlobalExceptionHandler`、`ValidUtils`、`DateTimeUtils` |
| `config/` | 3 | `SecurityConfig`、`JwtProperties`、`WebMvcConfig` |
| `security/` | 5 | `JwtTokenProvider`、`JwtAuthenticationFilter`、`AuthUser`、`JwtAuthenticationEntryPoint`、`CoachAccessDeniedHandler` |
| `entity/` | 8 | 對應 8 張既有資料表 |
| `repository/` | 11 | 8 個 `JpaRepository` + 3 個 projection interface |
| `service/` | 6 | 所有業務邏輯集中在這層 |
| `controller/` | 7 | 只做路由對映、參數綁定、回應包裝 |
| `dto/` | 29 | 依模組分 `admin/`、`coach/`、`course/`、`creditpackage/`、`user/`、`common/` |
| 根目錄 | 1 | `LivefitApplication` |

### 關鍵設計決策

- **Controller / Service 徹底分離**：Controller 不含任何 if 判斷，所有驗證與業務邏輯（含丟 `ApiException`）一律在 Service
- **JWT 驗證流程照 Node 的 `isAuth`**：驗完 token 後仍回資料庫撈 user，角色以 **DB 當下的 `role` 為準**，不信任 token 內的 role
- **401 訊息三向分流**（`請先登入` / `Token 已過期` / `無效的 token`）：`JwtAuthenticationFilter` 把訊息放進 request attribute，由 `JwtAuthenticationEntryPoint` 取用
- **權限不足回 401 而非 403**（`使用者尚未成為教練`），比照 Node 的 `isCoach`
- **`anyRequest()` 用 `permitAll`**：讓未定義路徑落到 `NoHandlerFoundException` 回 404 `無此路由`，與 Node 逐路由掛中介層的行為一致。因此所有需登入的端點都在 `SecurityConfig` 明列
- **Security matcher 順序**：`POST /api/admin/coaches/courses` 必須排在 `POST /api/admin/coaches/*`（升級教練，免登入）之前，否則建立課程會被放行

---

## 四、驗證（實際啟動跑過，不是只有編譯過）

接上運行中的 PostgreSQL 容器（`node-js-final-2026-postgres-1`），**M0–M6 共 40 項檢查全數通過**。

| 模組 | 驗證內容 |
|---|---|
| M0 | `/healthcheck` 回純文字 `OK`；未定義路由回 404 `無此路由` |
| M1 | 技能／方案的新增查詢、重複 409、空欄位 400；`createdAt` 正確回填 |
| M2 | 註冊 201、重複 email 409、弱密碼 400；登入簽出含 `{id, role, exp}` 的 HS256 token；無 token → `請先登入`、壞 token → `無效的 token`；同名改暱稱 → `使用者名稱未變更` |
| M3 | USER token 打教練端點 → `使用者尚未成為教練`；升級教練 201（回應不含 password hash）、重複升級 409；教練資料讀取與整批更換技能；課程 CRUD |
| M4 | 教練分頁列表、缺參數 400；教練詳情含 skills；教練課程與全站進行中課程 |
| M5 | 無堂數 → `已無可使用堂數`；購買後報名成功；重複報名 → `已經報名過此課程`；`max_participants=1` 時第二人 → `已達最大參加人數，無法參加`；取消為軟刪除，再次取消 → `ID錯誤` |
| M6 | 有效報名時 `{revenue: 164, participants: 1, course_count: 1}`；月份錯誤 → 400 |

另外逐欄比對 `information_schema.columns`，確認 8 張表與 entity 對映完全一致。

### 作業原生驗收測試：68/68 全數通過

把 Spring Boot 後端跑在 8080 後，直接執行專案根目錄的合約測試 —— `test/helpers.js:12` 的 base URL 是 `process.env.API_BASE_URL || 'http://localhost:8080'`，所以不必改任何測試檔：

```
Test Suites: 7 passed, 7 total
Tests:       68 passed, 68 total
```

M1～M6 與 smoke 全數通過，代表 Spring Boot 版與 Node 版在**驗收層面行為等價**。
若 Spring 跑在其他 port：`API_BASE_URL=http://localhost:8085 npm test`。

### 過程中抓到並修掉的兩個 bug

1. **`@CreationTimestamp` 與交易邊界**
   `save()` 只是把 entity 放進 persistence context，時間欄位要到 flush 才寫入。在 `@Transactional` 方法內直接用 `save()` 的回傳值組 DTO，`createdAt` 會是 `null`。
   → 凡是回應含 `createdAt` / `created_at` / `updated_at` 的建立與更新，一律改用 **`saveAndFlush()`**。

2. **Spring Security 預設帳號**
   沒有 `UserDetailsService` bean 時，Boot 會建立 in-memory 使用者並在啟動時印出用不到的隨機密碼。
   → 在 `LivefitApplication` 排除 `UserDetailsServiceAutoConfiguration`。

---

## 五、追加處理的三個需求

### 1. IntelliJ 中文亂碼

**原因**：檔案本身全是正確的 UTF-8（已用 `file -I` 逐檔確認），問題在 `.idea/encodings.xml` **只涵蓋 `src/main/java`**，沒有涵蓋 `src/main/resources`，而 IntelliJ 對 `.properties` 檔的預設編碼是 **ISO-8859-1**（Java `Properties` 規範遺留）。

**處置**：

- `.idea/encodings.xml` 改成專案全域 UTF-8，加上 `defaultCharsetForPropertiesFiles="UTF-8"` 與 `native2AsciiForPropertiesFiles="false"`
- `pom.xml` 也寫死編碼，保護命令列建置

**注意**：IntelliJ 開著時改 `.idea/` 會被覆蓋，需先關閉再重開。個別檔案若仍亂碼，用右下角編碼標示選 UTF-8 → **`Reload`**（不是 `Convert`，`Convert` 會把已壞掉的字寫回檔案）。

> `application.properties` 裡的中文只在註解，**不影響執行** —— Spring Boot 讀取 `application.properties` 一律用 UTF-8。

### 2. `.env` 環境變數檔

用 **Spring Boot 3 原生功能**，不需 `spring-dotenv` 之類的第三方套件：

```properties
spring.config.import=optional:file:.env[.properties]
```

| 檔案 | 進版控 | 用途 |
|---|:---:|---|
| `livefit/.env` | ❌ | 實際值，含 48 字元隨機 `JWT_SECRET` |
| `livefit/.env.example` | ✅ | 範本，`cp .env.example .env` 後填值 |
| `livefit/.gitignore` | ✅ | 加上 `.env` / `!.env.example` |

可設定變數：`PORT`、`DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD`、`DB_DATABASE`、`JWT_SECRET`、`JWT_EXPIRES_DAY`。

**驗證方式**：把 `.env` 的 `PORT` 暫時改成 8099，確認日誌顯示 `Tomcat started on port 8099`、`curl :8099/healthcheck` 回 `OK` 且 8080 連不上，再改回 8080。另外完成註冊 → 登入 → 用 token 存取受保護端點的端到端測試，確認新 `JWT_SECRET` 可正常簽發與驗證。

### 3. 尾斜線導致教練列表頁 404

**現象**：伺服器日誌出現

```
WARN ... NoHandlerFoundException: No endpoint GET /api/coaches/.
WARN ... PageNotFound: No mapping for GET /api/coaches/
```

**先排除的可能**：不是 CORS。跨域失敗時請求根本進不了 controller、伺服器端不會留下紀錄，錯誤只出現在瀏覽器 console。實測帶 `Origin` 的請求也正常回了 `Access-Control-Allow-Origin: http://localhost:3000`。

**真正原因**：Spring Framework 6（Boot 3）起改用 `PathPatternParser`，**預設不再把 `/api/coaches` 與 `/api/coaches/` 視為等價**；Express 預設兩者皆可。實測對照：

| 路徑 | Spring（修正前） | Node |
|---|:---:|:---:|
| `/api/coaches?per=10&page=1` | 200 | 200 |
| `/api/coaches/?per=10&page=1` | **404** | 200 |
| `/api/courses/` | **404** | 200 |
| `/api/coaches/skill/` | **404** | 200 |

**而且前端確實依賴這個行為** —— `frontend/src/api/coaches.js:3`：

```js
return request.get(`coaches/?per=${per}&page=${page}`);   // baseURL: http://127.0.0.1:8080/api/
```

組出來是 `/api/coaches/?per=10&page=1`，帶尾斜線。不處理的話**教練列表頁會整頁 404**。日誌另一行的 `/api/coaches/.` 是同一成因的變形（`.` 被當成 coachId），修正後兩版都回 400。

**處置**：新增 `config/WebMvcConfig.java`，實作 `WebMvcConfigurer.configurePathMatch()` 設定 `setUseTrailingSlashMatch(true)`。修正後七個路徑的 Spring 與 Node 回應碼完全一致。

> ⚠️ **技術債**：`setUseTrailingSlashMatch` 在 Spring 6 已 deprecated、預計 Spring 7 移除，目前用 `@SuppressWarnings("deprecation")` 壓住。升級到 Boot 4 時要改成在各 `@RequestMapping` 明列 `{"", "/"}` 兩種 pattern，或在反向代理層做 301 轉址。

---

## 六、變更檔案總表

### 新增

| 檔案 | 數量 |
|---|---:|
| `livefit/src/main/java/com/percyku/livefit/**/*.java` | 76 |
| `livefit/.env`（不進版控）、`livefit/.env.example` | 2 |
| `docs/springboot-migration-plan.md`、`docs/springboot-migration-worklog.md` | 2 |
| `CLAUDE.md`（Claude Code 的 repo 指引） | 1 |

### 修改

| 檔案 | 說明 |
|---|---|
| `livefit/pom.xml` | Boot 降版、starter 修正、補 validation、編碼設定 |
| `livefit/src/main/resources/application.properties` | Dialect、ddl-auto、環境變數、JWT、時區、404、`.env` 匯入 |
| `livefit/src/main/java/com/percyku/livefit/LivefitApplication.java` | 排除 `UserDetailsServiceAutoConfiguration` |
| `livefit/.gitignore` | 加入 `.env` 規則 |
| `livefit/.idea/encodings.xml` | 專案全域 UTF-8 |
| `livefit/src/main/java/.../config/WebMvcConfig.java` | 新增，開啟尾斜線比對 |

### 刪除

- `livefit/src/test/`（測試骨架）
- `livefit/target/`（舊建置產物）

> `.gitignore`（根目錄）、`.claude/`、`.vscode/`、`ask.md` 的變動**不是本次工作產生的** —— 前兩者在開工前就已存在，`.vscode/` 是編輯器自己產生的。

---

## 七、需要留意的事項

1. **`server.port` 目前預設 8080**，與 Node 後端容器相同，兩邊不能同時啟動。要並行就把 `.env` 的 `PORT` 改成 8085，不需改程式碼。

2. **JDK 限制**：`/Library/Java/JavaVirtualMachines` 下的 temurin-25 與 adoptopenjdk-11 都是 x86 版，在 Apple Silicon 上會回 `bad CPU type`。可用的是 `~/Library/Java/JavaVirtualMachines/temurin-21.0.11`。
   另外 macOS 的 `mktemp -d` 會忽略 `TMPDIR`，若 `/var/folders/...` 不可寫，`./mvnw` 的自動下載會以 `cannot create temp dir` 失敗 —— 建議 `brew install maven` 用系統的 `mvn`。

3. **`JWT_SECRET` 必須 ≥32 個位元組**（HS256 規格要求），`JwtTokenProvider` 會在啟動時檢查並直接拋錯。也因為 secret 與 Node 端不同，**兩邊的 token 不通用**。

4. **`.env` 是 Java properties 格式**：`KEY=value`，不要加引號、不要寫 `export`（那是 shell 語法，引號會被當成值的一部分）。這與根目錄那份給 Node `dotenv` 用的 `.env` 規則不同。

5. **`POST /api/upload`** 依 openapi 標明「不列驗收、前端未呼叫」，經確認後不實作。

6. **所有變更尚未 commit。**

5. **尾斜線相容是刻意開啟的**，不要當成多餘設定移除 —— 前端的教練列表依賴它（見第五章第 3 點）。
