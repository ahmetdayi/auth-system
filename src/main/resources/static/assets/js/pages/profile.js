import {
  $, api, clear, countdownBadge, formatInstant, h, loadSession, onAction, onSubmit, showResult, wireCountdowns,
} from '../api.js';

const view = $('#profile-view');

/**
 * UI composition: the profile comes from the customer module, the membership from the membership module
 * (204 when there is none). A failed membership call only blanks that row.
 */
async function loadProfile() {
  const [response, membershipResponse] = await Promise.all([
    api.get('/api/customer/profile'), api.get('/api/membership')]);
  clear($('#profile-result'));
  if (!response.ok) {
    clear(view).append(h('p', { class: 'muted' }, 'Profil okunamadı.'));
    showResult($('#profile-result'), response);
    return;
  }
  const profile = response.data;
  const form = $('#rename-form');
  if (!form.customerId.value) form.customerId.value = profile.id;
  if (!form.firstName.value) form.firstName.value = profile.firstName;
  if (!form.lastName.value) form.lastName.value = profile.lastName;

  const m = membershipResponse.ok ? membershipResponse.data : null;
  let membership;
  if (!membershipResponse.ok) {
    membership = h('span', { class: 'badge err' }, `okunamadı (${membershipResponse.status})`);
  } else if (m) {
    membership = h('span', { class: `badge ${m.validNow ? 'ok' : 'warn'}` },
      `${m.tier} · ${m.validNow ? 'geçerli' : 'geçersiz'}` + (m.endsAt ? ` · bitiş ${formatInstant(m.endsAt)}` : ''));
  } else {
    membership = h('span', { class: 'badge' }, 'üyelik yok');
  }

  const addresses = profile.addresses.length === 0
    ? h('p', { class: 'muted small' }, 'Adres yok.')
    : h('table', {},
      h('thead', {}, h('tr', {}, h('th', {}, 'Başlık'), h('th', {}, 'Şehir'), h('th', {}, 'Adres'), h('th', {}, ''))),
      h('tbody', {}, profile.addresses.map((address) => h('tr', {},
        h('td', {}, address.label),
        h('td', {}, address.city),
        h('td', {}, address.line),
        h('td', {}, h('button', {
          type: 'button', class: 'danger secondary small', dataset: { action: 'remove-address', id: address.id },
        }, 'Sil'))))));

  clear(view).append(
    h('table', {}, h('tbody', {},
      h('tr', {}, h('th', {}, 'Id'), h('td', {}, String(profile.id))),
      h('tr', {}, h('th', {}, 'E-posta'), h('td', {}, profile.contactEmail)),
      h('tr', {}, h('th', {}, 'Ad Soyad'), h('td', {}, `${profile.firstName} ${profile.lastName}`)),
      h('tr', {}, h('th', {}, 'Puan'), h('td', {}, String(profile.loyaltyPoints))),
      h('tr', {}, h('th', {}, 'Üyelik'), h('td', {}, membership)))),
    h('h3', {}, 'Adresler'),
    addresses);
}

