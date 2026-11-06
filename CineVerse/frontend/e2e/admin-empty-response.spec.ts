import { test, expect } from "@playwright/test";
import fs from "node:fs";

const admin = "http://localhost:5174";

test("shared client accepts empty success responses and preserves JSON and API errors", async ({
  page,
}) => {
  await page.goto(admin + "/dang-nhap");
  const cases = [
    { status: 200, body: "", expected: { ok: true } },
    { status: 200, body: "  \n", expected: { ok: true } },
    { status: 204, body: "", expected: { ok: true } },
    { status: 200, body: '{"id":123}', expected: { ok: true, value: { id: 123 } } },
    {
      status: 200,
      body: "invalid",
      expected: { ok: false, status: 200, code: "INVALID_RESPONSE" },
    },
    {
      status: 409,
      body: '{"errorCode":"IN_USE","message":"Đang có vé"}',
      expected: { ok: false, status: 409, code: "IN_USE" },
    },
  ];
  for (const item of cases) {
    await page.route("**/api/v1/regression-empty-response", (route) =>
      route.fulfill({
        status: item.status,
        body: item.body,
        contentType: "application/json",
      }),
    );
    const actual = await page.evaluate(async () => {
      const modulePath = performance
        .getEntriesByType("resource")
        .map((entry) => entry.name)
        .find((url) => url.includes("/frontend/src/lib/api.ts"));
      if (!modulePath) throw new Error("API module was not loaded by the admin app");
      const { send } = await import(/* @vite-ignore */ modulePath);
      try {
        return { ok: true, value: await send("/regression-empty-response", "DELETE") };
      } catch (error) {
        const e = error as { status: number; code: string };
        return { ok: false, status: e.status, code: e.code };
      }
    });
    expect(actual).toEqual({ value: undefined, ...item.expected });
    await page.unroute("**/api/v1/regression-empty-response");
  }
});

test("admin permanently deletes a new cinema and refreshes the list", async ({
  page,
}) => {
  const password = JSON.parse(
    fs.readFileSync("../.runtime/dev-secrets.json", "utf8").replace(/^\uFEFF/, ""),
  ).demoPassword;
  await page.goto(admin + "/dang-nhap");
  await page.getByLabel("Email quản trị").fill("admin@cineverse.local");
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Vào trang quản trị", exact: true }).click();
  await expect(page).toHaveURL(admin + "/admin");
  const name = "Kiểm thử xóa rạp " + Date.now();
  const id = await page.evaluate(async (name) => {
    const modulePath = performance
      .getEntriesByType("resource")
      .map((entry) => entry.name)
      .find((url) => url.includes("/frontend/src/lib/api.ts"));
    if (!modulePath) throw new Error("API module was not loaded by the admin app");
    const { send } = await import(/* @vite-ignore */ modulePath);
    return (
      await send("/admin/cinemas", "POST", {
        name,
        city: "TP.HCM",
        address: "Rạp kiểm thử hồi quy",
        active: true,
      })
    ).id as number;
  }, name);
  try {
    await page.goto(admin + "/admin/cinemas?q=" + encodeURIComponent(name));
    const row = page.locator("tbody tr").filter({ hasText: name });
    await expect(row).toHaveCount(1);
    await expect(row.locator(".boolean.yes")).toHaveCount(1);
    await row.getByRole("button", { name: "Xóa vĩnh viễn " + id, exact: true }).click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Xác nhận", exact: true })
      .click();
    await expect(page.getByRole("dialog")).toBeHidden();
    await expect(
      page.getByText("Đã xóa vĩnh viễn dữ liệu", { exact: true }),
    ).toBeVisible();
    await expect(row).toHaveCount(0);
    await page.reload();
    await expect(row).toHaveCount(0);
  } finally {
    await page.evaluate(async (id) => {
      const modulePath = performance
        .getEntriesByType("resource")
        .map((entry) => entry.name)
        .find((url) => url.includes("/frontend/src/lib/api.ts"));
      if (!modulePath) throw new Error("API module was not loaded by the admin app");
      const { send } = await import(/* @vite-ignore */ modulePath);
      try {
        await send(`/admin/cinemas/${id}`, "DELETE");
      } catch (error) {
        if ((error as { status: number }).status !== 404) throw error;
      }
    }, id);
    await page.getByRole("button", { name: "Đăng xuất admin", exact: true }).click();
  }
});
