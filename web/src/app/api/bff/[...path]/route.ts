import { NextRequest, NextResponse } from "next/server";
import { getSession } from "@/lib/session";
import { isAllowedPath } from "@/lib/bff-allow";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

/**
 * Authenticated proxy for the write actions the console performs: check-out,
 * return, transfer, offboard, and the create / edit / status calls. The browser
 * hits /api/bff/<path>; this attaches the bearer token from the httpOnly cookie
 * and forwards to the gateway's /api/<path>. Only an allow-list of paths is
 * permitted, and only for a signed-in user.
 */
async function forward(req: NextRequest, path: string[]) {
  const session = await getSession();
  if (!session) {
    return NextResponse.json({ code: "NO_SESSION" }, { status: 401 });
  }

  const rel = path.join("/") + req.nextUrl.search;
  if (!isAllowedPath(rel)) {
    return NextResponse.json(
      { code: "FORBIDDEN_PATH", path: rel },
      { status: 403 },
    );
  }

  const init: RequestInit = {
    method: req.method,
    headers: {
      Authorization: `Bearer ${session.token}`,
      Accept: "application/json",
    },
  };

  // Forwarded, not generated here: the key has to be the *client's*, and stable
  // across its retries. A key minted per proxy hop would be new on every attempt,
  // which is the one thing that makes the mechanism useless.
  const idempotencyKey = req.headers.get("Idempotency-Key");
  if (idempotencyKey) {
    (init.headers as Record<string, string>)["Idempotency-Key"] =
      idempotencyKey;
  }
  if (req.method !== "GET" && req.method !== "HEAD") {
    const text = await req.text();
    if (text) {
      init.body = text;
      (init.headers as Record<string, string>)["Content-Type"] =
        "application/json";
    }
  }

  const res = await fetch(`${GATEWAY}/api/${rel}`, init);
  const text = await res.text();
  return new NextResponse(text || null, {
    status: res.status,
    headers: {
      "Content-Type": res.headers.get("Content-Type") ?? "application/json",
    },
  });
}

type Ctx = { params: Promise<{ path: string[] }> };

export async function GET(req: NextRequest, ctx: Ctx) {
  return forward(req, (await ctx.params).path);
}
export async function POST(req: NextRequest, ctx: Ctx) {
  return forward(req, (await ctx.params).path);
}
export async function PATCH(req: NextRequest, ctx: Ctx) {
  return forward(req, (await ctx.params).path);
}
export async function DELETE(req: NextRequest, ctx: Ctx) {
  return forward(req, (await ctx.params).path);
}
