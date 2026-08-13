package org.openstreetmap.josm.plugins.betterworkspace.validation;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.I18n;

/**
 * Flags a highway way whose two endpoints each connect end-to-end to other
 * highway(s) of one single, consistent, differing classification, AND that
 * differing classification is the same at both ends — e.g. a short
 * "unclassified" segment sandwiched between two "path" segments.
 *
 * O(H^2) over all highway ways — runs as a third-pass rule only.
 * Ported from MapathonQA-JOSM-plugin's SelectHighwayClassificationMismatchAction.
 */
public class HighwayClassificationMismatch extends BwThirdPassTest {

    private static final int CODE = 80101;
    private final List<Way> highways = new ArrayList<>();

    public HighwayClassificationMismatch() {
        super(I18n.tr("BW: Highway classification mismatch"),
              I18n.tr("Flags highway ways sandwiched between end-to-end connected ways of the same "
                    + "differing classification at both endpoints. Only runs in third-pass mode."));
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        highways.clear();
    }

    @Override
    public void visit(Way w) {
        if (shouldSkip()) return;
        if (!w.isIncomplete() && w.hasKey("highway")) highways.add(w);
    }

    @Override
    public void endTest() {
        if (!shouldSkip()) {
            for (Way w : highways) {
                String cls = w.get("highway");
                if (cls == null) continue;
                List<Node> nodes = w.getNodes();
                if (nodes.size() < 2) continue;
                Node first = nodes.get(0);
                Node last  = nodes.get(nodes.size() - 1);

                String diffAtFirst = findDifferingNeighborClass(w, cls, first);
                String diffAtLast  = findDifferingNeighborClass(w, cls, last);

                if (diffAtFirst != null && diffAtLast != null && diffAtFirst.equals(diffAtLast)) {
                    errors.add(TestError.builder(this, Severity.WARNING, CODE)
                            .message(I18n.tr("Highway classification mismatch - BetterWorkspace"))
                            .primitives(w)
                            .build());
                }
            }
        }
        highways.clear();
        super.endTest();
    }

    /**
     * Among other highway ways that connect to {@code endpoint} via their own first/last node
     * (a true end-to-end junction, not merely crossing) and whose highway= differs from
     * {@code wClass}, returns that differing value only if every such neighbor agrees on the
     * same one — returns null if there are none or if they disagree (ambiguous).
     */
    private String findDifferingNeighborClass(Way w, String wClass, Node endpoint) {
        String result = null;
        for (Way other : highways) {
            if (other == w) continue;
            List<Node> otherNodes = other.getNodes();
            if (otherNodes.size() < 2) continue;
            Node otherFirst = otherNodes.get(0);
            Node otherLast  = otherNodes.get(otherNodes.size() - 1);
            if (otherFirst != endpoint && otherLast != endpoint) continue;

            String otherClass = other.get("highway");
            if (otherClass == null || otherClass.equals(wClass)) continue;

            if (result == null) {
                result = otherClass;
            } else if (!result.equals(otherClass)) {
                return null;
            }
        }
        return result;
    }
}
