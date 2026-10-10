# LiveFit — Spring Boot 後端移植計畫（已實作完成）

## 背景

現有 `./backend` 是 Node.js（Express 5 + TypeORM）後端，controller 與業務邏輯混寫在一起。目標是在 `./livefit` 用 Spring Boot 重建一套功能等價的後端，採 MVC 分層（controller / service / repository），並導入 Spring Security + JWT 以便日後擴充角色權限。

`./livefit` 目前只有 Spring Initializr 骨架（只有 `LivefitApplication`），且 `pom.xml` / `application.properties` 有數個會直接導致啟動失敗的設定，需一併修正。

資料庫（PostgreSQL 16.4-alpine3.20，容器內 `fitness` / schema `public`）的 8 張表是由 Node 端 TypeORM `synchronize: true` 建立的，**本專案不得修改任何 schema**。

> **後續變更**：`feature/social-login` 起，Spring Boot 版改用獨立資料庫 `livefit` 並開啟 `ddl-auto=update`，上面這條限制與 §2、§4 的 `ddl-auto=none` 已不再適用，詳見 [§11](#11-後續擴充獨立資料庫與-google-登入)。

### 已確認的決策

| 項目 | 決定 |
|---|---|
| 框架版本 | 降到 Spring Boot **3.5.x LTS**，Java 21 |
| Base package | 沿用 **`com.percyku.livefit`**（groupId `com.percyku`） |
| Port | 維持 **8085** |
| 行為基準 | 以 **`docs/openapi.yaml`** 為準（修正 Node 版瑕疵） |
| 其他 | 不寫 Dockerfile、不寫單元測試、不改動資料庫 schema |

---

## 1. `pom.xml` 修正（`livefit/pom.xml`）

目前的 pom 是 **Spring Boot 4 專用寫法**，降版後這幾個 artifact 在 Boot 3 BOM 中根本不存在，會直接 build 失敗：

| 問題 | 處置 |
|---|---|
| `spring-boot-starter-parent` 4.1.1 | 改 **3.5.16**（Maven Central 上最新的 3.5.x） |
| `spring-boot-starter-webmvc` | Boot 3 沒有這個 artifact → 改 **`spring-boot-starter-web`** |
| `spring-boot-starter-{data-jpa,security,webmvc}-test` | Boot 3 沒有這三個 → 全部移除（不寫測試），僅保留 `spring-boot-starter-test`（可選） |
| 缺 Bean Validation | 新增 **`spring-boot-starter-validation`** |
| `postgresql` 寫死 42.7.3 | 移除 `<version>`，交給 Boot BOM 管理 |
| `jjwt` 0.13.0 | 查證後 **0.13.0 確實存在**（0.12.x 系列 API），維持原版本；`jjwt-api` compile、`jjwt-impl` / `jjwt-jackson` runtime，三者版本以 `${jjwt.version}` 統一 |
| 明寫 `maven-compiler-plugin` source/target 21 | 移除，`<java.version>21</java.version>` 已足夠，避免與 parent 衝突 |
| 空的 `<name/>` `<licenses/>` `<developers/>` `<scm/>` | 填入 `<name>livefit</name>` / 移除空區塊 |

不需要額外的 BCrypt 套件 — `spring-boot-starter-security` 內建 `BCryptPasswordEncoder`，且與 Node 的 `bcryptjs`（cost 10、`$2a$`/`$2b$`）雜湊**完全相容**，既有使用者可直接登入。

> **JDK 注意事項**：`/Library/Java/JavaVirtualMachines` 下的 temurin-25 與 adoptopenjdk-11 都是 x86 版本，在 Apple Silicon 上會回 `bad CPU type`。可用的是
> `~/Library/Java/JavaVirtualMachines/temurin-21.0.11`，請把 `JAVA_HOME` 指向它，或在 IntelliJ 的 Project SDK 選這一個。
>
> 另外 macOS 的 `mktemp -d` 會忽略 `TMPDIR` 而改用 `/var/folders/...`，若該路徑不可寫，`./mvnw` 的自動下載會以
> `cannot create temp dir` 失敗。此時改用系統安裝的 `mvn`（`brew install maven`）即可。

## 2. `application.properties` 修正（`livefit/src/main/resources/application.properties`）

| 問題 | 處置 |
|---|---|
| `spring.jpa.database-platform=org.hibernate.dialect.PostgreSQL10Dialect` | **Hibernate 6 已移除此類別，會啟動失敗** → 整行刪除，讓 Hibernate 自動偵測 |
| `ddl-auto=validate` | 改 **`none`**。表是 TypeORM 建的（`credit_packages.name` 無長度、`price_paid numeric(10,2)`、`timestamp without time zone`），`validate` 極易因精度／型別細節誤判而擋住啟動；需求也禁止動 schema |
| DB 帳密硬寫在檔案 | 改成環境變數佔位：`${DB_HOST:localhost}`、`${DB_PORT:5432}`、`${DB_USERNAME:student}`、`${DB_PASSWORD:student666}`、`${DB_DATABASE:fitness}`，實際值放在 `livefit/.env`（見下方） |
| 無 JWT 設定 | 新增 `jwt.secret=${JWT_SECRET:}`（沒有預設值，見下方「產生 `JWT_SECRET`」）、`jwt.expires-day=${JWT_EXPIRES_DAY:30d}`（綁到 `JwtProperties`） |
| 無時區／序列化設定 | 新增 `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`、`spring.jackson.time-zone=UTC`、`spring.jpa.open-in-view=false` |
| 找不到路由要回 404 | 新增 `spring.mvc.throw-exception-if-no-handler-found=true`、`spring.web.resources.add-mappings=false` |

### `.env` 設定（`livefit/.env`）

`application.properties` 開頭加上：

```properties
spring.config.import=optional:file:.env[.properties]
```

這是 **Spring Boot 3 原生功能**，不需要 `spring-dotenv` 之類的第三方套件。說明：

- `[.properties]` 是格式提示——`.env` 沒有副檔名，這段告訴 Boot 用 Java properties 規則解析它
- `optional:` 表示檔案不存在也不會啟動失敗，此時一律採用 `${VAR:預設值}` 的預設值
- 路徑是相對於**工作目錄**，也就是 `livefit/`（`./mvnw spring-boot:run` 與 IntelliJ Run 都是從這裡啟動）
- **優先順序**：作業系統環境變數 > `.env` > `application.properties` 的預設值，所以容器化時可直接用環境變數覆蓋，不必改檔案
- 格式限制：`KEY=value`，**不要加引號、不要寫 `export`**（那是 shell 的語法，Java properties 會把引號當成值的一部分）

檔案配置：

| 檔案 | 進版控 | 用途 |
|---|---|---|
| `livefit/.env` | ❌（已加入 `.gitignore`） | 實際值，含真實 `JWT_SECRET` |
| `livefit/.env.example` | ✅ | 範本，新環境 `cp .env.example .env` 後填值 |

可設定的變數：`PORT`、`DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD`、`DB_DATABASE`、`JWT_SECRET`、`JWT_EXPIRES_DAY`。

> ⚠️ **JWT_SECRET 長度**：HS256 依 RFC 7518 要求金鑰至少 32 個位元組，`JwtTokenProvider` 會在啟動時檢查並直接拋錯。
> 專案根目錄 `.env.example` 的 `JWT_SECRET` 若太短（例如 `node2026percy`），Spring 這邊必須換成 32 字元以上的字串。

### 產生 `JWT_SECRET`

`JWT_SECRET` **必填、沒有預設值**（2026-10-10 起）。`application.properties` 原本有一個寫在版控裡的預設密鑰，沒有 `.env` 時會直接拿它簽 token；看得到 repo 的人都能用同一把密鑰簽出任何使用者的 JWT，所以拿掉了。現在留空或少於 32 個位元組，啟動都會失敗並在錯誤訊息裡說明原因。

**產生**（擇一，輸出是一行 64 個字元的十六進位字串）：

```bash
openssl rand -hex 32
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
python3 -c "import secrets; print(secrets.token_hex(32))"
```

三個指令都是向作業系統要 32 個位元組（256 bit）的亂數，剛好是 HS256 需要的強度。不要自己打一串字、也不要用線上產生器：前者亂度不夠，後者等於把密鑰交給別人。

**設定**：把輸出貼到 `livefit/.env`，等號後面直接接值，不加引號：

```properties
JWT_SECRET=這裡貼上剛剛產生的 64 個字元
```

也可以一行完成（`.env` 裡已經有 `JWT_SECRET=` 這一行時）：

```bash
cd livefit && sed -i '' "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env   # macOS
cd livefit && sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env      # Linux
```

容器或正式環境不放 `.env`，改用作業系統環境變數 `JWT_SECRET`（優先權高於 `.env`）。

**確認**：`cd livefit && mvn spring-boot:run` 能正常啟動就代表有讀到。看到 `尚未設定 JWT_SECRET` 是沒讀到（最常見的原因是不在 `livefit/` 下執行，或 `.env` 檔名打錯）；看到 `至少需要 32 個位元組` 是值太短。

要注意的事：

- **選十六進位是因為它只有 `0-9a-f`**，在 Java properties、dotenv、shell、YAML 裡都不需要跳脫。`openssl rand -base64 48` 也可以，但輸出會有 `+ / =`，放進某些格式要加引號。
- **程式是把這個字串的 UTF-8 位元組直接當金鑰**（`JwtTokenProvider.init`），不會先做十六進位或 base64 解碼。所以 64 個字元就是 64 個位元組的金鑰，長度檢查看的也是這個數字。
- **換密鑰會讓所有已簽發的 token 立刻失效**，使用者要重新登入。這也是懷疑密鑰外洩時的處理方式：換一把新的、重啟。
- **每個環境各用一把**，本機、測試、正式不要共用；也不要與 Node 版的 `backend/.env` 共用（兩邊的 token 本來就不互通）。
- **不要進版控、不要貼到聊天或 issue**。`livefit/.env` 已在 `.gitignore`；`.env.example` 的 `JWT_SECRET` 要保持空白。

保留 `server.port=8085`。注意 Swagger UI（`docs/openapi.yaml` 的 server 寫死 `http://localhost:8080`）與前端 `VITE_API_BASE_URL` 都指向 8080，要打這支後端需手動改 base URL。

## 3. 專案結構（`livefit/src/main/java/com/percyku/livefit/`）

```
LivefitApplication.java          排除 UserDetailsServiceAutoConfiguration（驗證一律走 JWT）
common/
  ApiResponse.java               record，統一輸出 {status, data}
  ApiException.java              RuntimeException + int status（對應 backend/utils/appError.js）
  GlobalExceptionHandler.java    @RestControllerAdvice → {status:"failed"|"error", message}
  ErrorMessages.java             全部錯誤訊息常數（含四句固定訊息）
  ErrorResponse.java             record，統一輸出 {status, message}
  DateTimeUtils.java             課程起訖時間字串解析（一律視為 UTC）
  ValidUtils.java                isValidString / isInteger / isValidPassword
                                 （逐字移植 backend/utils/validUtils.js 的規則，
                                  密碼 regex：^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,16}$）
config/
  SecurityConfig.java            SecurityFilterChain + CORS + PasswordEncoder Bean
  JwtProperties.java             @ConfigurationProperties(prefix="jwt")
  WebMvcConfig.java              開啟尾斜線比對（前端依賴，見 §6.5）
security/
  JwtTokenProvider.java          io.jsonwebtoken 簽發／解析（HS256）
  JwtAuthenticationFilter.java   OncePerRequestFilter
  AuthUser.java                  實作 UserDetails，持有 User entity
  JwtAuthenticationEntryPoint.java  401 訊息分流
  CoachAccessDeniedHandler.java     401 使用者尚未成為教練
entity/        User, Skill, CreditPackage, Coach, CoachWithSkill,
               Course, CourseBooking, CreditPurchase
repository/    對應 8 個 JpaRepository
dto/           依模組分子套件（user/ coach/ course/ creditpackage/ admin/）
service/       UserService, SkillService, CreditPackageService,
               CoachService, CourseService, AdminCoachService
controller/    HealthController, SkillController, UserController,
               CreditPackageController, CoachController,
               CourseController, AdminCoachController
```

**分層原則**：Controller 只做路由對映、參數綁定、包裝 `ApiResponse`；所有驗證與業務邏輯（含錯誤判斷、丟 `ApiException`）一律在 Service。

## 4. Entity 對映（嚴格對齊既有表，`ddl-auto=none`）

| Entity | 表名 | 要點 |
|---|---|---|
| `User` | `users` | name(50), email(320) unique, password(255), role(20) default `USER` |
| `Skill` | `skills` | name(50) unique |
| `CreditPackage` | `credit_packages` | name unique（**無長度限制**）, credit_amount int, price **int** |
| `Coach` | `coaches` | user_id uuid **unique**, description `text`, profile_image_url(2048) |
| `CoachWithSkill` | `coach_with_skills` | coach_id, skill_id |
| `Course` | **`course`**（單數，易踩雷） | user_id 指向 **User.id 而非 Coach.id**；description `text`；meeting_url(2048) nullable |
| `CourseBooking` | `course_booking` | booking_at 與 created_at **兩個都是建立時間**；cancelled_at 為軟取消標記 |
| `CreditPurchase` | `credit_purchase` | price_paid **`numeric(10,2)` → `BigDecimal`**；purchase_at 為手動寫入 |

- 所有 `id`：`@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id`
- `created_at` → `@CreationTimestamp`；`updated_at` → `@UpdateTimestamp`
- 時間欄位是 `timestamp without time zone`：統一用 **`Instant`** + `hibernate.jdbc.time_zone=UTC`，輸出即為 openapi 要求的 `2026-08-20T10:00:00.000Z`
- 關聯一律 `@ManyToOne(fetch = LAZY)` 並保留原始 `user_id` / `skill_id` 等欄位（`@Column(insertable=false, updatable=false)` 或反過來），因為多支 API 直接回傳這些 id

## 5. Spring Security 設計

- `SecurityFilterChain`：`csrf disable`、`cors` 全開（對應 Node 的 `app.use(cors())`）、`sessionCreationPolicy(STATELESS)`、關閉 formLogin / httpBasic
- `JwtAuthenticationFilter` 放在 `UsernamePasswordAuthenticationFilter` 之前：
  1. 讀 `Authorization`，非 `Bearer ` 開頭 → 放行讓 EntryPoint 處理（訊息 `請先登入`）
  2. `JwtTokenProvider` 驗證 → 取 `id`，查 DB 撈 `User`（比照 Node：**角色以 DB 當下的 role 為準**，不信任 token 內的 role）
  3. 查不到使用者 → request attribute 設 `無效的 token`
  4. `ExpiredJwtException` → `Token 已過期`；其他 JWT 例外 → `無效的 token`
- `JwtAuthenticationEntryPoint` 讀該 attribute 決定訊息，預設 `請先登入`，一律回 **401** + `{status:"failed", message:...}`
- `CoachAccessDeniedHandler` 回 **401**（非 403）+ `使用者尚未成為教練`，比照 Node 的 `isCoach`
- 權限：DB `role` → authority `ROLE_USER` / `ROLE_COACH`

**authorizeHttpRequests 順序（關鍵，寫反會壞）**：

```
POST   /api/admin/coaches/courses            → hasRole("COACH")   ← 必須排在下一條之前
PUT    /api/admin/coaches/courses/**         → hasRole("COACH")
GET    /api/admin/coaches/courses, /courses/**, /revenue → hasRole("COACH")
GET/PUT /api/admin/coaches                   → hasRole("COACH")
POST   /api/admin/coaches/*                  → permitAll          ← 升級教練，依 openapi 不需登入
GET    /healthcheck                          → permitAll
GET/POST /api/coaches/skill, DELETE /api/coaches/skill/*  → permitAll
GET/POST /api/credit-package, DELETE /api/credit-package/* → permitAll
POST   /api/credit-package/*                 → authenticated      ← 購買，與上面同路徑不同 method
POST   /api/users/signup, /api/users/login   → permitAll
GET    /api/coaches, /api/coaches/**         → permitAll
GET    /api/courses                          → permitAll
anyRequest()                                 → authenticated
```

`POST /api/admin/coaches/courses` 與 `POST /api/admin/coaches/{userId}` 路徑形狀相同，Controller 端 Spring MVC 會優先比對字面路徑（`courses`）因此無誤，但 Security 的 matcher 是**依序比對**，順序必須如上。

## 6. API 清單與實作重點（28 支）

以 `docs/openapi.yaml` 為權威來源；業務邏輯細節取自 `backend/controllers/*.js`。

**M0** `GET /healthcheck` — 執行 `SELECT 1`，回純文字 `OK` / 503 `Service Unavailable`（**唯一不包 `{status,data}` 的端點**）

**M1 Skill / CreditPackage**（`SkillController`, `CreditPackageController`，皆免登入）

- `GET|POST /api/coaches/skill`、`DELETE /api/coaches/skill/{skillId}`
- `GET|POST /api/credit-package`、`DELETE /api/credit-package/{creditPackageId}`
- 名稱重複 → 409 `資料重複`；`affected == 0` → 400 `ID錯誤`；列表 `order by created_at ASC`
- `credit_amount` / `price` 必須是整數且 > 0

**M2 會員**（`UserController` / `UserService`）

- `POST /signup`：email 正規化 `trim().toLowerCase()`；重複 → 409 `Email 已被使用`；BCrypt cost 10；回 **201** `{user:{id,name}}`
- `POST /login`：**登入也會驗密碼格式**；失敗一律 400 `使用者不存在或密碼輸入錯誤`；簽 JWT payload `{id, role, exp}`（HS256，`30d`）；回 **201** `{token, user:{name}}`
- `GET|PUT /profile`、`PUT /password`（舊密碼相同 → `新密碼不能與舊密碼相同`；不一致 → `新密碼與驗證新密碼不一致`；驗證失敗 → `密碼輸入錯誤`）
- 密碼錯誤訊息常數：`密碼不符合規則，需要包含英文數字大小寫，最短8個字，最長16個字`

**M3 教練後台**（`AdminCoachController` / `AdminCoachService`）

- `POST /api/admin/coaches/{userId}`：免登入；`experience_years` 須為整數且 > 0；`profile_image_url` 有值時須 `https` 開頭；user 不存在 → 400，已是 COACH → 409；**回傳的 user 只放 `{name, role}`，不得外洩 password hash**（依 openapi）
- `GET|PUT /api/admin/coaches`：PUT 為整批更換技能（delete by coach_id 後 insert），放在同一個 `@Transactional` service method
- `GET|POST /api/admin/coaches/courses`、`GET|PUT /api/admin/coaches/courses/{courseId}`：course 以 `id + user_id` 查，查無 → 400 `課程不存在`
- 課程列表的 `status` 由 `start_at` / `end_at` 與現在時間推導（`尚未開始` / `進行中` / `已結束`），`participants` 用 group-by 統計（`cancelled_at IS NULL`），回**數字**

**M4 公開瀏覽**（`CoachController`, `CourseController`）

- `GET /api/coaches?per=&page=`：兩個 query 參數必填，非數字 → 400 `欄位未填寫正確`；分頁 `Pageable.ofSize(per).withPage(page-1)`
- `GET /api/coaches/{coachId}`（coachId 是 **Coach.id**）、`GET /api/coaches/{coachId}/courses`（`end_at > now`）
- `GET /api/courses`（`start_at <= now AND end_at > now`）

**M5 購買與報名**（`CreditPackageService`, `CourseService`）

- `POST /api/credit-package/{id}`：建立 `CreditPurchase`，`purchase_at = now`
- `POST /api/courses/{courseId}`：依序檢查 → 課程不存在 `ID錯誤` → **`已經報名過此課程`** → **`已無可使用堂數`**（`SUM(purchased_credits) - COUNT(未取消 booking) <= 0`，sum 為 null 時視為 0）→ **`已達最大參加人數，無法參加`**
- `DELETE /api/courses/{courseId}`：軟刪除，設 `cancelled_at = now`；查無 → 400 `ID錯誤`
- ⚠️ 這四句錯誤訊息前端會逐字比對，**一字不可差**

**M6 營收**（`AdminCoachService`）

- `GET /api/admin/coaches/revenue?month=january…december`（英文小寫全名，無效 → 400）
- 年份固定取當年；三段統計用 `@Query(nativeQuery = true)` + interface projection：
  1. `COUNT(DISTINCT cb.user_id)` → `participants`
  2. `COUNT(cb.id)` → `course_count`（皆為 `cancelled_at IS NULL` 且 `EXTRACT(YEAR/MONTH FROM cb.created_at)` 命中）
  3. `SUM(credit_amount)`, `SUM(price)` FROM `credit_packages` → 單堂均價
- `revenue = floor(course_count × 單堂均價)`；教練無任何課程 → 三個值都回 0

**加分題** `POST /api/upload` — openapi 標明不列驗收、前端未呼叫。**本次不實作**。

## 6.5 尾斜線相容（`config/WebMvcConfig.java`）

Spring Framework 6（Boot 3）起改用 `PathPatternParser`，**預設不再把 `/api/coaches` 與 `/api/coaches/` 視為等價**；Express 預設兩者皆可。

前端確實依賴這個行為 —— `frontend/src/api/coaches.js:3` 的教練列表呼叫的是：

```js
request.get(`coaches/?per=${per}&page=${page}`)   // baseURL 為 http://127.0.0.1:8080/api/
```

組出來是 `/api/coaches/?per=10&page=1`，帶尾斜線。不處理的話教練列表頁會整頁 404。

處置：`WebMvcConfig` 實作 `WebMvcConfigurer.configurePathMatch()`，設定 `setUseTrailingSlashMatch(true)`。

> ⚠️ 此 API 在 Spring 6 已 deprecated、預計 Spring 7 移除。升級到 Boot 4 時要改成在各 `@RequestMapping` 明列 `{"", "/"}` 兩種 pattern，或在反向代理層做 301 轉址。

## 7. 回應與錯誤處理

- 成功：`{"status":"success","data":...}`；可預期失敗：`{"status":"failed","message":"..."}`；未預期：`{"status":"error","message":"伺服器錯誤"}`
- `GlobalExceptionHandler` 處理 `ApiException`（帶 status）、`MethodArgumentNotValidException` → 400 `欄位未填寫正確`、`NoHandlerFoundException` → 404 `無此路由`、`Exception` → 500
- 需設 `spring.mvc.throw-exception-if-no-handler-found=true` 才能接到 404

## 8. 與 Node 現況的行為差異（依 openapi 修正）

| # | Node 現況 | Spring Boot 版 |
|---|---|---|
| 1 | `GET /api/users/credit-package` 的 `purchase_at` 因變數名打錯（`item.purchaseAt`）永遠不回傳 | 正常回傳 `purchase_at` |
| 2 | `GET /api/admin/coaches/courses` 重複註冊，第二個 `getCoachAllCourse`（回 `201 {}`）是死碼 | 不移植該死路由 |
| 3 | `POST /api/admin/coaches/{userId}` 回傳完整 user entity（含 password hash） | 只回 `{name, role}` |
| 4 | `putCoachProfile` 的 `skill_ids.every(s => !isValidString(s))` 邏輯寫反 | 改為「任一無效即 400」 |
| 5 | `deleteBookingCourse` 檢查 `save()` 的 `.affected`（永遠 false，死碼） | 移除該檢查 |
| 6 | `GET /api/users/courses` 的原生 SQL 未過濾已取消預約、無排序 | 保留全部預約（openapi 的 `cancelled_at` 可為 null，前端自行判斷），補 `ORDER BY c.start_at ASC` |
| 7 | 購買總堂數 `sum()` 無紀錄時回 `null`，造成 `credit_remain` 變負數 | `COALESCE(...,0)` |
| 8 | `DELETE /api/coaches/skill/{id}` 回 `{status:"success"}` 無 data | 依 openapi 回 `data:{raw:[], affected:1}` |
| 9 | 多支 POST 回 200 而非 201 | 依 openapi 標示（規格書明示 200/201 皆可，維持 openapi 的值） |
| 10 | `getCoachProfile` 在 coach 資料缺失時 NPE → 500 | 回 400 `找不到該教練` |
| 11 | 註冊與修改名稱不檢查 `name` 長度，超過 `users.name` 的 `varchar(50)` 時資料庫寫入失敗 → 500 | 去除前後空白後超過 50 字回 400 `欄位未填寫正確` |
| 12 | 註冊是「先查 email 再寫入」，兩個同 email 的請求同時到時，後者撞 unique 約束 → 500 | 接住約束衝突後重查，回 409 `Email 已被使用`（Google 登入首次建立帳號的併發同理，改用已建好的帳號照常登入） |

**刻意保留的 Node 行為**（openapi 也如此規範，不改）：

- `POST /api/admin/coaches/{userId}` 免登入
- `isCoach` 失敗回 **401** 而非 403
- `POST /api/courses/{courseId}` 的「已經報名過此課程」檢查**不排除已取消的紀錄**（取消後無法重新報名）
- `POST /api/users/login` 對密碼格式也做驗證

## 9. 實作狀態

全部完成，共 77 個 Java 檔案，`mvn compile` 通過，並通過作業原生的 68 項合約測試。

1. ✅ `pom.xml` + `application.properties`
2. ✅ `common/`（ApiResponse、ApiException、ErrorResponse、ErrorMessages、GlobalExceptionHandler、ValidUtils、DateTimeUtils）+ `HealthController`
3. ✅ 8 個 `entity/` + 8 個 `repository/`（含 3 個 projection interface）
4. ✅ `config/SecurityConfig` + `security/`（JWT 全套）
5. ✅ M1 → M6 全數實作完成

### 實作過程中發現並修正的兩個細節

- **`@CreationTimestamp` 與交易邊界**：`save()` 只是把 entity 放進 persistence context，時間欄位要到 flush 才會寫入。
  若在 `@Transactional` 方法內直接用 `save()` 的回傳值組 DTO，`createdAt` 會是 `null`。
  凡是回應中含 `createdAt` / `created_at` / `updated_at` 的建立與更新，一律改用 **`saveAndFlush()`**。
- **Spring Security 預設帳號**：沒有 `UserDetailsService` bean 時，Boot 會建立 in-memory 使用者並在啟動時印出隨機密碼。
  本專案驗證一律走 JWT，故在 `LivefitApplication` 排除 `UserDetailsServiceAutoConfiguration`。

## 10. 驗證結果

已於 2026-09-20 對照既有的 PostgreSQL 容器（`node-js-final-2026-postgres-1`）實跑，M0–M6 共 40 項檢查全數通過：

| 模組 | 驗證內容 |
|---|---|
| M0 | `/healthcheck` 回純文字 `OK`；未定義路由回 404 `無此路由` |
| M1 | 技能／方案的新增、查詢、重複（409 `資料重複`）、空欄位（400 `欄位未填寫正確`）；`createdAt` 正確回填 |
| M2 | 註冊 201、重複 email 409、弱密碼 400；登入 201 並簽出含 `{id, role, exp}` 的 HS256 token；無 token → 401 `請先登入`、壞 token → 401 `無效的 token`；同名改暱稱 → 400 `使用者名稱未變更` |
| M3 | USER token 打教練端點 → 401 `使用者尚未成為教練`；升級教練 201（回應不含 password hash）、重複升級 409；教練資料讀取與整批更換技能；課程建立／查詢／更新，`created_at` / `updated_at` 正確回填 |
| M4 | 教練分頁列表、缺 `per`/`page` → 400；教練詳情含 skills 陣列；教練課程與全站進行中課程 |
| M5 | 無堂數報名 → 400 `已無可使用堂數`；購買後報名成功；重複報名 → 400 `已經報名過此課程`；`max_participants=1` 時第二人報名 → 400 `已達最大參加人數，無法參加`；取消為軟刪除，再次取消 → 400 `ID錯誤`；`credit_remain` / `credit_usage` 計算正確 |
| M6 | 有效報名時 `{revenue: 164, participants: 1, course_count: 1}`；月份參數錯誤 → 400 |

### 作業原生驗收測試：68/68 全數通過

把 Spring Boot 後端跑在 8080 後，直接執行專案根目錄的合約測試（`test/helpers.js` 的 base URL 預設就是 `http://localhost:8080`）：

```
Test Suites: 7 passed, 7 total
Tests:       68 passed, 68 total
```

M1～M6 與 smoke 全部通過，代表 Spring Boot 版與 Node 版在驗收層面上行為等價。
若 Spring 跑在其他 port，用 `API_BASE_URL=http://localhost:8085 npm test` 指定。

資料庫欄位型別已逐欄比對 `information_schema.columns`，8 張表與 entity 對映完全一致（`course` 為單數表名、`credit_purchase.price_paid` 為 `numeric(10,2)`、所有時間欄位為 `timestamp without time zone`）。

### 重現步驟

**前置**：`JAVA_HOME` 指向 arm64 的 JDK 21（見 §1）。

1. 確認資料庫與資料表存在（表由 Node 端 TypeORM 建立）：

   ```bash
   docker compose up -d postgres
   # 若 pgData volume 是空的，先讓 Node 後端建表：
   cd backend && npm run dev
   ```

2. 啟動 Spring Boot：

   ```bash
   cd livefit && ./mvnw spring-boot:run     # 或在 IntelliJ 直接 Run
   ```

3. 煙霧測試：

   ```bash
   curl http://localhost:8085/healthcheck                    # → OK
   curl http://localhost:8085/api/coaches/skill              # → {"status":"success","data":[...]}
   curl -X POST http://localhost:8085/api/users/signup \
        -H 'Content-Type: application/json' \
        -d '{"name":"test","email":"t@t.com","password":"Test1234"}'
   curl http://localhost:8085/api/users/profile              # 無 token → 401 "請先登入"
   ```

4. 逐 M 對照驗收：Swagger UI 在 `docker compose up -d swagger`（8081 埠），把每支 API 的回應欄位與錯誤訊息逐一比對；四句固定錯誤訊息務必逐字核對。
5. 交叉驗證：Node 後端在 8080、Spring 在 8085，對同一組測資打相同 endpoint 比對 JSON——差異應只落在 §8 表列的項目。

> 根目錄的 `npm run test:m1`~`test:m6`（jest + supertest，68 tests）是打 8080 的 Node 後端；若要拿來驗 Spring 版，需把測試的 base URL 指到 8085。

---

## 11. 後續擴充：獨立資料庫與 Google 登入

§1–§10 記錄的是「與 Node 版共用 `fitness`、不得更動 schema」的移植階段。`feature/social-login` 起，Spring Boot 版改為獨立發展，以下三點取代前面的對應描述。

### 11.1 獨立資料庫 `livefit`

| 項目 | 移植階段 | 現在 |
|---|---|---|
| 資料庫 | `fitness`（與 Node 共用） | **`livefit`**（同一個 postgres 容器，與 `fitness` 並存、互不影響） |
| `ddl-auto` | `none` | **`update`**（`.env` 的 `DDL_AUTO` 可覆寫） |
| 建表 | 必須先跑 Node 後端 | Hibernate 首次啟動時依 Entity 建立 8 張表 |

- postgres 容器只會自動建立 `fitness`，新環境要先手動建一次：

  ```bash
  docker compose exec postgres psql -U student -d fitness -c "CREATE DATABASE livefit"
  ```

- `application.properties` 的 `DB_DATABASE` 預設值也改成 `livefit`，避免沒有 `.env` 時 `update` 去改到原始的 `fitness`。
- 加上 `hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`，讓 `Instant` 仍建成 `timestamp without time zone`（Hibernate 6 預設是 `with time zone`），與既有的 `hibernate.jdbc.time_zone=UTC` 設計一致。
- 與 TypeORM 建出的 schema 的差異：外鍵只存在於 Entity 有宣告關聯的欄位（`course.user_id`、`course.skill_id`、`coaches.user_id`、`coach_with_skills.skill_id`、`credit_purchase.credit_package_id`）；`credit_packages.name` 是 `varchar(255)`；時間欄位是 `timestamp(6)`。
- 兩個資料庫的資料不互通：同一個帳號要在兩邊各自註冊。

### 11.2 Google 登入（`POST /api/users/google`）

流程：前端用 Google Identity Services 取得 ID token → 送到後端驗證 → 後端簽發與一般登入**相同格式**的自家 JWT。後端維持 STATELESS，不使用 `oauth2-client` 的 redirect / session 流程，也不需要 Google client secret。

| 檔案 | 內容 |
|---|---|
| `pom.xml` | 新增 `spring-security-oauth2-jose`（只用 `NimbusJwtDecoder`） |
| `security/GoogleIdTokenVerifier` | 以 Google JWKS 驗簽，檢查 `exp`、`iss`、`aud`（= `GOOGLE_CLIENT_ID`） |
| `config/GoogleProperties` | `google.client-id` ← 環境變數 `GOOGLE_CLIENT_ID` |
| `entity/User` | `password` 改為可 null；新增 `google_sub`（unique，§11.5 起改存 `user_identities`） |
| `UserService.googleLogin` | 帳號對應邏輯（見下） |
| `SecurityConfig` | matcher 不需改，`anyRequest().permitAll()` 已涵蓋 |

帳號對應規則（依序）：

1. `google_sub` 已存在 → 直接登入。
2. Google 的 email（必須 `email_verified=true`）已有帳號 → 寫入 `google_sub` 綁定，**不動原密碼**。
3. 都沒有 → 建立新帳號，`password` 為 null、`role` 為 `USER`。

各帳號型態可用的功能只取決於 `password` 是否為 null：

| 帳號型態 | 密碼登入 | Google 登入 | 修改密碼 |
|---|---|---|---|
| 密碼註冊、未綁 Google | ✅ | 首次使用時自動綁定 | ✅ |
| 密碼註冊、已綁 Google | ✅ | ✅ | ✅ |
| 純 Google 建立 | ❌ 400 `使用者不存在或密碼輸入錯誤` | ✅ | ❌ 400 `此帳號使用第三方登入，無法修改密碼` |

`googleLogin` 刻意不加 `@Transactional`：驗證 token 要連 Google 抓公鑰，不該佔著資料庫連線等網路。

### 11.3 前端

- `components/GoogleLoginButton.vue`：動態載入 GIS script 並渲染官方按鈕，登入頁與註冊頁共用。**`VITE_GOOGLE_CLIENT_ID` 未設定時不顯示**，所以搭配 Node 後端時畫面與原本相同。
- `utils/loginHandler.js`：登入成功後的共用處理（存 cookie、更新 store、依角色導頁），密碼登入與 Google 登入共用。
- `config/routeTable.js`：`post-users` 白名單加入 `/google`，避免殘留的舊 token 被附上。
- `VITE_GOOGLE_CLIENT_ID` 是 build-time 變數：本機開發寫在 `frontend/.env`；容器化時由 `docker-compose.yml` 的 build arg 傳入。

### 11.4 驗證結果

- 根目錄 68 項合約測試對新資料庫 `livefit` 全數通過。
- `\d users` 確認 `password` 可 null、`google_sub` 有 unique 約束（當時的狀態；這個欄位已在 §11.7 刪除）、時間欄位為 `timestamp without time zone`；`fitness.users` 未被更動。
- `POST /api/users/google`：空 body → 400 `欄位未填寫正確`；未設定 Client ID → 400 `尚未設定 Google 登入`；偽造簽章或格式錯誤的 token → 400 `Google 登入驗證失敗`。
- 真實 Google 帳號的登入、綁定流程需要有效的 Client ID，須在瀏覽器手動驗證。

### 11.5 第三方登入共用層（`refactor/social-identity`）

為了之後加 GitHub、Facebook，把 Google 專用的帳號對應抽成各平台共用。**對外行為不變**，唯一的差別是無密碼帳號修改密碼時的訊息（見下）。

| 檔案 | 內容 |
|---|---|
| `entity/UserIdentity`、`repository/UserIdentityRepository` | 新表 `user_identities`：`user_id`（FK）、`provider`、`provider_user_id`；unique(`provider`, `provider_user_id`) 與 unique(`user_id`, `provider`) |
| `entity/User`、`UserRepository` | 移除 `googleSub` 與 `findByGoogleSub` |
| `security/SocialProfile` | 各平台驗證後統一的使用者資料：`provider, subject, email, emailVerified, name`，取代 `GoogleIdTokenVerifier.GoogleProfile` |
| `UserService.socialLogin` | 原 `linkOrCreateGoogleUser` 的通用版，`googleLogin` 驗完 token 後呼叫它 |
| `ErrorMessages` | `GOOGLE_ACCOUNT_NO_PASSWORD` → `SOCIAL_ACCOUNT_NO_PASSWORD`，文字改為 `此帳號使用第三方登入，無法修改密碼` |
| `pom.xml`、`src/test/` | 新增 `spring-boot-starter-test` 與 `UserServiceSocialLoginTest` |

設計上的幾個決定：

- **`provider` 是字串常數（`UserIdentity.PROVIDER_*`）而非 enum**。Hibernate 6 會替 enum 欄位建 check 約束，`ddl-auto=update` 之後不會更新它，新增平台時寫入會失敗。
- **`googleLogin` 仍自己擋掉 `email_verified=false`**（400 `Google 登入驗證失敗`），不交給 `socialLogin`。`socialLogin` 對未驗證 email 的處理是「不綁定既有帳號、但可建立新帳號」，那是留給不提供驗證旗標的平台用的。
- **寫入用 `TransactionTemplate` 包成一個小交易**（建立帳號要寫 `users` 與 `user_identities` 兩張表），unique 約束衝突在交易**外面**接住後重查。外層方法仍不加 `@Transactional`。
- **交易內發現帳號已綁過同一個平台時，要再查一次是不是同一個平台帳號**：是的話代表另一個請求剛搶先綁好，直接登入；不是才回 409。少了這一步，同一個人連點兩下就會有一個請求收到 409。

資料搬移：`ddl-auto=update` 會建新表，但不會搬資料。新版啟動一次後手動執行（可重複執行）：

```sql
INSERT INTO user_identities (id, user_id, provider, provider_user_id, created_at)
SELECT gen_random_uuid(), id, 'GOOGLE', google_sub, now()
FROM users u
WHERE google_sub IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM user_identities i WHERE i.user_id = u.id AND i.provider = 'GOOGLE');
```

`users.google_sub` 欄位**暫時保留**（Entity 已不對映，不影響寫入），這樣 revert 時舊程式碼仍可運作。等 GitHub、Facebook 登入都完成後再 `ALTER TABLE users DROP COLUMN google_sub`（已完成，見 §11.7 最後一段）。

測試：`cd livefit && mvn test`。`UserServiceSocialLoginTest` 把 `GoogleIdTokenVerifier` 換成假的，其餘走真的 `livefit` 資料庫（**需要 postgres 在線**），用隨機 email 並在結束後刪掉自己建的資料。

### 11.6 GitHub 登入（`POST /api/users/github`）

GitHub 不像 Google 會給前端一張可離線驗簽的 ID token，所以走 authorization code 流程：

1. 前端按鈕產生隨機 `state` 存進 `sessionStorage`，整頁導向 `https://github.com/login/oauth/authorize`。
2. 使用者授權後，GitHub 帶著 `code` 與 `state` 跳回前端的 `/oauth/callback/github`。
3. 前端比對 `state` 後，把 `{ code, redirect_uri }` POST 給後端。
4. 後端用 `code` + client secret 換 access token，再取使用者資料，組成 `SocialProfile` 交給 `socialLogin`（§11.5），簽發自家 JWT。

後端仍然是 STATELESS：沒有用 `spring-security-oauth2-client`，也沒有 session；`state` 由前端自己存、自己比對。沒有新增 Maven 依賴（`RestClient` 隨 `spring-boot-starter-web` 提供）。

| 檔案 | 內容 |
|---|---|
| `security/GithubOAuthClient` | 連 GitHub 三次：換 token、`/user`、`/user/emails` |
| `config/GithubProperties` | `GITHUB_CLIENT_ID`、`GITHUB_CLIENT_SECRET` |
| `config/OAuthProperties` | `OAUTH_REDIRECT_URIS`：允許的 `redirect_uri` 白名單（之後 Facebook 共用） |
| `dto/user/OAuthCodeLoginRequest` | `{ code, redirect_uri }`（之後 Facebook 共用） |
| `UserService.githubLogin` | 驗欄位與白名單 → `GithubOAuthClient` → `socialLogin` |
| `SecurityConfig` | matcher 不需改；兩個新的 Properties 要加進 `@EnableConfigurationProperties` |

容易踩到的地方：

- **GitHub 換 token 失敗時回的是 HTTP 200**，錯誤放在 body 的 `error` 欄位（例如 `bad_verification_code`），所以判斷依據是「有沒有 `access_token`」而不是狀態碼。另外要帶 `Accept: application/json`，否則回的是 form 編碼。
- **只採用 `primary && verified` 的 email**，所以交給 `socialLogin` 時 `emailVerified` 一律是 true，可以綁定既有帳號。主要 email 未驗證時不會退而求其次用別的 email，直接回 400。
- **GitHub 的顯示名稱可以不填**（`name` 為 null），這時用帳號名稱 `login`。
- **逾時是連線 2 秒、讀取 3 秒**。前端 axios 的逾時是 10 秒，後端連續三次呼叫的時間要壓在這之下，否則前端先斷線、後端卻已建好帳號。
- **`code` 只能用一次**，所以前端 callback 頁取出 `state` 後立刻從 `sessionStorage` 移除：重新整理或按上一頁回到這頁時會直接回登入頁，不會重送。
- **容器版前端（3000）能不能共用同一個 OAuth App 還沒確認**。callback URL 登記的是 5173；GitHub 文件說 loopback 位址的 `redirect_uri` 不必與登記的 port 相同，但只明確提到 `127.0.0.1`，`localhost` 是否適用沒有實測。不行的話就為 3000 另建一個 OAuth App。

前端：

- `config/oauthProviders.js`：各平台的授權網址、scope、對應的 API 函式。加新平台只要在這裡加一筆。
- `components/OAuthRedirectButton.vue`：導向授權頁的按鈕，未設定 client id 時不顯示。
- `components/SocialLoginButtons.vue`：把「或」分隔線、Google 按鈕、GitHub 按鈕包在一起，登入頁與註冊頁共用。分隔線原本在 `GoogleLoginButton.vue` 裡，搬到這裡是為了只設定 GitHub 時也有分隔線、兩個都設定時不會出現兩條。
- `pages/public/auth/OAuthCallbackView.vue` + 路由 `/oauth/callback/:provider`。
- `config/routeTable.js`：`post-users` 白名單加入 `/github`。

測試：`GithubOAuthClientTest` 用 `MockRestServiceServer` 假造 GitHub 的回應（不連線、不需要資料庫）；`UserServiceSocialLoginTest` 多了三項 GitHub 的情境。

### 11.7 Facebook 登入（`POST /api/users/facebook`）

流程與 GitHub（§11.6）完全相同，共用 callback 頁、按鈕元件、`OAuthCodeLoginRequest` 與 `OAUTH_REDIRECT_URIS` 白名單；`UserService` 裡兩者共用 `oauthCodeLogin`，只差在用哪一個 client 換使用者資料。

| 檔案 | 內容 |
|---|---|
| `security/FacebookOAuthClient` | 連 Facebook 兩次：`GET /oauth/access_token` 換 token、`GET /me?fields=id,name,email` |
| `security/OAuthRestClients` | 從 `GithubOAuthClient` 抽出的逾時設定（連線 2 秒、讀取 3 秒），兩個 client 共用 |
| `config/FacebookProperties` | `FACEBOOK_APP_ID`、`FACEBOOK_APP_SECRET` |
| `UserService.facebookLogin` | 與 `githubLogin` 共用 `oauthCodeLogin` |

**與 Google、GitHub 最大的不同：Facebook 不提供 email 是否驗證過的旗標。** `FacebookOAuthClient` 因此一律回傳 `emailVerified=false`，`socialLogin` 的處理是：

| 情況 | 結果 |
|---|---|
| 這個 Facebook 帳號登入過 | 直接登入（以 Facebook 的使用者 id 對應，與 email 無關） |
| 沒登入過，email 也沒人用 | 建立無密碼的新帳號 |
| 沒登入過，但 email 已有帳號 | 409 `此 Email 已註冊，請改用原本的方式登入`，**不綁定** |

第三種情況不自動綁定，是為了守住「未驗證的信箱不能接管既有帳號」：否則任何人只要在 Facebook 填上別人的信箱，就能登入那個人的帳號。

其他細節：

- **Facebook 帳號可能沒有 email**（用手機號碼註冊，或使用者在授權頁取消提供 email）。`users.email` 不可為 null，所以回 400 `此 Facebook 帳號沒有提供 Email，無法登入`。
- **換 token 是 GET，密鑰在 query string 裡。** `RestClient` 的例外訊息只含狀態碼與回應內容（連線失敗時也會去掉 query），所以 log 不會印出密鑰；改動這段的錯誤處理時不要把完整網址寫進 log。
- **Graph API 版本寫死為 `v26.0`**（後端 `FacebookOAuthClient` 與前端 `config/oauthProviders.js` 各一處）。每個版本約在推出兩年後停用，停用後的請求會被自動導到最舊的可用版本，不會直接壞掉，但升級時兩處要一起改。
- **Facebook App 在開發模式下只有具有應用程式角色的人（管理員、開發人員、測試人員）能登入**，其他人會看到錯誤頁。要開放給所有人得把 App 切到上線模式，那需要隱私權政策網址等資料。
- 授權頁按取消時，Facebook 帶回的是 `error=access_denied`，前端 callback 頁與 GitHub 共用同一段處理。

#### 已知限制：反方向的帳號接管

上面的規則只擋住「用未驗證的信箱**綁進**既有帳號」。反過來的順序擋不住：

1. 攻擊者在 Facebook 把信箱填成受害者的（Facebook 不保證驗證過），用它登入本站 → 建立一個以受害者信箱為 email 的帳號。
2. 受害者之後用 Google 或 GitHub（信箱已驗證）登入 → 依規則自動綁進那個帳號。
3. 攻擊者仍可用 Facebook 登入同一個帳號。

**這個問題不是 Facebook 登入帶進來的**：一般註冊（`POST /api/users/signup`）同樣不驗證信箱，攻擊者用密碼註冊受害者的信箱也是一樣的結果。要根治得做信箱驗證信，並且只讓「信箱驗證過的帳號」接受自動綁定；這超出這份作業的範圍，所以只記錄、不處理。正式上線的服務不能這樣做。

**狀態：已採用做法 B（2026-10-10），實作見 §11.8。** 這個情況曾用真實帳號重現過（先 Facebook 建帳號、再用同 email 的 Google 登入，Google 被綁進 Facebook 建的帳號）。下面三種做法的比較留作紀錄。

三種做法都要先加 `users.email_verified`（Google、GitHub 建立的帳號為 true；密碼註冊與 Facebook 建立的為 false），差別在驗證過的登入遇到未驗證的帳號時怎麼處理：

| 做法 | 內容 | 優點 | 缺點 |
|---|---|---|---|
| A. 拒絕綁定 | 回 409，不綁定 | 改動最小 | 攻擊者可用別人的信箱佔位，本人反而登不進來；現有的「密碼註冊後用 Google 登入自動綁定」會失效 |
| B. 接管並清除 | 照樣綁定，同時清除密碼、刪除未驗證的綁定、讓已簽發的 JWT 失效、標成已驗證 | 真正擋住攻擊，不需要寄信 | 誠實使用者的密碼也會被清掉，要補「無密碼帳號設定密碼」的功能；JWT 目前無法撤銷，要加版本號；攻擊者留下的資料要不要清得另外決定 |
| C. 註冊驗證信 | 未點驗證連結的帳號不能登入、不佔用信箱 | 正式服務的標準做法，之後 Facebook 也能安全地自動綁定 | 需要寄信服務與驗證流程，工程量最大；註冊後不能直接登入，作業的 68 項測試會受影響 |

評估：以作業來說維持現狀即可（三種都會讓行為偏離 `openapi.yaml`）；當成練習要做的話選 B，約一個分支的工作量；要上線的話做 C。

#### `users.google_sub` 欄位

§11.5 保留下來的舊欄位，Entity 已不對映。只有在 §11.5 之前就建好的資料庫才有它；全新建立的資料庫不會有。三個平台都驗證過之後刪除（本機的 `livefit` 已於 2026-10-05 執行）：

```sql
-- 先確認沒有還沒搬到 user_identities 的資料，應該回 0
SELECT count(*) FROM users u
WHERE google_sub IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM user_identities i
                  WHERE i.user_id = u.id AND i.provider = 'GOOGLE' AND i.provider_user_id = u.google_sub);

ALTER TABLE users DROP COLUMN google_sub;
```

刪除後就無法 revert 回 §11.5 之前的程式碼（舊版會找不到欄位）。

### 11.8 擋住反方向的帳號接管（`fix/social-prehijack`）

§11.7「已知限制」的修正，採用做法 B：信箱本人第一次用驗證過的平台登入時，把別人可能留在帳號上的登入方式全部清掉。

**規則**

`users` 多一個 `email_verified`：Google、GitHub 建立的帳號為 true，密碼註冊與 Facebook 建立的為 false。Google 或 GitHub 登入以 email 找到既有帳號時：

| 既有帳號 | 結果 |
|---|---|
| `email_verified=true` | 與之前相同：只新增一筆綁定，密碼保留 |
| `email_verified=false` | **接管**（`UserService.takeOver`）：密碼清成 null、刪除帳號既有的所有 `user_identities`、`token_version` 加一、標成已驗證，然後才新增這次的綁定 |

Facebook 登入的規則不變（email 已有帳號回 409，不綁定也不接管）。

| 檔案 | 內容 |
|---|---|
| `entity/User` | 新增 `email_verified`（預設 false）、`token_version`（預設 0） |
| `repository/UserRepository` | `findByEmailForUpdate`、`findByIdForUpdate`（`SELECT ... FOR UPDATE`） |
| `repository/UserIdentityRepository` | `deleteAllByUserId`（一句 JPQL DELETE） |
| `security/JwtTokenProvider` | `createToken(User)`，payload 多一個 `ver`；`tokenVersionOf` 讀取，沒有 `ver` 視為 0 |
| `security/JwtAuthenticationFilter` | `ver` 與 `users.token_version` 不同時回 401 `無效的 token` |
| `security/AuthUser` | 多帶通過驗證當下的 `token_version` 與 token 的簽發時間 |
| `UserService` | `takeOver`、`lockCurrentUser`、`setFirstPassword`；`updateName`、`updatePassword` 改收 `AuthUser` |
| `AdminCoachService.signupCoach` | 改用 `findByIdForUpdate` |
| `dto/user/ProfileResponse` | `data.user` 多一個 `has_password` |
| `ErrorMessages` | 移除 `SOCIAL_ACCOUNT_NO_PASSWORD`，新增 `SET_PASSWORD_RELOGIN` |

**為什麼需要鎖與第二次版本檢查**

只做「接管時清掉」不夠，審查時找到兩種讓攻擊者把存取權拿回來的時間差：

- **整列寫回**。Hibernate 的 UPDATE 預設寫回所有欄位。`updateName`、`updatePassword`、`signupCoach` 都是「讀出、改一個欄位、存回去」，如果在接管 commit 之前讀、之後寫，會把舊的密碼與 `token_version` 一起寫回，接管等於沒發生。`signupCoach` 還是免登入的端點。所以這三處與接管交易都用 `FOR UPDATE` 鎖住該列。**之後新增任何會修改 `User` 再存回去的程式，都要用 `findByIdForUpdate` / `findByEmailForUpdate`。**
- **驗證與寫入不在同一個交易**。`JwtAuthenticationFilter` 檢查 token 時還沒接管、進到 service 時已經接管完，這個請求就會看到一個沒有密碼的帳號，可以直接設定密碼。所以 `lockCurrentUser` 在鎖住該列之後再比對一次 `token_version`，不同就回 401。只加鎖而不做這個檢查反而更糟：攻擊者的請求會剛好排在接管後面。

刪除綁定用一句 JPQL DELETE 而不是逐筆 `remove`：Google 與 GitHub 同時接管同一個帳號時，後到的那個要刪的資料已經不在，逐筆刪會丟 `StaleStateException` 變成 500。

**無密碼帳號設定密碼**

接管會清掉誠實使用者的密碼，所以要有辦法設回來。沿用 `PUT /api/users/password`：帳號有密碼時行為完全不變；沒有密碼時不需要舊密碼（`password` 欄位忽略），只驗 `new_password` 與 `confirm_new_password`。

沒有舊密碼把關，改成**要求這次登入在 5 分鐘內**（看 token 的 `iat`，`UserService.SET_PASSWORD_LOGIN_WINDOW`），否則回 400 `為了確認是本人操作，請重新登入後再設定密碼`。少了這條，偷到 token 的人可以替純第三方登入的帳號設一組密碼，把最多 30 天的存取權變成永久的。

前端：`ProfileView.vue` 依 `has_password` 隱藏舊密碼欄位；`router/index.js` 在 profile 回 401 時清掉 cookie 與 store。後者原本沒有處理，因為 cookie 的到期時間等於 token 的 `exp`，「cookie 還在但 token 無效」幾乎不會發生；有了 `token_version` 之後，被接管帳號的其他裝置都會遇到，不清的話每次換頁都丟例外，連登入頁都進不去。

**既有資料庫升級**

`ddl-auto=update` 會在第一次啟動時補上兩個欄位，既有資料全部是 `email_verified=false`、`token_version=0`。**啟動一次讓欄位建好之後、對外服務之前**，執行下面的 SQL（可重複執行）：

```sql
BEGIN;

-- 已經綁了 Google 或 GitHub 的帳號，信箱本人確定進得來。
-- 但升級前的程式不分驗證與否一律綁定，這些帳號上的 Facebook 綁定與密碼可能是別人留下的（§11.7 的攻擊），
-- 所以比照接管處理，不能只把 email_verified 改成 true

DELETE FROM user_identities i
WHERE i.provider NOT IN ('GOOGLE', 'GITHUB')
  AND EXISTS (SELECT 1 FROM user_identities v
              WHERE v.user_id = i.user_id AND v.provider IN ('GOOGLE', 'GITHUB'));

UPDATE users u
SET password = NULL, token_version = token_version + 1, email_verified = true
WHERE u.email_verified = false
  AND EXISTS (SELECT 1 FROM user_identities v
              WHERE v.user_id = u.id AND v.provider IN ('GOOGLE', 'GITHUB'));

COMMIT;
```

受影響的使用者要重新登入；原本有密碼的，登入後到個人資料頁重新設定。只用密碼或只用 Facebook 的帳號不受影響，維持未驗證。

沒執行的話不會壞，但有兩個後果：升級前就被接管過的帳號，攻擊者的登入方式會一直有效（之後都走「這個平台帳號登入過」的路徑，不會再觸發接管）；正常的舊帳號第一次用另一個平台登入時會被當成接管，密碼與原本的綁定被清掉。

啟動後用 `\d users` 確認兩個欄位都是 `not null` 且有 default。`ddl-auto=update` 補欄位失敗時只會寫 log、不會讓啟動失敗。

**已知的代價與限制**

- 用密碼註冊、之後第一次用 Google 或 GitHub 登入的誠實使用者，密碼會被清掉，要重新設定。伺服器分不出註冊的人是不是信箱本人。
- 用 Facebook 建立帳號、之後用 Google 或 GitHub 登入的誠實使用者，Facebook 之後登不進來（回 409）。專案沒有「登入後再綁定其他平台」的功能。
- 帳號裡的資料不清：名稱、教練身分與簡介、開的課、購買與報名紀錄都會留給信箱本人，其中可能有冒用者留下的。
- 被接管帳號的其他裝置會在下一次換頁時被登出。
- 信箱本人第一次登入與冒用者註冊剛好同時發生時，本人可能收到一次 409 `Email 已被使用`，重試即可。
- Google 的 `email_verified` 只看旗標，沒有另外檢查 `hd` 或是否為 `@gmail.com`。
- 密碼註冊仍然不驗證信箱，冒用者還是可以先佔住別人的信箱，只是本人一登入就會收回。要連佔位都擋住得做註冊驗證信（做法 C）。

**驗證**

- `cd livefit && mvn test` 33 項通過，連跑 5 次（`UserServiceSocialLoginTest` 18 項、`JwtTokenProviderTest` 4 項、`GithubOAuthClientTest` 6 項、`FacebookOAuthClientTest` 5 項）。
- 根目錄 68 項合約測試對 Spring Boot 版（port 8085）全數通過。
- `vite build` 通過。
- 升級 SQL 已在本機 `livefit` 執行（2026-10-10）：兩個已綁 Google / GitHub 的帳號被標成已驗證、`token_version` 變成 1，再執行一次影響 0 筆。當時沒有 Facebook 綁定、這兩個帳號也沒有密碼，所以「清密碼、刪 Facebook 綁定」沒有實際資料可以驗到。
- 用真實帳號在瀏覽器跑了兩輪「先 Facebook、再同 email 的 Google」，以後端 log 與資料庫確認：Google 登入時 log 出現 `已清除原本的登入方式`，帳號只剩 Google 綁定，再用 Facebook 登入回 409。
- 用無頭 Chromium 實際操作前端 22 項通過：被接管後站內換頁會導回登入頁並清掉 cookie、設定密碼的畫面與成功後的切換、登入超過 5 分鐘時被拒絕。接管後的狀態是用 SQL 直接製造的。
- 需要真實帳號的兩項由使用者在瀏覽器手動確認（2026-10-10）：由 Google / GitHub 登入實際接管密碼註冊的帳號，以及接管後再用 Facebook 登入時畫面顯示 409 的訊息。
- **尚未驗證**：升級 SQL「清密碼、刪 Facebook 綁定」那兩個動作沒有實際資料可以驗到；兩個時間差的修正沒有做到在兩個交易之間精確插入的測試。
