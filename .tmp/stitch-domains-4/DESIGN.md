---
name: Luminous Prism
colors:
  surface: '#f4faff'
  surface-dim: '#c8ddea'
  surface-bright: '#f4faff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#e8f6ff'
  surface-container: '#dcf1ff'
  surface-container-high: '#d6ebf9'
  surface-container-highest: '#d0e6f3'
  on-surface: '#091e27'
  on-surface-variant: '#3e4949'
  inverse-surface: '#1f333d'
  inverse-on-surface: '#e2f3ff'
  outline: '#6e7979'
  outline-variant: '#bdc9c8'
  surface-tint: '#00696a'
  primary: '#006465'
  on-primary: '#ffffff'
  primary-container: '#087f80'
  on-primary-container: '#ddffff'
  inverse-primary: '#79d5d6'
  secondary: '#9b4513'
  on-secondary: '#ffffff'
  secondary-container: '#fe915a'
  on-secondary-container: '#722c00'
  tertiary: '#a82735'
  on-tertiary: '#ffffff'
  tertiary-container: '#ca404b'
  on-tertiary-container: '#fff6f5'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#95f2f2'
  primary-fixed-dim: '#79d5d6'
  on-primary-fixed: '#002020'
  on-primary-fixed-variant: '#004f50'
  secondary-fixed: '#ffdbcc'
  secondary-fixed-dim: '#ffb693'
  on-secondary-fixed: '#351000'
  on-secondary-fixed-variant: '#7a3000'
  tertiary-fixed: '#ffdad9'
  tertiary-fixed-dim: '#ffb3b3'
  on-tertiary-fixed: '#400009'
  on-tertiary-fixed-variant: '#8e1124'
  background: '#f4faff'
  on-background: '#091e27'
  surface-variant: '#d0e6f3'
typography:
  display-hero:
    fontFamily: Plus Jakarta Sans
    fontSize: 56px
    fontWeight: '700'
    lineHeight: 64px
    letterSpacing: -0.03em
  display-hero-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.025em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.005em
  label-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  code-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1.25rem
  gutter-sm: 0.75rem
  margin: 2rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

This design system expresses a high-precision, luminous SaaS identity tailored for next-generation analytics, productivity, and cloud orchestration tools. It balances Linear's data-dense efficiency, Stripe's soft ambient radiance, and Vercel's typographic discipline. 

The aesthetic is characterized by:
- **Prismatic Luminosity:** Geometric facets, translucent glass layers, and vivid spectral gradients that shift smoothly across deep Teal, warm Amber, and vibrant Coral.
- **Atmospheric Clarity:** Crisp, ultra-clean surfaces on cool off-white grounds with razor-sharp micro-borders and soft ambient diffusion.
- **Disciplined Precision:** High-density structural layouts where utility, tabular data, and micro-interactions remain frictionless and visually uncluttered.

## Colors

The palette draws inspiration directly from refracted light through a geometric crystalline prism:

- **Primary Spectrum (Teal):** `#087F80` anchored by energetic `#25B5AE`. Governs core CTAs, structural brand focus, and affirmative system states.
- **Secondary Spectrum (Amber/Gold):** `#FF925B` transitioning to `#FFB94F`. Represents dynamic velocity, secondary highlights, warnings, and warm interactive focal points.
- **Tertiary Spectrum (Coral/Crimson):** `#E95760` tapering into `#F36F68`. Utilized for high-impact conversions, critical actions, and key visual accents.
- **Neutral Core:** `#172B35` (Deep Abyss Slate) functions as the definitive dark base for high-contrast primary typography and dark containers.
- **Secondary & Muted Neutrals:** `#64748B` provides balanced, accessible hierarchy for secondary labels and metadata.
- **Surface & Canvas:** `#F7FAFA` sets an airy, lightly-tinted cool background; cards and elevated panes utilize pure white (`#FFFFFF`) or frosted translucent fills (`rgba(255, 255, 255, 0.75)`).
- **Hairline Dividers:** `#E5ECEC` provides ultra-crisp, non-intrusive container definition.
- **Signature Gradient:** Linear and conical gradients blending `Teal (#25B5AE) -> Amber (#FFB94F) -> Coral (#E95760)` at 135-degree inclinations serve as accent trims, active badges, and hero CTAs.

## Typography

The typographic hierarchy pairs two distinct sans-serif voices:
- **Headings & Key Navigation (Plus Jakarta Sans):** Brings contemporary warmth, open apertures, and geometric rhythm to major sections and titles, evoking technical craft without sterility.
- **Interface, Numbers & Tabular Body (Inter):** Deploys neutral clarity for high-information density, dashboards, tables, tooltips, and forms. Numeric characters should leverage tabular lining (`font-feature-settings: 'tnum' 1`) for clean vertical scanning across dense analytics.

