package com.happyu.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public final class HappyuConfigurable implements SearchableConfigurable {
    private final HappyuSettings settings;
    private JPanel panel;
    private JBCheckBox autoSleep;
    private JSpinner idleMinutes;
    private JBLabel idleLabel;
    private JBCheckBox agentPanel;
    private JBCheckBox agentReading;
    private JBCheckBox autoHideToolbar;

    public HappyuConfigurable(Project project) {
        settings = project.getService(HappyuSettings.class);
    }

    @Override
    public @NotNull String getId() {
        return "com.happyu.settings";
    }

    @Override
    public String getDisplayName() {
        return "happyu";
    }

    @Override
    public JComponent createComponent() {
        if (panel != null) return panel;
        autoSleep = new JBCheckBox("Automatically sleep inactive background tabs");
        idleMinutes = new JSpinner(new SpinnerNumberModel(15, 1, 1440, 1));
        idleLabel = new JBLabel("Idle time (minutes):");
        agentPanel = new JBCheckBox("Show Agent-style conversation panel at the bottom");
        agentReading = new JBCheckBox("Use Agent conversation style for web reading");
        autoHideToolbar = new JBCheckBox("Automatically hide the address toolbar");
        autoSleep.addActionListener(event -> {
            idleMinutes.setEnabled(autoSleep.isSelected());
            idleLabel.setEnabled(autoSleep.isSelected());
        });
        panel = FormBuilder.createFormBuilder()
                .addComponent(autoSleep)
                .addLabeledComponent(idleLabel, idleMinutes)
                .addComponent(new JBLabel("<html>Sleeping tabs reload when opened.<br>"
                        + "Unsaved forms, page state and browsing history may be lost.<br>"
                        + "Background media and uploads are not excluded from automatic sleep.<br>"
                        + "Cookies are not explicitly cleared. The selected tab never sleeps.</html>"))
                .addComponent(agentPanel)
                .addComponent(new JBLabel("<html>Local conversation mockup. Input is not sent to a model.<br>"
                        + "You can also toggle it using the happyu title-bar button.</html>"))
                .addComponent(agentReading)
                .addComponent(new JBLabel("<html>Extract current-page text as an Agent-style response, entirely locally.<br>"
                        + "Updates dynamically; scroll to load more and expand forum replies.<br>"
                        + "Toggle off to return to the live page.</html>"))
                .addComponent(autoHideToolbar)
                .addComponent(new JBLabel("<html>Move the pointer to the browser's top edge to overlay navigation and the URL without resizing the page.<br>"
                        + "The toolbar stays visible while editing the URL or using its popup menus.</html>"))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        if (panel == null) return false;
        HappyuSettings.Options saved = settings.getState();
        return autoSleep.isSelected() != saved.autoSleepEnabled
                || ((Number) idleMinutes.getValue()).intValue() != saved.idleMinutes
                || agentPanel.isSelected() != saved.agentPanelVisible
                || agentReading.isSelected() != saved.agentReadingMode
                || autoHideToolbar.isSelected() != saved.autoHideToolbar;
    }

    @Override
    public void apply() throws ConfigurationException {
        if (panel == null) return;
        try {
            idleMinutes.commitEdit();
        } catch (java.text.ParseException e) {
            throw new ConfigurationException("Enter an idle time between 1 and 1440 minutes.");
        }
        int minutes = ((Number) idleMinutes.getValue()).intValue();
        if (minutes < 1 || minutes > 1440) {
            throw new ConfigurationException("Enter an idle time between 1 and 1440 minutes.");
        }
        settings.update(autoSleep.isSelected(), minutes);
        settings.setAgentPanelVisible(agentPanel.isSelected());
        settings.setAgentReadingMode(agentReading.isSelected());
        settings.setAutoHideToolbar(autoHideToolbar.isSelected());
    }

    @Override
    public void reset() {
        if (panel == null) return;
        HappyuSettings.Options saved = settings.getState();
        autoSleep.setSelected(saved.autoSleepEnabled);
        idleMinutes.setValue(saved.idleMinutes);
        idleMinutes.setEnabled(saved.autoSleepEnabled);
        idleLabel.setEnabled(saved.autoSleepEnabled);
        agentPanel.setSelected(saved.agentPanelVisible);
        agentReading.setSelected(saved.agentReadingMode);
        autoHideToolbar.setSelected(saved.autoHideToolbar);
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        autoSleep = null;
        idleMinutes = null;
        idleLabel = null;
        agentPanel = null;
        agentReading = null;
        autoHideToolbar = null;
    }
}
