package org.openstreetmap.josm.plugins.betterworkspace.validation;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.I18n;

/**
 * Flags building=* areas (closed ways or multipolygon relations) whose true, hole-subtracted
 * extent genuinely overlaps the boundary of a landuse=residential area (closed way or
 * multipolygon relation) - not merely sits inside or outside one, which is the normal case for
 * almost every building and would make for a useless, constantly-firing check.
 *
 * <p>Reuses {@link BwLanduseArea}'s hole-aware geometry (see {@link OverlappingLanduseAreas},
 * which checks landuse-vs-landuse the same way): a building entirely inside a hole cut into a
 * residential multipolygon (e.g. a park excluded from it) is correctly NOT flagged, and a
 * building whose wall merely reuses part of the residential boundary (shared nodes) is not
 * mistaken for a crossing.
 *
 * <p>O(buildings x residential areas), bbox-filtered - runs as a third-pass rule only.
 */
public class BuildingOverlappingResidentialArea extends BwTest {

    private static final int CODE = 80105;
    private DataSet dataSet;

    public BuildingOverlappingResidentialArea() {
        super("building-overlaps-residential-landuse",
              I18n.tr("BW: Building overlapping residential landuse"),
              I18n.tr("Flags building=* areas whose true extent genuinely crosses the boundary of a "
                    + "landuse=residential area, rather than sitting entirely inside or outside it. "
                    + "Only runs in third-pass mode."));
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
    public void endTest() {
        if (!shouldSkip() && dataSet != null) {
            List<BwLanduseArea> residentialAreas = new ArrayList<>();
            for (BwLanduseArea a : BwLanduseArea.collectFromDataSet(dataSet)) {
                if ("residential".equals(a.tagValue)) residentialAreas.add(a);
            }
            List<BwLanduseArea> buildings = new ArrayList<>();
            for (BwLanduseArea b : BwLanduseArea.collectBuildings(dataSet)) {
                // Matches the building=no/entrance exclusion used elsewhere (HamletVillageTaggingMismatch) -
                // both are used on ways that aren't actually a building's footprint.
                if (!"no".equals(b.tagValue) && !"entrance".equals(b.tagValue)) buildings.add(b);
            }

            for (BwLanduseArea building : buildings) {
                for (BwLanduseArea residential : residentialAreas) {
                    if (!building.getBBox().intersects(residential.getBBox())) continue;
                    // Deliberately NOT using strictlyContainsAnyVertexOf here (unlike
                    // OverlappingLanduseAreas): a building fully inside a residential area is the
                    // normal, expected case, not an error. A shape can't have some vertices
                    // inside and some outside another without an edge crossing its boundary
                    // somewhere, so strictlyCrosses alone already catches a genuine straddle
                    // without flagging a building that's simply, entirely inside.
                    if (building.strictlyCrosses(residential)) {
                        errors.add(TestError.builder(this, Severity.WARNING, CODE)
                                .message(I18n.tr("Building overlapping residential landuse - BetterWorkspace"))
                                .primitives(building.primitive, residential.primitive)
                                .build());
                    }
                }
            }
        }
        dataSet = null;
        super.endTest();
    }
}
