AppReady.then(async user => {
  if (!user || !Auth.admin) return;
  const { node, button, table, actions, badge, dialog, confirm, page, filter, pager, query, alert, link, errorText } = UI;
  const isGenre = document.body.dataset.page === 'genres'; const resource = isGenre ? 'genres' : 'rooms'; const name = isGenre ? 'thể loại' : 'phòng chiếu';
  const base = '/api/admin/' + resource; const view = page('Quản lý ' + name, isGenre ? 'Danh mục thể loại dùng chung cho phim.' : 'Quản lý phòng và số ghế vật lý. Số ghế không phải số chỗ trống của suất.');
  const fields = isGenre ? [{ name: 'name', label: 'Tên thể loại', required: true, maxLength: 100 }, { name: 'description', label: 'Mô tả', type: 'textarea', maxLength: 500, wide: true }] :
    [{ name: 'code', label: 'Mã phòng', required: true, maxLength: 32, pattern: '[A-Za-z0-9._-]{1,32}', hint: 'Chữ ASCII, số, dấu chấm, gạch dưới hoặc gạch ngang.' }, { name: 'name', label: 'Tên phòng', required: true, maxLength: 100 }, { name: 'status', label: 'Trạng thái', required: true, options: [['ACTIVE', 'Đang sử dụng'], ['INACTIVE', 'Ngừng sử dụng']], value: 'ACTIVE' }];
  const panel = node('section', undefined, 'panel'); view.host.append(panel); let filters = {};
  const filterView = filter([{ name: 'q', label: 'Tìm tên / mã' }, ...(!isGenre ? [{ name: 'status', label: 'Trạng thái', options: [['', 'Tất cả'], ['ACTIVE', 'Đang sử dụng'], ['INACTIVE', 'Ngừng sử dụng']] }] : [])], values => { filters = values; list.reset(); }); panel.append(filterView.form);
  function edit(item) { dialog((item ? 'Sửa ' : 'Thêm ') + name, fields, item || {}, { onSave: async body => {
    if (item?.status === 'ACTIVE' && body.status === 'INACTIVE' && !window.confirm('Ngừng sử dụng phòng “' + item.name + '”? Backend sẽ kiểm tra các suất liên quan.')) return false;
    await Api.request(base + (item ? '/' + item.id : ''), { method: item ? 'PUT' : 'POST', body }); alert('Đã lưu ' + name + '.'); await list.refreshAfterWrite();
  } }); }
  async function readEdit(item) { try { edit(await Api.request(base + '/' + item.id)); } catch (e) { alert(errorText(e), true); } }
  const columns = [ ...(!isGenre ? [{ title: 'Mã phòng', key: 'code' }] : []), { title: 'Tên', key: 'name' }, ...(isGenre ? [{ title: 'Mô tả', key: 'description' }] : [{ title: 'Trạng thái', render: x => badge(x.status) }, { title: 'Ghế vật lý / hoạt động', render: x => `${x.seatCount} / ${x.activeSeatCount}` }]),
    { title: 'Thao tác', render: item => actions(button('Sửa', () => readEdit(item)), !isGenre && link('Sơ đồ ghế', '/admin/seats.html?roomId=' + item.id), button('Xóa', () => confirm('Xóa ' + name + '?', `Xóa “${item.name}”? Dữ liệu có liên kết sẽ được backend bảo vệ.`, async () => { await Api.request(base + '/' + item.id, { method: 'DELETE' }); alert('Đã xóa ' + name + '.'); await list.refreshAfterWrite(); }), 'button delete')) } ];
  const add = button('＋ Thêm ' + name, () => edit(), 'button primary'); view.heading.append(add);
  const list = pager(panel, (p, size) => Api.request(base + '?' + query({ ...filters, page: p, size, sort: 'id', direction: 'desc' })), { render: items => table(columns, items), lock: value => { add.disabled = value; filterView.form.querySelectorAll('input,select,button').forEach(n => n.disabled = value); } });
  await list.run();
}).catch(e => UI.alert(UI.errorText(e), true));
