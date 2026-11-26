package com.happyu.toolwindow;

import com.happyu.browser.HappyuBrowserPanel;
import com.happyu.state.HappyuTabsState;
import com.happyu.settings.HappyuSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class HappyuToolWindowFactory implements ToolWindowFactory, DumbAware {
    private static final String ID = "happyu";
    private static final Key<HappyuBrowserPanel> BROWSER_KEY = Key.create("happyu.browser.panel");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentManager manager = toolWindow.getContentManager();
        HappyuTabsState.Snapshot saved = project.getService(HappyuTabsState.class).getState();
        boolean[] restoring = {true};
        ContentManagerListener listener = new ContentManagerListener() {
            @Override
            public void contentAdded(@NotNull ContentManagerEvent event) {
                if (!restoring[0]) saveTabs(project, manager);
            }

            @Override
            public void contentRemoved(@NotNull ContentManagerEvent event) {
                if (!restoring[0]) saveTabs(project, manager);
            }

            @Override
            public void selectionChanged(@NotNull ContentManagerEvent event) {
                if (!restoring[0]) {
                    startSelected(manager);
                    saveSelection(project, manager);
                }
            }
        };
        manager.addContentManagerListener(listener);
        Disposer.register(manager, () -> manager.removeContentManagerListener(listener));
        toolWindow.setToHideOnEmptyContent(false);
        toolWindow.setContentUiType(ToolWindowContentUiType.TABBED, null);
        // Keep title actions and their layout space when the mouse leaves the tool window.
        toolWindow.getComponent().putClientProperty(ToolWindowContentUi.DONT_HIDE_TOOLBAR_IN_HEADER, true);
        toolWindow.setTitleActions(List.of(new DumbAwareAction(
                "New Tab", "Open a new happyu browser tab", AllIcons.General.Add) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                addTab(project, toolWindow, HappyuBrowserPanel.HOME_URL);
            }
        }, new DumbAwareToggleAction("Agent Conversation Panel", "Show or hide the Agent-style conversation panel",
                IconLoader.getIcon("/icons/agent.svg", HappyuToolWindowFactory.class)) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent event) {
                return project.getService(HappyuSettings.class).getState().agentPanelVisible;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent event, boolean selected) {
                project.getService(HappyuSettings.class).setAgentPanelVisible(selected);
            }
        }, new DumbAwareToggleAction("Agent Reading Mode", "Read web pages in Agent conversation style",
                IconLoader.getIcon("/icons/reading.svg", HappyuToolWindowFactory.class)) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent event) {
                return project.getService(HappyuSettings.class).getState().agentReadingMode;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent event, boolean selected) {
                project.getService(HappyuSettings.class).setAgentReadingMode(selected);
            }
        }));
        if (saved.initialized) {
            for (HappyuTabsState.Tab tab : saved.tabs) addTab(project, toolWindow, tab, false);
            if (saved.selectedIndex >= 0) manager.setSelectedContent(manager.getContent(saved.selectedIndex));
        } else {
            addTab(project, toolWindow, new HappyuTabsState.Tab(), false);
        }
        restoring[0] = false;
        startSelected(manager);
        saveTabs(project, manager);
        HappyuTabScroller.install(manager);
        new HappyuTabSleepController(project, manager);
    }

    public static void show(Project project, Consumer<HappyuBrowserPanel> onSelected) {
        if (project.isDisposed()) {
            return;
        }
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (toolWindow == null) {
            return;
        }
        toolWindow.activate(() -> {
            if (project.isDisposed()) {
                return;
            }
            ContentManager manager = toolWindow.getContentManager();
            if (manager.getContentCount() == 0) {
                addTab(project, toolWindow, HappyuBrowserPanel.HOME_URL);
            }
            Content selected = manager.getSelectedContent();
            if (selected != null) {
                HappyuBrowserPanel panel = selected.getUserData(BROWSER_KEY);
                if (panel != null) {
                    onSelected.accept(panel);
                }
            }
        });
    }

    private static void addTab(Project project, ToolWindow toolWindow, String url) {
        addTab(project, toolWindow, new HappyuTabsState.Tab(url, "New Tab", true), true);
    }

    private static void addTab(Project project, ToolWindow toolWindow, HappyuTabsState.Tab tab, boolean select) {
        if (project.isDisposed()) {
            return;
        }
        ContentManager manager = toolWindow.getContentManager();
        Content content = manager.getFactory().createContent(new JPanel(), "New Tab", false);
        setTitle(content, tab.title);
        HappyuBrowserPanel panel = new HappyuBrowserPanel(project, tab.url, tab.imagesBlocked, tab.sleeping, tab.zoomPercent,
                target -> ToolWindowManager.getInstance(project).invokeLater(
                        () -> addTab(project, toolWindow, target)),
                title -> {
                    if (setTitle(content, title)) saveTab(project, manager, content);
                }, () -> {
            refreshTabTitle(content);
            saveTab(project, manager, content);
        });
        content.setComponent(panel.getComponent());
        content.setPreferredFocusableComponent(panel.getPreferredFocusedComponent());
        content.setCloseable(true);
        content.putUserData(BROWSER_KEY, panel);
        refreshTabTitle(content);
        content.setDisposer(panel);
        manager.addContent(content);
        if (select) manager.setSelectedContent(content, true);
    }

    private static boolean setTitle(Content content, String title) {
        String label = title.isBlank() ? "happyu" : title.strip();
        if (label.equals(content.getDescription())) return false;
        content.setDescription(label);
        refreshTabTitle(content);
        return true;
    }

    private static void refreshTabTitle(Content content) {
        String title = content.getDescription();
        String label = title.length() > 32 ? title.substring(0, 32) + "…" : title;
        HappyuBrowserPanel panel = getBrowserPanel(content);
        if (panel != null && panel.isSleeping()) label = "[Sleeping] " + label;
        if (!label.equals(content.getDisplayName())) content.setDisplayName(label);
    }

    public static @Nullable HappyuBrowserPanel getBrowserPanel(@Nullable Content content) {
        return content == null ? null : content.getUserData(BROWSER_KEY);
    }

    public static boolean canSleepTab(@Nullable Content content) {
        HappyuBrowserPanel panel = getBrowserPanel(content);
        ContentManager manager = content == null ? null : content.getManager();
        return manager != null && !manager.isDisposed() && content != manager.getSelectedContent()
                && panel != null && panel.isBrowserStarted() && !panel.isSleeping();
    }

    public static boolean sleepTab(@Nullable Content content) {
        return canSleepTab(content) && getBrowserPanel(content).sleep();
    }

    public static @Nullable Content findContextTab(AnActionEvent event) {
        Component component = event.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        while (component != null) {
            if (component instanceof BaseLabel label && label.getContent() != null) {
                Content content = label.getContent();
                return getBrowserPanel(content) == null ? null : content;
            }
            component = component.getParent();
        }
        ContentManager manager = event.getData(PlatformDataKeys.CONTENT_MANAGER);
        Content content = manager == null ? null : manager.getSelectedContent();
        return getBrowserPanel(content) == null ? null : content;
    }

    private static void startSelected(ContentManager manager) {
        Content selected = manager.getSelectedContent();
        HappyuBrowserPanel panel = selected == null ? null : selected.getUserData(BROWSER_KEY);
        if (panel != null) panel.ensureBrowserCreated();
    }

    private static void saveTabs(Project project, ContentManager manager) {
        if (project.isDisposed() || manager.isDisposed()) return;
        List<HappyuTabsState.Tab> tabs = new ArrayList<>();
        int selectedIndex = -1;
        for (Content content : manager.getContents()) {
            HappyuBrowserPanel panel = content.getUserData(BROWSER_KEY);
            if (panel != null) {
                if (content == manager.getSelectedContent()) selectedIndex = tabs.size();
                tabs.add(new HappyuTabsState.Tab(panel.getCurrentUrl(), content.getDescription(), panel.isImagesBlocked(),
                        panel.isSleeping(), panel.getZoomPercent()));
            }
        }
        project.getService(HappyuTabsState.class).update(tabs, selectedIndex);
    }

    private static void saveTab(Project project, ContentManager manager, Content content) {
        if (project.isDisposed() || manager.isDisposed()) return;
        int index = manager.getIndexOfContent(content);
        HappyuBrowserPanel panel = content.getUserData(BROWSER_KEY);
        if (index >= 0 && panel != null) {
            project.getService(HappyuTabsState.class).updateTab(index,
                    panel.getCurrentUrl(), content.getDescription(), panel.isImagesBlocked(), panel.isSleeping(), panel.getZoomPercent());
        }
    }

    private static void saveSelection(Project project, ContentManager manager) {
        if (project.isDisposed() || manager.isDisposed()) return;
        Content selected = manager.getSelectedContent();
        project.getService(HappyuTabsState.class).updateSelection(
                selected == null ? -1 : manager.getIndexOfContent(selected));
    }
}
