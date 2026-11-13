import { $, api, formValues, h, loadSession, onAction, onSubmit, showResult } from '../api.js';

const form = $('#register-form');
const encoder = new TextEncoder();

form.password.addEventListener('input', () => {
  const value = form.password.value;
  $('#password-info').textContent =
    `${[...value].length} karakter, ${encoder.encode(value).length} bayt (UTF-8)`;
});

onSubmit(form, async (values) => {
  const response = await api.post('/api/auth/register', values);
  showResult($('#register-result'), response);
  if (response.ok) {
    $('#register-result').append(h('p', { class: 'note small' },
      'Kayıt tamam. ', h('a', { href: '/console/login' }, 'Giriş sayfasına git →')));
  }
});

onAction('register-without-csrf', async () => {
  const response = await api.post('/api/auth/register', formValues(form), { csrf: false });
  showResult($('#register-result'), response, 'X-XSRF-TOKEN başlığı gönderilmedi');
});

loadSession();
