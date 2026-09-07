import { cn } from "@/lib/cn";

/*
 * A file input is not a text input. The browser draws its own button inside
 * the box, so `px-3` leaves that button jammed against the left border with
 * nothing between it and the filename beside it, wearing default chrome that
 * matches nothing else on the page. This pads the box and restyles the button
 * as the secondary button it is meant to read as. The heights are deliberate:
 * 6px of padding either side of a 28px button fills the 40px control exactly,
 * so it lines up with every other input in a form.
 */
const FILE =
  "cursor-pointer py-1.5 pl-1.5 pr-3 text-muted-foreground " +
  "file:mr-3 file:h-7 file:cursor-pointer file:rounded file:border-0 " +
  "file:bg-muted file:px-3 file:text-xs file:font-medium file:text-foreground " +
  "file:transition-colors hover:file:bg-accent hover:file:text-primary";

export function Input({
  className,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        "h-10 w-full rounded-md border border-border bg-background px-3 text-sm outline-none transition-colors",
        props.type === "file" && FILE,
        "placeholder:text-muted-foreground",
        "focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-[hsl(var(--ring))]/25",
        className,
      )}
      {...props}
    />
  );
}
