AppReady.then(user => {
  if (!user || !Auth.admin) return;
  const view = UI.page('Quản lý tài khoản', 'Chỉ ADMIN. Chức năng này đang chờ hợp đồng API nghiệp vụ từ Kha.');
  const panel = UI.node('section', undefined, 'panel unavailable-panel');
  panel.append(UI.node('h2', 'Chưa thể tạo, khóa hoặc cấp quyền qua giao diện'), UI.node('p', 'Backend hiện có đăng nhập, xem phiên và đăng xuất. docs/api.md chưa định nghĩa CRUD tài khoản; các route users mới được dành quyền và chưa có controller nghiệp vụ.'), UI.node('p', 'Cần Kha bổ sung: danh sách/tìm kiếm/phân trang, tạo tài khoản, đổi vai trò, khóa/mở khóa, quy tắc ADMIN cuối cùng, định dạng lỗi và chính sách phiên sau khi khóa. Không gửi request ghi hoặc dùng dữ liệu giả.'), UI.link('Quay lại tổng quan', '/admin/dashboard.html'));
  view.host.append(panel);
});
