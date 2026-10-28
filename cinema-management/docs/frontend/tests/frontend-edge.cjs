/* Focused regression of uncertain booking result, errors and keyboard flows.
 * Credentials file is deleted as soon as it is read. Uses existing UI test fixtures.
 */
const { chromium } = require('playwright');
const fs = require('node:fs/promises'); const path = require('node:path'); const assert = require('node:assert/strict');
const base = process.env.CINEMA_BASE_URL || 'http://127.0.0.1:8080';
const evidence = path.resolve(__dirname, '../evidence'); const results = { checks: [], startedAt: new Date().toISOString() };
const check = (name, passed) => { assert(passed, name); results.checks.push({ name, passed: true }); console.log('PASS ' + name); };
(async () => {
  const credentials = JSON.parse(await fs.readFile(process.env.CINEMA_CREDENTIALS_FILE, 'utf8')); await fs.unlink(process.env.CINEMA_CREDENTIALS_FILE);
  const fixtures = JSON.parse(await fs.readFile(path.join(evidence, 'frontend-e2e.json'))).fixtures;
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe' }); const context = await browser.newContext({ viewport: { width: 1366, height: 900 } }); const page = await context.newPage(); page.setDefaultTimeout(8000);
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  try {
    await page.goto(base + '/login.html'); await page.locator('#login:not([disabled])').waitFor(); await page.locator('#email').fill(credentials.adminEmail); await page.locator('#password').fill(credentials.adminPassword); await page.locator('#login').click(); await page.waitForURL('**/admin/dashboard.html');
    await page.goto(base + '/admin/bookings.html?showtimeId=' + fixtures.showtimeId); await page.getByRole('button', { name: /A2, Thường, Trống/ }).click(); await page.locator('#age-checked').check();
    let responseLost = false, savedBooking, firstKey;
    await page.route('**/api/bookings', async route => { if (route.request().method() === 'POST' && !responseLost) { responseLost = true; firstKey = route.request().headers()['idempotency-key']; const response = await route.fetch(); assert.equal(response.status(), 201); savedBooking = await response.json(); await route.abort('failed'); } else await route.continue(); });
    await page.getByRole('button', { name: 'Xem lại và xác nhận', exact: true }).click(); await page.locator('dialog[open]').getByRole('button', { name: 'Xác nhận', exact: true }).click(); await page.locator('dialog .error-summary').filter({ hasText: 'NETWORK_ERROR' }).waitFor();
    check('Lost response keeps original idempotency key', await page.evaluate(key => Object.keys(sessionStorage).some(k => { try { return JSON.parse(sessionStorage.getItem(k)).key === key; } catch { return false; } }), firstKey));
    await page.locator('dialog[open]').getByRole('button', { name: 'Hủy', exact: true }).click(); await page.unroute('**/api/bookings');
    let replayKey; page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/api/bookings')) replayKey = request.headers()['idempotency-key']; });
    await page.getByRole('button', { name: 'Kiểm tra kết quả / gửi lại cùng mã yêu cầu', exact: true }).click(); await page.locator('.booking-result').filter({ hasText: savedBooking.code }).waitFor();
    check('Retry uses same key and returns original booking', replayKey === firstKey);
    const matching = await page.evaluate(async id => (await Api.request('/api/bookings?showtimeId=' + id)).items, fixtures.showtimeId);
    check('No duplicate booking after replay', matching.filter(b => b.id === savedBooking.id).length === 1 && matching.filter(b => b.seats.some(s => s.code === 'A2') && b.status === 'CONFIRMED').length === 1);
    await page.screenshot({ path: path.join(evidence, '11-replayed-booking.png') });
    await page.evaluate(async id => Api.request('/api/bookings/' + id + '/cancel', { method: 'POST', body: { reason: 'Kết thúc kiểm thử mất phản hồi frontend' } }), savedBooking.id);

    await page.reload(); await page.getByRole('button', { name: /A2, Thường, Trống/ }).click(); await page.locator('#age-checked').check();
    await page.route('**/api/bookings', route => route.request().method() === 'POST' ? route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ status: 409, code: 'PRICE_CHANGED', message: 'Giá thay đổi trong kiểm thử response.', fieldErrors: {}, path: '/api/bookings' }) }) : route.continue());
    await page.getByRole('button', { name: 'Xem lại và xác nhận', exact: true }).click(); await page.locator('dialog[open]').getByRole('button', { name: 'Xác nhận', exact: true }).click(); await page.locator('dialog').waitFor({ state: 'hidden' });
    check('Price rejection closes stale review and requires new age confirmation', !(await page.locator('#age-checked').isChecked()) && await page.getByRole('button', { name: 'Xem lại và xác nhận', exact: true }).isDisabled()); await page.unroute('**/api/bookings');

    await page.goto(base + '/admin/movies.html'); await page.locator('#add:not([disabled])').waitFor(); await page.keyboard.press('Tab'); await page.keyboard.press('Enter');
    await page.locator('#add').click(); await page.locator('#save').focus(); await page.keyboard.press('Tab'); check('Dialog Tab wraps', await page.evaluate(() => document.querySelector('#editor').contains(document.activeElement))); await page.keyboard.press('Shift+Tab'); check('Dialog reverse Tab wraps', await page.evaluate(() => document.activeElement.id === 'save')); await page.keyboard.press('Escape'); check('Dialog restores trigger focus', await page.evaluate(() => document.activeElement.id === 'add'));
    await page.locator('#query').fill('NO-RESULT-' + Date.now()); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click(); await page.locator('#list-state').filter({ hasText: 'Không có phim' }).waitFor(); check('Empty results distinguished from failure', await page.locator('#count').textContent() === '0 phim');
    await page.route('**/api/admin/movies?**', r => r.abort('failed')); await page.locator('#reload').click(); await page.locator('#list-state').filter({ hasText: 'NETWORK_ERROR' }).waitFor(); check('Network failure is not an empty list', await page.locator('#count').textContent() === 'Chưa tải được dữ liệu'); await page.unroute('**/api/admin/movies?**');

    await page.goto(base + '/admin/statistics.html'); await page.locator('#filter-from').fill('2026-10-01'); await page.locator('#filter-to').fill('2026-11-02'); await page.getByRole('button', { name: 'Áp dụng', exact: true }).click(); check('Statistics rejects range over 31 days', (await page.locator('#feedback').textContent()).includes('31 ngày'));
    await page.route('**/api/admin/statistics/occupancy?**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ from: '2026-10-28', to: '2026-10-28', timeZone: 'Asia/Ho_Chi_Minh', timeBasis: 'SHOWTIME_START_DATE', asOf: '2026-10-28T14:00:00+07:00', page: 0, size: 20, total: 2, totalPages: 1, items: [{ showtimeId: 1, movieTitle: 'Fixture thiếu snapshot', roomCode: 'P01', startsAt: '2026-10-28T10:00:00+07:00', bookedSeats: 3, capacity: null, capacityBasis: 'MISSING_HISTORICAL_CAPACITY', occupancyPercent: null, anomaly: false }, { showtimeId: 2, movieTitle: 'Fixture vượt sức chứa', roomCode: 'P02', startsAt: '2026-10-28T10:00:00+07:00', bookedSeats: 3, capacity: 2, capacityBasis: 'SNAPSHOT', occupancyPercent: 150, anomaly: true }] }) }));
    await page.locator('#filter-from').fill('2026-10-28'); await page.locator('#filter-to').fill('2026-10-28'); await page.locator('#filter-report').selectOption('occupancy'); await page.getByRole('button', { name: 'Áp dụng', exact: true }).click(); await page.getByRole('cell', { name: /150%/ }).waitFor(); check('Occupancy preserves null and values above 100%', (await page.locator('main').innerText()).includes('Chưa có dữ liệu sức chứa lịch sử') && (await page.locator('main').innerText()).includes('150%')); await page.unroute('**/api/admin/statistics/occupancy?**');
    await page.setViewportSize({ width: 360, height: 800 }); await page.locator('.menu-toggle').click(); check('Mobile menu moves focus to navigation', await page.evaluate(() => document.activeElement.classList.contains('nav-link'))); await page.keyboard.press('Escape'); check('Mobile menu closes with Escape', await page.locator('.menu-toggle').getAttribute('aria-expanded') === 'false');
    await context.clearCookies(); await page.locator('#filter-from').fill('2026-10-28'); await page.getByRole('button', { name: 'Áp dụng', exact: true }).click(); await page.waitForURL('**/login.html?expired=1'); check('Session expiry redirects to login', true);
    check('No JavaScript errors', errors.length === 0); results.javascriptErrors = errors;
  } catch (e) { results.failure = e.message; await page.screenshot({ path: path.join(evidence, 'edge-failure.png') }); throw e; }
  finally { results.finishedAt = new Date().toISOString(); await fs.writeFile(path.join(evidence, 'frontend-edge.json'), JSON.stringify(results, null, 2)); await browser.close(); }
})().catch(e => { console.error(e.message); process.exitCode = 1; });
