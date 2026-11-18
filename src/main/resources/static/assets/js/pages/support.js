import {
  $, api, clear, countdownBadge, formatInstant, h, loadSession, onAction, onSubmit, showResult, wireCountdowns,
} from '../api.js';
import { notifyActAsChanged } from '../actas-fab.js';

function scopeBadges(scopes) {
  return h('div', { class: 'authorities' },
    (scopes || []).map((x) => h('span', { class: 'badge info' }, x)));
}

function selectedScopes() {
  return [...document.querySelectorAll('#scope-picker input[name="scopes"]:checked')].map((el) => el.value);
}

function setAllScopes(checked) {
  for (const el of document.querySelectorAll('#scope-picker input[name="scopes"]')) {
    el.checked = checked;
  }
}

async function loadScopePicker() {
  const picker = clear($('#scope-picker'));
  const response = await api.get('/api/support/scopes');
  if (!response.ok) {
    picker.append(h('p', { class: 'muted' }, 'Scope kataloğu okunamadı (SUPPORT girişi gerekir).'));
    showResult($('#request-result'), response, 'GET /api/support/scopes');
    return;
  }
  const catalog = response.data ?? [];
  if (!catalog.length) {
    picker.append(h('p', { class: 'muted' }, 'Tanımlı scope yok.'));
    return;
  }
  for (const item of catalog) {
    const id = `scope-${item.code.replace(/[^a-z0-9]+/gi, '-')}`;
    picker.append(h('label', {
      class: 'small',
      style: 'display:flex;gap:0.5rem;align-items:flex-start;margin:0.35rem 0;cursor:pointer',
    },
      h('input', { type: 'checkbox', name: 'scopes', value: item.code, id, checked: true }),
      h('span', {},
        h('strong', {}, item.label),
        ' · ',
        h('code', {}, item.code),
        h('br'),
        h('span', { class: 'muted' }, item.description))));
  }
}

async function loadSupportSession() {
  const response = await api.get('/api/support/session');
  const view = clear($('#session-view'));
  if (!response.ok) {
    showResult(view, response);
    return;
  }
  const s = response.data;
  if (!s.active) {
    view.append(h('p', { class: 'muted' },
      'Act-as cookie yok — kendi SUPPORT hesabındasın. ACTIVE grant için Claim ile müşteriye geç.'));
    return;
  }
  view.append(h('table', {}, h('tbody', {},
    h('tr', {}, h('th', {}, 'Grant'), h('td', {}, String(s.grantId))),
    h('tr', {}, h('th', {}, 'Müşteri'), h('td', {}, `#${s.customerId} · ${s.customerEmail}`)),
    h('tr', {}, h('th', {}, 'Bitiş'), h('td', {}, formatInstant(s.expiresAt))),
    h('tr', {}, h('th', {}, 'Kalan'), h('td', {}, countdownBadge(s.expiresAt))),
    h('tr', {}, h('th', {}, 'Scope'), h('td', {}, scopeBadges(s.scopes))))));
  wireCountdowns(view);
}

function extensionRows(extensions) {
  if (!extensions || extensions.length === 0) {
    return h('p', { class: 'muted small' }, 'Uzatma yok.');
  }
  return h('table', {},
    h('thead', {}, h('tr', {},
      h('th', {}, 'Id'), h('th', {}, 'Dk'), h('th', {}, 'Talep'), h('th', {}, 'Karar'), h('th', {}, 'Karar zamanı'))),
    h('tbody', {}, extensions.map((e) => h('tr', {},
      h('td', {}, String(e.id)),
      h('td', {}, String(e.requestedMinutes)),
      h('td', {}, formatInstant(e.requestedAt)),
      h('td', {}, e.decision),
      h('td', {}, formatInstant(e.decidedAt))))));
}

