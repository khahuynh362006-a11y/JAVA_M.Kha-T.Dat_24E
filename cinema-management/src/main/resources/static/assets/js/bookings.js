AppReady.then(async user => {
  if (!user) return;
  const { node, button, page, filter, all, table, pager, money, time, badge, alert, errorText, query, confirm, detail, actions } = UI;
  const view = page('Đặt chỗ & tra cứu', 'Đặt chỗ tại quầy. Ghế đang chọn chưa được giữ; giá và kết quả chính thức do máy chủ xác nhận.');
  const createPanel = node('section', undefined, 'panel booking-panel'); createPanel.append(node('h2', '1. Chọn suất chiếu', 'section-heading')); view.host.append(createPanel);
  const storageKey = 'cinema-booking-intent-' + user.id;
  let intent = null, map = null, selected = new Set(), busy = false, loading = false, currentId = '', shows = [];
  try { const saved = JSON.parse(sessionStorage.getItem(storageKey)); if (saved && /^[0-9a-f-]{36}$/i.test(saved.key) && Number.isSafeInteger(saved.body?.showtimeId) && Array.isArray(saved.body.seatIds) && Number.isSafeInteger(saved.body.priceVersion) && typeof saved.body.ageChecked === 'boolean') intent = saved; } catch { /* Invalid local state is ignored. */ }
  const controls = filter([{ name: 'date', label: 'Ngày bắt đầu suất', type: 'date', value: UI.today() }, { name: 'showtimeId', label: 'Suất chiếu', options: [['', 'Chọn suất']] }], () => loadMap(controls.controls.showtimeId.value));
  createPanel.append(controls.form);
  const mapInfo = node('p', 'Chọn ngày và suất để tải ghế.', 'map-summary'); const board = node('div', undefined, 'seat-board');
  const refresh = button('Tải lại ghế / giá', () => loadMap(currentId, true)); createPanel.append(mapInfo, refresh, board);
  const legend = node('p', 'Chú giải: Trống · Đã đặt · Không sử dụng · Đang chọn. VIP được ghi trên từng ghế.', 'footnote'); createPanel.append(legend);
  const summary = node('div', undefined, 'booking-summary'); const chosen = node('p', 'Chưa chọn ghế.'), total = node('p', 'Tổng dự kiến: —');
  const ageLabel = node('label', undefined, 'check-label'), age = node('input'); age.type = 'checkbox'; age.id = 'age-checked'; ageLabel.append(age, node('span', 'Đã kiểm tra tuổi khách tại quầy theo tuổi tối thiểu của suất.'));
  const submit = button('Xem lại và xác nhận', review, 'button primary'), retry = button('Kiểm tra kết quả / gửi lại cùng mã yêu cầu', () => sendIntent(), 'button');
  const pendingInfo = node('p', undefined, 'error-summary'); pendingInfo.hidden = true; retry.hidden = true;
  summary.append(chosen, total, ageLabel, pendingInfo, actions(submit, retry)); createPanel.append(summary);
  const result = node('div', undefined, 'booking-result'); result.hidden = true; view.host.append(result);
  const lookup = node('section', undefined, 'panel'); lookup.append(node('h2', '2. Tra cứu đặt chỗ', 'section-heading')); view.host.append(lookup);
  let listFilters = {};
  const listFilter = filter([{ name: 'q', label: 'Tìm mã đặt chỗ' }, { name: 'showtimeId', label: 'ID suất (tùy chọn)', type: 'number' }, { name: 'status', label: 'Trạng thái', options: [['', 'Tất cả'], ['CONFIRMED', 'Đã đặt'], ['CANCELLED', 'Đã hủy']] }], v => { listFilters = v; bookingList.reset(); });
  lookup.append(listFilter.form);
  const exact = button('Tra cứu đúng mã', async () => { const code = listFilter.controls.q.value.trim(); if (!code || code.length > 40) return alert('Nhập đúng mã đặt chỗ, tối đa 40 ký tự.', true); exact.disabled = true; try { showDetail(await Api.request('/api/bookings/by-code/' + encodeURIComponent(code))); } catch (e) { alert(errorText(e), true); } finally { exact.disabled = false; } }); listFilter.form.append(exact);
  const bookingList = pager(lookup, (p, size) => Api.request('/api/bookings?' + query({ ...listFilters, page: p, size, sort: 'createdAt', direction: 'desc' })), { render: items => table([{ title: 'Mã', key: 'code' }, { title: 'Phim / Phòng', render: b => `${b.movieTitle} · ${b.roomCode}` }, { title: 'Bắt đầu (VN)', render: b => time(b.startsAt) }, { title: 'Ghế / Giá trị', render: b => `${b.seats.map(s => s.code).join(', ')} · ${money(b.totalAmount)}` }, { title: 'Trạng thái', render: b => badge(b.status) }, { title: 'Thao tác', render: b => button('Chi tiết', async () => { try { showDetail(await Api.request('/api/bookings/' + b.id)); } catch (e) { alert(errorText(e), true); } }) }], items), lock: value => listFilter.form.querySelectorAll('input,select,button').forEach(n => n.disabled = value) });
  function lock() {
    const blocked = busy || loading || !!intent;
    controls.form.querySelectorAll('input,select,button').forEach(n => n.disabled = blocked);
    board.querySelectorAll('button').forEach(n => n.disabled = blocked || n.dataset.available !== 'true');
    age.disabled = blocked || !map; refresh.disabled = busy || loading || !currentId;
    submit.disabled = blocked || !map?.bookable || selected.size === 0 || selected.size > 20 || (map.showtime.minimumAge > 0 && !age.checked);
    retry.hidden = !intent; retry.disabled = busy || loading; pendingInfo.hidden = !intent;
    pendingInfo.textContent = intent ? 'Có yêu cầu đang chờ xác minh kết quả. Giữ nguyên suất, ghế và giá; bấm kiểm tra/gửi lại cùng mã để tránh tạo trùng. Không tạo đơn mới cho cùng ý định trước khi biết kết quả.' : '';
  }
  function selection() {
    const seats = (map?.seats || []).filter(s => selected.has(s.id));
    chosen.textContent = seats.length ? `Ghế đang chọn (${seats.length}/20): ${seats.map(s => s.code).join(', ')}` : 'Chưa chọn ghế.';
    total.textContent = 'Tổng dự kiến: ' + (seats.length ? money(seats.reduce((sum, s) => sum + s.price, 0)) : '—');
    for (const b of board.querySelectorAll('button')) { const active = selected.has(Number(b.dataset.id)); b.classList.toggle('selected', active); b.setAttribute('aria-pressed', String(active)); }
    lock();
  }
  async function loadShows(date) {
    if (intent || busy) return; loading = true; map = null; currentId = ''; selected.clear(); board.replaceChildren(); controls.controls.showtimeId.replaceChildren(new Option('Chọn suất', '')); lock();
    try { shows = await all('/api/showtimes', { date, status: 'SCHEDULED', sort: 'startsAt', direction: 'asc' });
      for (const s of shows.filter(s => s.phase === 'UPCOMING')) controls.controls.showtimeId.add(new Option(`${time(s.startsAt)} · ${s.movieTitle} · ${s.roomCode}`, s.id));
      mapInfo.textContent = controls.controls.showtimeId.options.length > 1 ? 'Chọn suất để tải sơ đồ.' : 'Ngày này không có suất sắp chiếu còn mở.';
    } catch (e) { mapInfo.textContent = errorText(e); }
    finally { loading = false; lock(); }
  }
  async function loadMap(id, preserve = false) {
    if (busy || loading) return; if (!id) { map = null; board.replaceChildren(); selected.clear(); currentId = ''; selection(); return; }
    loading = true; currentId = String(id); board.replaceChildren(); mapInfo.textContent = 'Đang tải ghế và giá…'; lock();
    try {
      const data = await Api.request('/api/showtimes/' + id + '/seats'); map = data;
      if (!preserve) { selected.clear(); age.checked = false; }
      else if (!intent) selected = new Set([...selected].filter(id => data.seats.some(s => s.id === id && s.availability === 'AVAILABLE')));
      mapInfo.textContent = `${data.showtime.movieTitle} · ${data.showtime.roomCode} · ${time(data.showtime.startsAt)} → ${time(data.showtime.endsAt)} · Tuổi tối thiểu ${data.showtime.minimumAge}+${data.bookable ? '' : ' · Suất hiện không cho đặt'}`;
      board.append(node('div', 'MÀN HÌNH CHIẾU', 'screen'));
      if (!data.seats.length) board.append(node('p', 'Không có ghế trong sơ đồ.', 'state'));
      let rowLabel = null, row;
      for (const seat of data.seats) {
        if (seat.rowLabel !== rowLabel) { rowLabel = seat.rowLabel; row = node('div', undefined, 'seat-row'); row.append(node('span', rowLabel, 'row-label')); board.append(row); }
        const b = button(`${seat.code}\n${seat.seatType === 'VIP' ? 'VIP' : 'Thường'}\n${UI.labels[seat.availability]}`, () => {
          if (busy || loading || intent || seat.availability !== 'AVAILABLE') return;
          if (selected.has(seat.id)) selected.delete(seat.id); else { if (selected.size >= 20) return alert('Mỗi đơn tối đa 20 ghế.', true); selected.add(seat.id); }
          selection();
        }, 'seat ' + (seat.availability !== 'AVAILABLE' ? 'unavailable' : seat.seatType === 'VIP' ? 'vip' : ''));
        b.dataset.id = seat.id; b.dataset.available = String(seat.availability === 'AVAILABLE' && data.bookable); b.setAttribute('aria-label', `${seat.code}, ${UI.labels[seat.seatType]}, ${UI.labels[seat.availability]}, ${money(seat.price)}`); row.append(b);
      }
    } catch (e) { map = null; mapInfo.textContent = errorText(e); }
    finally { loading = false; selection(); }
  }
  function review() {
    if (submit.disabled) return;
    const body = { showtimeId: map.showtime.id, seatIds: [...selected].sort((a, b) => a - b), priceVersion: map.showtime.priceVersion, ageChecked: age.checked };
    const text = `${map.showtime.movieTitle} · ${time(map.showtime.startsAt)} · Ghế ${map.seats.filter(s => selected.has(s.id)).map(s => s.code).join(', ')}. ${total.textContent}. Đây là đặt chỗ, chưa phải thanh toán.`;
    confirm('Xác nhận đặt chỗ?', text, async () => {
      if (!intent) { intent = { key: crypto.randomUUID(), body }; sessionStorage.setItem(storageKey, JSON.stringify(intent)); }
      await sendIntent(true);
    });
  }
  async function sendIntent(fromDialog = false) {
    if (!intent || busy) return; busy = true; lock(); const current = intent;
    try {
      const booking = await Api.request('/api/bookings', { method: 'POST', headers: { 'Idempotency-Key': current.key }, body: current.body });
      sessionStorage.removeItem(storageKey); intent = null; selected.clear(); age.checked = false;
      result.hidden = false; result.replaceChildren(node('h2', 'Kết quả đặt chỗ'), node('p', `Mã: ${booking.code} · ${UI.labels[booking.status]} · ${booking.seats.map(s => s.code).join(', ')} · Giá trị chính thức: ${money(booking.totalAmount)}`), button('Xem chi tiết', () => showDetail(booking)));
      alert(booking.status === 'CANCELLED' ? 'Đã tìm lại đơn cũ đã hủy. Không có ghế nào được đặt lại.' : 'Đã xác nhận đặt chỗ. Chưa có thao tác thu tiền.');
      busy = false; await loadMap(current.body.showtimeId, true); await bookingList.refreshAfterWrite();
    } catch (e) {
      const uncertain = e.status === 0 || e.status >= 500 || e.code === 'INVALID_RESPONSE' || e.status === 401;
      if (!uncertain) { sessionStorage.removeItem(storageKey); intent = null; }
      busy = false;
      if (!uncertain && e.status === 409) { age.checked = false; await loadMap(current.body.showtimeId, true); }
      alert(errorText(e) + (uncertain ? ' Giữ nguyên yêu cầu để kiểm tra/gửi lại cùng mã.' : ''), true);
      // Definite rejection closes the old review so refreshed prices/seats must be
      // reviewed again. An uncertain request keeps the original body and key.
      if (fromDialog && uncertain) throw e;
    } finally { busy = false; lock(); }
  }
  function showDetail(b) {
    const buttons = []; let d;
    if (Auth.admin && b.status === 'CONFIRMED' && new Date(b.startsAt) > new Date()) buttons.push(button('Hủy đặt chỗ', () => {
      d.close(); confirm('Hủy toàn bộ đặt chỗ?', `${b.code} · ${b.seats.map(s => s.code).join(', ')}. Lịch sử và giá được giữ; ghế được giải phóng sau khi backend xác nhận. Không có hoàn tiền trong chức năng này.`, async body => { const updated = await Api.request('/api/bookings/' + b.id + '/cancel', { method: 'POST', body }); alert('Đã hủy đặt chỗ.'); if (currentId === String(b.showtimeId)) await loadMap(currentId, true); await bookingList.refreshAfterWrite(); showDetail(updated); }, true);
    }, 'button delete'));
    d = detail('Đặt chỗ ' + b.code, [['Phim', b.movieTitle], ['Phòng', b.roomCode], ['Bắt đầu (VN)', time(b.startsAt)], ['Kết thúc (VN)', time(b.endsAt)], ['Ghế / đơn giá', b.seats.map(s => `${s.code} · ${UI.labels[s.seatType]} · ${money(s.unitPrice)} · ${UI.labels[s.bookingStatus]}`).join('\n')], ['Giá trị đặt chỗ', money(b.totalAmount)], ['Trạng thái', UI.labels[b.status]], ['Người tạo', b.createdByName], ['Ngày tạo (VN)', time(b.createdAt)], ['Ngày hủy (VN)', time(b.cancelledAt)], ['Người hủy (ID)', b.cancelledBy], ['Lý do hủy', b.cancelReason], ['Quyền hủy', Auth.admin ? 'ADMIN, khi backend cho phép' : 'STAFF chưa được cấp quyền hủy']], buttons);
  }
  age.addEventListener('change', selection);
  controls.controls.date.addEventListener('change', () => loadShows(controls.controls.date.value)); controls.controls.showtimeId.addEventListener('change', () => loadMap(controls.controls.showtimeId.value));
  const initialId = intent?.body.showtimeId || new URLSearchParams(location.search).get('showtimeId');
  if (intent) { currentId = String(intent.body.showtimeId); selected = new Set(intent.body.seatIds); age.checked = intent.body.ageChecked; await loadMap(currentId, true); }
  else if (initialId) { try { const s = await Api.request('/api/showtimes/' + initialId); controls.controls.date.value = s.startsAt.slice(0, 10); await loadShows(controls.controls.date.value); controls.controls.showtimeId.value = String(initialId); await loadMap(initialId); } catch (e) { alert(errorText(e), true); } }
  else await loadShows(UI.today());
  await bookingList.run(); lock();
}).catch(e => UI.alert(UI.errorText(e), true));
