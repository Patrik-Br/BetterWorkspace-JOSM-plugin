package org.openstreetmap.josm.plugins.betterworkspace.validation;

import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * Base class for BetterWorkspace validation rules. Each rule has its own on/off preference,
 * toggled individually from More Tools -&gt; BetterWorkspace -&gt; Manage validation rules...,
 * grouped there as "regular" (always fast) or "third-pass" (slower geometry checks, off by
 * default) per the {@code slow} flag on its {@link BwValidationConfig.BwRuleInfo} entry.
 *
 * Rules stay registered in JOSM's own Validator too (so they still show up in Preferences -&gt;
 * Validator), but skip all work whenever their individual rule is disabled.
 *
 * How to add a rule:
 *   1. Add a BwValidationConfig.BwRuleInfo entry to BwValidationConfig.RULES with a unique id.
 *   2. Create a class that extends BwTest, passing that same id to the constructor.
 *   3. Override visit(Way), visit(Node), and/or visit(Relation) - and endTest() if it needs one.
 *   4. Start every visit()/endTest() override with: if (shouldSkip()) return;
 *   5. Register via OsmValidator.addTest(YourRule.class) in BetterWorkspacePlugin
 *
 * See ExampleRegularRule / ExampleThirdPassRule for complete templates.
 */
public abstract class BwTest extends Test {

    private final String ruleId;
    private boolean skip;

    protected BwTest(String ruleId, String name, String description) {
        super(name, description);
        this.ruleId = ruleId;
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor); // always initialises the errors list
        skip = !BwValidationConfig.isRuleEnabled(ruleId);
    }

    /**
     * Returns true when this rule is currently disabled.
     * Call this at the top of every visit()/endTest() override.
     */
    protected final boolean shouldSkip() {
        return skip;
    }
}
