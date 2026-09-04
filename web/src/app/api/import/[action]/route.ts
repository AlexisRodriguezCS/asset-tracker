import { NextRequest, NextResponse } from "next/server";
import { getSession } from "@/lib/session";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

const PATHS: Record<string, string> = {
  analyze: "/api/assets/import/analyze",
  preview: "/api/assets/import/preview",
  run: "/api/assets/import",
};

const PASS_THROUGH = [
  "clientId",
  "mapping",
  "createMissingTypes",
  "saveProfileAs",
];

/**
 * Forwards the spreadsheet-import wizard's multipart uploads to the gateway with the session
 * bearer. The file rides the multipart body; everything else goes on the query string (the
 * controller reads them as @RequestParam).
 */
export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ action: string }> },
) {
  const { action } = await params;
  const path = PATHS[action];
  if (!path) {
    return NextResponse.json(
      { message: "unknown import action" },
      { status: 404 },
    );
  }

  const session = await getSession();
  if (!session) {
    return NextResponse.json({ message: "not signed in" }, { status: 401 });
  }

  const form = await req.formData();
  const qs = new URLSearchParams();
  for (const key of PASS_THROUGH) {
    const value = form.get(key);
    if (typeof value === "string" && value !== "") qs.set(key, value);
  }

  const upstream = new FormData();
  const file = form.get("file");
  if (file) upstream.set("file", file);

  const res = await fetch(`${GATEWAY}${path}?${qs.toString()}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${session.token}` },
    body: upstream,
  });

  const text = await res.text();
  return new NextResponse(text, {
    status: res.status,
    headers: {
      "content-type": res.headers.get("content-type") ?? "application/json",
    },
  });
}
