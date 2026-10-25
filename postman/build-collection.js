/**
 * 產生 livefit（Spring Boot 版）的 Postman Collection。
 *
 *   node postman/build-collection.js
 *
 * 產出兩個檔案：
 *   - livefit.postman_collection.json   請求、流程斷言與 openapi 驗證
 *   - livefit.postman_environment.json  選用，用來覆蓋 baseUrl 與管理員帳密
 * 請改這支腳本再重新產生，不要直接改 JSON。
 *
 * 斷言有兩層：
 *   1. 流程斷言：每支請求自己的 test script，照 livefit 的實際行為寫（狀態碼、訊息、資料內容）。
 *   2. openapi 驗證：collection 層級的 test script，每支請求跑完都會拿回應去比對
 *      docs/openapi.yaml 記載的該狀態碼的回應結構。這一層是從 openapi 讀出來的，改了規格重新產生就會跟著變。
 *
 * 整份 collection 是「一條從頭跑到尾的情境」：前面的請求把 token、id 存進 collection 變數，
 * 後面的請求拿來用，所以要整份依序執行，不能只跑中間某個資料夾。
 * 流程與斷言對照 test/m1 ~ m6.test.js 與 livefit 的 AdminUserApiTest（M7）。
 */
const fs = require('fs');
const path = require('path');

const OUTPUT = path.join(__dirname, 'livefit.postman_collection.json');
const ENV_OUTPUT = path.join(__dirname, 'livefit.postman_environment.json');
const OPENAPI_PATH = path.join(__dirname, '..', 'docs', 'openapi.yaml');

/** baseUrl 與管理員帳密的預設值：collection 變數與 environment 檔共用 */
const SETTINGS = [
  { key: 'baseUrl', value: 'http://localhost:8080' },
  { key: 'adminEmail', value: 'admin@gmail.com' },
  { key: 'adminPassword', value: 'Admin123' },
];

const FAKE_UUID = '00000000-0000-4000-8000-000000000000';
const PASSWORD = 'Aa12345678';
const NEW_PASSWORD = 'Bb87654321';
/** AdminUserService.RESET_PASSWORD：重設管理員密碼後的固定密碼 */
const RESET_PASSWORD = 'Admin123';

const MSG = {
  notLogin: '請先登入',
  alreadyBooked: '已經報名過此課程',
  noCredit: '已無可使用堂數',
  full: '已達最大參加人數，無法參加',
  invalidFields: '欄位未填寫正確',
  invalidId: 'ID錯誤',
  duplicated: '資料重複',
  tokenInvalid: '無效的 token',
  notCoach: '使用者尚未成為教練',
  notAdmin: '需要管理員權限',
  routeNotFound: '無此路由',
  passwordRule: '密碼不符合規則，需要包含英文數字大小寫，最短8個字，最長16個字',
  emailTaken: 'Email 已被使用',
  loginFailed: '使用者不存在或密碼輸入錯誤',
  nameNotChanged: '使用者名稱未變更',
  passwordSameAsOld: '新密碼不能與舊密碼相同',
  passwordConfirmMismatch: '新密碼與驗證新密碼不一致',
  passwordWrong: '密碼輸入錯誤',
  googleVerifyFailed: 'Google 登入驗證失敗',
  googleNotConfigured: '尚未設定 Google 登入',
  userNotFound: '使用者不存在',
  alreadyCoach: '使用者已經是教練',
  onlyUserCanBePromoted: '只能升級一般使用者',
  onlyAdminCanBeReset: '只能重設管理員的密碼',
  cannotResetSelf: '不能重設自己的密碼，請改用修改密碼',
  coachNotFound: '找不到該教練',
  courseNotFound: '課程不存在',
};

// ===== 斷言片段（產生 test script 的原始碼字串） =====

/** 每支 test script 的開頭：json 解析失敗時給空物件，讓後面的斷言是「失敗」而不是「腳本錯誤」 */
const PREAMBLE = [
  'let json = {};',
  'try { json = pm.response.json(); } catch (e) { /* 不是 JSON */ }',
  'const V = (key) => pm.collectionVariables.get(key);',
];

const q = (value) => JSON.stringify(value);

/** 成功合約：2xx + status 為 success（200 與 201 可互換） */
const ok = () => [
  'pm.test("2xx 且 status 為 success", () => {',
  '  pm.expect(pm.response.code).to.be.within(200, 299);',
  '  pm.expect(json.status).to.eql("success");',
  '});',
];

/** 失敗合約：指定狀態碼 + status 為 failed，有給 message 就逐字比對 */
const fail = (code, message) => {
  const lines = [
    `pm.test("回 ${code} 且 status 為 failed", () => {`,
    `  pm.response.to.have.status(${code});`,
    '  pm.expect(json.status).to.eql("failed");',
    '});',
  ];
  if (message) {
    lines.push(
      `pm.test(${q(`message 逐字等於「${message}」`)}, () => {`,
      `  pm.expect(json.message).to.eql(${q(message)});`,
      '});',
    );
  }
  return lines;
};

/** 自訂斷言 */
const t = (name, ...body) => [`pm.test(${q(name)}, () => {`, ...body.map((l) => `  ${l}`), '});'];

/** 把回應裡的值存成 collection 變數，給後面的請求用 */
const save = (key, expr) => [`if (${expr} !== undefined) pm.collectionVariables.set(${q(key)}, ${expr});`];

/** 解 JWT payload（前端也是自己 decode，payload 必含 id / role / exp） */
const DECODE_JWT = [
  'const decodeJwt = (token) => {',
  '  const part = String(token).split(".")[1] || "";',
  '  const b64 = part.replace(/-/g, "+").replace(/_/g, "/");',
  '  return JSON.parse(atob(b64 + "=".repeat((4 - (b64.length % 4)) % 4)));',
  '};',
];

/** 登入成功：存 token，並檢查 JWT payload 的 role */
const loginOk = (tokenVar, role, idVar) => [
  ...ok(),
  ...DECODE_JWT,
  ...t(
    `回傳 token，JWT payload 含 id / role=${role} / exp`,
    'pm.expect(json.data.token).to.be.a("string").and.not.empty;',
    'const payload = decodeJwt(json.data.token);',
    'pm.expect(payload.id).to.be.a("string").and.not.empty;',
    `pm.expect(payload.role).to.eql(${q(role)});`,
    'pm.expect(payload.exp).to.be.a("number");',
    `pm.collectionVariables.set(${q(tokenVar)}, json.data.token);`,
    ...(idVar ? [`pm.collectionVariables.set(${q(idVar)}, payload.id);`] : []),
  ),
];

// ===== 請求與資料夾 =====

/**
 * @param {string} name   請求名稱（Runner 的結果列表會顯示）
 * @param {string} method HTTP method
 * @param {string} url    路徑，自動加上 {{baseUrl}}
 * @param {object} opts   auth：存 token 的變數名稱；body：request body；pre / tests：腳本原始碼
 */
function request(name, method, url, opts = {}) {
  const { auth, body, pre, tests = [] } = opts;
  const item = {
    name,
    request: {
      method,
      header: [],
      url: `{{baseUrl}}${url}`,
      auth: auth
        ? { type: 'bearer', bearer: [{ key: 'token', value: `{{${auth}}}`, type: 'string' }] }
        : { type: 'noauth' },
    },
    event: [
      { listen: 'test', script: { type: 'text/javascript', exec: [...PREAMBLE, ...tests] } },
    ],
  };
  if (body !== undefined) {
    item.request.header.push({ key: 'Content-Type', value: 'application/json' });
    item.request.body = {
      mode: 'raw',
      raw: typeof body === 'string' ? body : JSON.stringify(body, null, 2),
      options: { raw: { language: 'json' } },
    };
  }
  if (pre) {
    item.event.unshift({ listen: 'prerequest', script: { type: 'text/javascript', exec: pre } });
  }
  return item;
}

const folder = (name, description, item) => ({ name, description, item });

const get = (name, url, opts) => request(name, 'GET', url, opts);
const post = (name, url, opts) => request(name, 'POST', url, opts);
const put = (name, url, opts) => request(name, 'PUT', url, opts);
const del = (name, url, opts) => request(name, 'DELETE', url, opts);

const signupBody = (prefix, label) => ({
  name: `${label}_{{runId}}`,
  email: `${prefix}_{{runId}}@example.com`,
  password: PASSWORD,
});
const loginBody = (prefix, password = PASSWORD) => ({
  email: `${prefix}_{{runId}}@example.com`,
  password,
});

