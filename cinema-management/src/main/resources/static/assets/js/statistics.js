AppReady.then(async user => {
  if (!user || !Auth.admin) return;
  const { node, page, filter, pager, table, time, money, alert, errorText, query, all } = UI;
  const dashboard = document.body.dataset.page === 'dashboard';
  const view = page(dashboard ? 'Tổng quan rạp' : 'Thống kê', 'Dữ liệu thật từ backend. Ngày/giờ Việt Nam; giá trị đặt chỗ không phải doanh thu đã thu.');
  const panel = node('section', undefined, 'panel'); view.host.append(panel);
  const reports = {
    showtimes: { title: 'Suất chiếu theo ngày', note: 'Đếm theo ngày bắt đầu suất, gồm lịch đã hủy.', columns: [{ title: 'Ngày', key: 'date' }, { title: 'Tổng suất', key: 'totalShowtimes' }, { title: 'Đã lên lịch', key: 'scheduledShowtimes' }, { title: 'Đã hủy', key: 'cancelledShowtimes' }] },
    'booked-seats': { title: 'Đặt chỗ theo phim', note: 'Chỉ suất SCHEDULED và đơn/ghế CONFIRMED, theo ngày bắt đầu suất. Giá trị đặt chỗ không phải tiền đã thu.', columns: [{ title: 'Phim', key: 'movieTitle' }, { title: 'Đơn còn hiệu lực', key: 'activeBookings' }, { title: 'Ghế đã đặt', key: 'bookedSeats' }, { title: 'Giá trị đặt chỗ (VND)', render: r => money(r.bookingValue) }] },
    occupancy: { title: 'Tỷ lệ lấp đầy theo suất', note: 'Sức chứa trước giờ chiếu có thể tạm tính theo ghế ACTIVE. Không đổi dữ liệu thiếu thành 0% hoặc giới hạn tỷ lệ ở 100%.', columns: [{ title: 'Suất / Phim', render: r => `#${r.showtimeId} · ${r.movieTitle}` }, { title: 'Phòng / Bắt đầu', render: r => `${r.roomCode} · ${time(r.startsAt)}` }, { title: 'Đã đặt / Sức chứa', render: r => `${r.bookedSeats} / ${r.capacity == null ? 'Chưa có' : r.capacity}` }, { title: 'Tỷ lệ / Cơ sở', render: r => `${r.occupancyPercent == null ? r.capacityBasis === 'MISSING_HISTORICAL_CAPACITY' ? 'Chưa có dữ liệu sức chứa lịch sử' : 'Không áp dụng' : r.occupancyPercent + '%'} · ${r.capacityBasis === 'CURRENT_ACTIVE_SEATS_PROVISIONAL' ? 'Sức chứa tạm tính' : r.capacityBasis === 'SNAPSHOT' ? 'Sức chứa chụp tại suất' : 'Thiếu snapshot'}${r.anomaly ? ' · CẢNH BÁO: Ghế đặt vượt sức chứa' : ''}` }] },
    cancellations: { title: 'Hủy đặt chỗ theo ngày', note: 'Đếm theo ngày hủy, gồm mọi trạng thái suất. Giá trị hủy không phải tiền đã hoàn.', columns: [{ title: 'Ngày hủy', key: 'date' }, { title: 'Đơn hủy', key: 'cancelledBookings' }, { title: 'Ghế hủy', key: 'cancelledSeats' }, { title: 'Giá trị đã hủy (VND)', render: r => money(r.cancelledBookingValue) }] }
  };
  let filters = { from: UI.today(), to: UI.today() }, report = 'showtimes', loadDashboard = async () => {};
  const fields = [{ name: 'from', label: dashboard ? 'Ngày' : 'Từ ngày', type: 'date', value: UI.today() }, ...(!dashboard ? [{ name: 'to', label: 'Đến ngày', type: 'date', value: UI.today() }, { name: 'movieId', label: 'Phim', options: [['', 'Tất cả phim']] }, { name: 'roomId', label: 'Phòng', options: [['', 'Tất cả phòng']] }, { name: 'report', label: 'Báo cáo', options: Object.entries(reports).map(([id, r]) => [id, r.title]) }] : [])];
  const filterView = filter(fields, values => {
    if (!values.from || (!dashboard && !values.to)) return alert('Chọn đủ ngày báo cáo.', true);
    const to = dashboard ? values.from : values.to, days = (new Date(to) - new Date(values.from)) / 86400000;
    if (days < 0 || days > 30) return alert('Khoảng báo cáo từ 1–31 ngày, gồm cả hai đầu.', true);
    report = values.report || 'showtimes'; filters = { from: values.from, to, movieId: values.movieId, roomId: values.roomId };
    dashboard ? loadDashboard() : list.reset();
  }); panel.append(filterView.form);
  const heading = node('h2', undefined, 'section-heading'), note = node('p', undefined, 'report-note'), asOf = node('p', undefined, 'report-note'); panel.append(heading, note, asOf);
  let list;
  const lockFilters = value => filterView.form.querySelectorAll('input,select,button').forEach(n => n.disabled = value);
  if (!dashboard) {
    list = pager(panel, async (p, size) => {
      heading.textContent = reports[report].title; note.textContent = reports[report].note;
      const data = await Api.request('/api/admin/statistics/' + report + '?' + query({ ...filters, page: p, size }));
      asOf.textContent = `Cập nhật: ${time(data.asOf)} · ${data.timeZone} · ${data.timeBasis === 'CANCELLATION_DATE' ? 'Theo ngày hủy' : 'Theo ngày bắt đầu suất'}`; return data;
    }, { render: items => table(reports[report].columns, items), lock: lockFilters });
    try { const [movies, rooms] = await Promise.all([all('/api/admin/movies'), all('/api/admin/rooms')]); for (const m of movies) filterView.controls.movieId.add(new Option(m.title, m.id)); for (const r of rooms) filterView.controls.roomId.add(new Option(r.code + ' · ' + r.name, r.id)); } catch (e) { alert(errorText(e), true); }
    await list.run();
  } else {
    const content = node('div', undefined, 'dashboard-content'); panel.append(content);
    async function load() {
      lockFilters(true); content.replaceChildren(node('p', 'Đang tải tổng quan…', 'state'));
      try {
        const [showtimes, booked, cancelled, occupancy] = await Promise.all(['showtimes', 'booked-seats', 'cancellations', 'occupancy'].map(r => all('/api/admin/statistics/' + r, filters)));
        const sum = (items, key) => items.reduce((total, r) => total + r[key], 0);
        const metrics = node('div', undefined, 'metrics');
        for (const [label, value] of [['Suất chưa hủy (ngày bắt đầu)', sum(showtimes, 'scheduledShowtimes')], ['Đơn hiệu lực trên suất chưa hủy', sum(booked, 'activeBookings')], ['Ghế đang được đặt', sum(booked, 'bookedSeats')], ['Đơn hủy trong ngày', sum(cancelled, 'cancelledBookings')]]) { const tile = node('div', undefined, 'metric'); tile.append(node('p', label), node('strong', value)); metrics.append(tile); }
        content.replaceChildren(metrics, node('h2', 'Phim được đặt nhiều (theo số ghế)', 'section-heading'), table(reports['booked-seats'].columns, booked.sort((a, b) => b.bookedSeats - a.bookedSeats)), node('h2', 'Lấp đầy theo suất', 'section-heading'), table(reports.occupancy.columns, occupancy));
        note.textContent = 'Các số tổng hợp từ đủ các trang API của ngày được chọn. Đơn hiệu lực theo ngày bắt đầu suất; đơn hủy theo ngày hủy. Tỷ lệ lấp đầy được hiển thị theo từng suất.';
      } catch (e) { content.replaceChildren(node('p', errorText(e), 'state error'), UI.button('Thử lại', load)); }
      finally { lockFilters(false); }
    }
    loadDashboard = load; await load();
  }
}).catch(e => UI.alert(UI.errorText(e), true));
