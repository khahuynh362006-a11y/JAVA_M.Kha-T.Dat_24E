(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const node = (tag, text, cls) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (cls) n.className = cls; return n; };
  const button = (text, action, cls = 'button') => { const n = node('button', text, cls); n.type = 'button'; if (action) n.addEventListener('click', action); return n; };
  const money = (value) => value == null ? '—' : new Intl.NumberFormat('vi-VN').format(value) + ' ₫';
  const time = (value) => value ? new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value.includes('+') || value.endsWith('Z') ? value : value + '+07:00')) : '—';
  const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const labels = { ACTIVE: 'Đang sử dụng', INACTIVE: 'Ngừng sử dụng', SCHEDULED: 'Đã lên lịch', CANCELLED: 'Đã hủy', CONFIRMED: 'Đã đặt', UPCOMING: 'Sắp chiếu', IN_PROGRESS: 'Đang chiếu', COMPLETED: 'Đã kết thúc', STANDARD: 'Thường', VIP: 'VIP', AVAILABLE: 'Trống', BOOKED: 'Đã đặt', UNAVAILABLE: 'Không sử dụng' };
  const badge = (value) => node('span', labels[value] || value || '—', 'badge ' + (['ACTIVE', 'SCHEDULED', 'CONFIRMED', 'AVAILABLE'].includes(value) ? 'active' : 'inactive'));
  function errorText(error) {
    const messages = {
      CSRF_INVALID: 'Phiên bảo vệ thay đổi. Kiểm tra dữ liệu rồi bấm gửi lại; yêu cầu vừa rồi đã bị từ chối.',
      FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
      MOVIE_HAS_SHOWTIMES: 'Phim đã có suất chiếu, không thể xóa.',
      SHOWTIME_CONFLICT: 'Lịch chiếu trùng hoặc chưa đủ 15 phút nghỉ giữa các suất.',
      SEAT_LAYOUT_LOCKED: 'Phòng đã có lịch sử suất chiếu nên không thể thay đổi cấu trúc ghế.',
      SEAT_STATUS_LOCKED: 'Phòng còn suất chưa kết thúc nên không thể đổi trạng thái ghế.',
      SHOWTIME_HAS_BOOKINGS: 'Suất đã có lịch sử đặt chỗ, không thể sửa, hủy hoặc xóa.',
      PRICE_CHANGED: 'Giá đã thay đổi. Kiểm tra giá mới và xác nhận lại.',
      SEAT_ALREADY_BOOKED: 'Ghế vừa được người khác đặt. Sơ đồ cần tải lại.',
      RESOURCE_NOT_FOUND: 'Không tìm thấy tài nguyên. Hãy tải lại dữ liệu.'
    };
    return `${messages[error.code] || error.message || 'Không thực hiện được yêu cầu.'}${error.code ? ` (${error.code})` : ''}`;
  }
  function alert(message, isError = false, target = $('feedback')) {
    if (!target) return; target.textContent = message; target.hidden = false; target.className = 'notice' + (isError ? ' error' : '');
  }
  function table(columns, items) {
    if (!items.length) return node('p', 'Không có dữ liệu phù hợp.', 'state');
    const wrap = node('div', undefined, 'table-scroll'); const t = node('table'); const head = node('thead'), hr = node('tr');
    for (const c of columns) { const th = node('th', c.title); th.scope = 'col'; hr.append(th); } head.append(hr); t.append(head);
    const body = node('tbody');
    for (const item of items) { const row = node('tr'); for (const c of columns) { const td = node('td'); td.dataset.label = c.title; const value = c.render ? c.render(item) : item[c.key]; if (value instanceof Node) td.append(value); else td.textContent = value == null ? '—' : String(value); row.append(td); } body.append(row); }
    t.append(body); wrap.append(t); return wrap;
  }
  function actions(...buttons) { const n = node('div', undefined, 'actions'); n.append(...buttons.filter(Boolean)); return n; }
  function link(text, href, cls = 'button') { const n = node('a', text, cls); n.href = href; return n; }
  function page(title, description) {
    const host = $('app'); host.replaceChildren(); const heading = node('div', undefined, 'heading'); const text = node('div');
    text.append(node('p', 'KHA ĐẠT CINEMA', 'eyebrow'), node('h1', title), node('p', description, 'muted')); heading.append(text); host.append(heading);
    const feedback = node('div'); feedback.id = 'feedback'; feedback.hidden = true; feedback.setAttribute('role', 'status'); feedback.setAttribute('aria-live', 'polite'); host.append(feedback);
    return { host, heading };
  }
  function filter(fields, onSubmit) {
    const form = node('form', undefined, 'filters'); const controls = {};
    for (const f of fields) { const l = node('label', f.label); const input = f.options ? node('select') : node('input'); input.name = f.name; input.id = 'filter-' + f.name;
      if (f.options) for (const [value, text] of f.options) input.add(new Option(text, value)); else input.type = f.type || 'search';
      if (f.type === 'number') { input.min = '1'; input.step = '1'; }
      if (!f.type && !f.options) input.maxLength = 200;
      if (f.value != null) input.value = f.value; l.append(input); controls[f.name] = input; form.append(l); }
    const submit = node('button', 'Áp dụng', 'button primary'); submit.type = 'submit'; form.append(submit);
    form.addEventListener('submit', e => { e.preventDefault(); onSubmit(Object.fromEntries(Object.entries(controls).map(([k, v]) => [k, v.value]))); });
    return { form, controls, values: () => Object.fromEntries(Object.entries(controls).map(([k, v]) => [k, v.value])) };
  }
  async function all(url, extra = {}) {
    const items = []; let page = 0, pages = 1;
    while (page < pages) { const data = await Api.request(url + '?' + new URLSearchParams({ ...extra, page, size: 100 })); if (!Array.isArray(data.items)) throw new Error('Response danh sách không khớp hợp đồng.'); items.push(...data.items); pages = data.totalPages; page++; }
    return items;
  }
  function pager(host, load, options = {}) {
    let index = 0, size = 20, pages = 0, busy = false;
    const state = node('div', 'Đang tải…', 'state'), content = node('div'), footer = node('div', undefined, 'pagination');
    state.setAttribute('role', 'status'); state.setAttribute('aria-live', 'polite');
    const count = node('p'), prev = button('←', () => run(Math.max(0, index - 1))), next = button('→', () => run(index + 1));
    prev.setAttribute('aria-label', 'Trang trước'); next.setAttribute('aria-label', 'Trang sau');
    const current = node('span'); const sizeLabel = node('label', 'Số dòng'), select = node('select'); for (const s of [10, 20, 50, 100]) select.add(new Option(s, s)); select.value = 20; sizeLabel.append(select);
    select.addEventListener('change', () => { size = Number(select.value); run(0); });
    const retry = button('Tải lại', () => run(index)); footer.append(count, sizeLabel, prev, current, next, retry); host.append(state, content, footer);
    function lock(value) { busy = value; prev.disabled = value || index <= 0; next.disabled = value || index + 1 >= pages; select.disabled = value; retry.disabled = value; options.lock?.(value); content.querySelectorAll('button').forEach(b => b.disabled = value); content.setAttribute('aria-busy', value); }
    async function run(target = index, afterWrite = false) {
      if (busy) return; index = target; lock(true); state.hidden = false; state.classList.remove('error'); state.textContent = 'Đang tải dữ liệu…'; content.hidden = true;
      try { const data = await load(index, size); pages = data.totalPages;
        if (index > 0 && index >= pages) { lock(false); return run(Math.max(0, pages - 1), afterWrite); }
        content.replaceChildren(options.render(data.items)); state.hidden = true; content.hidden = false;
        count.textContent = `${data.total} kết quả`; current.textContent = `Trang ${pages ? index + 1 : 0} / ${pages}`;
      } catch (e) { state.textContent = (afterWrite ? 'Đã lưu thành công nhưng chưa tải lại được danh sách. ' : '') + errorText(e); state.classList.add('error'); count.textContent = 'Chưa tải được dữ liệu'; pages = 0; }
      finally { lock(false); }
    }
    return { run, reset: () => run(0), refreshAfterWrite: () => run(index, true), get busy() { return busy; } };
  }
  function dialog(title, fields, initial = {}, { submitText = 'Lưu', onSave, description = '' } = {}) {
    const d = node('dialog'), form = node('form'); d.setAttribute('aria-label', title); form.noValidate = true; const header = node('header', undefined, 'dialog-head'); header.append(node('h2', title));
    const close = button('✕', () => d.close(), 'icon-button'); close.setAttribute('aria-label', 'Đóng'); header.append(close);
    const body = node('div', undefined, 'dialog-body'); if (description) body.append(node('p', description, 'muted'));
    const error = node('div', undefined, 'error-summary'); error.role = 'alert'; error.tabIndex = -1; error.hidden = true; body.append(error);
    const grid = node('div', undefined, 'form-grid'); const controls = {}, outputs = {};
    for (const f of fields) {
      const label = node('label', f.label + (f.required ? ' *' : ''), f.wide ? 'wide' : '');
      const input = f.options ? node('select') : f.type === 'textarea' ? node('textarea') : node('input'); input.name = f.name; input.id = 'edit-' + f.name;
      if (f.options) for (const [value, text] of f.options) input.add(new Option(text, value)); else if (f.type !== 'textarea') input.type = f.type || 'text';
      if (f.type === 'textarea') input.rows = 4;
      for (const attr of ['required', 'min', 'max', 'step', 'maxLength', 'pattern']) if (f[attr] != null) input[attr] = f[attr];
      if (f.type === 'number') input.step = f.step || '1';
      input.value = initial[f.name] ?? f.value ?? (f.options?.[0]?.[0] ?? '');
      const out = node('span', undefined, 'field-error'); out.id = 'field-' + f.name; input.setAttribute('aria-describedby', out.id); label.append(input);
      if (f.hint) label.append(node('small', f.hint)); label.append(out); grid.append(label); controls[f.name] = input; outputs[f.name] = out;
    }
    body.append(grid); const foot = node('footer', undefined, 'dialog-foot'); const cancel = button('Hủy', () => d.close()); const submit = node('button', submitText, 'button primary'); submit.type = 'submit'; foot.append(cancel, submit); form.append(header, body, foot); d.append(form); document.body.append(d);
    let saving = false; const trigger = document.activeElement;
    function displayError(e) { error.textContent = errorText(e); for (const [name, messages] of Object.entries(e.fieldErrors || {})) { if (outputs[name]) { outputs[name].textContent = messages.join(' '); controls[name].setAttribute('aria-invalid', 'true'); } else error.textContent += '\n' + messages.join(' '); } error.hidden = false; error.focus(); }
    form.addEventListener('submit', async e => {
      e.preventDefault(); if (saving) return; error.hidden = true; Object.values(outputs).forEach(n => n.textContent = ''); Object.values(controls).forEach(n => n.removeAttribute('aria-invalid'));
      const invalid = fields.filter(f => !controls[f.name].validity.valid || (f.required && !controls[f.name].value.trim()));
      if (invalid.length) { displayError({ message: 'Kiểm tra các trường bắt buộc, định dạng và giới hạn.', fieldErrors: Object.fromEntries(invalid.map(f => [f.name, ['Giá trị không hợp lệ.']])) }); return; }
      const values = Object.fromEntries(fields.map(f => [f.name, f.type === 'number' || f.valueType === 'number' ? Number(controls[f.name].value) : controls[f.name].value.trim() || null]));
      saving = true; form.querySelectorAll('button,input,select,textarea').forEach(n => n.disabled = true); submit.textContent = 'Đang xử lý…';
      try { const outcome = await onSave(values); if (outcome !== false) d.close(); } catch (failure) { displayError(failure); }
      finally { saving = false; form.querySelectorAll('button,input,select,textarea').forEach(n => n.disabled = false); submit.textContent = submitText; }
    });
    d.addEventListener('cancel', e => { if (saving) e.preventDefault(); });
    trap(d); d.addEventListener('close', () => { d.remove(); if (trigger?.isConnected) trigger.focus(); }); d.showModal(); Object.values(controls)[0]?.focus(); return { dialog: d, controls, body };
  }
  function trap(d) { d.addEventListener('keydown', e => { if (e.key !== 'Tab') return; const nodes = [...d.querySelectorAll('button,input,select,textarea,a[href],[tabindex="0"]')].filter(n => !n.disabled && n.getClientRects().length); if (!nodes.length) { e.preventDefault(); return; } if (e.shiftKey && document.activeElement === nodes[0]) { e.preventDefault(); nodes.at(-1).focus(); } else if (!e.shiftKey && document.activeElement === nodes.at(-1)) { e.preventDefault(); nodes[0].focus(); } }); }
  const confirm = (title, description, onSave, reason = false) => dialog(title, reason ? [{ name: 'reason', label: 'Lý do', type: 'textarea', required: true, maxLength: 500, wide: true }] : [], {}, { submitText: 'Xác nhận', description, onSave });
  function detail(title, rows, buttons = []) {
    const d = node('dialog'); d.setAttribute('aria-label', title); const header = node('header', undefined, 'dialog-head'); header.append(node('h2', title), button('Đóng', () => d.close()));
    const body = node('div', undefined, 'dialog-body'); const dl = node('dl', undefined, 'detail-grid'); for (const [key, value] of rows) { dl.append(node('dt', key), node('dd', value == null ? '—' : String(value))); } body.append(dl, actions(...buttons)); d.append(header, body); document.body.append(d); const trigger = document.activeElement; d.addEventListener('close', () => { d.remove(); trigger?.isConnected && trigger.focus(); }); trap(d); d.showModal(); return d;
  }
  const query = (data) => new URLSearchParams(Object.entries(data).filter(([, v]) => v !== '' && v != null)).toString();
  window.UI = { $, node, button, money, time, today, labels, badge, errorText, alert, table, actions, link, page, filter, all, pager, dialog, confirm, detail, query, trap };
})();