const courseBody = (overrides = {}) => ({
  skill_id: '{{skillId}}',
  name: '課程_{{runId}}',
  description: '一堂用 Postman 生出來的課',
  start_at: '{{futureStart}}',
  end_at: '{{futureEnd}}',
  max_participants: 10,
  meeting_url: 'https://example.com/meeting',
  ...overrides,
});

/** 第一支請求的 pre-request：產生這一輪的亂數與時間，跑幾次都不會撞 unique 限制 */
const INIT = [
  'const runId = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);',
  'pm.collectionVariables.set("runId", runId);',
  '',
  '// UTC ISO 8601（不含毫秒），days 天後 + hours 小時',
  'const iso = (days, hours = 0) =>',
  '  new Date(Date.now() + days * 86400000 + hours * 3600000).toISOString().replace(/\\.\\d{3}Z$/, "Z");',
  'pm.collectionVariables.set("futureStart", iso(7));',
  'pm.collectionVariables.set("futureEnd", iso(7, 2));',
  'pm.collectionVariables.set("laterStart", iso(14));',
  'pm.collectionVariables.set("laterEnd", iso(14, 2));',
  'pm.collectionVariables.set("ongoingStart", iso(-1));',
  'pm.collectionVariables.set("ongoingEnd", iso(1));',
  '',
  '// 月營收的 month 參數收英文小寫月份名；後端以 UTC 的當年當月計算',
  'const months = ["january", "february", "march", "april", "may", "june",',
  '  "july", "august", "september", "october", "november", "december"];',
  'pm.collectionVariables.set("currentMonth", months[new Date().getUTCMonth()]);',
  '',
  '// M7 收尾時用來換掉固定密碼 Admin123 的隨機密碼（符合大小寫英數 8-16 字）',
  'pm.collectionVariables.set("randomPassword", "Pm" + String(Math.random()).slice(2, 10) + "x");',
];

// ---------- M0 / M1 ----------

const m1 = folder('M0-M1 健康檢查、技能與方案', '全部免登入。建立後面會用到的技能與方案。', [
  get('健康檢查', '/healthcheck', {
    pre: INIT,
    tests: [
      ...t(
        '回 200 純文字 OK（唯一不包 status / data 外殼的端點）',
        'pm.response.to.have.status(200);',
        'pm.expect(pm.response.text()).to.eql("OK");',
      ),
    ],
  }),
  post('新增技能', '/api/coaches/skill', {
    body: { name: '技能_{{runId}}' },
    tests: [
      ...ok(),
      ...t(
        '回傳 id 與 name',
        'pm.expect(json.data.id).to.be.a("string").and.not.empty;',
        'pm.expect(json.data.name).to.eql("技能_" + V("runId"));',
      ),
      ...save('skillId', 'json.data && json.data.id'),
    ],
  }),
  get('技能列表查得到剛新增的技能', '/api/coaches/skill', {
    tests: [
      ...ok(),
      ...t(
        'data 是陣列，且包含剛新增的技能',
        'pm.expect(json.data).to.be.an("array");',
        'const found = json.data.find((item) => item.id === V("skillId"));',
        'pm.expect(found, "列表內找不到 skillId").to.exist;',
        'pm.expect(found.name).to.eql("技能_" + V("runId"));',
      ),
    ],
  }),
  post('新增技能：名稱重複', '/api/coaches/skill', {
    body: { name: '技能_{{runId}}' },
    tests: fail(409, MSG.duplicated),
  }),
  post('新增技能：沒帶 name', '/api/coaches/skill', {
    body: {},
    tests: fail(400, MSG.invalidFields),
  }),
  // 字串欄位送數字或布林要擋下來，不能被 Jackson 轉成字串收下（JacksonConfig）
  post('新增技能：name 是數字', '/api/coaches/skill', {
    body: { name: 12345 },
    tests: fail(400, MSG.invalidFields),
  }),
  post('新增暫時的技能（等一下刪掉）', '/api/coaches/skill', {
    body: { name: '暫時技能_{{runId}}' },
    tests: [...ok(), ...save('tempSkillId', 'json.data && json.data.id')],
  }),
  del('刪除技能', '/api/coaches/skill/{{tempSkillId}}', { tests: ok() }),
  get('技能列表不再出現已刪除的技能', '/api/coaches/skill', {
    tests: [
      ...ok(),
      ...t(
        '列表內沒有被刪掉的技能',
        'pm.expect(json.data.find((item) => item.id === V("tempSkillId"))).to.be.undefined;',
      ),
    ],
  }),
  del('刪除技能：id 不存在', `/api/coaches/skill/${FAKE_UUID}`, {
    tests: fail(400, MSG.invalidId),
  }),
  del('刪除技能：id 不是 uuid', '/api/coaches/skill/not-a-uuid', {
    tests: fail(400, MSG.invalidFields),
  }),
  post('新增方案', '/api/credit-package', {
    body: { name: '方案_{{runId}}', credit_amount: 7, price: 1400 },
    tests: [
      ...ok(),
      ...t('回傳 id', 'pm.expect(json.data.id).to.be.a("string").and.not.empty;'),
      ...save('packageId', 'json.data && json.data.id'),
    ],
  }),
  get('方案列表查得到剛新增的方案', '/api/credit-package', {
    tests: [
      ...ok(),
      ...t(
        '欄位形狀正確（id / name / credit_amount / price）',
        'pm.expect(json.data).to.be.an("array");',
        'const found = json.data.find((item) => item.id === V("packageId"));',
        'pm.expect(found, "列表內找不到 packageId").to.exist;',
        'pm.expect(found.name).to.eql("方案_" + V("runId"));',
        'pm.expect(Number(found.credit_amount)).to.eql(7);',
        'pm.expect(Number(found.price)).to.eql(1400);',
      ),
    ],
  }),
  post('新增方案：名稱重複', '/api/credit-package', {
    body: { name: '方案_{{runId}}', credit_amount: 5, price: 1000 },
    tests: fail(409, MSG.duplicated),
  }),
  post('新增方案：缺 price', '/api/credit-package', {
    body: { name: '缺價格_{{runId}}', credit_amount: 7 },
    tests: fail(400, MSG.invalidFields),
  }),
  post('新增方案：缺 name', '/api/credit-package', {
    body: { credit_amount: 7, price: 1400 },
    tests: fail(400, MSG.invalidFields),
  }),
  // 整數欄位送字串或小數要擋下來，不能被 Jackson 轉成整數收下（application.properties 的兩個 Jackson 設定）
  post('新增方案：price 是字串', '/api/credit-package', {
    body: { name: '字串價格_{{runId}}', credit_amount: 7, price: '1400' },
    tests: fail(400, MSG.invalidFields),
  }),
  post('新增方案：credit_amount 是小數', '/api/credit-package', {
    body: { name: '小數堂數_{{runId}}', credit_amount: 7.5, price: 1400 },
    tests: fail(400, MSG.invalidFields),
  }),
  post('新增方案：name 是布林', '/api/credit-package', {
    body: { name: true, credit_amount: 7, price: 1400 },
    tests: fail(400, MSG.invalidFields),
  }),
  post('新增暫時的方案（等一下刪掉）', '/api/credit-package', {
    body: { name: '暫時方案_{{runId}}', credit_amount: 1, price: 100 },
    tests: [...ok(), ...save('tempPackageId', 'json.data && json.data.id')],
  }),
  del('刪除方案', '/api/credit-package/{{tempPackageId}}', { tests: ok() }),
  get('方案列表不再出現已刪除的方案', '/api/credit-package', {
    tests: [
      ...ok(),
      ...t(
        '列表內沒有被刪掉的方案',
        'pm.expect(json.data.find((item) => item.id === V("tempPackageId"))).to.be.undefined;',
      ),
    ],
  }),
  del('刪除方案：id 不存在', `/api/credit-package/${FAKE_UUID}`, {
    tests: fail(400, MSG.invalidId),
  }),
]);

// ---------- M2 ----------

