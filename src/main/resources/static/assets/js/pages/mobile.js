import {
  $, base64UrlEncode, clear, decodeJwt, formatInstant, h, json, loadSession, onAction, onSubmit, request, showResult,
} from '../api.js';

// Kept in memory only, like a mobile app's secure storage would keep them.
const tokens = { access: null, refresh: null, previousRefresh: null, expiresIn: null, sessionId: null };

function bearer(method, url, body, label) {
  return request(method, url, { auth: 'bearer', token: tokens.access, body, label });
}

function anonymous(method, url, body, label) {
  return request(method, url, { auth: 'none', body, label });
}

function renderTokens() {
  const view = clear($('#tokens-view'));
  if (!tokens.access) {
    view.append(h('p', { class: 'muted' }, 'Token yok.'));
    return;
  }
  const decoded = decodeJwt(tokens.access);
  view.append(
    h('p', { class: 'small' }, `Oturum: ${tokens.sessionId} · access token ${tokens.expiresIn} sn geçerli`),
    h('label', {}, 'Access token (JWT)'),
    h('div', { class: 'token-box' }, tokens.access),
    h('label', {}, 'Refresh token (opak, JWT değil; sunucuda yalnızca SHA-256 özeti tutulur)'),
    h('div', { class: 'token-box' }, tokens.refresh),
    tokens.previousRefresh ? h('label', {}, 'Bir önceki refresh token (rotation ile emekli oldu)') : null,
    tokens.previousRefresh ? h('div', { class: 'token-box' }, tokens.previousRefresh) : null,
    h('label', {}, 'Access token içeriği (yalnızca gösterim; imzayı istemci doğrulamaz)'),
    decoded ? json({
      header: decoded.header,
      payload: {
        ...decoded.payload,
        iat: `${decoded.payload.iat} (${formatInstant(decoded.payload.iat * 1000)})`,
        exp: `${decoded.payload.exp} (${formatInstant(decoded.payload.exp * 1000)})`,
      },
    }) : h('p', {}, 'Çözülemedi.'));
}

function store(data) {
  tokens.previousRefresh = tokens.refresh;
  tokens.access = data.accessToken;
  tokens.refresh = data.refreshToken;
  tokens.expiresIn = data.expiresIn;
  tokens.sessionId = data.sessionId;
  renderTokens();
}

onSubmit($('#mobile-login-form'), async (values) => {
  const response = await anonymous('POST', '/api/auth/mobile/login', values, 'mobil giriş');
  showResult($('#mobile-login-result'), response);
  if (response.ok) {
    tokens.refresh = null;
    store(response.data);
  }
});

onAction('fill-demo', (button) => {
  const form = $('#mobile-login-form');
  form.email.value = button.dataset.email;
  form.password.value = button.dataset.password;
  form.userType.value = button.dataset.type;
});

onAction('call-me', async () => {
  showResult($('#ops-result'), await bearer('GET', '/api/me'), 'GET /api/me');
});

onAction('refresh', async () => {
  const response = await anonymous('POST', '/api/auth/mobile/refresh', { refreshToken: tokens.refresh }, 'yenile');
  showResult($('#ops-result'), response, 'Yenileme');
  if (response.ok) store(response.data);
});

onAction('reuse-old', async () => {
  if (!tokens.previousRefresh) {
    clear($('#ops-result')).append(h('p', { class: 'note warn small' }, 'Önce en az bir kez "Yenile" yap.'));
    return;
  }
  const response = await anonymous('POST', '/api/auth/mobile/refresh',
    { refreshToken: tokens.previousRefresh }, 'eski refresh token');
  showResult($('#ops-result'), response, 'Emekli refresh token tekrar gönderildi');
});

onAction('refresh-as-access', async () => {
  const response = await request('GET', '/api/me', { auth: 'bearer', token: tokens.refresh, label: 'refresh token Bearer' });
  showResult($('#ops-result'), response, 'Refresh token bir JWT değil: imza doğrulaması geçmez');
});

onAction('tampered', async () => {
  if (!tokens.access) return;
  const [header, , signature] = tokens.access.split('.');
  const claims = decodeJwt(tokens.access).payload;
  claims.authorities = [...claims.authorities, 'ROLE_SUPER_ADMIN', 'TYPE_ADMIN'];
  const forged = base64UrlEncode(JSON.stringify(claims));
  const response = await request('GET', '/api/admin/dashboard',
    { auth: 'bearer', token: `${header}.${forged}.${signature}`, label: 'değiştirilmiş token' });
  showResult($('#ops-result'), response, 'TYPE_ADMIN eklenmiş, imza eski: sunucu reddeder');
});

onAction('logout', async () => {
  const response = await request('POST', '/api/auth/mobile/logout',
    { auth: 'bearer', token: tokens.access, body: { refreshToken: tokens.refresh }, label: 'mobil çıkış' });
  showResult($('#ops-result'), response, 'Çıkış (token\'lar bellekte duruyor, tekrar deneyebilirsin)');
});

onAction('logout-all', async () => {
  showResult($('#ops-result'), await bearer('POST', '/api/auth/logout-all'), 'Tüm cihazlardan çıkış');
});

onSubmit($('#custom-form'), async (values) => {
  let body;
  if (values.body) {
    try {
      body = JSON.parse(values.body);
    } catch {
      clear($('#custom-result')).append(h('p', { class: 'note warn small' }, 'Gövde geçerli JSON değil.'));
      return;
    }
  }
  showResult($('#custom-result'), await bearer(values.method, values.path, body, 'serbest istek'));
});

renderTokens();
loadSession();
