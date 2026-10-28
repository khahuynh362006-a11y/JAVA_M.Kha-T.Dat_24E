window.AppReady = (async () => {
  'use strict';
  const { node, button, link } = UI;
  try {
    const user = await Auth.requireUser(); const current = location.pathname.split('/').at(-1);
    const admin = user.roles.includes('ADMIN');
    if (!admin && !['showtimes.html', 'bookings.html'].includes(current)) { location.replace('/admin/showtimes.html'); return null; }
    document.body.classList.add('with-sidebar');
    const aside = node('aside', undefined, 'sidebar'); const brand = link('Kha Đạt Cinema', Auth.destination(user), 'brand'); aside.append(brand, node('p', 'ĐIỀU HÀNH RẠP', 'eyebrow'));
    const nav = node('nav'); nav.setAttribute('aria-label', 'Chức năng hệ thống');
    const routes = [ ['dashboard.html', 'Tổng quan', true], ['genres.html', 'Thể loại', true], ['movies.html', 'Phim', true], ['rooms.html', 'Phòng chiếu', true], ['seats.html', 'Sơ đồ ghế', true], ['showtimes.html', 'Lịch chiếu'], ['bookings.html', 'Đặt chỗ & tra cứu'], ['statistics.html', 'Thống kê', true], ['users.html', 'Tài khoản', true] ];
    for (const [file, label, onlyAdmin] of routes) { if (onlyAdmin && !admin) continue; const a = link(label, '/admin/' + file, 'nav-link'); if (file === current) a.setAttribute('aria-current', 'page'); nav.append(a); }
    aside.append(nav, node('p', 'Một rạp · Nhiều trải nghiệm', 'sidebar-foot'));
    const skip = document.querySelector('.skip-link'); if (skip) skip.after(aside); else document.body.prepend(aside);
    let topbar = document.querySelector('.topbar');
    if (!topbar) { topbar = node('header', undefined, 'topbar'); topbar.append(node('span', 'Không gian điều hành', 'brand')); const account = node('div', undefined, 'account'); const out = button('Đăng xuất', async () => { out.disabled = true; try { await Auth.logout(); } catch (e) { UI.alert(UI.errorText(e), true); } finally { out.disabled = false; } }, 'button quiet'); account.append(node('span', `${user.displayName || user.username} · ${user.roles.join(', ')}`), out); topbar.append(account); document.body.insertBefore(topbar, aside.nextSibling); }
    const mobile = button('☰ Menu', () => { const expanded = document.body.classList.toggle('nav-open'); mobile.setAttribute('aria-expanded', expanded); if (expanded) nav.querySelector('a')?.focus(); }, 'button quiet menu-toggle'); mobile.setAttribute('aria-expanded', 'false'); topbar.prepend(mobile);
    document.addEventListener('keydown', e => { if (e.key === 'Escape' && document.body.classList.contains('nav-open')) { document.body.classList.remove('nav-open'); mobile.setAttribute('aria-expanded', 'false'); mobile.focus(); } });
    return user;
  } catch (e) { const host = document.getElementById('app') || document.getElementById('content'); host?.replaceChildren(node('p', UI.errorText(e), 'error-summary'), button('Thử lại', () => location.reload())); return null; }
})();
