import { expect, test, type Browser, type Page } from "@playwright/test";
import { poolFor, RUN, statePath, uniqueEventDate } from "./support";

/**
 * The event sign-out flow, clicked the whole way through: an employee asks, a
 * POC approves, a tech hands out real units, and the gear turns up on the
 * employee's own list.
 *
 * This is the one journey that crosses every role and both services, and until
 * it was driven in a browser it had only ever been checked a call at a time.
 * Doing it by hand found nothing; doing it by hand *every time* is the part
 * that does not scale.
 *
 * Each step runs as a different person, so each opens its own context from that
 * persona's saved session rather than the suite sharing one - which is also
 * what stops a step passing on the previous role's cookie by accident.
 */

const EVENT = `Playwright Fair ${RUN}`;
const ITEM_NOTE = "One needs an HDMI adapter";

/** Runs `body` in a fresh browser context signed in as `role`. */
async function as(
  browser: Browser,
  role: "USER" | "POC" | "TECH",
  body: (page: Page) => Promise<void>,
): Promise<void> {
  const context = await browser.newContext({ storageState: statePath(role) });
  try {
    await body(await context.newPage());
  } finally {
    await context.close();
  }
}

test.describe.serial("an event from request to hand-out", () => {
  const date = uniqueEventDate();
  let requestUrl = "";
  /**
   * Puts every TV back in the stockroom.
   *
   * Without this the suite passes once and then fails forever: it hands two TVs
   * out of a pool of four and never returns them, so a later run finds an empty
   * stockroom and reports it as a broken hand-out. The API matrix and the
   * REST-Assured suite each had to learn the same thing - a test that consumes
   * shared state has to put it back.
   *
   * It runs *before* as well as after, and returns every assigned TV rather
   * than only the ones this run took. The first version recorded what it handed
   * out and gave that back, which quietly did nothing whenever a test failed
   * before reaching the bookkeeping - so a single red run poisoned every run
   * after it. Cleanup that only works on the happy path is not cleanup. No TV
   * is assigned to anyone in the seed data, so "return them all" is exactly
   * right and safe to repeat.
   */
  async function returnEveryTv(browser: Browser): Promise<void> {
    await as(browser, "TECH", async (page) => {
      // Found through the catalog page rather than a BFF read. `/^assets$/` in
      // the allow-list takes no query string on purpose, and widening the
      // proxy's authorization surface to make a test tidier is the wrong trade.
      await page.goto("/?type=TV&status=ASSIGNED");
      const ids = await page
        .locator('tbody tr a[href^="/assets/"]')
        .evaluateAll((links) =>
          links
            .map((a) => Number(a.getAttribute("href")?.split("/").pop()))
            .filter((n) => Number.isFinite(n)),
        );

      for (const id of ids) {
        // returning *is* on the allow-list - it is a thing the console does
        await page.request.post(
          `/api/bff/assignments/return?assetId=${id}&clientId=1`,
        );
      }
    });
  }

  test.beforeAll(async ({ browser }) => returnEveryTv(browser));
  test.afterAll(async ({ browser }) => returnEveryTv(browser));

  test("an employee requests two TVs for a day that has them", async ({
    browser,
  }) => {
    await as(browser, "USER", async (page) => {
      // arrange: the day must genuinely have two TVs free, or the later
      // "they ran out" assertion is proving nothing
      expect((await poolFor(page, date)).TV).toBe(2);

      await page.goto("/events");
      await page.getByRole("link", { name: /New request/i }).click();
      await expect(
        page.getByRole("heading", { name: "Event sign-out" }),
      ).toBeVisible();

      await page.getByLabel(/Event name/i).fill(EVENT);
      await page.getByLabel("Date", { exact: false }).fill(date);

      const tvRow = page.locator("li").filter({ hasText: "TV" }).first();
      await expect(tvRow).toContainText("2 of 2 free");

      await tvRow.getByRole("button", { name: "One more TV" }).click();
      await tvRow.getByRole("button", { name: "One more TV" }).click();
      await expect(tvRow.getByLabel("How many TV")).toHaveValue("2");

      // the per-item note only appears once something is picked, and it is the
      // only way a requester can say "one of them needs an adapter"
      const note = tvRow.getByLabel("Note about the TV");
      await expect(note).toBeVisible();
      await note.fill(ITEM_NOTE);

      // a third is not on offer, because the client only owns two
      await expect(
        tvRow.getByRole("button", { name: "One more TV" }),
      ).toBeDisabled();

      await page.getByRole("button", { name: /Submit request/i }).click();

      await expect(page).toHaveURL(/\/events$/);
      const card = page.locator("li").filter({ hasText: EVENT });
      await expect(card).toContainText("Waiting on a decision");
      await expect(card).toContainText("2 × TV");
    });
  });

  /** The whole reason the pool exists: the day is now empty for everyone. */
  test("the same day now offers no TVs to anybody", async ({ browser }) => {
    await as(browser, "USER", async (page) => {
      await page.goto("/events/new");
      await page.getByLabel("Date", { exact: false }).fill(date);

      const tvRow = page.locator("li").filter({ hasText: "TV" }).first();
      await expect(tvRow).toContainText("all 2 booked that day");
      await expect(
        tvRow.getByRole("button", { name: "One more TV" }),
      ).toBeDisabled();
      await expect(tvRow.getByLabel("How many TV")).toBeDisabled();

      // the next day is untouched - the hold is per-day, not global
      await page.getByLabel("Date", { exact: false }).fill(uniqueEventDate(1));
      await expect(tvRow).toContainText("2 of 2 free");
    });
  });

  test("a POC approves it, and is not offered the hand-out", async ({
    browser,
  }) => {
    await as(browser, "POC", async (page) => {
      await page.goto("/events");
      // the list shows every request, so scope to this run's card before
      // clicking - and wait for the detail route, or the assertions below are
      // still reading the list and match eight other requests
      await page.getByRole("link").filter({ hasText: EVENT }).first().click();
      await page.waitForURL(/\/events\/\d+$/);
      await expect(page.getByRole("heading", { name: EVENT })).toBeVisible();

      // the note the requester typed against the line, carried through the API
      // and rendered here - a path that existed on both ends but was dead in
      // the middle, because the form used to post null
      await expect(page.getByText(ITEM_NOTE)).toBeVisible();

      // approving is a POC's job; moving custody is not
      await expect(
        page.getByRole("heading", { name: /Hand the gear out/i }),
      ).toHaveCount(0);

      await page.getByRole("button", { name: "Approve", exact: true }).click();

      await expect(page.getByText("Approved", { exact: true })).toBeVisible();
      await expect(page.getByText("poc@acme.example")).toBeVisible();
      requestUrl = page.url();
    });
  });

  test("a tech hands out two real units", async ({ browser }) => {
    await as(browser, "TECH", async (page) => {
      await page.goto(requestUrl);
      await expect(
        page.getByRole("heading", { name: /Hand the gear out/i }),
      ).toBeVisible();

      // picking is by tag: the request said "2 TVs", the tech says which two.
      // Only units actually in the stockroom are offered, so if an earlier run
      // left them all out this is where it shows.
      const tags = page.getByRole("button", { name: /^ACME-TV-/ });
      await expect(tags.first()).toBeVisible();
      expect(await tags.count()).toBeGreaterThanOrEqual(2);

      await tags.nth(0).click();
      await tags.nth(1).click();

      const handOut = page.getByRole("button", { name: /Hand out 2 items/i });
      await expect(handOut).toBeEnabled();
      await handOut.click();

      // "Handed out" is both the status badge and part of "2 handed out",
      // so each is asserted by its exact text rather than by substring
      await expect(page.getByText("Handed out", { exact: true })).toBeVisible();
      await expect(
        page.getByText("2 handed out", { exact: true }),
      ).toBeVisible();
    });
  });

  /**
   * The assertion that makes the rest of it mean something. Event gear goes out
   * through the ordinary check-out path, so it has to appear on the requester's
   * own list exactly like anything else she holds - same status, same table.
   */
  test("the TVs are now on the employee's own asset list", async ({
    browser,
  }) => {
    await as(browser, "USER", async (page) => {
      await page.goto("/");
      const tvRows = page.locator("tbody tr").filter({ hasText: "TV" });
      await expect(tvRows.first()).toContainText("Assigned");
      expect(await tvRows.count()).toBeGreaterThanOrEqual(2);
    });
  });
});
