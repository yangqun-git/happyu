package com.happyu.browser;

import com.happyu.settings.HappyuSettings;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightPlatformTestCase;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.network.CefRequest;
import org.cef.browser.CefBrowser;
import com.intellij.util.ui.UIUtil;

import javax.swing.JComboBox;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

public final class HappyuBrowserPanelTest extends LightPlatformTestCase {
    public void testToolbarAutoHideUpdatesLiveWithoutStartingSleepingBrowser() throws Exception {
        HappyuSettings settings = getProject().getService(HappyuSettings.class);
        settings.loadState(new HappyuSettings.Options());
        HappyuBrowserPanel panel = createPanel(true);
        var field = HappyuBrowserPanel.class.getDeclaredField("addressField");
        field.setAccessible(true);
        javax.swing.JComponent address = (javax.swing.JComponent) field.get(panel);
        javax.swing.JComponent toolbar = (javax.swing.JComponent) address.getParent();
        try {
            assertTrue(toolbar.isVisible());
            assertSame(address, panel.getPreferredFocusedComponent());
            settings.setAutoHideToolbar(true);
            assertFalse(toolbar.isVisible());
            assertNotSame(address, panel.getPreferredFocusedComponent());
            assertFalse(panel.isBrowserStarted());
            assertTrue(panel.isSleeping());
            settings.setAutoHideToolbar(false);
            assertTrue(toolbar.isVisible());
            assertSame(address, panel.getPreferredFocusedComponent());
        } finally {
            settings.setAutoHideToolbar(false);
        }
    }

    private HappyuBrowserPanel createPanel(boolean sleeping) {
        Registry.get("ide.browser.jcef.enabled").setValue(false, getTestRootDisposable());
        HappyuBrowserPanel panel = new HappyuBrowserPanel(getProject(), "https://example.com/last", true, sleeping,
                url -> { }, title -> { }, () -> { });
        Disposer.register(getTestRootDisposable(), panel);
        return panel;
    }

    public void testSleepCancelsPendingScriptWorkAndPanelCanWakeRepeatedly() throws Exception {
        HappyuBrowserPanel panel = createPanel(false);
        panel.ensureBrowserCreated();
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        CefBrowser browser = (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(),
                new Class<?>[]{CefBrowser.class}, (proxy, method, args) -> {
                    throw new AssertionError("Script touched a released browser: " + method.getName());
                });
        HappyuCefScripts scripts = new HappyuCefScripts(browser, () -> "theme", workers::add);
        scripts.requestAllFrames();
        var updates = HappyuBrowserPanel.class.getDeclaredField("scriptUpdates");
        updates.setAccessible(true);
        updates.set(panel, scripts);
        assertTrue(panel.sleep());
        workers.remove().run();
        assertTrue(workers.isEmpty());
        assertTrue(panel.isSleeping());
        assertFalse(panel.isBrowserStarted());
        panel.toggleImages();
        for (int i = 0; i < 3; i++) {
            panel.ensureBrowserCreated();
            assertTrue(panel.isBrowserStarted());
            assertFalse(panel.isSleeping());
            assertEquals("https://example.com/last", panel.getCurrentUrl());
            assertFalse(panel.isImagesBlocked());
            assertTrue(panel.sleep());
        }
        Disposer.dispose(panel);
        panel.ensureBrowserCreated();
        assertFalse(panel.isBrowserStarted());
        assertFalse(panel.sleep());
    }

    public void testQueuedCallbacksFromOldBrowserCannotModifyResumedTab() throws Exception {
        HappyuBrowserPanel panel = createPanel(false);
        panel.ensureBrowserCreated();
        var generation = HappyuBrowserPanel.class.getDeclaredField("browserGeneration");
        generation.setAccessible(true);
        var schedule = HappyuBrowserPanel.class.getDeclaredMethod("runOnBrowserUiThread", long.class, Runnable.class);
        schedule.setAccessible(true);
        AtomicInteger changes = new AtomicInteger();
        long old = generation.getLong(panel);
        schedule.invoke(panel, old, (Runnable) changes::incrementAndGet);
        assertTrue(panel.sleep());
        panel.ensureBrowserCreated();
        schedule.invoke(panel, old, (Runnable) changes::incrementAndGet);
        schedule.invoke(panel, generation.getLong(panel), (Runnable) changes::incrementAndGet);
        UIUtil.dispatchAllInvocationEvents();
        assertEquals(1, changes.get());
    }

