package com.happyu.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ui.JBUI;
import org.junit.Assume;

import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.HierarchyEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowEvent;

public final class HappyuAutoHideToolbarTest extends LightPlatformTestCase {
    private static final class Page extends JPanel {
        boolean showing = true;

        Page() {
            super(new BorderLayout());
        }

        @Override
        public boolean isShowing() {
            return showing;
        }
    }

    private Page page;
    private JPanel toolbar;
    private JTextField address;
    private HappyuAutoHideToolbar header;
    private JPanel content;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        page = new Page();
        page.setSize(600, 400);
        page.addMouseMotionListener(new MouseMotionAdapter() {
        });
        toolbar = new JPanel();
        address = new JTextField("https://example.test", 20);
        toolbar.add(address);
        header = new HappyuAutoHideToolbar(page, toolbar);
        page.add(header, BorderLayout.NORTH);
        content = new JPanel(new BorderLayout());
        page.add(content, BorderLayout.CENTER);
        Disposer.register(getTestRootDisposable(), header);
        layout();
    }

    private void layout() {
        page.doLayout();
        header.doLayout();
        toolbar.doLayout();
    }

    private void move(int x, int y) {
        page.dispatchEvent(new MouseEvent(page, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false));
        layout();
    }

    public void testTopEdgeRevealsAndLeavingCollapsesWithoutConsumingMouseInput() {
        assertTrue(toolbar.isVisible());
        header.setAutoHide(true);
        layout();
        assertFalse(toolbar.isVisible());
        assertEquals(JBUI.scale(4), header.getHeight());
        Rectangle stableBounds = content.getBounds();
        move(80, 2);
        assertTrue(toolbar.isVisible());
        assertEquals(JBUI.scale(4), header.getHeight());
        assertEquals(stableBounds, content.getBounds());
        move(80, toolbar.getPreferredSize().height - 1);
        header.hideIfIdle();
        assertTrue(toolbar.isVisible());
        MouseEvent outside = new MouseEvent(page, MouseEvent.MOUSE_MOVED, 1, 0, 80, 200, 0, false);
        page.dispatchEvent(outside);
        assertFalse(outside.isConsumed());
        assertTrue(toolbar.isVisible()); // The delay avoids flicker while crossing child controls.
        header.hideIfIdle();
        layout();
        assertFalse(toolbar.isVisible());
        assertEquals(stableBounds, content.getBounds());
        header.setAutoHide(false);
        assertTrue(toolbar.isVisible());
    }

    public void testEditingAddressAndToolbarPopupKeepToolbarVisible() {
        KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        class Focus extends DefaultKeyboardFocusManager {
            void focus(java.awt.Component component) {
                setGlobalPermanentFocusOwner(component);
            }
        }
        Focus focus = new Focus();
        KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
        try {
            header.setAutoHide(true);
            move(80, 2);
            focus.focus(address);
            move(80, 200);
            header.hideIfIdle();
            assertTrue(toolbar.isVisible());
            focus.focus(null);
            header.hideIfIdle();
            assertFalse(toolbar.isVisible());
            JComboBox<String> combo = new JComboBox<>(new String[]{"100%", "125%"});
            toolbar.add(combo);
            JPopupMenu popup = new JPopupMenu();
            popup.setInvoker(combo);
            popup.add(new JMenuItem("125%"));
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{popup});
            assertTrue(toolbar.isVisible());
            header.hideIfIdle();
            assertTrue(toolbar.isVisible());
            MenuSelectionManager.defaultManager().clearSelectedPath();
            header.hideIfIdle();
            assertFalse(toolbar.isVisible());
        } finally {
            MenuSelectionManager.defaultManager().clearSelectedPath();
            header.dispose();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
        }
    }

    public void testHiddenPagesAndDisposalReleaseGlobalMouseListeners() {
        int before = Toolkit.getDefaultToolkit().getAWTEventListeners().length;
        header.setAutoHide(true);
        assertEquals(before + 1, Toolkit.getDefaultToolkit().getAWTEventListeners().length);
        page.showing = false;
        page.dispatchEvent(new HierarchyEvent(page, HierarchyEvent.HIERARCHY_CHANGED, page, null, HierarchyEvent.SHOWING_CHANGED));
        assertEquals(before, Toolkit.getDefaultToolkit().getAWTEventListeners().length);
        page.showing = true;
        page.dispatchEvent(new HierarchyEvent(page, HierarchyEvent.HIERARCHY_CHANGED, page, null, HierarchyEvent.SHOWING_CHANGED));
        assertEquals(before + 1, Toolkit.getDefaultToolkit().getAWTEventListeners().length);
        move(50, 2);
        header.dispose();
        assertEquals(before, Toolkit.getDefaultToolkit().getAWTEventListeners().length);
        header.setAutoHide(false);
        header.hideIfIdle();
        assertFalse(toolbar.isVisible());
    }

    public void testEventsFromAnotherPanelDoNotRevealToolbar() {
        header.setAutoHide(true);
        JPanel other = new JPanel();
        other.addMouseMotionListener(new MouseMotionAdapter() {
        });
        other.dispatchEvent(new MouseEvent(other, MouseEvent.MOUSE_MOVED, 1, 0, 50, 2, 0, false));
        assertFalse(toolbar.isVisible());
        move(-1, 2);
        assertFalse(toolbar.isVisible());
        move(700, 2);
        assertFalse(toolbar.isVisible());
    }

    public void testNativeMouseForwardedToWindowUsesBrowserRelativeCoordinates() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Frame window = new Frame();
        try {
            window.setLayout(null);
            window.setSize(800, 600);
            window.add(page);
            page.setBounds(60, 80, 600, 400);
            header.setAutoHide(true);
            Point top = SwingUtilities.convertPoint(page, 50, 2, window);
            window.dispatchEvent(new MouseEvent(window, MouseEvent.MOUSE_MOVED, 1, 0, top.x, top.y, 0, false));
            assertTrue(toolbar.isVisible());
            window.dispatchEvent(new MouseEvent(window, MouseEvent.MOUSE_MOVED, 1, 0, 10, 10, 0, false));
            header.hideIfIdle();
            assertFalse(toolbar.isVisible());
        } finally {
            window.dispose();
        }
    }

    public void testOwnedOverlayCoversNativeSurfaceWithoutResizingItAndClosesOnWindowSwitch() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Frame window = new Frame();
        Frame other = new Frame();
        Canvas nativeSurface = new Canvas();
        content.add(nativeSurface, BorderLayout.CENTER);
        try {
            window.setAutoRequestFocus(false);
            window.add(page);
            window.setBounds(80, 80, 700, 500);
            window.setVisible(true);
            header.setAutoHide(true);
            layout();
            content.doLayout();
            Rectangle stableBounds = nativeSurface.getBounds();
            Point stableLocation = nativeSurface.getLocationOnScreen();
            move(80, 2);
            content.doLayout();
            var field = HappyuAutoHideToolbar.class.getDeclaredField("overlay");
            field.setAccessible(true);
            JWindow popup = (JWindow) field.get(header);
            assertNotNull(popup);
            assertTrue(popup.isVisible());
            assertSame(window, popup.getOwner());
            assertFalse(popup.isAutoRequestFocus());
            assertTrue(SwingUtilities.isDescendingFrom(toolbar, popup));
            assertEquals(page.getLocationOnScreen(), popup.getLocationOnScreen());
            assertEquals(page.getWidth(), popup.getWidth());
            assertEquals(stableBounds, nativeSurface.getBounds());
            assertEquals(stableLocation, nativeSurface.getLocationOnScreen());
            toolbar.dispatchEvent(new MouseEvent(toolbar, MouseEvent.MOUSE_MOVED, 1, 0, 50, 10, 0, false));
            header.hideIfIdle();
            assertTrue(popup.isVisible());
            window.setLocation(100, 100);
            page.dispatchEvent(new ComponentEvent(page, ComponentEvent.COMPONENT_MOVED));
            assertEquals(page.getLocationOnScreen(), popup.getLocationOnScreen());
            assertEquals(stableBounds, nativeSurface.getBounds());
            stableLocation = nativeSurface.getLocationOnScreen();
            // FocusManager drops synthetic lost-focus events for a window that never requested
            // focus. Exercise the registered AWT callback without stealing desktop focus.
            header.onInput(new WindowEvent(window, WindowEvent.WINDOW_LOST_FOCUS, other));
            assertFalse(popup.isVisible());
            layout();
            content.doLayout();
            assertEquals(stableBounds, nativeSurface.getBounds());
            assertEquals(stableLocation, nativeSurface.getLocationOnScreen());
            header.dispose();
            assertFalse(popup.isDisplayable());
        } finally {
            header.dispose();
            window.dispose();
            other.dispose();
        }
    }
}
