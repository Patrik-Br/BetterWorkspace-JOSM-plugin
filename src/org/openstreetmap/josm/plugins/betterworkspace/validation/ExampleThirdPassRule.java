package org.openstreetmap.josm.plugins.betterworkspace.validation;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.tools.I18n;

/**
 * Template for a slow, third-pass (off-by-default) BetterWorkspace validation rule.
 *
 * Copy this file, rename it, fill in the constructor strings, implement your logic in visit(),
 * and:
 *   1. Add a BwValidationConfig.BwRuleInfo entry to BwValidationConfig.RULES with a unique id
 *      and slow=true, defaultEnabled=false.
 *   2. Register the class in BetterWorkspacePlugin with: OsmValidator.addTest(YourRule.class);
 *
 * The user enables it individually from More Tools -> BetterWorkspace -> Manage validation
 * rules..., under the "Third-pass (slow)" group.
 *
 * IMPORTANT: every visit()/endTest() override MUST start with:  if (shouldSkip()) return;
 *
 * Error codes: use a unique integer per rule in the 80100-80199 range for third-pass rules.
 * Pick one that isn't already used by another rule here.
 */
public class ExampleThirdPassRule extends BwTest {

    private static final int CODE = 80104;

    public ExampleThirdPassRule() {
        super("example-third-pass-rule",
              I18n.tr("BW: Example third-pass rule"),
              I18n.tr("Replace this description with what the rule checks. Slow — off by default."));
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
