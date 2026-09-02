"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

/**
 * Catches render/fetch failures in any route (most often the API gateway being
 * unreachable). Keeps the app chrome; offers a retry that re-runs the server
 * components rather than a hard reload.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const gatewayDown = /fetch failed|ECONNREFUSED|gateway/i.test(error.message);

  return (
    <div className="mx-auto max-w-md animate-fade-in-up py-16 text-center">
      <h1 className="font-display text-2xl font-semibold tracking-tight">
        Something went wrong
      </h1>
      <p className="mt-2 text-sm text-muted-foreground">
        {gatewayDown
          ? "The platform API didn't respond. It may still be starting up."
          : "An unexpected error occurred while loading this page."}
      </p>
      <Card className="mt-6 space-y-3">
        <Button className="w-full" onClick={reset}>
          Try again
        </Button>
        <Link
          href="/dashboard"
          className="block text-sm text-muted-foreground hover:text-foreground"
        >
          Back to the dashboard
        </Link>
        {error.digest && (
          <p className="text-xs text-muted-foreground">
            Reference: <span className="font-mono">{error.digest}</span>
          </p>
        )}
      </Card>
    </div>
  );
}
