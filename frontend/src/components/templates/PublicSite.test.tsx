import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { PublicSite } from "./PublicSite";
import type { SiteConfiguration } from "../../types";

const configuration: SiteConfiguration = {
  theme: { background: "#f7f6f3", ink: "#171717", accent: "#7a1f2b", font: "Public Sans", displayFont: "Fraunces" },
  event: { name: "Meera and Arun", subtitle: "A wedding", description: "Dinner follows.", startAt: "2026-12-12T13:30:00Z", endAt: "", timezone: "Asia/Kolkata", venueName: "The linen hall", venueAddress: "Fort Kochi", mapUrl: "" },
  rsvp: { enabled: false, deadline: "", maxGuestsPerResponse: 4, allowPlusOne: false, collectPhone: false, collectEmail: false, confirmationMessage: "" },
  sections: [{ id: "hero", type: "HERO", properties: { title: "<script>alert(1)</script>", subtitle: "Together" } }],
};

describe("PublicSite", () => {
  it("prints markup as text and does not insert a script", () => {
    const client = new QueryClient();
    const view = render(
      <QueryClientProvider client={client}>
        <PublicSite event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "WEDDING_LINEN", configuration }} />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("<script>alert(1)</script>");
    expect(screen.getByText("Made with WebAux")).toBeInTheDocument();
    expect(view.container.querySelector("script")).toBeNull();
  });

  it("writes preview copy back through onEdit", () => {
    const onEdit = vi.fn();
    const client = new QueryClient();
    render(
      <QueryClientProvider client={client}>
        <PublicSite preview onEdit={onEdit} event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "WEDDING_LINEN", configuration }} />
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "My birthday" } });
    expect(onEdit).toHaveBeenCalledWith({ target: "section", id: "hero", key: "title", value: "My birthday" });
  });

  it("places an image between sections so later sections follow it", () => {
    const client = new QueryClient();
    const withPicture: SiteConfiguration = {
      ...configuration,
      sections: [
        { id: "hero", type: "HERO", properties: { title: "Meera", subtitle: "Together", backgroundImage: "11111111-1111-1111-1111-111111111111", imageX: 10, imageY: 50, imageW: 40, imageH: 30 } },
        { id: "note", type: "TEXT_BLOCK", properties: { heading: "After", body: "Later" } },
      ],
    };
    const view = render(
      <QueryClientProvider client={client}>
        <PublicSite preview onEdit={vi.fn()} event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "BIRTHDAY_DARK", configuration: withPicture }} />
      </QueryClientProvider>,
    );
    const picture = view.container.querySelector(".flow-image");
    const hero = view.container.querySelector(".event-hero");
    const note = view.container.querySelector(".event-section");
    expect(picture).toBeInstanceOf(HTMLElement);
    expect(hero).toBeInstanceOf(HTMLElement);
    expect(note).toBeInstanceOf(HTMLElement);
    expect(screen.getByRole("button", { name: "Resize image" })).toBeInTheDocument();
    expect(hero!.compareDocumentPosition(picture!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(picture!.compareDocumentPosition(note!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("shows every placed picture", () => {
    const client = new QueryClient();
    const withPictures: SiteConfiguration = {
      ...configuration,
      sections: [
        { id: "hero", type: "HERO", properties: { title: "Meera", subtitle: "Together" } },
        { id: "one", type: "IMAGE", properties: { mediaId: "11111111-1111-1111-1111-111111111111", imageY: 50, imageW: 40, imageH: 24 } },
        { id: "two", type: "IMAGE", properties: { mediaId: "22222222-2222-2222-2222-222222222222", imageY: 50, imageW: 40, imageH: 24 } },
      ],
    };
    const view = render(
      <QueryClientProvider client={client}>
        <PublicSite preview event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "BIRTHDAY_DARK", configuration: withPictures }} />
      </QueryClientProvider>,
    );
    expect(view.container.querySelectorAll(".flow-image")).toHaveLength(2);
  });

  it("leaves template artwork off the page", () => {
    const client = new QueryClient();
    const withArt: SiteConfiguration = {
      ...configuration,
      sections: [{ id: "hero", type: "HERO", properties: { title: "Meera", subtitle: "Together", backgroundImage: "/placeholders/dark.svg" } }],
    };
    const view = render(
      <QueryClientProvider client={client}>
        <PublicSite event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "BIRTHDAY_DARK", configuration: withArt }} />
      </QueryClientProvider>,
    );
    expect(view.container.querySelector("img")).toBeNull();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Meera");
  });

  it("places a text block on the grid and changes its type size", () => {
    const onEdit = vi.fn();
    const client = new QueryClient();
    const withBlock: SiteConfiguration = {
      ...configuration,
      sections: [
        { id: "note", type: "TEXT_BLOCK", properties: { heading: "After", body: "Later", frameX: 50, frameW: 50, frameH: 30, fontSize: 28 } },
      ],
    };
    const view = render(
      <QueryClientProvider client={client}>
        <PublicSite preview onEdit={onEdit} event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "BIRTHDAY_DARK", configuration: withBlock }} />
      </QueryClientProvider>,
    );
    const block = view.container.querySelector(".event-block") as HTMLElement;
    expect(block.style.gridColumn).toBe("7 / span 6");
    expect(block.style.getPropertyValue("--block-h")).toBe("30");
    expect(block.style.fontSize).toBe("28px");
    fireEvent.click(screen.getByRole("button", { name: "Larger text" }));
    expect(onEdit).toHaveBeenCalledWith({ target: "section", id: "note", key: "fontSize", value: "30" });
    expect(screen.getByRole("button", { name: "Resize block" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Move block" })).toBeInTheDocument();
  });

  it("renders a custom canvas instead of the locked sections", () => {
    const client = new QueryClient();
    const custom: SiteConfiguration = {
      ...configuration,
      canvas: {
        height: 640,
        elements: [
          { id: "hello", type: "text", x: 8, y: 24, w: 50, h: 70, z: 1, text: "Hello canvas", fontSize: 32, color: "#171717", align: "left", fill: "", mediaId: "", weight: "display", font: "Newsreader", bold: true, italic: true, underline: true },
          { id: "more", type: "text", x: 8, y: 120, w: 40, h: 40, z: 2, text: "Second line", fontSize: 16, color: "#171717", align: "left", fill: "", mediaId: "", weight: "regular" },
        ],
      },
    };
    render(
      <QueryClientProvider client={client}>
        <PublicSite preview onEdit={vi.fn()} event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "BIRTHDAY_DARK", configuration: custom }} />
      </QueryClientProvider>,
    );
    expect(screen.getByText("Hello canvas")).toBeInTheDocument();
    expect(screen.getByText("Made with WebAux")).toBeInTheDocument();
    const face = screen.getByText("Hello canvas").closest(".canvas-face") as HTMLElement;
    expect(face.style.fontFamily).toContain("Newsreader");
    expect(face.style.fontWeight).toBe("700");
    expect(face.style.fontStyle).toBe("italic");
    expect(face.style.textDecoration).toContain("underline");
    expect(screen.getByText("Second line")).toBeInTheDocument();
    expect(screen.queryByText("<script>alert(1)</script>")).toBeNull();
  });

  it("uploads into an empty picture and styles the selected piece", () => {
    const onEdit = vi.fn();
    const client = new QueryClient();
    const custom: SiteConfiguration = {
      ...configuration,
      canvas: {
        height: 640,
        elements: [
          { id: "pic", type: "image", x: 8, y: 24, w: 30, h: 160, z: 1, text: "", fontSize: 16, color: "#171717", align: "left", fill: "", mediaId: "", weight: "regular" },
          { id: "come", type: "button", x: 8, y: 200, w: 28, h: 48, z: 2, text: "Come", fontSize: 16, color: "#ffffff", align: "center", fill: "#171717", mediaId: "", weight: "regular" },
        ],
      },
    };
    render(
      <QueryClientProvider client={client}>
        <PublicSite preview mediaEventId="event-1" onEdit={onEdit} event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "BIRTHDAY_DARK", configuration: custom }} />
      </QueryClientProvider>,
    );
    expect(screen.getByLabelText("Upload a picture")).toBeInTheDocument();
    fireEvent.pointerDown(screen.getByText("Come"));
    expect(screen.getByLabelText("Text colour")).toBeInTheDocument();
    expect(screen.getByLabelText("Border width")).toBeInTheDocument();
    expect(screen.getByLabelText("Corner radius")).toBeInTheDocument();
    expect(screen.getByLabelText("Shadow")).toBeInTheDocument();
    expect(screen.getByLabelText("Link address")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Bring forward" }));
    fireEvent.click(screen.getByRole("button", { name: "Send backward" }));
    expect(onEdit).toHaveBeenNthCalledWith(1, { target: "canvas-order", id: "come", direction: "front" });
    expect(onEdit).toHaveBeenNthCalledWith(2, { target: "canvas-order", id: "come", direction: "back" });
    fireEvent.click(screen.getByRole("button", { name: "Bold" }));
    fireEvent.click(screen.getByRole("button", { name: "Italic" }));
    fireEvent.click(screen.getByRole("button", { name: "Underline" }));
    fireEvent.change(screen.getByLabelText("Font"), { target: { value: "Newsreader" } });
    expect(onEdit).toHaveBeenCalledWith({ target: "canvas-element", id: "come", patch: { bold: true } });
    expect(onEdit).toHaveBeenCalledWith({ target: "canvas-element", id: "come", patch: { italic: true } });
    expect(onEdit).toHaveBeenCalledWith({ target: "canvas-element", id: "come", patch: { underline: true } });
    expect(onEdit).toHaveBeenCalledWith({ target: "canvas-element", id: "come", patch: { font: "Newsreader" } });
    fireEvent.click(screen.getByRole("button", { name: "Remove border" }));
    expect(onEdit).toHaveBeenCalledWith({ target: "canvas-element", id: "come", patch: { borderWidth: 0 } });
  });

  it("turns a button into an anchor and drops a script link", () => {
    const client = new QueryClient();
    const linked: SiteConfiguration = {
      ...configuration,
      canvas: {
        height: 400,
        elements: [
          { id: "map", type: "button", x: 8, y: 24, w: 28, h: 48, z: 1, text: "Map", fontSize: 16, color: "#ffffff", align: "center", fill: "#171717", mediaId: "", weight: "regular", href: "https://example.com/map" },
          { id: "bad", type: "button", x: 8, y: 90, w: 28, h: 48, z: 2, text: "Bad", fontSize: 16, color: "#ffffff", align: "center", fill: "#171717", mediaId: "", weight: "regular", href: "javascript:alert(1)" },
        ],
      },
    };
    render(
      <QueryClientProvider client={client}>
        <PublicSite preview event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "BIRTHDAY_DARK", configuration: linked }} />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("link", { name: "Map" })).toHaveAttribute("href", "https://example.com/map");
    expect(screen.queryByRole("link", { name: "Bad" })).toBeNull();
    expect(screen.getByText("Bad")).toBeInTheDocument();
  });

  it("selects several pieces with shift and moves them together", () => {
    const onEdit = vi.fn();
    const client = new QueryClient();
    const custom: SiteConfiguration = {
      ...configuration,
      canvas: {
        height: 640,
        elements: [
          { id: "one", type: "text", x: 8, y: 24, w: 40, h: 70, z: 1, text: "One", fontSize: 16, color: "#171717", align: "left", fill: "", mediaId: "", weight: "regular" },
          { id: "two", type: "text", x: 8, y: 120, w: 40, h: 40, z: 2, text: "Two", fontSize: 16, color: "#171717", align: "left", fill: "", mediaId: "", weight: "regular" },
        ],
      },
    };
    const view = render(
      <QueryClientProvider client={client}>
        <PublicSite preview onEdit={onEdit} event={{ slug: "meera", eventName: "Meera", availability: "PUBLISHED", templateCode: "BIRTHDAY_DARK", configuration: custom }} />
      </QueryClientProvider>,
    );
    const point = (target: Document | Element | Window, type: string, init: MouseEventInit = {}) => {
      fireEvent(target, new MouseEvent(type, { bubbles: true, cancelable: true, ...init }));
    };
    point(screen.getByText("One"), "pointerdown");
    point(window, "pointerup");
    point(screen.getByText("Two"), "pointerdown", { shiftKey: true });
    expect(view.container.querySelectorAll(".canvas-el.is-selected")).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Resize element" })).toHaveLength(2);
    point(screen.getByText("One"), "pointerdown", { clientX: 10, clientY: 10 });
    point(window, "pointermove", { clientX: 10, clientY: 40 });
    expect(onEdit).toHaveBeenCalledWith({
      target: "canvas-elements",
      patches: [
        { id: "one", patch: { x: 8, y: 54 } },
        { id: "two", patch: { x: 8, y: 150 } },
      ],
    });
    point(window, "pointerup");
    point(screen.getAllByRole("button", { name: "Resize element" })[0], "pointerdown", { clientX: 0, clientY: 0 });
    point(window, "pointermove", { clientX: 0, clientY: 20 });
    expect(onEdit).toHaveBeenCalledWith({
      target: "canvas-elements",
      patches: [
        { id: "one", patch: { w: 40, h: 90 } },
        { id: "two", patch: { w: 40, h: 60 } },
      ],
    });
    point(window, "pointerup");
    const page = view.container.querySelector(".canvas-page") as HTMLElement;
    page.getBoundingClientRect = () => ({ width: 100, height: 640, top: 0, left: 0, right: 100, bottom: 640, x: 0, y: 0, toJSON() { return {}; } }) as DOMRect;
    point(screen.getAllByRole("button", { name: "Resize width" })[0], "pointerdown", { clientX: 0, clientY: 0 });
    point(window, "pointermove", { clientX: 10, clientY: 40 });
    expect(onEdit).toHaveBeenCalledWith({
      target: "canvas-elements",
      patches: [
        { id: "one", patch: { w: 50 } },
        { id: "two", patch: { w: 50 } },
      ],
    });
    point(window, "pointerup");
    point(screen.getAllByRole("button", { name: "Resize height" })[0], "pointerdown", { clientX: 0, clientY: 0 });
    point(window, "pointermove", { clientX: 10, clientY: 20 });
    expect(onEdit).toHaveBeenCalledWith({
      target: "canvas-elements",
      patches: [
        { id: "one", patch: { h: 90 } },
        { id: "two", patch: { h: 60 } },
      ],
    });
    point(window, "pointerup");
    point(screen.getByText("One"), "pointerdown", { ctrlKey: true });
    expect(view.container.querySelectorAll(".canvas-el.is-selected")).toHaveLength(1);
  });
});
