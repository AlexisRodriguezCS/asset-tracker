import { cn } from "@/lib/cn";

type Props = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "outline" | "ghost";
  size?: "sm" | "md" | "lg";
};

export function Button({
  className,
  variant = "primary",
  size = "md",
  children,
  ...props
}: Props) {
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-md font-medium transition-[transform,background-color,border-color,opacity] duration-150",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[hsl(var(--ring))] focus-visible:ring-offset-2 focus-visible:ring-offset-background",
        "disabled:pointer-events-none disabled:opacity-50",
        size === "sm" && "h-8 px-3 text-xs",
        size === "md" && "h-10 px-4 text-sm",
        size === "lg" && "h-11 px-6 text-sm",
        variant === "primary" &&
          "bg-gradient-primary text-primary-foreground hover:brightness-110 active:scale-[0.98]",
        variant === "outline" &&
          "border border-border bg-transparent hover:border-primary/50 hover:bg-accent active:scale-[0.98]",
        variant === "ghost" && "hover:bg-muted",
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}
