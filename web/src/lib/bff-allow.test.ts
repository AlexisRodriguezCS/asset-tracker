import { describe, expect, it } from "vitest";
import { ALLOW, isAllowedPath } from "@/lib/bff-allow";

/**
 * The BFF attaches the signed-in user's bearer token server-side, so anything
 * this list lets through is a request made with their credentials. The failure
 * mode is not a broken page - it is an authenticated proxy to whatever an
 * attacker can name. These cases are written as attempts to get through.
 */

describe("paths the console actually uses", () => {
  it.each([
    "assignments",
    "assignments/return?assetId=1&clientId=1",
    "assignments/transfer",
    "assignments/offboard?clientId=1&personId=2",
    "assignments/event-requests",
    "assignments/event-requests/12/approve",
    "assignments/event-requests/12/deny",
    "assignments/event-requests/12/fulfil",
    "assignments/event-equipment",
    "assignments/event-equipment?clientId=1&date=2026-10-03",
    "assignments/event-equipment/7",
    "assets",
    "assets/42",
    "assets/42/status",
    "assets/types",
    "assets/types/7?clientId=1",
    "people",
    "people/3/offboarding",
    "people/3/departed",
    "people/3/desk",
    "locations",
    "clients",
  ])("allows %s", (path) => expect(isAllowedPath(path)).toBe(true));
});

describe("refuses anything not on the list", () => {
  it.each([
    // endpoints that exist but the console has no business proxying
    "auth/login",
    "auth/register",
    "assets/audit",
    "assets/import",
    "assets/stats",
    "assets/attention",
    "notifications",
    "actuator/health",
    "actuator/env",
    // plausible-looking but unlisted verbs
    "assets/42/delete",
    "assets/42/assign",
    "people/3/delete",
    "clients/1",
    "assignments/event-requests/12",
    "assignments/event-requests/12/close",
    "assignments/event-equipment/7/delete",
    "assignments/event-equipment/abc",
  ])("refuses %s", (path) => expect(isAllowedPath(path)).toBe(false));
});

describe("cannot be walked out of", () => {
  it.each([
    "assets/../auth/login",
    "assets/42/../../auth/login",
    "../auth/login",
    "..%2Fauth%2Flogin",
    "assets/42%2Fdelete",
    "assets//42",
    "/assets",
    " assets",
    "assets ",
  ])("refuses %o", (path) => expect(isAllowedPath(path)).toBe(false));

  /**
   * Every pattern is anchored at both ends. An unanchored one would make
   * "assets" match "assets/anything", which is exactly how this kind of list
   * usually springs a leak.
   */
  it("anchors every pattern", () => {
    for (const re of ALLOW) {
      expect(
        re.source.startsWith("^"),
        `${re} is not anchored at the start`,
      ).toBe(true);
      expect(re.source.endsWith("$"), `${re} is not anchored at the end`).toBe(
        true,
      );
    }
  });

  it("does not accept a suffix after an allowed prefix", () => {
    expect(isAllowedPath("assets/evil")).toBe(false);
    expect(isAllowedPath("assetsevil")).toBe(false);
    expect(isAllowedPath("clients/../assets")).toBe(false);
  });
});

describe("ids are numeric", () => {
  it.each([
    "assets/abc",
    "assets/1a",
    "assets/-1",
    "people/x/desk",
    "assignments/event-requests/x/approve",
  ])("refuses non-numeric id in %s", (path) =>
    expect(isAllowedPath(path)).toBe(false),
  );
});

describe("query strings", () => {
  it("allows one only where a pattern opted in", () => {
    expect(isAllowedPath("assignments/return?assetId=1")).toBe(true);
    // /^assets$/ has no query allowance, so this is refused rather than forwarded
    expect(isAllowedPath("assets?clientId=1")).toBe(false);
    expect(isAllowedPath("assets/42?force=true")).toBe(false);
  });

  it("a query cannot smuggle a different path in", () => {
    expect(isAllowedPath("assignments/return?x=/../../auth/login")).toBe(true);
    // ...which is safe only because the path half is fixed; the gateway routes
    // on the path, and the query is carried as a query.
    expect(isAllowedPath("auth/login?assetId=1")).toBe(false);
  });
});
