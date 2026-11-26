package com.happyu.toolwindow;

import com.happyu.browser.HappyuBrowserPanel;
import com.happyu.state.HappyuTabsState;
import com.happyu.settings.HappyuSettings;
import com.happyu.action.SleepHappyuTabAction;
import com.happyu.ui.HappyuAgentPanel;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.ui.UIUtil;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.BorderLayout;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class HappyuToolWindowFactoryTest extends LightPlatformTestCase {
    private ContentManager manager;
    private final List<AnAction> titleActions = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Registry.get("ide.browser.jcef.enabled").setValue(false, getTestRootDisposable());
        getProject().getService(HappyuTabsState.class).loadState(new HappyuTabsState.Snapshot());
        getProject().getService(HappyuSettings.class).loadState(new HappyuSettings.Options());
        createWindow();
    }

    private void createWindow() {
        manager = ContentFactory.getInstance().createContentManager(true, getProject());
        Disposer.register(getTestRootDisposable(), manager);
        JPanel windowComponent = new JPanel();
        ToolWindow window = (ToolWindow) Proxy.newProxyInstance(ToolWindow.class.getClassLoader(),
                new Class<?>[]{ToolWindow.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getContentManager" -> manager;
                    case "getComponent" -> windowComponent;
                    case "setTitleActions" -> {
                        for (Object action : (List<?>) args[0]) titleActions.add((AnAction) action);
                        yield null;
                    }
                    case "setContentUiType", "setToHideOnEmptyContent" -> null;
                    default -> throw new AssertionError("Unexpected tool window call: " + method.getName());
                });
        new HappyuToolWindowFactory().createToolWindowContent(getProject(), window);
    }

    public void testNewTabsHaveIndependentBrowserPanelsAndImageSwitches() {
        Content first = manager.getSelectedContent();
        assertNotNull(first);
        assertEquals(1, manager.getContentCount());
        assertTrue(first.isCloseable());

        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        Content second = manager.getSelectedContent();
        assertNotNull(second);
        assertEquals(2, manager.getContentCount());
        assertNotSame(first.getDisposer(), second.getDisposer());

        HappyuBrowserPanel secondPanel = (HappyuBrowserPanel) second.getDisposer();
        assertNotNull(secondPanel);
        secondPanel.toggleImages();
        assertTrue(hasButton(first.getComponent(), "Back"));
        assertTrue(hasButton(first.getComponent(), "Forward"));
        assertTrue(hasButton(first.getComponent(), "Reload"));
        assertTrue(hasButton(first.getComponent(), "Images Off"));
        assertTrue(hasButton(second.getComponent(), "Images On"));
    }

    public void testAgentPanelToggleUpdatesRestoredTabsWithoutStartingBackgroundBrowsers() {
        HappyuTabsState.Snapshot saved = savedTabs();
        saved.tabs = List.of(new HappyuTabsState.Tab(), new HappyuTabsState.Tab());
        saved.selectedIndex = 0;
        restore(saved);
        ToggleAction toggle = (ToggleAction) titleActions.get(1);
        assertFalse(toggle.isSelected(TestActionEvent.createTestEvent()));
        assertNull(agentPanel(0));
        assertNull(agentPanel(1));
        assertFalse(panel(1).isBrowserStarted());
        toggle.setSelected(TestActionEvent.createTestEvent(), true);
        assertTrue(agentPanel(0).isVisible());
        assertTrue(agentPanel(1).isVisible());
        assertFalse(panel(1).isBrowserStarted());
        HappyuAgentPanel original = agentPanel(0);
        toggle.setSelected(TestActionEvent.createTestEvent(), false);
        assertFalse(original.isVisible());
        assertFalse(agentPanel(1).isVisible());
        toggle.setSelected(TestActionEvent.createTestEvent(), true);
        assertSame(original, agentPanel(0));
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        assertTrue(agentPanel(2).isVisible());
    }

    private HappyuAgentPanel agentPanel(int index) {
        JPanel root = (JPanel) panel(index).getComponent();
        return (HappyuAgentPanel) ((BorderLayout) root.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
    }

    public void testReadingModeActionPersistsIndependentlyAndLeavesLazyBrowsersUnloaded() throws Exception {
        HappyuTabsState.Snapshot saved = savedTabs();
        saved.tabs = List.of(new HappyuTabsState.Tab(), new HappyuTabsState.Tab());
        saved.selectedIndex = 0;
        restore(saved);
        HappyuSettings settings = getProject().getService(HappyuSettings.class);
        ToggleAction reading = (ToggleAction) titleActions.get(2);
        reading.setSelected(TestActionEvent.createTestEvent(), true);
        assertTrue(reading.isSelected(TestActionEvent.createTestEvent()));
        assertTrue(settings.getState().agentReadingMode);
        assertFalse(settings.getState().agentPanelVisible);
        assertFalse(panel(1).isBrowserStarted());
        Disposer.dispose(manager);
        titleActions.clear();
        createWindow();
        assertTrue(((ToggleAction) titleActions.get(2)).isSelected(TestActionEvent.createTestEvent()));
        assertFalse(panel(1).isBrowserStarted());
        assertNull(agentPanel(0));
        var script = HappyuBrowserPanel.class.getDeclaredField("contentModeScript");
        script.setAccessible(true);
        assertTrue(((String) script.get(panel(1))).contains("Agent · 阅读"));
        ((ToggleAction) titleActions.get(2)).setSelected(TestActionEvent.createTestEvent(), false);
        assertTrue(((String) script.get(panel(1))).endsWith("window.__happyuAgentReading?.dispose();"));
    }

    public void testClosingAllTabsDisposesPanelsAndNewTabStillWorks() {
        Content first = manager.getSelectedContent();
        assertNotNull(first);
        HappyuBrowserPanel panel = (HappyuBrowserPanel) first.getDisposer();
        assertNotNull(panel);
        assertTrue(manager.removeContent(first, true));
        assertEquals(0, manager.getContentCount());
        assertTrue(Disposer.isDisposed(panel));
        assertTrue(savedTabs().tabs.isEmpty());

        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        assertEquals(1, manager.getContentCount());
        assertNotSame(panel, manager.getSelectedContent().getDisposer());
    }

    public void testRestoresSerializedTabsAndStartsOnlySelectedBrowser() {
        HappyuTabsState.Snapshot state = new HappyuTabsState.Snapshot();
        state.initialized = true;
        state.tabs = List.of(new HappyuTabsState.Tab("https://example.com/?q=a&b=c#123", "First", false),
                new HappyuTabsState.Tab("https://other.example/path", "第二页", true),
                new HappyuTabsState.Tab("https://last.example/", "Last", false));
        state.selectedIndex = 1;
        // Exercise the actual XML bean serializer used by PersistentStateComponent.
        restore(XmlSerializer.deserialize(XmlSerializer.serialize(state), HappyuTabsState.Snapshot.class));
        assertEquals(3, manager.getContentCount());
        assertEquals(manager.getContent(1), manager.getSelectedContent());
        assertEquals("First", manager.getContent(0).getDisplayName());
        assertEquals("第二页", manager.getContent(1).getDisplayName());
        assertEquals(state.tabs.get(0).url, panel(0).getCurrentUrl());
        assertFalse(panel(0).isImagesBlocked());
        assertTrue(panel(1).isImagesBlocked());
        assertFalse(panel(0).isBrowserStarted());
        assertTrue(panel(1).isBrowserStarted());
        assertFalse(panel(2).isBrowserStarted());
        manager.setSelectedContent(manager.getContent(2));
        assertTrue(panel(2).isBrowserStarted());
        assertFalse(panel(0).isBrowserStarted());
        assertEquals(2, savedTabs().selectedIndex);
    }

    public void testSavesNavigationImageStateAndRemovedTabs() throws Exception {
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        HappyuBrowserPanel selected = panel(1);
        selected.toggleImages();
        // Simulate a URL delivered by a main-frame navigation callback, on the EDT.
        var recordUrl = HappyuBrowserPanel.class.getDeclaredMethod("recordUrl", String.class);
        recordUrl.setAccessible(true);
        recordUrl.invoke(selected, "https://example.com/new?q=C%2B%2B#123");
        assertEquals("https://example.com/new?q=C%2B%2B#123", savedTabs().tabs.get(1).url);
        assertFalse(savedTabs().tabs.get(1).imagesBlocked);
        assertEquals(1, savedTabs().selectedIndex);
        recordUrl.invoke(selected, "about:blank");
        assertEquals("https://example.com/new?q=C%2B%2B#123", savedTabs().tabs.get(1).url);
        assertTrue(manager.removeContent(manager.getContent(0), true));
        assertEquals(1, savedTabs().tabs.size());
        assertEquals(selected.getCurrentUrl(), savedTabs().tabs.get(0).url);
        assertEquals(0, savedTabs().selectedIndex);
    }

    public void testZoomPreferencesPersistIndependentlyAndSurviveSleepAndRestore() throws Exception {
        panel(0).setZoomPercent(80);
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        assertEquals(100, panel(1).getZoomPercent());
        panel(1).setZoomPercent(150);
        var record = HappyuBrowserPanel.class.getDeclaredMethod("recordZoomPercent", int.class);
        record.setAccessible(true);
        record.invoke(panel(1), 133);
        assertTrue(HappyuToolWindowFactory.sleepTab(manager.getContent(0)));
        assertEquals(80, savedTabs().tabs.get(0).zoomPercent);
        assertEquals(133, savedTabs().tabs.get(1).zoomPercent);
        HappyuTabsState.Snapshot saved = savedTabs();
        HappyuTabsState.Snapshot serialized = XmlSerializer.deserialize(XmlSerializer.serialize(saved),
                HappyuTabsState.Snapshot.class);
        saved.tabs.get(0).zoomPercent = 200;
        assertEquals(80, savedTabs().tabs.get(0).zoomPercent);
        restore(serialized);
        assertEquals(80, panel(0).getZoomPercent());
        assertTrue(panel(0).isSleeping());
        assertFalse(panel(0).isBrowserStarted());
        assertEquals(133, panel(1).getZoomPercent());
        manager.setSelectedContent(manager.getContent(0));
        assertFalse(panel(0).isSleeping());
        assertEquals(80, panel(0).getZoomPercent());
        assertEquals(80, savedTabs().tabs.get(0).zoomPercent);
        assertEquals(133, savedTabs().tabs.get(1).zoomPercent);
    }

    public void testMissingAndInvalidSavedZoomUseDefault() {
        HappyuTabsState.Snapshot saved = savedTabs();
        var oldTabXml = new org.jdom.Element("Tab").addContent(new org.jdom.Element("option")
                .setAttribute("name", "url").setAttribute("value", "https://example.com/legacy"));
        saved.tabs = List.of(XmlSerializer.deserialize(oldTabXml, HappyuTabsState.Tab.class));
        restore(saved);
        assertEquals(100, panel(0).getZoomPercent());
        assertEquals("https://example.com/legacy", panel(0).getCurrentUrl());
        HappyuTabsState.Snapshot invalid = savedTabs();
        invalid.tabs.get(0).zoomPercent = -50;
        restore(invalid);
        assertEquals(100, panel(0).getZoomPercent());
        assertEquals(100, savedTabs().tabs.get(0).zoomPercent);
    }

    public void testFrequentSingleTabUpdatesPreserveOtherTabsAndDetachedSnapshots() throws Exception {
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        HappyuTabsState.Snapshot before = savedTabs();
        var recordUrl = HappyuBrowserPanel.class.getDeclaredMethod("recordUrl", String.class);
        recordUrl.setAccessible(true);
        var titleChanged = HappyuBrowserPanel.class.getDeclaredField("titleChanged");
        titleChanged.setAccessible(true);
        @SuppressWarnings("unchecked")
        Consumer<String> title = (Consumer<String>) titleChanged.get(panel(1));
        for (int i = 0; i < 100; i++) {
            title.accept("Page " + i);
            title.accept("Page " + i);
            recordUrl.invoke(panel(1), "https://example.com/page/" + i);
        }
        panel(1).toggleImages();
        manager.setSelectedContent(manager.getContent(0));
        HappyuTabsState.Snapshot after = savedTabs();
        assertEquals(0, after.selectedIndex);
        assertEquals("Page 99", after.tabs.get(1).title);
        assertEquals("https://example.com/page/99", after.tabs.get(1).url);
        assertFalse(after.tabs.get(1).imagesBlocked);
        assertEquals(before.tabs.get(0).url, after.tabs.get(0).url);
        assertEquals(before.tabs.get(0).title, after.tabs.get(0).title);
        assertEquals("New Tab", before.tabs.get(1).title);
        assertEquals(HappyuBrowserPanel.HOME_URL, before.tabs.get(1).url);
        assertTrue(before.tabs.get(1).imagesBlocked);
        assertEquals(1, before.selectedIndex);
        // A caller mutating a returned serialization bean cannot alter live state.
        after.tabs.get(1).url = "https://changed.example/";
        assertEquals("https://example.com/page/99", savedTabs().tabs.get(1).url);
        assertTrue(manager.removeContent(manager.getContent(0), true));
        title.accept("After reorder");
        assertEquals("After reorder", savedTabs().tabs.get(0).title);
        assertEquals("https://example.com/page/99", savedTabs().tabs.get(0).url);
    }

    public void testDisposingWindowDoesNotEraseSavedTabs() {
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        HappyuTabsState.Snapshot before = savedTabs();
        Disposer.dispose(manager);
        assertEquals(before.tabs.size(), savedTabs().tabs.size());
        assertEquals(before.selectedIndex, savedTabs().selectedIndex);
        titleActions.clear();
        createWindow();
        assertEquals(before.tabs.size(), manager.getContentCount());
        assertEquals(before.selectedIndex, manager.getIndexOfContent(manager.getSelectedContent()));
    }

    public void testProjectShutdownRemovalsDoNotReplaceSavedTabs() {
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectManager.TOPIC)
                .projectClosing(getProject());
        panel(1).toggleImages();
        manager.setSelectedContent(manager.getContent(0));
        manager.removeAllContents(true);
        assertEquals(2, savedTabs().tabs.size());
        assertTrue(savedTabs().tabs.get(1).imagesBlocked);
        assertEquals(1, savedTabs().selectedIndex);
    }

    public void testCancelledProjectCloseStillSavesTabChanges() {
        // A failed save can cancel closing after this notification, without projectClosing.
        var publisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectManager.TOPIC);
        publisher.projectClosingBeforeSave(getProject());
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        assertEquals(2, manager.getContentCount());
        assertEquals(2, savedTabs().tabs.size());
        assertEquals(1, savedTabs().selectedIndex);
        panel(1).toggleImages();
        assertFalse(savedTabs().tabs.get(1).imagesBlocked);
        assertTrue(manager.removeContent(manager.getContent(0), true));
        assertEquals(1, savedTabs().tabs.size());
        assertFalse(savedTabs().tabs.get(0).imagesBlocked);
        publisher.projectClosingBeforeSave(getProject());
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        assertEquals(2, savedTabs().tabs.size());
    }

    public void testExplicitlyClosedAllTabsRestoreEmptyAndInvalidSelectionIsClamped() {
        manager.removeAllContents(true);
        restore(savedTabs());
        assertEquals(0, manager.getContentCount());
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        assertEquals(1, manager.getContentCount());
        HappyuTabsState.Snapshot state = savedTabs();
        state.selectedIndex = 999;
        restore(state);
        assertEquals(manager.getContent(0), manager.getSelectedContent());
    }

    public void testSleepAndWakePreserveTabMetadataAndProtectSelectedTab() throws Exception {
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        Content first = manager.getContent(0);
        HappyuBrowserPanel firstPanel = panel(0);
        firstPanel.toggleImages();
        var recordUrl = HappyuBrowserPanel.class.getDeclaredMethod("recordUrl", String.class);
        recordUrl.setAccessible(true);
        recordUrl.invoke(firstPanel, "https://example.com/latest?q=C%2B%2B#123");
        assertFalse(HappyuToolWindowFactory.sleepTab(manager.getSelectedContent()));
        assertTrue(HappyuToolWindowFactory.sleepTab(first));
        assertTrue(firstPanel.isSleeping());
        assertFalse(firstPanel.isBrowserStarted());
        assertEquals(2, manager.getContentCount());
        assertTrue(first.getDisplayName().startsWith("[Sleeping] "));
        HappyuTabsState.Tab saved = savedTabs().tabs.get(0);
        assertTrue(saved.sleeping);
        assertFalse(saved.imagesBlocked);
        assertEquals("New Tab", saved.title);
        assertEquals(firstPanel.getCurrentUrl(), saved.url);
        assertFalse(HappyuToolWindowFactory.sleepTab(first));
        manager.setSelectedContent(first);
        assertFalse(firstPanel.isSleeping());
        assertTrue(firstPanel.isBrowserStarted());
        assertEquals(saved.url, firstPanel.getCurrentUrl());
        assertFalse(firstPanel.isImagesBlocked());
        assertEquals("New Tab", first.getDisplayName());
        assertFalse(savedTabs().tabs.get(0).sleeping);
    }

    public void testSleepActionProtectsSelectedTabAndIsHiddenForOtherToolWindows() {
        SleepHappyuTabAction action = new SleepHappyuTabAction();
        AnActionEvent ours = AnActionEvent.createFromDataContext(ActionPlaces.TOOLWINDOW_POPUP, null,
                key -> PlatformDataKeys.CONTENT_MANAGER.is(key) ? manager : null);
        action.update(ours);
        assertTrue(ours.getPresentation().isVisible());
        assertFalse(ours.getPresentation().isEnabled());
        action.actionPerformed(ours);
        assertFalse(panel(0).isSleeping());
        ContentManager other = ContentFactory.getInstance().createContentManager(true, getProject());
        Disposer.register(getTestRootDisposable(), other);
        Content foreign = other.getFactory().createContent(new JPanel(), "Other plugin", false);
        other.addContent(foreign);
        other.setSelectedContent(foreign);
        AnActionEvent theirs = AnActionEvent.createFromDataContext(ActionPlaces.TOOLWINDOW_POPUP, null,
                key -> PlatformDataKeys.CONTENT_MANAGER.is(key) ? other : null);
        action.update(theirs);
        assertFalse(theirs.getPresentation().isVisible());
        assertFalse(theirs.getPresentation().isEnabled());
    }

    public void testSleepingTabsSurviveXmlRoundTripAndOnlyWakeWhenSelected() {
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        assertTrue(HappyuToolWindowFactory.sleepTab(manager.getContent(0)));
        HappyuTabsState.Snapshot before = savedTabs();
        restore(XmlSerializer.deserialize(XmlSerializer.serialize(before), HappyuTabsState.Snapshot.class));
        assertTrue(panel(0).isSleeping());
        assertFalse(panel(0).isBrowserStarted());
        assertTrue(manager.getContent(0).getDisplayName().startsWith("[Sleeping] "));
        assertTrue(panel(1).isBrowserStarted());
        manager.setSelectedContent(manager.getContent(0));
        assertFalse(panel(0).isSleeping());
        assertTrue(panel(0).isBrowserStarted());
        assertFalse(savedTabs().tabs.get(0).sleeping);
    }

    public void testAutomaticSleepIsOffByDefaultAndHonorsTimeoutWithoutLoadingLazyTabs() {
        HappyuTabsState.Snapshot tabs = new HappyuTabsState.Snapshot();
        tabs.initialized = true;
        tabs.tabs = List.of(new HappyuTabsState.Tab(), new HappyuTabsState.Tab(), new HappyuTabsState.Tab());
        tabs.selectedIndex = 0;
        restore(tabs);
        manager.setSelectedContent(manager.getContent(1));
        AtomicLong clock = new AtomicLong();
        HappyuTabSleepController controller = new HappyuTabSleepController(getProject(), manager, clock::get);
        clock.set(TimeUnit.HOURS.toNanos(1));
        controller.checkIdleTabs();
        UIUtil.dispatchAllInvocationEvents();
        assertFalse(panel(0).isSleeping());
        getProject().getService(HappyuSettings.class).update(true, 15);
        manager.setSelectedContent(manager.getContent(0));
        manager.setSelectedContent(manager.getContent(1));
        clock.addAndGet(TimeUnit.MINUTES.toNanos(15) - 1);
        controller.checkIdleTabs();
        UIUtil.dispatchAllInvocationEvents();
        assertFalse(panel(0).isSleeping());
        clock.incrementAndGet();
        controller.checkIdleTabs();
        UIUtil.dispatchAllInvocationEvents();
        assertTrue(panel(0).isSleeping());
        assertFalse(panel(1).isSleeping());
        assertTrue(panel(1).isBrowserStarted());
        assertFalse(panel(2).isSleeping());
        assertFalse(panel(2).isBrowserStarted());
    }

    public void testReturningToTabResetsIdleTimeAndCancelsQueuedSleep() {
        titleActions.get(0).actionPerformed(TestActionEvent.createTestEvent());
        AtomicLong clock = new AtomicLong();
        HappyuTabSleepController controller = new HappyuTabSleepController(getProject(), manager, clock::get);
        getProject().getService(HappyuSettings.class).update(true, 1);
        clock.set(TimeUnit.SECONDS.toNanos(59));
        manager.setSelectedContent(manager.getContent(0));
        manager.setSelectedContent(manager.getContent(1));
        clock.set(TimeUnit.SECONDS.toNanos(60));
        controller.checkIdleTabs();
        UIUtil.dispatchAllInvocationEvents();
        assertFalse(panel(0).isSleeping());
        clock.set(TimeUnit.SECONDS.toNanos(119));
        controller.checkIdleTabs();
        manager.setSelectedContent(manager.getContent(0));
        UIUtil.dispatchAllInvocationEvents();
        assertFalse(panel(0).isSleeping());
        assertFalse(panel(1).isSleeping());
        manager.setSelectedContent(manager.getContent(1));
        clock.addAndGet(TimeUnit.MINUTES.toNanos(1));
        controller.checkIdleTabs();
        getProject().getService(HappyuSettings.class).update(false, 1);
        UIUtil.dispatchAllInvocationEvents();
        assertFalse(panel(0).isSleeping());
        getProject().getService(HappyuSettings.class).update(true, 1);
        controller.checkIdleTabs();
        Disposer.dispose(controller);
        UIUtil.dispatchAllInvocationEvents();
        assertFalse(panel(0).isSleeping());
    }

    private HappyuTabsState.Snapshot savedTabs() {
        return getProject().getService(HappyuTabsState.class).getState();
    }

    private HappyuBrowserPanel panel(int index) {
        return (HappyuBrowserPanel) manager.getContent(index).getDisposer();
    }

    private void restore(HappyuTabsState.Snapshot state) {
        Disposer.dispose(manager);
        getProject().getService(HappyuTabsState.class).loadState(state);
        titleActions.clear();
        createWindow();
    }

    private static boolean hasButton(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && name.equals(button.getAccessibleContext().getAccessibleName())) {
                assertNotNull(button.getIcon());
                assertEquals(16, button.getIcon().getIconWidth());
                assertTrue(button.getText() == null || button.getText().isEmpty());
                return true;
            }
            if (child instanceof Container container && hasButton(container, name)) return true;
        }
        return false;
    }
}
