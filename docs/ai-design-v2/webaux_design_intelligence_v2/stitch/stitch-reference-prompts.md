# Google Stitch reference-generation prompts

Use these prompts through the configured Stitch MCP tools only after discovering the actual available tool names and schemas. Do not assume tool names or fabricate project/screen IDs.

## Prompt A — Wedding archetype exploration
Create three genuinely different high-fidelity responsive wedding website concepts:
1. Luxury editorial: expressive serif typography, asymmetrical editorial composition, immersive photography, restrained warm palette.
2. Botanical romance: organic botanical motifs, delicate typography, soft natural palette, layered imagery.
3. Royal heritage: culturally adaptable ornamental framing, rich colour, formal typography, carefully controlled decorative detail.

For each concept create a full landing page, not just a hero. Include hero, story/about, event details, schedule, gallery, RSVP, location, and footer. Use realistic placeholder copy clearly marked as sample; do not invent real names or dates. Show desktop and mobile. Make the three compositions structurally distinct.

## Prompt B — Birthday archetype exploration
Create three distinct responsive birthday invitation websites:
1. Playful colourful editorial collage.
2. Warm storybook illustration-inspired layout.
3. Bold contemporary typographic celebration.

Include hero, event details, schedule/activities where appropriate, gallery or visual feature, RSVP, location, footer. Avoid using the same section layout across concepts.

## Prompt C — Corporate / conference exploration
Create three distinct professional event websites:
1. Editorial conference with bold type and strong schedule hierarchy.
2. Modern technology summit with modular data-rich layout.
3. Premium business gathering with restrained luxury and photography.

Include event identity, speakers, agenda, venue, registration CTA, sponsors if provided, FAQ and footer. Do not invent factual speaker names, sponsors, or event details.

## Prompt D — Component extraction
From the approved screen, identify reusable visual compositions and specify:
- Component name and purpose
- Required and optional content fields
- Layout variants
- Desktop/tablet/mobile behaviour
- Typography and spacing roles
- Image aspect ratio/crop/focal behaviour
- Colour and decoration tokens
- Interactions and accessibility requirements
- How it can be represented using WebAux's existing document tree and proposed component registry

Do not copy source code blindly. Produce a component specification that can be implemented in the existing WebAux renderer.

## Prompt E — Design system extraction
Extract semantic design tokens from the approved reference:
- Colour roles and contrast notes
- Display, heading, body, and accent typography roles
- Type scale, weights, line heights, letter spacing
- Spacing scale and section rhythm
- Content widths and grid rules
- Border, radius, shadow, and image treatment
- Motion principles and reduced-motion fallback
- Responsive breakpoint behaviour

Return structured, implementation-oriented values and explain which values are archetype-specific versus reusable.
