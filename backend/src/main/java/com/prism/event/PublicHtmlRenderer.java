package com.prism.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PublicHtmlRenderer {

    private final PublicEventService publicEvents;
    private final PrismProperties properties;

    public PublicHtmlRenderer(PublicEventService publicEvents, PrismProperties properties) {
        this.publicEvents = publicEvents;
        this.properties = properties;
    }

    public String render(String slug) {
        Map<String, Object> resolved;
        try {
            resolved = publicEvents.resolve(slug, true);
        } catch (ApiException exception) {
            return document("Page not available", "This page is not available.", "This page is not available.", "#f7f6f3", "#171717", "");
        }
        String availability = String.valueOf(resolved.get("availability"));
        String name = String.valueOf(resolved.get("eventName"));
        if ("SUSPENDED".equals(availability)) {
            return document(name, name + " is paused.", "This site has been paused by WebAux.", "#f4f6f7", "#141618", "");
        }
        if ("EXPIRED".equals(availability)) {
            return document(name, name + " has ended.", "Hosting for this site has ended.", "#f4f6f7", "#141618", "");
        }
        JsonNode configuration = (JsonNode) resolved.get("configuration");
        String background = configuration.path("theme").path("background").asText("#f7f6f3");
        String ink = configuration.path("theme").path("ink").asText("#171717");
        String accent = configuration.path("theme").path("accent").asText("#1b6c80");
        String description = configuration.path("event").path("description").asText(configuration.path("event").path("subtitle").asText(name));
        String canonical = properties.getPublicBaseUrl().replaceAll("/$", "") + "/" + slug;
        String image = firstImage(configuration, canonical);
        JsonNode visual = resolved.get("editorDocument") instanceof JsonNode node ? node : null;
        if (visual != null && visual.path("root").isObject()) {
            String pageDescription = visual.path("page").path("description").asText(description);
            String visualImage = firstVisualImage(visual.path("root"), canonical);
            StringBuilder visualBody = new StringBuilder();
            visualBody.append("<article class=\"site\">");
            appendVisual(visualBody, visual.path("root"));
            visualBody.append("<p class=\"prism-credit\">Made with WebAux</p></article>");
            return document(name, pageDescription, visualBody.toString(), background, ink, meta(name, pageDescription, canonical, visualImage.isBlank() ? image : visualImage, accent));
        }
        StringBuilder body = new StringBuilder();
        body.append("<article class=\"site\">");
        JsonNode elements = configuration.path("canvas").path("elements");
        if (elements.isArray() && !elements.isEmpty()) {
            for (JsonNode element : elements) {
                String type = element.path("type").asText("");
                if ("image".equals(type) || "box".equals(type)) {
                    continue;
                }
                if ("rsvp".equals(type)) {
                    body.append("<section><h2>Reply</h2><p>Open this page in a browser to send a reply.</p></section>");
                    continue;
                }
                if ("button".equals(type)) {
                    String href = element.path("href").asText("");
                    String label = esc(element.path("text").asText(""));
                    if (!href.isBlank()) {
                        body.append("<p><a href=\"").append(esc(href)).append("\">").append(label).append("</a></p>");
                    } else {
                        body.append("<p>").append(label).append("</p>");
                    }
                    continue;
                }
                body.append("<p>").append(esc(element.path("text").asText(""))).append("</p>");
            }
        } else {
            for (JsonNode section : configuration.path("sections")) {
                body.append(sectionHtml(section, configuration));
            }
        }
        body.append("<p class=\"prism-credit\">Made with WebAux</p></article>");
        return document(name, description, body.toString(), background, ink, meta(name, description, canonical, image, accent));
    }

    private void appendVisual(StringBuilder body, JsonNode node) {
        String type = node.path("type").asText("");
        JsonNode props = node.path("props");
        switch (type) {
            case "HEADING" -> {
                int level = Math.min(6, Math.max(1, props.path("level").asInt(2)));
                body.append("<h").append(level).append(">").append(esc(props.path("text").asText(""))).append("</h").append(level).append(">");
            }
            case "PARAGRAPH" -> body.append("<p>").append(esc(props.path("text").asText(""))).append("</p>");
            case "BUTTON" -> {
                String href = props.path("href").asText("");
                String label = esc(props.path("text").asText(""));
                if (href.startsWith("https://") || href.startsWith("http://") || href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("#") || href.startsWith("/")) {
                    body.append("<p><a href=\"").append(esc(href)).append("\">").append(label).append("</a></p>");
                } else if (!label.isBlank()) {
                    body.append("<p>").append(label).append("</p>");
                }
            }
            case "IMAGE" -> {
                String src = props.path("mediaId").asText("");
                if (src.startsWith("/api/v1/public/media/")) {
                    body.append("<img src=\"").append(esc(src)).append("\" alt=\"").append(esc(props.path("alt").asText(""))).append("\">");
                }
            }
            case "QUOTE" -> {
                body.append("<blockquote><p>").append(esc(props.path("text").asText(""))).append("</p>");
                String attribution = props.path("attribution").asText("");
                if (!attribution.isBlank()) {
                    body.append("<cite>").append(esc(attribution)).append("</cite>");
                }
                body.append("</blockquote>");
            }
            case "COUNTDOWN" -> {
                String label = props.path("label").asText("Countdown");
                body.append("<section><h2>").append(esc(label)).append("</h2><p>")
                        .append(esc(props.path("target").asText(""))).append("</p></section>");
            }
            case "FAQ_ACCORDION", "TIMELINE" -> {
                String title = props.path("title").asText("");
                body.append("<section>");
                if (!title.isBlank()) {
                    body.append("<h2>").append(esc(title)).append("</h2>");
                }
                for (JsonNode child : node.path("children")) {
                    appendVisual(body, child);
                }
                body.append("</section>");
            }
            case "FAQ_ITEM" -> body.append("<details><summary>").append(esc(props.path("title").asText("")))
                    .append("</summary><p>").append(esc(props.path("text").asText(""))).append("</p></details>");
            case "TIMELINE_ITEM" -> body.append("<div><strong>").append(esc(props.path("title").asText("")))
                    .append("</strong><p>").append(esc(props.path("text").asText(""))).append("</p></div>");
            default -> {
                for (JsonNode child : node.path("children")) {
                    appendVisual(body, child);
                }
            }
        }
    }

    private String firstVisualImage(JsonNode node, String canonical) {
        if ("IMAGE".equals(node.path("type").asText())) {
            String resolved = resolveImage(node.path("props").path("mediaId").asText(""), canonical);
            if (!resolved.isBlank()) {
                return resolved;
            }
        }
        for (JsonNode child : node.path("children")) {
            String found = firstVisualImage(child, canonical);
            if (!found.isBlank()) {
                return found;
            }
        }
        return "";
    }

    private String sectionHtml(JsonNode section, JsonNode configuration) {
        String type = section.path("type").asText();
        JsonNode properties = section.path("properties");
        JsonNode event = configuration.path("event");
        return switch (type) {
            case "HERO" -> "<header class=\"hero\"><h1>" + esc(properties.path("title").asText(event.path("name").asText()))
                    + "</h1><p>" + esc(properties.path("subtitle").asText(event.path("subtitle").asText())) + "</p></header>";
            case "TEXT_BLOCK" -> "<section><h2>" + esc(properties.path("heading").asText()) + "</h2><p>" + esc(properties.path("body").asText()) + "</p></section>";
            case "EVENT_DETAILS" -> "<section><h2>" + esc(properties.path("heading").asText("Details")) + "</h2><p>"
                    + esc(event.path("startAt").asText()) + "</p><p>" + esc(event.path("venueName").asText()) + "</p><p>"
                    + esc(event.path("venueAddress").asText()) + "</p></section>";
            case "EVENT_SCHEDULE" -> schedule(properties);
            case "LOCATION_MAP" -> "<section><h2>" + esc(properties.path("heading").asText("Place")) + "</h2><p>"
                    + esc(properties.path("note").asText()) + "</p><p>" + esc(event.path("venueAddress").asText()) + "</p></section>";
            case "RSVP_FORM" -> configuration.path("rsvp").path("enabled").asBoolean(false)
                    ? "<section><h2>" + esc(properties.path("heading").asText("Reply")) + "</h2><p>Open this page in a browser to send a reply.</p></section>"
                    : "";
            case "FOOTER" -> "<footer><p>" + esc(properties.path("note").asText()) + "</p></footer>";
            case "COUNTDOWN" -> "<section><h2>" + esc(properties.path("label").asText("Countdown")) + "</h2><p>" + esc(event.path("startAt").asText()) + "</p></section>";
            default -> "";
        };
    }

    private String schedule(JsonNode properties) {
        StringBuilder builder = new StringBuilder("<section><h2>").append(esc(properties.path("heading").asText("Schedule"))).append("</h2><ul>");
        for (JsonNode item : properties.path("items")) {
            builder.append("<li>").append(esc(item.path("time").asText())).append(" ")
                    .append(esc(item.path("title").asText())).append(" — ")
                    .append(esc(item.path("detail").asText())).append("</li>");
        }
        builder.append("</ul></section>");
        return builder.toString();
    }

    private String firstImage(JsonNode configuration, String canonical) {
        for (JsonNode element : configuration.path("canvas").path("elements")) {
            String image = element.path("mediaId").asText("");
            String resolved = resolveImage(image, canonical);
            if (!resolved.isBlank()) {
                return resolved;
            }
        }
        for (JsonNode section : configuration.path("sections")) {
            String image = section.path("properties").path("backgroundImage").asText("");
            String resolved = resolveImage(image, canonical);
            if (!resolved.isBlank()) {
                return resolved;
            }
        }
        return canonical;
    }

    private String resolveImage(String image, String canonical) {
        if (image.isBlank() || image.startsWith("/placeholders/")) {
            return "";
        }
        if (image.startsWith("http")) {
            return image;
        }
        if (image.startsWith("/")) {
            return properties.getPublicBaseUrl().replaceAll("/$", "") + image;
        }
        return properties.getPublicBaseUrl().replaceAll("/$", "") + "/api/v1/public/media/" + image;
    }

    private String meta(String title, String description, String url, String image, String accent) {
        String site = properties.getPublicBaseUrl() == null ? "https://webaux.app" : properties.getPublicBaseUrl().replaceAll("/$", "");
        return "<link rel=\"canonical\" href=\"" + esc(url) + "\">"
                + "<meta name=\"robots\" content=\"index, follow\">"
                + "<meta name=\"author\" content=\"WebAux\">"
                + "<meta property=\"og:site_name\" content=\"WebAux\">"
                + "<meta property=\"og:type\" content=\"website\">"
                + "<meta property=\"og:locale\" content=\"en_IN\">"
                + "<meta property=\"og:title\" content=\"" + esc(title) + "\">"
                + "<meta property=\"og:description\" content=\"" + esc(description) + "\">"
                + "<meta property=\"og:url\" content=\"" + esc(url) + "\">"
                + "<meta property=\"og:image\" content=\"" + esc(image) + "\">"
                + "<meta name=\"twitter:card\" content=\"summary_large_image\">"
                + "<meta name=\"twitter:title\" content=\"" + esc(title) + "\">"
                + "<meta name=\"twitter:description\" content=\"" + esc(description) + "\">"
                + "<meta name=\"twitter:image\" content=\"" + esc(image) + "\">"
                + "<meta name=\"theme-color\" content=\"" + esc(accent) + "\">"
                + "<link rel=\"icon\" href=\"" + esc(site) + "/favicon.ico\">"
                + "<script type=\"application/ld+json\">"
                + "{\"@context\":\"https://schema.org\",\"@type\":\"WebPage\",\"name\":\"" + esc(title) + "\","
                + "\"description\":\"" + esc(description) + "\",\"url\":\"" + esc(url) + "\","
                + "\"isPartOf\":{\"@type\":\"WebSite\",\"name\":\"WebAux\",\"url\":\"" + esc(site) + "/\"},"
                + "\"primaryImageOfPage\":\"" + esc(image) + "\"}"
                + "</script>";
    }

    private String document(String title, String description, String body, String background, String ink, String extraHead) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + esc(title) + "</title>"
                + "<meta name=\"description\" content=\"" + esc(description) + "\">"
                + extraHead
                + "<style>body{margin:0;background:" + background + ";color:" + ink + ";font:18px/1.5 Georgia,serif}"
                + "article{max-width:720px;margin:0 auto;padding:48px 20px}h1{font-size:64px;line-height:.95;letter-spacing:-.03em;margin:0 0 12px}"
                + "h2{font-size:28px;margin:40px 0 8px}p,li{font-size:18px}a{color:inherit}"
                + "p.prism-credit{margin:32px 0 0;font:11px/16px sans-serif;letter-spacing:.01em;text-align:center;opacity:.55}</style>"
                + "</head><body>" + body + "</body></html>";
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
