# DESIGN.md — asset-tracker console

The design system this console already uses, written down so it stays consistent.

Not aspirational: every value here is read out of `src/app/globals.css`,
`tailwind.config.ts`, and the components in `src/components/ui`. If you change
one of those, change this.

This is a **dense operational console**, not a marketing site. Technicians scan
tables of a hundred rows looking for the one that is wrong. Every rule below
serves that: quiet by default, colour only where it means something, nothing
decorative competing with data.

---

## Colour

Tokens are HSL triples on `:root`, swapped under `prefers-color-scheme: dark`.
There is no manual theme toggle — the OS decides.

**Always use the token, never the raw palette.** `text-amber-500` and
`text-emerald-600` were both in here once, alongside the tokens that meant the
same thing, and the two disagreed about which shade of each. There are now no
raw Tailwind palette colours anywhere in `src/`.

| token                | light         | dark          | means                          |
| -------------------- | ------------- | ------------- | ------------------------------ |
| `background`         | `0 0% 100%`   | `0 0% 10%`    | the page                       |
| `card`               | `0 0% 100%`   | `0 0% 13%`    | any raised surface             |
| `foreground`         | `45 8% 20%`   | `0 0% 83%`    | primary text                   |
| `muted`              | `45 20% 96%`  | `0 0% 15%`    | recessed fill                  |
| `muted-foreground`   | `45 2% 46%`   | `0 0% 61%`    | secondary text                 |
| `border`             | `60 4% 91%`   | `0 0% 18%`    | every border, globally         |
| `primary`            | `265 56% 32%` | `266 72% 70%` | brand, actions, links          |
| `primary-2`          | `272 48% 48%` | `274 66% 74%` | gradient partner only          |
| `primary-foreground` | `0 0% 100%`   | `266 60% 10%` | text on primary                |
| `accent`             | `254 62% 96%` | `255 28% 20%` | hover / selected tint          |
| `destructive`        | `0 72% 51%`   | `0 70% 62%`   | broken, lost, denied, delete   |
| `success`            | `152 60% 36%` | `152 58% 48%` | in stock, approved, handed out |
| `warning`            | `32 95% 44%`  | `43 96% 56%`  | in repair, awaiting a decision |
| `ring`               | `265 56% 40%` | `266 72% 70%` | focus ring                     |

Light `primary` is BUILD Chicago's brand purple, `#4b2480`. Dark mode lifts it
rather than reusing it — the brand purple is far too dark on a near-black page.

`--border` is applied globally (`* { border-color: hsl(var(--border)) }`), so a
bare `border` class is already the right colour. Do not restate it.

### The brand is an accent, not a theme

**Surfaces are neutral, and warm in light.** Light greys carry a yellow bias
after Notion, and the ink is `#37352f` rather than black — that pair is most of
why reading there is comfortable for an hour. Dark is a true neutral charcoal at
`#191919`, not a near-black: a near-black ground forces hairlines lighter to
stay visible, which makes them louder than the rows they separate. Purple is spent in four places and nowhere else: the brand mark
and its gradient, the primary action, the active nav item, and `--accent` for
hover and selection.

This was learned by breaking it. Tinting the surfaces purple to "match" the
accent made every pixel purple — ground, cards, borders and text all in one
narrow band — and an accent cannot stand out against a background that is
already the accent. Two things did most of the damage:

- **the ambient wash.** Three lavender radials over `body` read as depth on the
  old blue-grey ground; over a neutral near-black one they became the page's
  dominant hue, tinting every table through the glass. It is now
  `.ambient-wash`, used by the landing page alone, where atmosphere is the job.
- **purple doing data work.** The asset tag was `text-primary`, so the catalog
  drew fifty brand-coloured items in one column, and `ASSIGNED` was toned
  `info`, putting a purple dot on a third of every list. Both are neutral now.

A useful corollary: **a column that is entirely links does not need link
colour.** It distinguishes nothing there and only paints the column. Colour a
link that sits inside a sentence; leave the column alone.

### Colour has to earn its place

**The most important rule here.** Colour marks the exception, never the norm.

Condition used to tone `GOOD` as `success`, which put a green pill in the
condition column beside the green `In stock` pill in the status column — two
identical chips, one column apart, meaning different things, on a catalog where
most rows are both. Nothing could stand out because everything already was.

So: a healthy grade renders as plain `text-muted-foreground`, and only `FAIR`,
`POOR` and `DAMAGED` get a chip. A normal row carries exactly one point of
colour — its status. A damaged unit that is also broken shows two reds, because
there both have earned it.

Apply the same test to anything new: **if this colour appears on most rows, it
is decoration, and it is costing you the ability to highlight anything.**

### Gradients

Two utilities, both `100deg` from `primary` to `primary-2`:

- `.bg-gradient-primary` — primary buttons and the brand mark. Nine uses.
- `.text-gradient` — reserved for display headings.

`.ambient-wash` carries three faint radial washes of `primary` / `primary-2`,
pushed to the corners, drifting over 28s. **The landing page uses it; no other
surface does.** It sat on `body` once and tinted the entire console. Do not put
it back there, and do not add a second one.

## Typography

- **Sans:** Inter (`--font-sans`) — everything.
- **Display:** Space Grotesk (`--font-display`) — `h1`, `h2`, `.font-display`
  only, at `-0.02em` tracking.