async function loadSupportGrants() {
  const view = clear($('#support-grants-view'));
  clear($('#support-grants-result'));
  const [incoming, history] = await Promise.all([
    api.get('/api/support/grants/incoming'),
    api.get('/api/support/grants/history'),
  ]);
  if (!incoming.ok && incoming.status === 403) {
    view.append(h('p', { class: 'muted small' }, 'Yalnızca müşteri hesabında görünür.'));
    return;
  }
  if (!incoming.ok) {
    showResult($('#support-grants-result'), incoming, 'incoming');
    return;
  }
  const blocks = [];
  for (const g of incoming.data ?? []) {
    const buttons = [];
    if (g.status === 'PENDING') {
      buttons.push(h('button', {
        type: 'button', class: 'small', dataset: { action: 'approve-grant', id: g.id },
      }, 'Onayla'));
      buttons.push(h('button', {
        type: 'button', class: 'danger secondary small', dataset: { action: 'deny-grant', id: g.id },
      }, 'Reddet'));
    }
    if (g.status === 'PENDING_EXTENSION') {
      const pending = (g.extensions || []).find((e) => e.decision === 'PENDING');
      if (pending) {
        buttons.push(h('button', {
          type: 'button', class: 'small',
          dataset: { action: 'approve-extension', id: g.id, ext: pending.id },
        }, `Uzatmayı onayla (+${pending.requestedMinutes} dk)`));
        buttons.push(h('button', {
          type: 'button', class: 'danger secondary small',
          dataset: { action: 'deny-extension', id: g.id, ext: pending.id },
        }, 'Uzatmayı reddet'));
      }
    }
    if (g.status === 'ACTIVE' || g.status === 'PENDING_EXTENSION') {
      buttons.push(h('button', {
        type: 'button', class: 'danger secondary small', dataset: { action: 'end-grant-customer', id: g.id },
      }, 'Oturumu bitir'));
    }
    const meta = [];
    if (g.expiresAt && (g.status === 'ACTIVE' || g.status === 'PENDING_EXTENSION')) {
      meta.push(h('p', { class: 'small' },
        `Bitiş ${formatInstant(g.expiresAt)} · kalan: `, countdownBadge(g.expiresAt)));
    }
    blocks.push(h('div', { class: 'card' },
      h('div', { class: 'row' },
        h('span', {}, `#${g.id} ${g.status} · ajan #${g.agentId} (${g.agentEmail})`),
        h('span', { class: 'spacer' }),
        ...buttons),
      ...meta,
      h('p', { class: 'small muted' }, 'İstenen yetkiler (onaylayınca act-as token bunları taşır):'),
      h('div', { class: 'authorities' },
        (g.scopes || []).map((x) => h('span', { class: 'badge info' }, x)))));
  }
  if (blocks.length === 0) {
    blocks.push(h('p', { class: 'muted small' }, 'Bekleyen talep yok.'));
  }
  blocks.push(h('h3', {}, 'Geçmiş'));
  if (!history.ok) {
    blocks.push(h('p', { class: 'muted small' }, 'Geçmiş okunamadı.'));
  } else if (!(history.data || []).length) {
    blocks.push(h('p', { class: 'muted small' }, 'Kayıt yok.'));
  } else {
    blocks.push(h('table', {},
      h('thead', {}, h('tr', {},
        h('th', {}, 'Id'), h('th', {}, 'Ajan'), h('th', {}, 'Durum'), h('th', {}, 'Dk'),
        h('th', {}, 'Uzatma'), h('th', {}, 'Bitiş nedeni'))),
      h('tbody', {}, history.data.map((g) => h('tr', {},
        h('td', {}, String(g.id)),
        h('td', {}, g.agentEmail),
        h('td', {}, g.status),
        h('td', {}, String(g.initialMinutes + g.totalApprovedExtensionMinutes)),
        h('td', {}, String(g.extensionCount)),
        h('td', {}, g.endReason || '—'))))));
  }
  view.append(...blocks);
  wireCountdowns(view);
}

onAction('load-profile', loadProfile);
onAction('load-support-grants', loadSupportGrants);

onAction('approve-grant', async (btn) => {
  showResult($('#support-grants-result'), await api.post(`/api/support/grants/${btn.dataset.id}/approve`));
  await loadSupportGrants();
});
onAction('deny-grant', async (btn) => {
  showResult($('#support-grants-result'), await api.post(`/api/support/grants/${btn.dataset.id}/deny`));
  await loadSupportGrants();
});
onAction('approve-extension', async (btn) => {
  showResult($('#support-grants-result'),
    await api.post(`/api/support/grants/${btn.dataset.id}/extensions/${btn.dataset.ext}/approve`));
  await loadSupportGrants();
});
onAction('deny-extension', async (btn) => {
  showResult($('#support-grants-result'),
    await api.post(`/api/support/grants/${btn.dataset.id}/extensions/${btn.dataset.ext}/deny`));
  await loadSupportGrants();
});
onAction('end-grant-customer', async (btn) => {
  showResult($('#support-grants-result'), await api.post(`/api/support/grants/${btn.dataset.id}/end`));
  await loadSupportGrants();
});

onAction('remove-address', async (button) => {
  const response = await api.delete(`/api/customer/addresses/${button.dataset.id}`);
  showResult($('#address-result'), response, 'Adres silme');
  if (response.ok) await loadProfile();
});

onSubmit($('#rename-form'), async (values) => {
  const response = await api.put(`/api/customers/${encodeURIComponent(values.customerId ?? '')}/name`,
    { firstName: values.firstName, lastName: values.lastName });
  showResult($('#rename-result'), response);
  if (response.ok) await loadProfile();
});

onSubmit($('#address-form'), async (values, form) => {
  const response = await api.post('/api/customer/addresses', values);
  showResult($('#address-result'), response);
  if (response.ok) {
    form.reset();
    await loadProfile();
  }
});

onSubmit($('#password-form'), async (values, form) => {
  const response = await api.post('/api/account/password', values);
  showResult($('#password-result'), response);
  if (response.ok) {
    form.reset();
    await loadSession();
    $('#password-result').append(h('p', { class: 'note small' },
      'Şifre değişti ve bütün oturumlar kapandı. ', h('a', { href: '/console/login' }, 'Yeni şifreyle giriş yap →')));
  }
});

loadSession().then(async () => {
  await loadProfile();
  await loadSupportGrants();
});
