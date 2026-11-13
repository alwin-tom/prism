import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HostingRunway } from "./HostingRunway";

describe("HostingRunway", () => {
  it("draws one cell for each hosting day", () => {
    render(<HostingRunway days={7} label="7 hosting days" />);
    expect(screen.getByLabelText("7 hosting days").querySelectorAll("i")).toHaveLength(7);
  });
});