async function loadMine() {
  const response = await api.get('/api/support/grants/mine');
  const view = clear($('#mine-view'));
  clear($('#mine-result'));
  if (!response.ok) {
    showResult($('#mine-result'), response, 'mine');
    view.append(h('p', { class: 'muted' }, 'Liste okunamadı (SUPPORT girişi gerekir).'));
    return;
  }
  const rows = response.data;
  if (!rows.length) {
    view.append(h('p', { class: 'muted' }, 'Henüz grant yok.'));
    return;
  }
  for (const g of rows) {
    const actions = [];
    if (g.status === 'ACTIVE' || g.status === 'PENDING_EXTENSION') {
      actions.push(h('button', {
        type: 'button', class: 'small', dataset: { action: 'claim-grant', id: g.id },
      }, 'Claim cookie'));
      actions.push(h('button', {
        type: 'button', class: 'secondary small', dataset: { action: 'extend-grant', id: g.id },
      }, 'Uzatma iste'));
      actions.push(h('button', {
        type: 'button', class: 'danger secondary small', dataset: { action: 'end-grant', id: g.id },
      }, 'Bitir'));
    } else if (g.status === 'PENDING') {
      actions.push(h('span', { class: 'muted small' }, 'Müşteri onayı bekleniyor'));
    }
    view.append(h('div', { class: 'card' },
      h('div', { class: 'row' },
        h('strong', {}, `#${g.id} · ${g.status}`),
        h('span', { class: 'spacer' }),
        ...actions),
      h('p', { class: 'small' },
        `Müşteri #${g.customerId} (${g.customerEmail}) · başlangıç ${g.initialMinutes} dk · ` +
        `uzatma ${g.extensionCount} (+${g.totalApprovedExtensionMinutes} dk) · ` +
        `bitiş ${formatInstant(g.expiresAt)}` +
        (g.endReason ? ` · ${g.endReason}` : '')),
      (g.status === 'ACTIVE' || g.status === 'PENDING_EXTENSION') && g.expiresAt
        ? h('p', { class: 'small' }, 'Kalan: ', countdownBadge(g.expiresAt))
        : null,
      scopeBadges(g.scopes),
      extensionRows(g.extensions)));
  }
  wireCountdowns(view);
}

onSubmit($('#request-form'), async (values) => {
  const scopes = selectedScopes();
  if (scopes.length === 0) {
    showResult($('#request-result'), {
      status: 0, ok: false, code: 'SCOPES_REQUIRED',
      data: { detail: 'En az bir yetki seç (veya Tüm yetkiler).' },
    }, 'client');
    return;
  }
  const response = await api.post('/api/support/grants', {
    customerId: Number(values.customerId),
    scopes,
  });
  showResult($('#request-result'), response, 'POST /api/support/grants');
  if (response.ok) await loadMine();
});

onAction('scopes-all', () => setAllScopes(true));
onAction('scopes-none', () => setAllScopes(false));
onAction('load-session', loadSupportSession);
onAction('load-mine', loadMine);

onAction('leave-session', async () => {
  const response = await api.post('/api/support/session/leave');
  showResult($('#session-result'), response, 'leave (grant açık kalır)');
  await loadSupportSession();
  await loadMine();
  notifyActAsChanged();
});

onAction('claim-grant', async (btn) => {
  const response = await api.post(`/api/support/grants/${btn.dataset.id}/claim`);
  showResult($('#mine-result'), response, 'claim (cookie + body token)');
  await loadSupportSession();
  await loadMine();
  notifyActAsChanged();
});

onAction('extend-grant', async (btn) => {
  const response = await api.post(`/api/support/grants/${btn.dataset.id}/extensions`);
  showResult($('#mine-result'), response, 'extension request');
  await loadMine();
  notifyActAsChanged();
});

onAction('end-grant', async (btn) => {
  const response = await api.post(`/api/support/grants/${btn.dataset.id}/end`);
  showResult($('#mine-result'), response, 'end');
  await loadSupportSession();
  await loadMine();
  notifyActAsChanged();
});

onAction('load-acted-profile', async () => {
  const response = await api.get('/api/customer/profile');
  showResult($('#acted-profile-result'), response, 'act-as profile');
  const view = clear($('#acted-profile-view'));
  if (response.ok) {
    const p = response.data;
    view.append(h('p', {}, `${p.firstName} ${p.lastName} · #${p.id} · ${p.contactEmail}`));
  }
});

await loadSession();
await loadScopePicker();
await loadSupportSession();
await loadMine();
