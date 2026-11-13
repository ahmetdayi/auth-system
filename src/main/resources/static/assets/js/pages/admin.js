import {
  $, api, clear, formValues, formatInstant, h, loadSession, onAction, onSubmit, showResult,
} from '../api.js';

let currentPage = 0;
let selectedCustomerId = null;

/**
 * UI composition: the staff context knows the admin's name, department and regions; the identity context
 * (via /api/me, straight from the token) knows the e-mail, privilege level and permissions.
 */
async function loadDashboard() {
  const [profile, me] = await Promise.all([api.get('/api/admin/dashboard'), api.get('/api/me')]);
  const view = clear($('#dashboard-view'));
  if (!profile.ok) {
    showResult(view, profile);
    return;
  }
  const d = profile.data;
  const authorities = me.ok ? me.data.authorities : [];
  const level = authorities.includes('ROLE_SUPER_ADMIN') ? 'SUPER' : 'STANDARD';
  const permissions = authorities.filter((a) => a.includes(':'));
  view.append(h('table', {}, h('tbody', {},
    h('tr', {}, h('th', {}, 'Admin'), h('td', {}, `${d.fullName} (#${d.id})`)),
    h('tr', {}, h('th', {}, 'E-posta'), h('td', {}, me.ok ? me.data.email : '—')),
    h('tr', {}, h('th', {}, 'Birim'), h('td', {}, d.department || '—')),
    h('tr', {}, h('th', {}, 'Seviye'), h('td', {}, h('span', { class: 'badge info' }, level))),
    h('tr', {}, h('th', {}, 'Bölgeler'), h('td', {}, d.managedRegions.join(', ') || '—')),
    h('tr', {}, h('th', {}, 'İzinler'), h('td', {},
      h('div', { class: 'authorities' }, permissions.map((p) => h('span', { class: 'badge info' }, p))))))),
  h('p', { class: 'small muted' },
    'Ad, birim, bölgeler: staff modülü (/api/admin/dashboard). E-posta, seviye, izinler: identity modülü (/api/me).'));
}

function customerQuery(page) {
  const values = formValues($('#customer-filter'));
  const params = new URLSearchParams({
    page: String(page), size: values.size, sort: values.sort, direction: values.direction,
  });
  if (values.email) params.set('email', values.email);
  return `/api/admin/customers?${params}`;
}

/** Looks up data of the listed ids in another module; a failed lookup leaves the column empty, not the list. */
async function lookup(path, ids) {
  if (ids.length === 0) return new Map();
  const response = await api.get(`${path}?${new URLSearchParams(ids.map((id) => ['ids', String(id)]))}`);
  if (!response.ok) return new Map();
  return new Map(response.data.map((row) => [row.id ?? row.memberId, row]));
}

function membershipBadge(membership) {
  if (!membership) return '—';
  const until = membership.endsAt ? ` · ${formatInstant(membership.endsAt)}` : '';
  return h('span', { class: `badge ${membership.validNow ? 'info' : 'warn'}` },
    membership.tier + until + (membership.validNow ? '' : ' (bitti)'));
}

/**
 * UI composition over three modules: the customer directory (customer), then account status (identity) and
 * membership (membership) for exactly the ids on this page — no module reads another module's tables.
 */
async function loadCustomers(page = currentPage) {
  const response = await api.get(customerQuery(page));
  const view = clear($('#customers-view'));
  const pager = clear($('#pager'));
  clear($('#customers-result'));
  if (!response.ok) {
    showResult($('#customers-result'), response);
    return;
  }
  currentPage = response.data.page;
  const ids = response.data.content.map((c) => c.id);
  const [accounts, memberships] = await Promise.all([
    lookup('/api/admin/accounts', ids),
    lookup('/api/admin/memberships', ids),
  ]);
  view.append(h('table', {},
    h('thead', {}, h('tr', {},
      ['Id', 'E-posta', 'Ad Soyad', 'Durum', 'Üyelik', 'Kayıt', 'İşlemler'].map((t) => h('th', {}, t)))),
    h('tbody', {}, response.data.content.map((c) => {
      const status = accounts.get(c.id)?.status;
      return h('tr', {},
        h('td', {}, String(c.id)),
        h('td', {}, c.contactEmail),
        h('td', {}, `${c.firstName} ${c.lastName}`),
        h('td', {}, status
          ? h('span', { class: `badge ${status === 'ACTIVE' ? 'ok' : 'err'}` }, status)
          : h('span', { class: 'muted' }, '?')),
        h('td', {}, membershipBadge(memberships.get(c.id))),
        h('td', { class: 'small' }, formatInstant(c.registeredAt)),
        h('td', {}, h('div', { class: 'row' },
          status === 'DISABLED'
            ? h('button', { type: 'button', class: 'small', dataset: { action: 'enable', id: c.id } }, 'Etkinleştir')
            : h('button', { type: 'button', class: 'danger small', dataset: { action: 'disable', id: c.id } }, 'Devre dışı bırak'),
          h('button', { type: 'button', class: 'secondary small', dataset: { action: 'customer-sessions', id: c.id } }, 'Oturumlar'),
          h('button', { type: 'button', class: 'secondary small', dataset: { action: 'pick-membership', id: c.id } }, 'Üyelik'))));
    }))));
  view.append(h('p', { class: 'small muted' },
    'Liste: customer modülü · Durum: identity (/api/admin/accounts) · Üyelik: membership (/api/admin/memberships)'));
  const { page: p, totalPages, totalElements } = response.data;
  pager.append(
    h('button', { type: 'button', class: 'secondary small', dataset: { action: 'page', page: p - 1 }, disabled: p <= 0 }, '← Önceki'),
    h('span', { class: 'small muted' }, `Sayfa ${p + 1} / ${Math.max(totalPages, 1)} · toplam ${totalElements}`),
    h('button', { type: 'button', class: 'secondary small', dataset: { action: 'page', page: p + 1 }, disabled: p + 1 >= totalPages }, 'Sonraki →'));
}

