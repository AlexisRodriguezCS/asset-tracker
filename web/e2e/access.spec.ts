import { expect, test } from "@playwright/test";
import { statePath } from "./support";

/**
 * What each role can reach, checked in the browser rather than at the API.
 *
 * `infra/qa/api-matrix.cjs` already proves the refusals refuse. What it cannot
 * prove is the half that lives in the console: that the nav does not offer a
 * page the caller may not use, and - far more important - that typing the URL
 * anyway lands somewhere else. A hidden link is not a control; the page has to
 * refuse, and that only shows up in a browser.
 */

test.describe("signed out", () => {
  test("the landing page asks for Microsoft 365 and shows no nav", async ({
    page,
  }) => {
    await page.context().clearCookies();
    await page.goto("/welcome");

    await expect(
      page.getByRole("link", { name: /Sign in with Microsoft 365/i }),
    ).toBeVisible();
    // the signed-in chrome must not render for someone with no session
    await expect(page.getByRole("link", { name: "Dashboard" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Reports" })).toHaveCount(0);
  });

  test("a protected page bounces to the landing page", async ({ page }) => {
    await page.context().clearCookies();
    await page.goto("/dashboard");

    await expect(page).toHaveURL(/\/welcome/);
  });
});

test.describe("an employee", () => {
  test.use({ storageState: statePath("USER") });

  test("gets two links and no operator pages", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("link", { name: "My assets" })).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Event sign-out" }),
    ).toBeVisible();
    for (const hidden of ["Dashboard", "People", "Desks", "Reports", "Types"]) {
      await expect(page.getByRole("link", { name: hidden })).toHaveCount(0);
    }
  });

  test("sees only their own gear, and every row is theirs", async ({
    page,
  }) => {
    await page.goto("/");

    await expect(
      page.getByRole("heading", { name: "My assets" }),
    ).toBeVisible();
    const rows = page.locator("tbody tr");
    await expect(rows.first()).toBeVisible();
    // the count in the subtitle has to agree with the table under it
    const summary = await page.getByText(/assigned to you/).innerText();
    const claimed = Number(summary.match(/^(\d+)/)?.[1]);
    expect(await rows.count()).toBe(claimed);
  });

  test("is not offered controls that would be refused anyway", async ({
    page,
  }) => {
    await page.goto("/");

    await expect(page.getByRole("link", { name: /Add asset/i })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /Retire/i })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /Replace/i })).toHaveCount(0);
  });

  /**
   * The gap that made this file worth writing: these pages were absent from the
   * employee's nav but reachable by typing the URL, and the scoped reads meant
   * they rendered rather than erroring. Hiding a link is not authorization.
   */
  for (const guarded of [
    "/dashboard",
    "/people",
    "/desks",
    "/types",
    "/reports",
  ]) {
    test(`typing ${guarded} redirects away`, async ({ page }) => {
      await page.goto(guarded);

      await expect(page).not.toHaveURL(new RegExp(`${guarded}$`));
    });
  }
});

test.describe("a tech", () => {
  test.use({ storageState: statePath("TECH") });

  test("gets the operator nav including Types", async ({ page }) => {
    await page.goto("/dashboard");

    // asserted by destination, not by label: the nav only draws labels at xl,
    // so a text selector here would really be testing the breakpoint
    for (const href of [
      "/dashboard",
      "/people",
      "/desks",
      "/events",
      "/reports",
      "/types",
    ]) {
      await expect(page.locator(`nav a[href="${href}"]`).first()).toBeVisible();
    }
  });
});

/** Curating the type catalog is an operator job; HR only collects gear. */
test.describe("HR", () => {
  test.use({ storageState: statePath("HR") });

  test("is kept out of the type manager", async ({ page }) => {
    await page.goto("/types");

    await expect(page).not.toHaveURL(/\/types$/);
  });
});

test.describe("a POC", () => {
  test.use({ storageState: statePath("POC") });

  test("can open reports but not the type manager", async ({ page }) => {
    await page.goto("/reports");
    await expect(page.getByRole("heading", { name: "Reports" })).toBeVisible();

    await page.goto("/types");
    await expect(page).not.toHaveURL(/\/types$/);
  });
});
