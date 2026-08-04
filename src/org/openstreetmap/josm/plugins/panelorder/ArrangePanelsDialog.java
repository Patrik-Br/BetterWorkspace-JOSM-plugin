package org.openstreetmap.josm.plugins.panelorder;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.util.WindowGeometry;
import org.openstreetmap.josm.tools.I18n;

/**
 * Dialog for changing the top-to-bottom order of JOSM's docked side panels.
 * Panels can be dragged directly in the list, or moved with the Top/Up/Down/
 * Bottom buttons; Reset restores whatever order the panels were in when JOSM
 * started this session. Applying the new order and persisting it is left to
 * {@link PanelReorderer}.
 */
public class ArrangePanelsDialog extends ExtendedDialog {

    private final DefaultListModel<Entry> model = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(model);

    public ArrangePanelsDialog(MapFrame mapFrame) {
        super(MainApplication.getMainFrame(), I18n.tr("Arrange Side Panels"),
                new String[]{I18n.tr("Apply"), I18n.tr("Cancel")});
        setButtonIcons(new String[]{"ok", "cancel"});
        setRememberWindowGeometry(getClass().getName() + ".geometry",
                WindowGeometry.centerInWindow(MainApplication.getMainFrame(), new Dimension(420, 480)));

        for (ToggleDialog dialog : PanelReorderer.getCurrentOrder(mapFrame)) {
            model.addElement(new Entry(dialog));
        }
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(Math.max(10, model.size()));
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new ReorderHandler());

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JLabel("<html>" + I18n.tr(
                "Drag entries or use the buttons to change the top-to-bottom order<br>of the panels docked on the right side.")
                + "</html>"), BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(320, 340));
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buildButtonColumn(), BorderLayout.EAST);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setContent(panel, false);
    }

    private JPanel buildButtonColumn() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);

        JButton top = new JButton(I18n.tr("Top"));
        JButton up = new JButton(I18n.tr("Up"));
        JButton down = new JButton(I18n.tr("Down"));
        JButton bottom = new JButton(I18n.tr("Bottom"));
        JButton reset = new JButton(I18n.tr("Reset"));
        reset.setToolTipText(I18n.tr("Restore the order JOSM started with in this session"));

        top.addActionListener(e -> moveSelected(Integer.MIN_VALUE));
        up.addActionListener(e -> moveSelected(-1));
        down.addActionListener(e -> moveSelected(1));
        bottom.addActionListener(e -> moveSelected(Integer.MAX_VALUE));
        reset.addActionListener(e -> resetToStartupOrder());

        panel.add(top, c);
        panel.add(up, c);
        panel.add(down, c);
        panel.add(bottom, c);
        c.insets = new Insets(14, 0, 2, 0);
        panel.add(reset, c);
        c.insets = new Insets(0, 0, 0, 0);
        c.weighty = 1.0;
        panel.add(new JPanel(), c);
        return panel;
    }

    private void moveSelected(int delta) {
        int from = list.getSelectedIndex();
        if (from < 0) {
            return;
        }
        int to = delta == Integer.MIN_VALUE ? 0
                : delta == Integer.MAX_VALUE ? model.size() - 1
                : from + delta;
        to = Math.max(0, Math.min(model.size() - 1, to));
        if (to == from) {
            return;
        }
        Entry entry = model.remove(from);
        model.add(to, entry);
        list.setSelectedIndex(to);
        list.ensureIndexIsVisible(to);
    }

    private void resetToStartupOrder() {
        List<String> startupOrder = PanelReorderer.getStartupOrder();
        if (startupOrder == null) {
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            entries.add(model.get(i));
        }
        entries.sort(Comparator.comparingInt(entry -> {
            int index = startupOrder.indexOf(entry.dialog.getClass().getName());
            return index >= 0 ? index : startupOrder.size();
        }));
        model.clear();
        entries.forEach(model::addElement);
    }

    public void showAndApply(MapFrame mapFrame) {
        showDialog();
        if (getValue() != 1) {
            return;
        }
        List<ToggleDialog> order = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            order.add(model.get(i).dialog);
        }
        if (PanelReorderer.applyOrder(mapFrame, order)) {
            PanelReorderer.saveOrder(order);
        }
    }

    private static final class Entry {
        final ToggleDialog dialog;

        Entry(ToggleDialog dialog) {
            this.dialog = dialog;
        }

        @Override
        public String toString() {
            String name = dialog.getName();
            if (name == null || name.isEmpty()) {
                name = dialog.getClass().getSimpleName();
            }
            if (!dialog.isDialogShowing()) {
                return name + " " + I18n.tr("(hidden)");
            }
            if (dialog.isDialogInCollapsedView()) {
                return name + " " + I18n.tr("(collapsed)");
            }
            if (!dialog.isDialogInDefaultView()) {
                return name + " " + I18n.tr("(floating)");
            }
            return name;
        }
    }

    private final class ReorderHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return TransferHandler.MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            int index = list.getSelectedIndex();
            return index < 0 ? null : new StringSelection(Integer.toString(index));
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            JList.DropLocation dropLocation = (JList.DropLocation) support.getDropLocation();
            int to = dropLocation.getIndex();
            int from;
            try {
                from = Integer.parseInt((String) support.getTransferable().getTransferData(DataFlavor.stringFlavor));
            } catch (UnsupportedFlavorException | IOException | NumberFormatException ex) {
                return false;
            }
            if (from < 0 || from >= model.size() || to < 0 || to > model.size()) {
                return false;
            }
            if (to > from) {
                to--;
            }
            if (to == from) {
                return true;
            }
            Entry entry = model.remove(from);
            model.add(to, entry);
            list.setSelectedIndex(to);
            list.ensureIndexIsVisible(to);
            return true;
        }
    }
}
