import { beforeEach, describe, expect, it, vi } from "vitest";

const cookieStore = new Map<string, string>();
vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      cookieStore.has(name)
        ? { name, value: cookieStore.get(name) }
        : undefined,
    set: (name: string, value: string) => cookieStore.set(name, value),
    delete: (name: string) => cookieStore.delete(name),
  }),
}));

const { jwtDecode } = await import("@/lib/jwt");
const { getSession } = await import("@/lib/session");
const { currentClientId } = await import("@/lib/client");

/** Builds an unsigned token; nothing here verifies signatures - the gateway does. */
function token(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) =>
    Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "RS256" })}.${b64(claims)}.signature-not-checked-here`;
}

const hoursFromNow = (h: number) => Math.floor(Date.now() / 1000) + h * 3600;

beforeEach(() => cookieStore.clear());

describe("jwtDecode", () => {
  it("reads the payload of a well-formed token", () => {
    expect(jwtDecode(token({ sub: "a@b.c", role: "TECH" }))).toMatchObject({
      sub: "a@b.c",
      role: "TECH",
    });
  });

  it("handles base64url padding, which real tokens use", () => {
    const claims = { sub: "someone+with/padding@example.com", role: "USER" };
    expect(jwtDecode(token(claims))?.sub).toBe(claims.sub);
  });

  it.each(["", "not-a-token", "only.two", "a.b.c.d", "...."])(
    "returns null rather than throwing for %o",
    (bad) => expect(jwtDecode(bad)).toBeNull(),
  );

  it("returns null when the payload is not JSON", () => {
    expect(jwtDecode("header.bm90LWpzb24.sig")).toBeNull();
  });
});

describe("getSession", () => {
  it("is null with no cookie", async () => {
    expect(await getSession()).toBeNull();
  });

  it("reads subject, role, clientIds and personId off the token", async () => {
    cookieStore.set(
      "att_session",
      token({
        sub: "dana.reyes@acme.example",
        role: "USER",
        clientIds: [1],
        personId: 1,
        exp: hoursFromNow(1),
      }),
    );
    expect(await getSession()).toMatchObject({
      subject: "dana.reyes@acme.example",
      role: "USER",
      clientIds: [1],
      personId: 1,
    });
  });

  /** An expired cookie must read as signed-out, not as a session the UI trusts. */
  it("is null once the token has expired", async () => {
    cookieStore.set(
      "att_session",
      token({ sub: "a@b.c", exp: hoursFromNow(-1) }),
    );
    expect(await getSession()).toBeNull();
  });

  it("is null for a malformed cookie value", async () => {
    cookieStore.set("att_session", "garbage");
    expect(await getSession()).toBeNull();
  });

  it("defaults personId to null when the token has none, so staff are not mistaken for an employee", async () => {
    cookieStore.set(
      "att_session",
      token({
        sub: "t@a.c",
        role: "TECH",
        clientIds: [1, 2],
        exp: hoursFromNow(1),
      }),
    );
    expect((await getSession())?.personId).toBeNull();
  });

  it("tolerates a missing clientIds claim rather than throwing", async () => {
    cookieStore.set(
      "att_session",
      token({ sub: "t@a.c", role: "TECH", exp: hoursFromNow(1) }),
    );
    expect((await getSession())?.clientIds).toEqual([]);
  });
});

describe("currentClientId", () => {
  const signedInAs = (clientIds: number[]) =>
    cookieStore.set(
      "att_session",
      token({ sub: "t@a.c", role: "TECH", clientIds, exp: hoursFromNow(1) }),
    );

  it("defaults to the first tenant when no cookie is set", async () => {
    signedInAs([1, 2, 3]);
    expect(await currentClientId()).toBe(1);
  });

  it("honours a cookie naming a tenant the session may act on", async () => {
    signedInAs([1, 2, 3]);
    cookieStore.set("att_client", "2");
    expect(await currentClientId()).toBe(2);
  });

  /**
   * The regression this exists for: a POC scoped to Acme kept a stale cookie
   * naming Initech and every page rendered zeroes instead of saying why. The
   * cookie is a preference, not a grant.
   */
  it("ignores a cookie naming a tenant the session may not act on", async () => {
    signedInAs([1]);
    cookieStore.set("att_client", "3");
    expect(await currentClientId()).toBe(1);
  });

  it.each(["", "abc", "-1", "0", "99999999999999999999"])(
    "falls back to a permitted tenant for cookie %o",
    async (value) => {
      signedInAs([2, 3]);
      cookieStore.set("att_client", value);
      expect([2, 3]).toContain(await currentClientId());
    },
  );
});