async function loadCustomerSessions(customerId) {
  selectedCustomerId = customerId;
  const response = await api.get(`/api/admin/customers/${customerId}/sessions`);
  const title = clear($('#customer-sessions-title'));
  const view = clear($('#customer-sessions-view'));
  title.append(`Müşteri #${customerId} `,
    h('button', { type: 'button', class: 'danger small', dataset: { action: 'terminate-customer-sessions' } },
      'Tüm oturumları ve token\'ları sonlandır'));
  if (!response.ok) {
    showResult(view, response);
    return;
  }
  renderSessions(view, response.data, (session) => h('button', {
    type: 'button', class: 'danger secondary small', dataset: { action: 'revoke-customer-session', id: session.id },
  }, 'Kapat'));
}

function renderSessions(view, sessions, actionFor) {
  if (sessions.length === 0) {
    view.append(h('p', { class: 'muted small' }, 'Aktif oturum yok.'));
    return;
  }
  view.append(h('table', {},
    h('thead', {}, h('tr', {}, ['Id', 'Cihaz', 'IP', 'Son yenileme', ''].map((t) => h('th', {}, t)))),
    h('tbody', {}, sessions.map((s) => h('tr', {},
      h('td', {}, String(s.id)),
      h('td', { class: 'small' }, s.userAgent),
      h('td', { class: 'mono' }, s.ipAddress),
      h('td', { class: 'small' }, formatInstant(s.lastRefreshedAt)),
      h('td', {}, actionFor ? actionFor(s) : null))))));
}

onAction('load-dashboard', loadDashboard);
onAction('page', (button) => loadCustomers(Number(button.dataset.page)));
onSubmit($('#customer-filter'), () => loadCustomers(0));

onAction('disable', async (button) => {
  showResult($('#customers-result'), await api.post(`/api/admin/customers/${button.dataset.id}/disable`),
    `Müşteri #${button.dataset.id} devre dışı`);
  await loadCustomers();
});

onAction('enable', async (button) => {
  showResult($('#customers-result'), await api.post(`/api/admin/customers/${button.dataset.id}/enable`),
    `Müşteri #${button.dataset.id} etkin`);
  await loadCustomers();
});

onAction('customer-sessions', (button) => loadCustomerSessions(button.dataset.id));

onAction('terminate-customer-sessions', async () => {
  if (!selectedCustomerId) return;
  showResult($('#customer-sessions-result'),
    await api.delete(`/api/admin/customers/${selectedCustomerId}/sessions`), 'Tümünü sonlandır');
  await loadCustomerSessions(selectedCustomerId);
});

onAction('revoke-customer-session', async (button) => {
  showResult($('#customer-sessions-result'),
    await api.delete(`/api/admin/customers/${selectedCustomerId}/sessions/${button.dataset.id}`),
    `Oturum ${button.dataset.id}`);
  await loadCustomerSessions(selectedCustomerId);
});

onAction('pick-membership', (button) => {
  $('#m-customerId').value = button.dataset.id;
  $('#m-customerId').focus();
});

onSubmit($('#membership-form'), async (values) => {
  const validUntil = values.validUntil ? new Date(values.validUntil).toISOString() : null;
  showResult($('#membership-result'),
    await api.put(`/api/admin/customers/${values.customerId}/membership`, { tier: values.tier, validUntil }));
  await loadCustomers();
});

onAction('cancel-membership', async () => {
  const customerId = $('#m-customerId').value;
  showResult($('#membership-result'), await api.delete(`/api/admin/customers/${customerId}/membership`));
  await loadCustomers();
});

onSubmit($('#admin-sessions-form'), async (values) => {
  const response = await api.get(`/api/admin/admins/${values.adminId}/sessions`);
  const view = clear($('#admin-sessions-view'));
  if (!response.ok) {
    showResult(view, response);
    return;
  }
  renderSessions(view, response.data, null);
});

onAction('terminate-admin-sessions', async () => {
  const adminId = $('#admin-sessions-form').adminId.value;
  showResult(clear($('#admin-sessions-view')), await api.delete(`/api/admin/admins/${adminId}/sessions`),
    `Admin #${adminId} oturumları`);
});

loadSession().then(async () => {
  await loadDashboard();
  await loadCustomers(0);
});
