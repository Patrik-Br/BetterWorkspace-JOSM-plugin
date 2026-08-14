package org.openstreetmap.josm.plugins.betterworkspace.validation;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.tools.I18n;

/**
 * Template for a regular (fast, on-by-default) BetterWorkspace validation rule.
 *
 * Copy this file, rename it, fill in the constructor strings, implement your logic in visit(),
 * and:
 *   1. Add a BwValidationConfig.BwRuleInfo entry to BwValidationConfig.RULES with a unique id
 *      and slow=false, defaultEnabled=true.
 *   2. Register the class in BetterWorkspacePlugin with: OsmValidator.addTest(YourRule.class);
 *
 * IMPORTANT: every visit() override MUST start with:  if (shouldSkip()) return;
 * That's what lets the user turn this rule off from More Tools -> BetterWorkspace -> Manage
 * validation rules... even though it's registered.
 *
 * Error codes: use a unique integer per rule in the 80000-80099 range for regular rules
 * (80100+ for third-pass rules). Pick one that isn't already used by any other rule here.
 */
public class ExampleRegularRule extends BwTest {

    private static final int CODE = 80001;

    public ExampleRegularRule() {
        super("example-regular-rule",
              I18n.tr("BW: Example regular rule"),
              I18n.tr("Replace this description with what the rule checks."));
    }

    @Override
    public void visit(Way w) {
        if (shouldSkip()) return; // required — do not remove

        // Example pattern — replace with your logic:
        //
        // if (w.hasTag("highway") && !w.hasTag("name")) {
        //     errors.add(TestError.builder(this, Severity.WARNING,
        //                     I18n.tr("Highway is missing a name"))
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
