# 更新紀錄

記錄 Spring Boot 版後端（`livefit/`）從 `springboot-backend` 分支之後的功能更新。最新的放最上面。

Node 版（`backend/`）的作業內容不在此記錄範圍；Spring Boot 版最初的移植過程見 `docs/springboot-migration-plan.md`。

---

## 2026-10-26 — 設定 branch protection，實際用 GitHub、Facebook 登入一次

處理下面那筆「還沒做的」第一、三項。程式碼沒有改動。

### branch protection

使用者在 GitHub 建了 ruleset `springboot-backend CI`（Active，目標 `springboot-backend`），用 `gh api repos/.../rulesets` 讀回確認：

- 必要檢查：`mvn test`、`jest 與 Postman`（來源 GitHub Actions），沒有勾「分支必須是最新」。
- Restrict deletions、Block force pushes。
- Bypass：Repository admin，只限 PR。
- 沒有勾 Require a pull request before merging。效果差不多：有必要檢查時，直接 push 的 commit 必須先在別處跑過 CI，所以本機 `git merge` 後 push 的 merge commit 會被拒絕。

之後要改用 PR 頁面的合併按鈕或 `gh pr merge`，不再本機合併後 push。workflow 有 `paths` 過濾，只改文件的 PR 不會觸發 CI、必要檢查會一直等待，這時用 bypass 合併。規則是否真的會擋，下一次開 PR 時確認。

### GitHub、Facebook 實際登入

在 `livefit-final-postgres-1`、本機 livefit（8080）、前端 `npm run dev`（5173）上，由使用者操作登入，從後端 log 與 `user_identities` 確認結果。目的是驗證 10-25 的嚴格型別設定下，平台實際回傳的型別與 record 相符（不符時登入會直接失敗）。

| 平台 | 結果 | 證明了什麼 |
|---|---|---|
| GitHub | 成功。email 對到一個 Google 建的已驗證帳號，只新增綁定，`token_version` 沒變（沒有接管） | 換 token、`/user`（`Long id`）、`/user/emails`（`primary`、`verified`）都解析成功 |
| Facebook | 409 `此 Email 已註冊，請改用原本的方式登入`，email 已有帳號且 Facebook 視為未驗證，符合設計 | 這句是拿到 email 之後才丟的，換 token 與 `/me`（`String id`、`name`、`email`）都解析成功 |

後端 log 沒有反序列化錯誤。Facebook「建立新帳號並綁定」的路徑沒有走到，它和 409 用的是同一段解析，不另外補驗。

### 還沒做的

- **選用的架構調整**：同 2026-10-21 CI 那筆，先保留。

---

## 2026-10-25 — CI 第二階段：在 CI 上跑 jest 與 Postman

分支：`feature/livefit-ci-stage2`（PR #2）

處理 2026-10-21 CI 那筆「還沒做的」第一項。`livefit/` 的主程式沒有改動。

### 新增

`.github/workflows/livefit-test.yml` 新增 `contract` job（「jest 與 Postman」），和原本的 `mvn test` 並行：

- 用自己的空 postgres，`-DskipTests package` 打包後在背景啟動 livefit，等 `/healthcheck` 就緒（發現 java 已結束就停，`curl` 有逾時）。
- 跑 `npm test`（jest 68 項，含 smoke 4 項）與 `postman:build`、`postman:test`（168 支請求）。jest 失敗時 Postman 照跑，同一輪看得到兩套的結果。
- 失敗、逾時或被取消時，印出 livefit log 的最後 200 行，完整的 log 上傳成 artifact `livefit-log`。
- `-DskipTests` 是刻意的：Java 測試由 `mvn test` job 負責。前提是 `mvn test` job 一直存在而且無條件執行。

其他改動：

- 觸發路徑加上 `test/**`、`postman/**`、`docs/openapi.yaml`、`package.json`、`package-lock.json`；`livefit/`、`postman/` 底下的 `.md` 排除。
- DB 連線的 `env` 從 job 層移到 workflow 層，兩個 job 共用。

### 安全設定（`security-reviewer` 提出）

- newman 從 `npx --yes newman` 改成 devDependency `6.2.3`（不加 `^`），整個相依樹由 `package-lock.json` 鎖住。CI 用 `npm ci --ignore-scripts`（lockfile 裡唯一有安裝腳本的是 macOS 專用的 `fsevents`）。
- workflow 寫明 `permissions: contents: read`；checkout 用 `persist-credentials: false`。repo 目前的預設權限本來就是唯讀，這是讓設定改了也不受影響。
- `contract` job 的 `JWT_SECRET` 只交給 java 行程，不寫進 `GITHUB_ENV`。
- 加入 newman 後 `npm audit` 從 6 個漏洞變成 24 個（含 1 個 critical：handlebars），全在 newman 的相依樹，用 `npx` 時其實是同一批、只是看不到。`npm audit --omit=dev` 是 0。newman 6.2.3 是最新版，只跑自己的 collection、只連本機時風險很低，接受；前提寫在 `security-reviewer.md`。

### 審查意見的其他處理（`code-reviewer` 提出）

- 上傳 artifact 加 `overwrite: true`（重跑失敗的 job 時不會撞名）；CI 上的 Postman 每支請求最多等 10 秒（`--timeout-request 10000`）。
- 沒做：Maven wrapper 的 `distributionSha256Sum`（既有問題，選做）；`SERVER_ADDRESS=127.0.0.1`（Node 解析 `localhost` 可能先拿到 `::1`，jest 與 newman 會連不上）；隨機的 `ADMIN_PASSWORD`（帳密本來就公開在版本庫，runner 是拋棄式的）。

### 文件

- `CLAUDE.md`：「CI」一節改寫（兩個 job 的表格、觸發路徑、env、資料庫、`-DskipTests` 的前提、安全設定、CI 與本機結果不同時怎麼查）；「Postman」補 newman 與 `npm audit`；「測試的分工」註明三套都在 CI 上跑；Jackson 一節改寫兩支 Java 測試的理由；「注意事項」補 newman 與 `package-lock.json` 不要 merge 回 `main`。
- `.claude/agents/`：`code-reviewer.md` 的 workflow 規則加上三個例外與新的回報情況；`security-reviewer.md` 的「CI 與機密」改成現在式並補上安全設定、Maven 快取、`npm audit` 的前提；`test-runner.md` 補 smoke、CI 的說明與找不到 newman 時的處理。
- `docs/springboot-migration-plan.md` §15、`postman/README.md`（`npm ci`、CI 也會跑）、`build-collection.js` 與 `JacksonCoercionTest` 的註解。

