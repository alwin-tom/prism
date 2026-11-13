import { describe, expect, it } from "vitest";
import { visible } from "./conditions";

describe("questionnaire conditions", () => {
  const answers = { countdown: "yes", guests: 40, themes: ["floral"] };

  it("matches equality, emptiness, comparison, and groups", () => {
    expect(visible({ key: "countdown", eq: "yes" }, answers)).toBe(true);
    expect(visible({ key: "countdown", neq: "yes" }, answers)).toBe(false);
    expect(visible({ key: "themes", contains: "floral" }, answers)).toBe(true);
    expect(visible({ key: "missing", empty: true }, answers)).toBe(true);
    expect(visible({ key: "guests", gt: 10 }, answers)).toBe(true);
    expect(visible({ key: "guests", lt: 80 }, answers)).toBe(true);
    expect(visible({ all: [{ key: "countdown", eq: "yes" }, { key: "guests", gt: 10 }] }, answers)).toBe(true);
    expect(visible({ all: [{ key: "countdown", eq: "no" }, { key: "guests", gt: 10 }] }, answers)).toBe(false);
    expect(visible({ any: [{ key: "countdown", eq: "no" }, { key: "guests", gt: 10 }] }, answers)).toBe(true);
    expect(visible(undefined, answers)).toBe(true);
  });
});
