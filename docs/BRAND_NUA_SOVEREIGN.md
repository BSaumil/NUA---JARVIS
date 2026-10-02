# NUA Sovereign — Brand System

This is the single source of truth for how NUA presents itself: name hierarchy,
personality, palette, wordmark, app icon, Orb state colour rules, tagline usage,
light/dark treatment, accessibility requirements, and the asset inventory. It
documents the P0 brand work from the 2026-10-02 Personal Test Deployment Master
Directive. If code and this document disagree, the code is wrong — file it as a
defect, don't quietly reinterpret the brand.

## 1. Name hierarchy

- **Customer-facing name: NUA.** This is the only name a user should ever see in
  the UI, store listing, notifications, or marketing copy. Not "NUA POS", not
  "JARVIS".
- **"JARVIS"** is a legitimate internal codename: it appears in commit history,
  internal docs, and — critically — as the literal wake-word the user speaks to
  trigger voice activation (`WakePhrase.kt`, `NuaForegroundService.kt`). That
  usage is correct and must not be removed or renamed; it is not a customer-facing
  brand surface, it's a spoken trigger phrase the user chooses to say.
- **Brand system name: NUA Sovereign.** Used when referring to the design system
  itself (this document, asset filenames, internal design reviews) — not
  typically shown to end users verbatim.
- The Android application ID / package name is **not** being renamed as part of
  this work. Renaming it has real migration/signing/Play-Console-identity
  consequences that the directive explicitly says to investigate before acting
  on, and no such investigation has produced a case for it. Treat the package
  name as a build/distribution identifier, independent of the customer-facing
  brand name.

## 2. Personality

NUA Sovereign is: **premium, calm, powerful, trustworthy, privacy-conscious,
personal, sophisticated, future-facing.**

It is explicitly **not**: gaming-tech, a generic chatbot skin, a
robot-circuit-board aesthetic, or an Iron-Man-style HUD pastiche. No neon,
no circuit-trace textures, no holographic HUD chrome, no robot imagery.
The visual language is restrained and architectural — closer to a premium
watch or a private bank's app than to a sci-fi assistant.

## 3. Tagline

- **Primary tagline: "Your Intelligence. Your Control."** This is the brand's
  one sentence. It states ownership (yours) before it states capability
  (intelligence) — control is not a footnote.
- **Secondary line: "Intelligence That Acts."** Selective use only —
  onboarding, the About screen, splash/launch moments. It must never appear
  repeated throughout the working UI (chat, settings rows, notifications).
  Repetition would cheapen it into a slogan; it is meant to land once, when
  the user is first orienting themselves, not on every screen.

Both lines are implemented today in `AboutCard()` in
`app/src/main/java/com/nua/assistant/ui/SettingsScreen.kt`, which is the
correct kind of surface for the secondary line (a deliberate, once-per-visit
About screen, not a banner).

## 4. Colour tokens

Six tokens, final:

| Token | Hex | Role |
|---|---|---|
| **Burgundy** | `#6B1738` | Primary brand identity colour. Filled surfaces, primary actions, brand accents. |
| **Deep Wine** | `#3C0B21` | Deep premium surfaces and gradients; the "more saturated" end of brand gradients. |
| **Obsidian** | `#111113` | Main dark background. NUA is dark-first; this is the app's base canvas. |
| **Graphite** | `#25252A` | Cards and secondary surfaces sitting above Obsidian. |
| **Warm Ivory** | `#F6F0E5` | Primary light surface / primary light-on-dark text/foreground colour. |
| **Intelligence Violet** | `#8B5CF6` | Reserved for the AI-active state only: listening, thinking, reasoning, processing, acting. Never used as a plain decorative accent. |

Source of truth in code: `app/src/main/java/com/nua/assistant/ui/theme/NuaPalette.kt`
(raw constants + the WCAG contrast-ratio math that drives the rules below) and
`app/src/main/java/com/nua/assistant/ui/theme/NuaColors.kt` (the named
`androidx.compose.ui.graphics.Color` values consumed by Compose:
`NuaBurgundy`, `NuaDeepWine`, `NuaBackground`/Obsidian, `NuaSurface`/Graphite,
`NuaSurfaceElevated`, `NuaTextPrimary`/Warm Ivory, `NeuralViolet`).