const m2 = folder('M2 會員系統', '註冊、登入、個人資料、修改密碼。會員 A 的 token 存在 memberToken。', [
  post('註冊會員 A', '/api/users/signup', {
    body: signupBody('pm_member', '會員'),
    tests: [
      ...ok(),
      ...t(
        '回傳 user.id 與 user.name，沒有 password',
        'pm.expect(json.data.user.id).to.be.a("string").and.not.empty;',
        'pm.expect(json.data.user.name).to.eql("會員_" + V("runId"));',
        'pm.expect(json.data.user).to.not.have.property("password");',
      ),
      ...save('memberId', 'json.data && json.data.user && json.data.user.id'),
    ],
  }),
  post('註冊：Email 重複', '/api/users/signup', {
    body: signupBody('pm_member', '會員'),
    tests: fail(409, MSG.emailTaken),
  }),
  post('註冊：密碼不符合規則', '/api/users/signup', {
    body: { name: '弱密碼_{{runId}}', email: 'pm_weak_{{runId}}@example.com', password: '123' },
    tests: fail(400, MSG.passwordRule),
  }),
  post('註冊：缺 email', '/api/users/signup', {
    body: { name: '缺信箱_{{runId}}', password: PASSWORD },
    tests: fail(400, MSG.invalidFields),
  }),
  post('登入會員 A', '/api/users/login', {
    body: loginBody('pm_member'),
    tests: [
      ...loginOk('memberToken', 'USER'),
      ...t(
        'JWT 的 id 就是註冊時拿到的 id，user.name 是本人',
        'pm.expect(decodeJwt(json.data.token).id).to.eql(V("memberId"));',
        'pm.expect(json.data.user.name).to.eql("會員_" + V("runId"));',
      ),
    ],
  }),
  post('登入：密碼錯誤', '/api/users/login', {
    body: loginBody('pm_member', 'Aa87654321'),
    tests: fail(400, MSG.loginFailed),
  }),
  post('登入：Email 不存在', '/api/users/login', {
    body: loginBody('pm_nobody'),
    tests: fail(400, MSG.loginFailed),
  }),
  post('登入：缺密碼', '/api/users/login', {
    body: { email: 'pm_member_{{runId}}@example.com' },
    tests: fail(400, MSG.invalidFields),
  }),
  get('取得個人資料', '/api/users/profile', {
    auth: 'memberToken',
    tests: [
      ...ok(),
      ...t(
        '回傳本人的 email、name，has_password 為 true',
        'pm.expect(json.data.user.email).to.eql("pm_member_" + V("runId") + "@example.com");',
        'pm.expect(json.data.user.name).to.eql("會員_" + V("runId"));',
        'pm.expect(json.data.user.has_password).to.eql(true);',
        'pm.expect(json.data.user).to.not.have.property("password");',
      ),
    ],
  }),
  get('取得個人資料：沒帶 token', '/api/users/profile', {
    tests: fail(401, MSG.notLogin),
  }),
  get('取得個人資料：token 是亂寫的', '/api/users/profile', {
    pre: ['pm.request.headers.upsert({ key: "Authorization", value: "Bearer not.a.token" });'],
    tests: fail(401, MSG.tokenInvalid),
  }),
  put('更新暱稱', '/api/users/profile', {
    auth: 'memberToken',
    body: { name: '新名_{{runId}}' },
    tests: [
      ...ok(),
      ...t('回傳新的名稱', 'pm.expect(json.data.user.name).to.eql("新名_" + V("runId"));'),
    ],
  }),
  get('改名後再查個人資料', '/api/users/profile', {
    auth: 'memberToken',
    tests: [
      ...ok(),
      ...t('看得到新名稱', 'pm.expect(json.data.user.name).to.eql("新名_" + V("runId"));'),
    ],
  }),
  put('更新暱稱：名稱沒有變', '/api/users/profile', {
    auth: 'memberToken',
    body: { name: '新名_{{runId}}' },
    tests: fail(400, MSG.nameNotChanged),
  }),
  put('更新暱稱：缺 name', '/api/users/profile', {
    auth: 'memberToken',
    body: {},
    tests: fail(400, MSG.invalidFields),
  }),
  put('更新暱稱：name 是數字', '/api/users/profile', {
    auth: 'memberToken',
    body: { name: 2026 },
    tests: fail(400, MSG.invalidFields),
  }),
  put('修改密碼：新密碼不符合規則', '/api/users/password', {
    auth: 'memberToken',
    body: { password: PASSWORD, new_password: '123', confirm_new_password: '123' },
    tests: fail(400, MSG.passwordRule),
  }),
  put('修改密碼：新密碼與舊密碼相同', '/api/users/password', {
    auth: 'memberToken',
    body: { password: PASSWORD, new_password: PASSWORD, confirm_new_password: PASSWORD },
    tests: fail(400, MSG.passwordSameAsOld),
  }),
  put('修改密碼：兩次新密碼不一致', '/api/users/password', {
    auth: 'memberToken',
    body: { password: PASSWORD, new_password: NEW_PASSWORD, confirm_new_password: 'Cc11112222' },
    tests: fail(400, MSG.passwordConfirmMismatch),
  }),
  put('修改密碼：舊密碼錯誤', '/api/users/password', {
    auth: 'memberToken',
    body: { password: 'Zz99998888', new_password: NEW_PASSWORD, confirm_new_password: NEW_PASSWORD },
    tests: fail(400, MSG.passwordWrong),
  }),
  put('修改密碼：沒帶 token', '/api/users/password', {
    body: { password: PASSWORD, new_password: NEW_PASSWORD, confirm_new_password: NEW_PASSWORD },
    tests: fail(401, MSG.notLogin),
  }),
  put('修改密碼', '/api/users/password', {
    auth: 'memberToken',
    body: { password: PASSWORD, new_password: NEW_PASSWORD, confirm_new_password: NEW_PASSWORD },
    tests: ok(),
  }),
  post('改完密碼：舊密碼登不進去', '/api/users/login', {
    body: loginBody('pm_member'),
    tests: fail(400, MSG.loginFailed),
  }),
  post('改完密碼：新密碼登得進去', '/api/users/login', {
    body: loginBody('pm_member', NEW_PASSWORD),
    tests: loginOk('memberToken', 'USER'),
  }),
]);

// ---------- 第三方登入 ----------

const social = folder(
  '第三方登入（僅失敗案例）',
  '成功路徑需要真的 Google credential 或 OAuth code，Runner 裡造不出來，所以只驗後端在連到平台之前就會擋下的情況。',
  [
    post('Google：缺 credential', '/api/users/google', {
      body: {},
      tests: fail(400, MSG.invalidFields),
    }),
    post('Google：credential 不是合法的 ID token', '/api/users/google', {
      body: { credential: 'not-a-jwt' },
      tests: [
        ...fail(400),
        ...t(
          'message 是驗證失敗，或後端沒設定 GOOGLE_CLIENT_ID',
          `pm.expect(json.message).to.be.oneOf(${q([MSG.googleVerifyFailed, MSG.googleNotConfigured])});`,
        ),
      ],
    }),
    post('GitHub：缺 code', '/api/users/github', {
      body: { redirect_uri: 'http://localhost:5173/oauth/callback/github' },
      tests: fail(400, MSG.invalidFields),
    }),
    post('GitHub：redirect_uri 不在白名單', '/api/users/github', {
      body: { code: 'anything', redirect_uri: 'https://evil.example.com/callback' },
      tests: fail(400, MSG.invalidFields),
    }),
    post('Facebook：缺 code', '/api/users/facebook', {
      body: { redirect_uri: 'http://localhost:5173/oauth/callback/facebook' },
      tests: fail(400, MSG.invalidFields),
    }),
    post('Facebook：redirect_uri 不在白名單', '/api/users/facebook', {
      body: { code: 'anything', redirect_uri: 'https://evil.example.com/callback' },
      tests: fail(400, MSG.invalidFields),
    }),
  ],
);

// ---------- M3 ----------

