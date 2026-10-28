/* API dùng chung. Cookie và CSRF theo docs/api.md.
 * Luôn dùng API thật cùng origin. Không có mock hoặc credential cố định.
 * Entry công khai: /assets/movies-entry.html; trang quản trị: /admin/movies.html.
 */
(() => {
  'use strict';
  let csrf = null;
  class ApiError extends Error {
    constructor(status, data = {}) {
      super(data.message || `Yêu cầu thất bại (HTTP ${status}).`);
      this.status = status; this.code = data.code || 'HTTP_ERROR';
      this.fieldErrors = data.fieldErrors || {}; this.path = data.path || '';
    }
  }
  async function request(url, { method = 'GET', body, signal, headers: customHeaders = {} } = {}) {
    const headers = { Accept: 'application/json', ...customHeaders };
    if (method !== 'GET') {
      if (!csrf) await refreshCsrf();
      headers[csrf.headerName] = csrf.token;
    }
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try { response = await fetch(url, { method, headers, credentials: 'same-origin', signal,
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}) }); }
    catch (error) {
      if (error.name === 'AbortError') throw error;
      throw new ApiError(0, { code: 'NETWORK_ERROR', message: method === 'GET'
        ? 'Không thể kết nối tới máy chủ. Kiểm tra kết nối rồi thử lại.'
        : 'Mất kết nối khi gửi. Chưa xác định được kết quả; hãy tải lại danh sách để kiểm tra trước khi gửi lại.' });
    }
    let data = null;
    if (response.status !== 204) {
      let text;
      try { text = await response.text(); }
      catch { throw new ApiError(0, { code: 'NETWORK_ERROR', message: method === 'GET' ? 'Kết nối bị ngắt khi nhận dữ liệu. Hãy tải lại.' : 'Kết nối bị ngắt khi nhận kết quả. Chưa xác định được yêu cầu đã lưu hay chưa; kiểm tra trước khi gửi lại.' }); }
      if (text) { try { data = JSON.parse(text); } catch { throw new ApiError(response.status, { code: 'INVALID_RESPONSE', message: 'Máy chủ trả về dữ liệu không đúng định dạng JSON.' }); } }
    }
    if (!response.ok) {
      if (data?.code === 'CSRF_INVALID' || response.status === 401) csrf = null;
      if (response.status === 401 && !url.startsWith('/api/auth/')) window.dispatchEvent(new Event('session-expired'));
      throw new ApiError(response.status, data || {});
    }
    return data;
  }
  async function refreshCsrf() {
    const data = await request('/api/auth/csrf');
    if (!data?.headerName || !data?.token) throw new ApiError(0, { message: 'Không nhận được CSRF hợp lệ từ máy chủ.' });
    csrf = data;
  }
  function clearSession() { csrf = null; }
  window.Api = Object.freeze({ request, refreshCsrf, clearSession, ApiError });
})();
