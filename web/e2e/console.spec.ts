import { expect, test } from "@playwright/test";
import { statePath } from "./support";

/**
 * The staff pages render real data, and the numbers on them agree with each
 * other.
 *
 * These are cheap tests that catch an expensive class of bug: a page that
 * throws, a rollup wired to the wrong field, a summary strip that disagrees
 * with the table beneath it. All three are invisible to an API test, which sees
 * a correct payload either way.
 */

test.describe("as a tech", () => {
  test.use({ storageState: statePath("TECH") });

  test("the dashboard shows the fleet and its attention buckets", async ({
    page,
  }) => {
    await page.goto("/dashboard");

    await expect(
      page.getByRole("heading", { name: "Dashboard" }),
    ).toBeVisible();
    // scoped to the page, not the whole document: unscoped, this matched the
    // nav's own "Assets" label first, so it passed on the chrome being drawn
    // rather than on the stat tile it is here to check - and started failing
    // the moment the nav stopped drawing labels at this width.
    await expect(
      page.getByRole("main").getByText("Assets", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText(/Needs attention/i)).toBeVisible();
    // the buckets come from one purpose-built endpoint; an empty panel here
    // means it answered but with nothing, which is the failure worth catching
    await expect(page.getByText(/In repair or broken/i)).toBeVisible();
  });

  /**
   * Reports is a pile of rollups, and since they now all come from one endpoint
   * a single wrong field name empties several cards at once while the page
   * still renders and the API still returns 200. Only the browser sees that.
   */
  test("every reports rollup is actually drawn", async ({ page }) => {
    await page.goto("/reports");

    await expect(page.getByRole("heading", { name: "Reports" })).toBeVisible();

    for (const card of [
      "By type",
      "By status",
      "By condition",
      "Warranty",
      "By department (who holds it)",
      "Lifecycle events (audit trail)",
    ]) {
      await expect(page.getByText(card, { exact: true })).toBeVisible();
    }

    // fleet value renders as money, not as a raw cent count
    await expect(page.getByText(/^\$[\d,]+$/).first()).toBeVisible();
  });

  /**
   * Handing gear over and taking it back, both from the page about the person.
   *
   * This used to be a trip to each asset: find it, pick the person out of a dropdown, check it
   * out - and the only thing the person page offered was the offboarding sweep, which is far too
   * big a hammer for swapping a charger.
   *
   * The test hands one item over and gives it straight back, so it can run twice.
   */
  test("gear can be handed over and taken back from the person page", async ({
    page,
  }) => {
    await page.goto("/people/1");

    const rows = page.locator("li").filter({ hasText: "Assigned" });
    const before = await rows.count();

    const stock = page.getByLabel("Gear to hand over");
    const first = await stock.locator("option").nth(1).getAttribute("value");
    await stock.selectOption(first!);
    await page.getByRole("button", { name: "Hand over" }).click();

    await expect(rows).toHaveCount(before + 1);

    // and back, or the stockroom shrinks by one on every run
    await rows.last().getByRole("button", { name: "Return" }).click();
    await expect(rows).toHaveCount(before);
  });

  test("the asset list paginates rather than dumping the catalog", async ({
    page,
  }) => {
    await page.goto("/");

    const rows = page.locator("tbody tr");
    await expect(rows.first()).toBeVisible();
    // the page size is capped server-side; a catalog of hundreds must not all
    // land in one render
    expect(await rows.count()).toBeLessThanOrEqual(50);
  });

  test("opening an asset shows its detail and history", async ({ page }) => {
    await page.goto("/");
    await page.locator("tbody tr a").first().click();

    await expect(page).toHaveURL(/\/assets\/\d+/);
    await expect(page.getByText(/History|Trail|Audit/i).first()).toBeVisible();
  });

  test("the type manager lists types with their usage", async ({ page }) => {
    await page.goto("/types");

    await expect(
      page.getByRole("heading", { name: /Asset types/i }),
    ).toBeVisible();
    await expect(
      page.getByText("Laptop", { exact: false }).first(),
    ).toBeVisible();
  });

  test("people and desks render", async ({ page }) => {
    await page.goto("/people");
    await expect(page.getByRole("heading", { name: "People" })).toBeVisible();

    await page.goto("/desks");
    await expect(
      page.getByRole("heading", { name: "Desks", exact: true }).first(),
    ).toBeVisible();
  });
});

/**
 * The nav is the thing that breaks silently at odd widths, and 1100px is where
 * seven labelled links plus a search box stopped fitting.
 */
test.describe("the nav at awkward widths", () => {
  test.use({ storageState: statePath("TECH") });

  test("at 1100px nothing overflows the viewport", async ({ page }) => {
    await page.setViewportSize({ width: 1100, height: 800 });
    await page.goto("/dashboard");

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test("on a phone the links collapse into a menu", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/dashboard");

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
    // the full link row is not rendered inline at this width
    await expect(page.getByRole("link", { name: "Reports" })).toBeHidden();
  });

  test("everything account-related lives in one menu", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/dashboard");

    // Sign out is a menuitem, not a button - it carries an explicit
    // role="menuitem". Asking for a button matched nothing either way, so the
    // "not in the header" half of this test passed without proving anything.
    const signOut = page.getByRole("menuitem", { name: /Sign out/i });

    // closed: it is inside the dropdown, not loose in the header
    await expect(signOut).toHaveCount(0);

    await page.getByRole("button", { name: "Account" }).click();
    await expect(signOut).toBeVisible();
  });
});
