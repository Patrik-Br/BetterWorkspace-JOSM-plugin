package org.openstreetmap.josm.plugins.betterworkspace.validation;

import java.util.List;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.I18n;

/**
 * Flags pairs of landuse=* areas (any value, closed ways or multipolygon relations) whose
 * true, hole-subtracted extents genuinely overlap.
 *
 * <p>Areas that merely touch - sharing a boundary edge or node, including the common pattern
 * where one area's outline is reused as another area's inner (hole) ring - are NOT flagged;
 * only areas with a real overlapping interior are. This replaces the mapcss
 * {@code *[landuse] ⧉ *[landuse]} rule, whose built-in JOSM overlap operator ignores holes and
 * false-positives on exactly that hole-reuse pattern (see {@link BwLanduseArea}).
 *
 * <p>O(n^2) over all landuse areas, bbox-filtered - runs as a third-pass rule only.
 */
public class OverlappingLanduseAreas extends BwTest {

    private static final int CODE = 80103;
    private DataSet dataSet;

    public OverlappingLanduseAreas() {
        super("overlapping-landuse-areas",
              I18n.tr("BW: Overlapping landuse areas"),
              I18n.tr("Flags landuse=* areas whose true extents genuinely overlap, ignoring shared "
                    + "boundaries such as an area that exactly fills another area's hole. Only runs in third-pass mode."));
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
            List<BwLanduseArea> areas = BwLanduseArea.collectFromDataSet(dataSet);
            for (int i = 0; i < areas.size(); i++) {
                BwLanduseArea a = areas.get(i);
                for (int j = i + 1; j < areas.size(); j++) {
                    BwLanduseArea b = areas.get(j);
                    if (!a.getBBox().intersects(b.getBBox())) continue;
                    if (a.strictlyContainsAnyVertexOf(b) || b.strictlyContainsAnyVertexOf(a)
                            || a.strictlyCrosses(b)) {
                        errors.add(TestError.builder(this, Severity.WARNING, CODE)
                                .message(I18n.tr("Overlapping landuse areas - BetterWorkspace"))
                                .primitives(a.primitive, b.primitive)
                                .build());
                    }
                }
            }
        }
        dataSet = null;
        super.endTest();
    }
}