const m3 = folder(
  'M3 升級教練與教練後台',
  '教練 A（coachToken）是課主，教練 B（strangerToken）用來驗「只能查改自己的課」。',
  [
    post('註冊教練 A 的帳號', '/api/users/signup', {
      body: signupBody('pm_coach', '教練'),
      tests: [...ok(), ...save('coachUserId', 'json.data && json.data.user && json.data.user.id')],
    }),
    post('登入教練 A（升級前，還是 USER）', '/api/users/login', {
      body: loginBody('pm_coach'),
      tests: loginOk('coachToken', 'USER'),
    }),
    get('教練資料：一般會員不能看', '/api/admin/coaches', {
      auth: 'coachToken',
      tests: fail(401, MSG.notCoach),
    }),
    get('教練資料：沒帶 token', '/api/admin/coaches', {
      tests: fail(401, MSG.notLogin),
    }),
    post('升級教練：年資為 0（M3 這支不接受）', '/api/admin/coaches/{{coachUserId}}', {
      body: { experience_years: 0, description: '年資 0' },
      tests: fail(400, MSG.invalidFields),
    }),
    // 要排在「升級教練 A」之前：被當成 3 收下的話，教練 A 會在這裡就被升級
    post('升級教練：年資是字串', '/api/admin/coaches/{{coachUserId}}', {
      body: { experience_years: '3', description: '字串年資' },
      tests: fail(400, MSG.invalidFields),
    }),
    post('升級教練：userId 不存在', `/api/admin/coaches/${FAKE_UUID}`, {
      body: { experience_years: 3, description: '查無此人' },
      tests: fail(400, MSG.userNotFound),
    }),
    post('升級教練 A', '/api/admin/coaches/{{coachUserId}}', {
      body: { experience_years: 3, description: '一位用 Postman 生出來的教練' },
      tests: [
        ...ok(),
        ...t(
          'user.role 變成 COACH，coach.user_id 指回使用者，沒有回傳 password',
          'pm.expect(json.data.user.role).to.eql("COACH");',
          'pm.expect(json.data.user).to.not.have.property("password");',
          'pm.expect(json.data.coach.id).to.be.a("string").and.not.empty;',
          'pm.expect(json.data.coach.user_id).to.eql(V("coachUserId"));',
          'pm.expect(Number(json.data.coach.experience_years)).to.eql(3);',
        ),
        ...save('coachId', 'json.data && json.data.coach && json.data.coach.id'),
      ],
    }),
    post('升級教練：同一個人再升級一次', '/api/admin/coaches/{{coachUserId}}', {
      body: { experience_years: 3, description: '重複升級' },
      tests: fail(409, MSG.alreadyCoach),
    }),
    post('重新登入教練 A（拿到 role=COACH 的 token）', '/api/users/login', {
      body: loginBody('pm_coach'),
      tests: loginOk('coachToken', 'COACH'),
    }),
    get('教練資料', '/api/admin/coaches', {
      auth: 'coachToken',
      tests: [
        ...ok(),
        ...t(
          '回傳 experience_years / description / skill_ids',
          'pm.expect(json.data.id).to.eql(V("coachId"));',
          'pm.expect(Number(json.data.experience_years)).to.eql(3);',
          'pm.expect(json.data.description).to.be.a("string");',
          'pm.expect(json.data.skill_ids).to.be.an("array").that.is.empty;',
        ),
      ],
    }),
    put('更新教練資料', '/api/admin/coaches', {
      auth: 'coachToken',
      body: {
        experience_years: 5,
        description: '更新後的教練自我介紹',
        profile_image_url: 'https://example.com/avatar.png',
        skill_ids: ['{{skillId}}'],
      },
      tests: [
        ...ok(),
        ...t(
          'skill_ids 包含指定的技能，年資更新為 5',
          'pm.expect(json.data.skill_ids).to.include(V("skillId"));',
          'pm.expect(Number(json.data.experience_years)).to.eql(5);',
          'pm.expect(json.data.profile_image_url).to.eql("https://example.com/avatar.png");',
        ),
      ],
    }),
    put('更新教練資料：圖片網址不是 https', '/api/admin/coaches', {
      auth: 'coachToken',
      body: {
        experience_years: 5,
        description: '其他欄位都合法，只有圖片網址不是 https',
        profile_image_url: 'http://example.com/avatar.png',
        skill_ids: ['{{skillId}}'],
      },
      tests: fail(400, MSG.invalidFields),
    }),
    put('更新教練資料：skill_ids 是空陣列', '/api/admin/coaches', {
      auth: 'coachToken',
      body: {
        experience_years: 5,
        description: '沒有技能',
        profile_image_url: 'https://example.com/avatar.png',
        skill_ids: [],
      },
      tests: fail(400, MSG.invalidFields),
    }),
    put('更新教練資料：年資是小數', '/api/admin/coaches', {
      auth: 'coachToken',
      body: {
        experience_years: 2.5,
        description: '其他欄位都合法，只有年資是小數',
        profile_image_url: 'https://example.com/avatar.png',
        skill_ids: ['{{skillId}}'],
      },
      tests: fail(400, MSG.invalidFields),
    }),
    post('開課', '/api/admin/coaches/courses', {
      auth: 'coachToken',
      body: courseBody(),
      tests: [
        ...ok(),
        ...t(
          '回傳 course.id，user_id 是教練的使用者 id（不是 coach id）',
          'pm.expect(json.data.course.id).to.be.a("string").and.not.empty;',
          'pm.expect(json.data.course.user_id).to.eql(V("coachUserId"));',
          'pm.expect(json.data.course.skill_id).to.eql(V("skillId"));',
        ),
        ...save('courseId', 'json.data && json.data.course && json.data.course.id'),
      ],
    }),
    post('開課：缺課程名稱', '/api/admin/coaches/courses', {
      auth: 'coachToken',
      body: (() => {
        const { name, ...rest } = courseBody();
        return rest;
      })(),
      tests: fail(400, MSG.invalidFields),
    }),
    post('開課：meeting_url 不是 https', '/api/admin/coaches/courses', {
      auth: 'coachToken',
      body: courseBody({ name: '不安全連結_{{runId}}', meeting_url: 'http://example.com/meeting' }),
      tests: fail(400, MSG.invalidFields),
    }),
    post('開課：max_participants 是字串', '/api/admin/coaches/courses', {
      auth: 'coachToken',
      body: courseBody({ name: '字串名額_{{runId}}', max_participants: '10' }),
      tests: fail(400, MSG.invalidFields),
    }),
    post('開課：max_participants 是小數', '/api/admin/coaches/courses', {
      auth: 'coachToken',
      body: courseBody({ name: '小數名額_{{runId}}', max_participants: 10.5 }),
      tests: fail(400, MSG.invalidFields),
    }),
    post('開課：description 是小數', '/api/admin/coaches/courses', {
      auth: 'coachToken',
      body: courseBody({ name: '小數簡介_{{runId}}', description: 3.14 }),
      tests: fail(400, MSG.invalidFields),
    }),
    post('開課：一般會員不能開課', '/api/admin/coaches/courses', {
      auth: 'memberToken',
      body: courseBody({ name: '會員開課_{{runId}}' }),
      tests: fail(401, MSG.notCoach),
    }),
    post('開課：沒帶 token', '/api/admin/coaches/courses', {
      body: courseBody({ name: '訪客開課_{{runId}}' }),
      tests: fail(401, MSG.notLogin),
    }),
    get('查單一課程', '/api/admin/coaches/courses/{{courseId}}', {
      auth: 'coachToken',
      tests: [
        ...ok(),
        ...t(
          '回扁平物件，含 name / skill_id / skill_name / meeting_url',
          'pm.expect(json.data.id).to.eql(V("courseId"));',
          'pm.expect(json.data.name).to.eql("課程_" + V("runId"));',
          'pm.expect(json.data.skill_id).to.eql(V("skillId"));',
          'pm.expect(json.data.skill_name).to.eql("技能_" + V("runId"));',
          'pm.expect(json.data.meeting_url).to.eql("https://example.com/meeting");',
        ),
      ],
    }),
    put('更新課程', '/api/admin/coaches/courses/{{courseId}}', {
      auth: 'coachToken',
      body: courseBody({
        name: '改版課程_{{runId}}',
        description: '更新後的課程介紹',
        start_at: '{{laterStart}}',
        end_at: '{{laterEnd}}',
        max_participants: 8,
        meeting_url: 'https://example.com/new-meeting',
      }),
      tests: ok(),
    }),
    get('更新後再查一次課程', '/api/admin/coaches/courses/{{courseId}}', {
      auth: 'coachToken',
      tests: [
        ...ok(),
        ...t(
          '內容真的變了',
          'pm.expect(json.data.name).to.eql("改版課程_" + V("runId"));',
          'pm.expect(Number(json.data.max_participants)).to.eql(8);',
          'pm.expect(json.data.meeting_url).to.eql("https://example.com/new-meeting");',
        ),
      ],
    }),
    get('教練課程列表', '/api/admin/coaches/courses', {
      auth: 'coachToken',
      tests: [
        ...ok(),
        ...t(
          '包含剛開的課，有中文 status 與 participants',
          'pm.expect(json.data).to.be.an("array");',
          'const mine = json.data.find((item) => item.id === V("courseId"));',
          'pm.expect(mine, "列表內找不到 courseId").to.exist;',
          'pm.expect(mine.status).to.eql("尚未開始");',
          'pm.expect(Number(mine.participants)).to.eql(0);',
        ),
      ],
    }),
    post('註冊教練 B 的帳號', '/api/users/signup', {
      body: signupBody('pm_stranger', '另一位教練'),
      tests: [...ok(), ...save('strangerUserId', 'json.data && json.data.user && json.data.user.id')],
    }),
    post('升級教練 B', '/api/admin/coaches/{{strangerUserId}}', {
      body: { experience_years: 1, description: '用來驗證只能查改自己的課' },
      tests: ok(),
    }),
    post('登入教練 B', '/api/users/login', {
      body: loginBody('pm_stranger'),
      tests: loginOk('strangerToken', 'COACH'),
    }),
    get('查單一課程：別的教練的課查不到', '/api/admin/coaches/courses/{{courseId}}', {
      auth: 'strangerToken',
      tests: fail(400, MSG.courseNotFound),
    }),
    put('更新課程：別的教練的課改不了', '/api/admin/coaches/courses/{{courseId}}', {
      auth: 'strangerToken',
      body: courseBody({ name: '被外人改的課_{{runId}}', meeting_url: 'https://example.com/hijack' }),
      tests: fail(400, MSG.courseNotFound),
    }),
    get('課主再查一次：沒有被外人蓋掉', '/api/admin/coaches/courses/{{courseId}}', {
      auth: 'coachToken',
      tests: [
        ...ok(),
        ...t('名稱維持原樣', 'pm.expect(json.data.name).to.eql("改版課程_" + V("runId"));'),
      ],
    }),
  ],
);

