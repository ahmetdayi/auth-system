// API client of the test console. Every page imports it; nothing here uses innerHTML with server data,
// so values returned by the API are always rendered as text.

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);
// AUTHENTICATION_REQUIRED too: the access cookie's Max-Age equals the token lifetime, so the browser usually
// deletes the cookie when the token expires and the next request carries no token at all.
const REFRESHABLE_CODES = new Set(['TOKEN_EXPIRED', 'TOKEN_REVOKED', 'AUTHENTICATION_REQUIRED']);
const MAX_LOG_ENTRIES = 60;

// ---------------------------------------------------------------------------------------------- DOM helpers

/** Creates an element. Children that are strings become text nodes, never HTML. */
export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key === 'dataset') Object.assign(el.dataset, value);
    else if (key === 'text') el.textContent = value;
    else el.setAttribute(key, value === true ? '' : String(value));
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

export function $(selector, root = document) {
  return root.querySelector(selector);
}

export function clear(el) {
  while (el.firstChild) el.removeChild(el.firstChild);
  return el;
}

export function json(value) {
  return h('pre', { class: 'json' }, value === undefined ? '' : JSON.stringify(value, null, 2));
}

export function formatInstant(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('tr-TR');
}

export function statusBadge(status) {
  const cls = status >= 200 && status < 300 ? 'ok' : status >= 500 || status === 0 ? 'err' : 'warn';
  return h('span', { class: `badge ${cls}` }, status === 0 ? 'AĞ HATASI' : String(status));
}

/** Shows a response in a result box: status, error code and the JSON body. */
export function showResult(container, response, title) {
  clear(container);
  const head = h('div', { class: 'result-head' },
    statusBadge(response.status),
    response.code ? h('span', { class: 'badge info' }, response.code) : null,
    title ? h('span', { class: 'muted' }, title) : null);
  container.append(h('div', { class: 'result' }, head,
    response.data !== null && response.data !== undefined ? json(response.data) : null));
}

/** Reads a form into a plain object; empty strings become null. */
export function formValues(form) {
  const values = {};
  for (const [key, value] of new FormData(form).entries()) {
    values[key] = typeof value === 'string' && value.trim() === '' ? null : value;
  }
  return values;
}

/** Click delegation for [data-action] buttons (no inline handlers: the CSP forbids them). */
const actions = new Map();
export function onAction(name, handler) {
  actions.set(name, handler);
}
document.addEventListener('click', async (event) => {
  const target = event.target.closest('[data-action]');
  if (!target || !actions.has(target.dataset.action)) return;
  event.preventDefault();
  if (target.disabled) return;
  target.disabled = true;
  try {
    await actions.get(target.dataset.action)(target, event);
  } catch (error) {
    console.error(error);
  } finally {
    target.disabled = false;
  }
});

/** Submit handler that prevents the default and disables the submit button while running. */
export function onSubmit(form, handler) {
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const button = form.querySelector('[type=submit]');
    if (button) button.disabled = true;
    try {
      await handler(formValues(form), form);
    } catch (error) {
      console.error(error);
    } finally {
      if (button) button.disabled = false;
    }
  });
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// --------------------------------------------------------------------------------------------- HTTP client

export function readCookie(name) {
  const prefix = `${name}=`;
  for (const part of document.cookie.split(';')) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) return decodeURIComponent(cookie.substring(prefix.length));
  }
  return null;
}

let refreshInFlight = null;
// Set when the server said there is no refresh cookie: no point asking again until the next login.
let noRefreshCookie = false;

/**
 * Browser refresh. Single-flight: parallel 401s on one page share one refresh request. A 409 means another
 * tab has just rotated the cookies; they are already updated, so it counts as success.
 */
export function refreshBrowserSession() {
  if (noRefreshCookie) return Promise.resolve(false);
  if (!refreshInFlight) {
    refreshInFlight = request('POST', '/api/auth/refresh', { retry: false })
      .then(async (response) => {
        if (response.code === 'REFRESH_TOKEN_MISSING') noRefreshCookie = true;
        if (response.code === 'REFRESH_IN_PROGRESS') {
          // The other request's answer (with the new cookies) may still be on its way to the browser.
          await sleep(400);
          return true;
        }
        return response.ok;
      })
      .finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

/**
 * Sends a request and never throws for HTTP errors.
 *
 * @param {string} method
 * @param {string} url
 * @param {object} options
 *   auth:  'cookie' (default; browser cookies + X-XSRF-TOKEN), 'bearer' (Authorization header, no cookies)
 *          or 'none' (no cookies, no header: an anonymous client)
 *   token: access token for 'bearer'
 *   body:  sent as JSON
 *   csrf:  false to deliberately omit the X-XSRF-TOKEN header (to see the 403)
 *   retry: false to disable the silent refresh / 409 retry
 *   label: shown in the log
 * @returns {Promise<{status:number, ok:boolean, code:string|null, data:any, durationMs:number}>}
 */
export async function request(method, url, options = {}) {
  const { auth = 'cookie', token = null, body, csrf = true, retry = true, label = null } = options;
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth === 'bearer' && token) headers.Authorization = `Bearer ${token}`;
  if (auth === 'cookie' && csrf && !SAFE_METHODS.has(method)) {
    const xsrf = readCookie('XSRF-TOKEN');
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
  }

  const started = performance.now();
  let response;
  try {
    const raw = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: auth === 'cookie' ? 'same-origin' : 'omit',
      cache: 'no-store',
      redirect: 'manual',
    });
    const text = await raw.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = text; }
    }
    response = {
      status: raw.status,
      ok: raw.ok,
      code: data && typeof data === 'object' && !Array.isArray(data) ? data.code ?? null : null,
      data,
      durationMs: Math.round(performance.now() - started),
    };
  } catch (error) {
    response = { status: 0, ok: false, code: 'NETWORK_ERROR', data: String(error), durationMs: 0 };
  }
  log({ method, url, auth, body, response, label });
  if (auth === 'cookie' && url === '/api/auth/login' && response.ok) noRefreshCookie = false;

  if (!retry || auth !== 'cookie' || url.startsWith('/api/auth/')) return response;

  // Access token expired or was revoked: refresh once silently, then repeat the original request.
  if (response.status === 401 && REFRESHABLE_CODES.has(response.code)) {
    if (await refreshBrowserSession()) {
      return request(method, url, { ...options, retry: false });
    }
    updateSessionStatus({ status: 401 });
  }
  return response;
}

