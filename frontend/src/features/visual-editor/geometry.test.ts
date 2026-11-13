import { describe, expect, it } from "vitest";
import { alignMove, edgeDrift, lengthPx, moveStyles, preferPercent, resizedFrame, resizeStyles, rotateTransform } from "./geometry";

const start = { left: 10, top: 20, width: 100, height: 40, position: "" };

describe("canvas geometry", () => {
  it("resizes width from the right and height from the bottom", () => {
    expect(resizeStyles(start, 15, 0, "e", false).width).toBe("115px");
    expect(resizeStyles(start, 0, 12, "s", false).height).toBe("52px");
  });

  it("resizes from the left and keeps the right edge in place", () => {
    const next = resizeStyles(start, 10, 0, "w", false);
    expect(next.width).toBe("90px");
    expect(next.left).toBe("20px");
    expect(next.position).toBe("relative");
  });

  it("stores a horizontal resize as a percentage of the row", () => {
    expect(resizeStyles(start, 15, 0, "e", false, 200).width).toBe("57.5%");
    const next = resizeStyles(start, 10, 0, "w", false, 200);
    expect(next.width).toBe("45%");
    expect(next.left).toBe("10%");
    expect(resizeStyles(start, 0, 12, "s", false, 200).height).toBe("52px");
    expect(preferPercent("120px", 400)).toBe("30%");
    expect(preferPercent("50%", 400)).toBe("50%");
    expect(preferPercent("auto", 400)).toBe("auto");
  });

  it("keeps the corner proportion and the opposite corner", () => {
    const next = resizeStyles(start, 50, 4, "se", true);
    expect(next.width).toBe("150px");
    expect(next.height).toBe("60px");
    expect(next.minWidth).toBe("0");
    expect(next.minHeight).toBe("0");
    expect(next.aspectRatio).toBe("100 / 40");
    const frame = resizedFrame(start, 50, 4, "se", false);
    expect(frame.left).toBe(10);
    expect(frame.top).toBe(20);
    expect(frame.width).toBe(150);
  });

  it("keeps the dragged corner on one continuous scale", () => {
    const box = { left: 0, top: 0, width: 100, height: 40 };
    const before = resizedFrame(box, 30, 29, "se", false);
    const after = resizedFrame(box, 30, 31, "se", false);
    expect(Math.abs(before.width - after.width)).toBeLessThan(15);
    expect(before.width / before.height).toBeCloseTo(after.width / after.height, 1);
  });

  it("keeps the top edge of the frame still when the bottom grows", () => {
    const frame = resizedFrame(start, 0, 12, "s", false);
    expect(frame.height).toBe(52);
    expect(frame.top).toBe(20);
    expect(resizeStyles(start, 0, 12, "s", false).aspectRatio).toBe("auto");
  });

  it("reports how far the pinned edge drifted", () => {
    const drift = edgeDrift(start, { left: 0, top: 14, width: 110, height: 52 }, "s");
    expect(drift.y).toBe(6);
    expect(drift.x).toBe(10);
  });

  it("moves sideways as a percentage of the row and keeps the vertical offset in pixels", () => {
    expect(moveStyles(start, 8, -4, 200)).toEqual({ position: "relative", left: "9%", top: "16px" });
    expect(lengthPx("9%", 200)).toBe(18);
    expect(lengthPx("12px", 200)).toBe(12);
  });

  it("marks the centre and matching edges while a move is close", () => {
    const page = { left: 0, top: 0, right: 200, bottom: 400 };
    const neighbour = { left: 40, top: 80, right: 90, bottom: 120 };
    const centered = alignMove({ left: 78, top: 30, right: 118, bottom: 70 }, [page]);
    expect(centered.dx).toBe(2);
    expect(centered.guides.some((line) => line.axis === "x" && line.at === 100)).toBe(true);
    const aligned = alignMove({ left: 36, top: 78, right: 70, bottom: 110 }, [neighbour]);
    expect(aligned.dx).toBe(4);
    expect(aligned.dy).toBe(2);
    expect(aligned.guides.map((line) => line.axis).sort()).toEqual(["x", "y"]);
    const clear = alignMove({ left: 0, top: 200, right: 20, bottom: 220 }, [neighbour]);
    expect(clear.guides).toEqual([]);
  });

  it("keeps the centre line on the parent middle when another element is only slightly off it", () => {
    const middle = { left: 20, top: 0, right: 220, bottom: 80 };
    const shifted = { left: 100, top: 10, right: 160, bottom: 40 };
    const moving = { left: 106, top: 20, right: 146, bottom: 50 };
    const aligned = alignMove(moving, [shifted], 6, middle);
    expect(aligned.dx).toBe(-6);
    expect(aligned.guides.some((line) => line.axis === "x" && line.at === 120)).toBe(true);
  });

  it("replaces only the rotation in a transform", () => {
    expect(rotateTransform("scale(1.2) rotate(4deg)", 15)).toBe("rotate(15deg) scale(1.2)");
  });
});
