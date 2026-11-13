const SITE_NAME = "WebAux";
const DEFAULT_DESCRIPTION =
  "WebAux hosts an event website at its own address for ₹500 a day. Build the invitation, collect RSVPs, and keep it live only while you host it.";

type PageSeoOptions = {
  title: string;
  description?: string;
  path?: string;
  image?: string;
  robots?: string;
  type?: "website" | "article";
  noIndex?: boolean;
};

function absoluteUrl(path = "/"): string {
  const origin = typeof window !== "undefined" ? window.location.origin : "https://webaux.app";
  if (!path || path === "/") return `${origin}/`;
  return `${origin}${path.startsWith("/") ? path : `/${path}`}`;
}

function upsertMeta(attr: "name" | "property", key: string, content: string) {
  let node = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`);
  if (!node) {
    node = document.createElement("meta");
    node.setAttribute(attr, key);
    document.head.appendChild(node);
  }
  node.setAttribute("content", content);
}

function upsertLink(rel: string, href: string) {
  let node = document.head.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`);
  if (!node) {
    node = document.createElement("link");
    node.setAttribute("rel", rel);
    document.head.appendChild(node);
  }
  node.setAttribute("href", href);
}

export function applyPageSeo({
  title,
  description = DEFAULT_DESCRIPTION,
  path,
  image = "/web-app-manifest-512x512.png",
  robots,
  type = "website",
  noIndex = false,
}: PageSeoOptions) {
  const fullTitle = title === SITE_NAME ? SITE_NAME : `${title} · ${SITE_NAME}`;
  const url = absoluteUrl(path ?? (typeof window !== "undefined" ? window.location.pathname : "/"));
  const imageUrl = image.startsWith("http") ? image : absoluteUrl(image);
  const robotsValue = robots ?? (noIndex ? "noindex, nofollow" : "index, follow");

  document.title = fullTitle;
  upsertMeta("name", "description", description);
  upsertMeta("name", "robots", robotsValue);
  upsertMeta("name", "googlebot", robotsValue);
  upsertLink("canonical", url);

  upsertMeta("property", "og:site_name", SITE_NAME);
  upsertMeta("property", "og:type", type);
  upsertMeta("property", "og:title", fullTitle);
  upsertMeta("property", "og:description", description);
  upsertMeta("property", "og:url", url);
  upsertMeta("property", "og:image", imageUrl);
  upsertMeta("property", "og:locale", "en_IN");

  upsertMeta("name", "twitter:card", "summary_large_image");
  upsertMeta("name", "twitter:title", fullTitle);
  upsertMeta("name", "twitter:description", description);
  upsertMeta("name", "twitter:image", imageUrl);
}

export { DEFAULT_DESCRIPTION, SITE_NAME };
