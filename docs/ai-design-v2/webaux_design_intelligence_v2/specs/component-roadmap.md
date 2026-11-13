# WebAux component roadmap

## Keep the primitive layer
SECTION, CONTAINER, FLEX, GRID, GROUP, HEADING, PARAGRAPH, BUTTON, IMAGE, SPACER, DIVIDER.

## Add a typed design-component layer
Prioritise these families:

### Hero
- HERO_FULL_BLEED
- HERO_SPLIT
- HERO_EDITORIAL
- HERO_OVERLAP
- HERO_COLLAGE
- HERO_TYPOGRAPHIC

### Story and event
- STORY_TIMELINE
- STORY_CHAPTER
- QUOTE_STATEMENT
- EVENT_DETAILS
- EVENT_SCHEDULE
- PEOPLE_PROFILES
- VENUE_DETAILS

### Media
- IMAGE_GALLERY
- IMAGE_MASONRY
- IMAGE_COLLAGE
- CAROUSEL

### Conversion and utility
- COUNTDOWN
- RSVP
- LOCATION_LINK
- FAQ_ACCORDION
- CONTACT_DETAILS

### Decorative and structure
- ORNAMENT_DIVIDER
- DECORATIVE_SHAPE
- PATTERN_BACKGROUND
- FLOATING_NAVIGATION
- FOOTER_VARIANT

## Implementation rule
A type is not complete until it is represented consistently in:
1. Document schema/types
2. Backend allowlist and validation
3. Normalizer/sanitizer
4. Editor insertion registry and inspector
5. Editor selection/layers/history behaviour
6. Desktop/tablet/mobile renderer
7. Public and no-JavaScript rendering
8. Accessibility behaviour
9. Tests and documentation

Prefer components composed from primitives where practical. Do not add a component that only renders in the editor but not on the public site. Reuse the existing RSVP API; never create a parallel RSVP backend.
