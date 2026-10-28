/* Real backend UI test. Credentials are read from stdin and never saved in evidence.
 * Run only on a local development database. Keeps fixtures with booking history.
 * Requires playwright and a Chromium/Chrome executable. See ../HUONG_DAN.md.
 */
const { chromium } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');
const readline = require('node:readline');
const base = process.env.CINEMA_BASE_URL || 'http://127.0.0.1:8080';
const evidence = path.resolve(__dirname, '../evidence');
const reportFilename = process.env.SMOKE_ONLY === '1' ? 'packaged-smoke.json' : 'frontend-e2e.json';
const report = { startedAt: new Date().toISOString(), baseUrl: base, checks: [], fixtures: {}, javascriptErrors: [] };
const check = (name, value = true) => { assert(value, name); report.checks.push({ name, passed: true }); console.log('PASS ' + name); };
const readCredentials = async () => {
  if (process.env.CINEMA_CREDENTIALS_FILE) {
    const filename = process.env.CINEMA_CREDENTIALS_FILE;
    const data = JSON.parse(await fs.readFile(filename, 'utf8'));
    await fs.unlink(filename); return data;
  }
  return new Promise(resolve => { console.log('Waiting for local credentials on stdin (JSON, not recorded).'); const input = readline.createInterface({ input: process.stdin, terminal: false }); input.once('line', line => { input.close(); resolve(JSON.parse(line)); }); });
};
(async () => {
  const credentials = await readCredentials(); await fs.mkdir(evidence, { recursive: true });
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const context = await browser.newContext({ viewport: { width: 1366, height: 900 } }); const page = await context.newPage(); page.setDefaultTimeout(8000);
  page.on('pageerror', error => report.javascriptErrors.push(error.message));
  const shot = async name => page.screenshot({ path: path.join(evidence, name + '.png'), fullPage: false });
  const goto = async file => { await page.goto(base + '/admin/' + file + '.html'); await page.locator('.sidebar').waitFor({ state: 'attached' }); await page.waitForLoadState('networkidle'); };
  const login = async (email, password, target) => { await page.goto(base + '/login.html'); await page.locator('#login:not([disabled])').waitFor(); await page.locator('#email').fill(email); await page.locator('#password').fill(password); await page.locator('#login').click(); await page.waitForURL('**/admin/' + target + '.html'); await page.waitForLoadState('networkidle'); };
  const fill = async (name, value) => { const control = page.locator('dialog[open] [name="' + name + '"]'); if (await control.evaluate(n => n.tagName === 'SELECT')) await control.selectOption(String(value)); else await control.fill(String(value)); };
  const save = async (text = 'Lưu') => { const handle = await page.locator('dialog[open]').elementHandle(); await page.locator('dialog[open]').getByRole('button', { name: text, exact: true }).click(); await handle.waitForElementState('hidden'); await page.waitForLoadState('networkidle'); };
  const getApi = async url => page.evaluate(async url => Api.request(url), url);
  const suffix = Date.now().toString(36).toUpperCase();
  try {
    await login(credentials.adminEmail, credentials.adminPassword, 'dashboard'); check('ADMIN login and dashboard'); await shot('01-dashboard');
    for (const file of ['genres', 'movies', 'rooms', 'seats', 'showtimes', 'bookings', 'statistics', 'users']) { await goto(file); check('Page loads: ' + file, !(await page.locator('body').innerText()).includes('ReferenceError')); }
    for (const width of [1366, 768, 360]) { await page.setViewportSize({ width, height: 900 }); await goto('bookings'); check('No horizontal overflow at ' + width, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); }
    await page.setViewportSize({ width: 1366, height: 900 });
    if (process.env.SMOKE_ONLY === '1') return;

    const genreName = 'Thể loại UI ' + suffix;
    await goto('genres'); await page.getByRole('button', { name: '＋ Thêm thể loại' }).click(); await fill('name', genreName); await fill('description', 'Fixture do kiểm thử giao diện tạo'); await save();
    let genres = await getApi('/api/admin/genres?q=' + encodeURIComponent(genreName)); const genre = genres.items.find(x => x.name === genreName); check('Genre create persists', !!genre); report.fixtures.genreId = genre.id;
    let row = page.getByRole('row').filter({ hasText: genreName }); await row.getByRole('button', { name: 'Sửa', exact: true }).click(); await fill('description', 'Mô tả đã sửa'); await save(); check('Genre update persists', (await getApi('/api/admin/genres/' + genre.id)).description === 'Mô tả đã sửa'); await shot('02-genres');

    const movieCode = 'UI-' + suffix;
    await goto('movies'); await page.locator('#add').click(); await page.locator('#save').click(); check('Movie empty validation', await page.locator('#form-error').isVisible());
    await page.locator('[name=code]').fill(movieCode); await page.locator('[name=title]').fill('Phim UI ' + suffix); await page.locator('[name=durationMinutes]').fill('90'); await page.locator('[name=minimumAge]').fill('13'); await page.locator('[name=genreIds][value="' + genre.id + '"]').check();
    await page.locator('#save').click(); await page.locator('#editor').waitFor({ state: 'hidden' }); await page.waitForLoadState('networkidle');
    const movies = await getApi('/api/admin/movies?q=' + movieCode); const movie = movies.items.find(x => x.code === movieCode); check('Movie create persists', !!movie); report.fixtures.movieId = movie.id; await shot('03-movies');

    const roomCode = 'UI-' + suffix;
    await goto('rooms'); await page.getByRole('button', { name: '＋ Thêm phòng chiếu' }).click(); await fill('code', roomCode); await fill('name', 'Phòng UI ' + suffix); await save();
    const room = (await getApi('/api/admin/rooms?q=' + roomCode)).items.find(x => x.code === roomCode); check('Room create persists', !!room); report.fixtures.roomId = room.id; await shot('04-rooms');
    await page.goto(base + '/admin/seats.html?roomId=' + room.id); await page.getByRole('button', { name: 'Tạo sơ đồ hàng loạt' }).click(); await fill('rows', 'A,B'); await fill('seatsPerRow', '3'); await save();
    const physical = await getApi('/api/admin/rooms/' + room.id + '/seats'); check('Bulk seat layout persists', physical.seats.length === 6);
    await page.getByRole('button', { name: 'A1, STANDARD, Đang sử dụng', exact: true }).click(); await fill('seatType', 'VIP'); await save(); check('Seat type update persists', (await getApi('/api/admin/rooms/' + room.id + '/seats')).seats.find(s => s.code === 'A1').seatType === 'VIP'); await shot('05-seat-layout');

    const local = new Date(Date.now() + 14 * 86400000 + 7 * 3600000).toISOString().slice(0, 10) + 'T10:00'; report.fixtures.date = local.slice(0, 10);
    await goto('showtimes'); await page.getByRole('button', { name: '＋ Tạo suất chiếu' }).click(); await fill('movieId', movie.id); await fill('roomId', room.id); await fill('startsAt', local); await fill('standardPrice', 80000); await fill('vipPrice', 100000); await save();
    const shows = await getApi('/api/admin/showtimes?roomId=' + room.id); const show = shows.items[0]; check('Showtime create and server end time', show.durationMinutes === 90 && show.endsAt.includes('11:30')); report.fixtures.showtimeId = show.id;
    await page.locator('#filter-date').fill(local.slice(0, 10)); await page.getByRole('button', { name: 'Áp dụng', exact: true }).click(); await page.waitForLoadState('networkidle'); await shot('06-showtimes');
    await page.getByRole('button', { name: '＋ Tạo suất chiếu' }).click(); await fill('movieId', movie.id); await fill('roomId', room.id); await fill('startsAt', local); await page.locator('dialog[open]').getByRole('button', { name: 'Lưu', exact: true }).click(); await page.locator('dialog .error-summary').filter({ hasText: 'SHOWTIME_CONFLICT' }).waitFor(); check('Showtime conflict preserves form', await page.locator('#edit-startsAt').inputValue() === local); await page.locator('dialog[open]').getByRole('button', { name: 'Hủy', exact: true }).click();

    await page.goto(base + '/admin/bookings.html?showtimeId=' + show.id); await page.getByRole('button', { name: /A1, VIP, Trống/ }).waitFor(); await page.getByRole('button', { name: /A1, VIP, Trống/ }).click(); await page.getByRole('button', { name: /A2, Thường, Trống/ }).click(); await page.locator('#age-checked').check(); await page.getByRole('button', { name: 'Xem lại và xác nhận', exact: true }).click(); await save('Xác nhận');
    const bookings = await getApi('/api/bookings?showtimeId=' + show.id); const booking = bookings.items[0]; check('Booking created with real server prices', booking.totalAmount === 180000 && booking.seats.length === 2); report.fixtures.bookingId = booking.id; report.fixtures.bookingCode = booking.code; await shot('07-booking');
    await page.reload(); await page.waitForLoadState('networkidle'); check('Booking survives reload', (await getApi('/api/bookings/' + booking.id)).status === 'CONFIRMED');
    await page.locator('#filter-q').fill(booking.code); await page.getByRole('button', { name: 'Tra cứu đúng mã', exact: true }).click(); await page.locator('dialog[open]').getByRole('button', { name: 'Hủy đặt chỗ', exact: true }).click(); await fill('reason', 'Kiểm thử hủy từ giao diện'); await save('Xác nhận');
    await page.locator('dialog[open]').getByRole('button', { name: 'Đóng', exact: true }).click(); check('ADMIN cancellation persists', (await getApi('/api/bookings/' + booking.id)).status === 'CANCELLED');
    check('Cancellation releases seats', (await getApi('/api/showtimes/' + show.id + '/seats')).seats.filter(s => ['A1', 'A2'].includes(s.code)).every(s => s.availability === 'AVAILABLE'));
    await goto('statistics'); await page.locator('#filter-from').fill(local.slice(0, 10)); await page.locator('#filter-to').fill(local.slice(0, 10));
    for (const r of ['showtimes', 'booked-seats', 'occupancy', 'cancellations']) { await page.locator('#filter-report').selectOption(r); await page.getByRole('button', { name: 'Áp dụng', exact: true }).click(); await page.waitForLoadState('networkidle'); check('Statistics report: ' + r, !(await page.locator('main').innerText()).includes('INTERNAL_ERROR')); }
    await shot('08-statistics');
    const missing = await page.evaluate(async () => { const r = await fetch('/api/admin/users?page=0&size=20', { credentials: 'same-origin', headers: { Accept: 'application/json' } }); return { status: r.status, body: await r.json() }; }); report.accountApi = missing; check('Account API limitation captured', missing.status === 404);
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await page.waitForURL('**/login.html'); check('ADMIN logout');
    await login(credentials.staffEmail, credentials.staffPassword, 'showtimes'); check('STAFF role navigation', await page.locator('.nav-link').count() === 2);
    await page.goto(base + '/admin/bookings.html?showtimeId=' + show.id); await page.getByRole('button', { name: /A1, VIP, Trống/ }).click(); await page.locator('#age-checked').check(); await page.getByRole('button', { name: 'Xem lại và xác nhận' }).click(); await save('Xác nhận');
    check('STAFF can book', (await getApi('/api/bookings?showtimeId=' + show.id)).items.some(b => b.status === 'CONFIRMED' && b.createdByName === 'Nhân viên quầy'));
    await page.locator('#filter-q').fill(booking.code); await page.getByRole('button', { name: 'Tra cứu đúng mã' }).click(); check('STAFF cancellation control absent', await page.getByRole('button', { name: 'Hủy đặt chỗ', exact: true }).count() === 0); await page.locator('dialog[open]').getByRole('button', { name: 'Đóng', exact: true }).click(); await shot('09-staff-booking');
    await page.setViewportSize({ width: 360, height: 800 }); await shot('10-mobile'); check('No browser JavaScript errors', report.javascriptErrors.length === 0);
  } catch (error) { report.failure = error.message; await shot('failure'); throw error; }
  finally { report.finishedAt = new Date().toISOString(); await fs.writeFile(path.join(evidence, reportFilename), JSON.stringify(report, null, 2)); await browser.close(); console.log('Evidence: ' + path.join(evidence, reportFilename)); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