// ---------- M4 ----------

const m4 = folder('M4 公開瀏覽', '訪客不用登入就能看的資料。', [
  get('教練列表', '/api/coaches?per=100&page=1', {
    tests: [...ok(), ...t('data 是陣列', 'pm.expect(json.data).to.be.an("array");')],
  }),
  get('教練列表：帶尾斜線（前端實際打的路徑）', '/api/coaches/?per=10000&page=1', {
    tests: [
      ...ok(),
      ...t(
        '找得到教練 A，且有 id / user_id / name',
        'const found = json.data.find((c) => c.user_id === V("coachUserId"));',
        'pm.expect(found, "列表內找不到教練 A").to.exist;',
        'pm.expect(found.id).to.eql(V("coachId"));',
        'pm.expect(found.name).to.eql("教練_" + V("runId"));',
      ),
    ],
  }),
  get('教練列表：沒帶 per 與 page', '/api/coaches', {
    tests: fail(400, MSG.invalidFields),
  }),
  get('教練詳情', '/api/coaches/{{coachId}}', {
    tests: [
      ...ok(),
      ...t(
        'user.name 正確，coach.skills 是技能名稱的陣列',
        'pm.expect(json.data.user.name).to.eql("教練_" + V("runId"));',
        'pm.expect(json.data.user.role).to.eql("COACH");',
        'pm.expect(json.data.user).to.not.have.property("password");',
        'pm.expect(json.data.coach.id).to.eql(V("coachId"));',
        'pm.expect(Number(json.data.coach.experience_years)).to.eql(5);',
        'pm.expect(json.data.coach.skills).to.be.an("array").that.includes("技能_" + V("runId"));',
      ),
    ],
  }),
  get('教練詳情：id 不存在', `/api/coaches/${FAKE_UUID}`, {
    tests: fail(400, MSG.coachNotFound),
  }),
  get('教練的課程列表', '/api/coaches/{{coachId}}/courses', {
    tests: [
      ...ok(),
      ...t(
        '包含還沒結束的課，帶 coach_name 與 skill_name',
        'pm.expect(json.data).to.be.an("array");',
        'const found = json.data.find((c) => c.id === V("courseId"));',
        'pm.expect(found, "列表內找不到 courseId").to.exist;',
        'pm.expect(found.name).to.eql("改版課程_" + V("runId"));',
        'pm.expect(found.coach_name).to.eql("教練_" + V("runId"));',
        'pm.expect(found.skill_name).to.eql("技能_" + V("runId"));',
        'pm.expect(Number(found.max_participants)).to.eql(8);',
        'pm.expect(found.start_at).to.be.a("string");',
        'pm.expect(found.end_at).to.be.a("string");',
      ),
    ],
  }),
  get('進行中課程：還沒開始的課不會出現', '/api/courses', {
    tests: [
      ...ok(),
      ...t(
        'data 是陣列，且不含未來的課（口徑：start_at <= now < end_at）',
        'pm.expect(json.data).to.be.an("array");',
        'pm.expect(json.data.find((c) => c.id === V("courseId"))).to.be.undefined;',
      ),
    ],
  }),
  post('開一堂昨天開始、明天結束的課', '/api/admin/coaches/courses', {
    auth: 'coachToken',
    body: courseBody({
      name: '進行中課程_{{runId}}',
      start_at: '{{ongoingStart}}',
      end_at: '{{ongoingEnd}}',
    }),
    tests: [...ok(), ...save('ongoingCourseId', 'json.data && json.data.course && json.data.course.id')],
  }),
  get('進行中課程：已開始且還沒結束的課會出現', '/api/courses', {
    tests: [
      ...ok(),
      ...t(
        '找得到進行中的課，帶 coach_name 與 skill_name',
        'const found = json.data.find((c) => c.id === V("ongoingCourseId"));',
        'pm.expect(found, "列表內找不到 ongoingCourseId").to.exist;',
        'pm.expect(found.name).to.eql("進行中課程_" + V("runId"));',
        'pm.expect(found.coach_name).to.eql("教練_" + V("runId"));',
        'pm.expect(found.skill_name).to.eql("技能_" + V("runId"));',
      ),
    ],
  }),
  get('不存在的路徑', '/api/no-such-route', {
    tests: fail(404, MSG.routeNotFound),
  }),
]);

// ---------- M5 ----------

const userCourses = (name, auth, ...assertions) =>
  get(name, '/api/users/courses', { auth, tests: [...ok(), ...assertions] });

const m5 = folder(
  'M5 購買與報名',
  '四句固定訊息前端會逐字比對。報名的檢查順序：課程存在 → 是否報名過（含已取消）→ 剩餘堂數 → 名額。',
  [
    post('購買方案：沒帶 token', '/api/credit-package/{{packageId}}', {
      tests: fail(401, MSG.notLogin),
    }),
    post('購買方案：方案不存在', `/api/credit-package/${FAKE_UUID}`, {
      auth: 'memberToken',
      tests: fail(400, MSG.invalidId),
    }),
    post('報名：沒買過方案', '/api/courses/{{courseId}}', {
      auth: 'memberToken',
      tests: fail(400, MSG.noCredit),
    }),
    post('購買方案', '/api/credit-package/{{packageId}}', {
      auth: 'memberToken',
      tests: ok(),
    }),
    get('購買紀錄', '/api/users/credit-package', {
      auth: 'memberToken',
      tests: [
        ...ok(),
        ...t(
          '看得到剛買的方案，堂數與金額正確',
          'pm.expect(json.data).to.be.an("array").with.lengthOf(1);',
          'const record = json.data[0];',
          'pm.expect(record.name).to.eql("方案_" + V("runId"));',
          'pm.expect(Number(record.purchased_credits)).to.eql(7);',
          'pm.expect(Number(record.price_paid)).to.eql(1400);',
          'pm.expect(record.purchase_at).to.be.a("string");',
        ),
      ],
    }),
    post('報名：沒帶 token', '/api/courses/{{courseId}}', {
      tests: fail(401, MSG.notLogin),
    }),
    post('報名：課程不存在', `/api/courses/${FAKE_UUID}`, {
      auth: 'memberToken',
      tests: fail(400, MSG.invalidId),
    }),
    post('報名課程', '/api/courses/{{courseId}}', {
      auth: 'memberToken',
      tests: ok(),
    }),
    userCourses(
      '我的課表：報名後堂數少 1',
      'memberToken',
      ...t(
        'credit_remain 為 6，course_booking 看得到這堂課',
        'pm.expect(Number(json.data.credit_remain)).to.eql(6);',
        'pm.expect(Number(json.data.credit_usage)).to.eql(1);',
        'const booked = json.data.course_booking.find((b) => b.course_id === V("courseId"));',
        'pm.expect(booked, "課表內找不到 courseId").to.exist;',
        'pm.expect(booked.name).to.eql("改版課程_" + V("runId"));',
        'pm.expect(booked.coach_name).to.eql("教練_" + V("runId"));',
        'pm.expect(booked.cancelled_at).to.be.null;',
      ),
    ),
    post('報名：同一堂課報第二次', '/api/courses/{{courseId}}', {
      auth: 'memberToken',
      tests: fail(400, MSG.alreadyBooked),
    }),
    post('開一堂名額只有 1 的課', '/api/admin/coaches/courses', {
      auth: 'coachToken',
      body: courseBody({ name: '小班課_{{runId}}', max_participants: 1 }),
      tests: [...ok(), ...save('fullCourseId', 'json.data && json.data.course && json.data.course.id')],
    }),
    post('會員 A 報滿小班課', '/api/courses/{{fullCourseId}}', {
      auth: 'memberToken',
      tests: ok(),
    }),
    post('註冊會員 B', '/api/users/signup', {
      body: signupBody('pm_member_b', '會員B'),
      tests: ok(),
    }),
    post('登入會員 B', '/api/users/login', {
      body: loginBody('pm_member_b'),
      tests: loginOk('memberBToken', 'USER'),
    }),
    post('會員 B 購買方案', '/api/credit-package/{{packageId}}', {
      auth: 'memberBToken',
      tests: ok(),
    }),
    post('報名：課程已額滿', '/api/courses/{{fullCourseId}}', {
      auth: 'memberBToken',
      tests: fail(400, MSG.full),
    }),
    del('取消報名：沒帶 token', '/api/courses/{{fullCourseId}}', {
      tests: fail(401, MSG.notLogin),
    }),
    del('取消報名', '/api/courses/{{fullCourseId}}', {
      auth: 'memberToken',
      tests: ok(),
    }),
    userCourses(
      '我的課表：取消後堂數加回來，紀錄還在',
      'memberToken',
      ...t(
        'credit_remain 回到 6，小班課的 cancelled_at 有值',
        'pm.expect(Number(json.data.credit_remain)).to.eql(6);',
        'const cancelled = json.data.course_booking.find((b) => b.course_id === V("fullCourseId"));',
        'pm.expect(cancelled, "取消後紀錄應該還在（軟刪除）").to.exist;',
        'pm.expect(cancelled.cancelled_at).to.be.a("string");',
      ),
    ),
    del('取消報名：同一堂課取消第二次', '/api/courses/{{fullCourseId}}', {
      auth: 'memberToken',
      tests: fail(400),
    }),
    post('報名：取消過的課不能再報', '/api/courses/{{fullCourseId}}', {
      auth: 'memberToken',
      tests: fail(400, MSG.alreadyBooked),
    }),
  ],
);