    public void testRestoredSleepingTabStaysUnloadedUntilWoken() {
        HappyuBrowserPanel panel = createPanel(true);
        assertTrue(panel.isSleeping());
        assertFalse(panel.isBrowserStarted());
        panel.toggleImages();
        assertFalse(panel.isBrowserStarted());
        panel.ensureBrowserCreated();
        assertTrue(panel.isBrowserStarted());
        assertFalse(panel.isSleeping());
        assertFalse(panel.isImagesBlocked());
    }

    public void testReadingModeUpdatesScriptWithoutWakingSleepingTabsOrChangingComposerPreference() throws Exception {
        HappyuSettings settings = getProject().getService(HappyuSettings.class);
        settings.loadState(new HappyuSettings.Options());
        HappyuBrowserPanel panel = createPanel(true);
        var script = HappyuBrowserPanel.class.getDeclaredField("contentModeScript");
        script.setAccessible(true);
        assertTrue(((String) script.get(panel)).endsWith("window.__happyuAgentReading?.dispose();"));
        try {
            settings.setAgentReadingMode(true);
            String enabled = (String) script.get(panel);
            assertTrue(enabled.contains("Agent · 阅读"));
            assertTrue(enabled.contains("getComputedStyle(node)"));
            assertTrue(panel.isSleeping());
            assertFalse(panel.isBrowserStarted());
            assertFalse(settings.getState().agentPanelVisible);
            settings.update(true, 30);
            assertSame(enabled, script.get(panel));
            panel.ensureBrowserCreated();
            assertTrue(panel.isBrowserStarted());
            assertTrue(((String) script.get(panel)).contains("Agent · 阅读"));
            settings.setAgentReadingMode(false);
            assertTrue(((String) script.get(panel)).endsWith("window.__happyuAgentReading?.dispose();"));
        } finally {
            settings.setAgentReadingMode(false);
            settings.update(false, 15);
        }
    }

    public void testZoomMenuMarksDefaultAndDoesNotWakeSleepingTab() throws Exception {
        HappyuBrowserPanel panel = createPanel(true);
        var zoomField = HappyuBrowserPanel.class.getDeclaredField("zoomCombo");
        zoomField.setAccessible(true);
        JComboBox<?> menu = (JComboBox<?>) zoomField.get(panel);
        assertEquals("100% (Default)", menu.getSelectedItem().toString());
        assertEquals("Page zoom", menu.getAccessibleContext().getAccessibleName());
        for (int i = 0; i < menu.getItemCount(); i++) {
            if (menu.getItemAt(i).toString().equals("125%")) {
                menu.setSelectedIndex(i);
                break;
            }
        }
        assertEquals(125, panel.getZoomPercent());
        assertTrue(panel.isSleeping());
        assertFalse(panel.isBrowserStarted());
        panel.ensureBrowserCreated();
        assertEquals(125, panel.getZoomPercent());
        assertTrue(panel.sleep());
        assertEquals(125, panel.getZoomPercent());
        panel.setZoomPercent(100);
        assertEquals("100% (Default)", menu.getSelectedItem().toString());
    }

    public void testZoomChangesNotifyOnceAndIgnoreDisposedPanel() {
        AtomicInteger changes = new AtomicInteger();
        HappyuBrowserPanel panel = new HappyuBrowserPanel(getProject(), HappyuBrowserPanel.HOME_URL, true, true,
                url -> { }, title -> { }, changes::incrementAndGet);
        Disposer.register(getTestRootDisposable(), panel);
        panel.setZoomPercent(150);
        assertEquals(1, changes.get());
        panel.setZoomPercent(150);
        assertEquals(1, changes.get());
        panel.setZoomPercent(0);
        assertEquals(100, panel.getZoomPercent());
        assertEquals(2, changes.get());
        panel.setZoomPercent(501);
        assertEquals(2, changes.get());
        Disposer.dispose(panel);
        panel.setZoomPercent(200);
        assertEquals(100, panel.getZoomPercent());
        assertEquals(2, changes.get());
    }

