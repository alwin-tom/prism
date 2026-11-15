import { describe, expect, it } from "vitest";
import { addChild, createNode, duplicateNode, getEffectiveProperty, groupNodes, hasOverride, insertBlock, moveChild, placeAcross, propsFor, removeNode, removeResponsiveOverride, restack, setDeviceVisibility, setResponsiveProperty, shiftChild, stylesFor, ungroupNode, updateNode, type EditorDocument } from "./document";

function page(): EditorDocument {
  const root = createNode("SECTION");
  root.id = "page";
  return {
    schemaVersion: 1,
    documentId: "11111111-1111-1111-1111-111111111111",
    page: { title: "Anna", description: "" },
    breakpoints: { desktop: 1200, tablet: 768 },
    designTokens: { colors: {}, fonts: {}, spacing: {} },
    root,
  };
}

describe("visual document", () => {
  it("nests a heading under the page and keeps the page when it is removed", () => {
    const document = page();
    const heading = addChild(document, "page", "HEADING");
    expect(document.root.children[0].type).toBe("HEADING");
    removeNode(document, "page");
    expect(document.root.children).toHaveLength(1);
    removeNode(document, heading.id);
    expect(document.root.children).toHaveLength(0);
  });

  it("keeps a mobile move off the desktop and tablet layouts", () => {
    const document = page();
    const heading = addChild(document, "page", "HEADING");
    updateNode(document, heading.id, { styles: { left: "40px" } }, "tablet");
    placeAcross(document, heading.id, { position: "relative", left: "8%", top: "12px" }, "mobile");
    expect(stylesFor(heading, "desktop").left).toBeUndefined();
    expect(stylesFor(heading, "tablet").left).toBe("40px");
    expect(stylesFor(heading, "mobile").left).toBe("8%");
    expect(stylesFor(heading, "mobile").top).toBe("12px");
    expect(stylesFor(heading, "desktop").top).toBeUndefined();
    expect(stylesFor(heading, "tablet").top).toBeUndefined();
  });

  it("stores a tablet size without changing the desktop size", () => {
    const document = page();
    const heading = addChild(document, "page", "HEADING");
    updateNode(document, heading.id, { styles: { fontSize: "28px" } }, "tablet");
    expect(stylesFor(heading, "desktop").fontSize).toBe("36px");
    expect(stylesFor(heading, "tablet").fontSize).toBe("28px");
  });

  it("duplicates a heading and groups two siblings", () => {
    const document = page();
    const heading = addChild(document, "page", "HEADING");
    const text = addChild(document, "page", "PARAGRAPH");
    const copy = duplicateNode(document, heading.id);
    expect(document.root.children).toHaveLength(3);
    expect(copy?.id).not.toBe(heading.id);
    const group = groupNodes(document, [heading.id, text.id]);
    expect(group?.type).toBe("GROUP");
    expect(group?.children.map((child) => child.id)).toEqual([heading.id, text.id]);
    ungroupNode(document, group!.id);
    expect(document.root.children.some((child) => child.id === heading.id)).toBe(true);
  });

  it("raises a selected element without reordering its section", () => {
    const document = page();
    const heading = addChild(document, "page", "HEADING");
    const text = addChild(document, "page", "PARAGRAPH");
    restack(document, heading.id, "front");
    expect(document.root.children.map((child) => child.id)).toEqual([heading.id, text.id]);
    expect(stylesFor(heading, "desktop").zIndex).toBe("1");
    expect(stylesFor(text, "desktop").zIndex).toBe("0");
    expect(stylesFor(heading, "desktop").position).toBe("relative");
    restack(document, heading.id, "back");
    expect(stylesFor(heading, "desktop").zIndex).toBe("0");
    expect(stylesFor(text, "desktop").zIndex).toBe("1");
  });

  it("keeps sibling z-index values bounded when bringing forward repeatedly", () => {
    const document = page();
    const a = addChild(document, "page", "HEADING");
    const b = addChild(document, "page", "PARAGRAPH");
    const c = addChild(document, "page", "BUTTON");
    for (let step = 0; step < 12; step += 1) restack(document, a.id, "forward");
    expect(Number(stylesFor(a, "desktop").zIndex)).toBeLessThanOrEqual(2);
    expect(stylesFor(a, "desktop").zIndex).toBe("2");
    expect(stylesFor(b, "desktop").zIndex).toBe("0");
    expect(stylesFor(c, "desktop").zIndex).toBe("1");
  });

  it("stores a background image and overlay without a css url", () => {
    const document = page();
    const section = document.root;
    updateNode(document, section.id, {
      styles: {
        backgroundMediaId: "22222222-2222-2222-2222-222222222222",
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
        backgroundAttachment: "scroll",
        backgroundOverlayType: "gradient",
        backgroundOverlayColor: "#000000",
        backgroundOverlayEnd: "#111111",
        backgroundOverlayOpacity: "40",
        backgroundOverlayAngle: "180",
      },
    }, "desktop");
    expect(stylesFor(section, "desktop").backgroundMediaId).toBe("22222222-2222-2222-2222-222222222222");
    expect(stylesFor(section, "desktop").background).toBeUndefined();
    updateNode(document, section.id, {
      styles: {
        backgroundMediaId: "",
        backgroundOverlayType: "",
        backgroundOverlayColor: "",
        backgroundOverlayOpacity: "",
        backgroundOverlayAngle: "",
        backgroundOverlayEnd: "",
        backgroundSize: "",
        backgroundPosition: "",
        backgroundRepeat: "",
        backgroundAttachment: "",
      },
    }, "desktop");
    expect(stylesFor(section, "desktop").backgroundMediaId).toBeUndefined();
  });

  it("hides a locked layer without unlocking it", () => {
    const document = page();
    const heading = addChild(document, "page", "HEADING");
    updateNode(document, heading.id, { locked: true }, "desktop");
    setDeviceVisibility(document, heading.id, "desktop", true);
    expect(heading.locked).toBe(true);
    expect(stylesFor(heading, "desktop").display).toBe("none");
  });

  it("keeps an image move on the device that was edited", () => {
    const document = page();
    const picture = addChild(document, "page", "IMAGE");
    updateNode(document, picture.id, { styles: { left: "40px", width: "200px", position: "static" } }, "tablet");
    updateNode(document, picture.id, { styles: { left: "12px", width: "180px" } }, "mobile");
    placeAcross(document, picture.id, { position: "relative", left: "8%", top: "4px", display: "block", width: "24%", height: "auto", aspectRatio: "253 / 259" }, "mobile");
    expect(stylesFor(picture, "desktop").left).toBeUndefined();
    expect(stylesFor(picture, "desktop").width).toBe("100%");
    expect(stylesFor(picture, "tablet").left).toBe("40px");
    expect(stylesFor(picture, "tablet").width).toBe("200px");
    expect(stylesFor(picture, "mobile").left).toBe("8%");
    expect(stylesFor(picture, "mobile").width).toBe("24%");
    expect(stylesFor(picture, "mobile").top).toBe("4px");
    expect(stylesFor(picture, "tablet").top).toBeUndefined();
  });

  it("customises each device without copying the whole element", () => {
    const document = page();
    const hero = addChild(document, "page", "SECTION");
    const heading = addChild(document, hero.id, "HEADING");
    const picture = addChild(document, hero.id, "IMAGE");
    const carousel = addChild(document, "page", "CAROUSEL");
    setResponsiveProperty(document, heading.id, "fontSize", "60px", "desktop");
    setResponsiveProperty(document, heading.id, "fontSize", "40px", "tablet");
    setResponsiveProperty(document, heading.id, "fontSize", "30px", "mobile");
    setResponsiveProperty(document, hero.id, "padding", "16px", "mobile");
    setResponsiveProperty(document, picture.id, "display", "none", "mobile");
    updateNode(document, carousel.id, { props: { perView: 3 } }, "desktop");
    updateNode(document, carousel.id, { props: { perView: 2 } }, "tablet");
    updateNode(document, carousel.id, { props: { perView: 1 } }, "mobile");

    expect(getEffectiveProperty(heading, "fontSize", "desktop")).toBe("60px");
    expect(getEffectiveProperty(heading, "fontSize", "tablet")).toBe("40px");
    expect(getEffectiveProperty(heading, "fontSize", "mobile")).toBe("30px");
    expect(heading.styles.fontSize).toBe("60px");
    expect(heading.responsiveStyles.tablet.fontSize).toBe("40px");
    expect(heading.responsiveStyles.mobile.fontSize).toBe("30px");
    expect(heading.responsiveStyles.mobile.padding).toBeUndefined();
    expect(getEffectiveProperty(hero, "padding", "mobile")).toBe("16px");
    expect(getEffectiveProperty(hero, "padding", "tablet")).toBe("24px");
    expect(hasOverride(heading, "fontSize", "mobile")).toBe(true);
    expect(hasOverride(heading, "color", "mobile")).toBe(false);
    expect(getEffectiveProperty(picture, "display", "mobile")).toBe("none");
    expect(getEffectiveProperty(picture, "display", "desktop")).toBe("");
    expect(propsFor(carousel, "desktop").perView).toBe(3);
    expect(propsFor(carousel, "tablet").perView).toBe(2);
    expect(propsFor(carousel, "mobile").perView).toBe(1);
    expect(carousel.props.perView).toBe(3);

    const before = structuredClone(document);
    removeResponsiveOverride(document, heading.id, ["fontSize"], "mobile");
    expect(getEffectiveProperty(heading, "fontSize", "mobile")).toBe("60px");
    expect(getEffectiveProperty(heading, "fontSize", "tablet")).toBe("40px");
    expect(getEffectiveProperty(heading, "fontSize", "desktop")).toBe("60px");
    removeResponsiveOverride(document, heading.id, ["fontSize"], "tablet");
    expect(getEffectiveProperty(heading, "fontSize", "mobile")).toBe("60px");
    expect(propsFor(carousel, "mobile").perView).toBe(1);

    const restored = structuredClone(before);
    expect(getEffectiveProperty(restored.root.children[0].children[0], "fontSize", "mobile")).toBe("30px");
    expect(getEffectiveProperty(restored.root.children[0].children[0], "fontSize", "tablet")).toBe("40px");
    expect(getEffectiveProperty(restored.root.children[0].children[0], "fontSize", "desktop")).toBe("60px");

    const reloaded = JSON.parse(JSON.stringify(restored)) as EditorDocument;
    expect(getEffectiveProperty(reloaded.root.children[0].children[0], "fontSize", "desktop")).toBe("60px");
    expect(getEffectiveProperty(reloaded.root.children[0].children[0], "fontSize", "tablet")).toBe("40px");
    expect(getEffectiveProperty(reloaded.root.children[0].children[0], "fontSize", "mobile")).toBe("30px");
    expect(propsFor(reloaded.root.children[1], "mobile").perView).toBe(1);
    expect(propsFor(reloaded.root.children[1], "desktop").perView).toBe(3);

    setDeviceVisibility(document, picture.id, "mobile", true);
    expect(stylesFor(picture, "mobile").display).toBe("none");
    expect(stylesFor(picture, "desktop").display).toBeUndefined();
    expect(stylesFor(picture, "tablet").display).toBeUndefined();
    setDeviceVisibility(document, picture.id, "mobile", false);
    expect(stylesFor(picture, "mobile").display).toBeUndefined();

    delete (heading as { responsiveStyles?: unknown }).responsiveStyles;
    delete (heading as { responsiveProps?: unknown }).responsiveProps;
    expect(getEffectiveProperty(heading, "fontSize", "mobile")).toBe("60px");
    expect(getEffectiveProperty(heading, "fontSize", "tablet")).toBe("60px");
  });

  it("moves a child earlier and later within its parent", () => {
    const document = page();
    const heading = addChild(document, "page", "HEADING");
    const text = addChild(document, "page", "PARAGRAPH");
    const picture = addChild(document, "page", "IMAGE");
    shiftChild(document, picture.id, -1);
    expect(document.root.children.map((child) => child.id)).toEqual([heading.id, picture.id, text.id]);
    shiftChild(document, heading.id, -1);
    expect(document.root.children.map((child) => child.id)).toEqual([heading.id, picture.id, text.id]);
    shiftChild(document, heading.id, 1);
    expect(document.root.children.map((child) => child.id)).toEqual([picture.id, heading.id, text.id]);
  });

  it("moves a child into another container", () => {
    const document = page();
    const row = addChild(document, "page", "FLEX");
    const text = addChild(document, "page", "PARAGRAPH");
    moveChild(document, text.id, row.id, 0);
    expect(document.root.children).toHaveLength(1);
    expect(row.children[0].id).toBe(text.id);
  });

  it("inserts a welcome note as a heading and a paragraph", () => {
    const document = page();
    const block = insertBlock(document, "page", "note");
    expect(block.name).toBe("Welcome note");
    expect(block.children.map((child) => child.type)).toEqual(["HEADING", "PARAGRAPH"]);
    expect(document.root.children[0].id).toBe(block.id);
  });
});
