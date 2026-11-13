export type Account = {
  id: string;
  fullName: string;
  phoneNumber: string;
  phoneDisplay: string;
  email: string | null;
  roles: string[];
  status: string;
};

export type Category = {
  id: string;
  name: string;
  code: string;
  description: string;
  thumbnailUrl: string | null;
  active: boolean;
};

export type TemplateSummary = {
  id: string;
  categoryId: string;
  name: string;
  code: string;
  description: string;
  thumbnailUrl: string | null;
  active: boolean;
  version: number | null;
  versionId: string | null;
};

export type Section = {
  id: string;
  type: string;
  properties: Record<string, unknown>;
};

export type SiteConfiguration = {
  theme: {
    background: string;
    ink: string;
    accent: string;
    font: string;
    displayFont: string;
  };
  event: {
    name: string;
    subtitle: string;
    description: string;
    startAt: string;
    endAt: string;
    timezone: string;
    venueName: string;
    venueAddress: string;
    mapUrl: string;
  };
  rsvp: {
    enabled: boolean;
    deadline: string;
    maxGuestsPerResponse: number;
    allowPlusOne: boolean;
    collectPhone: boolean;
    collectEmail: boolean;
    confirmationMessage: string;
  };
  sections: Section[];
  canvas?: SiteCanvas;
};

export type SiteCanvas = {
  height: number;
  elements: CanvasElement[];
};

export type CanvasElement = {
  id: string;
  type: "text" | "image" | "button" | "box" | "rsvp";
  x: number;
  y: number;
  w: number;
  h: number;
  z: number;
  text: string;
  fontSize: number;
  color: string;
  align: "left" | "center" | "right";
  fill: string;
  mediaId: string;
  weight: "regular" | "display";
  font?: string;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  borderWidth?: number;
  borderColor?: string;
  radius?: number;
  shadow?: number;
  href?: string;
};

export type HostingSummary = {
  status: string | null;
  startAt: string | null;
  endAt: string | null;
  days: number | null;
  daysRemaining: number;
  dailyRatePaise?: number;
};

export type ReviewNote = {
  id: string;
  decision: string;
  comments: string;
  createdAt: string;
};

export type EventSite = {
  id: string;
  eventName: string;
  slug: string;
  status: string;
  requestedDays: number;
  categoryId: string;
  categoryName: string | null;
  templateId: string | null;
  templateName: string | null;
  templateCode: string | null;
  revisionId: string | null;
  revisionNumber: number | null;
  submissionStatus: string | null;
  configuration: SiteConfiguration;
  editorDocument?: import("./features/visual-editor/document").EditorDocument | null;
  publicUrl: string;
  hosting: HostingSummary;
  latestReview: ReviewNote | null;
};

export type Quote = {
  days: number;
  dailyRatePaise: number;
  amountPaise: number;
  currency: string;
  formatted: string;
};

export type PublicEvent = {
  slug: string;
  eventName: string;
  availability: "PUBLISHED" | "EXPIRED" | "SUSPENDED";
  templateCode: string | null;
  configuration: SiteConfiguration | null;
  editorDocument?: import("./features/visual-editor/document").EditorDocument | null;
  seo?: { title: string; description: string };
};

export type RsvpSummary = {
  total: number;
  accepted: number;
  declined: number;
  expectedGuests: number;
  pageViews?: number;
};

export type Envelope<T> = { success: boolean; data: T; meta?: { page: number; size: number; total: number; totalPages: number } };
