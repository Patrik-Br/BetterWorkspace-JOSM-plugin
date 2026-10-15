package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.Component;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.SystemOfMeasurement;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapStatus;
import org.openstreetmap.josm.gui.widgets.ImageLabel;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;

/**
 * Adds an "area" field to JOSM's bottom status bar, right after the distance field, showing the
 * area of the current selection - so the order reads latitude, longitude, heading, angle,
 * distance, area, name.
 *
 * <p>The area is computed exactly like the measurement plugin's "Selection Area" (its
 * {@code MeasurementDialog.refresh} and {@code MeasurementLayer.calcX/calcY}), so both always
 * show the same number: a shoelace sum over each selected closed way's nodes, projected with a
 * 6,367 km sphere, halved and made absolute per way; with no ways and no nodes selected, the sum
 * of {@link Geometry#multipolygonArea} over selected multipolygon relations. Formatted the same
 * way too ({@code #0.000}, via the current system of measurement).
 *
 * <p>MapStatus has no extension point for extra fields; its labels are plain children in a
 * GridBagLayout with relative positioning, so inserting right after its private
 * {@code distText} child places the new field between distance and name.
 */
final class StatusBarArea {

    private static final double EARTH_RADIUS = 6367000.0;

    private static ImageLabel areaText;
    private static DataSelectionListener selectionListener;
    private static DataSetListenerAdapter dataListener;
    private static SystemOfMeasurement.SoMChangeListener somListener;

    private StatusBarArea() {
    }

    static void install(MapFrame mapFrame) {
        uninstall();
        if (mapFrame == null || mapFrame.statusLine == null) {
            return;
        }
        MapStatus status = mapFrame.statusLine;
        int index = indexAfterDistText(status);
        if (index < 0) {
            Logging.warn("BetterWorkspace: status bar distance field not found - area field not added");
            return;
        }
        areaText = new ImageLabel("area", I18n.tr("The area of the selected closed ways or multipolygons. (BetterWorkspace)"),
                14, MapStatus.PROP_BACKGROUND_COLOR.get()); // fits e.g. "72343.690 km²" - wider than distance's 10, area texts run longer
        areaText.setForeground(MapStatus.PROP_FOREGROUND_COLOR.get());
        status.add(areaText, GBC.std().insets(3, 0, 0, 0), index);
        status.revalidate();

        selectionListener = e -> refresh(e.getSelection());
        dataListener = new DataSetListenerAdapter(e -> refreshFromActiveDataSet());
        somListener = (oldSoM, newSoM) -> refreshFromActiveDataSet();
        SelectionEventManager.getInstance().addSelectionListenerForEdt(selectionListener);
        DatasetEventManager.getInstance().addDatasetListener(dataListener, DatasetEventManager.FireMode.IN_EDT_CONSOLIDATED);
        SystemOfMeasurement.addSoMChangeListener(somListener);
        refreshFromActiveDataSet();
    }

    static void uninstall() {
        if (selectionListener != null) {
            SelectionEventManager.getInstance().removeSelectionListener(selectionListener);
            selectionListener = null;
        }
        if (dataListener != null) {
            DatasetEventManager.getInstance().removeDatasetListener(dataListener);
            dataListener = null;
        }
        if (somListener != null) {
            SystemOfMeasurement.removeSoMChangeListener(somListener);
            somListener = null;
        }
        areaText = null;
    }

    private static int indexAfterDistText(MapStatus status) {
        try {
            Field f = MapStatus.class.getDeclaredField("distText");
            f.setAccessible(true);
            Object dist = f.get(status);
            Component[] children = status.getComponents();
            for (int i = 0; i < children.length; i++) {
                if (children[i] == dist) {
                    return i + 1;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Logging.debug(e);
        }
        return -1;
    }

    private static void refreshFromActiveDataSet() {
        DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
        refresh(ds == null ? Collections.emptyList() : ds.getAllSelected());
    }

    private static void refresh(Collection<? extends OsmPrimitive> selection) {
        if (areaText == null) {
            return;
        }
        double area = selectionArea(selection);
        areaText.setText(area > 0
                ? SystemOfMeasurement.getSystemOfMeasurement().getAreaText(area, new DecimalFormat("#0.000"), 0.001)
                : "--");
    }

    /** Same rules as the measurement plugin's "Selection Area" - see the class comment. */
    static double selectionArea(Collection<? extends OsmPrimitive> selection) {
        List<Way> ways = selection.stream().filter(Way.class::isInstance).map(Way.class::cast).toList();
        if (ways.isEmpty()) {
            if (selection.stream().anyMatch(Node.class::isInstance)) {
                return 0;
            }
            double area = 0;
            for (OsmPrimitive p : selection) {
                if (p instanceof Relation r && r.isMultipolygon()) {
                    area += Geometry.multipolygonArea(r);
                }
            }
            return area;
        }
        double area = 0;
        for (Way w : ways) {
            List<Node> nodes = w.getNodes();
            if (nodes.isEmpty()) {
                continue;
            }
            double wayArea = 0;
            Node prev = null;
            for (Node n : nodes) {
                if (prev != null && prev.isLatLonKnown() && n.isLatLonKnown()) {
                    wayArea += calcX(n) * calcY(prev) - calcY(n) * calcX(prev);
                }
                prev = n;
            }
            if (prev.equals(nodes.get(0))) {
                area += Math.abs(wayArea / 2);
            }
        }
        return area;
    }

    private static double calcX(ILatLon p) {
        return p.lat() * Math.PI * EARTH_RADIUS / 180;
    }

    private static double calcY(ILatLon p) {
        return p.lon() * 111125.11347447896 * Math.cos(p.lat() * Math.PI / 180); // = PI * EARTH_RADIUS / 180, as the measurement plugin has it
    }
}
