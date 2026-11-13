import { describe, expect, it } from "vitest";
import type { EditorDocument } from "../visual-editor/document";
import { stylesFor } from "../visual-editor/document";
import { applyPreset, fontById, presentedStyles, resolvedTokens, stack, stylesheet, toFixedSize, toFluid } from "./fonts";

describe("font registry", () => {
  it("resolves a stable id to its family and stack", () => {
    const font = fontById("playfair-display");
    expect(font?.family).toBe("Playfair Display");
    expect(font?.styles).toContain("italic");
    expect(font?.weights).toContain(600);
    expect(font?.weights).not.toContain(100);
    expect(stack("Playfair Display")).toContain("Playfair Display");
    expect(stack("Playfair Display")).toContain("Georgia");
    expect(fontById("manrope")?.styles).not.toContain("italic");
    expect(fontById("fraunces")?.axes.map((axis) => axis.tag)).toEqual(["wght", "opsz"]);
    expect(fontById("bebas-neue")?.axes).toEqual([]);
  });

  it("requests only the families and weights a page uses", () => {
    const url = stylesheet([
      { id: "playfair-display", weights: [600], italic: true, variable: false },
      { id: "manrope", weights: [400], italic: true, variable: false },
    ]);
    expect(url?.startsWith("https://fonts.googleapis.com/css2?")).toBe(true);
    expect(url).toContain("family=Playfair+Display");
    expect(url).toContain("family=Manrope");
    expect(url).toContain("display=swap");
    expect(url).not.toContain("Inter");
    expect(url).not.toContain("ital,wght@0,400;1,400");
    expect(stylesheet([{ id: "https://evil.example/font.css", weights: [400], italic: false, variable: false }])).toBeNull();
  });

  it("uses a variable range only when the font provides that axis", () => {
    const url = stylesheet([{ id: "inter", weights: [450], italic: false, variable: true }]);
    expect(url).toContain("wght@100..900");
    expect(stylesheet([{ id: "bebas-neue", weights: [400], italic: false, variable: true }])).toContain("wght@400");
  });

  it("keeps a custom font and a mobile size when a pairing is applied", () => {
    const document = page();
    applyPreset(document, "luxury-editorial");
    const title = document.root.children[0];
    const custom = document.root.children[1];
    const copy = document.root.children[2];
    expect(title.styles.fontFamily).toBe("Playfair Display");
    expect(title.props.text).toBe("Hello");
    expect(stylesFor(title, "mobile").fontSize).toBe("30px");
    expect(stylesFor(title, "desktop").fontSize).not.toBe("30px");
    expect(custom.styles.fontFamily).toBe("Inter");
    expect(custom.props.text).toBe("Stay");
    expect(copy.styles.fontFamily).toBe("Manrope");
    expect(copy.props.text).toBe("Body copy");
    expect(document.typography?.roles.body.lineHeight).toBe("1.7");
    expect(document.typography?.roles.display.fontId).toBe("playfair-display");
  });

  it("lets an element font override the page token", () => {
    const document = page();
    document.typography = resolvedTokens(document);
    const title = document.root.children[0];
    expect(presentedStyles(title, "desktop", document.typography).fontFamily).toBe("Fraunces");
    title.styles.fontFamily = "";
    expect(presentedStyles(title, "desktop", document.typography).fontFamily).toBe("Fraunces");
    title.styles.fontFamily = "Inter";
    expect(presentedStyles(title, "desktop", document.typography).fontFamily).toBe("Inter");
  });

  it("turns a fixed size into a clamp and back", () => {
    expect(toFluid("32px")).toMatch(/^clamp\(/);
    expect(toFixedSize(toFluid("32px"))).toBe("32px");
  });
});

function page(): EditorDocument {
  return {
    schemaVersion: 1,
    documentId: "11111111-1111-1111-1111-111111111111",
    page: { title: "A", description: "" },
    breakpoints: { desktop: 1024, tablet: 768 },
    designTokens: { colors: {}, fonts: {}, spacing: {} },
    root: {
      id: "page",
      type: "SECTION",
      name: "Page",
      props: {},
      styles: { fontFamily: "Fraunces" },
      responsiveStyles: { tablet: {}, mobile: {} },
      children: [
        { id: "title", type: "HEADING", name: "Heading", props: { text: "Hello", level: 1 }, styles: { fontFamily: "Fraunces", fontSize: "64px" }, responsiveStyles: { tablet: {}, mobile: { fontSize: "30px" } }, children: [] },
        { id: "custom", type: "HEADING", name: "Heading", props: { text: "Stay", level: 2 }, styles: { fontFamily: "Inter", fontSize: "40px" }, responsiveStyles: { tablet: {}, mobile: {} }, children: [] },
        { id: "copy", type: "PARAGRAPH", name: "Paragraph", props: { text: "Body copy" }, styles: { fontFamily: "Newsreader", fontSize: "18px" }, responsiveStyles: { tablet: {}, mobile: {} }, children: [] },
      ],
    },
  };
}