// ---------- M6 ----------

/** 用當下所有方案重算單堂均價，存起來給下一支營收請求比對 */
const savePerCreditPrice = get('重算單堂均價（全部方案 Σprice ÷ Σcredit_amount）', '/api/credit-package', {
  tests: [
    ...ok(),
    'const totalPrice = json.data.reduce((sum, p) => sum + Number(p.price), 0);',
    'const totalCredits = json.data.reduce((sum, p) => sum + Number(p.credit_amount), 0);',
    'pm.collectionVariables.set("perCreditPrice", String(totalPrice / totalCredits));',
    ...t('方案總堂數大於 0', 'pm.expect(totalCredits).to.be.above(0);'),
  ],
});

const revenue = (name, auth, bookings, participants) =>
  get(name, '/api/admin/coaches/revenue?month={{currentMonth}}', {
    auth,
    tests: [
      ...ok(),
      ...t(
        `participants = ${participants}、course_count = ${bookings}（報名筆數）、revenue = floor(${bookings} × 均價)`,
        'const total = json.data.total;',
        `pm.expect(Number(total.participants)).to.eql(${participants});`,
        `pm.expect(Number(total.course_count)).to.eql(${bookings});`,
        // floor 在乘完之後才做，不是先 floor 均價再乘
        `pm.expect(Number(total.revenue)).to.eql(Math.floor(${bookings} * Number(V("perCreditPrice") || 0)));`,
      ),
    ],
  });

const m6 = folder(
  'M6 教練月營收',
  '報名以建立時間計入該月；取消的報名不算。course_count 的語意是「該月未取消的報名筆數」，不是課程數。',
  [
    get('營收：還沒開課的教練都是 0', '/api/admin/coaches/revenue?month={{currentMonth}}', {
      auth: 'strangerToken',
      tests: [
        ...ok(),
        ...t(
          'revenue / participants / course_count 都是 0',
          'pm.expect(Number(json.data.total.revenue)).to.eql(0);',
          'pm.expect(Number(json.data.total.participants)).to.eql(0);',
          'pm.expect(Number(json.data.total.course_count)).to.eql(0);',
        ),
      ],
    }),
    get('營收：一般會員不能查', '/api/admin/coaches/revenue?month={{currentMonth}}', {
      auth: 'memberToken',
      tests: fail(401, MSG.notCoach),
    }),
    get('營收：month 不是英文月份名', '/api/admin/coaches/revenue?month=13', {
      auth: 'coachToken',
      tests: fail(400, MSG.invalidFields),
    }),
    get('營收：沒帶 month', '/api/admin/coaches/revenue', {
      auth: 'coachToken',
      tests: fail(400, MSG.invalidFields),
    }),
    post('會員 B 報名課程', '/api/courses/{{courseId}}', {
      auth: 'memberBToken',
      tests: ok(),
    }),
    savePerCreditPrice,
    revenue('營收：兩位會員當月報名', 'coachToken', 2, 2),
    del('會員 B 取消報名', '/api/courses/{{courseId}}', {
      auth: 'memberBToken',
      tests: ok(),
    }),
    savePerCreditPrice,
    revenue('營收：取消的報名不計入', 'coachToken', 1, 1),
  ],
);

// ---------- M7 ----------

const adminUsers = (name, query, ...assertions) =>
  get(name, `/api/admin/users${query}`, { auth: 'adminToken', tests: [...ok(), ...assertions] });

