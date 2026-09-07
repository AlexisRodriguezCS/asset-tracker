import { defineConfig, devices } from "@playwright/test";

/**
 * Browser tests: the console driven the way a person drives it.
 *
 * Every other suite in this repo stops at an HTTP boundary. The REST-Assured
 * `e2e/` suite proves the platform answers correctly; the Vitest tests prove the
 * console's pure logic; `infra/qa/api-matrix.cjs` proves the refusals refuse.
 * None of them press a button, and plenty can break where they meet: a control
 * that never renders, a disabled state that lies, a form that posts the wrong
 * shape, a redirect that does not fire.
 *
 * These run against a *running* console and a *running* platform. That is the
 * point - a stub would only re-test what the other suites already cover.
 */
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "./e2e",
  // Custody is global state: two workers handing out the same TV race each
  // other. The suite is small and the flows are sequential by nature.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]]
    : [["list"]],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [
    // one real login per persona, saved for everything else to reuse
    { name: "setup", testMatch: /auth.setup.ts/ },
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
      dependencies: ["setup"],
    },
  ],
});
