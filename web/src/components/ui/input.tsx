import { cn } from "@/lib/cn";

export function Input({
  className,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        "h-10 w-full rounded-md border border-border bg-background px-3 text-sm outline-none transition-colors",
        "placeholder:text-muted-foreground",
        "focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-[hsl(var(--ring))]/25",
        className,
      )}
      {...props}
    />
  );
}
