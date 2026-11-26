package com.happyu.ui;

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.JBUI;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;

/**
 * Fixed top-edge target with an owned native overlay above windowed JCEF.
 */
public final class HappyuAutoHideToolbar extends JPanel implements Disposable {
    private final JComponent page;
    private final JComponent toolbar;
    private final JPanel edge = new JPanel();
    private final JPanel overlayContent = new JPanel(new BorderLayout());
    private JWindow overlay;
    private final Timer hideTimer = new Timer(300, event -> hideIfIdle());
    private final AWTEventListener mouseListener = this::onInput;
    private final PropertyChangeListener focusListener = event -> updateRetention();
    private final ChangeListener menuListener = event -> updateRetention();
    private final HierarchyListener hierarchyListener = event -> {
        if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) updateListening();
    };
    private final ComponentAdapter boundsListener = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent event) {
            updateOverlayBounds();
        }

        @Override
        public void componentResized(ComponentEvent event) {
            updateOverlayBounds();
        }
    };
    private final HierarchyBoundsAdapter ancestorBoundsListener = new HierarchyBoundsAdapter() {
        @Override
        public void ancestorMoved(HierarchyEvent event) {
            updateOverlayBounds();
        }

        @Override
        public void ancestorResized(HierarchyEvent event) {
            updateOverlayBounds();
        }
    };
    private boolean autoHide;
    private boolean listening;
    private boolean pointerInside;
    private boolean disposed;

    public HappyuAutoHideToolbar(JComponent page, JComponent toolbar) {
        super(new BorderLayout());
        this.page = page;
        this.toolbar = toolbar;
        hideTimer.setRepeats(false);
        edge.setPreferredSize(JBUI.size(0, 4));
        edge.setToolTipText("Move the pointer to the top edge to show the address toolbar");
        edge.setVisible(false);
        add(edge, BorderLayout.NORTH);
        add(toolbar, BorderLayout.CENTER);
        page.addHierarchyListener(hierarchyListener);
        page.addComponentListener(boundsListener);
        page.addHierarchyBoundsListener(ancestorBoundsListener);
    }

    public void setAutoHide(boolean enabled) {
        if (disposed || autoHide == enabled) return;
        autoHide = enabled;
        pointerInside = false;
        hideTimer.stop();
        if (enabled) overlayContent.add(toolbar, BorderLayout.CENTER);
        else {
            releaseOverlay();
            add(toolbar, BorderLayout.CENTER);
        }
        edge.setVisible(enabled);
        setExpanded(!enabled || isEditing());
        // Only changing the setting changes the page's layout. Hover never changes this strip.
        revalidate();
        page.revalidate();
        page.repaint();
        updateListening();
    }

    private void updateListening() {
        boolean needed = !disposed && autoHide && page.isShowing();
        if (listening == needed) return;
        listening = needed;
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        MenuSelectionManager menus = MenuSelectionManager.defaultManager();
        if (needed) {
            Toolkit.getDefaultToolkit().addAWTEventListener(mouseListener,
                    AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.WINDOW_FOCUS_EVENT_MASK);
            focus.addPropertyChangeListener("permanentFocusOwner", focusListener);
            menus.addChangeListener(menuListener);
            if (toolbar.isVisible()) showOverlay();
        } else {
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener);
            focus.removePropertyChangeListener("permanentFocusOwner", focusListener);
            menus.removeChangeListener(menuListener);
            hideTimer.stop();
            pointerInside = false;
            if (autoHide) {
                setExpanded(false);
                releaseOverlay();
            }
        }
    }

    void onInput(AWTEvent event) {
        if (event instanceof WindowEvent window && overlay != null
                && window.getID() == WindowEvent.WINDOW_LOST_FOCUS
                && (window.getWindow() == overlay || window.getWindow() == overlay.getOwner())
                && window.getOppositeWindow() != overlay.getOwner() && !isOverlayWindow(window.getOppositeWindow())) {
            pointerInside = false;
            setExpanded(false);
            return;
        }
        if (disposed || !listening || !(event instanceof MouseEvent mouse)) return;
        Component source = mouse.getComponent();
        boolean related = source != null && (source == page || SwingUtilities.isDescendingFrom(source, page)
                || belongsToToolbar(source) || source == overlay
                || source == SwingUtilities.getWindowAncestor(page));
        // Native JCEF can forward mouse events to the top-level window; convert that coordinate
        // system too. Never consume page input or react to a different browser/window.
        Point point = related ? SwingUtilities.convertPoint(source, mouse.getPoint(), page) : null;
        int height = toolbar.isVisible() ? toolbar.getPreferredSize().height : JBUI.scale(8);
        pointerInside = point != null && page.getVisibleRect().contains(point)
                && point.y >= 0 && point.y < height;
        if (pointerInside) {
            hideTimer.stop();
            setExpanded(true);
        } else updateRetention();
    }

    private boolean belongsToToolbar(Component component) {
        return component != null && (component == toolbar || SwingUtilities.isDescendingFrom(component, toolbar));
    }

    private boolean isOverlayWindow(Window window) {
        if (overlay == null) return false;
        for (Window current = window; current != null; current = current.getOwner()) {
            if (current == overlay) return true;
        }
        return false;
    }

    private boolean isEditing() {
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (overlay != null && focus.getFocusedWindow() != overlay.getOwner()
                && !isOverlayWindow(focus.getFocusedWindow())) return false;
        Component owner = focus.getPermanentFocusOwner();
        if (owner instanceof JTextComponent && belongsToToolbar(owner)) return true;
        for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) {
            if (element instanceof JPopupMenu popup && belongsToToolbar(popup.getInvoker())) return true;
        }
        return false;
    }

    private void updateRetention() {
        if (disposed || !listening) return;
        if (pointerInside || isEditing()) {
            hideTimer.stop();
            setExpanded(true);
        } else if (toolbar.isVisible() && !hideTimer.isRunning()) hideTimer.start();
    }

    void hideIfIdle() {
        if (!disposed && autoHide && !pointerInside && !isEditing()) setExpanded(false);
    }

    private void setExpanded(boolean expanded) {
        if (toolbar.isVisible() == expanded) {
            if (autoHide && expanded && (overlay == null || !overlay.isVisible())) showOverlay();
            return;
        }
        toolbar.setVisible(expanded);
        if (autoHide && expanded) showOverlay();
        else if (overlay != null) overlay.setVisible(false);
    }

    private void showOverlay() {
        Window owner = SwingUtilities.getWindowAncestor(page);
        if (owner == null || !owner.isShowing() || !page.isShowing()) return;
        if (overlay == null || overlay.getOwner() != owner) {
            releaseOverlay();
            overlay = new JWindow(owner);
            overlay.setType(Window.Type.POPUP);
            overlay.setAutoRequestFocus(false);
            overlay.setContentPane(overlayContent);
        }
        updateOverlayBounds();
        if (overlay != null && toolbar.isVisible()) overlay.setVisible(true);
    }

    private void updateOverlayBounds() {
        if (overlay == null || !page.isShowing() || !page.isDisplayable()) return;
        if (overlay.getOwner() != SwingUtilities.getWindowAncestor(page)) {
            setExpanded(false);
            releaseOverlay();
            return;
        }
        Rectangle visible = page.getVisibleRect();
        if (visible.isEmpty()) {
            setExpanded(false);
            return;
        }
        Point origin = page.getLocationOnScreen();
        Dimension preferred = toolbar.getPreferredSize();
        overlay.setBounds(origin.x + visible.x, origin.y + visible.y, visible.width,
                Math.min(visible.height, preferred.height));
    }

    private void releaseOverlay() {
        if (overlay == null) return;
        JWindow previous = overlay;
        overlay = null;
        previous.setVisible(false);
        previous.setContentPane(new JPanel());
        previous.dispose();
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        updateListening();
        hideTimer.stop();
        releaseOverlay();
        page.removeHierarchyListener(hierarchyListener);
        page.removeComponentListener(boundsListener);
        page.removeHierarchyBoundsListener(ancestorBoundsListener);
    }
}
