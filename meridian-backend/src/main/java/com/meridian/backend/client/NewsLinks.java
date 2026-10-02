package com.meridian.backend.client;

import java.net.URI;
import java.net.URISyntaxException;

// Article links are shown as clickable links and images, so only web addresses are let through:
// a "javascript:" or "data:" link from a provider must never reach the page.
final class NewsLinks {

    private NewsLinks() {
    }

    /** The link if it is an absolute http or https address, else null. */
    static String web(String raw) {
        return withScheme(raw, "http", "https");
    }

    /** The link if it is an absolute https address (an http image would be blocked on an https site), else null. */
    static String secure(String raw) {
        return withScheme(raw, "https");
    }

    private static String withScheme(String raw, String... schemes) {
        if (raw == null || raw.isBlank()) return null;
        try {
            URI uri = new URI(raw.trim());
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            for (String scheme : schemes) {
                if (scheme.equalsIgnoreCase(uri.getScheme())) return uri.toString();
            }
            return null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Null for blank text, else the text trimmed. */
    static String text(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}
