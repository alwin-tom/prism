import { describe, expect, it } from "vitest";
import { quoteFromDays } from "./money";

describe("quoteFromDays", () => {
  it("prices seven hosting days at five hundred rupees each", () => {
    const quote = quoteFromDays(7);
    expect(quote.amountPaise).toBe(350_000);
    expect(quote.formatted).toBe("₹3,500");
  });
});
