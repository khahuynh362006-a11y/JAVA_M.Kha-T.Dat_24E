import { test, expect, type Page, type APIRequestContext } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
const secretsPath = path.resolve("../.runtime/dev-secrets.json");
const demoPassword =
  process.env.DEMO_PASSWORD ??
  (fs.existsSync(secretsPath)
    ? JSON.parse(fs.readFileSync(secretsPath, "utf8").replace(/^\uFEFF/, "")).demoPassword
    : "");
const origin = "http://localhost:5173";
const userEmail = `e2e-${Date.now()}@example.test`,
  password = "E2eStrong!2026";
let createdOrder = "";
let createdBooking = "";
async function login(page: Page, email: string, pass: string) {
  const admin = email === "admin@cineverse.local";
  await page.goto(admin ? "http://localhost:5174/dang-nhap" : "/dang-nhap");
  await page.getByLabel(admin ? "Email quản trị" : "Địa chỉ email").fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(pass);
  await page
    .getByRole("button", {
      name: admin ? "Vào trang quản trị" : "Đăng nhập",
      exact: true,
    })
    .click();
  await expect(page).toHaveURL(admin ? /localhost:5174\/admin$/ : /localhost:5173\/$/);
}
async function mail(request: APIRequestContext, email: string, subject: string) {
  let result: { Text: string; HTML: string } | undefined;
  await expect
    .poll(
      async () => {
        const res = await request.get("http://localhost:8025/api/v1/search", {
          params: { query: `to:${email}` },
        });
        const data = await res.json();
        const message = data.messages?.find((m: { Subject: string }) =>
          m.Subject.includes(subject),
        );
        if (message) {
          result = await (
            await request.get("http://localhost:8025/api/v1/message/" + message.ID)
          ).json();
          return true;
        }
        return false;
      },
      { timeout: 25000 },
    )
    .toBe(true);
  return result!;
}
test.describe.serial("User journey against real MySQL + Mailpit", () => {
  test("register and login immediately without email verification", async ({ page }) => {
    await page.goto("/dang-ky");
    await page.getByLabel("Họ và tên").fill("Khán giả E2E");
    await page.getByLabel("Địa chỉ email").fill(userEmail);
    await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
    await page.getByLabel("Xác nhận mật khẩu").fill(password);
    await page.getByRole("checkbox", { name: /Tôi đồng ý/ }).check();
    await page.getByRole("button", { name: "Tạo tài khoản", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Đăng ký thành công" })).toBeVisible();
    await expect(
      page.getByText("Bạn có thể đăng nhập ngay.", { exact: false }),
    ).toBeVisible();
    await login(page, userEmail, password);
  });
  test("server search, showtime selection and atomic seat hold", async ({ page }) => {
    await login(page, userEmail, password);
    await page.goto("/tim-kiem?q=phia%20ben%20kia");
    await expect(page.locator(".movie-card")).toHaveCount(1);
    await page.locator(".movie-card-info a").click();
    await expect(page.locator(".detail-copy h1")).toHaveText("Phía bên kia chân trời");
    await page
      .locator(".detail-copy")
      .getByRole("link", { name: "Đặt vé ngay", exact: true })
      .click();
    await page.locator(".showtime-button").first().click();
    await expect(
      page.getByRole("heading", { name: "Chọn góc nhìn của bạn." }),
    ).toBeVisible();
    await page.locator("button.seat.available").nth(0).click();
    await page.locator("button.seat.available").nth(1).click();
    await page.getByRole("button", { name: "Tiếp tục thanh toán", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "Buổi hẹn sắp bắt đầu." }),
    ).toBeVisible();
    createdBooking = page.url().split("/").pop()!;
    await expect(page.locator(".hold-countdown")).toContainText("Ghế đang được giữ");
  });
  test("favorites, rating update and notifications persist", async ({ page }) => {
    await login(page, userEmail, password);
    await page.goto("/phim/thanh-pho-khong-ngu");
    await page
      .getByRole("button", { name: "Thêm vào danh sách", exact: true })
      .first()
      .click();
    await page.goto("/danh-sach-cua-toi");
    await expect(page.locator(".movie-card-info")).toContainText("Thành phố không ngủ");
    await page.goto("/phim/thanh-pho-khong-ngu");
    await page
      .getByLabel("Nội dung đánh giá")
      .fill("Bình luận E2E kiểm tra lưu dữ liệu thật.");
    await page.getByRole("button", { name: "Gửi đánh giá", exact: true }).click();
    await expect(page.locator(".review-list")).toContainText(
      "Bình luận E2E kiểm tra lưu dữ liệu thật.",
    );
    await page.getByRole("button", { name: "Chỉnh sửa", exact: true }).click();
    await page.getByLabel("Nội dung đánh giá").fill("Bình luận E2E đã được cập nhật.");
    await page.getByRole("button", { name: "Cập nhật đánh giá", exact: true }).click();
    await expect(page.locator(".review-list")).toContainText(
      "Bình luận E2E đã được cập nhật.",
    );
    await page
      .getByRole("button", { name: "Xóa khỏi danh sách", exact: true })
      .first()
      .click();
    await page.goto("/danh-sach-cua-toi");
    await expect(page.getByText("Dành chỗ cho những câu chuyện hay")).toBeVisible();
    await page.goto("/thong-bao");
    await expect(page.locator(".notification-list")).toContainText("Chào mừng");
  });
  test("booking checkout, simulated payment, QR tickets, SMTP and idempotency", async ({
    page,
    request,
  }) => {
    const config = await (await request.get(origin + "/api/v1/payment-config")).json();
    test.skip(
      config.mode !== "simulation",
      "Simulation-only payment fixture; actual Sandbox transactions need merchant credentials and bank Test flow.",
    );
    await login(page, userEmail, password);
    await page.goto("/dat-ve/thanh-toan/" + createdBooking);
    await page.getByRole("button", { name: /^Thanh toán [0-9]/ }).click();
    await expect(page.getByText("SIMULATED TEST PAYMENT", { exact: true })).toBeVisible();
    createdOrder = page.url().split("/").pop()!;
    await page.getByRole("button", { name: "Mô phỏng thành công", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "Hẹn gặp bạn tại rạp." }),
    ).toBeVisible();
    await mail(request, userEmail, "Vé xem phim");
    await page.getByRole("link", { name: "Xem vé điện tử", exact: true }).click();
    await expect(page.locator(".e-ticket")).toHaveCount(2);
    await page
      .getByRole("button", { name: /Xem mã QR ghế/ })
      .first()
      .click();
    await expect(page.getByRole("dialog").locator("svg")).toHaveCount(2);
    await page.getByRole("button", { name: "Đóng", exact: true }).click();
    const session = await (
      await request.post(origin + "/api/v1/auth/login", {
        data: { email: userEmail, password },
      })
    ).json();
    const headers = { Authorization: "Bearer " + session.accessToken, Origin: origin };
    const before = await (
      await request.get(origin + "/api/v1/bookings/" + createdBooking, { headers })
    ).json();
    const duplicate = await request.post(
      origin + "/api/v1/payments/" + createdOrder + "/simulate",
      { headers, data: { outcome: "success" } },
    );
    expect(duplicate.ok()).toBeTruthy();
    const after = await (
      await request.get(origin + "/api/v1/bookings/" + createdBooking, { headers })
    ).json();
    expect(after.tickets.map((t: { id: string }) => t.id)).toEqual(
      before.tickets.map((t: { id: string }) => t.id),
    );
    expect(after.status).toBe("CONFIRMED");
  });
  test("password reset email, reset form and new password login", async ({
    page,
    request,
  }) => {
    await page.goto("/quen-mat-khau");
    await page.getByLabel("Địa chỉ email").fill(userEmail);
    await page.getByRole("button", { name: "Gửi liên kết đặt lại", exact: true }).click();
    await expect(page.getByText("Yêu cầu đã được xử lý")).toBeVisible();
    const message = await mail(request, userEmail, "Đặt lại mật khẩu");
    const link = message.Text.match(
      /https?:\/\/[^\s]+\/dat-lai-mat-khau\?token=[^\s]+/,
    )![0];
    await page.goto(link);
    await page.getByLabel("Mật khẩu", { exact: true }).fill("ChangedE2e!2026");
    await page.getByLabel("Xác nhận mật khẩu").fill("ChangedE2e!2026");
    await page.getByRole("button", { name: "Đặt lại mật khẩu", exact: true }).click();
    await expect(page.getByText("Yêu cầu đã được xử lý")).toBeVisible();
    await login(page, userEmail, "ChangedE2e!2026");
  });
});
test("admin creates, publishes, updates and hides a database movie", async ({ page }) => {
  expect(demoPassword, "Set DEMO_PASSWORD or run scripts/start-local.ps1").not.toBe("");
  await login(page, "admin@cineverse.local", demoPassword);
  await page.goto("/admin/movies");
  await page.getByRole("button", { name: "Thêm mới", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên phim", { exact: true }).fill("Phim kiểm thử E2E");
  await dialog.getByLabel("Tên gốc", { exact: true }).fill("E2E Journey");
  const slug = "e2e-journey-" + Date.now();
  await dialog.getByLabel("Slug URL (chữ thường, dấu gạch nối)").fill(slug);
  await dialog
    .getByLabel("Mô tả ngắn", { exact: true })
    .fill("Một bộ phim hư cấu dùng để kiểm thử tạo và xuất bản.");
  await dialog
    .getByLabel("Nội dung chi tiết", { exact: true })
    .fill("Nội dung thử nghiệm do Playwright tạo, dùng video nguồn mở.");
  await dialog.getByRole("button", { name: "Thông tin phim", exact: true }).click();
  await dialog.getByLabel("Đạo diễn", { exact: true }).fill("Đạo diễn E2E");
  await dialog.getByLabel("Diễn viên", { exact: true }).fill("Diễn viên E2E");
  await dialog.getByLabel("Phiêu lưu", { exact: true }).check();
  await dialog.getByRole("button", { name: "Hình ảnh & trailer", exact: true }).click();
  await dialog.getByLabel("Tệp MP4 trailer", { exact: true }).fill("sintel.mp4");
  await dialog.getByRole("button", { name: "Xuất bản", exact: true }).click();
  await dialog.getByLabel("Trạng thái", { exact: true }).selectOption("PUBLISHED");
  await dialog.getByRole("button", { name: "Lưu phim", exact: true }).click();
  await expect(dialog).toBeHidden();
  await page.goto(origin + "/phim/" + slug);
  await expect(page.locator(".detail-copy h1")).toHaveText("Phim kiểm thử E2E");
  await page.goto("/admin/movies?q=" + slug);
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("button", { name: /Chỉnh sửa/ }).click();
  await page
    .getByRole("dialog")
    .getByLabel("Tên phim", { exact: true })
    .fill("Phim E2E đã cập nhật");
  await page.getByRole("button", { name: "Lưu phim", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await page.getByRole("button", { name: /Xóa vĩnh viễn/ }).click();
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.getByText("Chưa có dữ liệu phù hợp")).toBeVisible();
});
test("admin catalog, seasons, episodes, plans, moderation and audit pages render from API", async ({
  page,
}) => {
  await login(page, "admin@cineverse.local", demoPassword);
  for (const route of [
    "",
    "genres",
    "countries",
    "cinemas",
    "auditoriums",
    "seats",
    "showtimes",
    "bookings",
    "tickets",
    "check-in",
    "users",
    "payments",
    "comments",
    "banners",
    "notifications",
    "analytics",
    "email-logs",
    "audit-logs",
    "settings",
  ]) {
    await page.goto("/admin" + (route ? "/" + route : ""));
    await expect(page.locator(".admin-content h1")).toBeVisible();
    await expect(page.locator(".admin-content")).not.toContainText(
      "Chưa thể tải nội dung",
    );
  }
});
test("responsive pages at six required sizes have no overflow or runtime errors", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  for (const [width, height] of [
    [375, 812],
    [390, 844],
    [768, 1024],
    [1366, 768],
    [1440, 900],
    [1920, 1080],
  ]) {
    await page.setViewportSize({ width, height });
    for (const route of [
      "/",
      "/kham-pha",
      "/phim/phia-ben-kia-chan-troi",
      "/rap-chieu",
      "/dang-ky",
    ]) {
      await page.goto(route);
      await expect(page.locator("h1").first()).toBeVisible();
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
        `${route} at ${width}`,
      ).toBeTruthy();
    }
    await page.goto("/");
    await page.locator(".movie-card").first().waitFor();
    await page.screenshot({ path: `../docs/screenshots/home-${width}.png` });
  }
  expect(errors).toEqual([]);
});
