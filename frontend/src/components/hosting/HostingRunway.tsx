type Props = {
  days: number;
  filled?: number;
  label: string;
};

export function HostingRunway({ days, filled, label }: Props) {
  const count = Math.min(Math.max(days, 1), 31);
  const on = Math.min(filled ?? days, count);
  return (
    <div className="runway" aria-label={label}>
      {Array.from({ length: count }, (_, index) => (
        <i key={index} className={index < on ? "on" : undefined} />
      ))}
    </div>
  );
}
