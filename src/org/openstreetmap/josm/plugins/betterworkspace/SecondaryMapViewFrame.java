package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.data.osm.visitor.paint.PaintColors;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.tools.I18n;

/**
 * A second, view-only map window. It paints layers via the main {@link MapView}'s
 * own {@code paintLayer(Layer, Graphics2D)}, which always uses that MapView's
 * current position/scale/projection - so this window is automatically in sync
 * with the main view without any manual position/zoom tracking code, as long as
 * its canvas stays the same pixel size as the main map view (kept in sync here
 * via a resize listener). Which layers are painted is a separate checkbox list,
 * independent of each layer's shared {@code Layer.isVisible()} - checking a box
 * here never changes what's shown in the main Layers panel, and vice versa.
 */
final class SecondaryMapViewFrame extends JFrame {

    private final Set<Layer> shown = new LinkedHashSet<>();
    private final Map<Layer, JCheckBox> checkboxes = new LinkedHashMap<>();
    private final JPanel checklistPanel = new JPanel();
    private final Canvas canvas = new Canvas();

    private final LayerManager.LayerChangeListener layerChangeListener = new LayerManager.LayerChangeListener() {
        @Override
        public void layerAdded(LayerManager.LayerAddEvent e) {
            addCheckbox(e.getAddedLayer());
        }

        @Override
        public void layerRemoving(LayerManager.LayerRemoveEvent e) {
            removeCheckbox(e.getRemovedLayer());
        }

        @Override
        public void layerOrderChanged(LayerManager.LayerOrderChangeEvent e) {
            rebuildChecklist();
        }
    };

    private final NavigatableComponent.ZoomChangeListener zoomChangeListener = canvas::repaint;
    private final MapView.RepaintListener repaintListener = (tm, x, y, w, h) -> canvas.repaint();

    private final ComponentAdapter mainViewResizeListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            syncCanvasSize();
        }
    };

    SecondaryMapViewFrame() {
        super(I18n.tr("Secondary Map View"));
        // JFrame defaults to HIDE_ON_CLOSE, which would leave this frame non-null but invisible
        // after clicking the window's X - SecondaryMapViewAction's toggle relies on windowClosed
        // firing (which only happens on dispose) to know the window is really gone.
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        checklistPanel.setLayout(new BoxLayout(checklistPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(checklistPanel);
        scroll.setPreferredSize(new Dimension(180, 0));

        JButton alignButton = new JButton(I18n.tr("Align to Main View"));
        alignButton.setToolTipText(I18n.tr(
                "Snap this window so its map canvas sits pixel-adjacent to the main view''s canvas, "
                + "on whichever side this window is currently closer to"));
        alignButton.addActionListener(e -> alignToMainView());

        JPanel westPanel = new JPanel(new BorderLayout());
        westPanel.add(alignButton, BorderLayout.NORTH);
        westPanel.add(scroll, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(westPanel, BorderLayout.WEST);
        add(canvas, BorderLayout.CENTER);

        MainApplication.getLayerManager().addAndFireLayerChangeListener(layerChangeListener);
        NavigatableComponent.addZoomChangeListener(zoomChangeListener);
        MapView mapView = MainApplication.getMap().mapView;
        mapView.addRepaintListener(repaintListener);
        mapView.addComponentListener(mainViewResizeListener);
        syncCanvasSize();
    }

    @Override
    public void dispose() {
        MainApplication.getLayerManager().removeLayerChangeListener(layerChangeListener);
        NavigatableComponent.removeZoomChangeListener(zoomChangeListener);
        if (MainApplication.getMap() != null) {
            MapView mapView = MainApplication.getMap().mapView;
            mapView.removeRepaintListener(repaintListener);
            mapView.removeComponentListener(mainViewResizeListener);
        }
        super.dispose();
    }

    private void syncCanvasSize() {
        if (MainApplication.getMap() == null) {
            return;
        }
        canvas.setPreferredSize(MainApplication.getMap().mapView.getSize());
        pack();
    }

    /**
     * Repositions (and re-syncs the size of) this window so its canvas sits pixel-adjacent to the
     * main view's canvas - fixes both "window edges don't line up with canvas edges" (this window
     * has a sidebar, the main window has its own toolbar/panels of a different width) and "canvas
     * silently drifted out of size-sync" (e.g. this window itself got resized) in one action.
     * Snaps left or right of the main view based on this window's current position, so it matches
     * whichever side the window was already roughly dragged to.
     */
    private void alignToMainView() {
        if (MainApplication.getMap() == null) {
            return;
        }
        syncCanvasSize();
        MapView mapView = MainApplication.getMap().mapView;
        if (!mapView.isShowing() || !canvas.isShowing()) {
            return;
        }
        Point mainViewScreenPos = mapView.getLocationOnScreen();
        Point canvasScreenPos = canvas.getLocationOnScreen();
        Point frameScreenPos = getLocationOnScreen();
        int canvasOffsetX = canvasScreenPos.x - frameScreenPos.x;
        int canvasOffsetY = canvasScreenPos.y - frameScreenPos.y;

        boolean placeOnLeft = getX() < mainViewScreenPos.x;
        int targetCanvasX = placeOnLeft
                ? mainViewScreenPos.x - canvas.getWidth()
                : mainViewScreenPos.x + mapView.getWidth();
        int targetCanvasY = mainViewScreenPos.y;

        setLocation(targetCanvasX - canvasOffsetX, targetCanvasY - canvasOffsetY);
    }

    private void addCheckbox(Layer layer) {
        if (checkboxes.containsKey(layer)) {
            return;
        }
        JCheckBox cb = new JCheckBox(layer.getName(), layer.isVisible());
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (cb.isSelected()) {
            shown.add(layer);
        }
        cb.addActionListener(e -> {
            if (cb.isSelected()) {
                shown.add(layer);
            } else {
                shown.remove(layer);
            }
            canvas.repaint();
        });
        checkboxes.put(layer, cb);
        rebuildChecklist();
    }

    private void removeCheckbox(Layer layer) {
        checkboxes.remove(layer);
        shown.remove(layer);
        rebuildChecklist();
    }

    private void rebuildChecklist() {
        checklistPanel.removeAll();
        for (Layer l : MainApplication.getLayerManager().getLayers()) {
            JCheckBox cb = checkboxes.get(l);
            if (cb != null) {
                checklistPanel.add(cb);
            }
        }
        checklistPanel.add(Box.createVerticalGlue());
        checklistPanel.revalidate();
        checklistPanel.repaint();
    }

    private final class Canvas extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            if (MainApplication.getMap() == null) {
                return;
            }
            MapView mapView = MainApplication.getMap().mapView;
            g.setColor(PaintColors.getBackgroundColor());
            g.fillRect(0, 0, getWidth(), getHeight());
            // getLayers() is ordered topmost-first, so paint back-to-front (bottom layer first).
            List<Layer> layers = MainApplication.getLayerManager().getLayers();
            for (int i = layers.size() - 1; i >= 0; i--) {
                Layer l = layers.get(i);
                if (shown.contains(l)) {
                    mapView.paintLayer(l, (Graphics2D) g);
                }
            }
        }
    }
}
