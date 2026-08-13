package org.openstreetmap.josm.plugins.betterworkspace.validation;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.tools.I18n;

/**
 * Template for a regular (always-on) BetterWorkspace validation rule.
 *
 * Copy this file, rename it, fill in the constructor strings, implement your
 * logic in visit(), and register the class in BetterWorkspacePlugin with:
 *
 *   OsmValidator.addTest(YourRule.class);
 *
 * Error codes: use a unique integer per rule in the 80000-80099 range for
 * regular rules (80100+ for third-pass rules). Pick one that isn't already
 * used by any other rule in this plugin.
 */
public class ExampleRegularRule extends Test {

    private static final int CODE = 80001;

    public ExampleRegularRule() {
        super(I18n.tr("BW: Example regular rule"),
              I18n.tr("Replace this description with what the rule checks."));
    }

    @Override
    public void visit(Way w) {
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
        // implement if needed, otherwise remove this override
    }

    @Override
    public void visit(Relation r) {
        // implement if needed, otherwise remove this override
    }
}