### 驗證

- PR #2 第一次推上去就通過：`mvn test` 128 項（1 分 12 秒），`jest 與 Postman`：jest 68 項、Postman 168 支請求 481 項斷言（1 分 20 秒）。runner 是 `ubuntu-24.04`。
- 加固後再推一次也通過：`npm ci --ignore-scripts` 裝了 433 個套件，用的是 lockfile 裡的 newman。
- **「jest 失敗時 Postman 照跑」沒有在 CI 上實際觸發過**：要觸發得改壞 `test/`，那是不可修改的目錄。運算式由 `code-reviewer` 依 GitHub Actions 的語意逐一確認過。逾時、取消時的診斷步驟也沒有實際觸發過。
- 兩個 reviewer 各審兩輪（`security-reviewer` 第二輪曾因 rate limit 中斷，之後接著審完），最後都沒有高、中嚴重度的問題。

### 還沒做的

- **branch protection**：`springboot-backend` 沒有設保護規則，CI 失敗時仍然可以 merge，`-DskipTests` 的前提也只靠 workflow 本身維持。建議在 GitHub 的 Settings → Branches 把 `mvn test` 與 `jest 與 Postman` 兩個 check 設成必要檢查（只能由使用者設定）。
- **選用的架構調整**：同 2026-10-21 CI 那筆。
- 用 GitHub、Facebook 實際登入一次，記下平台的回應（同上一筆）。

---

## 2026-10-25 — 字串欄位不再接受數字與布林

分支：`feature/jackson-strict-string`

處理下面「整數欄位不再接受字串與小數」那筆「還沒做的」第一項。

### 修正

- 新增 `config/JacksonConfig`：一個 `Jackson2ObjectMapperBuilderCustomizer`，用 `postConfigurer` 把「整數、小數、布林轉成字串」（`LogicalType.Textual`）設成失敗。這個方向沒有對應的 properties，所以寫成設定類別。
- `{"name": 123}`、`true`、`3.14` 原本會被存成 `"123"`、`"true"`、`"3.14"`（實測技能名稱），現在回 400 `欄位未填寫正確`，與 Node 版的 `isValidString`（`typeof === "string"`）一致。
- 影響所有 request body 的字串欄位，以及 GitHub、Facebook 回應的解析。兩個平台回傳的字串欄位都是 JSON 字串，這是依兩個 reviewer 對平台文件的了解，沒有連網查證；OAuth 測試現在會驗 record 與假回應一致。
- 型別錯誤排在所有 service 檢查之前，所以有幾處回的訊息變了，狀態碼都還是 400：
  - 註冊、登入、修改密碼、建立管理員送數字密碼：原本回「密碼不符合規則…」，現在回 `欄位未填寫正確`，與 Node 版一致。數字不可能符合密碼規則，所以沒有原本能成功的請求因此失敗。
  - 無密碼帳號設定密碼：`password` 帶數字或布林，原本會被忽略、照常設定，現在回 400；`new_password` 帶數字時，原本先回「請重新登入」，現在先回 `欄位未填寫正確`。openapi 補上說明。前端對無密碼帳號不送 `password`，不受影響。
  - M7 升級教練的 `description` 帶數字，原本會被存成字串，現在回 400。
- `profile_image_url` 送數字時，Node 版會跳過 https 檢查照收，livefit 回 400。這個差異原本就有（livefit 以前會轉成字串，再卡在 https 檢查），不是這次造成的，這次記進 migration plan §8 第 15 列。

### 測試

Postman 新增 4 支，全部預期 400 `欄位未填寫正確`，整數、小數、布林三種輸入都有：

| 請求 | 輸入 |
|---|---|
| 新增技能：name 是數字 | `12345` |
| 新增方案：name 是布林 | `true` |
| 更新暱稱：name 是數字 | `2026` |
| 開課：description 是小數 | `3.14` |

Java 測試：

- `JacksonCoercionTest` 加上 `@Import(JacksonConfig.class)`，新增「數字與布林不能轉成字串」「字串與null照常接受」2 項，共 5 項。
- 新增 `JacksonConfigWiringTest`（`@SpringBootTest`，1 項）：從 `RequestMappingHandlerAdapter` 的 converter 清單取出解析 JSON 的 mapper，驗兩個方向的設定都有套用。`@JsonTest` 要 `@Import` 才會載入 `JacksonConfig`，驗不到正式環境靠 component scan 載入；拿掉 `@Configuration` 時其他測試都照樣通過，這支補上這個缺口（`code-reviewer` 指出）。第一版直接注入 converter bean，`code-reviewer` 複審時指出加上 `@EnableWebMvc` 時 bean 還在、MVC 卻換了 mapper，所以改成從清單取。
- `GithubOAuthClientTest`、`FacebookOAuthClientTest` 加上 `@Import(JacksonConfig.class)`。`OAuthTestRestClients` 開頭先檢查注入的 mapper 會拒絕把數字轉成字串，忘了 `@Import` 時直接失敗。

### 文件

- `CLAUDE.md` 原本的「整數欄位不接受字串與小數」一節改寫成「Jackson 型別轉換：欄位不接受別的型別」，用表格列出兩個方向各靠哪個設定。
- `.claude/agents/security-reviewer.md`、`code-reviewer.md` 同步：`JacksonConfig` 與它的 `@Configuration` 不能拿掉、`@JsonTest` 要 `@Import`。`security-reviewer.md` 刪掉「數字送進字串欄位是已知落差，不必回報」，改成寫明被型別錯誤搶先回 400 不構成繞過，會被這一層跳過的是登入失敗計數、稽核紀錄這類每個請求都要做一次的副作用，要放在 filter 層。
- `docs/openapi.yaml` 無密碼帳號設定密碼那一段，補上三個欄位有帶的話都必須是字串或 null。
- `docs/springboot-migration-plan.md` §8 加第 15 列。
- `postman/README.md` 的數量改成 168 支請求、481 個斷言。

### 驗證

