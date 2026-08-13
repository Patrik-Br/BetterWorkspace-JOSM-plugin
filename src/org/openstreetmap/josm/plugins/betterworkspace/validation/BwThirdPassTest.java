package org.openstreetmap.josm.plugins.betterworkspace.validation;

import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * Base class for BetterWorkspace third-pass validation rules.
 *
 * Rules that extend this class are always registered in the Validator and appear
 * in its Preferences list, but they only perform actual checks when third-pass
 * mode is enabled via the BetterWorkspace menu toggle. When disabled they return
 * instantly, so regular validation stays fast.
 *
 * How to add a third-pass rule:
 *   1. Create a class that extends BwThirdPassTest
 *   2. Call super(name, description) from your constructor
 *   3. Override visit(Way), visit(Node), and/or visit(Relation)
 *   4. Start every visit() override with: if (shouldSkip()) return;
 *   5. Register via OsmValidator.addTest(YourRule.class) in BetterWorkspacePlugin
 *
 * See ExampleThirdPassRule for a complete template.
 */
public abstract class BwThirdPassTest extends Test {

    private boolean skip;

    protected BwThirdPassTest(String name, String description) {
        super(name, description);
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor); // always initialises the errors list
        skip = !BwValidationConfig.isThirdPassEnabled();
    }

    /**
     * Returns true when third-pass mode is disabled.
     * Call this at the top of every visit() override.
     */
    protected final boolean shouldSkip() {
        return skip;
    }
}
