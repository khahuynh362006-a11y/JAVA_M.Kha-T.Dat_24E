AppReady.then(async user => {
  if (!user || !Auth.admin) return;
  const { node, button, page, filter, all, dialog, confirm, alert, errorText, badge } = UI;
  const view = page('Sơ đồ ghế vật lý', 'Thêm hoặc điều chỉnh ghế của phòng. Trạng thái đã đặt chỉ xuất hiện trong sơ đồ theo suất.');
  const panel = node('section', undefined, 'panel'); view.host.append(panel);
  let selectedRoom = '', busy = false, seats = [];
  const roomFilter = filter([{ name: 'roomId', label: 'Phòng chiếu', options: [['', 'Chọn phòng']] }], v => { selectedRoom = v.roomId; load(); }); panel.append(roomFilter.form);
  const toolbar = node('div', undefined, 'panel-title'); const add = button('Thêm ghế', () => edit()); const bulk = button('Tạo sơ đồ hàng loạt', bulkEdit, 'button primary'); const refresh = button('Tải lại sơ đồ', load); toolbar.append(add, bulk, refresh); panel.append(toolbar);
  const state = node('p', 'Chọn phòng để tải sơ đồ.', 'state'), grid = node('div', undefined, 'seat-board'); panel.append(state, grid);
  const legend = node('p', 'Chú giải: Thường · VIP · Ngừng sử dụng. Chọn ghế bằng Tab/Enter để sửa. Cấu trúc bị khóa khi phòng đã có lịch sử suất chiếu.', 'footnote'); view.host.append(legend);
  function lock(value) { busy = value; roomFilter.form.querySelectorAll('input,select,button').forEach(n => n.disabled = value); add.disabled = value || !selectedRoom; bulk.disabled = value || !selectedRoom || seats.length > 0; refresh.disabled = value || !selectedRoom; grid.querySelectorAll('button').forEach(n => n.disabled = value); }
  const url = () => '/api/admin/rooms/' + selectedRoom + '/seats';
  async function load() {
    if (busy) return; seats = []; grid.replaceChildren(); lock(true); state.hidden = false; state.textContent = selectedRoom ? 'Đang tải sơ đồ…' : 'Chọn phòng để tải sơ đồ.';
    try { if (!selectedRoom) return; const data = await Api.request(url()); seats = data.seats; state.textContent = `${data.room.name} · ${data.room.seatCount} ghế · ${data.room.activeSeatCount} đang sử dụng`; state.className = 'map-summary'; grid.append(node('div', 'MÀN HÌNH CHIẾU', 'screen'));
      if (!seats.length) grid.append(node('p', 'Phòng chưa có ghế. Có thể thêm ghế hoặc tạo sơ đồ hàng loạt.', 'state'));
      const rows = new Map(); for (const seat of seats) { if (!rows.has(seat.rowLabel)) rows.set(seat.rowLabel, []); rows.get(seat.rowLabel).push(seat); }
      for (const [rowLabel, items] of rows) { const row = node('div', undefined, 'seat-row'); row.append(node('span', rowLabel, 'row-label')); for (const seat of items) { const b = button(`${seat.code}\n${seat.seatType === 'VIP' ? 'VIP' : 'Thường'}${seat.status === 'INACTIVE' ? '\nNgừng' : ''}`, () => edit(seat), 'seat ' + (seat.status === 'INACTIVE' ? 'unavailable' : seat.seatType === 'VIP' ? 'vip' : '')); b.setAttribute('aria-label', `${seat.code}, ${seat.seatType}, ${UI.labels[seat.status]}`); row.append(b); } grid.append(row); }
    } catch (e) { state.textContent = errorText(e); state.className = 'state error'; }
    finally { lock(false); }
  }
  const fields = [{ name: 'rowLabel', label: 'Hàng ghế', required: true, pattern: '[A-Za-z]{1,4}', maxLength: 4, hint: '1–4 chữ ASCII, ví dụ A hoặc AA.' }, { name: 'seatNumber', label: 'Số ghế', type: 'number', required: true, min: 1, max: 65535 }, { name: 'seatType', label: 'Loại ghế', required: true, options: [['STANDARD', 'Thường'], ['VIP', 'VIP']] }, { name: 'status', label: 'Trạng thái', required: true, options: [['ACTIVE', 'Đang sử dụng'], ['INACTIVE', 'Ngừng sử dụng']] }];
  function edit(seat) {
    if (!selectedRoom || busy) return; const room = selectedRoom;
    const formView = dialog(seat ? 'Sửa ghế ' + seat.code : 'Thêm ghế', fields, seat || {}, { onSave: async body => { if (seat?.status === 'ACTIVE' && body.status === 'INACTIVE' && !window.confirm('Ngừng sử dụng ghế ' + seat.code + '?')) return false; await Api.request('/api/admin/rooms/' + room + '/seats' + (seat ? '/' + seat.id : ''), { method: seat ? 'PUT' : 'POST', body }); alert('Đã lưu ghế.'); await load(); } });
    if (seat) formView.body.append(button('Xóa ghế này', () => confirm('Xóa ghế ' + seat.code + '?', 'Chỉ xóa ghế khi backend cho phép. Không tự xóa lịch sử.', async () => { await Api.request('/api/admin/rooms/' + room + '/seats/' + seat.id, { method: 'DELETE' }); formView.dialog.close(); alert('Đã xóa ghế.'); await load(); }), 'button delete'));
  }
  function bulkEdit() {
    const room = selectedRoom;
    dialog('Tạo sơ đồ hàng loạt', [{ name: 'rows', label: 'Các hàng, ngăn cách bằng dấu phẩy', required: true, value: 'A,B,C', hint: 'Từ 1–26 hàng, không trùng. Chỉ dùng cho phòng chưa có ghế và chưa có suất.', wide: true }, { name: 'seatsPerRow', label: 'Ghế mỗi hàng', type: 'number', required: true, min: 1, max: 50, value: 10 }, { name: 'seatType', label: 'Loại ghế ban đầu', options: [['STANDARD', 'Thường'], ['VIP', 'VIP']] }], {}, { onSave: async values => {
      const rows = values.rows.split(',').map(s => s.trim().toUpperCase());
      if (!rows.length || rows.length > 26 || rows.some(r => !/^[A-Z]{1,4}$/.test(r)) || new Set(rows).size !== rows.length || rows.length * values.seatsPerRow > 500) throw new Error('Hàng không hợp lệ, bị trùng hoặc tổng vượt 500 ghế.');
      await Api.request('/api/admin/rooms/' + room + '/seats/bulk', { method: 'POST', body: { rows, seatsPerRow: values.seatsPerRow, seatType: values.seatType } }); alert('Đã tạo sơ đồ ghế.'); await load();
    } });
  }
  try { const rooms = await all('/api/admin/rooms'); for (const r of rooms) roomFilter.controls.roomId.add(new Option(`${r.code} · ${r.name}`, r.id)); const initial = new URLSearchParams(location.search).get('roomId'); if (rooms.some(r => String(r.id) === initial)) { selectedRoom = initial; roomFilter.controls.roomId.value = initial; } }
  catch (e) { alert(errorText(e), true); }
  roomFilter.controls.roomId.addEventListener('change', () => { selectedRoom = roomFilter.controls.roomId.value; load(); }); await load();
}).catch(e => UI.alert(UI.errorText(e), true));
