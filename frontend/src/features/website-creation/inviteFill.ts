import type { Question } from "./types";

const OPEN = new Set(["text", "textarea", "date", "time"]);

export function fillFromInvite(text: string, questions: Question[]): Record<string, string> {
  const lines = text.split(/\n/).map((line) => line.trim()).filter(Boolean);
  if (lines.length === 0) return {};
  const dateMatch = text.match(/\b(\d{1,2}[/.\\-]\d{1,2}[/.\\-]\d{2,4}|\d{1,2}(?:st|nd|rd|th)?\s+[A-Za-z]+\s+\d{4}|[A-Za-z]+\s+\d{1,2}(?:st|nd|rd|th)?,?\s+\d{4})\b/);
  const timeMatch = text.match(/\b(\d{1,2}(?::\d{2})?\s*(?:am|pm))\b/i);
  const names = lines.find((line) => /\b(and|&)\b/i.test(line) && line.length < 90);
  const venue = lines.find((line) => /\b(hall|hotel|venue|resort|auditorium|church|temple|mandapam)\b/i.test(line));
  const story = lines.filter((line) => line.length > 80).join("\n");
  const patch: Record<string, string> = {};
  let nameUsed = false;
  let dateUsed = false;
  let timeUsed = false;
  let venueUsed = false;
  let storyUsed = false;
  for (const question of questions) {
    if (!OPEN.has(question.type)) continue;
    const blob = `${question.key} ${question.label}`.toLowerCase();
    if (!nameUsed && names && question.type !== "date" && question.type !== "time" && /name|couple|host|title|person|who/.test(blob)) {
      patch[question.key] = names.replace(/^(dear guests|dear|invitation|invite)[:\s-]*/i, "").trim();
      nameUsed = true;
      continue;
    }
    if (!dateUsed && dateMatch && question.type !== "time" && /date|when|day/.test(blob)) {
      patch[question.key] = question.type === "date" ? toIsoDate(dateMatch[0]) : dateMatch[0];
      dateUsed = true;
      continue;
    }
    if (!timeUsed && timeMatch && /time|hour/.test(blob)) {
      patch[question.key] = question.type === "time" ? toIsoTime(timeMatch[0]) : timeMatch[0];
      timeUsed = true;
      continue;
    }
    if (!venueUsed && venue && question.type !== "date" && question.type !== "time" && /venue|place|location|address|where|city/.test(blob)) {
      patch[question.key] = venue;
      venueUsed = true;
      continue;
    }
    if (!storyUsed && story && question.type === "textarea" && /story|about|message|description|note|detail/.test(blob)) {
      patch[question.key] = story;
      storyUsed = true;
    }
  }
  return patch;
}

export function toIsoDate(raw: string) {
  const numeric = raw.match(/^(\d{1,2})[/.\\-](\d{1,2})[/.\\-](\d{2,4})$/);
  if (numeric) {
    const year = numeric[3].length === 2 ? `20${numeric[3]}` : numeric[3];
    return `${year}-${numeric[2].padStart(2, "0")}-${numeric[1].padStart(2, "0")}`;
  }
  const parsed = new Date(raw.replace(/(\d)(st|nd|rd|th)/gi, "$1"));
  if (Number.isNaN(parsed.getTime())) return raw;
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const day = String(parsed.getDate()).padStart(2, "0");
  return `${parsed.getFullYear()}-${month}-${day}`;
}

function toIsoTime(raw: string) {
  const match = raw.trim().match(/^(\d{1,2})(?::(\d{2}))?\s*(am|pm)$/i);
  if (!match) return raw;
  let hour = Number(match[1]);
  const minute = match[2] ?? "00";
  const mer = match[3].toLowerCase();
  if (mer === "pm" && hour < 12) hour += 12;
  if (mer === "am" && hour === 12) hour = 0;
  if (hour > 23) return raw;
  return `${String(hour).padStart(2, "0")}:${minute}`;
}
