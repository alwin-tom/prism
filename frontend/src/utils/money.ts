export function formatPaise(paise: number) {
  const rupees = Math.trunc(paise / 100);
  const remainder = Math.abs(paise % 100);
  const whole = new Intl.NumberFormat("en-IN").format(rupees);
  if (remainder === 0) return `₹${whole}`;
  return `₹${whole}.${String(remainder).padStart(2, "0")}`;
}

export function quoteFromDays(days: number, dailyPaise = 50000) {
  return {
    days,
    dailyRatePaise: dailyPaise,
    amountPaise: days * dailyPaise,
    formatted: formatPaise(days * dailyPaise),
  };
}
