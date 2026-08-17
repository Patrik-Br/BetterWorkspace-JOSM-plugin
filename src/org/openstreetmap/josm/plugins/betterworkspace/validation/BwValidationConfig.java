package org.openstreetmap.josm.plugins.betterworkspace.validation;

import java.util.List;

import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;

/** Metadata and on/off persistence for each individual BetterWorkspace validation rule. */
public final class BwValidationConfig {

    private static final String PREF_PREFIX = "betterworkspace.rule.";

    /** One BetterWorkspace validation rule, as shown in the "Manage validation rules..." dialog. */
    public record BwRuleInfo(String id, String displayName, String description, boolean slow, boolean defaultEnabled) { }

    /** Every rule the plugin registers, in dialog display order. */
    public static final List<BwRuleInfo> RULES = List.of(
            new BwRuleInfo("residential-multiple-place-nodes",
                    I18n.tr("Residential with multiple place nodes"),
                    I18n.tr("Flags landuse=residential areas that contain more than one node tagged with place=*."),
                    false, false),
            new BwRuleInfo("hamlet-village-mismatch",
                    I18n.tr("Hamlet/village building count mismatch"),
                    I18n.tr("Counts buildings inside each residential area and flags its place=hamlet/village "
                          + "node if the count doesn't match: hamlet expects <15 buildings, village expects >=15."),
                    false, false),
            new BwRuleInfo("highway-classification-mismatch",
                    I18n.tr("Highway classification mismatch"),
                    I18n.tr("Flags highway ways sandwiched between end-to-end connected ways of the same "
                          + "differing classification at both endpoints. E.g. unclassified - path - unclassified "
                          + "will flag the path."),
                    true, false),
            new BwRuleInfo("residential-without-highway",
                    I18n.tr("Residential area without a highway"),
                    I18n.tr("Flags landuse=residential areas with no highway way passing through or touching them."),
                    true, false),
            new BwRuleInfo("overlapping-landuse-areas",
                    I18n.tr("Overlapping landuse areas"),
                    I18n.tr("Flags landuse=* areas whose true extents genuinely overlap, ignoring shared "
                          + "boundaries such as an area that exactly fills another area's hole."),
                    true, false));

    private BwValidationConfig() {}

    public static boolean isRuleEnabled(String ruleId) {
        return Config.getPref().getBoolean(PREF_PREFIX + ruleId + ".enabled", defaultFor(ruleId));
    }

    public static void setRuleEnabled(String ruleId, boolean enabled) {
        Config.getPref().putBoolean(PREF_PREFIX + ruleId + ".enabled", enabled);
    }

    private static boolean defaultFor(String ruleId) {
        for (BwRuleInfo r : RULES) {
            if (r.id().equals(ruleId)) return r.defaultEnabled();
        }
        return true;
    }
}