### 4.1 A critical, empirically-verified rule: Burgundy and Deep Wine are not text colours

Before wiring these tokens into `NuaTheme.kt`'s Material3 colour scheme, we
computed actual WCAG relative-luminance/contrast ratios (the same formulas
now asserted in `NuaPaletteTest.kt`) rather than assuming Burgundy would
behave like the old brand Orange. The result reverses the old assumption:

- Burgundy and Deep Wine are **dark** colours. Their contrast ratio against
  every other dark surface (Obsidian, Graphite, Graphite-elevated) is below
  1.7 — they fail even AA-large's 3.0 minimum as a *foreground* colour on a
  dark background.
- They are **filled-surface colours only**: paint them as the background of a
  button, chip, or card, then put **Warm Ivory** text on top of them, not the
  other way around.
- This is the opposite of the retired Orange identity, where the brand colour
  itself was bright enough to serve as foreground text on dark surfaces.

This is why `NuaTheme.kt`'s `NuaDarkColorScheme` sets `onPrimary =
NuaTextPrimary` and `onTertiary = NuaTextPrimary` (both resolve to Warm
Ivory) rather than a dark `onColor` — using a dark `onPrimary` against a
Burgundy `primary` fill would have been functionally illegible. Any future
component that paints a Burgundy or Deep Wine surface must follow the same
rule: light text/iconography on top, never dark.

Violet keeps the old behaviour: dark text (`NuaBackground`/Obsidian) reads
fine on top of it, which is why `onSecondary = NuaBackground` is unchanged
from before the refresh.

### 4.2 What must never happen to these tokens

- Never substitute Burgundy for the semantic **warning**, **error**, or
  **success** colours (`NuaWarning`, `NuaCritical`, `NuaSuccess` in
  `NuaColors.kt`). Brand identity and system status are different channels;
  collapsing them removes a user's ability to tell "this is on-brand" from
  "this needs your attention."
- Never let an animation using these tokens override **Reduce Motion**. Every
  Orb animation path must still check the system Reduce Motion setting before
  running colour/scale/rotation transitions — this was true before the brand
  refresh and remains true now; the refresh only changed which colours are
  animated between, not whether the motion-reduction gate exists.
- Never drop contrast below WCAG AA for body text. Warm Ivory on Obsidian/
  Graphite and Obsidian on Warm Ivory are both high-contrast by construction;
  don't introduce a new surface pairing without running it through the same
  `relativeLuminance`/`contrastRatio` check used in `NuaPaletteTest.kt`.

## 5. Wordmark

Master source: `docs/brand/nua-wordmark.svg` — a geometric, minimal uppercase
"NUA" built from rectilinear letterforms plus one diagonal stroke each for
the N and the A. The **A's counter (the small triangular gap inside the
letter) doubles as the brand's intelligence node** — the same visual idea
used in the app icon, expressed inside typography instead of as a separate
mark.

The file uses `fill="currentColor"` on every path and sets no color itself,
so one master file drives every lockup by varying the SVG root's `color`
attribute:

| File | `color` | Use |
|---|---|---|
| `nua-wordmark-dark.svg` | `#F6F0E5` (Warm Ivory) | On Obsidian/Graphite or any dark surface. |
| `nua-wordmark-light.svg` | `#111113` (Obsidian) | On white/Warm-Ivory or any light surface. |
| `nua-wordmark-mono.svg` | `#000000` (flat black) | Single-colour contexts: print, engraving, low-colour displays. Invert fill for a dark single-colour surface. |

Each lockup is also exported as PNG at 1x/2x/4x (`@1x`/`@2x`/`@4x` suffixes)
for contexts that can't render SVG directly (e.g. quick previews, non-vector
pipelines). The PNGs are generated artifacts — regenerate them from the SVGs
if the letterforms ever change; don't hand-edit a PNG.

No robot head, no brain icon, no microphone glyph, no circuit-board texture,
no lightning bolt, no chat bubble — in the wordmark or anywhere else in the
identity system.

## 6. App icon — "Sovereign N"

Concept: **two interlocking strokes meeting at a small intelligence node.**

- **First stroke (Burgundy, left vertical):** Human — intent, identity,
  ownership. This is the fixed, stable element the design starts from.
