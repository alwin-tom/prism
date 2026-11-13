package com.prism.seo;

import com.prism.event.EventRepository;
import com.prism.event.EventSite;
import com.prism.hosting.HostingService;
import com.prism.shared.config.PrismProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
public class SeoController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final PrismProperties properties;
    private final EventRepository events;
    private final HostingService hosting;

    public SeoController(PrismProperties properties, EventRepository events, HostingService hosting) {
        this.properties = properties;
        this.events = events;
        this.hosting = hosting;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        String base = baseUrl();
        return """
                # WebAux
                User-agent: *
                Allow: /
                Allow: /login
                Allow: /register
                Disallow: /app
                Disallow: /app/
                Disallow: /admin
                Disallow: /admin/
                Disallow: /staff
                Disallow: /api/
                Disallow: /render/

                Sitemap: %s/sitemap.xml
                """.formatted(base);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        String base = baseUrl();
        Instant now = Instant.now();
        String today = DAY.format(now);
        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(xml, base + "/", today, "daily", "1.0");
        url(xml, base + "/login", today, "weekly", "0.8");
        url(xml, base + "/register", today, "weekly", "0.8");

        List<EventSite> published = events.findAllByStatus("PUBLISHED");
        List<EventSite> live = new ArrayList<>();
        for (EventSite event : published) {
            if (event.publishedRevisionId != null && hosting.covers(event.id, now)) {
                live.add(event);
            }
        }
        live.sort((a, b) -> b.updatedAt.compareTo(a.updatedAt));
        for (EventSite event : live) {
            url(xml, base + "/" + event.slug, DAY.format(event.updatedAt), "weekly", "0.6");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private String baseUrl() {
        String base = properties.getPublicBaseUrl() == null ? "https://webaux.app" : properties.getPublicBaseUrl().trim();
        return base.replaceAll("/$", "");
    }

    private static void url(StringBuilder xml, String loc, String lastmod, String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(esc(loc)).append("</loc>\n");
        xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