- 改前用 curl 重現（測完刪除）；改後 `12345`、`true`、`3.14` 都回 400，正常字串照常建立，上一筆的整數設定仍然有效。
- `mvn test` 128 項（原本 125；`JacksonCoercionTest` 加 2 項、`JacksonConfigWiringTest` 1 項）、jest 68 項、Postman 168 支請求 481 項斷言，0 失敗。資料庫是 `livefit-final-postgres-1`。10 支型別案例（上一筆 6 支加這次 4 支）逐支確認都回 400。jest 是在補 `JacksonConfigWiringTest` 與改文件之前跑的，之後主程式沒有變動。
- 前端只有四個整數欄位做了數字轉換，沒有把數字送進字串欄位的地方。
- 反向驗證，都已還原：
  - `JacksonCoercionTest` 拿掉 `@Import`：「數字與布林不能轉成字串」失敗。
  - `GithubOAuthClientTest` 拿掉 `@Import`：helper 的檢查讓 6 項全部失敗。
  - Facebook 假回應的 `id` 改成數字：成功登入那項失敗。
  - 拿掉 `JacksonConfig` 的 `@Configuration`：`JacksonConfigWiringTest` 失敗，`JacksonCoercionTest` 照樣通過。
  - `WebMvcConfig` 加上 `@EnableWebMvc`：`JacksonConfigWiringTest` 失敗。
- 上一筆 merge 回 `springboot-backend` 後 push，GitHub 上的 CI 通過（125 項，`ubuntu-24.04`）。這個分支還沒 push。

### subagent 的使用情況

- `test-runner` 調整後的規則：逐支列出指定的 Postman 請求、回報資料庫容器、不再把兩個管理員混為一談，這三點都做到了。第一次呼叫時，前置檢查的 `docker ps`、`curl` 還是先在沙盒內試了一次（三套測試本身直接在沙盒外跑），之後兩次沒有再發生。
- `code-reviewer` 審了三輪：第一輪指出 `@Import` 驗不到接線、文件過時、openapi 不一致；第二輪指出注入 converter bean 不等於 MVC 實際用的 mapper；第三輪沒有問題。
- `security-reviewer` 沒有發現問題。

### 還沒做的

- **CI 第二階段**、**選用的架構調整**：同 2026-10-21 CI 那筆。
- 用 GitHub、Facebook 實際登入一次，記下平台的回應。今天兩筆修正都讓 record 型別不符時登入直接失敗，目前只有 reviewer 的判斷，沒有實際回應佐證。

---

## 2026-10-25 — 依第一次使用的狀況調整 `test-runner`

分支：`springboot-backend`

處理下面那筆「還沒做的」第三項。`livefit/` 的程式碼沒有改動。

`.claude/agents/test-runner.md`：

- 連 localhost 與 Docker 的指令（`curl`、`docker ps`、`mvn test`、jest、Postman）一開始就用 `dangerouslyDisableSandbox: true`，不要先在沙盒內試。之前在 prompt 裡寫明了，它還是先在沙盒內跑了一次。
- 權限確認回「暫時無法判斷」時，原樣重試一次就停下來回報。之前分類器沒有回應時，它連試了 4 次。
- 呼叫者指定的 Postman 請求要存檔後用 grep 逐支列出結果，只看摘要不算確認過。
- 寫明 Postman 重設的是它自己建立的管理員，不是 `admin@gmail.com`。之前它把兩者混為一談。
- 用 `docker ps` 回報在線的 postgres 是哪個容器；呼叫者指定的容器不在線時不執行。

`CLAUDE.md` 的「Subagent」一節補上這幾點。新的規則下一次呼叫時才會用到，目前還沒實際驗證過。

---

## 2026-10-25 — 整數欄位不再接受字串與小數

分支：`feature/jackson-strict-coercion`

處理 2026-10-21 CI 那筆「還沒做的」第二項，也是三個 subagent 第一次實際使用。

### 修正

- `application.properties` 加上 `spring.jackson.mapper.allow-coercion-of-scalars=false` 與 `spring.jackson.deserialization.accept-float-as-int=false`。`price`、`credit_amount`、`experience_years`、`max_participants` 送 `"10"` 或 `10.9`，原本會被轉成 `10` 收下（實測 `"1400"` 存成 1400、`7.5` 存成 7），現在回 400 `欄位未填寫正確`，與 openapi、Node 版一致。
- 兩行都要：小數轉整數由 `accept-float-as-int` 決定，而且 Jackson 先判斷它，只關第一行的話 `10.9` 仍會被截成 `10`。
- 空字串 `""` 原本轉成 null 再由 service 回 400，現在由 Jackson 直接擋，結果相同。
- `ValidUtils.isInteger` 的註解改成說明型別由這兩個設定擋。

附帶的差異：`10.0`、`1e1` 也會被擋，Node 版會收。前端的 `JSON.stringify` 不會送出這兩種寫法。記在 migration plan §8 第 14 列。

### 測試

Postman 新增 6 支，全部預期 400 `欄位未填寫正確`。四個有 `Integer` 欄位的 request DTO 各至少一支；字串與小數兩種輸入是合起來涵蓋，不是每個 DTO 都測兩種（設定作用在目標型別，不分 DTO）。M7 升級教練與更新課程共用同一個 DTO，沒有重複加。

| 請求 | 輸入 |
|---|---|
| 新增方案：price 是字串 | `"1400"` |
| 新增方案：credit_amount 是小數 | `7.5` |
| 升級教練：年資是字串 | `"3"`（排在「升級教練 A」之前，被收下的話教練 A 會提早升級） |
| 更新教練資料：年資是小數 | `2.5` |
| 開課：max_participants 是字串 | `"10"` |
| 開課：max_participants 是小數 | `10.5` |

Java 測試（兩個 reviewer 都指出的缺口）：

- `GithubOAuthClientTest`、`FacebookOAuthClientTest` 改成 `@JsonTest`，透過新的 `OAuthTestRestClients.builder` 用 Spring Boot 建好的 `ObjectMapper` 解析假回應。原本用 `RestClient.builder()`，它的 ObjectMapper 不讀 `application.properties`，record 型別與新設定不相容時測不出來。helper 會確認 JSON 真的交給換上的 converter 處理，沒換到時直接失敗，不會悄悄退回預設值。
- 新增 `JacksonCoercionTest`（3 項）：只驗 `ObjectMapper` 的設定有沒有生效，不打 API。CI 只跑 `mvn test`，沒有它的話設定被拿掉、或升級 Boot 後屬性名稱改了，CI 抓不到。

### 文件

