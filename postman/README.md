# LiveFit 的 Postman Collection

對 `livefit/`（Spring Boot 版）的端到端合約測試：168 支請求、481 個斷言，涵蓋 M0 ~ M7 與第三方登入的失敗案例。Node 版沒有 M7 與第三方登入，不適用。

| 檔案 | 用途 |
|---|---|
| `livefit.postman_collection.json` | 請求與斷言，只匯入這個就能跑。由腳本產生，不進版本庫 |
| `livefit.postman_environment.json` | 選用，要換受測位址或管理員帳密時才需要。由腳本產生，不進版本庫 |
| `build-collection.js` | 產生上面兩個檔案的腳本 |

## 執行

兩個 JSON 列在 `.gitignore`，剛 clone 下來時不存在，先在根目錄產生：

```bash
npm run postman:build
```

再啟動 postgres 與 livefit（見根目錄 `CLAUDE.md`），確認 `GET http://localhost:8080/healthcheck` 回 `OK`。

**Postman**：Import `livefit.postman_collection.json` → 在 collection 上按 Run → Run。不需要手動登入或填 token。

**命令列**：

```bash
npm run postman:test
```

## 變數

預設值寫在 collection 變數裡，所以不匯入 environment 也能跑。

| 變數 | 預設值 | 說明 |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | livefit 的位址 |
| `adminEmail` | `admin@gmail.com` | 預設管理員，對應 `livefit/.env` 的 `ADMIN_EMAIL` |
| `adminPassword` | `Admin123` | 對應 `ADMIN_PASSWORD`；改過預設管理員的密碼就要跟著改 |

其他變數（`runId`、各種 token 與 id）由執行過程自動寫入 collection 變數。

要改這三個值時，匯入 `livefit.postman_environment.json`、在右上角選用它，再改裡面的值。environment 的值會蓋過 collection 變數，collection 本身不用動。命令列的寫法：

```bash
npm run postman:test -- -e postman/livefit.postman_environment.json
npm run postman:test -- --env-var baseUrl=http://localhost:8085
```

## 兩層斷言

- **流程斷言**：寫在每支請求裡，照 livefit 的實際行為檢查狀態碼、錯誤訊息與資料內容。
- **openapi 驗證**：名稱以 `[openapi]` 開頭，寫在 collection 層級，每支請求跑完都會拿回應比對 `docs/openapi.yaml` 記載的該狀態碼的回應結構。openapi 沒有記載該狀態碼時會失敗。

openapi 驗證的規則有三點是這份腳本自己訂的，不是 openapi 寫的：

- 文件的回應 schema 都沒有 `required`，照字面驗的話空物件也會過，所以**文件列出的欄位一律要出現**。
- 約三分之一的回應（M1 的失敗回應與刪除、M3 的升級教練與教練資料、M7 全部）只有範例、沒有 schema，這些改從範例推出結構：欄位要在、型別要相同，純量欄位允許 null。
- 回 200 而文件寫 201（或相反）時，拿文件寫的那個 2xx 來比。

## 注意事項

- **要整份依序執行**。請求之間靠變數串接，只跑中間某個資料夾會拿到上一輪的舊值。
- **可以重複跑**，每一輪的名稱與 Email 都帶新的 `runId`。
- **資料會留在資料庫**。每跑一輪會多 6 個帳號（其中 1 個是管理員）、1 個技能、1 個方案、3 堂課；使用者與課程沒有刪除 API，要清空就重建資料庫。
- 這一輪建立的管理員在重設密碼後是固定的 `Admin123`，collection 最後會把它換成隨機密碼，不會留下密碼已知的管理員帳號。
- **第三方登入只有失敗案例**。成功路徑需要真的 Google credential 或 OAuth code，Runner 裡造不出來，請用前端手動測。
- 成功的回應只檢查 2xx（200 與 201 可互換），失敗的回應檢查確切的狀態碼與訊息。

## 修改

兩個 JSON 都是由 `build-collection.js` 產生的，請改腳本再重新產生，不要直接改 JSON：

```bash
npm run postman:build
```

改了 `docs/openapi.yaml` 之後也要重新產生，openapi 驗證才會跟著更新。腳本讀 YAML 用的 `js-yaml` 是 jest 帶進來的間接相依，沒有寫進 `package.json`。

後端新增端點或改了錯誤訊息時，在腳本裡對應的資料夾加請求即可；錯誤訊息集中在開頭的 `MSG`，與 `livefit` 的 `ErrorMessages.java` 對應。
