import { $, api, clear, formatInstant, h, loadSession, onAction, onSubmit, showResult } from '../api.js';

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

onAction('load-profile', loadProfile);

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

loadSession().then(loadProfile);
