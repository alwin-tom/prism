export type When = {
  key?: string;
  eq?: string | number | boolean;
  neq?: string | number | boolean;
  contains?: string;
  empty?: boolean;
  gt?: number;
  lt?: number;
  all?: When[];
  any?: When[];
};

export type Answers = Record<string, unknown>;

export function visible(when: When | null | undefined, answers: Answers): boolean {
  if (!when || Object.keys(when).length === 0) return true;
  if (when.all) return when.all.every((rule) => visible(rule, answers));
  if (when.any) return when.any.length === 0 || when.any.some((rule) => visible(rule, answers));
  const actual = answers[when.key ?? ""];
  if ("eq" in when) return same(actual, when.eq);
  if ("neq" in when) return !same(actual, when.neq);
  if ("contains" in when) return contains(actual, String(when.contains ?? ""));
  if ("empty" in when) return when.empty === isEmpty(actual);
  if ("gt" in when) return number(actual) > Number(when.gt);
  if ("lt" in when) return number(actual) < Number(when.lt);
  return true;
}

export function isEmpty(value: unknown): boolean {
  if (value == null) return true;
  if (Array.isArray(value)) return value.length === 0;
  return String(value).trim() === "";
}

function same(actual: unknown, expected: unknown): boolean {
  if (isEmpty(actual) && (expected == null || expected === "")) return isEmpty(actual);
  if (actual == null || expected == null) return false;
  if (typeof actual === "number" && typeof expected === "number") return actual === expected;
  return String(actual).trim().toLowerCase() === String(expected).trim().toLowerCase();
}

function contains(actual: unknown, needle: string): boolean {
  if (actual == null) return false;
  const target = needle.toLowerCase();
  if (Array.isArray(actual)) return actual.some((item) => String(item).toLowerCase().includes(target));
  return String(actual).toLowerCase().includes(target);
}

function number(value: unknown): number {
  if (typeof value === "number") return value;
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}
