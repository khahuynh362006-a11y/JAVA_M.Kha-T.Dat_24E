AppReady.then(async user => {
  if (!user) return;
  const { node, button, page, filter, all, dialog, confirm, detail, table, pager, badge, money, time, alert, errorText, query, actions, link } = UI;
  const base = Auth.admin ? '/api/admin/showtimes' : '/api/showtimes';
  const view = page('Lịch chiếu', 'Ngày lọc là ngày bắt đầu suất theo giờ Việt Nam. Giữa hai suất cùng phòng cần ít nhất 15 phút nghỉ.');
  let movies = [], rooms = [], filters = {}, list;
  const panel = node('section', undefined, 'panel'); view.host.append(panel);
  const filterView = filter([{ name: 'date', label: 'Ngày bắt đầu', type: 'date', value: UI.today() }, { name: 'movieId', label: 'Phim', options: [['', 'Tất cả phim']] }, { name: 'roomId', label: 'Phòng', options: [['', 'Tất cả phòng']] }, { name: 'status', label: 'Trạng thái', options: [['', 'Tất cả'], ['SCHEDULED', 'Đã lên lịch'], ['CANCELLED', 'Đã hủy']] }], values => { filters = values; list.reset(); });
  panel.append(filterView.form); filters = filterView.values();
  const columns = [{ title: 'Phim / Phòng', render: s => `${s.movieTitle} · ${s.roomCode}` }, { title: 'Bắt đầu → Kết thúc (VN)', render: s => `${time(s.startsAt)} → ${time(s.endsAt)}` }, { title: 'Giá thường / VIP', render: s => `${money(s.standardPrice)} / ${money(s.vipPrice)}` }, { title: 'Trạng thái', render: s => `${UI.labels[s.status]} · ${UI.labels[s.phase]}` }, { title: 'Thao tác', render: s => actions(button('Chi tiết', () => read(s.id)), s.status === 'SCHEDULED' && s.phase === 'UPCOMING' && link('Đặt ghế', '/admin/bookings.html?showtimeId=' + s.id)) }];
  const add = button('＋ Tạo suất chiếu', () => edit(), 'button primary'); if (Auth.admin) { add.disabled = true; view.heading.append(add); }
  list = pager(panel, (p, size) => Api.request(base + '?' + query({ ...filters, page: p, size, sort: 'startsAt', direction: 'asc' })), { render: items => table(columns, items), lock: value => { filterView.form.querySelectorAll('input,select,button').forEach(n => n.disabled = value); if (Auth.admin) add.disabled = value || !movies.length || !rooms.length; } });
  async function read(id) {
    try {
      const s = await Api.request(base + '/' + id); const links = s._links || {}; let d;
      const permitted = [];
      if (Auth.admin && links.update) permitted.push(button('Sửa suất', () => { d.close(); edit(s); }));
      if (Auth.admin && links.cancel) permitted.push(button('Hủy suất', () => { d.close(); confirm('Hủy suất chiếu?', `${s.movieTitle} · ${time(s.startsAt)}. Suất có lịch sử đặt chỗ sẽ bị từ chối hủy.`, async body => { await Api.request(base + '/' + s.id + '/cancel', { method: 'POST', body }); alert('Đã hủy suất.'); await list.refreshAfterWrite(); }, true); }, 'button delete'));
      if (Auth.admin && links.delete) permitted.push(button('Xóa suất', () => { d.close(); confirm('Xóa suất chiếu?', 'Chỉ xóa suất chưa có lịch sử và chưa bắt đầu.', async () => { await Api.request(base + '/' + s.id, { method: 'DELETE' }); alert('Đã xóa suất.'); await list.refreshAfterWrite(); }); }, 'button delete'));
      if (s.status === 'SCHEDULED' && s.phase === 'UPCOMING') permitted.push(link('Đặt ghế', '/admin/bookings.html?showtimeId=' + s.id, 'button primary'));
      d = detail('Chi tiết suất #' + s.id, [['Phim', s.movieTitle], ['Phòng', `${s.roomCode} · ${s.roomName}`], ['Bắt đầu (VN)', time(s.startsAt)], ['Kết thúc (VN)', time(s.endsAt)], ['Thời lượng / Tuổi', `${s.durationMinutes} phút / ${s.minimumAge}+`], ['Giá thường', money(s.standardPrice)], ['Giá VIP', money(s.vipPrice)], ['Trạng thái', `${UI.labels[s.status]} · ${UI.labels[s.phase]}`], ['Thao tác quản trị', permitted.length ? 'Các thao tác hợp lệ được hiển thị bên dưới.' : 'Chỉ đọc; không có hành động chỉnh sửa được backend cấp.']], permitted);
    } catch (e) { alert(errorText(e), true); }
  }
  function edit(s) {
    const fields = [{ name: 'movieId', valueType: 'number', label: 'Phim đang sử dụng', required: true, options: movies.map(m => [m.id, `${m.code} · ${m.title} (${m.durationMinutes} phút)`]) }, { name: 'roomId', valueType: 'number', label: 'Phòng đang sử dụng', required: true, options: rooms.map(r => [r.id, `${r.code} · ${r.name}`]) }, { name: 'startsAt', label: 'Bắt đầu (giờ Việt Nam)', type: 'datetime-local', required: true, step: 60, wide: true }, { name: 'standardPrice', label: 'Giá thường (VND)', type: 'number', min: 0, max: 999999999999, required: true, value: 80000 }, { name: 'vipPrice', label: 'Giá VIP (VND)', type: 'number', min: 0, max: 999999999999, required: true, value: 100000 }];
    const initial = s ? { ...s, startsAt: s.startsAt.slice(0, 16) } : {};
    const formView = dialog(s ? 'Sửa suất #' + s.id : 'Tạo suất chiếu', fields, initial, { description: 'Giờ kết thúc do backend tính. Giá VIP phải lớn hơn hoặc bằng giá thường. Không gửi trạng thái hoặc giờ kết thúc từ trình duyệt.', onSave: async body => {
      if (body.vipPrice < body.standardPrice) throw new Error('Giá VIP phải lớn hơn hoặc bằng giá thường.');
      body.startsAt += ':00+07:00';
      await Api.request(base + (s ? '/' + s.id : ''), { method: s ? 'PUT' : 'POST', body }); alert('Đã lưu suất chiếu.'); await list.refreshAfterWrite();
    } });
    const preview = node('p', 'Chọn phim và giờ để xem giờ kết thúc dự kiến.', 'footnote'); formView.body.append(preview);
    function update() { const m = movies.find(x => x.id === Number(formView.controls.movieId.value)), value = formView.controls.startsAt.value; if (!m || !value) return; const ends = new Date(new Date(value + ':00+07:00').getTime() + m.durationMinutes * 60000); if (!Number.isNaN(ends.getTime())) preview.textContent = `Kết thúc dự kiến: ${time(ends.toISOString())}. Nghỉ tối thiểu 15 phút; backend sẽ kiểm tra xung đột.`; }
    formView.controls.movieId.addEventListener('change', update); formView.controls.startsAt.addEventListener('input', update); update();
  }
  try {
    if (Auth.admin) [movies, rooms] = await Promise.all([all('/api/admin/movies', { status: 'ACTIVE' }), all('/api/admin/rooms', { status: 'ACTIVE' })]);
    else { const shows = await all('/api/showtimes'); movies = [...new Map(shows.map(s => [s.movieId, { id: s.movieId, title: s.movieTitle }])).values()]; rooms = [...new Map(shows.map(s => [s.roomId, { id: s.roomId, code: s.roomCode, name: s.roomName }])).values()]; }
    for (const m of movies) filterView.controls.movieId.add(new Option(m.title, m.id)); for (const r of rooms) filterView.controls.roomId.add(new Option(`${r.code} · ${r.name}`, r.id));
  } catch (e) { alert('Không tải được dữ liệu chọn phim/phòng: ' + errorText(e), true); }
  await list.run();
}).catch(e => UI.alert(UI.errorText(e), true));
