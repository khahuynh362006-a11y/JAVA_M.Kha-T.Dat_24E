import { test, expect } from "@playwright/test";
import fs from "node:fs";

test("VNPAY checkout waits for merchant keys and cannot use simulated payment", async ({
  page,
  request,
}) => {
  const config = await (await request.get("/api/v1/payment-config")).json();
  test.skip(
    config.mode !== "vnpay" || config.ready,
    "Requires VNPAY mode with merchant credentials absent",
  );
  const secret = JSON.parse(
    fs.readFileSync("../.runtime/dev-secrets.json", "utf8").replace(/^\uFEFF/, ""),
  );
  const email = "free@cineverse.local";
  const login = await request.post("/api/v1/auth/login", {
    data: { email, password: secret.demoPassword },
  });
  expect(login.ok()).toBeTruthy();
  const headers = { Authorization: "Bearer " + (await login.json()).accessToken };
  const shows = await (await request.get("/api/v1/showtimes")).json();
  const show = shows.at(-1);
  const map = await (await request.get(`/api/v1/showtimes/${show.id}/seats`)).json();
  const seat = map.seats.find((seat: { status: string }) => seat.status === "AVAILABLE");
  const held = await request.post("/api/v1/bookings", {
    headers,
    data: { showtimeId: show.id, seatIds: [seat.id] },
  });
  expect(held.status()).toBe(201);
  const booking = await held.json();
  try {
    await page.goto("/dang-nhap");
    await page.getByLabel("Địa chỉ email").fill(email);
    await page.getByLabel("Mật khẩu", { exact: true }).fill(secret.demoPassword);
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(page).toHaveURL("http://localhost:5173/");
    await page.goto("/dat-ve/thanh-toan/" + booking.id);
    await expect(
      page.getByRole("heading", { name: "VNPAY Sandbox", exact: true }),
    ).toBeVisible();
    await expect(page.getByText(/Chưa có TmnCode\/HashSecret/)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Thanh toán [0-9]/ })).toBeDisabled();
    const payment = await request.post("/api/v1/payments", {
      headers,
      data: { bookingId: booking.id },
    });
    expect(payment.status()).toBe(503);
    expect((await payment.json()).errorCode).toBe("VNPAY_NOT_CONFIGURED");
    const untouched = await (
      await request.get("/api/v1/bookings/" + booking.id, { headers })
    ).json();
    expect(untouched.status).toBe("HELD");
    expect(untouched.paymentOrderId).toBeNull();
    const simulate = await request.post("/api/v1/payments/not-an-order/simulate", {
      headers,
      data: { outcome: "success" },
    });
    expect(simulate.status()).toBe(403);
    for (const width of [375, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      ).toBeTruthy();
      await page.screenshot({
        path: `../docs/screenshots/vnpay-checkout-${width}.png`,
        fullPage: true,
      });
    }
  } finally {
    expect(
      (await request.post(`/api/v1/bookings/${booking.id}/cancel`, { headers })).ok(),
    ).toBeTruthy();
  }
});
