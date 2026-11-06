import { test, expect, type APIRequestContext, type Page } from "@playwright/test";
import fs from "node:fs";
const origin = "http://localhost:5173",
  base = origin + "/api/v1";
const password =
  process.env.DEMO_PASSWORD ??
  JSON.parse(
    fs.readFileSync("../.runtime/dev-secrets.json", "utf8").replace(/^\uFEFF/, ""),
  ).demoPassword;
async function token(request: APIRequestContext, email: string) {
  const r = await request.post(base + "/auth/login", { data: { email, password } });
  expect(r.ok()).toBeTruthy();
  return (await r.json()).accessToken as string;
}
const headers = (value: string) => ({ Authorization: "Bearer " + value, Origin: origin });
async function adminLogin(page: Page) {
  await page.goto("http://localhost:5174/dang-nhap");
  await page.getByLabel("Email quản trị").fill("admin@cineverse.local");
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Vào trang quản trị", exact: true }).click();
  await expect(page).toHaveURL("http://localhost:5174/admin");
}
test("two users race for the same MySQL seat: exactly one hold wins", async ({
  request,
}) => {
  const a = await token(request, "free@cineverse.local"),
    b = await token(request, "vip@cineverse.local");
  const shows = await (await request.get(base + "/showtimes")).json();
  const s = shows.at(-1);
  const map = await (await request.get(base + `/showtimes/${s.id}/seats`)).json();
  const seat = map.seats.find((x: { status: string }) => x.status === "AVAILABLE");
  const data = { showtimeId: s.id, seatIds: [seat.id] };
  const responses = await Promise.all([
    request.post(base + "/bookings", { headers: headers(a), data }),
    request.post(base + "/bookings", { headers: headers(b), data }),
  ]);
  expect(responses.map((r) => r.status()).sort()).toEqual([201, 409]);
  const winner = responses.findIndex((r) => r.status() === 201),
    booking = await responses[winner].json();
  const loserRead = await request.get(base + "/bookings/" + booking.id, {
    headers: headers(winner === 0 ? b : a),
  });
  expect(loserRead.status()).toBe(403);
  const admin = await token(request, "admin@cineverse.local");
  const closeRoom = await request.put(base + `/admin/auditoriums/${s.auditoriumId}`, {
    headers: headers(admin),
    data: {
      cinemaId: s.cinemaId,
      name: s.auditoriumName,
      format: s.format,
      rows: 8,
      columns: 10,
      active: false,
    },
  });
  expect(closeRoom.status()).toBe(400);
  expect((await closeRoom.json()).message).toContain("đơn vé đang hiệu lực");
  const closeShow = await request.delete(base + `/admin/showtimes/${s.id}`, {
    headers: headers(admin),
  });
  expect(closeShow.status()).toBe(400);
  const canceled = await request.post(base + `/bookings/${booking.id}/cancel`, {
    headers: headers(winner === 0 ? a : b),
  });
  expect(canceled.status()).toBe(204);
  const refreshed = await (await request.get(base + `/showtimes/${s.id}/seats`)).json();
  expect(refreshed.seats.find((x: { id: number }) => x.id === seat.id).status).toBe(
    "AVAILABLE",
  );
});
test("admin creates a cinema, room with seats, showtime and rejects overlap", async ({
  page,
  request,
}) => {
  await adminLogin(page);
  const unique = Date.now(),
    cinemaName = "Rạp E2E " + unique;
  await page.goto("/admin/cinemas");
  await page.getByRole("button", { name: "Thêm mới", exact: true }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên rạp", { exact: true }).fill(cinemaName);
  await dialog.getByLabel("Thành phố", { exact: true }).fill("TP. Hồ Chí Minh");
  await dialog
    .getByLabel("Địa chỉ", { exact: true })
    .fill("Địa chỉ hư cấu chỉ dành cho kiểm thử");
  let response = page.waitForResponse(
    (r) => r.url().endsWith("/api/v1/admin/cinemas") && r.request().method() === "POST",
  );
  await dialog.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  const cinemaId = (await (await response).json()).id;
  await expect(dialog).toBeHidden();
  await page.goto("/admin/auditoriums");
  await page.getByRole("button", { name: "Thêm mới", exact: true }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("Rạp chiếu", { exact: true }).selectOption(String(cinemaId));
  await dialog.getByLabel("Tên phòng", { exact: true }).fill("Phòng E2E");
  response = page.waitForResponse(
    (r) =>
      r.url().endsWith("/api/v1/admin/auditoriums") && r.request().method() === "POST",
  );
  await dialog.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  const roomId = (await (await response).json()).id;
  await expect(dialog).toBeHidden();
  await page.goto("/admin/showtimes");
  await page.getByRole("button", { name: "Thêm mới", exact: true }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("Phim", { exact: true }).selectOption("1");
  await dialog.getByLabel("Phòng chiếu", { exact: true }).selectOption(String(roomId));
  const day = new Date(Date.now() + 2 * 86400000 + 7 * 3600000)
    .toISOString()
    .slice(0, 10);
  await dialog.getByLabel("Bắt đầu (giờ Việt Nam)", { exact: true }).fill(day + "T14:00");
  await dialog.getByLabel("Giá ghế thường (VND)", { exact: true }).fill("85000");
  response = page.waitForResponse(
    (r) => r.url().endsWith("/api/v1/admin/showtimes") && r.request().method() === "POST",
  );
  await dialog.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  const showId = (await (await response).json()).id;
  await expect(dialog).toBeHidden();
  const shows = await (
    await request.get(base + "/showtimes", { params: { date: day, cinemaId } })
  ).json();
  expect(shows).toHaveLength(1);
  expect(shows[0].totalSeats).toBe(80);
  expect(shows[0].basePrice).toBe(85000);
  const adminToken = await token(request, "admin@cineverse.local");
  const overlap = await request.post(base + "/admin/showtimes", {
    headers: headers(adminToken),
    data: {
      movieId: 1,
      auditoriumId: roomId,
      startsAt: day + "T14:30:00+07:00",
      basePrice: 85000,
      active: true,
    },
  });
  expect(overlap.status()).toBe(400);
  expect((await overlap.json()).message).toContain("trùng giờ");
  for (const [resource, id] of [
    ["showtimes", showId],
    ["auditoriums", roomId],
    ["cinemas", cinemaId],
  ]) {
    const result = await request.delete(base + `/admin/${resource}/${id}`, {
      headers: headers(adminToken),
    });
    expect(result.ok()).toBeTruthy();
  }
});
test("staff checks a ticket in once and seat pages fit six viewport sizes", async ({
  page,
  request,
}) => {
  const config = await (await request.get(base + "/payment-config")).json();
  test.skip(
    config.mode !== "simulation",
    "This fixture issues a simulated ticket; run it only with simulation enabled.",
  );
  const user = await token(request, "free@cineverse.local");
  const shows = await (await request.get(base + "/showtimes")).json(),
    s = shows.at(-1);
  const map = await (await request.get(base + `/showtimes/${s.id}/seats`)).json(),
    seat = map.seats.find((x: { status: string }) => x.status === "AVAILABLE");
  const hold = await (
    await request.post(base + "/bookings", {
      headers: headers(user),
      data: { showtimeId: s.id, seatIds: [seat.id] },
    })
  ).json();
  const order = await (
    await request.post(base + "/payments", {
      headers: headers(user),
      data: { bookingId: hold.id },
    })
  ).json();
  expect(
    (
      await request.post(base + `/payments/${order.id}/simulate`, {
        headers: headers(user),
        data: { outcome: "success" },
      })
    ).ok(),
  ).toBeTruthy();
  const booking = await (
    await request.get(base + "/bookings/" + hold.id, { headers: headers(user) })
  ).json();
  expect(booking.tickets).toHaveLength(1);
  await adminLogin(page);
  await page.goto("/admin/check-in");
  await page
    .getByLabel("Mã QR / nội dung vé")
    .fill("CINEVERSE:TICKET:" + booking.tickets[0].code);
  await page.getByRole("button", { name: "Xác nhận soát vé", exact: true }).click();
  await expect(
    page.getByText("Vé hợp lệ · Ghế " + booking.tickets[0].seatLabel),
  ).toBeVisible();
  await page.getByRole("button", { name: "Xác nhận soát vé", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("đã được sử dụng");
  for (const [width, height] of [
    [375, 812],
    [390, 844],
    [768, 1024],
    [1366, 768],
    [1440, 900],
    [1920, 1080],
  ]) {
    await page.setViewportSize({ width, height });
    await page.goto("/dat-ve/suat/" + s.id);
    await expect(page.locator(".seat-map")).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      `seat map at ${width}`,
    ).toBeTruthy();
    await page.screenshot({ path: `../docs/screenshots/seats-${width}.png` });
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/ve-cua-toi/" + booking.id);
  await expect(page.locator(".e-ticket")).toHaveCount(1);
  await page.screenshot({
    path: "../docs/screenshots/ticket-desktop.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../docs/screenshots/ticket-mobile.png",
    fullPage: true,
  });
});
