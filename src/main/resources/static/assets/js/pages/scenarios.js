import {
  $, base64UrlEncode, clear, decodeJwt, h, loadSession, onAction, request, sleep,
} from '../api.js';

// Must match db/demo/R__demo_data.sql and app.token.rotation-grace-period.
const DEMO_PASSWORD = 'DemoSifre123!';
const GRACE_PERIOD_MS = 5000;
const LOGIN_FAILURES_BEFORE_BLOCK = 5;

const label = 'senaryo';

function anonymous(method, url, body) {
  return request(method, url, { auth: 'none', body, label });
}

function withToken(token) {
  return (method, url, body) => request(method, url, { auth: 'bearer', token, body, label });
}

/** Cookie mode, only to carry this page's XSRF-TOKEN for CSRF-protected anonymous calls (register). */
function withCsrf(method, url, body) {
  return request(method, url, { auth: 'cookie', body, label, retry: false });
}

function login(email, password = DEMO_PASSWORD, userType = 'CUSTOMER') {
  return anonymous('POST', '/api/auth/mobile/login', { email, password, userType });
}

async function loginOrFail(email, password = DEMO_PASSWORD, userType = 'CUSTOMER') {
  const response = await login(email, password, userType);
  if (!response.ok) throw new Error(`${email} giriş yapamadı: ${response.status} ${response.code ?? ''}`);
  return response.data;
}

function refresh(refreshToken) {
  return anonymous('POST', '/api/auth/mobile/refresh', { refreshToken });
}

/** Account ids are not fixed (upgraded databases number them differently): ask the token. */
async function accountIdOf(tokens) {
  const response = await withToken(tokens.accessToken)('GET', '/api/me');
  if (!response.ok) throw new Error(`/api/me okunamadı: ${response.status} ${response.code ?? ''}`);
  return response.data.accountId;
}

function describe(response) {
  return `${response.status}${response.code ? ` ${response.code}` : ''}`;
}

/** Each check returns { pass, actual }. */
function expectResponse(response, status, code) {
  const pass = response.status === status && (code === undefined || response.code === code);
  return { pass, actual: describe(response) };
}

function all(...checks) {
  return { pass: checks.every((c) => c.pass), actual: checks.map((c) => c.actual).join(' · ') };
}

function randomSuffix() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

// Shared state of one run.
const ctx = {};

async function registerFresh(prefix) {
  const suffix = randomSuffix();
  const account = {
    email: `${prefix}-${suffix}@example.com`,
    password: `Senaryo-${suffix}!`,
  };
  const response = await withCsrf('POST', '/api/auth/register',
    { email: account.email, password: account.password, firstName: 'Senaryo', lastName: 'Hesabı' });
  if (response.status !== 201) throw new Error(`Test hesabı açılamadı: ${describe(response)}`);
  account.id = response.data.id;
  return account;
}

