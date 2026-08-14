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
 * Flags landuse=residential areas (closed ways or multipolygon relations) that
 * contain more than one node with a place= tag.
 * Ported from MapathonQA-JOSM-plugin's SelectResidentialWithMultiplePlaceNodesAction.
 */
public class ResidentialMultiplePlaceNodes extends BwTest {

    private static final int CODE = 80001;
    private DataSet dataSet;

    public ResidentialMultiplePlaceNodes() {
        super("residential-multiple-place-nodes",
              I18n.tr("BW: Residential with multiple place nodes"),
              I18n.tr("Flags landuse=residential areas that contain more than one node tagged with place=*."));
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
            List<Node> placeNodes = new ArrayList<>();
            for (Node n : dataSet.getNodes()) {
                if (!n.isDeleted() && n.hasKey("place")) placeNodes.add(n);
            }
            for (BwResidentialArea area : areas) {
                int count = 0;
                for (Node p : placeNodes) {
                    if (area.containsNode(p)) {
                        count++;
                        if (count > 1) break;
                    }
                }
                if (count > 1) {
                    errors.add(TestError.builder(this, Severity.WARNING, CODE)
                            .message(I18n.tr("Residential area with multiple place nodes - BetterWorkspace"))
                            .primitives(area.primitive)
                            .build());
                }
            }
        }
        dataSet = null;
        super.endTest();
    }
}
