import { api, loadSession, onAction, showResult, $ } from '../api.js';

onAction('quick-login', async (button) => {
  const { email, type, password } = button.dataset;
  const response = await api.post('/api/auth/login', { email, password, userType: type });
  showResult($('#quick-login-result'), response, `${email} ile tarayıcı girişi`);
  if (response.ok) {
    // Reload so the server-side badge also shows the new identity.
    window.location.href = type === 'ADMIN' ? '/console/admin' : '/console/profile';
  }
});

loadSession();
