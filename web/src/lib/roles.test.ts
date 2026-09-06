import { describe, expect, it } from "vitest";
import {
  asRole,
  canCollect,
  canOperateAssets,
  isSelfServiceUser,
  ROLE_BLURBS,
  ROLE_LABELS,
  type Role,
} from "@/lib/roles";

/**
 * These predicates decide what the console renders, and the console is where a
 * reviewer forms their impression of who may do what. The backend enforces the
 * same rules independently, so a mistake here is not a security hole - but it
 * either offers someone a button that will 403, or hides one they need.
 *
 * The cases are written per role rather than per function so that adding a role
 * and forgetting to place it shows up as a failure rather than a silent "false".
 */

const ALL: Role[] = ["ADMIN", "TECH", "POC", "HR", "USER"];

describe("role predicates", () => {
  it.each([
    // role,   operator, collector, selfService
    ["ADMIN", true, true, false],
    ["TECH", true, true, false],
    ["POC", false, false, false],
    ["HR", false, true, false],
    ["USER", false, false, true],
  ] as const)("%s", (role, operator, collector, selfService) => {
    expect(canOperateAssets(role)).toBe(operator);
    expect(canCollect(role)).toBe(collector);
    expect(isSelfServiceUser(role)).toBe(selfService);
  });

  it("every operator can also collect", () => {
    for (const role of ALL) {
      if (canOperateAssets(role)) expect(canCollect(role)).toBe(true);
    }
  });

  it("only the employee role is self-service", () => {
    expect(ALL.filter(isSelfServiceUser)).toEqual(["USER"]);
  });

  /**
   * An unknown or missing role must fall to the least privilege. A truthy
   * default here would hand the buttons to anyone whose token lacked a role.
   */
  it.each([null, undefined, "", "ROOT", "admin", "Tech"])(
    "denies everything for %o",
    (value) => {
      expect(canOperateAssets(value as string)).toBe(false);
      expect(canCollect(value as string)).toBe(false);
      expect(isSelfServiceUser(value as string)).toBe(false);
    },
  );

  it("is case-sensitive, matching the values the token actually carries", () => {
    expect(canOperateAssets("TECH")).toBe(true);
    expect(canOperateAssets("tech")).toBe(false);
  });
});

describe("asRole", () => {
  it.each(ALL)("recognises %s", (role) => expect(asRole(role)).toBe(role));

  it.each([null, undefined, "", "ROOT", "tech"])("rejects %o", (value) =>
    expect(asRole(value as string)).toBeNull(),
  );
});

describe("role copy", () => {
  it("labels and blurbs cover every role, so the menu never renders blank", () => {
    for (const role of ALL) {
      expect(ROLE_LABELS[role]).toBeTruthy();
      expect(ROLE_BLURBS[role]).toBeTruthy();
    }
  });

  it("distinguishes the two admin roles, which is the whole point of having both", () => {
    expect(ROLE_LABELS.TECH).not.toBe(ROLE_LABELS.POC);
  });
});
