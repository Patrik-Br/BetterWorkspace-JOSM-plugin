package org.openstreetmap.josm.plugins.betterworkspace.validation;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.I18n;

/**
 * Flags landuse=residential areas (closed ways or multipolygon relations) that have
 * no highway way passing through or even touching them — checked both by node containment
 * and by segment/boundary intersection.
 *
 * O(R x H x N) — runs as a third-pass rule only.
 * Ported from MapathonQA-JOSM-plugin's SelectResidentialWithoutHighwayAction.
 */
public class ResidentialWithoutHighway extends BwTest {

    private static final int CODE = 80102;
    private final List<Way> highways = new ArrayList<>();
    private DataSet dataSet;

    public ResidentialWithoutHighway() {
        super("residential-without-highway",
              I18n.tr("BW: Residential area without a highway"),
              I18n.tr("Flags landuse=residential areas with no highway way passing through or touching them. "
                    + "Only runs in third-pass mode."));
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        highways.clear();
        dataSet = null;
    }

    @Override
    public void visit(Way w) {
        if (shouldSkip()) return;
        if (dataSet == null) dataSet = w.getDataSet();
        if (!w.isIncomplete() && w.hasKey("highway")) highways.add(w);
    }

    @Override
    public void endTest() {
        if (!shouldSkip() && dataSet != null) {
            List<BwResidentialArea> areas = BwResidentialArea.collectFromDataSet(dataSet);
            for (BwResidentialArea area : areas) {
                boolean hasHighway = false;
                outer:
                for (Way hw : highways) {
                    List<Node> nodes = hw.getNodes();
                    for (Node node : nodes) {
                        if (area.containsNode(node)) { hasHighway = true; break outer; }
                    }
                    for (int i = 0; i < nodes.size() - 1; i++) {
                        if (area.intersectsSegment(nodes.get(i), nodes.get(i + 1))) {
                            hasHighway = true; break outer;
                        }
                    }
                }
                if (!hasHighway) {
                    errors.add(TestError.builder(this, Severity.WARNING, CODE)
                            .message(I18n.tr("Residential area without a highway - BetterWorkspace"))
                            .primitives(area.primitive)
                            .build());
                }
            }
        }
        highways.clear();
        dataSet = null;
        super.endTest();
    }
}
