package org.openstreetmap.josm.plugins.betterworkspace;

import org.openstreetmap.josm.spi.preferences.Config;

/**
 * Stores the user's HOT Tasking Manager personal API token (JOSM preferences,
 * same storage mechanism JOSM itself uses for the OSM OAuth token). Get it
 * from tasking-manager.hotosm.org - Settings - enable "Expert mode" - "API Key"
 * card. The TM backend expects it as "Authorization: Token &lt;token&gt;"; TM's
 * own copy button copies that whole "Token xxx" string, so pasted values are
 * normalized here to tolerate either form.
 */
final class TmApiToken {

    private static final String PREF_KEY = "betterworkspace.tm.apitoken";

    private TmApiToken() { }

    static String get() {
        return Config.getPref().get(PREF_KEY, "");
    }

    static void set(String rawValue) {
        Config.getPref().put(PREF_KEY, normalize(rawValue));
    }

    /** Value to send as the Authorization header, or null if no token is set. */
    static String authorizationHeader() {
        String token = get();
        return token.isEmpty() ? null : "Token " + token;
    }

    private static String normalize(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.regionMatches(true, 0, "Token ", 0, 6)) {
            value = value.substring(6).trim();
        }
        return value;
    }
}