- `CLAUDE.md` 新增「整數欄位不接受字串與小數」一節；「測試的分工」的 Java 測試欄補上「CI 必須擋下的設定」。
- `.claude/agents/security-reviewer.md`、`code-reviewer.md`：Jackson 那一項從「目前會被接受」改成「不能拿掉」，並補上新增 `Integer` 欄位時要加 Postman 案例、設定層的 Java 測試不算重複 Postman。
- `docs/springboot-migration-plan.md` §8 加第 14 列。
- `postman/README.md` 的數量改成 164 支請求、469 個斷言。
- `code-reviewer` 第三輪複審後，`CLAUDE.md` 與 `code-reviewer.md` 另外補了三點：前端送整數欄位要送 JSON 數字；`fixtures.cleanUp()` 只有會寫入資料庫的測試需要，否則審查者會把 `@JsonTest` 的測試報成少了 cleanUp；升級 Boot 4 時要先把 `JacksonCoercionTest` 改成驗 MVC 實際使用的 mapper，否則可能驗錯對象而照樣通過。

### 驗證

- 改設定前用 curl 重現：`price: "1400"`、`credit_amount: 7.5` 都回 200 並寫入（測完刪除）。改後這兩種與 `""`、`10.0`、`1e1` 都回 400，`price: 1400` 照常建立。
- `mvn test` 125 項通過（原本 122，加 `JacksonCoercionTest` 3 項）。
- jest 68 項通過，合法的數字沒有被擋。
- Postman 164 支請求、469 項斷言，0 失敗。跑完查資料庫，6 支被擋的請求沒有留下資料，教練年資維持 5（沒有被 `2.5` 截成 2）。
- 上面幾項一開始起錯了資料庫：用的是根目錄 `docker-compose.yml` 的 postgres（舊環境裡手動建的 `livefit` 資料庫），livefit 平常用的是 `compose.livefit.yml` 的 `livefit-final`。換過去重跑：`mvn test` 125 項、jest 68 項通過；Postman 164 支請求、469 項斷言 0 失敗，6 支新請求逐支確認都回 400。舊環境留下一輪 Postman 的 `pm_*` 測試資料（6 個帳號、1 個技能、1 個方案、3 堂課），沒有清。
- 反向驗證：用 `-D` 把兩個設定改回 `true`，`JacksonCoercionTest` 有兩項失敗；GitHub 假回應的 `id` 改成字串，GitHub 測試失敗；helper 改成不換 converter，`FacebookOAuthClientTest` 5 項、`GithubOAuthClientTest` 6 項全部失敗。都已還原。
- 沒有實際用 GitHub、Facebook 登入。GitHub 的 `id` 是數字、`primary` 與 `verified` 是布林，Facebook 的 `id` 是字串，這些是依兩個 reviewer 對平台 API 格式的了解，沒有對照官方文件或實際回應（測試裡的假回應是照 record 寫的，不能當證據）。之後實際登入時，順便記下平台的回應來佐證。

### 三個 subagent 第一次使用

- 流程照 `CLAUDE.md`：改完 → `test-runner` → 兩個 reviewer 同時審。處理完 reviewer 提出的項目後，再跑一次 `test-runner`，並請 `code-reviewer` 複審補上的測試。
- 兩個 reviewer 都沒有高、中嚴重度的發現，但各自指出同一個測試缺口（上面的 OAuth 測試）。`code-reviewer` 複審時又抓到 helper 換不到 converter 時會悄悄通過。在 prompt 貼上 diff 就能正常審查。
- `test-runner` 的回報要自己再確認：
  - 它說 Postman「把預設管理員的密碼重設成隨機值」，實際上改的是 Postman 自己建立的管理員，`admin@gmail.com` / `Admin123` 仍可登入。
  - 第二次呼叫時 prompt 已註明要跳出沙盒，它還是先在沙盒內跑，全部失敗後才重跑。
  - Postman 只看了摘要，沒有逐支確認新請求的狀態碼，改用查資料庫補驗。換資料庫重跑時才逐支確認。
- 換資料庫重跑時，auto mode 的權限分類器一直沒有回應，`test-runner` 與主 session 都跑不了 Postman，最後由使用者在輸入框用 `!` 執行。

### 還沒做的

- **數字送進字串欄位會被轉成字串收下。** `{"name": 123}` 會被當成 `"123"`，Node 版的 `isValidString` 要求 `typeof === "string"`，所以回 400。這次的兩個設定管不到 `String`（`allow-coercion-of-scalars` 只管數字、布林、日期），沒有對應的 properties，要寫 `Jackson2ObjectMapperBuilderCustomizer`，把轉成 `LogicalType.Textual` 的整數、小數、布林輸入設成失敗。這會影響所有字串欄位，以及 GitHub、Facebook 回應的解析（平台回傳的字串欄位都是字串，預期不受影響，OAuth 測試現在能驗這點）。
- **CI 第二階段**、**選用的架構調整**：同 2026-10-21 CI 那筆，沒有變動。
- `test-runner.md` 可以寫明連 localhost 與跑 `mvn test` 要直接跳出沙盒，省掉在沙盒內白跑一次。

---

## 2026-10-21 — 新增審查與測試用的三個 subagent

分支：`springboot-backend`

下面那筆（CI）的「還沒做的」列了三項：CI 第二階段、Jackson 型別轉換、選用的架構調整。開始做之前，先加上三個專案層級的 subagent，之後每一項改完都用它們檢查。`livefit/` 的程式碼沒有改動。

### 新增

`.claude/agents/`：

- `security-reviewer`：權限、身分驗證、輸入驗證、CI 機密。工具只有 `Read, Grep, Glob`。
- `code-reviewer`：業務邏輯與邊界、是否符合 `docs/openapi.yaml`、測試有沒有被刪除、跳過或放寬。工具只有 `Read, Grep, Glob`。
- `test-runner`：執行 `mvn test`、jest、Postman，只回報失敗項目、位置與可能原因，不回傳完整 log。工具是 `Bash, Read`，沒有 Edit、Write；`model: haiku`。

三項待辦各由誰檢查：

| 項目 | 檢查重點 |
|---|---|
| CI 第二階段 | `security-reviewer` 看 workflow 有沒有印出密鑰、上傳的 artifact 有沒有含密鑰；`code-reviewer` 看有沒有用 `-DskipTests`、`continue-on-error` 等方式跳過測試；`test-runner` 先在本機跑 jest 與 Postman |
| Jackson 型別轉換 | `security-reviewer` 看 400 的回應有沒有帶出 Jackson 的原始訊息、GitHub 與 Facebook 回應的解析會不會因此失敗；`code-reviewer` 看全域設定對各端點的影響，以及 Postman 有沒有補上驗證 |
| 選用的架構調整 | `code-reviewer` 看交易範圍、上鎖順序、錯誤檢查的順序有沒有改變；`test-runner` 跑 `mvn test` |

