import { describe, expect, it } from "vitest";
import { createElement, growCanvas, orderCanvas, redoHistory, rememberHistory, undoHistory } from "./canvasModel";

function piece(id: string, z: number) {
  return createElement({ id, type: "text", z, text: id }, "#171717");
}

describe("orderCanvas", () => {
  it("paints a piece above the one that was covering it", () => {
    const elements = [piece("back", 1), piece("front", 2)];
    orderCanvas(elements, "back", "front");
    expect(elements.find((item) => item.id === "back")?.z).toBe(2);
    expect(elements.find((item) => item.id === "front")?.z).toBe(1);
  });

  it("separates two pieces that share a layer", () => {
    const elements = [piece("back", 4), piece("front", 4)];
    orderCanvas(elements, "back", "front");
    const back = elements.find((item) => item.id === "back");
    const front = elements.find((item) => item.id === "front");
    expect(back && front && back.z > front.z).toBe(true);
  });

  it("keeps the foremost piece in front", () => {
    const elements = [piece("only", 3)];
    orderCanvas(elements, "only", "front");
    expect(elements[0].z).toBe(3);
  });
});

describe("growCanvas", () => {
  it("keeps at most 50px under the lowest piece after it moves up", () => {
    const lower = createElement({ id: "lower", type: "text", y: 800, h: 80 }, "#171717");
    const upper = createElement({ id: "upper", type: "text", y: 40, h: 70 }, "#171717");
    const canvas = { height: 1600, elements: [upper, lower] };
    lower.y = 180;
    growCanvas(canvas);
    expect(canvas.height).toBe(180 + 80 + 50);
  });
});

describe("edit history", () => {
  it("undoes one step and treats a continued change as the same step", () => {
    const past: { n: number }[] = [];
    const future: { n: number }[] = [];
    rememberHistory(past, future, { n: 1 });
    rememberHistory(past, future, { n: 2 }, "coalesce");
    expect(past).toEqual([{ n: 1 }]);
    const undone = undoHistory(past, future, { n: 3 });
    expect(undone).toEqual({ n: 1 });
    expect(redoHistory(past, future, undone ?? { n: 0 })).toEqual({ n: 3 });
  });
});
