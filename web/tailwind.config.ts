import type { Config } from "tailwindcss";

export default {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ["var(--font-sans)", "ui-sans-serif", "system-ui", "sans-serif"],
        display: ["var(--font-display)", "var(--font-sans)", "sans-serif"],
      },
      colors: {
        border: "hsl(var(--border))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        card: "hsl(var(--card))",
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        primary: {
          DEFAULT: "hsl(var(--primary))",
          2: "hsl(var(--primary-2))",
          foreground: "hsl(var(--primary-foreground))",
        },
        accent: "hsl(var(--accent))",
        destructive: "hsl(var(--destructive))",
        success: "hsl(var(--success))",
        warning: "hsl(var(--warning))",
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 0.125rem)",
      },
      boxShadow: {
        card: "0 1px 0 hsl(0 0% 100% / 0.04) inset, 0 1px 2px hsl(224 60% 3% / 0.3)",
        /*
         * Two neutral layers: a tight one that draws the panel edge itself,
         * and a wide one for the distance off the page. What this replaced
         * was a single brand-purple glow at -16px spread - an accent doing a
         * job that belongs to shadow, and inset so far it never reached the
         * edges that are what make a panel look lifted at all.
         */
        lift: "0 1px 0 hsl(0 0% 100% / 0.05) inset, 0 2px 6px -1px hsl(224 45% 4% / 0.16), 0 18px 36px -12px hsl(224 45% 4% / 0.34)",
      },
      /*
       * 180ms and 4px, down from 400ms and 8px.
       *
       * This fires on every page navigation across twenty pages. A technician
       * moving Assets -> Dashboard -> People does it dozens of times a day, and
       * at that frequency the standard is "reduced motion or none" - 400ms is
       * neither, and it also broke the sub-300ms budget for any UI animation.
       * What was left was 400ms of content sliding before it settled, every
       * time, on a screen someone is trying to read quickly.
       *
       * The curve is unchanged: cubic-bezier(0.22,1,0.36,1) is a strong
       * ease-out, which is exactly right for something entering.
       */
      keyframes: {
        "fade-in-up": {
          from: { opacity: "0", transform: "translateY(4px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "fade-in": {
          from: { opacity: "0" },
          to: { opacity: "1" },
        },
        "pop-in": {
          from: { opacity: "0", transform: "scale(0.96)" },
          to: { opacity: "1", transform: "scale(1)" },
        },
      },
      animation: {
        "fade-in-up": "fade-in-up 0.18s cubic-bezier(0.22,1,0.36,1) both",
        "fade-in": "fade-in 0.18s cubic-bezier(0.22,1,0.36,1) both",
        // menus and dropdowns: grows from its trigger, never from scale(0)
        "pop-in": "pop-in 0.15s cubic-bezier(0.22,1,0.36,1) both",
      },
    },
  },
  plugins: [],
} satisfies Config;