### 已知限制

- 本機的 Claude Code（2.1.306）在 macOS 上預設沒有 `Grep`、`Glob`，搜尋走 Bash。依官方文件，subagent 的 `tools` 列了這兩個而沒列 `Bash` 時會單獨拿到它們，所以兩個 reviewer 能搜尋程式碼，但不能跑 git，呼叫時要在 prompt 附上 diff 或變更檔案清單。
- `test-runner` 有 Bash，可以寫入專案目錄。「不改檔案、不起停服務」是 prompt 裡的規則，沒有用 hook 強制：hook 只能用關鍵字比對指令，擋不完整。

### 文件

- `CLAUDE.md` 新增「Subagent（`.claude/agents/`，僅 `livefit/`）」一節。

### 驗證

- frontmatter 的欄位與 `tools` 的行為對照過官方文件。
- `test-runner` 用來判斷 8080 上是哪個後端的 `GET /api/admin/users`，是看兩邊的程式碼確認的（livefit 的 `SecurityConfig` matcher、Node 版 `backend/app.js` 的 404 handler），沒有實際打過。
- 還沒有實際呼叫過這三個 subagent：`.claude/agents/` 是 session 中途建立的，要重開 session 才會載入。

---

## 2026-10-21 — 加上 Spring Boot 版的 CI（只跑 `mvn test`）

分支：`feature/livefit-ci`

處理 2026-10-12 那筆「還沒做的」CI。第一版只跑 Java 測試，jest 與 Postman 要啟動後端，之後再決定要不要加。`livefit/` 的程式碼沒有改動。

### 新增

`.github/workflows/livefit-test.yml`：

- 觸發：push 到 `springboot-backend`，或開 PR 到 `springboot-backend`，而且改到 `livefit/**` 或這個 workflow 檔。只改文件不會跑。
- 沒有 `workflow_dispatch`：手動觸發要求 workflow 檔在預設分支 `main` 上，而這個檔案不會進 `main`。
- postgres service 用與 compose 相同的 `postgres:16.4-alpine3.20`，`POSTGRES_DB` 直接設成 `livefit`。
- JDK 21（temurin），`./mvnw -B -ntp test`，Maven 依賴用 `setup-java` 快取。
- 資料庫連線寫在 workflow 的 `env`；`JWT_SECRET` 每次執行用 `openssl rand -hex 32` 產生並遮罩，不把密鑰寫進版本庫。第三方登入的變數都不設。
- 失敗時把 `livefit/target/surefire-reports/` 上傳成 artifact `surefire-reports`。
- 現有的 `test.yml` 沒有動。**這個 workflow 檔不要 merge 回 `main`**（`.github/` 是 README 宣告不可修改的目錄）。

### 文件

- `CLAUDE.md` 新增「CI（僅 `livefit/`）」一節。
- `docs/springboot-migration-plan.md` §15 的表格補上 CI。

### 驗證

在本機模擬 CI 的條件：用 `git archive` 匯出乾淨的 `livefit/`（沒有 `.env`），新起一個空的 `postgres:16.4-alpine3.20`，只給 workflow 裡的那幾個環境變數與隨機的 `JWT_SECRET`。

- `mvn test` 122 項通過。
- 跑完後資料庫只剩 `AdminSeeder` 建的 `admin@gmail.com`，其他表都是空的：測試會清掉自己建的資料，也不依賴資料庫裡原本就有的資料。

2026-10-12 那筆列出的待確認事項，目前的狀況：

| 項目 | 結果 |
|---|---|
| 全新的空資料庫 | 本機模擬通過 |
| 第三方登入不需要金鑰 | 本機模擬時這些變數都沒設，通過 |
| `./mvnw` 在 Linux runner 上能不能用 | 能用，GitHub 上第一次執行就通過 |
| 併發測試在 runner 上是否穩定、時區 | 第一次執行通過（`BookingConcurrencyTest` 15 項 4.9 秒）。只跑過一次，偶爾失敗時照 `CLAUDE.md` 的說明先查是不是真的有競爭 |
| 跨年那一刻年份不一致 | 沒有處理，維持原本的判斷 |

GitHub 上的第一次執行：PR #1 觸發，runner 是 `ubuntu-24.04`，122 項通過，整個 job 1 分 6 秒（其中 `mvn test` 39 秒）。

GitHub 當時提示 `ubuntu-latest` 從 2026-10-19 起會陸續換成 Ubuntu 26。之後如果 CI 失敗但本機正常，先看 log 開頭的 `Image:` 是不是換了版本，必要時把 `runs-on` 固定成 `ubuntu-24.04`。

### 還沒做的

- **CI 第二階段**：在 CI 啟動 livefit，跑 jest 與 Postman。
- **Jackson 會把字串與小數轉成整數。** `{"price": "10"}` 與 `{"price": 10.9}` 現在都被當成 `10` 接受，但 Node 版與 openapi 都要求回 400 `欄位未填寫正確`（例如「max_participants 非數字整數」）。`ValidUtils.isInteger` 的註解寫「字串數字視為不合法」，實際上沒有擋到。型別轉換發生在反序列化，比 service 的檢查還早，所以改用 `@Valid` 也擋不到。修法是在 `application.properties` 加上 `spring.jackson.mapper.allow-coercion-of-scalars=false` 與 `spring.jackson.deserialization.accept-float-as-int=false`。已用 Jackson 2.19.4 實測，加了之後這幾種輸入會改成丟例外，由現有的 `HttpMessageNotReadableException` handler 回 400。前端的數字欄位都用 `v-model.number`，GitHub、Facebook 回應的型別也和 record 的欄位一致，預期都不受影響。這是 HTTP 合約層的行為，驗證要加在 Postman。
- **選用的架構調整，等 CI 上線後再做。** 檢視過 controller 與 service 的分工，結論是正確、維持現狀。驗證也繼續留在 service：錯誤訊息與檢查順序是合約，而且 `updatePassword` 的規則要看資料庫裡有沒有密碼，DTO 上的註解做不到。之後可以考慮的三點都在 service 層內部，而且不改變行為：
  - 剩餘堂數「購買總堂數 − 未取消報名數」在 `CourseService.book` 與 `UserService.getBookings` 各算一次。
  - `UserService.getBookings`、`getCreditPurchases` 屬於報名與方案，可以搬到 `CourseService`、`CreditPackageService`。
  - `AdminUserService.resetAdminPassword` 只用到呼叫者的 id，卻收整個 `AuthUser`。

---

