const { chromium } = require('playwright'); const fs = require('node:fs/promises'); const path = require('node:path'); const assert = require('node:assert/strict');
const base = process.env.CINEMA_BASE_URL || 'http://127.0.0.1:8080', evidence = path.resolve(__dirname, '../evidence');
(async () => {
  const credentials = JSON.parse(await fs.readFile(process.env.CINEMA_CREDENTIALS_FILE, 'utf8')); await fs.unlink(process.env.CINEMA_CREDENTIALS_FILE);
  const fixtures = JSON.parse(await fs.readFile(path.join(evidence, 'frontend-e2e.json'))).fixtures;
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe' }); const page = await browser.newPage(); page.setDefaultTimeout(8000); const report = { checks: [], screenshots: [], javascriptErrors: [] }; page.on('pageerror', e => report.javascriptErrors.push(e.message));
  try {
    await page.goto(base + '/login.html'); await page.locator('#login:not([disabled])').waitFor(); await page.locator('#email').fill(credentials.adminEmail); await page.locator('#password').fill(credentials.adminPassword); await page.locator('#login').click(); await page.waitForURL('**/admin/dashboard.html');
    for (const width of [1366, 768, 360]) {
      await page.setViewportSize({ width, height: 900 });
      for (const name of ['dashboard', 'genres', 'movies', 'rooms', 'seats', 'showtimes', 'bookings', 'statistics', 'users']) {
        const extra = name === 'seats' ? '?roomId=' + fixtures.roomId : name === 'bookings' ? '?showtimeId=' + fixtures.showtimeId : '';
        await page.goto(base + '/admin/' + name + '.html' + extra); await page.waitForLoadState('networkidle');
        const okay = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth); assert(okay, name + ' overflows at ' + width); report.checks.push({ page: name, width, noHorizontalOverflow: okay });
        if (width !== 768) { const screenshot = name + '-' + width + '.png'; await page.screenshot({ path: path.join(evidence, screenshot) }); report.screenshots.push(screenshot); }
      }
    }
    assert.equal(report.javascriptErrors.length, 0); console.log('PASS 27 page/viewport checks, 18 screenshots, no JavaScript errors.');
  } catch (e) { report.failure = e.message; throw e; }
  finally { report.finishedAt = new Date().toISOString(); await fs.writeFile(path.join(evidence, 'frontend-visual.json'), JSON.stringify(report, null, 2)); await browser.close(); }
})().catch(e => { console.error(e.message); process.exitCode = 1; });
