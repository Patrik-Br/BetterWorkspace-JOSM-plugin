package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.util.WindowGeometry;
import org.openstreetmap.josm.plugins.betterworkspace.validation.BwValidationConfig;
import org.openstreetmap.josm.plugins.betterworkspace.validation.BwValidationConfig.BwRuleInfo;
import org.openstreetmap.josm.tools.I18n;

/**
 * Lets the user individually enable/disable each BetterWorkspace validation rule, grouped into
 * "Regular" (always fast, on by default) and "Possibly slow" (slower geometry checks across many
 * primitives at once, off by default) sections. Each checkbox applies immediately - there's no
 * separate Apply step, so Close is the only button.
 */
public final class ValidationRulesDialog extends ExtendedDialog {

    public ValidationRulesDialog() {
        super(MainApplication.getMainFrame(), I18n.tr("BetterWorkspace Validation Rules"),
                new String[]{I18n.tr("Close")});
        setButtonIcons(new String[]{"ok"});
        setRememberWindowGeometry(getClass().getName() + ".geometry",
                WindowGeometry.centerInWindow(MainApplication.getMainFrame(), new Dimension(480, 420)));

        List<BwRuleInfo> regular = new ArrayList<>();
        List<BwRuleInfo> slow = new ArrayList<>();
        for (BwRuleInfo rule : BwValidationConfig.RULES) {
            (rule.slow() ? slow : regular).add(rule);
        }

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        content.add(sectionHeader(I18n.tr("Regular rules"),
                I18n.tr("Fast checks. Recommend to turn on")));
        for (BwRuleInfo rule : regular) content.add(ruleRow(rule));

        content.add(Box.createVerticalStrut(14));
        content.add(sectionHeader(I18n.tr("Possibly slow rules"),
                I18n.tr("Heavier geometry checks over many objects at once - may take noticeably "
                      + "longer on large downloads or slower machines. Handy for third-pass "
                      + "validation, but not limited to it.")));
        for (BwRuleInfo rule : slow) content.add(ruleRow(rule));

        content.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);
        setContent(wrapper, false);
    }

    private static JPanel sectionHeader(String title, String blurb) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(0f);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setAlignmentX(0f);
        JLabel blurbLabel = new JLabel("<html><body style='width:400px'>" + blurb + "</body></html>");
        blurbLabel.setAlignmentX(0f);
        panel.add(titleLabel);
        panel.add(blurbLabel);
        panel.add(Box.createVerticalStrut(4));
        return panel;
    }

    private static JCheckBox ruleRow(BwRuleInfo rule) {
        JCheckBox box = new JCheckBox(rule.displayName(), BwValidationConfig.isRuleEnabled(rule.id()));
        box.setAlignmentX(0f);
        box.setToolTipText("<html><body style='width:320px'>" + rule.description() + "</body></html>");
        box.addActionListener(e -> BwValidationConfig.setRuleEnabled(rule.id(), box.isSelected()));
        return box;
    }
}
