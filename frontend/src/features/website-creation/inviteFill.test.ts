import { describe, expect, it } from "vitest";
import { fillFromInvite, toIsoDate } from "./inviteFill";
import type { Question } from "./types";

const questions: Question[] = [
  { key: "coupleNames", type: "text", label: "Couple names" },
  { key: "eventDate", type: "date", label: "Wedding date" },
  { key: "eventTime", type: "time", label: "Start time" },
  { key: "venueName", type: "text", label: "Venue" },
  { key: "story", type: "textarea", label: "Your story" },
  { key: "palette", type: "single", label: "Palette", options: [{ value: "ivory", label: "Ivory" }] },
];

describe("fillFromInvite", () => {
  it("reads names, a day-first date, a time, a venue, and a long note", () => {
    const text = [
      "Vineesh and Jaya",
      "24/11/2026 at 6:30 pm",
      "Aluva Town Hall",
      "We met at the river and would like the page to carry a quiet note about the evening and the people who will be there with us.",
    ].join("\n");
    expect(fillFromInvite(text, questions)).toEqual({
      coupleNames: "Vineesh and Jaya",
      eventDate: "2026-11-24",
      eventTime: "18:30",
      venueName: "Aluva Town Hall",
      story: "We met at the river and would like the page to carry a quiet note about the evening and the people who will be there with us.",
    });
  });

  it("turns a written date into an input value", () => {
    expect(toIsoDate("24 November 2026")).toBe("2026-11-24");
  });
});
