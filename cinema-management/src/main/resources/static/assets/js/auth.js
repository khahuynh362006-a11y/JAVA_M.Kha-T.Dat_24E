(() => {
  'use strict';
  let user;
  const destination = account => account.roles.includes('ADMIN') ? '/admin/dashboard.html' : '/admin/showtimes.html';
  window.addEventListener('session-expired', () => location.replace('/login.html?expired=1'));
  async function requireUser() {
    try { user = await Api.request('/api/auth/me'); return user; }
    catch (e) { if (e.status === 401) location.replace('/login.html?expired=1'); throw e; }
  }
  async function login(email, password) {
    await Api.refreshCsrf(); const account = await Api.request('/api/auth/login', { method: 'POST', body: { email, password } });
    Api.clearSession(); await Api.refreshCsrf(); user = account; return account;
  }
  async function logout() { await Api.request('/api/auth/logout', { method: 'POST' }); Api.clearSession(); location.replace('/login.html'); }
  window.Auth = { requireUser, login, logout, destination, get user() { return user; }, get admin() { return user?.roles.includes('ADMIN'); } };
})();
