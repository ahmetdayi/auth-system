import {
  $, api, clear, formatInstant, h, loadSession, me, onAction, request, showResult,
} from '../api.js';

let countdownTimer = null;

function renderToken() {
  const view = clear($('#token-view'));
  const current = me();
  if (!current) {
    view.append(h('p', { class: 'muted' }, 'Giriş yapılmamış.'));
    return;
  }
  const expiresAt = new Date(current.accessTokenExpiresAt);
  const remaining = h('strong', {});
  view.append(
    h('p', {}, 'Oturum id: ', h('code', {}, String(current.sessionId)), ' · bitiş: ',
      formatInstant(current.accessTokenExpiresAt), ' · kalan: ', remaining),
    h('div', { class: 'authorities' }, current.authorities.map((a) => h('span', { class: 'badge info' }, a))));
  clearInterval(countdownTimer);
  const tick = () => {
    const seconds = Math.max(0, Math.round((expiresAt.getTime() - Date.now()) / 1000));
    remaining.textContent = `${Math.floor(seconds / 60)} dk ${seconds % 60} sn`;
  };
  tick();
  countdownTimer = setInterval(tick, 1000);
}

async function loadSessions() {
  const response = await api.get('/api/sessions');
  const view = clear($('#sessions-view'));
  clear($('#sessions-result'));
  if (!response.ok) {
    view.append(h('p', { class: 'muted' }, 'Oturumlar okunamadı.'));
    showResult($('#sessions-result'), response);
    return;
  }
  if (response.data.length === 0) {
    view.append(h('p', { class: 'muted' }, 'Aktif oturum yok.'));
    return;
  }
  view.append(h('table', {},
    h('thead', {}, h('tr', {},
      ['Id', 'Cihaz', 'IP', 'Başlangıç', 'Son yenileme', 'Bitiş', ''].map((t) => h('th', {}, t)))),
    h('tbody', {}, response.data.map((session) => h('tr', {},
      h('td', {}, String(session.id), session.current ? h('span', { class: 'badge ok' }, ' bu cihaz') : null),
      h('td', { class: 'small' }, session.userAgent),
      h('td', { class: 'mono' }, session.ipAddress),
      h('td', { class: 'small' }, formatInstant(session.startedAt)),
      h('td', { class: 'small' }, formatInstant(session.lastRefreshedAt)),
      h('td', { class: 'small' }, formatInstant(session.expiresAt)),
      h('td', {}, h('button', {
        type: 'button', class: 'danger secondary small', dataset: { action: 'revoke-session', id: session.id },
      }, 'Kapat')))))));
}

async function reloadAll() {
  await loadSession();
  renderToken();
  await loadSessions();
}

onAction('load-sessions', loadSessions);

onAction('revoke-session', async (button) => {
  const response = await api.delete(`/api/sessions/${button.dataset.id}`);
  showResult($('#sessions-result'), response, `Oturum ${button.dataset.id} kapatma`);
  await loadSessions();
});

onAction('refresh-now', async () => {
  const response = await api.post('/api/auth/refresh', undefined);
  showResult($('#refresh-result'), response, 'Yeni cookie\'ler yazıldı; gövdede token yok');
  await reloadAll();
});

onAction('parallel-refresh', async () => {
  const [first, second] = await Promise.all([
    request('POST', '/api/auth/refresh', { retry: false, label: 'paralel #1' }),
    request('POST', '/api/auth/refresh', { retry: false, label: 'paralel #2' }),
  ]);
  const box = clear($('#refresh-result'));
  const a = h('div', {});
  const b = h('div', {});
  box.append(a, b);
  showResult(a, first, 'Paralel istek #1');
  showResult(b, second, 'Paralel istek #2');
  await reloadAll();
});

onAction('logout', async () => {
  const response = await api.post('/api/auth/logout', undefined);
  showResult($('#logout-result'), response);
  await reloadAll();
});

onAction('logout-all', async () => {
  const response = await api.post('/api/auth/logout-all', undefined);
  showResult($('#logout-result'), response);
  await reloadAll();
});

onAction('logout-without-csrf', async () => {
  const response = await api.post('/api/auth/logout', undefined, { csrf: false });
  showResult($('#logout-result'), response, 'X-XSRF-TOKEN gönderilmedi: başka bir site de böyle gönderirdi');
});

reloadAll();