- **Mono:** the system stack, for asset tags, serials and IMEIs.

The scale in real use, by frequency: `text-sm` (128), `text-xs` (75), `text-2xl`
(7), `text-lg` (3), `text-3xl` (2). That shape is correct for this product —
`sm` is body, `xs` is metadata, and the large sizes appear once per page in a
`PageHeader`. Reach for `text-sm` first.

`text-[10px]` and `text-[11px]` exist in a couple of dense badges. Do not add
more; if something needs to be smaller than `xs`, it probably needs to be
somewhere else.

## Shape, depth, spacing

- `--radius: 0.4375rem` (7px). `rounded-lg` is the radius; `rounded-md` is
  `calc(radius - 0.125rem)` = 5px for controls; `rounded-full` for avatars and
  status dots. It was 16px, which put a consumer-app roundness on every tile and
  table shell — the loudest thing about the old chrome.
- `shadow-card` on resting surfaces. `shadow-lift` exists for one raised case.
  There is no third elevation — depth is carried by `border` and `bg-card`, not
  by stacked shadows.
- Spacing runs on Tailwind's 4px scale. Page sections are `space-y-6`, cards are
  `p-5`/`p-6`, table cells `px-4 py-2`.
- Content maxes at `max-w-7xl`; forms and prose at `max-w-3xl`.

## Components

Use these before writing new markup. All in `src/components/ui`.

| component    | what it is                                                                  |
| ------------ | --------------------------------------------------------------------------- |
| `Card`       | `rounded-lg border bg-card p-6 shadow-card` — the default surface           |
| `Button`     | `primary` (gradient) / `outline` / `ghost`; `sm` 32px, `md` 40px, `lg` 44px |
| `Badge`      | a **dot and a word**; tones `neutral` `success` `warn` `danger` `info`      |
| `StatStrip`  | the 2/3/5-up summary numbers at the top of a page                           |
| `TableCard`  | the table shell: rounded card, `overflow-x-auto`, hover per row             |
| `PageHeader` | title, subtitle, optional right-hand action                                 |

`AssetStatusBadge`, `PersonStatusBadge`, `ConditionBadge` and `EventStatusBadge`
map domain states to tones. **Add a state to those maps rather than styling a
badge inline** — the two badge systems drifting apart is what produced the
double-green problem above.

`TableCard`'s header is deliberately **not** sticky. It needs `overflow-x-auto`
for narrow screens; setting either overflow axis makes the other a scroll
container too, and a sticky header then resolves against a box that cannot
scroll, so it never sticks. The table is paginated to 50 rows instead.

## Motion

- `animate-fade-in-up` — **180ms**, 4px of travel, `cubic-bezier(0.22, 1, 0.36, 1)`,
  on the root of every page. It was 400ms and 8px, which broke two rules at once:
  the sub-300ms budget for UI, and the guidance that something seen dozens of
  times a day gets reduced motion or none. The curve is a strong ease-out and is
  correct for an entrance — only the duration was wrong.
- `animate-pop-in` — 150ms, `scale(0.96) → 1`. Menus and dropdowns only, and
  always with a `transform-origin` on the trigger they hang from: a panel that
  scales from its own centre reads as arriving from nowhere. Never animate from
  `scale(0)`.
- Interactive feedback is 150ms: `transition-colors`, `active:scale-[0.98]` on
  buttons, `hover:brightness-110` on primary.
- `prefers-reduced-motion: reduce` is **gentler, not off**. Movement is dropped
  and the opacity fade is kept, because the fade carries no motion and still
  helps a new page register as new. Setting `animation: none` threw the fade
  away too, which is over-correcting. Anything animated you add must be covered
  there — and covered this way.

## Focus and hit targets

Every interactive element takes `focus-visible:ring-2` on `--ring` with
`ring-offset-2 ring-offset-background`. Never remove the ring; never rely on
`:hover` alone to signal interactivity.

Minimum touch target is 44×44. Where a control is deliberately smaller — the
nav's 36px icon buttons inside a 56px bar — use `.touch-target`, which extends
the hit area with a pseudo-element without changing how big it looks.

## Responsive

Mobile-first. Breakpoints in use: `sm` 640 (11), `md` 768 (11), `lg` 1024 (4),
`xl` 1280 (3).

The nav is the tight case and is tuned deliberately: a hamburger holds the links
until `md`, search appears at `md`, the tenant picker at `sm`, and icons gain
text labels only at `xl` — seven labelled links plus a search field do not fit
at 1100px, and search is what gets squeezed. Browser tests assert no horizontal
overflow at 1100px and 390px; keep them passing.

## States

Every list needs an empty state that says what to do next, not just "no data".
Async routes fall back to `app/loading.tsx`, whose skeleton blocks **pulse** —
held still they read as a layout that failed rather than one on its way.

## Don't

- Don't introduce a raw Tailwind palette colour. There are none left; add a
  token.
- Don't give the default state a colour.
- Don't put the ambient wash on anything but the landing page.
- Don't give a surface token a brand hue — greys stay grey.
- Don't add a third elevation.
- Don't style a status inline instead of adding it to the tone map.
- Don't animate without covering `prefers-reduced-motion`.
