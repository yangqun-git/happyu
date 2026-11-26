package com.happyu.toolwindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.impl.ContentManagerImpl;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseWheelEvent;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Scrolls the native tool-window tabs without changing the selected content.
 */
final class HappyuTabScroller extends JScrollPane implements Disposable {
    private static final double SCROLL_RESPONSE = 35;
    private final JComponent tabs;
    private final ContentManager manager;
    private final LongSupplier clock;
    private final Timer scrollTimer = new Timer(8, event -> advanceScroll());
    private double scrollPosition;
    private double scrollVelocity;
    private int scrollTarget;
    private long scrollUpdatedAt;
    private boolean disposed;
    private boolean wrapping;
    private boolean wrapPending;
    private boolean revealPending;
    private double fractionalScroll;
    private final HierarchyListener hierarchyListener = event -> {
        if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) queueWrap();
        if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) stopScroll();
    };
    private final ContentManagerListener selectionListener = new ContentManagerListener() {
        @Override
        public void selectionChanged(@NotNull ContentManagerEvent event) {
            queueRevealSelected();
        }
    };
    private final ComponentAdapter resizeListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
            queueRevealSelected();
        }
    };

    static void install(ContentManager manager) {
        if (manager instanceof ContentManagerImpl impl && impl.getUI() instanceof ToolWindowContentUi ui) {
            new HappyuTabScroller(manager, ui.getTabComponent());
        }
    }

    HappyuTabScroller(ContentManager manager, JComponent tabs) {
        this(manager, tabs, System::nanoTime);
    }

    HappyuTabScroller(ContentManager manager, JComponent tabs, LongSupplier clock) {
        super(VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_NEVER);
        this.manager = manager;
        this.tabs = tabs;
        this.clock = clock;
        scrollTimer.setCoalesce(true);
        setBorder(JBUI.Borders.empty());
        setViewportBorder(JBUI.Borders.empty());
        setOpaque(false);
        getViewport().setOpaque(false);
        getViewport().addComponentListener(resizeListener);
        setWheelScrollingEnabled(false);
        getAccessibleContext().setAccessibleName("Browser tabs");
        addMouseWheelListener(this::scrollTabs);
        tabs.addHierarchyListener(hierarchyListener);
        manager.addContentManagerListener(selectionListener);
        Disposer.register(manager, this);
        wrap();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    private void queueWrap() {
        if (disposed || wrapping || wrapPending || tabs.getParent() == getViewport()) return;
        wrapPending = true;
        SwingUtilities.invokeLater(() -> {
            wrapPending = false;
            if (!disposed) wrap();
        });
    }

    private void wrap() {
        Container parent = tabs.getParent();
        if (parent == null || parent == getViewport()) return;
        stopScroll();
        wrapping = true;
        try {
            replace(parent, tabs, this);
            setViewportView(tabs);
            parent.revalidate();
            parent.repaint();
            queueRevealSelected();
        } finally {
            wrapping = false;
        }
    }

    private void queueRevealSelected() {
        stopScroll();
        if (disposed || revealPending) return;
        revealPending = true;
        SwingUtilities.invokeLater(() -> {
            revealPending = false;
            if (!disposed && !manager.isDisposed()) revealSelected();
        });
    }

    private static void replace(Container parent, Component oldComponent, Component replacement) {
        LayoutManager layout = parent.getLayout();
        if (layout instanceof GroupLayout group) {
            // Newer IDE headers refer to their tab component directly in layout groups.
            group.replace(oldComponent, replacement);
        } else {
            Object constraints = componentConstraints(layout, oldComponent);
            int index = parent.getComponentZOrder(oldComponent);
            parent.remove(oldComponent);
            parent.add(replacement, constraints, index);
        }
    }

    private static Object componentConstraints(LayoutManager layout, Component component) {
        if (layout instanceof BorderLayout border) return border.getConstraints(component);
        if (layout == null) return null;
        // Only legacy IDE headers need this adapter. Do not link against the class scheduled for removal.
        // This runs when wrapping/restoring the header, never while scrolling or laying out tabs.
        for (Class<?> type = layout.getClass(); type != null; type = type.getSuperclass()) {
            if (!type.getName().equals("com.intellij.ui.layout.migLayout.patched.MigLayout")) continue;
            try {
                Object constraints = type.getMethod("getComponentConstraints").invoke(layout);
                if (constraints instanceof Map<?, ?> map) return map.get(component);
                throw new IllegalStateException("Unexpected legacy tab layout constraints: " + constraints);
            } catch (ReflectiveOperationException exception) {
                // Read before removing the component so a failed adapter cannot discard its constraints.
                throw new IllegalStateException("Cannot read legacy tab layout constraints", exception);
            }
        }
        return null;
    }

    private void scrollTabs(MouseWheelEvent event) {
        if (disposed || event.isConsumed() || event.isControlDown() || event.isAltDown() || event.isMetaDown()) return;
        int max = Math.max(0, getViewport().getViewSize().width - getViewport().getExtentSize().width);
        if (max == 0) {
            stopScroll();
            fractionalScroll = 0;
            return;
        }
        double unit = event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
                ? getViewport().getExtentSize().width * 0.9 : JBUI.scale(16) * Math.max(1, event.getScrollAmount());
        double distance = event.getPreciseWheelRotation() * unit;
        // Account for time since the last frame before changing the target.
        if (distance != 0) advanceScroll();
        int current = getViewport().getViewPosition().x;
        if (distance != 0) {
            // Detect reversal before rounding: even a subpixel gesture must cancel old motion.
            if (scrollTimer.isRunning() && Math.signum(distance) != Integer.signum(scrollTarget - current)) stopScroll();
            if (Math.signum(distance) != Math.signum(fractionalScroll)) fractionalScroll = 0;
        }
        distance += fractionalScroll;
        int pixels = (int) distance;
        fractionalScroll = distance - pixels;
        event.consume();
        if (pixels == 0) return;
        // Accumulate nearby wheel events, but discard the previous destination on reversal.
        int base = scrollTimer.isRunning() && Integer.signum(scrollTarget - current) == Integer.signum(pixels)
                ? scrollTarget : current;
        if (!scrollTimer.isRunning()) {
            scrollPosition = current;
            scrollUpdatedAt = clock.getAsLong();
        }
        scrollTarget = (int) Math.max(0, Math.min(max, (long) base + pixels));
        if (scrollTarget == current) stopScroll();
        else scrollTimer.start();
    }

    void advanceScroll() {
        if (!scrollTimer.isRunning()) return;
        if (disposed || tabs.getParent() != getViewport()) {
            stopScroll();
            return;
        }
        int max = Math.max(0, getViewport().getViewSize().width - getViewport().getExtentSize().width);
        scrollTarget = Math.min(scrollTarget, max);
        long now = clock.getAsLong();
        double elapsed = Math.max(0, (now - scrollUpdatedAt) / 1_000_000_000.0);
        scrollUpdatedAt = now;
        // Exact critically damped motion: retarget without resetting velocity or depending on frame rate.
        double offset = scrollPosition - scrollTarget;
        double momentum = scrollVelocity + SCROLL_RESPONSE * offset;
        double decay = Math.exp(-SCROLL_RESPONSE * elapsed);
        scrollPosition = scrollTarget + (offset + momentum * elapsed) * decay;
        scrollVelocity = (scrollVelocity - SCROLL_RESPONSE * momentum * elapsed) * decay;
        scrollPosition = Math.max(0, Math.min(max, scrollPosition));
        if (scrollPosition == scrollTarget
                || (Math.abs(scrollPosition - scrollTarget) < 0.5 && Math.abs(scrollVelocity) < JBUI.scale(12))) {
            scrollPosition = scrollTarget;
            stopScroll();
        }
        int x = (int) Math.round(scrollPosition);
        if (x != getViewport().getViewPosition().x) getViewport().setViewPosition(new Point(x, 0));
    }

    private void stopScroll() {
        scrollTimer.stop();
        scrollVelocity = 0;
    }

    private void revealSelected() {
        if (tabs.getParent() != getViewport()) return;
        // Lay out at the full natural width, so native layout keeps every tab available.
        doLayout();
        getViewport().doLayout();
        tabs.doLayout();
        Content selected = manager.getSelectedContent();
        if (selected == null) return;
        for (Component component : tabs.getComponents()) {
            if (component instanceof BaseLabel label && label.getContent() == selected) {
                reveal(component.getBounds());
                break;
            }
        }
    }

    void reveal(Rectangle bounds) {
        stopScroll();
        fractionalScroll = 0;
        int width = getViewport().getExtentSize().width;
        if (width <= 0) return;
        int x = getViewport().getViewPosition().x;
        if (bounds.x < x) x = bounds.x;
        else if (bounds.x + bounds.width > x + width) x = bounds.x + bounds.width - width;
        int max = Math.max(0, getViewport().getViewSize().width - width);
        getViewport().setViewPosition(new Point(Math.max(0, Math.min(max, x)), 0));
    }

    @Override
    public void dispose() {
        disposed = true;
        stopScroll();
        tabs.removeHierarchyListener(hierarchyListener);
        getViewport().removeComponentListener(resizeListener);
        manager.removeContentManagerListener(selectionListener);
        Container parent = getParent();
        if (parent != null && tabs.getParent() == getViewport()) {
            setViewportView(null);
            replace(parent, this, tabs);
            parent.revalidate();
            parent.repaint();
        }
    }

    @Override
    public void removeNotify() {
        stopScroll();
        super.removeNotify();
    }
}
