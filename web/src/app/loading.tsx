/** Shown while a route's server components fetch from the gateway. */
export default function Loading() {
  return (
    <div className="animate-fade-in-up space-y-6" aria-busy="true">
      <div className="h-9 w-48 rounded-md bg-muted" />
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {Array.from({ length: 5 }).map((_, i) => (
          <div
            key={i}
            className="h-20 rounded-lg border border-border bg-card/60"
          />
        ))}
      </div>
      <div className="space-y-2">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-11 rounded-md bg-muted/70" />
        ))}
      </div>
    </div>
  );
}
