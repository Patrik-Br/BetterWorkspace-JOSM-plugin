package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedList;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryLayerInfo;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;

/**
 * Lets the user preview a TMS imagery layer for the current JOSM session only.
 * Unlike JOSM's own "Imagery -&gt; Add..." dialog, the resulting layer is never
 * registered with {@link ImageryLayerInfo#instance} (the imagery.xml-backed,
 * persisted list) - it only lives in the layer manager, so it behaves like any
 * other layer: it disappears when removed or JOSM closes, nothing is written to
 * preferences - unless "Pin to my imagery list" is checked, which additionally
 * calls {@link ImageryLayerInfo#addLayer(ImageryInfo)} (which itself both adds
 * and saves) so it also shows up under the normal Imagery menu next time.
 */
final class QuickTmsDialog extends ExtendedDialog {

    private static final int MAX_HISTORY = 10;
    /** In-memory only, by design - not saved to preferences, cleared on JOSM restart. */
    private static final LinkedList<HistoryEntry> HISTORY = new LinkedList<>();

    private final JTextField nameField = new JTextField(30);
    private final JComboBox<String> urlCombo = new JComboBox<>(historyUrls());
    private final JSpinner minZoomSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 25, 1));
    private final JSpinner maxZoomSpinner = new JSpinner(new SpinnerNumberModel(22, 0, 25, 1));
    private final JCheckBox pinCheckBox = new JCheckBox(
            I18n.tr("Pin to my imagery list (also add permanently under the Imagery menu)"));
    private final JLabel warningLabel = new JLabel(" ");

    QuickTmsDialog() {
        super(MainApplication.getMainFrame(), I18n.tr("Quick TMS"),
                new String[]{I18n.tr("Add Layer"), I18n.tr("Cancel")});
        setButtonIcons(new String[]{"ok", "cancel"});

        urlCombo.setEditable(true);
        urlCombo.setPrototypeDisplayValue("https://example.com/{z}/{x}/{y}.png");
        if (!HISTORY.isEmpty()) {
            nameField.setText(HISTORY.getFirst().name);
            urlCombo.setSelectedItem(HISTORY.getFirst().url);
        }
        urlCombo.addActionListener(e -> {
            String url = getUrlText();
            for (HistoryEntry h : HISTORY) {
                if (h.url.equals(url)) {
                    if (nameField.getText().trim().isEmpty()) {
                        nameField.setText(h.name);
                    }
                    break;
                }
            }
        });

        DocumentListener validateOnEdit = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateValidation();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateValidation();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateValidation();
            }
        };
        nameField.getDocument().addDocumentListener(validateOnEdit);
        ((JTextField) urlCombo.getEditor().getEditorComponent()).getDocument()
                .addDocumentListener(validateOnEdit);

        warningLabel.setForeground(Color.RED);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel(I18n.tr("TMS URL template:")), c);
        c.gridx = 1;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(urlCombo, c);

        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel(I18n.tr("Name (optional):")), c);
        c.gridx = 1;
        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(nameField, c);

        c.gridx = 1;
        c.gridy = 2;
        form.add(warningLabel, c);

        c.gridx = 0;
        c.gridy = 3;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel(I18n.tr("Zoom:")), c);
        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        zoomPanel.add(new JLabel(I18n.tr("Min")));
        zoomPanel.add(minZoomSpinner);
        zoomPanel.add(new JLabel(I18n.tr("Max")));
        zoomPanel.add(maxZoomSpinner);
        c.gridx = 1;
        c.gridy = 3;
        form.add(zoomPanel, c);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        form.add(pinCheckBox, c);

        setContent(form, false);
    }

    @Override
    public void setupDialog() {
        super.setupDialog();
        // buttons (incl. the OK/"Add Layer" button at index 0) only exist once
        // super.setupDialog() has built them - this is the first point we can
        // disable it for the initial (empty-fields) state.
        updateValidation();
    }

    private String getUrlText() {
        Object item = urlCombo.getEditor().getItem();
        return item == null ? "" : item.toString();
    }

    private void updateValidation() {
        String url = getUrlText().trim();
        boolean hasZoom = url.contains("{zoom}") || url.contains("{z}");
        boolean hasX = url.contains("{x}");
        boolean hasY = url.contains("{y}");
        boolean urlOk = hasZoom && hasX && hasY;

        warningLabel.setText(url.isEmpty() || urlOk
                ? " "
                : I18n.tr("URL is missing required placeholders:") + " {zoom}/{z}, {x}, {y}");

        if (buttons != null && !buttons.isEmpty()) {
            buttons.get(0).setEnabled(urlOk);
        }
    }

    void showAndCreate() {
        showDialog();
        if (getValue() != 1) {
            return;
        }
        String url = getUrlText().trim();
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = url;
        }
        int minZoom = (Integer) minZoomSpinner.getValue();
        int maxZoom = (Integer) maxZoomSpinner.getValue();
        boolean pin = pinCheckBox.isSelected();

        try {
            ImageryInfo info = new ImageryInfo(name, url);
            info.setImageryType(ImageryInfo.ImageryType.TMS);
            info.setDefaultMinZoom(minZoom);
            info.setDefaultMaxZoom(maxZoom);
            if (MainApplication.getMap() != null) {
                // A generic TMS URL has no known coverage area, so JOSM's own "Zoom to
                // layer" would otherwise zoom out to the whole world - use the current
                // view instead, since that's presumably roughly where this imagery is.
                Bounds view = MainApplication.getMap().mapView.getRealBounds();
                info.setBounds(new ImageryInfo.ImageryBounds(view.encodeAsString(","), ","));
            }

            ImageryLayer layer = ImageryLayer.create(info);
            MainApplication.getLayerManager().addLayer(layer);

            if (pin) {
                ImageryLayerInfo.addLayer(info);
            }
            addToHistory(name, url);
        } catch (RuntimeException ex) {
            Logging.warn(ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                    I18n.tr("Could not create the TMS layer:") + "\n" + msg,
                    "BetterWorkspace", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String[] historyUrls() {
        String[] arr = new String[HISTORY.size()];
        int i = 0;
        for (HistoryEntry h : HISTORY) {
            arr[i++] = h.url;
        }
        return arr;
    }

    private static void addToHistory(String name, String url) {
        HISTORY.removeIf(h -> h.url.equals(url));
        HISTORY.addFirst(new HistoryEntry(name, url));
        while (HISTORY.size() > MAX_HISTORY) {
            HISTORY.removeLast();
        }
    }

    private static final class HistoryEntry {
        final String name;
        final String url;

        HistoryEntry(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
