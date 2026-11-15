import { describe, expect, it } from "vitest";
import {
  applyLink,
  durationToSpeed,
  isSafeHref,
  normalizeMarqueeItems,
  plainFromRuns,
  removeLinksInRange,
  runsFromPlain,
  speedToDuration,
  syncPlainText,
} from "./richText";
import { createNode, ELEMENT_LIBRARY } from "./document";

describe("rich text links", () => {
  it("applies a link to a selected substring", () => {
    const runs = runsFromPlain("Discover our events and register today.");
    const start = "Discover our events and ".length;
    const end = start + "register today".length;
    const next = applyLink(runs, start, end, "https://example.com/register", "_blank");
    expect(plainFromRuns(next)).toBe("Discover our events and register today.");
    expect(next).toHaveLength(3);
    expect(next[1].text).toBe("register today");
    expect(next[1].href).toBe("https://example.com/register");
    expect(next[1].target).toBe("_blank");
  });

  it("supports multiple links in one block", () => {
    let runs = runsFromPlain("Visit site and call us");
    runs = applyLink(runs, 6, 10, "https://example.com");
    runs = applyLink(runs, 15, 22, "tel:+123456789");
    expect(runs.filter((run) => run.href)).toHaveLength(2);
  });

  it("removes a link without removing text", () => {
    const linked = applyLink(runsFromPlain("register today"), 0, 14, "https://example.com");
    const next = removeLinksInRange(linked, 0, 14);
    expect(plainFromRuns(next)).toBe("register today");
    expect(next[0].href).toBeUndefined();
  });

  it("rejects dangerous protocols", () => {
    expect(isSafeHref("javascript:alert(1)")).toBe(false);
    expect(isSafeHref("data:text/html,hi")).toBe(false);
    expect(isSafeHref("https://ok.example")).toBe(true);
    expect(isSafeHref("mailto:a@b.com")).toBe(true);
    expect(isSafeHref("#contact")).toBe(true);
    expect(isSafeHref("/about")).toBe(true);
  });

  it("preserves a whole-run link when plain text edits", () => {
    const linked = applyLink(runsFromPlain("Hello"), 0, 5, "https://example.com");
    const next = syncPlainText(linked, "Hello world");
    expect(next).toHaveLength(1);
    expect(next[0].href).toBe("https://example.com");
    expect(next[0].text).toBe("Hello world");
  });
});

describe("marquee model", () => {
  it("is available in the element library", () => {
    expect(ELEMENT_LIBRARY.some((item) => item.type === "MARQUEE")).toBe(true);
    const node = createNode("MARQUEE");
    expect(node.type).toBe("MARQUEE");
    expect(Array.isArray(node.props.items)).toBe(true);
  });

  it("normalizes marquee items and maps speed to duration", () => {
    const items = normalizeMarqueeItems([
      { id: "a1", kind: "text", text: "Hello", href: "https://example.com" },
      { id: "b2", kind: "image", mediaId: "11111111-1111-1111-1111-111111111111", alt: "Logo" },
      { id: "bad", kind: "text", text: "Nope", href: "javascript:alert(1)" },
    ]);
    expect(items).toHaveLength(3);
    expect(items[0].href).toBe("https://example.com");
    expect(items[2].href).toBeUndefined();
    expect(durationToSpeed(speedToDuration(50))).toBe(50);
  });
});