## Layout & Spacing

This system utilizes an 8pt base grid with a 4pt sub-grid dedicated to compact components and toolbar clusters.

- **Desktop (>= 1280px):** 12-column responsive fluid grid pinned to an optional maximum width of `1440px`. Column gutters remain at `1.25rem` (20px), with outer section canvas margins resting at `2rem` (32px).
- **Tablet (768px - 1279px):** 8-column layout with 16px gutters and 24px margins. Sidebars collapse to icon-only rails or slide-over sheets.
- **Mobile (< 768px):** 4-column layout with 12px gutters and 16px lateral padding. Dense table views stack gracefully into faceted item cards.
- **Information Density:** Component containers enforce compact horizontal padding and vertical micro-rhythms (e.g., table cell padding of `0.5rem 0.75rem`) to maximize visible data above the fold while maintaining balanced breathing room.

## Elevation & Depth

Visual depth is achieved through translucent optical layering, subtle refractive borders, and multi-tier diffuse shadows:

- **Level 0 (Flat / Canvas):** Surface color `#F7FAFA` without elevation.
- **Level 1 (Subtle Cards & Cells):** Pure `#FFFFFF` surface with a 1px border of `#E5ECEC` and micro-shadow: `0 1px 2px rgba(23, 43, 53, 0.04)`.
- **Level 2 (Hovered & Active Panes):** Translucent backdrop `#FFFFFF` (or `rgba(255, 255, 255, 0.88)` with `backdrop-filter: blur(12px)`); shadow: `0 4px 16px -2px rgba(23, 43, 53, 0.06), 0 2px 6px -1px rgba(37, 181, 174, 0.05)`.
- **Level 3 (Dropdowns & Popovers):** Elevated glass layer (`rgba(255, 255, 255, 0.94)`, `blur(16px)`) with outer glow: `0 12px 32px -4px rgba(23, 43, 53, 0.1), 0 1px 2px rgba(23, 43, 53, 0.04)`.
- **Level 4 (Modals & Overlays):** Deep ambient diffusion: `0 24px 64px -12px rgba(23, 43, 53, 0.18)`, bounded by a dual-tone inner stroke (`inset 0 1px 0 rgba(255, 255, 255, 0.8)`).
- **Prism Edge Effect:** Accent cards and active indicators feature an ultra-fine 1px border gradient transitioning from `#25B5AE` through `#FFB94F` to `#E95760` at 30% opacity.

## Shapes

The interface balances soft modern curves with geometric precision. A standard base radius of `0.5rem` (8px) is deployed for inputs, segmented controls, and badges. Medium containers (cards, data tables, dropdown menus) use `rounded-lg` (16px), while modal dialogs and major platform cards embrace `rounded-xl` (24px). Fully round pills (`rounded-full`) are reserved exclusively for status chips, micro-avatars, and toggle switches.

## Components

- **Buttons:**
  - *Primary Prism CTA:* Vibrant multi-hue gradient background (`linear-gradient(135deg, #087F80 0%, #25B5AE 45%, #FF925B 100%)`) with white text, crisp 0.5px white inner stroke, and a teal-tinted hover glow.
  - *Secondary:* Clean white ground, border `#E5ECEC`, text `#172B35`, subtly shifting to `#087F80` on hover.
  - *Ghost / Tertiary:* Transparent background, `#64748B` text, transitioning to `#F0F5F5` background on hover.
- **Inputs & Form Controls:**
  - Background `#FFFFFF`, border `1px solid #E5ECEC`, text `#172B35`, placeholder `#64748B`.
  - Focused state: Border transitions to `#25B5AE` accompanied by a 3px soft focus ring (`rgba(37, 181, 174, 0.18)`).
- **Chips & Badges:**
  - Pill-shaped (`rounded-full`) with semi-transparent tinted backgrounds: Teal (`rgba(37, 181, 174, 0.12)` with `#087F80` text), Amber (`rgba(255, 146, 91, 0.14)` with `#C45A17` text), and Coral (`rgba(233, 87, 96, 0.12)` with `#B82E37` text).
- **Cards & Data Tables:**
  - High-density borders `#E5ECEC` with subtle corner radiuses (`rounded-lg`). Tables feature sticky headers with frosted glass backgrounds (`rgba(247, 250, 250, 0.85)` + `blur(8px)`), zebra-striped hover rows, and monospaced numeric values.
- **Checkboxes & Radios:**
  - Inactive: `#E5ECEC` outline on white. Active: `#087F80` fill with a sharp white glyph, subtly transitioning to coral accents when engaged in batch selection states.
- **Glassmorphic Command Bar / Rail:**
  - Fixed floating controls utilize `rgba(255, 255, 255, 0.8)` with backdrop blur `20px`, crisp `#E5ECEC` borders, and high-contrast `#172B35` navigational iconography.