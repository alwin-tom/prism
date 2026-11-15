import { describe, expect, it } from "vitest";
import { backgroundPatch, hasOverlay, overlayCss, readBackground, supportsBackgroundImage } from "./background";

describe("background helpers", () => {
  it("supports layout surfaces and not bare pictures", () => {
    expect(supportsBackgroundImage("SECTION")).toBe(true);
    expect(supportsBackgroundImage("GROUP")).toBe(true);
    expect(supportsBackgroundImage("IMAGE")).toBe(false);
  });

  it("builds cover and custom size patches", () => {
    expect(backgroundPatch({ mediaId: "11111111-1111-1111-1111-111111111111", sizeMode: "cover", positionMode: "center", repeat: "no-repeat", attachment: "scroll" })).toMatchObject({
      backgroundMediaId: "11111111-1111-1111-1111-111111111111",
      backgroundSize: "cover",
      backgroundPosition: "center",
      backgroundRepeat: "no-repeat",
      backgroundAttachment: "scroll",
    });
    expect(backgroundPatch({ sizeMode: "custom", sizeCustom: "80% auto", positionMode: "custom", positionCustom: "10% 20%" })).toMatchObject({
      backgroundSize: "80% auto",
      backgroundPosition: "10% 20%",
    });
  });

  it("clears background fields", () => {
    const cleared = backgroundPatch({ clear: true });
    expect(cleared.backgroundMediaId).toBe("");
    expect(cleared.backgroundOverlayType).toBe("");
  });

  it("reads overlay settings for a gradient veil", () => {
    const styles = backgroundPatch({
      overlayType: "gradient",
      overlayColor: "#000000",
      overlayEnd: "#333333",
      overlayOpacity: 50,
      overlayAngle: 90,
    });
    expect(hasOverlay(styles)).toBe(true);
    expect(overlayCss(styles)).toEqual({
      background: "linear-gradient(90deg, #000000, #333333)",
      opacity: 0.5,
    });
    expect(readBackground(styles).overlayType).toBe("gradient");
  });
});
