package com.prism.creation;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Points empty reply CTAs at {@code #rsvp} so the public visual page can open the guest form.
 */
public final class ReplyButtonEnricher {

    private static final Pattern REPLY = Pattern.compile(
            "\\b(rsvp|reply|respond|let us know|send your reply|send a reply)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private ReplyButtonEnricher() {
    }

    public static ObjectNode apply(ObjectNode document) {
        if (document == null) {
            return null;
        }
        ObjectNode copy = document.deepCopy();
        if (copy.path("root") instanceof ObjectNode root) {
            fill(root);
        }
        return copy;
    }

    private static void fill(ObjectNode node) {
        if ("BUTTON".equals(node.path("type").asText(""))) {
            ObjectNode props = node.with("props");
            String href = props.path("href").asText("").trim();
            String text = props.path("text").asText("");
            if (href.isBlank() && REPLY.matcher(text).find()) {
                props.put("href", "#rsvp");
            } else if ("#reply".equals(href.toLowerCase(Locale.ROOT))) {
                props.put("href", "#rsvp");
            }
        }
        for (int i = 0; i < node.path("children").size(); i++) {
            if (node.path("children").get(i) instanceof ObjectNode child) {
                fill(child);
            }
        }
    }
}
