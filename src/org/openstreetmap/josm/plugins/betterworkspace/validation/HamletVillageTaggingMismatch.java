package org.openstreetmap.josm.plugins.betterworkspace.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.I18n;

/**
 * Flags place=hamlet or place=village nodes whose enclosing landuse=residential area's
 * building count doesn't match that classification:
 *   - hamlet expects fewer than 15 buildings
 *   - village expects 15 or more buildings
 *
 * Uses DataSet.searchNodes()/searchWays() (JOSM's spatial index) scoped to each area's
 * bounding box to keep this fast on large downloads.
 * Ported from MapathonQA-JOSM-plugin's SelectHamletVillageTaggingMismatchAction.
 */
public class HamletVillageTaggingMismatch extends BwTest {

    private static final int CODE = 80002;
    private static final int VILLAGE_THRESHOLD = 15;
    private DataSet dataSet;

    public HamletVillageTaggingMismatch() {
        super("hamlet-village-mismatch",
              I18n.tr("BW: Hamlet/village building count mismatch"),
              I18n.tr("Flags place=hamlet or place=village nodes whose enclosing residential area building count "
                    + "disagrees with the classification (hamlet < 15 buildings, village >= 15 buildings)."));
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        dataSet = null;
    }

    @Override
    public void visit(Way w) {
        if (shouldSkip()) return;
        if (dataSet == null) dataSet = w.getDataSet();
    }

    @Override
    public void visit(Node n) {
        if (shouldSkip()) return;
        if (dataSet == null && !n.isIncomplete()) dataSet = n.getDataSet();
    }

    @Override
    public void endTest() {
        if (!shouldSkip() && dataSet != null) {
            List<BwResidentialArea> areas = BwResidentialArea.collectFromDataSet(dataSet);

            Set<Node> placeNodes = new HashSet<>();
            for (Node n : dataSet.getNodes()) {
                if (n.isDeleted()) continue;
                String place = n.get("place");
                if ("hamlet".equals(place) || "village".equals(place)) placeNodes.add(n);
            }

            Set<Way> buildings = new HashSet<>();
            for (Way w : dataSet.getWays()) {
                if (w.isDeleted() || w.isIncomplete()) continue;
                if (w.isClosed() && w.hasKey("building")
                        && !"no".equals(w.get("building"))
                        && !"entrance".equals(w.get("building"))) {
                    buildings.add(w);
                }
            }

            for (BwResidentialArea area : areas) {
                BBox bbox = area.getBBox();

                List<Node> placesInArea = new ArrayList<>();
                for (Node p : dataSet.searchNodes(bbox)) {
                    if (placeNodes.contains(p) && area.containsNode(p)) placesInArea.add(p);
                }
                if (placesInArea.isEmpty()) continue;

                int buildingCount = 0;
                for (Way w : dataSet.searchWays(bbox)) {
                    if (buildings.contains(w) && buildingInArea(area, w)) buildingCount++;
                }

                for (Node p : placesInArea) {
                    boolean isHamlet = "hamlet".equals(p.get("place"));
                    boolean mismatch = isHamlet
                            ? buildingCount >= VILLAGE_THRESHOLD
                            : buildingCount < VILLAGE_THRESHOLD;
                    if (mismatch) {
                        errors.add(TestError.builder(this, Severity.WARNING, CODE)
                                .message(I18n.tr("Hamlet/village building count mismatch - BetterWorkspace"))
                                .primitives(p)
                                .build());
                    }
                }
            }
        }
        dataSet = null;
        super.endTest();
    }

    private static boolean buildingInArea(BwResidentialArea area, Way building) {
        for (Node n : building.getNodes()) {
            if (area.containsNode(n)) return true;
        }
        return false;
    }
}
