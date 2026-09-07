import { test as setup } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import { AUTH_DIR, PERSONAS, statePath } from "./support";

/**
 * Signs in once per persona and saves the session, for every spec to reuse.
 *
 * This exists because the first version signed in inside each test and the
 * suite knocked itself over: the gateway rate-limits `POST /api/auth/**` to ten
 * a minute per IP to blunt credential stuffing, and twenty-odd tests each doing
 * a real login blew that budget in seconds. Fifteen tests failed, none of them
 * for the reason they were written to catch.
 *
 * Five logins for the whole run also happens to be the honest shape: signing in
 * is not what these tests are about, so paying for it once is right.
 */
setup("sign in as every persona", async ({ browser }) => {
  fs.mkdirSync(AUTH_DIR, { recursive: true });

  for (const role of PERSONAS) {
    const context = await browser.newContext();
    const page = await context.newPage();

    // The rate limiter is per IP and shared with anything else hitting the
    // stack, so back off and retry rather than failing the whole run.
    let signedIn = false;
    for (let attempt = 1; attempt <= 4 && !signedIn; attempt++) {
      const res = await page.request.post("/api/auth/demo", { data: { role } });
      if (res.ok()) {
        signedIn = true;
        break;
      }
      if (res.status() !== 429) {
        throw new Error(
          `demo sign-in as ${role} failed: ${res.status()} ${await res.text()}`,
        );
      }
      const wait = Number(res.headers()["retry-after"] ?? 30) + 2;
      setup.setTimeout(setup.info().timeout + wait * 1000);
      await page.waitForTimeout(wait * 1000);
    }
    if (!signedIn) {
      throw new Error(`demo sign-in as ${role} never got past the rate limit`);
    }

    await context.storageState({ path: statePath(role) });
    await context.close();
  }
});

/** Kept so the directory is obviously a build artifact, not a source folder. */
setup.afterAll(() => {
  fs.writeFileSync(
    path.join(AUTH_DIR, ".gitignore"),
    "# Saved browser sessions, written by auth.setup.ts\n*\n",
  );
});
