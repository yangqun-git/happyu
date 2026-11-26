package com.happyu.browser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

/**
 * Reads native Ctrl+wheel zoom without consuming input or writing zoom back to CEF.
 */
final class HappyuZoomSync implements Disposable {
    private static final Logger LOG = Logger.getInstance(HappyuZoomSync.class);
    private final Component component;
    private final DoubleSupplier zoomFactor;
    private final IntConsumer changed;
    private final Executor executor;
    private final Timer timer = new Timer(150, event -> readZoom());
    private final AWTEventListener inputListener = this::onInput;
    private volatile boolean disposed;
    private volatile long revision;
    private long until;
    private boolean reading;

    HappyuZoomSync(Component component, DoubleSupplier zoomFactor, IntConsumer changed, Executor executor) {
        this.component = component;
        this.zoomFactor = zoomFactor;
        this.changed = changed;
        this.executor = executor;
        Toolkit.getDefaultToolkit().addAWTEventListener(inputListener, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    private void onInput(AWTEvent event) {
        if (disposed || !(event instanceof MouseWheelEvent wheel) || !wheel.isControlDown()
                || wheel.isAltDown() || wheel.isMetaDown()) return;
        if (!isBrowserInput(wheel)) return;
        // CEF handles the event after the AWT listener. Older OSR versions may batch wheel input for 500 ms.
        until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
        if (!timer.isRunning()) timer.start();
    }

    private boolean isBrowserInput(MouseWheelEvent wheel) {
        Component source = wheel.getComponent();
        if (source == component || SwingUtilities.isDescendingFrom(source, component)) return true;
        // Windowed JCEF posts native wheel input to the top-level window, with window-relative coordinates.
        if (source != SwingUtilities.getWindowAncestor(component) || !component.isShowing()) return false;
        Point point = SwingUtilities.convertPoint(source, wheel.getPoint(), component);
        Rectangle visible = component instanceof JComponent swing ? swing.getVisibleRect()
                : new Rectangle(0, 0, component.getWidth(), component.getHeight());
        return visible.contains(point);
    }

    void readZoom() {
        if (disposed || System.nanoTime() >= until) {
            timer.stop();
            return;
        }
        if (reading) return;
        reading = true;
        long expectedRevision = revision;
        executor.execute(() -> {
            double factor = Double.NaN;
            try {
                if (!disposed && revision == expectedRevision) factor = zoomFactor.getAsDouble();
            } catch (RuntimeException exception) {
                if (!disposed) LOG.debug("Unable to read happyu page zoom", exception);
            }
            double result = factor;
            SwingUtilities.invokeLater(() -> {
                reading = false;
                if (disposed || revision != expectedRevision || !Double.isFinite(result)) return;
                long percent = Math.round(result * 100);
                if (percent >= 25 && percent <= 500) changed.accept((int) percent);
            });
        });
    }

    void cancel() {
        revision++;
        until = 0;
        timer.stop();
    }

    @Override
    public void dispose() {
        disposed = true;
        cancel();
        Toolkit.getDefaultToolkit().removeAWTEventListener(inputListener);
    }
}
