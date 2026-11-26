package com.happyu.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "HappyuSettings", storages = @Storage(value = "happyu-settings.xml", roamingType = RoamingType.DISABLED),
        reloadable = false)
public final class HappyuSettings implements PersistentStateComponent<HappyuSettings.Options> {
    public static final Topic<Listener> TOPIC = Topic.create("happyu settings", Listener.class);
    private final Project project;
    private Options options = new Options();

    public HappyuSettings(Project project) {
        this.project = project;
    }

    @Override
    public synchronized @NotNull Options getState() {
        return copy(options);
    }

    @Override
    public synchronized void loadState(@NotNull Options state) {
        options = copy(state);
    }

    public void update(boolean autoSleepEnabled, int idleMinutes) {
        synchronized (this) {
            int minutes = Math.max(1, Math.min(1440, idleMinutes));
            if (options.autoSleepEnabled == autoSleepEnabled && options.idleMinutes == minutes) return;
            options.autoSleepEnabled = autoSleepEnabled;
            options.idleMinutes = minutes;
        }
        project.getMessageBus().syncPublisher(TOPIC).settingsChanged();
    }

    public void setAgentPanelVisible(boolean visible) {
        synchronized (this) {
            if (options.agentPanelVisible == visible) return;
            options.agentPanelVisible = visible;
        }
        project.getMessageBus().syncPublisher(TOPIC).settingsChanged();
    }

    public void setAgentReadingMode(boolean enabled) {
        synchronized (this) {
            if (options.agentReadingMode == enabled) return;
            options.agentReadingMode = enabled;
        }
        project.getMessageBus().syncPublisher(TOPIC).settingsChanged();
    }

    public void setAutoHideToolbar(boolean enabled) {
        synchronized (this) {
            if (options.autoHideToolbar == enabled) return;
            options.autoHideToolbar = enabled;
        }
        project.getMessageBus().syncPublisher(TOPIC).settingsChanged();
    }

    private static Options copy(Options source) {
        Options result = new Options();
        result.autoSleepEnabled = source.autoSleepEnabled;
        result.idleMinutes = Math.max(1, Math.min(1440, source.idleMinutes));
        result.agentPanelVisible = source.agentPanelVisible;
        result.agentReadingMode = source.agentReadingMode;
        result.autoHideToolbar = source.autoHideToolbar;
        return result;
    }

    public interface Listener {
        void settingsChanged();
    }

    public static final class Options {
        public boolean autoSleepEnabled;
        public int idleMinutes = 15;
        public boolean agentPanelVisible;
        public boolean agentReadingMode;
        public boolean autoHideToolbar;
    }
}
