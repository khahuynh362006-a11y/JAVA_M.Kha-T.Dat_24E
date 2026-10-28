(() => {
  'use strict';
  const $ = id => document.getElementById(id); let busy = true;
  function showError(e) { $('login-error').textContent = UI.errorText(e); $('login-error').hidden = false; for (const name of ['email', 'password']) { const messages = e.fieldErrors?.[name] || []; $(name + '-error').textContent = messages.join(' '); $(name).setAttribute('aria-invalid', messages.length ? 'true' : 'false'); } $('login-error').focus(); }
  (async () => { try { const account = await Api.request('/api/auth/me'); location.replace(Auth.destination(account)); }
    catch (e) { if (e.status !== 401) showError(e); }
    finally { busy = false; $('login').disabled = false; $('entry-status').textContent = new URLSearchParams(location.search).has('expired') ? 'Phiên đã hết hạn. Vui lòng đăng nhập lại.' : 'Sử dụng tài khoản ADMIN hoặc STAFF được cấp.'; }
  })();
  $('login-form').addEventListener('submit', async e => {
    e.preventDefault(); if (busy) return; const email = $('email').value.trim(), password = $('password').value;
    const fieldErrors = {}; if (!email || !$('email').validity.valid) fieldErrors.email = ['Nhập email hợp lệ.'];
    if (!password.trim() || password.length > 72 || new TextEncoder().encode(password).length > 72) fieldErrors.password = ['Mật khẩu bắt buộc, tối đa 72 ký tự và 72 byte UTF-8.'];
    if (Object.keys(fieldErrors).length) { showError({ message: 'Kiểm tra thông tin đăng nhập.', fieldErrors }); return; }
    busy = true; $('login').disabled = true; $('login').textContent = 'Đang đăng nhập…';
    try { const account = await Auth.login(email, password); $('password').value = ''; location.replace(Auth.destination(account)); }
    catch (error) { showError(error); }
    finally { busy = false; $('login').disabled = false; $('login').textContent = 'Đăng nhập'; }
  });
})();
