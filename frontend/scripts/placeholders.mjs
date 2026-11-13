import { mkdirSync, writeFileSync } from "node:fs";

const dir = new URL("../public/placeholders/", import.meta.url);
mkdirSync(dir, { recursive: true });
const items = [
  ["linen", "#f4f1ea", "#1c1915", "#1f4d3a"],
  ["night", "#141311", "#f3efe6", "#c4a574"],
  ["years", "#f7f4f2", "#241316", "#6e2430"],
  ["table", "#eef1ec", "#1c241c", "#3d5c45"],
  ["day", "#fbfbf8", "#1a1a1a", "#9f2d2d"],
  ["dark", "#161616", "#f5f2ea", "#e07a5f"],
  ["card", "#f6f1e7", "#221e18", "#1a1a1a"],
  ["evening", "#14181d", "#f4efe6", "#d6a15a"],
  ["agenda", "#f7f8f8", "#141618", "#1b6c80"],
  ["hall", "#121417", "#f2f4f5", "#d7b56d"],
];
for (const [name, background, ink, accent] of items) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 800" role="img"><rect width="1200" height="800" fill="${background}"/><path d="M600 150 920 650H280Z" fill="none" stroke="${accent}" stroke-width="8"/><path d="M460 650 600 400 740 650" fill="none" stroke="${ink}" stroke-width="6"/></svg>`;
  writeFileSync(new URL(`${name}.svg`, dir), svg);
}
