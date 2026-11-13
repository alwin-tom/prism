import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "../../utils/cn";

export function GradientLink({
  to,
  children,
  className,
}: {
  to: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Link to={to} className={cn("btn-gradient", className)}>
      {children}
    </Link>
  );
}

export function SoftButton({
  to,
  children,
  className,
}: {
  to: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Link to={to} className={cn("btn-soft", className)}>
      {children}
    </Link>
  );
}

export function KpiCard({
  label,
  value,
  hint,
  meta,
  accent,
  icon,
  children,
}: {
  label: string;
  value: string | number;
  hint?: string;
  meta?: ReactNode;
  accent?: "teal" | "amber" | "none";
  icon?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <article className={cn("kpi-card", accent === "teal" && "is-teal", accent === "amber" && "is-amber")}>
      <div className="kpi-card-body">
        <div className="kpi-card-top">
          <div className="kpi-card-copy">
            <span className="kpi-card-label">{label}</span>
            <div className="kpi-card-value">
              <strong className="num">{value}</strong>
              {hint ? <em>{hint}</em> : null}
            </div>
          </div>
          {icon ? <div className="kpi-card-icon">{icon}</div> : null}
        </div>
        {meta ? <p className="kpi-card-meta">{meta}</p> : null}
      </div>
      {children ? <div className="kpi-card-foot">{children}</div> : null}
    </article>
  );
}

export function HostingBattery({
  remaining,
  requested,
  label,
}: {
  remaining: number;
  requested: number;
  label: string;
}) {
  const bars = 6;
  const ratio = requested > 0 ? Math.max(0, Math.min(1, remaining / requested)) : 0;
  const filled = remaining > 0 ? Math.max(1, Math.round(ratio * bars)) : 0;
  return (
    <div className="hosting-battery" aria-label={label} title={label}>
      {Array.from({ length: bars }, (_, index) => (
        <i key={index} className={index < filled ? "on" : undefined} />
      ))}
    </div>
  );
}

export function SegmentedTabs<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T;
  options: Array<{ id: T; label: string }>;
  onChange: (next: T) => void;
}) {
  return (
    <div className="segmented" role="tablist">
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          role="tab"
          aria-selected={value === option.id}
          className={value === option.id ? "is-active" : undefined}
          onClick={() => onChange(option.id)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

export function OperationalPill({ label = "All systems operational" }: { label?: string }) {
  return (
    <span className="ops-pill">
      <span className="ops-pill-dot" aria-hidden />
      {label}
    </span>
  );
}
