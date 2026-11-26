package com.happyu.browser;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ui.UIUtil;
import org.junit.Assume;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class HappyuZoomSyncTest extends LightPlatformTestCase {
    private final ArrayDeque<Runnable> workers = new ArrayDeque<>();
    private final List<Integer> changes = new ArrayList<>();
    private final JPanel page = new JPanel();
    private final JPanel child = new JPanel();
    private final AtomicInteger reads = new AtomicInteger();
    private double nativeFactor = 1.25;
    private HappyuZoomSync sync;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        page.add(child);
        child.addMouseWheelListener(event -> {
        });
        sync = new HappyuZoomSync(page, () -> {
            reads.incrementAndGet();
            return nativeFactor;
        }, changes::add, workers::add);
        Disposer.register(getTestRootDisposable(), sync);
    }

    public void testCtrlWheelIsNotConsumedAndReadsActualZoomAfterNativeProcessing() {
        MouseWheelEvent event = wheel(child, InputEvent.CTRL_DOWN_MASK);
        child.dispatchEvent(event);
        assertFalse(event.isConsumed());
        assertEquals(0, reads.get());
        nativeFactor = 1.5;
        for (int i = 0; i < 30; i++) sync.readZoom();
        assertEquals(1, workers.size());
        workers.remove().run();
        UIUtil.dispatchAllInvocationEvents();
        assertEquals(1, reads.get());
        assertEquals(List.of(150), changes);
    }

    public void testOrdinaryWheelAndEventsOutsideThePageDoNotStartNativeReads() {
        child.dispatchEvent(wheel(child, 0));
        JPanel otherPage = new JPanel();
        otherPage.addMouseWheelListener(event -> {
        });
        otherPage.dispatchEvent(wheel(otherPage, InputEvent.CTRL_DOWN_MASK));
        child.dispatchEvent(wheel(child, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
        sync.readZoom();
        assertTrue(workers.isEmpty());
        assertEquals(0, reads.get());
    }

    public void testNativeWheelForwardedToTopLevelWindowUpdatesZoom() {
        withNativeWindow((window, browser) -> {
            Point point = SwingUtilities.convertPoint(browser, 25, 30, window);
            MouseWheelEvent forwarded = wheel(window, point.x, point.y);
            window.dispatchEvent(forwarded);
            assertFalse(forwarded.isConsumed());
            nativeFactor = 1.5;
            sync.readZoom();
            assertEquals(1, workers.size());
            workers.remove().run();
            UIUtil.dispatchAllInvocationEvents();
            assertEquals(List.of(150), changes);
        });
    }

    public void testForwardedWheelOutsidePageOrFromAnotherWindowIsIgnored() {
        withNativeWindow((window, browser) -> {
            window.dispatchEvent(wheel(window, 10, 10));
            window.dispatchEvent(wheel(window, 700, 500));
            Frame otherWindow = new Frame();
            try {
                otherWindow.addMouseWheelListener(event -> { });
                Point point = SwingUtilities.convertPoint(browser, 25, 30, window);
                otherWindow.dispatchEvent(wheel(otherWindow, point.x, point.y));
                sync.readZoom();
                assertTrue(workers.isEmpty());
                assertEquals(0, reads.get());
            } finally {
                otherWindow.dispose();
            }
        });
    }

    public void testForwardedWheelIgnoresHiddenAndClippedBrowser() {
        withNativeWindow((window, browser) -> {
            Point point = SwingUtilities.convertPoint(browser, 25, 30, window);
            browser.setVisible(false);
            window.dispatchEvent(wheel(window, point.x, point.y));
            sync.readZoom();
            assertTrue(workers.isEmpty());
            browser.setVisible(true);
            // Keep the page's own bounds large, but clip it using its parent container.
            browser.getParent().setSize(120, 100);
            window.dispatchEvent(wheel(window, point.x, point.y));
            sync.readZoom();
            assertTrue(workers.isEmpty());
            assertEquals(0, reads.get());
        });
    }

    private void withNativeWindow(BiConsumer<Frame, JPanel> test) {
        Assume.assumeFalse("Top-level AWT event tests require a desktop environment", GraphicsEnvironment.isHeadless());
        Disposer.dispose(sync);
        Frame window = new Frame();
        try {
            window.setLayout(null);
            window.setSize(800, 600);
            window.addMouseWheelListener(event -> { });
            JPanel container = new JPanel(null);
            container.setBounds(60, 80, 600, 400);
            window.add(container);
            // Keep the real AWT window undisplayed; simulate whether the browser is currently showing.
            JPanel browser = new JPanel(null) {
                @Override
                public boolean isShowing() {
                    return isVisible();
                }
            };
            browser.setBounds(100, 50, 300, 200);
            container.add(browser);
            sync = new HappyuZoomSync(browser, () -> {
                reads.incrementAndGet();
                return nativeFactor;
            }, changes::add, workers::add);
            Disposer.register(getTestRootDisposable(), sync);
            test.accept(window, browser);
        } finally {
            Disposer.dispose(sync);
            window.dispose();
        }
    }

    public void testExplicitZoomChangeCancelsQueuedReadBeforeItTouchesNativeBrowser() {
        child.dispatchEvent(wheel(child, InputEvent.CTRL_DOWN_MASK));
        sync.readZoom();
        sync.cancel();
        workers.remove().run();
        UIUtil.dispatchAllInvocationEvents();
        assertEquals(0, reads.get());
        assertTrue(changes.isEmpty());
        sync.readZoom();
        assertTrue(workers.isEmpty());
    }

    public void testExplicitChangeOrDisposalDiscardsAnAlreadyReadOldValue() {
        child.dispatchEvent(wheel(child, InputEvent.CTRL_DOWN_MASK));
        sync.readZoom();
        workers.remove().run();
        sync.cancel();
        UIUtil.dispatchAllInvocationEvents();
        assertTrue(changes.isEmpty());

        child.dispatchEvent(wheel(child, InputEvent.CTRL_DOWN_MASK));
        sync.readZoom();
        workers.remove().run();
        Disposer.dispose(sync);
        UIUtil.dispatchAllInvocationEvents();
        assertTrue(changes.isEmpty());
        child.dispatchEvent(wheel(child, InputEvent.CTRL_DOWN_MASK));
        sync.readZoom();
        assertTrue(workers.isEmpty());
    }

    public void testInvalidNativeValuesAreIgnoredAndFractionalPercentagesAreRounded() {
        child.dispatchEvent(wheel(child, InputEvent.CTRL_DOWN_MASK));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, 10}) {
            nativeFactor = invalid;
            sync.readZoom();
            workers.remove().run();
            UIUtil.dispatchAllInvocationEvents();
        }
        assertTrue(changes.isEmpty());
        nativeFactor = 1.333333333333;
        sync.readZoom();
        workers.remove().run();
        UIUtil.dispatchAllInvocationEvents();
        assertEquals(List.of(133), changes);
    }

    private static MouseWheelEvent wheel(Component component, int modifiers) {
        return new MouseWheelEvent(component, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiers,
                2, 2, 2, 2, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1, -1);
    }

    private static MouseWheelEvent wheel(Component window, int x, int y) {
        // Match CefBrowserWr's constructor: coordinates belong to the top-level window.
        return new MouseWheelEvent(window, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK, x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1);
    }
}
