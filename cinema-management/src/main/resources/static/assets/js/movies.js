(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const form = $('movie-form');
  const fields = ['code', 'title', 'durationMinutes', 'minimumAge', 'status', 'posterUrl', 'trailerUrl', 'description'];
  let genres = [], page = 0, totalPages = 0, loading = false, busy = false, ready = false;
  let editingId = null, originalStatus = null, deleting = null, returnFocus = null, sequence = 0, controller;
  const friendly = {
    MOVIE_HAS_SHOWTIMES: 'Không thể xóa phim đã có suất chiếu. Hãy giữ phim để bảo toàn lịch sử.',
    MOVIE_SCHEDULE_FIELDS_LOCKED: 'Phim đã có suất chiếu nên không thể đổi thời lượng hoặc tuổi tối thiểu. Giữ nguyên hai giá trị này để sửa thông tin khác.',
    MOVIE_HAS_UNFINISHED_SHOWTIMES: 'Phim còn suất chiếu chưa kết thúc. Cần xử lý các suất liên quan trước khi ngừng sử dụng.',
    UNKNOWN_GENRES: 'Có thể loại không còn tồn tại. Danh sách thể loại đã được tải lại; hãy chọn lại trước khi lưu.',
    CONCURRENT_MODIFICATION: 'Dữ liệu vừa được thay đổi bởi thao tác khác. Đóng biểu mẫu và tải lại trước khi thử lại.',
    CSRF_INVALID: 'Phiên bảo vệ đã thay đổi. Hãy thử lại thao tác sau khi kiểm tra dữ liệu; yêu cầu vừa rồi chưa được thực hiện.',
    FORBIDDEN: 'Tài khoản không có quyền ADMIN để quản lý phim.'
  };
  const errorText = (error) => `${friendly[error.code] || error.message}${error.code ? ` (${error.code})` : ''}`;
  const el = (tag, text, className) => {
    const node = document.createElement(tag); if (text !== undefined) node.textContent = text;
    if (className) node.className = className; return node;
  };
  function notice(message, error = false) {
    $('notice').textContent = message; $('notice').hidden = false;
    $('notice').classList.toggle('error', error);
  }
  function authorizeError(error) {
    if (error.status === 401) { location.replace('/login.html'); return true; }
    return false;
  }
  function controls() {
    $('add').disabled = !ready || busy || loading || !genres.length;
    $('reload').disabled = busy || loading;
    $('logout').disabled = busy || loading;
    $('prev').disabled = busy || loading || page <= 0;
    $('next').disabled = busy || loading || page + 1 >= totalPages;
    document.querySelectorAll('#rows button').forEach((node) => { node.disabled = busy || loading; });
    document.querySelectorAll('#filters input,#filters select,#filters button,#size').forEach((node) => { node.disabled = busy || loading; });
    $('table-region').setAttribute('aria-busy', String(loading));
  }
  function pending(value) {
    busy = value;
    for (const node of form.querySelectorAll('input,select,textarea,button')) node.disabled = value;
    document.querySelectorAll('[data-close],#delete-confirm').forEach((node) => { node.disabled = value; });
    $('save').textContent = value ? 'Đang lưu…' : 'Lưu phim';
    $('delete-confirm').textContent = value ? 'Đang xóa…' : 'Xóa phim';
    controls();
  }
  function state(message, isError = false) {
    $('list-state').textContent = message; $('list-state').hidden = false;
    $('list-state').classList.toggle('error', isError); $('table-region').hidden = true;
  }
  function checkPage(data) {
    if (!data || !Array.isArray(data.items) || !Number.isInteger(data.total) || !Number.isInteger(data.totalPages)
      || !Number.isInteger(data.page) || !Number.isInteger(data.size)) throw new Error('Response phân trang không khớp docs/api.md.');
    return data;
  }
  async function loadGenres() {
    const next = []; let index = 0, pages = 1;
    while (index < pages) {
      const data = checkPage(await MovieApi.request(`/api/admin/genres?page=${index}&size=100&sort=name&direction=asc`));
      next.push(...data.items); pages = data.totalPages; index++;
    }
    genres = next;
    const selectedFilter = $('genre-filter').value;
    const selectedChoices = new Set([...form.querySelectorAll('[name=genreIds]:checked')].map((node) => node.value));
    $('genre-filter').replaceChildren(new Option('Tất cả thể loại', ''));
    $('genre-choices').replaceChildren();
    for (const genre of genres) {
      $('genre-filter').add(new Option(genre.name, String(genre.id)));
      const label = el('label'); const input = el('input'); input.type = 'checkbox'; input.name = 'genreIds';
      input.value = String(genre.id); input.checked = selectedChoices.has(input.value);
      input.setAttribute('aria-describedby', 'error-genreIds'); label.append(input, el('span', genre.name)); $('genre-choices').append(label);
    }
    $('genre-filter').value = selectedFilter;
    if (!genres.length) $('genre-choices').append(el('p', 'Chưa có thể loại. Nhờ ADMIN/Kha tạo thể loại trước khi thêm phim.', 'muted'));
  }
  function poster(movie) {
    const fallback = el('span', 'PHIM', 'poster poster-placeholder'); fallback.setAttribute('aria-hidden', 'true');
    if (!movie.posterUrl) return fallback;
    try {
      const url = new URL(movie.posterUrl);
      if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return fallback;
      const img = el('img', undefined, 'poster'); img.alt = ''; img.loading = 'lazy'; img.referrerPolicy = 'no-referrer';
      img.addEventListener('error', () => img.replaceWith(fallback), { once: true }); img.src = url.href; return img;
    } catch { return fallback; }
  }
  function render(items) {
    $('rows').replaceChildren();
    for (const movie of items) {
      const row = el('tr');
      const cell = (label) => { const td = el('td'); td.dataset.label = label; row.append(td); return td; };
      const identity = el('div', undefined, 'movie-cell'); const names = el('div');
      names.append(el('span', movie.title, 'movie-title'), el('span', movie.code, 'movie-code'));
      identity.append(poster(movie), names); cell('Phim').append(identity);
      const chips = el('div', undefined, 'chips'); for (const genre of movie.genres || []) chips.append(el('span', genre.name, 'chip'));
      cell('Thể loại').append(chips);
      cell('Thời lượng / Tuổi').append(el('span', `${movie.durationMinutes} phút · ${movie.minimumAge}+`));
      cell('Trạng thái').append(el('span', movie.status === 'ACTIVE' ? 'Đang sử dụng' : 'Ngừng sử dụng', `badge ${movie.status === 'ACTIVE' ? 'active' : 'inactive'}`));
      const actions = el('div', undefined, 'actions');
      const edit = el('button', 'Sửa', 'button'); edit.type = 'button'; edit.setAttribute('aria-label', `Sửa phim ${movie.title}`);
      edit.addEventListener('click', () => editMovie(movie.id, edit));
      const remove = el('button', 'Xóa', 'button delete'); remove.type = 'button'; remove.setAttribute('aria-label', `Xóa phim ${movie.title}`);
      remove.addEventListener('click', () => {
        if (busy || loading) return; deleting = movie; returnFocus = remove;
        $('delete-description').textContent = `Bạn có chắc muốn xóa “${movie.title}” (${movie.code})?`;
        $('delete-error').hidden = true; $('delete-dialog').showModal(); $('delete-cancel').focus();
      }); actions.append(edit, remove); cell('Thao tác').append(actions); $('rows').append(row);
    }
  }
  async function loadMovies({ afterSave = false } = {}) {
    const current = ++sequence; controller?.abort(); controller = new AbortController();
    loading = true; controls(); state('Đang tải danh sách phim…');
    const [sort, direction] = $('sort').value.split(':');
    const params = new URLSearchParams({ q: $('query').value.trim(), page: String(page), size: $('size').value, sort, direction });
    if ($('status-filter').value) params.set('status', $('status-filter').value);
    if ($('genre-filter').value) params.set('genreId', $('genre-filter').value);
    try {
      const data = checkPage(await MovieApi.request(`/api/admin/movies?${params}`, { signal: controller.signal }));
      if (current !== sequence) return;
      totalPages = data.totalPages;
      if (page > 0 && page >= totalPages) { page = Math.max(0, totalPages - 1); return await loadMovies({ afterSave }); }
      render(data.items);
      $('count').textContent = data.total ? `${data.total} phim · Hiển thị ${page * data.size + 1}–${page * data.size + data.items.length}` : '0 phim';
      $('page').textContent = `Trang ${totalPages ? page + 1 : 0} / ${totalPages}`;
      if (data.items.length) { $('list-state').hidden = true; $('table-region').hidden = false; }
      else state('Không có phim phù hợp. Thử thay đổi bộ lọc hoặc thêm phim mới.');
    } catch (error) {
      if (error.name === 'AbortError' || current !== sequence || authorizeError(error)) return;
      state(`${afterSave ? 'Thao tác đã lưu thành công, nhưng chưa tải lại được danh sách. ' : ''}${errorText(error)} Bấm Tải lại để thử lại.`, true);
      $('count').textContent = 'Chưa tải được dữ liệu'; $('page').textContent = '—'; totalPages = 0;
    } finally { if (current === sequence) { loading = false; controls(); } }
  }
  function clearErrors() {
    $('form-error').hidden = true;
    for (const node of form.querySelectorAll('[aria-invalid]')) node.removeAttribute('aria-invalid');
    for (const node of form.querySelectorAll('.field-error')) node.textContent = '';
  }
  function formError(error) {
    clearErrors(); const details = [];
    for (const [name, messages] of Object.entries(error.fieldErrors || {})) {
      const output = $(`error-${name}`); if (output) output.textContent = messages.join(' ');
      for (const node of form.querySelectorAll(`[name="${name.replace(/[^a-zA-Z0-9]/g, '')}"]`)) node.setAttribute('aria-invalid', 'true');
      details.push(messages.join(' '));
    }
    $('form-error').textContent = [errorText(error), ...details].join('\n'); $('form-error').hidden = false; $('form-error').focus();
  }
  function openEditor(movie, trigger) {
    editingId = movie?.id ?? null; originalStatus = movie?.status ?? null; returnFocus = trigger; form.reset(); clearErrors();
    $('editor-title').textContent = movie ? 'Sửa phim' : 'Thêm phim';
    for (const name of fields) form.elements[name].value = movie?.[name] ?? (name === 'status' ? 'ACTIVE' : name === 'minimumAge' ? '0' : '');
    const ids = new Set((movie?.genreIds || []).map(String));
    for (const input of form.querySelectorAll('[name=genreIds]')) input.checked = ids.has(input.value);
    $('editor').showModal(); $('movie-code').focus();
  }
  async function editMovie(id, trigger) {
    if (busy || loading) return; pending(true);
    try { const movie = await MovieApi.request(`/api/admin/movies/${id}`); pending(false); openEditor(movie, trigger); }
    catch (error) { if (!authorizeError(error)) notice(errorText(error), true); }
    finally { pending(false); }
  }
  function validate() {
    const data = {}; const errors = {};
    for (const name of ['code', 'title', 'description', 'posterUrl', 'trailerUrl']) data[name] = form.elements[name].value.trim() || null;
    data.status = form.elements.status.value;
    for (const name of ['durationMinutes', 'minimumAge']) {
      const input = form.elements[name]; const value = Number(input.value);
      if (!input.value || !Number.isInteger(value) || !input.validity.valid) errors[name] = [name === 'durationMinutes' ? 'Nhập số phút nguyên từ 1–65535.' : 'Nhập tuổi nguyên từ 0–18.'];
      data[name] = value;
    }
    if (!data.code || !/^[A-Za-z0-9._-]{1,32}$/.test(data.code)) errors.code = ['Mã phim gồm 1–32 ký tự chữ ASCII, số, dấu chấm, gạch dưới hoặc gạch ngang.'];
    if (!data.title || data.title.length > 200) errors.title = ['Tên phim bắt buộc, tối đa 200 ký tự.'];
    if (!['ACTIVE', 'INACTIVE'].includes(data.status)) errors.status = ['Chọn trạng thái hợp lệ.'];
    if (data.description && new TextEncoder().encode(data.description).length > 65535) errors.description = ['Mô tả vượt quá 65.535 byte UTF-8.'];
    for (const name of ['posterUrl', 'trailerUrl']) {
      if (!data[name]) continue;
      try { const url = new URL(data[name]); if (data[name].length > 2048 || !['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password || url.port === '0') throw new Error(); }
      catch { errors[name] = ['Nhập URL HTTP/HTTPS tuyệt đối, tối đa 2048 ký tự, có host, không chứa thông tin đăng nhập; port từ 1–65535.']; }
    }
    data.genreIds = [...form.querySelectorAll('[name=genreIds]:checked')].map((node) => Number(node.value));
    if (!data.genreIds.length || data.genreIds.length > 100) errors.genreIds = ['Chọn từ 1 đến 100 thể loại.'];
    if (Object.keys(errors).length) { formError({ message: 'Kiểm tra các trường được đánh dấu.', fieldErrors: errors }); return null; }
    return data;
  }
  form.addEventListener('submit', async (event) => {
    event.preventDefault(); if (busy) return; clearErrors(); const body = validate(); if (!body) return;
    const isEdit = editingId !== null;
    if (isEdit && originalStatus === 'ACTIVE' && body.status === 'INACTIVE' && !window.confirm('Ngừng sử dụng phim này? Backend sẽ kiểm tra các suất chưa kết thúc.')) return;
    pending(true);
    try {
      await MovieApi.request(isEdit ? `/api/admin/movies/${editingId}` : '/api/admin/movies', { method: isEdit ? 'PUT' : 'POST', body });
      pending(false); $('editor').close(); notice(isEdit ? 'Đã cập nhật phim.' : 'Đã thêm phim.');
      await loadMovies({ afterSave: true });
    } catch (error) {
      if (authorizeError(error)) return;
      if (error.code === 'UNKNOWN_GENRES') { try { await loadGenres(); } catch (genreError) { notice(`Không tải lại được thể loại: ${errorText(genreError)}`, true); } }
      formError(error);
    } finally { pending(false); }
  });
  $('delete-form').addEventListener('submit', async (event) => {
    event.preventDefault(); if (busy || !deleting) return; pending(true); $('delete-error').hidden = true;
    try {
      await MovieApi.request(`/api/admin/movies/${deleting.id}`, { method: 'DELETE' });
      pending(false); $('delete-dialog').close(); notice('Đã xóa phim.'); await loadMovies({ afterSave: true });
    } catch (error) {
      if (!authorizeError(error)) { $('delete-error').textContent = errorText(error); $('delete-error').hidden = false; $('delete-error').focus(); }
    } finally { pending(false); }
  });
  for (const dialog of [$('editor'), $('delete-dialog')]) {
    dialog.addEventListener('cancel', (event) => { if (busy) event.preventDefault(); });
    dialog.addEventListener('keydown', (event) => {
      if (event.key !== 'Tab') return;
      const focusable = [...dialog.querySelectorAll('button,input,select,textarea,a[href],[tabindex="0"]')]
        .filter((node) => !node.disabled && node.getClientRects().length > 0);
      if (!focusable.length) { event.preventDefault(); return; }
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });
    dialog.addEventListener('close', () => {
      if (returnFocus?.isConnected) returnFocus.focus(); else $('add').focus();
    });
  }
  document.querySelectorAll('[data-close]').forEach((node) => node.addEventListener('click', () => { if (!busy) $(node.dataset.close).close(); }));
  $('add').addEventListener('click', () => openEditor(null, $('add')));
  $('filters').addEventListener('submit', (event) => { event.preventDefault(); if (!loading && !busy) { page = 0; loadMovies(); } });
  for (const id of ['status-filter', 'genre-filter', 'sort', 'size']) $(id).addEventListener('change', () => { page = 0; loadMovies(); });
  $('reset-filters').addEventListener('click', () => { $('filters').reset(); page = 0; loadMovies(); });
  $('query').addEventListener('search', () => { if (!$('query').value && !loading && !busy) { page = 0; loadMovies(); } });
  $('prev').addEventListener('click', () => { if (!loading && !busy && page > 0) { page--; loadMovies(); } });
  $('next').addEventListener('click', () => { if (!loading && !busy && page + 1 < totalPages) { page++; loadMovies(); } });
  $('reload').addEventListener('click', async () => {
    if (busy || loading) return; loading = true; controls();
    try { await loadGenres(); ready = true; } catch (error) { if (!authorizeError(error)) notice(errorText(error), true); }
    finally { loading = false; await loadMovies(); }
  });
  $('logout').addEventListener('click', async () => {
    if (busy) return; pending(true);
    try { await MovieApi.request('/api/auth/logout', { method: 'POST' }); MovieApi.clearSession(); location.replace('/login.html'); }
    catch (error) { if (!authorizeError(error)) notice(errorText(error), true); }
    finally { pending(false); }
  });
  (async () => {
    try {
      const user = await MovieApi.request('/api/auth/me');
      if (!user.roles?.includes('ADMIN')) throw new MovieApi.ApiError(403, { code: 'FORBIDDEN' });
      $('account').textContent = `${user.displayName || user.username} · ADMIN`;
      await loadGenres(); ready = true;
      if (!genres.length) notice('Chưa có thể loại. Nhờ ADMIN/Kha tạo thể loại để có thể thêm phim.', true);
      await loadMovies();
    } catch (error) {
      if (!authorizeError(error)) { state(`${errorText(error)} Bấm Tải lại để thử lại.`, true); controls(); }
    }
  })();
})();
