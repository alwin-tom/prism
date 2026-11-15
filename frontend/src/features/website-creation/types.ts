import type { When } from "./conditions";

export type QuestionOption = { value: string; label: string };
export type RepeatField = { key: string; type: string; label: string };

export type Question = {
  key: string;
  type: string;
  group?: string;
  label: string;
  description?: string;
  placeholder?: string;
  help?: string;
  required?: boolean;
  options?: QuestionOption[];
  item?: RepeatField[];
  when?: When;
};

export type CreationSession = {
  id: string;
  categoryCode: string | null;
  categoryName: string | null;
  questionnaireVersion: number;
  eventId: string | null;
  status: string;
  currentStep: string;
  updatedAt: string;
  answers?: Record<string, unknown>;
  questions?: Question[];
  hasDocument: boolean;
  latestJobId?: string;
  latestJobStatus?: string;
};

export type WebsiteCategory = {
  code: string;
  name: string;
  description: string;
  icon: string;
  active: boolean;
};

export type ImageProgress = {
  total: number;
  completed: number;
  failed: number;
  skipped: number;
  pending: number;
  currentLabel: string;
};

export type GenerationJob = {
  id: string;
  sessionId: string;
  eventId: string | null;
  status: string;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  imageProgress?: ImageProgress | null;
};

export const GROUPS = [
  "Website information",
  "Event or business details",
  "People and names",
  "Dates and locations",
  "Images and media",
  "Design preferences",
  "Additional requirements",
];
