package com.happyu.state;

import com.happyu.browser.HappyuBrowserPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service(Service.Level.PROJECT)
@State(name = "HappyuTabs", storages = @Storage(value = "happyu-tabs.xml", roamingType = RoamingType.DISABLED),
        reloadable = false)
public final class HappyuTabsState implements PersistentStateComponent<HappyuTabsState.Snapshot> {
    private Snapshot snapshot = new Snapshot();
    private volatile boolean closing;

    public HappyuTabsState(Project project) {
        ApplicationManager.getApplication().getMessageBus().connect(project)
                .subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
                    @Override
                    public void projectClosing(@NotNull Project closingProject) {
                        // Saving can fail and closing can still be cancelled after
                        // projectClosingBeforeSave. Freeze only once closing is committed.
                        if (closingProject == project) closing = true;
                    }
                });
    }

    @Override
    public synchronized @NotNull Snapshot getState() {
        return copy(snapshot);
    }

    @Override
    public synchronized void loadState(@NotNull Snapshot state) {
        snapshot = copy(state);
        closing = false;
    }

    public synchronized void update(List<Tab> tabs, int selectedIndex) {
        // Project shutdown disposes contents as well; those removals are not user-closed tabs.
        if (closing) return;
        Snapshot next = new Snapshot();
        next.initialized = true;
        next.tabs = tabs;
        next.selectedIndex = selectedIndex;
        snapshot = copy(next);
    }

    public synchronized void updateTab(int index, String url, String title, boolean imagesBlocked, boolean sleeping,
                                       int zoomPercent) {
        if (closing || index < 0 || index >= snapshot.tabs.size()) return;
        zoomPercent = HappyuBrowserPanel.normalizeZoomPercent(zoomPercent);
        Tab tab = snapshot.tabs.get(index);
        if (Objects.equals(tab.url, url) && Objects.equals(tab.title, title)
                && tab.imagesBlocked == imagesBlocked && tab.sleeping == sleeping && tab.zoomPercent == zoomPercent)
            return;
        // Keep serialization snapshots detached, but avoid copying every other tab on each event.
        snapshot.tabs.set(index, new Tab(url, title, imagesBlocked, sleeping, zoomPercent));
    }

    public synchronized void updateSelection(int selectedIndex) {
        if (closing) return;
        snapshot.selectedIndex = snapshot.tabs.isEmpty() ? -1
                : Math.max(0, Math.min(selectedIndex, snapshot.tabs.size() - 1));
    }

    private static Snapshot copy(Snapshot source) {
        Snapshot result = new Snapshot();
        result.initialized = source.initialized;
        if (source.tabs != null) {
            for (Tab tab : source.tabs) {
                if (tab != null)
                    result.tabs.add(new Tab(tab.url, tab.title, tab.imagesBlocked, tab.sleeping, tab.zoomPercent));
            }
        }
        result.selectedIndex = result.tabs.isEmpty() ? -1
                : Math.max(0, Math.min(source.selectedIndex, result.tabs.size() - 1));
        return result;
    }

    public static final class Snapshot {
        public boolean initialized;
        public List<Tab> tabs = new ArrayList<>();
        public int selectedIndex = -1;
    }

    public static final class Tab {
        public String url = HappyuBrowserPanel.HOME_URL;
        public String title = "New Tab";
        public boolean imagesBlocked = true;
        public boolean sleeping;
        public int zoomPercent = HappyuBrowserPanel.DEFAULT_ZOOM_PERCENT;

        public Tab() {
        }

        public Tab(String url, String title, boolean imagesBlocked) {
            this(url, title, imagesBlocked, false);
        }

        public Tab(String url, String title, boolean imagesBlocked, boolean sleeping) {
            this(url, title, imagesBlocked, sleeping, HappyuBrowserPanel.DEFAULT_ZOOM_PERCENT);
        }

        public Tab(String url, String title, boolean imagesBlocked, boolean sleeping, int zoomPercent) {
            this.url = url == null || url.isBlank() ? HappyuBrowserPanel.HOME_URL : url;
            this.title = title == null || title.isBlank() ? "New Tab" : title;
            this.imagesBlocked = imagesBlocked;
            this.sleeping = sleeping;
            this.zoomPercent = HappyuBrowserPanel.normalizeZoomPercent(zoomPercent);
        }
    }
}