## 2026-10-12 — 合併 Java 測試與 Postman Collection，新增 Postman Collection 的紀錄

`feature/livefit-service-tests` 與 `feature/postman-collection` 都已 merge 回 `springboot-backend`（`61ab39a`、`3ecee7a`，沒有衝突，已 push 到 `origin/springboot-backend`）。下面兩筆 2026-10-12、2026-10-11 是前者的內容；後者原本沒有自己的紀錄，補在這裡。

### Postman Collection（`postman/`）

針對 Spring Boot 版的端到端測試：158 支請求、451 個斷言，涵蓋 M0 ~ M7 與第三方登入的失敗案例。Node 版不適用。

- `postman/livefit.postman_collection.json` 與 `livefit.postman_environment.json` 都由 `postman/build-collection.js` 產生，不要直接改 JSON。**這兩個檔案合併後已列進 `.gitignore`、從版本庫移除**，剛 clone 下來要先跑 `npm run postman:build`。
- 根目錄 `package.json` 多了 `postman:build`（重新產生）與 `postman:test`（用 newman 整份執行）。根目錄 `package.json` 是 README 宣告不可修改的檔案，**這個改動不要 merge 回 `main`**。
- 請求之間用 collection 變數串接 token 與 id，不需手動登入；要整份依序執行。
- 斷言有兩層：流程斷言照 livefit 的實際行為寫在腳本裡；名稱以 `[openapi]` 開頭的拿每支回應比對 `docs/openapi.yaml`。
- 使用方式與注意事項在 `CLAUDE.md` 的「Postman Collection」與 `postman/README.md`。

### 文件

- `CLAUDE.md` 新增「測試的分工」與「報名的鎖與月營收的算法」兩節，內容取自 2026-10-11 那筆的討論結論。
- `docs/springboot-migration-plan.md`：§6 的營收公式改成先乘後除、§8 的差異表加上第 13 項（報名上鎖與唯一約束）、新增 §15 列出三套測試的位置與執行方式。
- 2026-10-11 那筆的「待決事項」小節已刪掉：合併與唯一約束都做完了，剩下的一項移到下面。

### 清掉已合併的分支

本機與遠端現在都只剩 `main` 與 `springboot-backend`。刪掉的分支內容都已經在 `springboot-backend` 裡，commit 與 merge commit 仍在歷史中，需要時可以用下表的 hash 重新開分支。

| 分支 | 最後一筆 |
|---|---|
| `feature/social-login`（本機與 `origin`） | `22d85fb` |
| `refactor/social-identity` | `00261c2` |
| `feature/github-login` | `191593f` |
| `feature/facebook-login` | `34773ac` |
| `fix/social-prehijack` | `67aeca3` |
| `fix/jwt-default-secret` | `6191712` |
| `fix/user-write-errors` | `99d63a5` |
| `feature/admin-role` | `a5a85dc` |
| `feature/livefit-service-tests` | `f99a57a` |
| `feature/postman-collection` | `cca9597` |

- 2026-10-04 那筆寫的「`feature/social-login` 合併後保留不刪」到這裡為止。當時保留是為了撤回 Google 登入後還能重新合併；之後 GitHub 登入、Facebook 登入、共用層、帳號接管、ADMIN 角色都疊在它上面，已經不可能單獨撤回那一次合併。
- 文件裡「`feature/xxx` 起…」的寫法指的是歷史上的時間點，分支已不存在。

### 合併後的驗證

- `mvn test` 122 項通過。
- `npm run postman:build` 重新產生的 collection 與當時版本庫裡的完全相同（之後才把產生的檔案移出版本庫）。
- 合併後沒有再啟動後端跑 jest 與 Postman。兩個分支改的檔案不重疊（一邊是 `livefit/` 與文件，一邊是 `postman/` 與 `package.json`），合併前對同一份 `livefit/` 程式碼跑過，結果見下一筆。

### 還沒做的

**CI**。2026-10-12 還沒決定要不要做。要做的話先接 `mvn test`，它不需要另外啟動後端，但需要 postgres 與 `livefit` 資料庫。當天先查到的事：

- **不要改現有的 `.github/workflows/test.yml`**。那是題目給的 Node 版驗收，只在 push 到 `main` 時觸發，而且 `.github/` 是 README 宣告不可修改的目錄。Spring Boot 版要另開一個 workflow 檔，觸發分支設成 `springboot-backend`，這個檔案不要 merge 回 `main`。
- **CI 上沒有 `livefit/.env`**，需要的值要寫在 workflow 的 `env`。`JWT_SECRET` 必填且至少 32 個位元組，沒設的話 Spring context 起不來，連資料庫的測試會全部失敗。資料庫連線用 `DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD`、`DB_DATABASE`。
- **資料庫名稱是 `livefit`**。現有 workflow 的 postgres service 建的是 `fitness`；新的 workflow 把 `POSTGRES_DB` 設成 `livefit` 就不用另外 `CREATE DATABASE`。表由 `ddl-auto=update` 在第一次啟動時建立。
- **JDK 用 21**。
- `./mvnw` 在 Linux runner 上應該能用：`CLAUDE.md` 寫的「不要用 `./mvnw`」是 macOS 的 `mktemp` 問題。**這一點沒有實測。**
- 還沒決定的：只跑 `mvn test`，還是也在 CI 啟動後端跑 jest 與 Postman。後者要先 `npm run postman:build`（兩個 JSON 不在版本庫裡），而且 Postman 的 M7 依賴預設管理員 `admin@gmail.com` / `Admin123`。

做 CI 前**不用先補測試**。CI 只是把現有的測試自動跑起來，之後新增的測試會自動被涵蓋。該確認的是現有的 122 項換到 CI 的環境會不會過，下面幾點都**還沒有在 CI 上實測**，做法是先接上去跑一次，哪個失敗修哪個：