- **Second stroke (Violet, diagonal bending into the right vertical):**
  Intelligence — reasoning flowing into execution. It visually originates
  from where the first stroke ends and carries the eye through to completion.
- **Intelligence node (Warm Ivory circle):** sits exactly at the diagonal
  stroke's midpoint, which by construction is also the icon canvas's exact
  centre, `(54, 54)` on the 108×108dp adaptive-icon grid.

### 6.1 Safe-zone verification

Android adaptive icons mask the 108dp foreground canvas down to an
effectively circular/squircle safe area of roughly 33dp radius from centre
before launcher icon shapes clip content. The icon's outer geometry was
checked against this directly: an initial draft placed corners at a distance
of ~41–42dp from centre — outside the safe zone, so a circular mask would
have clipped it — and was redesigned with a half-extent of 21dp, giving
corners at `21 × √2 ≈ 29.7dp` from centre, inside the ~33dp safe radius.

### 6.2 Asset inventory (Android)

- `app/src/main/res/drawable/ic_launcher_background.xml` — solid Obsidian
  (`#111113`) fill. Replaces the previous placeholder `#1A1B22` rectangle.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — the Sovereign N:
  Burgundy left-vertical path, Violet diagonal+right-vertical path, Warm
  Ivory node circle at `(54,54)`. Replaces a placeholder teal "N" glyph.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — Android 13+
  themed-icon variant: the same three shapes collapsed into one flat black
  path, for launchers that recolour icons to match the device's wallpaper
  theme.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and
  `ic_launcher_round.xml` — both reference `background` + `foreground` +
  `monochrome` drawables above.

No separate legacy raster fallback was added: the project's minimum SDK
already relies on the adaptive-icon XML path, so a raster PNG fallback isn't
required for current targets. If a future minSdk change ever drops below
API 26, revisit this.

## 7. Orb state → colour mapping

Source of truth: `appearanceFor()` in
`app/src/main/java/com/nua/assistant/ui/orb/OrbState.kt` (pure data, JVM
testable, no Compose dependency) feeding `paletteColors()` in
`app/src/main/java/com/nua/assistant/ui/orb/NuaOrb.kt` (the actual Compose
gradient/brush rendering).

| Orb state | Palette | Colours | Rationale |
|---|---|---|---|
| Idle | `BRAND` | Burgundy → Violet | Restrained Burgundy-led core; the brand identity at rest. |
| Listening | `EXCEPTIONAL` | Burgundy → Violet → Deep Wine | Responsive expansion; the extra Deep Wine stop reads as "opening up." |
| Thinking | `INTELLIGENCE` (new) | Violet → Violet(55% alpha) | Violet-dominant, not Burgundy-led — this is the one state the directive calls out explicitly as "Violet breathing/rotation," distinct from the general brand gradient. |
| Reasoning | `BRAND` | Burgundy → Violet | Controlled internal motion, same palette as Idle; the state's own animation (not its colour) carries the "reasoning" signal. |
| Acting | `BRAND` | Burgundy → Violet | Directional pulse layered on top via animation, not a colour change. |
| Success | `SUCCESS` | Semantic success colour | Never replaced with brand colours — status must stay legible as status. |
| Warning | `WARNING` | Semantic warning colour | Same reasoning as Success. |
| Error | `CRITICAL` | Semantic critical colour | Same reasoning as Success. |
| Offline | `MUTED` | Graphite-elevated (two alphas) | Repointed from a desaturated brand gradient to the literal Graphite surface token — "muted Graphite treatment" taken literally, not just "dimmer brand colour." |

The `INTELLIGENCE` palette value is new in this pass (`OrbPalette.INTELLIGENCE`
in `OrbState.kt`); it exists specifically so Thinking can be Violet-dominant
without changing what BRAND/EXCEPTIONAL mean for every other state.

**Rule:** the Orb must never animate a state that contradicts the real
underlying app state. If the app isn't actually listening, the Orb must not
show the Listening palette — these colours are a truthful status indicator,
not decoration.

## 8. Light/dark treatment

NUA Sovereign is **dark-first**: Obsidian is the default background
everywhere (`NuaBackground`, `nua_background` in `colors.xml`, the
pre-Compose launch `Theme.Nua` in `themes.xml`). There is currently no
separate light theme shipped in the app itself — the light wordmark lockup
and Obsidian-on-Warm-Ivory contrast pairing exist for contexts outside the
app surface itself (store listing assets, marketing/docs, print) where a
light background is unavoidable.

