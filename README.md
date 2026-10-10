# 最終任務：健身房網站後端建置

## 【任務描述】

課程從頭進行到現在，我們已經學習了 Node.js 模組、Express 路由、middleware、JWT、bcrypt、PostgreSQL 與容器化等。這一週的最終任務，就是把這些所學串聯、整合起來，實際打造出一個能運作的專案。

具體來說，你會拿到一個**完整的健身房網站前端**（會員、教練、課程、購買方案等頁面都完成了），以及一份 **API 規格書（Swagger）**。不過目前這個專案的後端不存在，所以你的任務就是根據現有情境跟規格，來嘗試將後端撰寫、建置出來，讓這個專案能正確運作。

關於後端程式的規劃跟撰寫，像是資料庫設計、程式如何拆分、要使用 TypeORM 或 pg，**同學都可自行決定**。驗收會從外部呼叫你的 API，然後檢查回應結果是否正確。


## 【環境準備】

1. 安裝並啟動 [Docker Desktop](https://www.docker.com/products/docker-desktop/)，並確認 Node.js 版本 >= 20

2. **Fork** 作業專案 [https://github.com/hexschool/node-js-final-2026/](https://github.com/hexschool/node-js-final-2026/)，再 clone 同學自己的 fork，進到專案後執行 `npm install`
   - 這次需使用到 Fork，因為最終驗收是要跑自己 Repo 的 GitHub Actions
   - Fork 專案之後進到自己 Repo 的 **Actions** 分頁，按下「**I understand my workflows, go ahead and enable them**」按鈕，因為 GitHub 對於 fork 過來的專案預設會關閉 Actions，所以我們需手動啟用。

3. 在此專案根目錄的終端機輸入：`docker compose up -d`，首次啟動要 build 前端，所以會需要多一些時間（約 2～5 分鐘），完成後會啟動三個服務，分別對應下面三個網址：
   - `http://localhost:3000`：健身房前端頁，**初始每一頁會報 API 錯誤**，這些還不能正常運作的頁面與功能，就是你的待辦清單
   - `http://localhost:8081`：Swagger API 文件。**這是作業唯一的 API 規格來源**，每個 endpoint 點進去都能看到完整的 request／response 範例與錯誤訊息
   - `localhost:5432`：PostgreSQL 資料庫（**目前是空的**），裡頭的資料表要從你的後端建立

（在 Docker Desktop 也會看到 `frontend`(3000)、`swagger`(8081)、`postgres`(5432) 成功啟動）

4. 接著在專案中建立一個後端資料夾，名稱請使用 `backend`（作業後續的容器化階段，`docker-compose.yml` 也是使用這個資料夾名稱）。建立後的專案結構如下：

```txt
node-js-final-2026/
├── backend/              # 目前要撰寫的後端，跑在 localhost:8080
├── frontend/
├── docs/
├── test/
└── docker-compose.yml
```

5. 承上，接著進入 `backend/`，初始化並安裝需要的後端套件，最後將專案根目錄的環境變數範本（`.env.example`）複製到 `backend/.env`。需注意：
   * 記得不可把 `.env` 推上 GitHub
   * 後端相關套件需安裝在 `backend/`，不可安裝在專案根目錄
   * ⚠️ `.env` 裡的 `PORT=8080` **不可更動**，這個後端需運作在 **8080**，因為前端寫死 `http://127.0.0.1:8080/api/` 路徑，以及 Swagger 的 Try it out 也是使用這個 port；如果更動 8080 port，前端跟 Swagger 就會運作失敗


```bash
# 進入後端資料夾
cd backend

# 初始化
npm init -y

# 安裝所需要的套件。例如：
npm install express cors dotenv pg

# 建議安裝 nodemon（存檔會自動重啟，方便開發）
npm install -D nodemon
```

6. 承上，初始化並安裝套件完成後，接著開啟 `backend/package.json`，在 `scripts` 中設定後端的啟動指令。以下範例假設實際負責啟動伺服器的入口檔案為 `backend/bin/www.js`，在 `scripts` 設置：

```
"dev": "nodemon ./bin/www.js",
"start": "node ./bin/www.js"
```

* `npm run dev`：開發期使用，透過 nodemon 啟動後端，當程式碼變更時，會自動重新啟動
* `npm start`：直接透過 Node.js 啟動後端，不會自動重新啟動。（GitHub Actions 驗收時會使用 `npm start`，所以記得要加上這個設定。）

⚠️ 啟動指令需指向實際負責啟動伺服器的入口檔案。如果你的入口檔案是 `server.js`、`app.js` 或其他路徑，請依照實際的專案結構調整指令。


**整體環境架構說明：**
這份作業分成**開發期**與**容器化**兩個階段。

**開發期：**
* 整體開發環境包含前端、Swagger、PostgreSQL、測試，以及同學正在撰寫的 `backend/`
* 前端、Swagger 與 PostgreSQL 透過 Docker Compose 啟動
* 開發期雖然已經將 `backend/` 建立在作業專案裡，但目前的 `docker compose up -d` 還不會啟動後端
* 後端需另外進入 `backend/`，透過 `npm run dev` 啟動

```txt
node-js-final-2026/
├── backend/              # npm run dev，跑在 localhost:8080
├── frontend/             # Docker Compose，跑在 localhost:3000
├── docs/                 # Docker Compose，跑在 localhost:8081
├── test/
└── docker-compose.yml

PostgreSQL                # Docker Compose，跑在 localhost:5432
```

**容器化階段：**
* 待開發期功能做完、正式繳交作業之前，再替 `backend/` 撰寫 Dockerfile，並將 backend 服務加入 `docker-compose.yml`
* 完成後，前端、Swagger、PostgreSQL 與後端都能透過 Docker Compose 啟動

## 【主線任務】

本週有 M0（確認後端服務正常啟動）、M1～M6 主軸任務，以及壓軸的容器化任務；另外有一個選做的 upload 加分題（圖片上傳功能，不列入驗收）。建議依據 Swagger 的分組順序，從 M0、M1 開始依序開發到 M6，最後再完成容器化任務。

**任務項目：**
- **M0｜確認後端服務啟動（需先完成）**
  - 內容：實作一支 GET /healthcheck 端點，回報服務狀態
  - 驗證：沒有獨立測試，但這支 API 服務要能正常運作
  - 前端頁面：屬於確認服務，無對應畫面

- **M1｜種資料**
  - 內容：技能、方案的新增／查詢／刪除
  - 驗證：`npm run test:m1`
  - 前端頁面：健身方案列表

- **M2｜會員系統**
  - 內容：註冊、登入（簽發 JWT）、查看與修改個人資料（暱稱、密碼）
  - 驗證：`npm run test:m2`
  - 前端頁面：註冊、登入、會員個人資料

- **M3｜教練後台**
  - 內容：升級為教練、維護教練個人檔案（含技能）、課程的開設／修改／查詢
  - 驗證：`npm run test:m3`
  - 前端頁面：教練個人後台、課程管理

- **M4｜公開瀏覽**
  - 內容：教練列表、教練詳情、教練的課程，以及全站課程列表（皆免登入）
  - 驗證：`npm run test:m4`
  - 前端頁面：教練列表、教練詳情

- **M5｜購買與報名**
  - 內容：購買方案、報名／取消課程，以及查看本人的購買紀錄與課表（含剩餘堂數）
  - 驗證：`npm run test:m5`
  - 前端頁面：購買方案、報名課程、我的課表、我的訂單
  - 注意事項：
    - 四句固定錯誤訊息：報名課程失敗時，前端會用錯誤訊息文字判斷要跳哪個提示視窗，下面四句必須一字不差（連標點符號都需一致）：`已經報名過此課程`／`已無可使用堂數`／`已達最大參加人數，無法參加`／`請先登入`
    - 取消報名是「軟刪除」：取消時系統不會刪掉這筆報名紀錄，只是標註一個取消時間（紀錄留著）；也因為這樣，取消過的課不能再報名。另外剩餘堂數沒有對應欄位，它是根據「買的總堂數 − 未取消的報名數」計算出來

- **M6｜月營收統計**
  - 內容：教練查詢自己指定月份的營收統計（需跨方案與報名做聚合計算）
  - 驗證：`npm run test:m6`
  - 前端頁面：教練營收報表
  - 注意事項：
    - 時間：一筆報名算進哪個月，看的是「報名建立的時間」，不是上課時間；年份固定為當年
    - 單堂均價 = 全部方案的總價 ÷ 全部方案的總堂數
    - 月營收 = 當月未取消的報名數 × 單堂均價（先乘再無條件捨去 floor）
    - 完整公式與計算範例，可參考 Swagger 的 M6 章節

- **壓軸｜容器化**
  - 內容：替 `backend/` 撰寫 Dockerfile，並將 backend 服務加入 `docker-compose.yml`
  - 驗證：執行 `docker compose up -d --build backend postgres` 後，再執行 `npm run test:smoke`
  - 前端頁面：無

**開發時的終端機配置：**

進行 M0～M6 開發時，建議同時開啟兩個終端機：

* **終端機 A｜啟動後端**
  - 說明：進入作業專案裡的 `backend/`，執行 `npm run dev`，讓後端持續監聽 `localhost:8080`
  - 路徑：node-js-final-2026/backend/
* **終端機 B｜執行測試**：
  - 說明：停留在專案根目錄，依照目前的任務進度執行 `npm run test:m1` ～ `npm run test:m6`
  - 路徑：node-js-final-2026/

**M1～M6 主軸開發流程：**

1. 先到 Swagger（`localhost:8081`）看相關 API 的規格，了解 request／response 的欄位跟範例
2. 在專案裡的 `backend/` 撰寫程式（如果使用 nodemon，會在存檔後自動重啟）
3. 用 Swagger 的 Try it out，測試撰寫是否正確；需要登入的 API，先按右上角 Authorize 貼上登入後拿到的 token
4. 等這個任務（例如 M1）的幾支 API 都完成後，可在專案根目錄的終端機執行對應的測試（M1 就執行 `npm run test:m1`），如果沒通過就根據錯誤訊息調整
5. 若這個任務有對應的前端頁面（見上方「前端頁面」欄位），也可以重新整理 `localhost:3000`，確認相關頁面、功能是否正常運作；若是沒有對應畫面的 API（例如 M1 技能種資料），主要以上方第 3、4 點確認即可

**共通規則：**

- **回傳格式與狀態碼**：驗收只看大方向，請求成功就回 2xx（200、201 都算對），失敗就回 4xx，再搭配回傳的 `status` 欄位。格式大致是：成功 `{ "status": "success", "data": ... }`、失敗 `{ "status": "failed", "message": "..." }`。錯誤訊息除了 M5 那四句要一模一樣，其他情況的文字可以自訂，細節以 Swagger 文件為主
- **環境變數**：資料庫連線和 `JWT_SECRET` 都從環境變數讀取（key 名對照作業專案 `.env.example`），不可寫死在程式裡
- **自動建表**：後端初始連到的是一個空資料庫（有 database、但沒有任何資料表），所以啟動時就得把需要的資料表建好
- **port 與後端服務啟動**：後端的 port 固定用 8080（參照環境準備第 5 點），而且 `GET /healthcheck` 要等資料庫就緒後才回應 200（M0）

**壓軸任務：容器化**

從 M0、M1 一路到 M6 都完成後，接著要進行最後的容器化任務。
在專案的 `backend/` 裡寫好 `Dockerfile`、`.dockerignore`（至少排除 `node_modules` 與 `.env`，別讓它們被打包到 image）。再照專案根目錄 `docker-compose.yml` 裡下方預留的 **W10 容器化挑戰**，根據註解寫好的規則、提示來將 backend 服務補上。

在進到運行容器化的步驟前，要記得把原先在 `backend/` 執行的 `npm run dev` 關閉，將 8080 port 空出，避免後續容器化的 backend 無法使用 8080 port。確認後在專案根目錄的終端機執行：

```bash
docker compose up -d --build backend postgres
npm run test:smoke # 會測幾支基本 API，確認容器裡的後端與資料庫能連線跟運作
```

以上步驟完成後，之後只要 `docker compose up -d` 指令就能將整體健身房網站運行起來。

## 【測試】

在開發或容器化階段，都是在專案根目錄輸入 `npm test` 來確認各個測試項目；
如果是開發階段，則可使用 `npm run test:m1` ～ `npm run test:m6` 分任務完成的階段來測試。

測試結果判別：
- ✓ 表示測試通過
- ✕ 表示測試失敗

**作業繳交前必須通過 npm test 整體測試**。
最後有看到 `Tests: 68 passed, 68 total` 即代表全數通過。

在容器化階段的步驟完成，並 push 上 GitHub Repo，這時 Repo 的 **Actions** 會自動跑同一套驗收，測試 M1～M6、容器化部分（共 7 個 job）。在 Actions 跑完後可點進此次的 workflow run 頁面，內容會有逐條 ✅／❌ 的結果，讓我們得知項目的正確與否。若有看到錯誤，可再根據錯誤訊息來做調整。

繳交前提醒**不需更動**部分：
作業專案的 `frontend/`、`docs/`、`test/`、`.github/`、根目錄 `package.json`／`package-lock.json`。
你主要撰寫並新增的是 `backend/`，相關套件需安裝在 `backend/package.json`。

## 【常見問題】

排查問題前，可先確認目前的階段：
* **開發期**：後端位於作業專案的 `backend/`，使用 `npm run dev` 啟動。
* **容器化後**：後端位置不變，但改由 Docker Compose 啟動。

**Q：後端連不上資料庫，或 `docker compose up` 啟動失敗？**

先確認 Docker Desktop 已開啟，再於專案根目錄執行：`docker compose ps`。如果 `postgres` 沒有正常啟動，可以執行 `docker compose logs postgres` 來查看 log。

常見原因包含 Docker 尚未啟動完成、Port 被占用，或環境變數設定錯誤。

**Q：後端出現 EADDRINUSE，顯示 8080 已被占用？**

代表已有其他程式使用 `8080`，常見情況是：

* 重複執行了兩次 `npm run dev`
* Docker backend 與本機執行的 backend 同時啟動

如果是 Docker backend 占用，可先在專案根目錄執行：`docker compose stop backend`，再進入 `backend/` 啟動本機後端。
（記得不可修改 8080，因為前端與 Swagger 都固定使用這個 Port。）

**Q：執行 `npm test` 時出現連線錯誤（像是：`ECONNREFUSED`）？**

`ECONNREFUSED` 通常代表測試連不到 `localhost:8080`，也就是後端尚未啟動或啟動失敗。
可先開啟：`http://localhost:8080/healthcheck`，這支 API 用來確認後端與資料庫是否已準備完成。若無法正常回應，請先查看後端終端機或 Docker log。

* 開發期請進入 `backend/` 執行：`npm run dev`
* 容器化後請在專案根目錄執行：`docker compose up -d --build backend postgres`

確認 `/healthcheck` 可正常回應後，再執行測試。

另外，這份作業不會自動建立後端的啟動指令，請自行在 `backend/package.json` 設定好 `dev`、`start`，並指向正確的入口檔案。

**Q：前端呼叫 API 時出現 CORS 錯誤？**

前端、Swagger 與後端使用不同 Port，因此後端需要開放跨來源請求。
記得加上 CORS middleware（`app.use(cors())`）。

**Q：頁面只剩選單和頁尾，內容一片空白？**

先查看瀏覽器 Console。如果同時出現 `401`、`無效的 token` 或`未授權訊息`，可能是瀏覽器仍保留舊的登入 token。通常發生情境：
- 執行過 `db:reset`
- 修改過 `JWT_SECRET`
- 重新建立過資料庫

解法：
可到 DevTools 的 `Application` → `Cookies`，刪除 `localhost:3000` 下的 `token`，再重新整理並登入。
若沒有 token 相關錯誤，請再從 Console 或 Network 查看是哪支 API 失敗。

**Q：一定要使用 TypeORM 嗎？可以只使用 pg 嗎？**

都是可行的。這份作業不限制資料庫操作方式，驗收主要確認 API 路徑、回傳格式、狀態碼與實際行為是否符合規格。

不過資料必須能存進 PostgreSQL，且後端啟動時要能在空資料庫中建立所需資料表。

**Q：M5 報名課程一直卡在錯誤訊息？**

以下四句必須完全一致，包含文字、標點與空格：
```txt
已經報名過此課程
已無可使用堂數
已達最大參加人數，無法參加
請先登入
```
可再檢查這些文字是否完全一致。

**Q：容器重啟後資料不見了？**

驗收會重啟一次容器，確認先前建立的資料是否仍然存在。因此，資料必須確實寫入 PostgreSQL，不能只暫存在後端程式的變數或陣列中，否則後端重啟後資料就會消失。

只要每一筆資料都有正確寫入資料庫，容器重啟後資料就會保留，也能通過這項驗收。

**Q：本機測試與 GitHub Actions 結果不同？**

常見原因是本機與 CI 的資料庫或環境不同。
可以先執行：`npm run db:reset`，清空本機資料庫後重新測試。

**Q：GitHub Actions 紅燈，要怎麼找問題？**

進入失敗的 workflow，依序查看：
1. 哪一個 job 失敗。
2. 第一個出現紅色叉叉的 step。
3. 該 step 中最早出現的錯誤訊息。

常見方向：

* 找不到 `backend/`：確認後端是否建立於專案根目錄，並已 commit、push 到 GitHub。
* backend 無法啟動：檢查啟動指令、環境變數與資料庫連線。
* 測試失敗：依測試名稱回本機執行對應的 `npm run test:m{N}`。

建議先在本機重現並修正，再重新 push。

**Q：哪些檔案不能修改？**

以下內容屬於作業專案原先環境（前端、文件、驗收檔案），請勿修改：
```txt
frontend/
docs/
test/
.github/
根目錄 package.json
根目錄 package-lock.json
```

主要需要新增與修改的是：`backend/`。
後端套件也應安裝在 `backend/`，因此 `backend/package.json` 與 `backend/package-lock.json` 可以正常修改。

`docker-compose.yml` 是例外，容器化階段需要依照註解加入 backend 服務。

---

# 【延伸】Spring Boot 版後端（`livefit/`）

> 以下**不屬於作業範圍**，是在作業完成後額外做的練習。
> 驗收對象仍然是 `backend/`（Node.js），GitHub Actions 也只跑那一套。

## 這是什麼

把 `backend/` 的業務邏輯用 **Spring Boot 3.5 + Spring Data JPA + Spring Security** 重寫一次，放在 `livefit/`。

原 Node 版把 controller 與業務邏輯寫在一起，這個版本刻意拆成 MVC 三層：controller 只做路由對映與回應包裝，**所有驗證與業務邏輯都在 service**。驗證改用 Spring Security + JWT（`io.jsonwebtoken`），以便日後擴充角色權限。

行為以 `docs/openapi.yaml` 為準，**通過作業原生的 68 項合約測試**。

另外多了 Node 版沒有的東西：**Google、GitHub、Facebook 第三方登入**（`POST /api/users/google`、`/github`、`/facebook`），以及**獨立的資料庫 `livefit`**（不與 Node 版的 `fitness` 共用）。

## 怎麼跑

需要 JDK 21 與 Maven。

```bash
# 1. 啟動資料庫（只啟動 postgres；不要連 Node 版的 backend 容器一起啟動，它會佔用 8080）
docker compose up -d postgres

# 2. 建立 Spring Boot 版專用的資料庫（只需做一次；npm run db:reset 之後要重做）
#    postgres 容器只會自動建立 Node 版用的 fitness
docker compose exec postgres psql -U student -d fitness -c "CREATE DATABASE livefit"

# 3. 設定環境變數
cd livefit && cp .env.example .env    # JWT_SECRET 必填，用 openssl rand -hex 32 產生後貼上，留空會無法啟動

# 4. 啟動（必須在 livefit/ 下執行，否則讀不到 .env）
mvn spring-boot:run
```

資料表由 Hibernate 在第一次啟動時自動建立（`ddl-auto=update`），**不需要先跑 Node 版**。

port 由 `livefit/.env` 的 `PORT` 控制（預設 8080）。**與 `backend/` 相同，兩者不能同時啟動**；要並行請改成其他 port，例如 8085。

> 沒有安裝 Maven（`command not found: mvn`）時，可以 `brew install maven`，其他做法見 [`CLAUDE.md`](CLAUDE.md)。

### 用 Spring Boot 版專用的 Compose 跑（不含 Node 版後端）

`compose.livefit.yml` 只有前端、Swagger、PostgreSQL 與 `livefit`，有自己的 volume，`livefit` 資料庫會自動建立，不需要上面的步驟 1、2。先完成步驟 3 的 `livefit/.env`，再擇一：

```bash
# 開發期：只起前端、Swagger、資料庫，後端在本機跑（cd livefit && mvn spring-boot:run）
docker compose -f compose.livefit.yml --env-file livefit/.env up -d

# 整套：後端也用容器跑，不需要本機的 JDK 與 Maven
docker compose -f compose.livefit.yml --env-file livefit/.env --profile app up -d --build

# 清空資料庫重來
docker compose -f compose.livefit.yml --env-file livefit/.env --profile app down -v
```

`livefit` 服務放在 `app` profile，沒帶 `--profile app` 時不會啟動，這樣開發期改程式不必每次重建 image。

這一組與根目錄 `docker-compose.yml` 佔用相同的 port（3000、8081、5432、8080），**不能同時啟動**；切換前先在根目錄執行 `docker compose stop`。兩組的資料庫各自獨立，帳號不互通。

### 啟用 Google 登入（選用）

不設定也能正常啟動，只是登入頁不會出現 Google 按鈕。

1. 到 Google Cloud Console 建立 **OAuth 2.0 Client ID（Web application）**，Authorized JavaScript origins 加入 `http://localhost:5173` 與 `http://localhost:3000`。
2. 把 Client ID 填進兩個地方（必須是同一個值）：
   - `livefit/.env` 的 `GOOGLE_CLIENT_ID`
   - `frontend/.env` 的 `VITE_GOOGLE_CLIENT_ID`（從 `frontend/.env.example` 複製）
3. 啟動前端：`cd frontend && npm install && npm run dev`，瀏覽器開 `http://localhost:5173/login`。

請用 `localhost` 而不是 `127.0.0.1` 開頁面，Google 把兩者視為不同來源。帳號綁定規則見 [`CHANGELOG.md`](CHANGELOG.md)。

### 啟用 GitHub 登入（選用）

不設定也能正常啟動，只是登入頁不會出現 GitHub 按鈕。

1. 到 GitHub → Settings → Developer settings → OAuth Apps 建立 OAuth App：
   - Homepage URL：`http://localhost:5173`
   - Authorization callback URL：`http://localhost:5173/oauth/callback/github`
2. 產生一組 Client secret，填進設定檔：
   - `livefit/.env` 的 `GITHUB_CLIENT_ID` 與 `GITHUB_CLIENT_SECRET`
   - `frontend/.env` 的 `VITE_GITHUB_CLIENT_ID`（與上面的 Client ID 相同；**secret 不要放前端**）
3. 重新啟動後端與前端，瀏覽器開 `http://localhost:5173/login`。

上面登記的是本機開發用的 5173。容器版前端（`http://localhost:3000`）能否共用同一個 OAuth App 尚未實測；登入時若 GitHub 顯示 redirect_uri 不符，就為 3000 另建一個 App。GitHub 帳號必須有一個**主要且驗證過**的 email 才能登入。

### 啟用 Facebook 登入（選用）

不設定也能正常啟動，只是登入頁不會出現 Facebook 按鈕。

1. 到 [Meta for Developers](https://developers.facebook.com/apps/creation/) 建立應用程式，使用案例選 Facebook 登入（用來驗證用戶並索取資料的那一項）。
2. 在該使用案例的權限設定中加入 `email`（`public_profile` 預設就有）。
3. Facebook 登入的設定頁 → 「有效的 OAuth 重新導向 URI」填 `http://localhost:5173/oauth/callback/facebook`。
4. 應用程式設定 → 基本資料，取得應用程式編號與應用程式密鑰，填進設定檔：
   - `livefit/.env` 的 `FACEBOOK_APP_ID` 與 `FACEBOOK_APP_SECRET`
   - `frontend/.env` 的 `VITE_FACEBOOK_APP_ID`（與上面的編號相同；**密鑰不要放前端**）
5. 重新啟動後端與前端。

應用程式在**開發模式**下只有具應用程式角色的帳號（管理員、開發人員、測試人員）能登入，自己測試用建立 App 的那個 Facebook 帳號即可。

Facebook 不會告訴我們 email 是否驗證過，所以它**不會自動綁定既有帳號**：同一個 email 已經用密碼、Google 或 GitHub 註冊過時，Facebook 登入會回「此 Email 已註冊，請改用原本的方式登入」。Facebook 帳號沒有 email 時也無法登入。

## 拿作業的測試來驗

`test/helpers.js` 的 base URL 讀 `API_BASE_URL` 環境變數（預設 `http://localhost:8080`），所以不用改任何測試檔：

```bash
npm test                                        # Spring Boot 跑在 8080 時
API_BASE_URL=http://localhost:8085 npm test     # 跑在其他 port 時
```

## 詳細文件

| 文件 | 內容 |
|---|---|
| [`docs/springboot-migration-plan.md`](docs/springboot-migration-plan.md) | 架構設計、28 支 API 的實作重點、Entity 對映、Spring Security 規則，以及與 Node 版的行為差異表 |
| [`docs/springboot-migration-worklog.md`](docs/springboot-migration-worklog.md) | 移植階段的變更紀錄與踩坑排查過程 |
| [`CHANGELOG.md`](CHANGELOG.md) | 移植完成之後的功能更新紀錄（Google 登入、獨立資料庫） |
| [`CLAUDE.md`](CLAUDE.md) | 給 Claude Code 的 repo 開發指引 |

## 已知限制

- 有 Dockerfile 與專用的 `compose.livefit.yml`，但**不參與作業的容器化驗收**（驗收只看根目錄 `docker-compose.yml` 的 `backend` 服務）
- 單元 / 整合測試只涵蓋第三方登入與 JWT（`cd livefit && mvn test`），其餘依靠上述的合約測試
- `POST /api/upload` 是 openapi 標明的選做加分題，兩套後端都未實作
- 用根目錄 `docker-compose.yml` 的 postgres 時，`livefit` 資料庫要手動建立一次（見上方步驟 2），之後的資料表才會自動建立；用 `compose.livefit.yml` 則不必
- 兩個資料庫的資料不互通，Node 版與 Spring Boot 版的帳號要各自註冊
- Google 登入只有 Spring Boot 版有；為此改過 `frontend/` 與 `docs/openapi.yaml`，**這些改動不要 merge 回 `main`**（作業規定這兩個目錄不可修改）
