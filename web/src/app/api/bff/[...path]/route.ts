import { NextRequest, NextResponse } from "next/server";
import { getSession } from "@/lib/session";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

/**
 * Authenticated proxy for the write actions the console performs: check-out,
 * return, transfer, offboard, and the create / edit / status calls. The browser
 * hits /api/bff/<path>; this attaches the bearer token from the httpOnly cookie
 * and forwards to the gateway's /api/<path>. Only an allow-list of paths is
 * permitted, and only for a signed-in user.
 */
const ALLOW: RegExp[] = [
  /^assignments$/,
  /^assignments\/return(\?.*)?$/,
  /^assignments\/transfer$/,
  /^assignments\/offboard(\?.*)?$/,
  /^assets$/,
  /^assets\/\d+$/,
  /^assets\/\d+\/status$/,
  /^people$/,
  /^people\/\d+\/(offboarding|departed|desk)$/,
  /^locations$/,
  /^clients$/,
];

async function forward(req: NextRequest, path: string[]) {
  const session = await getSession();
  if (!session) {
    return NextResponse.json({ code: "NO_SESSION" }, { status: 401 });
  }

  const rel = path.join("/") + req.nextUrl.search;
  if (!ALLOW.some((re) => re.test(rel))) {
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