- **全新的空資料庫**。CI 每次都從空的 `livefit` 開始，本機則累積了很多資料。讀過程式碼，現有測試都是自己建資料、結束時自己刪（`BookingFixtures.cleanUp`；`AdminUserApiTest` 用自己的管理員信箱 `m7-admin-test@example.com`，不依賴預設管理員），沒有看到預期資料庫裡已有某筆資料的測試。
- **併發測試在 runner 上是否穩定**。`BookingConcurrencyTest` 開 8 條執行緒，GitHub 的 runner 核心數少、速度不固定。等待上限是就位 10 秒、取結果 30 秒；逾時的話先看是不是機器慢，不要直接當成不穩定的測試而停用。
- **年份在跨年那一刻會不一致**。`CoachRevenueMonthBoundaryTest` 的 `YEAR` 是類別載入時用 `Year.now(UTC)` 算的，後端算營收時再取一次當年。只有剛好在 UTC 跨年前後執行才會受影響，本機不會遇到，CI 若排在那個時間點可能失敗一次。
- **時區**。runner 是 UTC，本機是 UTC+8。改 `course_booking.created_at` 的測試已經用 `CAST(? AS timestamp)` 搭配字串避開 JVM 時區換算，預期不受影響。
- **第三方登入的測試不需要真的金鑰**。`GoogleIdTokenVerifier` 換成假的，`GithubOAuthClientTest`、`FacebookOAuthClientTest` 不連外部服務，所以 `GOOGLE_CLIENT_ID` 等變數在 CI 可以留空。

---

## 2026-10-12 — `course_booking` 加唯一約束，重啟後補驗 HTTP 層

分支：`feature/livefit-service-tests`（2026-10-12 已 merge 回 `springboot-backend`）

處理 2026-10-11 那筆當時留下的待決事項（要不要加唯一約束），以及當時沒驗完的 HTTP 層。Node 版、`frontend/`、`docs/openapi.yaml`、根目錄 `test/` 都沒有改動。

### 改了什麼

- `CourseBooking`：`@Table` 加上 `uk_course_booking_user_course`，欄位是 `(user_id, course_id)`。已取消的報名也算在內，與「取消過的課不能再報名」的規則一致，所以不是只管未取消資料的部分索引。
- `CourseService.book`：寫入改用 `saveAndFlush`，接到 `DataIntegrityViolationException` 時回 400 `已經報名過此課程`。鎖與三項檢查的順序都沒有動。

### 約束與鎖的分工

- **鎖仍然是主要的防線，不能因為有約束就拿掉。** 約束只擋得住「同一人同一堂課」的重複，名額超賣與堂數扣成負的仍然只靠 `book` 裡的鎖。
- 約束擋的是鎖管不到的情況：有人拿掉鎖、繞過 `book` 直接寫入、或手動改資料。
- **`book` 裡一定要用 `saveAndFlush`，不要改回 `save`。** `save` 的 INSERT 會拖到交易提交才送出，例外在方法外才丟出來，接不到，使用者會看到 500 而不是 `已經報名過此課程`。

### 既有資料庫不用手動處理

10-11 那筆寫的「`ddl-auto=update` 不會把約束套到既有的表」**實測不成立**。Hibernate 6.6 啟動時發現表上沒有這個約束，會先送 `drop constraint if exists` 再 `add constraint`，本機的 `livefit` 資料庫（55 筆報名、沒有重複）就是這樣補上的。之後再啟動不會重建（約束的 oid 沒變）。

第一次啟動時 log 會有一行 `constraint "uk_course_booking_user_course" ... does not exist, skipping` 的 WARN，那是 `drop ... if exists` 的回應，可以忽略。

沒有實測的情況：表裡已經有重複的 `(user_id, course_id)` 時約束會加不上去。升級其他環境前先照 `docs/springboot-migration-plan.md` §14 的 SQL 檢查。

### 新增的測試

| 檔案 | 測什麼 | 連資料庫 |
|---|---|---|
| `BookingDatabaseStateTest`（加 2 項） | 繞過 `book` 直接寫入第二筆會被資料庫擋下；已取消的報名也算 | 是 |
| `BookingDuplicateBackstopTest`（新增，1 項） | 檢查都通過、寫入時才被約束擋下時，`book` 回 400 `已經報名過此課程` | 否（假的 repository） |

### 驗證

- `mvn test` 122 項全數通過（原有 119 項、新增 3 項），沒有停用的測試。併發的三個案例 15 次全過。
- Java 測試跑完後資料庫沒有殘留的測試資料。
- **HTTP 層已對重啟後的後端驗過**：根目錄 `npm test` 68 項通過；Postman Collection 158 個請求、451 個斷言通過。這也補上了 10-11 那筆兩個修正（報名上鎖、月營收先乘後除）當時沒驗到的部分。
- Postman 的檔案是用 `git show feature/postman-collection:postman/livefit.postman_collection.json` 取到暫存目錄執行的，沒有切分支。
- jest 與 Postman 各在 `livefit` 資料庫留下一輪測試帳號，這是既有行為。

### 沒有做的

- 沒有從 HTTP 同時送出多個報名請求來驗。併發只在 Java 直接呼叫 service 驗過；jest 與 Postman 都是一支一支打。

---

## 2026-10-11 — 補 M5 / M6 的 Java 測試，修正同時報名超賣與月營收少 1 元

分支：`feature/livefit-service-tests`（從 `springboot-backend` 的 `d38fc36` 開出，2026-10-12 已 merge 回去）

Node 版（`backend/`）、`frontend/`、`docs/`、根目錄 `test/` 都沒有改動。

### 要解決的問題

M1 ~ M6 只有根目錄的 jest 黑箱測試（與當時在另一個分支上的 Postman Collection），兩者都是一支一支打 API、只看回應，有幾類情況測不到：資料庫裡實際寫了什麼、同時送出的請求、時間邊界、計算的邊界。這次在 Java 補上這幾類，補的過程抓到兩個既有的 bug 並修掉。

### 修掉的兩個 bug

**同時報名會超賣**（`CourseService.book`）

`book` 的三項檢查（報名過、堂數、名額）都是先查再寫，沒有上鎖。用 8 條執行緒同時報名，三種情況每次都重現：

| 情境 | 應該 | 修正前 |
|---|---|---|
| 名額 1 的課，8 人同時報名 | 1 人成功 | 多人成功 |
| 同一人對同一堂課同時送 8 次 | 1 筆 | 8 筆 |
| 只剩 1 堂，同時報 8 堂不同的課 | 剩餘 0 | 剩餘 -4 到 -7 |

第二種在現實最容易發生：連點兩下報名按鈕就可能觸發。

**月營收在總額剛好整除時少 1 元**（`AdminCoachService.getRevenue`）

原本先把均價除到小數 10 位再乘筆數。1000 元 3 堂、3 筆報名會算成 333.3333333333 × 3 = 999.99…，捨去後是 999，照 openapi 的公式 floor(筆數 × Σprice ÷ Σcredit_amount) 應該是 1000。根目錄的 m6 測試只測 1 筆與 2 筆，所以沒抓到。

### 改了什麼