    public void testReadingNativeZoomUpdatesMenuAndOnlySavesWhenPercentageChanges() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        HappyuBrowserPanel panel = new HappyuBrowserPanel(getProject(), HappyuBrowserPanel.HOME_URL, true, true,
                url -> { }, title -> { }, changes::incrementAndGet);
        Disposer.register(getTestRootDisposable(), panel);
        var record = HappyuBrowserPanel.class.getDeclaredMethod("recordZoomPercent", int.class);
        record.setAccessible(true);
        var zoomField = HappyuBrowserPanel.class.getDeclaredField("zoomCombo");
        zoomField.setAccessible(true);
        JComboBox<?> menu = (JComboBox<?>) zoomField.get(panel);
        record.invoke(panel, 133);
        assertEquals(133, panel.getZoomPercent());
        assertEquals("133%", menu.getSelectedItem().toString());
        assertEquals(1, changes.get());
        record.invoke(panel, 133);
        assertEquals(1, changes.get());
        record.invoke(panel, 100);
        assertEquals("100% (Default)", menu.getSelectedItem().toString());
        assertEquals(2, changes.get());
        assertTrue(panel.isSleeping());
        assertFalse(panel.isBrowserStarted());
    }

    public void testOnlyBlockedMediaRequestsGetResourceCallbacks() {
        Registry.get("ide.browser.jcef.enabled").setValue(false, getTestRootDisposable());
        HappyuBrowserPanel panel = new HappyuBrowserPanel(getProject(), HappyuBrowserPanel.HOME_URL, true,
                url -> { }, title -> { }, () -> { });
        Disposer.register(getTestRootDisposable(), panel);
        assertNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_SCRIPT, "https://example.com/app.js"));
        assertNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_STYLESHEET, "https://example.com/app.css"));
        assertNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_MAIN_FRAME, "https://example.com/"));
        CefResourceRequestHandler blocker = panel.resourceRequestHandlerFor(
                CefRequest.ResourceType.RT_IMAGE, "https://example.com/image");
        assertNotNull(blocker);
        assertSame(blocker, panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_MEDIA, null));
        assertSame(blocker, panel.resourceRequestHandlerFor(
                CefRequest.ResourceType.RT_MAIN_FRAME, "https://example.com/image.PNG?size=2"));

        panel.toggleImages();
        assertNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, "https://example.com/image"));
        assertFalse(blocker.onBeforeResourceLoad(null, null, null));
    }

    public void testImagePreviewOnlyAllowsOneExplicitImageInItsOwnTabAndClearsOnCloseOrDisable() {
        HappyuSettings settings = getProject().getService(HappyuSettings.class);
        settings.setAgentReadingMode(true);
        HappyuBrowserPanel panel = createPanel(true);
        HappyuBrowserPanel otherTab = createPanel(true);
        String first = "https://example.com/first.png?size=large";
        String second = "https://example.com/second.png";
        try {
            assertNotNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, first));
            assertTrue(panel.authorizeImagePreview(first));
            assertNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, first));
            assertNotNull(otherTab.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, first));
            assertNotNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, second));
            assertNotNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_MAIN_FRAME, first));
            assertNotNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_MEDIA, first));
            assertTrue(panel.isImagesBlocked());
            assertTrue(panel.authorizeImagePreview(second));
            assertNotNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, first));
            assertNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, second));
            for (String invalid : new String[]{"javascript:alert(1)", "file:///private.png", "data:image/png;base64,abc",
                    "https:///missing-host.png", "https://example.com/img.png#fragment", "x".repeat(8193)}) {
                assertFalse(panel.authorizeImagePreview(invalid));
            }
            assertTrue(panel.authorizeImagePreview(""));
            assertNotNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, second));
            assertTrue(panel.authorizeImagePreview(first));
            settings.setAgentReadingMode(false);
            assertNotNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, first));
            assertFalse(panel.authorizeImagePreview(first));
            settings.setAgentReadingMode(true);
            assertNotNull(panel.resourceRequestHandlerFor(CefRequest.ResourceType.RT_IMAGE, first));
            assertTrue(panel.authorizeImagePreview(first));
            Disposer.dispose(panel);
            assertFalse(panel.authorizeImagePreview(first));
        } finally {
            settings.setAgentReadingMode(false);
        }
    }
}