export const api = {
  get: (url, options) => request('GET', url, options),
  post: (url, body, options = {}) => request('POST', url, { ...options, body }),
  put: (url, body, options = {}) => request('PUT', url, { ...options, body }),
  delete: (url, options) => request('DELETE', url, options),
};

// ------------------------------------------------------------------------------------------------- Log

const SECRET_KEYS = /password/i;
const TOKEN_KEYS = /token/i;

function maskForLog(value) {
  if (Array.isArray(value)) return value.map(maskForLog);
  if (value && typeof value === 'object') {
    const masked = {};
    for (const [key, inner] of Object.entries(value)) {
      if (SECRET_KEYS.test(key) && typeof inner === 'string') masked[key] = '••••••';
      else if (TOKEN_KEYS.test(key) && typeof inner === 'string' && inner.length > 24) masked[key] = `${inner.slice(0, 16)}…`;
      else masked[key] = maskForLog(inner);
    }
    return masked;
  }
  return value;
}

function log({ method, url, auth, body, response, label }) {
  const container = $('#log-entries');
  if (!container) return;
  $('#log-empty')?.remove();
  const modeText = auth === 'bearer' ? 'Bearer' : auth === 'none' ? 'anonim' : 'cookie';
  const entry = h('details', { class: 'log-entry' },
    h('summary', {},
      h('span', { class: 'time' }, new Date().toLocaleTimeString('tr-TR')),
      h('span', { class: 'method' }, method),
      h('span', { class: 'url' }, url),
      label ? h('span', { class: 'muted' }, label) : null,
      h('span', { class: 'badge' }, modeText),
      statusBadge(response.status),
      response.code ? h('span', { class: 'badge info' }, response.code) : null,
      h('span', { class: 'muted' }, `${response.durationMs} ms`)),
    h('div', { class: 'detail' },
      body !== undefined ? h('div', {}, h('div', { class: 'muted small' }, 'İstek gövdesi'), json(maskForLog(body))) : null,
      h('div', {}, h('div', { class: 'muted small' }, 'Yanıt'), json(maskForLog(response.data)))));
  container.prepend(entry);
  while (container.children.length > MAX_LOG_ENTRIES) container.lastElementChild.remove();
}

onAction('clear-log', () => {
  const container = $('#log-entries');
  if (container) clear(container).append(h('p', { class: 'muted small', id: 'log-empty' }, 'Henüz istek yok.'));
});

// -------------------------------------------------------------------------------------- Session indicator

let currentMe = null;

export function me() {
  return currentMe;
}

function updateSessionStatus(response) {
  const badge = $('#session-status');
  const logout = $('#header-logout');
  if (!badge) return;
  if (response.status === 200 && response.data) {
    currentMe = response.data;
    badge.className = 'badge ok';
    badge.textContent = `Tarayıcı: ${response.data.email} (${response.data.accountType})`;
    logout?.classList.remove('hidden');
  } else {
    currentMe = null;
    badge.className = 'badge';
    badge.textContent = 'Tarayıcı: giriş yapılmamış';
    logout?.classList.add('hidden');
  }
}

/** Asks the server who the browser is (with a silent refresh if the access token expired). */
export async function loadSession() {
  const response = await api.get('/api/me', { label: 'oturum durumu' });
  updateSessionStatus(response);
  return response;
}

export async function browserLogout() {
  const response = await api.post('/api/auth/logout', undefined);
  updateSessionStatus({ status: 401 });
  return response;
}

onAction('header-logout', async () => {
  await browserLogout();
  window.location.href = '/console/login';
});

/** Base64URL without padding, as used in JWTs. */
export function base64UrlEncode(text) {
  let binary = '';
  for (const byte of new TextEncoder().encode(text)) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Decodes a JWT's header and payload for display. This does NOT verify anything: only the server can. */
export function decodeJwt(token) {
  try {
    const [header, payload] = token.split('.').slice(0, 2).map((part) => {
      const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
      const bytes = Uint8Array.from(atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')),
        (c) => c.charCodeAt(0));
      return JSON.parse(new TextDecoder().decode(bytes));
    });
    return { header, payload };
  } catch {
    return null;
  }
}