const scenarios = [
  {
    title: 'Test hesabı aç (kayıt, CSRF başlığıyla)',
    expected: '201',
    run: async () => {
      ctx.fresh = await registerFresh('senaryo');
      return { pass: true, actual: `201 · #${ctx.fresh.id} ${ctx.fresh.email}` };
    },
  },
  {
    title: 'Hatalı şifre',
    expected: '401 INVALID_CREDENTIALS',
    run: async () => expectResponse(await login(ctx.fresh.email, 'yanlis-sifre-123'), 401, 'INVALID_CREDENTIALS'),
  },
  {
    title: 'Olmayan e-posta: hatalı şifreyle aynı cevap (hesap varlığı sızmaz)',
    expected: '401 INVALID_CREDENTIALS',
    run: async () => expectResponse(await login(`yok-${randomSuffix()}@example.com`), 401, 'INVALID_CREDENTIALS'),
  },
  {
    title: 'Devre dışı hesap, doğru şifre (pasif@)',
    expected: '401 INVALID_CREDENTIALS',
    run: async () => expectResponse(await login('pasif@example.com'), 401, 'INVALID_CREDENTIALS'),
  },
  {
    title: 'Müşteri bilgileriyle ADMIN tipi seçmek (hesap tipi giriş anahtarının parçası)',
    expected: '401 INVALID_CREDENTIALS',
    run: async () => expectResponse(await login(ctx.fresh.email, ctx.fresh.password, 'ADMIN'), 401,
      'INVALID_CREDENTIALS'),
  },
  {
    title: 'Mobil giriş: token\'lar gövdede',
    expected: '200 + accessToken + refreshToken',
    run: async () => {
      const response = await login(ctx.fresh.email, ctx.fresh.password);
      ctx.freshTokens = response.data;
      const ok = response.ok && !!response.data.accessToken && !!response.data.refreshToken;
      return { pass: ok, actual: describe(response) };
    },
  },
  {
    title: 'Profil outbox üzerinden oluşur (identity → CustomerRegistered → customer)',
    expected: '200 (en geç 5 sn)',
    run: async () => {
      // Registration commits only the account; the relay delivers the event and the customer module opens
      // the profile a moment later (eventual consistency between bounded contexts).
      const started = Date.now();
      let response;
      do {
        response = await withToken(ctx.freshTokens.accessToken)('GET', '/api/customer/profile');
        if (response.ok) break;
        await sleep(250);
      } while (Date.now() - started < 5000);
      return { pass: response.ok, actual: `${describe(response)} · ${Date.now() - started} ms` };
    },
  },
  {
    title: 'GET /api/me (Bearer): TYPE_CUSTOMER var, üyelik yok',
    expected: '200, TYPE_CUSTOMER, MEMBERSHIP_* yok',
    run: async () => {
      const response = await withToken(ctx.freshTokens.accessToken)('GET', '/api/me');
      const authorities = response.data?.authorities ?? [];
      const pass = response.ok && authorities.includes('TYPE_CUSTOMER')
        && !authorities.some((a) => a.startsWith('MEMBERSHIP_'));
      return { pass, actual: `${describe(response)} · ${authorities.join(', ')}` };
    },
  },
  {
    title: 'Refresh token\'ı access token gibi göndermek',
    expected: '401 TOKEN_INVALID',
    run: async () => expectResponse(await withToken(ctx.freshTokens.refreshToken)('GET', '/api/me'), 401,
      'TOKEN_INVALID'),
  },
  {
    title: 'Payload\'ına TYPE_ADMIN eklenmiş token (imza tutmaz)',
    expected: '401 TOKEN_INVALID',
    run: async () => {
      const [header, , signature] = ctx.freshTokens.accessToken.split('.');
      const claims = decodeJwt(ctx.freshTokens.accessToken).payload;
      claims.authorities = [...claims.authorities, 'TYPE_ADMIN', 'ROLE_SUPER_ADMIN'];
      const forged = `${header}.${base64UrlEncode(JSON.stringify(claims))}.${signature}`;
      return expectResponse(await withToken(forged)('GET', '/api/admin/dashboard'), 401, 'TOKEN_INVALID');
    },
  },
  {
    title: 'Token\'sız istek korumalı uç noktaya',
    expected: '401 AUTHENTICATION_REQUIRED',
    run: async () => expectResponse(await anonymous('GET', '/api/me'), 401, 'AUTHENTICATION_REQUIRED'),
  },
  {
    title: 'Müşteri admin dashboard\'una',
    expected: '403 ACCESS_DENIED',
    run: async () => expectResponse(await withToken(ctx.freshTokens.accessToken)('GET', '/api/admin/dashboard'),
      403, 'ACCESS_DENIED'),
  },
  {
    title: 'Müşteri başka bir müşterinin adını değiştirmeye çalışır',
    expected: '403 ACCESS_DENIED',
    run: async () => expectResponse(await withToken(ctx.freshTokens.accessToken)('PUT',
      `/api/customers/${ctx.fresh.id === 1 ? 2 : 1}/name`, { firstName: 'Hacker', lastName: 'Deneme' }), 403,
      'ACCESS_DENIED'),
  },
  {
    title: 'Müşteri kendi adını değiştirir',
    expected: '204',
    run: async () => expectResponse(await withToken(ctx.freshTokens.accessToken)('PUT',
      `/api/customers/${ctx.fresh.id}/name`, { firstName: 'Yeni', lastName: 'Ad' }), 204),
  },
  {
    title: 'GOLD üye (ayse) SILVER listesini görür (RoleHierarchy)',
    expected: '200',
    run: async () => {
      ctx.ayse = await loginOrFail('ayse@example.com');
      return expectResponse(await withToken(ctx.ayse.accessToken)('GET', '/api/content/tiers/SILVER/articles'), 200);
    },
  },
  {
    title: 'GOLD üye premium listeyi görür (PREMIUM_READER rolünden gelen izin)',
    expected: '200',
    run: async () => expectResponse(await withToken(ctx.ayse.accessToken)('GET', '/api/content/premium/articles'), 200),
  },
  {
    title: 'SILVER üye (mehmet) GOLD makaleyi açar (domain kuralı)',
    expected: '403 ARTICLE_REQUIRES_HIGHER_TIER',
    run: async () => {
      ctx.mehmet = await loginOrFail('mehmet@example.com');
      return expectResponse(await withToken(ctx.mehmet.accessToken)('GET', '/api/content/articles/3'), 403,
        'ARTICLE_REQUIRES_HIGHER_TIER');
    },
  },
  {
    title: 'SILVER üye GOLD seviye listesine (@PreAuthorize)',
    expected: '403 ACCESS_DENIED',
    run: async () => expectResponse(await withToken(ctx.mehmet.accessToken)('GET', '/api/content/tiers/GOLD/articles'),
      403, 'ACCESS_DENIED'),
  },
  {
    title: 'SILVER üye premium listeye (izni yok)',
    expected: '403 ACCESS_DENIED',
    run: async () => expectResponse(await withToken(ctx.mehmet.accessToken)('GET', '/api/content/premium/articles'),
      403, 'ACCESS_DENIED'),
  },
  {
    title: 'Üyeliği bitmiş hesap (zeynep): yetki yok, indirim 0',
    expected: '200, MEMBERSHIP_* yok, discountPercent 0',
    run: async () => {
      const zeynep = await loginOrFail('zeynep@example.com');
      const call = withToken(zeynep.accessToken);
      const meResponse = await call('GET', '/api/me');
      const discount = await call('GET', '/api/content/discount');
      const noMembership = !(meResponse.data?.authorities ?? []).some((a) => a.startsWith('MEMBERSHIP_'));
      const pass = meResponse.ok && noMembership && discount.ok && Number(discount.data.discountPercent) === 0;
      return { pass, actual: `${describe(meResponse)} · indirim ${discount.data?.discountPercent}` };
    },
  },
  {
    title: 'Admin token\'ı müşteri profili uç noktasına (TYPE_CUSTOMER gerekir)',
    expected: '403 ACCESS_DENIED',
    run: async () => {
      ctx.admin = await loginOrFail('admin@example.com', DEMO_PASSWORD, 'ADMIN');
      return expectResponse(await withToken(ctx.admin.accessToken)('GET', '/api/customer/profile'), 403,
        'ACCESS_DENIED');
    },
  },
  {
    title: 'Yalnızca customer:read olan admin (destek@) listeler',
    expected: '200',
    run: async () => {
      ctx.support = await loginOrFail('destek@example.com', DEMO_PASSWORD, 'ADMIN');
      return expectResponse(await withToken(ctx.support.accessToken)('GET', '/api/admin/customers?size=5'), 200);
    },
  },
  {
    title: 'customer:write olmadan müşteri devre dışı bırakmak (destek@)',
    expected: '403 ACCESS_DENIED',
    run: async () => expectResponse(await withToken(ctx.support.accessToken)('POST',
      `/api/admin/customers/${ctx.fresh.id}/disable`), 403, 'ACCESS_DENIED'),
  },
  {
    title: 'Sıralama alanı beyaz listede değil (sort=password)',
    expected: '400 MALFORMED_REQUEST',
    run: async () => expectResponse(await withToken(ctx.admin.accessToken)('GET', '/api/admin/customers?sort=password'),
      400, 'MALFORMED_REQUEST'),
  },
  {
    title: 'Sayfa boyu üst sınırın üstünde (size=500)',
    expected: '400 VALIDATION_FAILED',
    run: async () => expectResponse(await withToken(ctx.admin.accessToken)('GET', '/api/admin/customers?size=500'),
      400, 'VALIDATION_FAILED'),
  },
  {
    title: 'Standart admin başka adminin oturumlarına',
    expected: '403 ACCESS_DENIED',
    run: async () => {
      ctx.superAdmin = await loginOrFail('super@example.com', DEMO_PASSWORD, 'ADMIN');
      ctx.superAdminId = await accountIdOf(ctx.superAdmin);
      return expectResponse(await withToken(ctx.admin.accessToken)('GET',
        `/api/admin/admins/${ctx.superAdminId}/sessions`), 403, 'ACCESS_DENIED');
    },
  },
  {
    title: 'Müşteri oturum URL\'si admin hesabına ulaşmaz (ortak id alanı, tip kontrolü)',
    expected: '404 ACCOUNT_NOT_FOUND',
    run: async () => expectResponse(await withToken(ctx.admin.accessToken)('GET',
      `/api/admin/customers/${ctx.superAdminId}/sessions`), 404, 'ACCOUNT_NOT_FOUND'),
  },
  {
    title: 'SUPER_ADMIN: hasRole(ADMIN) uç noktası (hiyerarşi) ve admin oturumları',
    expected: '200 · 200',
    run: async () => {
      const call = withToken(ctx.superAdmin.accessToken);
      const adminId = await accountIdOf(ctx.admin);
      return all(expectResponse(await call('GET', '/api/admin/dashboard'), 200),
        expectResponse(await call('GET', `/api/admin/admins/${adminId}/sessions`), 200));
    },
  },
  {
    title: 'Refresh rotation; eski token hemen tekrar gelir (paralel sekme toleransı)',
    expected: '200 · 409 REFRESH_IN_PROGRESS',
    run: async () => {
      ctx.rotation = await loginOrFail(ctx.fresh.email, ctx.fresh.password);
      const rotated = await refresh(ctx.rotation.refreshToken);
      ctx.rotated = rotated.data;
      const reused = await refresh(ctx.rotation.refreshToken);
      return all(expectResponse(rotated, 200), expectResponse(reused, 409, 'REFRESH_IN_PROGRESS'));
    },
  },
  {
    title: 'Tolerans geçtikten sonra eski token: yeniden kullanım tespiti',
    expected: '401 REFRESH_TOKEN_INVALID',
    run: async () => {
      await sleep(GRACE_PERIOD_MS + 1000);
      return expectResponse(await refresh(ctx.rotation.refreshToken), 401, 'REFRESH_TOKEN_INVALID');
    },
  },
  {
    title: 'Tespitten sonra güncel refresh token da, access token da geçersiz',
    expected: '401 REFRESH_TOKEN_INVALID · 401 TOKEN_REVOKED',
    run: async () => all(
      expectResponse(await refresh(ctx.rotated.refreshToken), 401, 'REFRESH_TOKEN_INVALID'),
      expectResponse(await withToken(ctx.rotated.accessToken)('GET', '/api/me'), 401, 'TOKEN_REVOKED')),
  },
  {
    title: 'Mobil çıkış: access token iptal, refresh token geçersiz',
    expected: '204 · 401 TOKEN_REVOKED · 401 REFRESH_TOKEN_INVALID',
    run: async () => {
      const session = await loginOrFail(ctx.fresh.email, ctx.fresh.password);
      const logout = await request('POST', '/api/auth/mobile/logout', {
        auth: 'bearer', token: session.accessToken, body: { refreshToken: session.refreshToken }, label,
      });
      return all(expectResponse(logout, 204),
        expectResponse(await withToken(session.accessToken)('GET', '/api/me'), 401, 'TOKEN_REVOKED'),
        expectResponse(await refresh(session.refreshToken), 401, 'REFRESH_TOKEN_INVALID'));
    },
  },
  {
    title: 'Tüm cihazlardan çıkış: iki oturumun token\'ları birlikte düşer',
    expected: '204 · 401 TOKEN_REVOKED · 401 SESSION_ENDED',
    run: async () => {
      const first = await loginOrFail(ctx.fresh.email, ctx.fresh.password);
      const second = await loginOrFail(ctx.fresh.email, ctx.fresh.password);
      const logoutAll = await withToken(first.accessToken)('POST', '/api/auth/logout-all');
      return all(expectResponse(logoutAll, 204),
        expectResponse(await withToken(second.accessToken)('GET', '/api/me'), 401, 'TOKEN_REVOKED'),
        expectResponse(await refresh(second.refreshToken), 401, 'SESSION_ENDED'));
    },
  },
  {
    title: 'Ban: admin devre dışı bırakır → token, refresh ve giriş reddedilir; etkinleştirince giriş açılır',
    expected: '204 · 401 TOKEN_REVOKED · 401 SESSION_ENDED · 401 · 204 · 200',
    run: async () => {
      const victim = await loginOrFail(ctx.fresh.email, ctx.fresh.password);
      const adminCall = withToken(ctx.admin.accessToken);
      const disable = await adminCall('POST', `/api/admin/customers/${ctx.fresh.id}/disable`);
      const checks = [
        expectResponse(disable, 204),
        expectResponse(await withToken(victim.accessToken)('GET', '/api/me'), 401, 'TOKEN_REVOKED'),
        expectResponse(await refresh(victim.refreshToken), 401, 'SESSION_ENDED'),
        expectResponse(await login(ctx.fresh.email, ctx.fresh.password), 401, 'INVALID_CREDENTIALS'),
        expectResponse(await adminCall('POST', `/api/admin/customers/${ctx.fresh.id}/enable`), 204),
        expectResponse(await login(ctx.fresh.email, ctx.fresh.password), 200),
      ];
      return all(...checks);
    },
  },
  {
    title: 'Şifre değişikliği: eski token iptal, eski şifre geçmez, yeni şifre geçer',
    expected: '204 · 401 TOKEN_REVOKED · 401 · 200',
    run: async () => {
      const session = await loginOrFail(ctx.fresh.email, ctx.fresh.password);
      const newPassword = `Yeni-${randomSuffix()}!`;
      const change = await withToken(session.accessToken)('POST', '/api/account/password',
        { currentPassword: ctx.fresh.password, newPassword });
      const oldPassword = ctx.fresh.password;
      if (change.ok) ctx.fresh.password = newPassword;
      return all(expectResponse(change, 204),
        expectResponse(await withToken(session.accessToken)('GET', '/api/me'), 401, 'TOKEN_REVOKED'),
        expectResponse(await login(ctx.fresh.email, oldPassword), 401, 'INVALID_CREDENTIALS'),
        expectResponse(await login(ctx.fresh.email, newPassword), 200));
    },
  },
  {
    title: 'CSRF: X-XSRF-TOKEN olmadan durum değiştiren istek',
    expected: '403 CSRF_TOKEN_INVALID',
    run: async () => expectResponse(await anonymous('POST', '/api/auth/register',
      { email: `csrf-${randomSuffix()}@example.com`, password: 'GecerliSifre123!', firstName: 'A', lastName: 'B' }),
    403, 'CSRF_TOKEN_INVALID'),
  },
  {
    title: 'Kısa şifreyle kayıt (domain kuralı)',
    expected: '400 PASSWORD_TOO_SHORT',
    run: async () => expectResponse(await withCsrf('POST', '/api/auth/register',
      { email: `kisa-${randomSuffix()}@example.com`, password: 'kisa1234', firstName: 'A', lastName: 'B' }),
    400, 'PASSWORD_TOO_SHORT'),
  },
  {
    title: '72 bayttan uzun şifre (37 × "ğ" = 74 bayt, 37 karakter)',
    expected: '400 PASSWORD_TOO_LONG',
    run: async () => expectResponse(await withCsrf('POST', '/api/auth/register',
      { email: `uzun-${randomSuffix()}@example.com`, password: 'ğ'.repeat(37), firstName: 'A', lastName: 'B' }),
    400, 'PASSWORD_TOO_LONG'),
  },
  {
    title: 'Aynı e-posta, büyük harfle (Locale.ROOT ile küçültülür)',
    expected: '409 EMAIL_ALREADY_REGISTERED',
    run: async () => expectResponse(await withCsrf('POST', '/api/auth/register',
      { email: ctx.fresh.email.toUpperCase(), password: 'GecerliSifre123!', firstName: 'A', lastName: 'B' }),
    409, 'EMAIL_ALREADY_REGISTERED'),
  },
  {
    title: `Kaba kuvvet: ${LOGIN_FAILURES_BEFORE_BLOCK} hatalı denemeden sonra doğru şifre de reddedilir`,
    expected: `${LOGIN_FAILURES_BEFORE_BLOCK} × 401 · 429 TOO_MANY_LOGIN_ATTEMPTS`,
    run: async () => {
      const target = await registerFresh('kaba-kuvvet');
      const failures = [];
      for (let i = 0; i < LOGIN_FAILURES_BEFORE_BLOCK; i += 1) {
        failures.push(expectResponse(await login(target.email, 'yanlis-sifre-123'), 401, 'INVALID_CREDENTIALS'));
      }
      const blocked = expectResponse(await login(target.email, target.password), 429, 'TOO_MANY_LOGIN_ATTEMPTS');
      const failuresOk = failures.every((f) => f.pass);
      return { pass: failuresOk && blocked.pass, actual: `${failuresOk ? '5 × 401' : 'hata'} · ${blocked.actual}` };
    },
  },
];