- `CourseService.book`：進入後先 `userRepository.findByIdForUpdate`，再 `courseRepository.findByIdForUpdate`。鎖使用者擋重複報名與堂數超扣，鎖課程擋超過名額。錯誤訊息與檢查順序不變。
- `CourseRepository`：新增 `findByIdForUpdate`（`PESSIMISTIC_WRITE`）。
- `AdminCoachService.getRevenue`：改成先乘筆數再除總堂數。

### 新增的測試（`livefit/src/test/java/com/percyku/livefit/service/`）

| 檔案 | 測什麼 | 連資料庫 |
|---|---|---|
| `BookingDatabaseStateTest` | 取消只寫 `cancelled_at`、建立時間不變；失敗的報名與購買不留資料；購買存的是當下的堂數與價格 | 是 |
| `BookingConcurrencyTest` | 上表三種同時報名的情境，各重複 5 次 | 是 |
| `CoachRevenueMonthBoundaryTest` | 月初第一秒、月底最後一秒、跨年、去年同月、取消不計、別的教練不計、月份名稱格式 | 是 |
| `CoachRevenueCalculationTest` | 營收金額的算法：捨去時機、沒有方案時不除以零、大金額不溢位 | 否（假的 repository） |
| `BookingFixtures` | 上面三個連資料庫的測試共用的資料建立與清理 | |

```bash
cd livefit && mvn test                                          # 全部
cd livefit && mvn test -Dtest='Booking*Test,CoachRevenue*Test'  # 只跑這次新增的
```

### 三套測試各自負責什麼

這次討論後的結論：三套測的層面不同，**不要追求每一套都涵蓋全部**。同一件事寫三遍不會變三倍保險，只會讓改一句錯誤訊息要改三個地方。

| 測試 | 位置 | 負責 | 需要 |
|---|---|---|---|
| jest 黑箱測試 | 根目錄 `test/` | M1 ~ M6 的作業驗收合約。題目給的，不動 | 後端在線 |
| Postman Collection | `postman/` | M0 ~ M7 串成一條流程、回應是否符合 `docs/openapi.yaml`、手動操作時不用自己貼 token | 後端與資料庫在線 |
| Java 測試 | `livefit/src/test/` | 黑箱測不到的：資料庫裡實際寫了什麼、同時送出的請求、時間邊界、計算的邊界；以及 M7、第三方登入與帳號接管 | 多數要資料庫在線，不需要啟動後端 |

考慮過、決定不做的兩件事：

- **不在 Java 用 MockMvc 把 M1 ~ M6 的「打 API 看狀態碼與訊息」再寫一遍。** 那和 jest、Postman 重複，Java 這邊只補上表列的那幾類。
- **不在根目錄 `test/` 加 M7 的 jest 測試。** `test/` 是 README 宣告不可修改的目錄；Node 版沒有 M7，加了之後 `npm test` 對 Node 後端會失敗；而且「從外部用 HTTP 驗 M7」Postman 已經在做。

其他釐清過的事：

- Postman Collection 的定位是**回歸測試**：改完後端、合併分支之前整份跑一次，提早發現「改 A 弄壞 B」。它只保護已經寫進去的行為，新功能要自己加請求，而且要有人去跑才有用。
- 執行 collection **需要後端與資料庫都在線**；只有「產生」collection 的 `postman:build` 不需要。
- collection 的流程與斷言是照 livefit 的程式碼手寫的，不是從 openapi 產生的；只有名稱以 `[openapi]` 開頭的那一層是讀 `docs/openapi.yaml`。
- M7 有兩個地方驗：`AdminUserApiTest`（MockMvc，能直接查資料庫，測完會清資料）與 Postman 的 M7 資料夾（真的 HTTP，資料會留下）。改 M7 的邏輯時兩個都跑；只跑一個就選 `mvn test`。

### 寫 Java 測試要注意的事

- **連的是開發用的 `livefit` 資料庫，不是另外的測試資料庫。** 每個測試都要在 `@AfterEach` 呼叫 `fixtures.cleanUp()`；新增會建資料的 helper 時，要把 id 記進 `BookingFixtures` 的清單並在 `cleanUp` 依外鍵順序刪。測試建的資料 Email 是 `svc-test-*@example.com`、名稱以「測試」開頭，中斷後有殘留可以用這個找。
- **後端沒有可替換的時鐘，都是直接呼叫 `Instant.now()`。** 要測「報名發生在某個時間點」只能事後用 SQL 改 `course_booking.created_at`。欄位是 `timestamp without time zone`、內容是 UTC，要用 `CAST(? AS timestamp)` 搭配字串，不要傳 `Timestamp` 物件（JDBC 會依 JVM 時區換算）。
- **營收金額不要連著真的資料庫測。** 均價是資料庫裡「所有」方案的 Σprice ÷ Σcredit_amount，別人建的方案也會算進去，所以金額只在 `CoachRevenueCalculationTest` 用假的 repository 驗；連資料庫的那支只看人數與筆數。
- 併發測試只跑一次可能剛好通過，所以用 `@RepeatedTest`。改 `book` 之後若偶爾失敗，那就是真的有競爭，不要當成不穩定的測試而忽略。

### 注意

- **鎖的順序固定是先使用者、後課程。** 之後若有別的地方也要同時鎖這兩種資料列，必須用同樣的順序，否則會死結。
- 鎖使用者那一列會讓同一個人的報名與改名、改密碼、被升級教練互相等待，都是瞬間完成的操作。
- 月營收**一定要先乘筆數再除總堂數**，不要為了可讀性改回先算均價。

### 沒有做的

- **`course_booking` 當時沒有加 `(user_id, course_id)` 的唯一約束**，擋重複只靠 `book` 裡的鎖。已在 2026-10-12 加上；當時以為 `ddl-auto=update` 不會把約束套到既有的表，實測會，見 2026-10-12 加唯一約束那一筆。
- **取消報名沒有上鎖。** 同一筆同時取消兩次都會回成功，結果仍是已取消，資料不會錯。

### 驗證

- `mvn test` 119 項全數通過（原有 55 項、新增 64 項），沒有停用的測試。
- 併發的三個案例修正前 15 次全失敗，修正後 15 次全過；營收少 1 元的四組數字修正前全失敗，修正後全過。
- Java 測試跑完後資料庫沒有殘留的測試資料。
- Postman Collection 451 個斷言通過，但打的是修改前就啟動的後端，無法確認它已重新載入新的程式碼。2026-10-12 已重啟後端重跑，見 2026-10-12 加唯一約束那一筆。

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
