import type { Config } from "tailwindcss";

export default {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      /*
       * A breakpoint named for the thing it decides: the width at which the
       * nav can afford words next to its icons.
       *
       * It used to be xl. Seven labelled links cost ~670px, and with the
       * wordmark, tenant picker and account menu around them the row needs
       * ~1415px before the search field is left with a usable width - so at
       * xl (1280) labels appeared *by taking the search box's space*, down to
       * about 130px, narrow enough to clip its own placeholder. Below this the
       * links are icons with tooltips and the search is roomy; above it,
       * everything fits at once.
       */
      screens: { nav: "1440px" },
      fontFamily: {
        sans: ["var(--font-sans)", "ui-sans-serif", "system-ui", "sans-serif"],
        display: ["var(--font-display)", "var(--font-sans)", "sans-serif"],
      },
      colors: {
        border: "hsl(var(--border))",
        "border-strong": "hsl(var(--border-strong))",
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
        /* Defined per theme in globals.css - see the note there. */
        lift: "var(--shadow-lift)",
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
