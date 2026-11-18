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

/** Ends leftover PENDING/ACTIVE/PENDING_EXTENSION grants so support scenarios stay idempotent. */
async function clearLiveSupportGrants(agentTokens, customerTokens) {
  const live = new Set(['PENDING', 'ACTIVE', 'PENDING_EXTENSION']);
  const mine = await withToken(agentTokens.accessToken)('GET', '/api/support/grants/mine');
  for (const g of mine.ok ? mine.data ?? [] : []) {
    if (!live.has(g.status)) continue;
    await withToken(agentTokens.accessToken)('POST', `/api/support/grants/${g.id}/end`);
  }
  const incoming = await withToken(customerTokens.accessToken)('GET', '/api/support/grants/incoming');
  for (const g of incoming.ok ? incoming.data ?? [] : []) {
    if (!live.has(g.status)) continue;
    await withToken(customerTokens.accessToken)('POST', `/api/support/grants/${g.id}/end`);
  }
}

async function requestSupportGrant(agentTokens, customerId) {
  const response = await withToken(agentTokens.accessToken)('POST', '/api/support/grants', { customerId });
  if (response.status === 409 && response.code === 'SUPPORT_GRANT_CONFLICT') {
    await clearLiveSupportGrants(agentTokens, ctx.ayse);
    return withToken(agentTokens.accessToken)('POST', '/api/support/grants', { customerId });
  }
  return response;
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
      ctx.ayseId = await accountIdOf(ctx.ayse);
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
    title: 'İzin kataloğu (permissions tablosu) admin için okunur',
    expected: '200 + customer:read satırı',
    run: async () => {
      const response = await withToken(ctx.admin.accessToken)('GET', '/api/admin/permissions');
      const status = expectResponse(response, 200);
      if (!status.pass) return status;
      const names = (response.data ?? []).map((row) => row.name);
      if (!names.includes('customer:read')) {
        return { pass: false, actual: `katalogda customer:read yok: ${names.join(', ')}` };
      }
      return { pass: true, actual: `200 · ${names.length} permission` };
    },
  },
  {
    title: 'Müşteri izin kataloğuna erişemez',
    expected: '403 ACCESS_DENIED',
    run: async () => expectResponse(await withToken(ctx.ayse.accessToken)('GET', '/api/admin/permissions'),
      403, 'ACCESS_DENIED'),
  },
  {
    title: 'SUPPORT (destek@) admin paneline giremez',
    expected: '403 ACCESS_DENIED',
    run: async () => {
      ctx.supportAgent = await loginOrFail('destek@example.com', DEMO_PASSWORD, 'SUPPORT');
      ctx.ayseId = ctx.ayseId ?? await accountIdOf(ctx.ayse);
      await clearLiveSupportGrants(ctx.supportAgent, ctx.ayse);
      return expectResponse(await withToken(ctx.supportAgent.accessToken)('GET', '/api/admin/customers?size=5'),
        403, 'ACCESS_DENIED');
    },
  },
  {
    title: 'Müşteri destek grant talep edemez',
    expected: '403 ACCESS_DENIED',
    run: async () => expectResponse(await withToken(ctx.ayse.accessToken)('POST', '/api/support/grants',
      { customerId: ctx.ayseId }), 403, 'ACCESS_DENIED'),
  },
  {
    title: 'Destek grant: talep → incoming → red',
    expected: '201 PENDING → 200 DENIED',
    run: async () => {
      const requested = await requestSupportGrant(ctx.supportAgent, ctx.ayseId);
      if (requested.status !== 201) return expectResponse(requested, 201);
      ctx.deniedGrantId = requested.data.id;
      const incoming = await withToken(ctx.ayse.accessToken)('GET', '/api/support/grants/incoming');
      const inOk = expectResponse(incoming, 200);
      if (!inOk.pass) return inOk;
      const listed = (incoming.data ?? []).some((g) => g.id === ctx.deniedGrantId && g.status === 'PENDING');
      if (!listed) return { pass: false, actual: 'incoming’de PENDING yok' };
      const denied = await withToken(ctx.ayse.accessToken)('POST',
        `/api/support/grants/${ctx.deniedGrantId}/deny`);
      const dOk = expectResponse(denied, 200);
      if (!dOk.pass) return dOk;
      return denied.data.status === 'DENIED'
        ? { pass: true, actual: `DENIED #${ctx.deniedGrantId}` }
        : { pass: false, actual: `status=${denied.data.status}` };
    },
  },
  {
    title: 'Destek grant: talep → onay → claim → act-as profil',
    expected: 'ACTIVE + 200 profile',
    run: async () => {
      const requested = await requestSupportGrant(ctx.supportAgent, ctx.ayseId);
      if (requested.status !== 201) return expectResponse(requested, 201);
      ctx.grantId = requested.data.id;
      const approved = await withToken(ctx.ayse.accessToken)('POST',
        `/api/support/grants/${ctx.grantId}/approve`);
      const aOk = expectResponse(approved, 200);
      if (!aOk.pass) return aOk;
      if (approved.data.status !== 'ACTIVE') {
        return { pass: false, actual: `status=${approved.data.status}` };
      }
      const claimed = await withToken(ctx.supportAgent.accessToken)('POST',
        `/api/support/grants/${ctx.grantId}/claim`);
      const cOk = expectResponse(claimed, 200);
      if (!cOk.pass) return cOk;
      ctx.supportAccessToken = claimed.data?.supportAccessToken;
      if (!ctx.supportAccessToken) {
        return { pass: false, actual: `supportAccessToken yok · keys=${Object.keys(claimed.data ?? {})}` };
      }
      return expectResponse(await withToken(ctx.supportAccessToken)('GET', '/api/customer/profile'), 200);
    },
  },
  {
    title: 'Act-as + support:full: müşteri adına şifre değişimi',
    expected: '204; şifre geri alınır; claim yenilenir',
    run: async () => {
      const changed = await withToken(ctx.supportAccessToken)('POST', '/api/account/password',
        { currentPassword: DEMO_PASSWORD, newPassword: 'YeniSifre-999!' });
      const cOk = expectResponse(changed, 204);
      if (!cOk.pass) return cOk;
      const ayseNew = await loginOrFail('ayse@example.com', 'YeniSifre-999!');
      const restored = await withToken(ayseNew.accessToken)('POST', '/api/account/password',
        { currentPassword: 'YeniSifre-999!', newPassword: DEMO_PASSWORD });
      const rOk = expectResponse(restored, 204);
      if (!rOk.pass) return rOk;
      ctx.ayse = await loginOrFail('ayse@example.com');
      ctx.supportAgent = await loginOrFail('destek@example.com', DEMO_PASSWORD, 'SUPPORT');
      const claimed = await withToken(ctx.supportAgent.accessToken)('POST',
        `/api/support/grants/${ctx.grantId}/claim`);
      const claimOk = expectResponse(claimed, 200);
      if (!claimOk.pass) return claimOk;
      ctx.supportAccessToken = claimed.data?.supportAccessToken;
      if (!ctx.supportAccessToken) return { pass: false, actual: 'şifre sonrası claim token yok' };
      return { pass: true, actual: '204 + restore + claim' };
    },
  },
  {
    title: 'Act-as leave: kendi hesabına dön, grant açık, yeniden claim',
    expected: 'leave 200 active=false; claim sonrası profil 200',
    run: async () => {
      const left = await withToken(ctx.supportAgent.accessToken)('POST', '/api/support/session/leave');
      const lOk = expectResponse(left, 200);
      if (!lOk.pass) return lOk;
      if (left.data?.active) return { pass: false, actual: 'leave sonrası session hâlâ active' };
      const stillOpen = await withToken(ctx.supportAgent.accessToken)('GET',
        `/api/support/grants/${ctx.grantId}`);
      const gOk = expectResponse(stillOpen, 200);
      if (!gOk.pass) return gOk;
      if (stillOpen.data.status !== 'ACTIVE' && stillOpen.data.status !== 'PENDING_EXTENSION') {
        return { pass: false, actual: `grant kapandı: ${stillOpen.data.status}` };
      }
      const claimed = await withToken(ctx.supportAgent.accessToken)('POST',
        `/api/support/grants/${ctx.grantId}/claim`);
      const cOk = expectResponse(claimed, 200);
      if (!cOk.pass) return cOk;
      ctx.supportAccessToken = claimed.data?.supportAccessToken;
      if (!ctx.supportAccessToken) return { pass: false, actual: 'yeniden claim token yok' };
      return expectResponse(await withToken(ctx.supportAccessToken)('GET', '/api/customer/profile'), 200);
    },
  },
  {
    title: 'Destek uzatma: iste → müşteri onay → yeniden claim',
    expected: 'PENDING_EXTENSION → ACTIVE + yeni token',
    run: async () => {
      const ext = await withToken(ctx.supportAgent.accessToken)('POST',
        `/api/support/grants/${ctx.grantId}/extensions`);
      if (ext.status !== 201) return expectResponse(ext, 201);
      if (ext.data.status !== 'PENDING_EXTENSION') {
        return { pass: false, actual: `status=${ext.data.status}` };
      }
      const pending = (ext.data.extensions ?? []).find((e) => e.decision === 'PENDING');
      if (!pending) return { pass: false, actual: 'PENDING extension yok' };
      ctx.extensionId = pending.id;
      const approved = await withToken(ctx.ayse.accessToken)('POST',
        `/api/support/grants/${ctx.grantId}/extensions/${ctx.extensionId}/approve`);
      const aOk = expectResponse(approved, 200);
      if (!aOk.pass) return aOk;
      if (approved.data.status !== 'ACTIVE' || approved.data.extensionCount < 1) {
        return { pass: false, actual: `status=${approved.data.status} ext=${approved.data.extensionCount}` };
      }
      const claimed = await withToken(ctx.supportAgent.accessToken)('POST',
        `/api/support/grants/${ctx.grantId}/claim`);
      const cOk = expectResponse(claimed, 200);
      if (!cOk.pass) return cOk;
      ctx.supportAccessToken = claimed.data?.supportAccessToken;
      return ctx.supportAccessToken
        ? { pass: true, actual: `uzatma #${ctx.extensionId} + yeni token` }
        : { pass: false, actual: 'yeni supportAccessToken yok' };
    },
  },
  {
    title: 'Destek uzatma reddi yolu (ayrı grant) + history',
    expected: 'extension DENIED → grant ACTIVE; history dolu',
    run: async () => {
      const ended = await withToken(ctx.supportAgent.accessToken)('POST',
        `/api/support/grants/${ctx.grantId}/end`);
      const eOk = expectResponse(ended, 200);
      if (!eOk.pass) return eOk;
      const requested = await requestSupportGrant(ctx.supportAgent, ctx.ayseId);
      if (requested.status !== 201) return expectResponse(requested, 201);
      const grantId = requested.data.id;
      await withToken(ctx.ayse.accessToken)('POST', `/api/support/grants/${grantId}/approve`);
      const ext = await withToken(ctx.supportAgent.accessToken)('POST',
        `/api/support/grants/${grantId}/extensions`);
      if (ext.status !== 201) return expectResponse(ext, 201);
      const pending = (ext.data.extensions ?? []).find((e) => e.decision === 'PENDING');
      if (!pending) return { pass: false, actual: 'PENDING extension yok' };
      const denied = await withToken(ctx.ayse.accessToken)('POST',
        `/api/support/grants/${grantId}/extensions/${pending.id}/deny`);
      const dOk = expectResponse(denied, 200);
      if (!dOk.pass) return dOk;
      if (denied.data.status !== 'ACTIVE') {
        return { pass: false, actual: `deny sonrası status=${denied.data.status}` };
      }
      await withToken(ctx.supportAgent.accessToken)('POST', `/api/support/grants/${grantId}/end`);
      const history = await withToken(ctx.ayse.accessToken)('GET', '/api/support/grants/history');
      const hOk = expectResponse(history, 200);
      if (!hOk.pass) return hOk;
      const rows = history.data ?? [];
      const hasDenied = rows.some((g) => g.id === ctx.deniedGrantId);
      const hasEnded = rows.some((g) => g.id === ctx.grantId);
      const hasExtDeny = rows.some((g) => g.id === grantId
        && (g.extensions ?? []).some((e) => e.decision === 'DENIED'));
      return hasDenied && hasEnded && hasExtDeny
        ? { pass: true, actual: `history ${rows.length} grant` }
        : { pass: false, actual: `history eksik denied=${hasDenied} ended=${hasEnded} extDeny=${hasExtDeny}` };
    },
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
