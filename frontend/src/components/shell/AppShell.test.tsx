import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AppShell } from "./AppShell";
import type { Account } from "../../types";

const account: Account = {
  id: "user-1",
  fullName: "WebAux Admin",
  phoneNumber: "919176791836",
  phoneDisplay: "+91 91767 91836",
  email: null,
  roles: ["CUSTOMER"],
  status: "ACTIVE",
};

describe("AppShell", () => {
  it("collapses the sidebar and keeps that choice", () => {
    localStorage.removeItem("prism-sidebar");
    const view = render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AppShell account={account} mode="customer" />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(view.container.querySelector(".app")).not.toHaveClass("is-collapsed");
    const sidebar = view.container.querySelector("#app-sidebar");
    const collapse = screen.getByRole("button", { name: "Collapse sidebar" });
    expect(sidebar).toContainElement(collapse);
    fireEvent.click(collapse);
    expect(view.container.querySelector(".app")).toHaveClass("is-collapsed");
    const expand = screen.getByRole("button", { name: "Expand sidebar" });
    expect(expand).toHaveAttribute("aria-expanded", "false");
    expect(sidebar).toContainElement(expand);
    expect(localStorage.getItem("prism-sidebar")).toBe("collapsed");
    fireEvent.click(screen.getByRole("button", { name: "Expand sidebar" }));
    expect(view.container.querySelector(".app")).not.toHaveClass("is-collapsed");
    expect(localStorage.getItem("prism-sidebar")).toBe("open");
  });
});
