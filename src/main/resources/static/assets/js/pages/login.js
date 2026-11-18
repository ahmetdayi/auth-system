import { $, api, h, clear, json, loadSession, onAction, onSubmit, showResult } from '../api.js';

const form = $('#login-form');

onSubmit(form, async (values) => {
  const response = await api.post('/api/auth/login', values);
  showResult($('#login-result'), response, 'Yanıt gövdesi (token yok, cookie\'lerde)');
  if (response.ok) {
    await loadSession();
    const target = values.userType === 'ADMIN' ? '/console/admin'
      : values.userType === 'SUPPORT' ? '/console/support' : '/console/profile';
    $('#login-result').append(h('p', { class: 'note small' },
      'Giriş başarılı. ', h('a', { href: target }, 'Devam et →'),
      ' (sayfayı yenileyince sunucu tarafı gösterge de güncellenir)'));
  }
});

onAction('fill-demo', (button) => {
  form.email.value = button.dataset.email;
  form.password.value = button.dataset.password;
  form.userType.value = button.dataset.type;
});

onAction('show-cookies', () => {
  const box = clear($('#cookie-result'));
  const cookies = document.cookie ? document.cookie.split(';').map((c) => c.trim().split('=')[0]) : [];
  box.append(h('div', { class: 'result' },
    h('div', { class: 'result-head' }, 'JavaScript\'in görebildiği cookie adları'),
    json(cookies)));
});

loadSession();
