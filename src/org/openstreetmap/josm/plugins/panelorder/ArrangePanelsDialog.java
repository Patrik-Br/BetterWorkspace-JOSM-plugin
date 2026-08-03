/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.openstreetmap.josm.gui.ExtendedDialog
 *  org.openstreetmap.josm.gui.MainApplication
 *  org.openstreetmap.josm.gui.MapFrame
 *  org.openstreetmap.josm.gui.dialogs.ToggleDialog
 *  org.openstreetmap.josm.gui.util.WindowGeometry
 *  org.openstreetmap.josm.tools.I18n
 */
package org.openstreetmap.josm.plugins.panelorder;

import java.awt.BorderLayout;
import java.awt.Component;
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
import javax.swing.TransferHandler;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.util.WindowGeometry;
import org.openstreetmap.josm.plugins.panelorder.PanelReorderer;
import org.openstreetmap.josm.tools.I18n;

public class ArrangePanelsDialog
extends ExtendedDialog {
    private final DefaultListModel<Entry> model = new DefaultListModel();
    private final JList<Entry> list = new JList<Entry>(this.model);

    public ArrangePanelsDialog(MapFrame mapFrame) {
        super((Component)MainApplication.getMainFrame(), I18n.tr((String)"Arrange Side Panels", (Object[])new Object[0]), new String[]{I18n.tr((String)"Apply", (Object[])new Object[0]), I18n.tr((String)"Cancel", (Object[])new Object[0])});
        this.setButtonIcons(new String[]{"ok", "cancel"});
        this.setRememberWindowGeometry(((Object)((Object)this)).getClass().getName() + ".geometry", WindowGeometry.centerInWindow((Component)MainApplication.getMainFrame(), (Dimension)new Dimension(420, 480)));
        for (ToggleDialog object2 : PanelReorderer.getCurrentOrder(mapFrame)) {
            this.model.addElement(new Entry(object2));
        }
        this.list.setSelectionMode(0);
        this.list.setVisibleRowCount(Math.max(10, this.model.size()));
        this.list.setDragEnabled(true);
        this.list.setDropMode(DropMode.INSERT);
        this.list.setTransferHandler(new ReorderHandler());
        JPanel jPanel = new JPanel(new BorderLayout(10, 10));
        jPanel.add((Component)new JLabel("<html>" + I18n.tr((String)"Drag entries or use the buttons to change the top-to-bottom order<br>of the panels docked on the right side.", (Object[])new Object[0]) + "</html>"), "North");
        JScrollPane jScrollPane = new JScrollPane(this.list);
        jScrollPane.setPreferredSize(new Dimension(320, 340));
        jPanel.add((Component)jScrollPane, "Center");
        jPanel.add((Component)this.buildButtonColumn(), "East");
        jPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        this.setContent(jPanel, false);
    }

    private JPanel buildButtonColumn() {
        JPanel jPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = 2;
        gridBagConstraints.insets = new Insets(2, 0, 2, 0);
        JButton jButton = new JButton(I18n.tr((String)"Top", (Object[])new Object[0]));
        JButton jButton2 = new JButton(I18n.tr((String)"Up", (Object[])new Object[0]));
        JButton jButton3 = new JButton(I18n.tr((String)"Down", (Object[])new Object[0]));
        JButton jButton4 = new JButton(I18n.tr((String)"Bottom", (Object[])new Object[0]));
        JButton jButton5 = new JButton(I18n.tr((String)"Reset", (Object[])new Object[0]));
        jButton5.setToolTipText(I18n.tr((String)"Restore the order JOSM started with in this session", (Object[])new Object[0]));
        jButton.addActionListener(actionEvent -> this.moveSelected(Integer.MIN_VALUE));
        jButton2.addActionListener(actionEvent -> this.moveSelected(-1));
        jButton3.addActionListener(actionEvent -> this.moveSelected(1));
        jButton4.addActionListener(actionEvent -> this.moveSelected(Integer.MAX_VALUE));
        jButton5.addActionListener(actionEvent -> this.resetToStartupOrder());
        jPanel.add((Component)jButton, gridBagConstraints);
        jPanel.add((Component)jButton2, gridBagConstraints);
        jPanel.add((Component)jButton3, gridBagConstraints);
        jPanel.add((Component)jButton4, gridBagConstraints);
        gridBagConstraints.insets = new Insets(14, 0, 2, 0);
        jPanel.add((Component)jButton5, gridBagConstraints);
        gridBagConstraints.insets = new Insets(0, 0, 0, 0);
        gridBagConstraints.weighty = 1.0;
        jPanel.add((Component)new JPanel(), gridBagConstraints);
        return jPanel;
    }

    private void moveSelected(int n) {
        int n2 = this.list.getSelectedIndex();
        if (n2 < 0) {
            return;
        }
        int n3 = n == Integer.MIN_VALUE ? 0 : (n == Integer.MAX_VALUE ? this.model.size() - 1 : n2 + n);
        n3 = Math.max(0, Math.min(this.model.size() - 1, n3));
        if (n3 == n2) {
            return;
        }
        Entry entry = this.model.remove(n2);
        this.model.add(n3, entry);
        this.list.setSelectedIndex(n3);
        this.list.ensureIndexIsVisible(n3);
    }

    private void resetToStartupOrder() {
        List<String> list = PanelReorderer.getStartupOrder();
        if (list == null) {
            return;
        }
        ArrayList<Entry> arrayList = new ArrayList<Entry>();
        for (int i = 0; i < this.model.size(); ++i) {
            arrayList.add(this.model.get(i));
        }
        arrayList.sort(Comparator.comparingInt(entry -> {
            int n = list.indexOf(entry.dialog.getClass().getName());
            return n >= 0 ? n : list.size();
        }));
        this.model.clear();
        arrayList.forEach(this.model::addElement);
    }

    public void showAndApply(MapFrame mapFrame) {
        this.showDialog();
        if (this.getValue() != 1) {
            return;
        }
        ArrayList<ToggleDialog> arrayList = new ArrayList<ToggleDialog>();
        for (int i = 0; i < this.model.size(); ++i) {
            arrayList.add(this.model.get((int)i).dialog);
        }
        if (PanelReorderer.applyOrder(mapFrame, arrayList)) {
            PanelReorderer.saveOrder(arrayList);
        }
    }

    private static final class Entry {
        final ToggleDialog dialog;

        Entry(ToggleDialog toggleDialog) {
            this.dialog = toggleDialog;
        }

        public String toString() {
            String string = this.dialog.getName();
            if (string == null || string.isEmpty()) {
                string = this.dialog.getClass().getSimpleName();
            }
            if (!this.dialog.isDialogShowing()) {
                return string + " " + I18n.tr((String)"(hidden)", (Object[])new Object[0]);
            }
            if (this.dialog.isDialogInCollapsedView()) {
                return string + " " + I18n.tr((String)"(collapsed)", (Object[])new Object[0]);
            }
            if (!this.dialog.isDialogInDefaultView()) {
                return string + " " + I18n.tr((String)"(floating)", (Object[])new Object[0]);
            }
            return string;
        }
    }

    private final class ReorderHandler
    extends TransferHandler {
        private ReorderHandler() {
        }

        @Override
        public int getSourceActions(JComponent jComponent) {
            return 2;
        }

        @Override
        protected Transferable createTransferable(JComponent jComponent) {
            int n = ArrangePanelsDialog.this.list.getSelectedIndex();
            return n < 0 ? null : new StringSelection(Integer.toString(n));
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport transferSupport) {
            return transferSupport.isDrop() && transferSupport.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport transferSupport) {
            int n;
            if (!this.canImport(transferSupport)) {
                return false;
            }
            JList.DropLocation dropLocation = (JList.DropLocation)transferSupport.getDropLocation();
            int n2 = dropLocation.getIndex();
            try {
                n = Integer.parseInt((String)transferSupport.getTransferable().getTransferData(DataFlavor.stringFlavor));
            }
            catch (UnsupportedFlavorException | IOException | NumberFormatException exception) {
                return false;
            }
            if (n < 0 || n >= ArrangePanelsDialog.this.model.size() || n2 < 0 || n2 > ArrangePanelsDialog.this.model.size()) {
                return false;
            }
            if (n2 > n) {
                --n2;
            }
            if (n2 == n) {
                return true;
            }
            Entry entry = ArrangePanelsDialog.this.model.remove(n);
            ArrangePanelsDialog.this.model.add(n2, entry);
            ArrangePanelsDialog.this.list.setSelectedIndex(n2);
            ArrangePanelsDialog.this.list.ensureIndexIsVisible(n2);
            return true;
        }
    }
}

