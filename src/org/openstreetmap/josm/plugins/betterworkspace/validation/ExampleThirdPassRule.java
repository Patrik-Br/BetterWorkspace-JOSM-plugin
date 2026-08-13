package org.openstreetmap.josm.plugins.betterworkspace.validation;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.tools.I18n;

/**
 * Template for a slow third-pass BetterWorkspace validation rule.
 *
 * This rule only runs checks when third-pass mode is enabled via
 * More Tools → BetterWorkspace → Enable third-pass validation rules.
 * When disabled it returns instantly — no cost to regular validation.
 *
 * Copy this file, rename it, fill in the constructor strings, implement your
 * logic in visit(), and register the class in BetterWorkspacePlugin with:
 *
 *   OsmValidator.addTest(YourRule.class);
 *
 * IMPORTANT: every visit() override MUST start with:  if (shouldSkip()) return;
 *
 * Error codes: use a unique integer per rule in the 80100-80199 range for
 * third-pass rules. Pick one that isn't already used by another rule here.
 */
public class ExampleThirdPassRule extends BwThirdPassTest {

    private static final int CODE = 80101;

    public ExampleThirdPassRule() {
        super(I18n.tr("BW: Example third-pass rule"),
              I18n.tr("Replace this description with what the rule checks. Slow — only runs in third-pass mode."));
    }

    @Override
    public void visit(Way w) {
        if (shouldSkip()) return; // required — do not remove

        // Example pattern — replace with your logic:
        //
        // if (someExpensiveCheck(w)) {
        //     errors.add(TestError.builder(this, Severity.WARNING,
        //                     I18n.tr("Issue description"))
        //             .primitives(w)
        //             .code(CODE)
        //             .build());
        // }
    }

    @Override
    public void visit(Node n) {
        if (shouldSkip()) return; // required — do not remove
        // implement if needed, otherwise remove this override
    }

    @Override
    public void visit(Relation r) {
        if (shouldSkip()) return; // required — do not remove
        // implement if needed, otherwise remove this override
    }
}