function renderRows() {
  const body = clear($('#scenario-rows'));
  scenarios.forEach((scenario, index) => {
    scenario.row = h('tr', {},
      h('td', {}, String(index + 1)),
      h('td', {}, scenario.title),
      h('td', { class: 'scenario-actual' }, scenario.expected),
      h('td', { class: 'scenario-actual' }, '—'),
      h('td', { class: 'scenario-status' }, h('span', { class: 'badge' }, 'bekliyor')));
    body.append(scenario.row);
  });
}

function setRow(scenario, state, actual) {
  const cells = scenario.row.children;
  cells[3].textContent = actual ?? '—';
  const badge = { running: ['info', 'çalışıyor'], pass: ['ok', 'GEÇTİ'], fail: ['err', 'KALDI'], skip: ['', 'atlandı'] }[state];
  clear(cells[4]).append(h('span', { class: `badge ${badge[0]}` }, badge[1]));
}

onAction('run-all', async () => {
  for (const key of Object.keys(ctx)) delete ctx[key];
  renderRows();
  let passed = 0;
  let setupFailed = false;
  for (const scenario of scenarios) {
    if (setupFailed) {
      setRow(scenario, 'skip', 'test hesabı açılamadı');
      continue;
    }
    setRow(scenario, 'running');
    try {
      const result = await scenario.run();
      setRow(scenario, result.pass ? 'pass' : 'fail', result.actual);
      if (result.pass) passed += 1;
    } catch (error) {
      setRow(scenario, 'fail', String(error.message ?? error));
      if (scenario === scenarios[0]) setupFailed = true;
    }
    $('#summary').textContent = `${passed} / ${scenarios.length} geçti`;
  }
  $('#summary').textContent = `${passed} / ${scenarios.length} geçti`;
});

renderRows();
loadSession();