const m7 = folder(
  'M7 管理員後台',
  '需要預設管理員（collection 變數 adminEmail / adminPassword）。權限不足回 403，與教練端點的 401 不同。',
  [
    get('使用者列表：沒帶 token', '/api/admin/users', {
      tests: fail(401, MSG.notLogin),
    }),
    get('使用者列表：一般會員', '/api/admin/users', {
      auth: 'memberToken',
      tests: fail(403, MSG.notAdmin),
    }),
    get('使用者列表：教練', '/api/admin/users', {
      auth: 'coachToken',
      tests: fail(403, MSG.notAdmin),
    }),
    post('登入預設管理員', '/api/users/login', {
      body: { email: '{{adminEmail}}', password: '{{adminPassword}}' },
      tests: loginOk('adminToken', 'ADMIN', 'adminId'),
    }),
    get('教練資料：管理員不是教練', '/api/admin/coaches', {
      auth: 'adminToken',
      tests: fail(401, MSG.notCoach),
    }),
    adminUsers(
      '使用者列表',
      '',
      ...t(
        '每筆有 id / name / email / role / created_at，沒有 password 與 token_version',
        'pm.expect(json.data.users).to.be.an("array").that.is.not.empty;',
        'json.data.users.forEach((user) => {',
        '  pm.expect(user).to.include.all.keys("id", "name", "email", "role", "created_at");',
        '  pm.expect(user).to.not.have.any.keys("password", "token_version");',
        '});',
      ),
      ...t(
        '分頁預設 page 1、per 10',
        'pm.expect(json.data.pagination.page).to.eql(1);',
        'pm.expect(json.data.pagination.per).to.eql(10);',
        'pm.expect(json.data.users.length).to.be.at.most(10);',
        'pm.expect(json.data.pagination.total).to.be.at.least(json.data.users.length);',
      ),
    ),
    adminUsers(
      '使用者列表：分頁',
      '?page=2&per=1',
      ...t(
        '每頁 1 筆時，總頁數等於總筆數',
        'pm.expect(json.data.users).to.have.lengthOf(1);',
        'pm.expect(json.data.pagination.page).to.eql(2);',
        'pm.expect(json.data.pagination.per).to.eql(1);',
        'pm.expect(json.data.pagination.total_pages).to.eql(json.data.pagination.total);',
      ),
    ),
    adminUsers(
      '使用者列表：依角色篩選',
      '?role=admin&per=100',
      ...t(
        '全部都是 ADMIN，且包含預設管理員（role 不分大小寫）',
        'pm.expect(json.data.users.every((user) => user.role === "ADMIN")).to.be.true;',
        'pm.expect(json.data.users.map((user) => user.id)).to.include(V("adminId"));',
      ),
    ),
    adminUsers(
      '使用者列表：關鍵字搜尋',
      '?keyword={{runId}}&per=100',
      ...t(
        '只回傳名稱或 Email 含關鍵字的使用者，包含這一輪建立的四個帳號',
        'const runId = V("runId");',
        'pm.expect(json.data.users.every((user) =>',
        '  user.name.toLowerCase().includes(runId) || user.email.includes(runId))).to.be.true;',
        'const emails = json.data.users.map((user) => user.email);',
        '["pm_member", "pm_coach", "pm_stranger", "pm_member_b"].forEach((prefix) => {',
        '  pm.expect(emails).to.include(prefix + "_" + runId + "@example.com");',
        '});',
        'pm.expect(json.data.pagination.total).to.eql(json.data.users.length);',
      ),
    ),
    adminUsers(
      '使用者列表：關鍵字沒有符合的',
      '?keyword=zz-no-such-user-{{runId}}',
      ...t(
        '回空陣列，total 為 0',
        'pm.expect(json.data.users).to.be.an("array").that.is.empty;',
        'pm.expect(json.data.pagination.total).to.eql(0);',
      ),
    ),
    get('使用者列表：per 超過上限 100', '/api/admin/users?per=101', {
      auth: 'adminToken',
      tests: fail(400, MSG.invalidFields),
    }),
    get('使用者列表：page 為 0', '/api/admin/users?page=0', {
      auth: 'adminToken',
      tests: fail(400, MSG.invalidFields),
    }),
    get('使用者列表：role 不存在', '/api/admin/users?role=boss', {
      auth: 'adminToken',
      tests: fail(400, MSG.invalidFields),
    }),

    post('建立管理員：一般會員不能呼叫', '/api/admin/users/admins', {
      auth: 'memberToken',
      body: signupBody('pm_admin', '新管理員'),
      tests: fail(403, MSG.notAdmin),
    }),
    post('建立管理員', '/api/admin/users/admins', {
      auth: 'adminToken',
      body: signupBody('pm_admin', '新管理員'),
      tests: [
        ...ok(),
        ...t(
          'role 是 ADMIN，沒有回傳 password',
          'pm.expect(json.data.user.id).to.be.a("string").and.not.empty;',
          'pm.expect(json.data.user.name).to.eql("新管理員_" + V("runId"));',
          'pm.expect(json.data.user.email).to.eql("pm_admin_" + V("runId") + "@example.com");',
          'pm.expect(json.data.user.role).to.eql("ADMIN");',
          'pm.expect(json.data.user.created_at).to.be.a("string");',
          'pm.expect(json.data.user).to.not.have.property("password");',
        ),
        ...save('newAdminId', 'json.data && json.data.user && json.data.user.id'),
      ],
    }),
    post('建立管理員：Email 重複', '/api/admin/users/admins', {
      auth: 'adminToken',
      body: signupBody('pm_admin', '新管理員'),
      tests: fail(409, MSG.emailTaken),
    }),
    post('建立管理員：密碼不符合規則', '/api/admin/users/admins', {
      auth: 'adminToken',
      body: { name: '弱密碼管理員_{{runId}}', email: 'pm_admin_weak_{{runId}}@example.com', password: '123' },
      tests: fail(400, MSG.passwordRule),
    }),
    post('建立管理員：缺 email', '/api/admin/users/admins', {
      auth: 'adminToken',
      body: { name: '缺信箱管理員_{{runId}}', password: PASSWORD },
      tests: fail(400, MSG.invalidFields),
    }),
    post('登入新管理員', '/api/users/login', {
      body: loginBody('pm_admin'),
      tests: loginOk('newAdminToken', 'ADMIN'),
    }),

    post('重設密碼：不能重設自己', '/api/admin/users/{{adminId}}/reset-password', {
      auth: 'adminToken',
      tests: fail(400, MSG.cannotResetSelf),
    }),
    post('重設密碼：對象不是管理員', '/api/admin/users/{{memberId}}/reset-password', {
      auth: 'adminToken',
      tests: fail(400, MSG.onlyAdminCanBeReset),
    }),
    post('重設密碼：使用者不存在', `/api/admin/users/${FAKE_UUID}/reset-password`, {
      auth: 'adminToken',
      tests: fail(400, MSG.userNotFound),
    }),
    post('重設密碼：一般會員不能呼叫', '/api/admin/users/{{newAdminId}}/reset-password', {
      auth: 'memberToken',
      tests: fail(403, MSG.notAdmin),
    }),
    post('重設另一位管理員的密碼', '/api/admin/users/{{newAdminId}}/reset-password', {
      auth: 'adminToken',
      tests: ok(),
    }),
    get('重設後：對方原本的 token 失效', '/api/users/profile', {
      auth: 'newAdminToken',
      tests: fail(401, MSG.tokenInvalid),
    }),
    post('重設後：原本的密碼登不進去', '/api/users/login', {
      body: loginBody('pm_admin'),
      tests: fail(400, MSG.loginFailed),
    }),
    post('重設後：用固定密碼登入', '/api/users/login', {
      body: loginBody('pm_admin', RESET_PASSWORD),
      tests: loginOk('newAdminToken', 'ADMIN'),
    }),
    put('新管理員把固定密碼換成隨機密碼', '/api/users/password', {
      auth: 'newAdminToken',
      body: {
        password: RESET_PASSWORD,
        new_password: '{{randomPassword}}',
        confirm_new_password: '{{randomPassword}}',
      },
      tests: ok(),
    }),

    post('註冊準備被升級的會員', '/api/users/signup', {
      body: signupBody('pm_candidate', '準教練'),
      tests: [...ok(), ...save('candidateId', 'json.data && json.data.user && json.data.user.id')],
    }),
    post('登入準教練（升級前）', '/api/users/login', {
      body: loginBody('pm_candidate'),
      tests: loginOk('candidateToken', 'USER'),
    }),
    post('管理員升級教練：一般會員不能呼叫', '/api/admin/users/{{candidateId}}/coach', {
      auth: 'memberToken',
      body: { experience_years: 0 },
      tests: fail(403, MSG.notAdmin),
    }),
    post('管理員升級教練：沒帶 token', '/api/admin/users/{{candidateId}}/coach', {
      body: { experience_years: 0 },
      tests: fail(401, MSG.notLogin),
    }),
    post('管理員升級教練：缺年資', '/api/admin/users/{{candidateId}}/coach', {
      auth: 'adminToken',
      body: {},
      tests: fail(400, MSG.invalidFields),
    }),
    post('管理員升級教練：使用者不存在', `/api/admin/users/${FAKE_UUID}/coach`, {
      auth: 'adminToken',
      body: { experience_years: 0 },
      tests: fail(400, MSG.userNotFound),
    }),
    post('管理員升級教練：對象是管理員', '/api/admin/users/{{newAdminId}}/coach', {
      auth: 'adminToken',
      body: { experience_years: 0 },
      tests: fail(400, MSG.onlyUserCanBePromoted),
    }),
    post('M3 免登入的升級路徑也不能把管理員改成教練', '/api/admin/coaches/{{newAdminId}}', {
      body: { experience_years: 3, description: '嘗試把管理員改成教練' },
      tests: fail(400, MSG.onlyUserCanBePromoted),
    }),
    post('管理員升級教練（年資 0、簡介空白）', '/api/admin/users/{{candidateId}}/coach', {
      auth: 'adminToken',
      body: { experience_years: 0 },
      tests: [
        ...ok(),
        ...t(
          'user.role 變成 COACH，年資為 0',
          'pm.expect(json.data.user.name).to.eql("準教練_" + V("runId"));',
          'pm.expect(json.data.user.role).to.eql("COACH");',
          'pm.expect(json.data.coach.user_id).to.eql(V("candidateId"));',
          'pm.expect(Number(json.data.coach.experience_years)).to.eql(0);',
        ),
      ],
    }),
    post('管理員升級教練：已經是教練', '/api/admin/users/{{candidateId}}/coach', {
      auth: 'adminToken',
      body: { experience_years: 0 },
      tests: fail(409, MSG.alreadyCoach),
    }),
    get('升級後：對方原本的 token 失效', '/api/users/profile', {
      auth: 'candidateToken',
      tests: fail(401, MSG.tokenInvalid),
    }),
    post('升級後：重新登入拿到 role=COACH 的 token', '/api/users/login', {
      body: loginBody('pm_candidate'),
      tests: loginOk('candidateToken', 'COACH'),
    }),
    get('升級後：進得去教練後台', '/api/admin/coaches', {
      auth: 'candidateToken',
      tests: [
        ...ok(),
        ...t(
          '年資 0、沒有技能',
          'pm.expect(Number(json.data.experience_years)).to.eql(0);',
          'pm.expect(json.data.skill_ids).to.be.an("array").that.is.empty;',
        ),
      ],
    }),
  ],
);

