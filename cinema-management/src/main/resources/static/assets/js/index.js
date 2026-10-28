(async () => {
  try { const user = await Api.request('/api/auth/me'); location.replace(Auth.destination(user)); }
  catch (e) { if (e.status === 401) location.replace('/login.html'); else document.getElementById('status').textContent = e.message; }
})();
