package org.openstreetmap.josm.plugins.betterworkspace.validation;

import org.openstreetmap.josm.spi.preferences.Config;

/** Persists the on/off state for BetterWorkspace third-pass validation rules. */
public final class BwValidationConfig {

    private static final String PREF_KEY = "betterworkspace.thirdpass.enabled";

    private BwValidationConfig() {}

    public static boolean isThirdPassEnabled() {
        return Config.getPref().getBoolean(PREF_KEY, false);
    }

    public static void setThirdPassEnabled(boolean enabled) {
        Config.getPref().putBoolean(PREF_KEY, enabled);
    }
}