// ===== openapi 驗證（collection 層級的 test script） =====

function loadOpenapi() {
  let yaml;
  try {
    // 根目錄的 package.json 不能動，這裡借用 jest 帶進來的 js-yaml
    yaml = require('js-yaml');
  } catch (e) {
    throw new Error('找不到 js-yaml，請先在根目錄執行 npm install');
  }
  return yaml.load(fs.readFileSync(OPENAPI_PATH, 'utf8'));
}

const SCHEMA_KEYWORDS = ['type', 'enum', 'format', 'minimum', 'maximum', 'minLength', 'maxLength',
  'minItems', 'maxItems', 'pattern'];

/**
 * OpenAPI 3.0 的 schema 轉成 Postman 內建的 Ajv 看得懂的 JSON Schema。
 * openapi.yaml 的回應 schema 都沒有寫 required，照字面驗的話回傳空物件也會過，
 * 所以這裡把「文件列出的欄位」都當成必須出現（值可以是 null 的欄位仍要有 key）。
 */
function toJsonSchema(schema) {
  if (!schema || typeof schema !== 'object') return {};
  const out = {};
  SCHEMA_KEYWORDS.forEach((key) => {
    if (schema[key] !== undefined) out[key] = schema[key];
  });
  ['oneOf', 'anyOf', 'allOf'].forEach((key) => {
    if (Array.isArray(schema[key])) out[key] = schema[key].map(toJsonSchema);
  });
  if (schema.items) out.items = toJsonSchema(schema.items);
  if (schema.properties) {
    out.properties = {};
    Object.entries(schema.properties).forEach(([name, sub]) => {
      out.properties[name] = toJsonSchema(sub);
    });
    out.required = schema.required || Object.keys(schema.properties);
  }
  if (schema.nullable) {
    if (out.type) out.type = [].concat(out.type, 'null');
    if (out.enum) out.enum = out.enum.concat(null);
  }
  return out;
}

/**
 * 回應只有 example、沒有 schema 時，從範例推出結構：欄位都要在，型別要相同。
 * 範例看不出哪些欄位可以是 null（例如還沒填的 profile_image_url），所以純量欄位一律允許 null。
 */
function inferSchema(example) {
  if (example === null || example === undefined) return {};
  if (Array.isArray(example)) {
    return example.length ? { type: 'array', items: inferSchema(example[0]) } : { type: 'array' };
  }
  if (typeof example === 'object') {
    const properties = {};
    Object.entries(example).forEach(([name, value]) => {
      properties[name] = inferSchema(value);
    });
    return { type: 'object', properties, required: Object.keys(example) };
  }
  return { type: [typeof example, 'null'] };
}

/** 把 openapi 的每支 API 整理成 { 方法, 路徑比對式, 各狀態碼的 schema } */
function buildOperations(openapi) {
  const operations = [];
  Object.entries(openapi.paths).forEach(([template, methods]) => {
    Object.entries(methods).forEach(([method, operation]) => {
      if (!operation || !operation.responses) return;
      const responses = {};
      Object.entries(operation.responses).forEach(([status, response]) => {
        const content = response.content || {};
        const type = content['application/json'] ? 'application/json' : Object.keys(content)[0];
        const media = content[type] || {};
        const example = media.example !== undefined
          ? media.example
          : (Object.values(media.examples || {})[0] || {}).value;
        const entry = { kind: type === 'application/json' ? 'json' : 'text' };
        if (media.schema) {
          entry.schema = toJsonSchema(media.schema);
          entry.from = 'schema';
        } else if (example !== undefined) {
          entry.schema = inferSchema(example);
          entry.from = 'example';
        }
        responses[status] = entry;
      });
      operations.push({
        method: method.toUpperCase(),
        path: template,
        pattern: `^${template.replace(/\{[^}]+\}/g, '[^/]+')}$`,
        params: (template.match(/\{[^}]+\}/g) || []).length,
        responses,
      });
    });
  });
  // 字面路徑優先：/api/admin/coaches/courses 要排在 /api/admin/coaches/{userId} 前面
  return operations.sort((a, b) => a.params - b.params);
}

/** 每支請求跑完都會執行（在請求自己的 test script 之前） */
const openapiValidator = (operations) => [
  '// 由 postman/build-collection.js 從 docs/openapi.yaml 產生，請勿手動修改',
  `const OPERATIONS = ${JSON.stringify(operations)};`,
  '',
  'const url = pm.variables.replaceIn(pm.request.url.toString());',
  'let path = (url.match(/^[a-z]+:\\/\\/[^/?#]+([^?#]*)/i) || [])[1] || "/";',
  'if (path.length > 1) path = path.replace(/\\/+$/, "");',
  'const operation = OPERATIONS.find((op) => op.method === pm.request.method && new RegExp(op.pattern).test(path));',
  '',
  '// openapi 沒有這條路徑（例如故意打不存在的路由）就不驗',
  'if (operation) {',
  '  const label = operation.method + " " + operation.path;',
  '  const status = String(pm.response.code);',
  '  let documented = operation.responses[status];',
  '  let usedStatus = status;',
  '  if (!documented && pm.response.code >= 200 && pm.response.code < 300) {',
  '    // 200 與 201 可互換：文件寫的是另一個 2xx 時，拿那一個的結構來比',
  '    usedStatus = Object.keys(operation.responses).find((code) => code.charAt(0) === "2");',
  '    documented = operation.responses[usedStatus];',
  '  }',
  '',
  '  if (!documented) {',
  '    pm.test("[openapi] " + label + " 有記載 " + status + " 回應", () => {',
  '      pm.expect.fail("openapi.yaml 只記載了 " + Object.keys(operation.responses).join(" / "));',
  '    });',
  '  } else if (documented.schema) {',
  '    const source = documented.from === "schema" ? "schema" : "範例結構";',
  '    pm.test("[openapi] 回應符合 " + label + " 的 " + usedStatus + " " + source, () => {',
  '      const Ajv = require("ajv");',
  '      const ajv = new Ajv({ allErrors: true, unknownFormats: "ignore" });',
  '      const body = documented.kind === "json" ? pm.response.json() : pm.response.text();',
  '      if (!ajv.validate(documented.schema, body)) {',
  '        // 陣列每一筆都錯時訊息會很長，只列前五個',
  '        const more = ajv.errors.length > 5 ? "（另有 " + (ajv.errors.length - 5) + " 個）" : "";',
  '        pm.expect.fail(ajv.errorsText(ajv.errors.slice(0, 5), { dataVar: "body" }) + more);',
  '      }',
  '    });',
  '  }',
  '}',
];

// ===== 組成 collection =====

const collection = {
  info: {
    name: 'LiveFit API（Spring Boot 版）',
    description: [
      '對 `livefit/` 的端到端合約測試，在 Collection Runner 整份執行即可，不需要手動填 token。',
      '',
      '- 由 `postman/build-collection.js` 產生，要修改請改腳本後重新產生。',
      '- 請求之間靠 collection 變數串接，必須整份依序執行。',
      '- 每次執行都會用新的亂數建立帳號、技能、方案、課程，可以重複跑，但資料會留在資料庫。',
      '- M7 需要預設管理員：`adminEmail` / `adminPassword`。',
      '- 第三方登入只驗失敗案例。',
      '- 名稱以 `[openapi]` 開頭的斷言是拿回應比對 `docs/openapi.yaml`，其餘是流程斷言。',
    ].join('\n'),
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
  },
  variable: SETTINGS,
  event: [
    {
      listen: 'test',
      script: { type: 'text/javascript', exec: openapiValidator(buildOperations(loadOpenapi())) },
    },
  ],
  item: [m1, m2, social, m3, m4, m5, m6, m7],
};

/** 選用：值與 collection 變數相同，匯入後改這裡就能切換受測環境，不必動 collection */
const environment = {
  name: 'LiveFit 本機',
  values: SETTINGS.map((setting) => ({ ...setting, type: 'default', enabled: true })),
  _postman_variable_scope: 'environment',
};

fs.writeFileSync(OUTPUT, `${JSON.stringify(collection, null, 2)}\n`);
fs.writeFileSync(ENV_OUTPUT, `${JSON.stringify(environment, null, 2)}\n`);

const count = collection.item.reduce((sum, f) => sum + f.item.length, 0);
console.log(`已產生 ${path.relative(process.cwd(), OUTPUT)}：${collection.item.length} 個資料夾、${count} 支請求`);
