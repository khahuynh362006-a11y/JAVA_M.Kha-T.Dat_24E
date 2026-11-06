import { test, expect, type Page } from "@playwright/test";
import fs from "node:fs";
const customer = "http://localhost:5173",
  admin = "http://localhost:5174";
const password = JSON.parse(
  fs.readFileSync("../.runtime/dev-secrets.json", "utf8").replace(/^\uFEFF/, ""),
).demoPassword;
async function login(page: Page, isAdmin: boolean, email: string) {
  await page.goto((isAdmin ? admin : customer) + "/dang-nhap");
  await page.getByLabel(isAdmin ? "Email quản trị" : "Địa chỉ email").fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page
    .getByRole("button", {
      name: isAdmin ? "Vào trang quản trị" : "Đăng nhập",
      exact: true,
    })
    .click();
}
test("customer and admin keep independent sessions in one browser; admin logout preserves customer", async ({
  page,
  context,
}) => {
  await login(page, false, "free@cineverse.local");
  await expect(page).toHaveURL(customer + "/");
  const staff = await context.newPage();
  await login(staff, true, "admin@cineverse.local");
  await expect(staff).toHaveURL(admin + "/admin");
  const names = (await context.cookies()).map((c) => c.name);
  expect(names).toContain("cv_refresh");
  expect(names).toContain("cv_admin_refresh");
  await page.goto(customer + "/tai-khoan");
  await expect(
    page.locator(".account-panel").getByText("free@cineverse.local", { exact: true }),
  ).toBeVisible();
  await staff.reload();
  await expect(staff.locator(".admin-content h1")).toContainText("Tổng quan");
  await staff.getByRole("button", { name: "Đăng xuất admin", exact: true }).click();
  await expect(staff).toHaveURL(/localhost:5174\/dang-nhap(?:\?|$)/);
  await page.reload();
  await expect(
    page.locator(".account-panel").getByText("free@cineverse.local", { exact: true }),
  ).toBeVisible();
});
test("customer role is refused by admin app and API; admin login is responsive", async ({
  page,
}) => {
  await login(page, true, "free@cineverse.local");
  await expect(page.getByRole("alert")).toContainText("không có quyền quản trị");
  await page.goto(admin + "/admin/users");
  await expect(page).toHaveURL(/5174\/dang-nhap/);
  await expect(page.getByLabel("Email quản trị")).toBeVisible();
  for (const width of [375, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
    await page.screenshot({ path: `../docs/screenshots/admin-login-${width}.png` });
  }
});
test("admin movie update reaches open customer page through shared database and catalog refresh", async ({
  page,
  context,
}) => {
  await login(page, true, "admin@cineverse.local");
  await expect(page).toHaveURL(admin + "/admin");
  const customerPage = await context.newPage();
  await customerPage.goto(customer + "/phim/phia-ben-kia-chan-troi");
  const heading = customerPage.locator(".detail-copy h1");
  const original = await heading.innerText();
  const revised = original + " · E2E " + Date.now();
  await page.goto(admin + "/admin/movies?q=phia-ben-kia-chan-troi");
  await expect(page.locator("tbody tr")).toHaveCount(1);
  async function saveTitle(title: string) {
    await page.getByRole("button", { name: /Chỉnh sửa/ }).click();
    await page.getByRole("dialog").getByLabel("Tên phim", { exact: true }).fill(title);
    await page.getByRole("button", { name: "Lưu phim", exact: true }).click();
    await expect(page.getByRole("dialog")).toBeHidden();
  }
  try {
    await saveTitle(revised);
    await customerPage.bringToFront();
    await expect(heading).toHaveText(revised, { timeout: 40000 });
    await page.screenshot({ path: "../docs/screenshots/admin-movies-1440.png" });
  } finally {
    await saveTitle(original);
  }
  await customerPage.reload();
  await expect(heading).toHaveText(original);
});
test("payOS without merchant keys is explicit and cannot create a payment", async ({
  request,
}) => {
  const config = await (await request.get(customer + "/api/v1/payment-config")).json();
  expect(config.provider).toBe("PAYOS");
  expect(config.ready).toBe(false);
  const auth = await request.post(customer + "/api/v1/auth/login", {
    data: { email: "free@cineverse.local", password },
    headers: { Origin: customer },
  });
  expect(auth.ok()).toBeTruthy();
  const { accessToken } = await auth.json();
  const result = await request.post(customer + "/api/v1/payments", {
    headers: { Authorization: `Bearer ${accessToken}`, Origin: customer },
    data: { bookingId: "does-not-create-a-real-order" },
  });
  expect(result.status()).toBe(503);
  expect((await result.json()).errorCode).toBe("PAYOS_NOT_CONFIGURED");
});