If a true in-app light theme is ever built, it must reuse these same six
tokens with the same contrast rules in section 4.1 — not introduce new ones.

## 9. Accessibility requirements

- All body text pairings (Warm Ivory on Obsidian/Graphite, Obsidian on Warm
  Ivory) must clear WCAG AA; this is asserted in
  `app/src/test/java/com/nua/assistant/ui/theme/NuaPaletteTest.kt`.
- Burgundy/Deep Wine are fill-only; see section 4.1. Any new UI that puts
  text on a Burgundy/Deep Wine fill must use Warm Ivory text, verified against
  the same contrast helper functions.
- Violet continues to clear AA for large text and UI components on every
  surface it's used on — unchanged from the pre-refresh identity.
- Orb colour-state transitions must respect system Reduce Motion.
- None of this brand work touches screen-reader labels, touch target sizes,
  or focus order — those are separate, unaffected accessibility surfaces.

## 10. Asset inventory

| Path | Description |
|---|---|
| `docs/brand/nua-wordmark.svg` | Master wordmark, `currentColor`-parameterised. |
| `docs/brand/nua-wordmark-dark.svg` | Warm Ivory lockup, for dark surfaces. |
| `docs/brand/nua-wordmark-light.svg` | Obsidian lockup, for light surfaces. |
| `docs/brand/nua-wordmark-mono.svg` | Flat black monochrome fallback. |
| `docs/brand/nua-wordmark*@{1x,2x,4x}.png` | PNG exports of all four SVGs above, generated via `cairosvg`. |
| `app/src/main/res/drawable/ic_launcher_background.xml` | App icon background layer (Obsidian). |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | App icon foreground (Sovereign N). |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | App icon Android 13+ themed/monochrome layer. |
| `app/src/main/java/com/nua/assistant/ui/theme/NuaPalette.kt` | Raw hex constants + WCAG contrast math. |
| `app/src/main/java/com/nua/assistant/ui/theme/NuaColors.kt` | Compose `Color` values and the `NuaColors` data class. |
| `app/src/main/java/com/nua/assistant/ui/theme/NuaTheme.kt` | Material3 colour scheme wiring (`onPrimary`/`onTertiary` light-text fix). |
| `app/src/main/java/com/nua/assistant/ui/theme/NuaGradients.kt` | Brand gradient helpers (`sweep()`, `exceptional()`, `glassTint()`). |
| `app/src/main/java/com/nua/assistant/ui/orb/OrbState.kt` | Orb state → palette mapping (pure data). |
| `app/src/main/java/com/nua/assistant/ui/orb/NuaOrb.kt` | Orb palette → actual gradient/brush rendering. |
| `app/src/main/java/com/nua/assistant/ui/SettingsScreen.kt` (`AboutCard()`) | Tagline + secondary line surface. |

## 11. "Do not" examples

- Do not show "JARVIS" anywhere in customer-facing UI, notifications, or the
  store listing. It is a spoken wake-word and an internal codename, not a
  product name.
- Do not repeat "Intelligence That Acts." throughout the working UI. It
  belongs on onboarding/About/splash, once.
- Do not put dark text on a Burgundy or Deep Wine fill. Both are dark
  surface colours; only Warm Ivory text is legible on them.
- Do not use Intelligence Violet as a generic decorative accent. It is
  reserved for AI-active states (listening/thinking/reasoning/processing/
  acting) specifically so its appearance is informative, not just pretty.
- Do not substitute Burgundy for semantic warning/error/success colours.
- Do not design new icon/wordmark elements that include a robot head, a
  brain, a microphone, a circuit-board texture, a lightning bolt, or a chat
  bubble. None of those match the "premium / calm / powerful / trustworthy /
  privacy-conscious / personal / sophisticated / future-facing" personality,
  and several (robot head, HUD chrome) actively contradict it.
- Do not let an Orb state's colour contradict the real app state — the Orb's
  colour is a status signal, not mood lighting.
- Do not rename the Android application ID as part of brand work without a
  separate, explicit investigation into the migration/signing/Play-Console
  consequences.
