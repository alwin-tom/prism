import { useEffect, useState } from "react";
import QRCode from "qrcode";
import { Button } from "../ui";

export function InviteCard({ name, slug, when, venue }: { name: string; slug: string; when?: string; venue?: string }) {
  const url = `${window.location.origin}/${slug}`;
  const [svg, setSvg] = useState("");
  useEffect(() => {
    let live = true;
    void QRCode.toString(url, { type: "svg", margin: 1, width: 168, color: { dark: "#172B35", light: "#ffffff" } }).then((markup) => {
      if (live) setSvg(markup);
    }).catch(() => {
      if (live) setSvg("");
    });
    return () => {
      live = false;
    };
  }, [url]);

  function printCard() {
    const popup = window.open("", "_blank", "noopener,width=480,height=720");
    if (!popup) return;
    const whenLine = readable(when) ? `<p>${escapeHtml(readable(when))}</p>` : "";
    const venueLine = venue ? `<p>${escapeHtml(venue)}</p>` : "";
    popup.document.write(`<!doctype html><title>${escapeHtml(name)}</title><body style="margin:0;font-family:Inter,sans-serif;color:#172b35"><article style="max-width:360px;margin:32px auto;padding:28px;border:1px solid #e5ecec;border-radius:16px;text-align:center"><p style="letter-spacing:.08em;text-transform:uppercase;font-size:12px;color:#087f80">You are invited</p><h1 style="font-size:28px;margin:8px 0">${escapeHtml(name)}</h1>${whenLine}${venueLine}<div style="margin:16px auto;width:168px">${svg}</div><p style="word-break:break-all">${escapeHtml(url)}</p></article><script>window.onload=()=>window.print()<\/script></body>`);
    popup.document.close();
  }

  return (
    <article className="invite-card">
      <p className="invite-kicker">Invite</p>
      <h2>{name}</h2>
      {readable(when) ? <p>{readable(when)}</p> : null}
      {venue ? <p>{venue}</p> : null}
      {svg ? <div className="invite-qr" dangerouslySetInnerHTML={{ __html: svg }} /> : <p className="id">{url}</p>}
      <p className="id">{url}</p>
      <Button type="button" tone="secondary" onClick={printCard}>Print badge</Button>
    </article>
  );
}

function readable(value?: string) {
  if (!value) return "";
  if (!/^\d{4}-\d{2}-\d{2}T/.test(value)) return value;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString("en-IN", { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" });
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[char] ?? char));
}
