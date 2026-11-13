import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../utils/cn";

const button = cva("btn", {
  variants: { tone: { primary: "btn", secondary: "btn-secondary", text: "btn-text" } },
  defaultVariants: { tone: "primary" },
});

export function Button({
  tone,
  className,
  asChild,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof button> & { asChild?: boolean }) {
  const Comp = asChild ? Slot : "button";
  return <Comp className={cn(button({ tone }), className)} {...props} />;
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint ? <em className="field-hint">{hint}</em> : null}
    </label>
  );
}

export function Pill({ status }: { status: string }) {
  const tone = status === "PUBLISHED" ? "good" : status === "REJECTED" || status === "EXPIRED" || status === "SUSPENDED" ? "bad" : status === "APPROVED" || status === "SUBMITTED" || status === "IN_REVIEW" || status === "CHANGES_REQUESTED" ? "wait" : "";
  const label = status.replaceAll("_", " ").toLowerCase();
  return <span className={`pill ${tone}`}>{label}</span>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}
