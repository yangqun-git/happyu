package com.happyu.browser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Runs script RPC outside CEF callbacks and the EDT without retaining callback-scoped frames.
 */
final class HappyuCefScripts implements Disposable {
    private static final Logger LOG = Logger.getInstance(HappyuCefScripts.class);
    private static final Object MAIN_FRAME = new Object();
    private static final Object ALL_FRAMES = new Object();
    private static final Method FRAME_IDENTIFIER;
    private static final Method FIND_FRAME;

    static {
        try {
            // CEF changed frame identifiers from long to String, and renamed the lookup API.
            // Resolve against the running JBR, keeping compatibility with IDEA 2024.1 onward.
            FRAME_IDENTIFIER = CefFrame.class.getMethod("getIdentifier");
            FIND_FRAME = FRAME_IDENTIFIER.getReturnType() == long.class
                    ? CefBrowser.class.getMethod("getFrame", long.class)
                    : CefBrowser.class.getMethod("getFrameByIdentifier", String.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported JCEF frame API", e);
        }
    }

    private final CefBrowser browser;
    private final Supplier<String> script;
    private final Executor executor;
    private final Map<Object, Target> pending = new LinkedHashMap<>();
    private boolean running;
    private volatile boolean disposed;

    HappyuCefScripts(CefBrowser browser, Supplier<String> script, Executor executor) {
        this.browser = browser;
        this.script = script;
        this.executor = executor;
    }

    void requestAllFrames() {
        // Coalesce repeated theme/toggle events. Enumerate frames only on the worker,
        // including cross-origin frames that JavaScript in the main document cannot reach.
        enqueue(ALL_FRAMES, new Target(ALL_FRAMES, false, null));
    }

    void request(CefFrame callbackFrame) {
        if (disposed || callbackFrame == null || !callbackFrame.isValid()) return;
        try {
            boolean main = callbackFrame.isMain();
            Object id = main ? MAIN_FRAME : FRAME_IDENTIFIER.invoke(callbackFrame);
            // Copy metadata while the callback owns the frame; only the worker acquires a new frame.
            enqueue(id, new Target(id, main, callbackFrame.getURL()));
        } catch (ReflectiveOperationException e) {
            LOG.warn("Unable to identify happyu frame", e);
        }
    }

    private void enqueue(Object key, Target target) {
        synchronized (pending) {
            if (disposed) return;
            pending.put(key, target);
            if (running) return;
            running = true;
        }
        executor.execute(this::drain);
    }

    private void drain() {
        while (true) {
            Target target;
            synchronized (pending) {
                if (disposed || pending.isEmpty()) {
                    pending.clear();
                    running = false;
                    return;
                }
                Object key = pending.keySet().iterator().next();
                target = pending.remove(key);
            }
            CefFrame frame = null;
            try {
                if (disposed) continue;
                if (target.id == ALL_FRAMES) {
                    for (Object id : browser.getFrameIdentifiers()) {
                        if (disposed) break;
                        enqueue(id, new Target(id, false, null));
                    }
                    continue;
                }
                frame = target.main ? browser.getMainFrame() : (CefFrame) FIND_FRAME.invoke(browser, target.id);
                if (disposed || frame == null || !frame.isValid()) continue;
                if (target.url != null && !Objects.equals(target.url, frame.getURL())) continue;
                String snapshot = script.get();
                if (!disposed && !snapshot.isEmpty()) frame.executeJavaScript(snapshot, "", 0);
            } catch (Exception e) {
                if (!disposed) LOG.warn("Unable to update happyu frame styles", e);
            } finally {
                // This frame was acquired by this worker, unlike the borrowed callback frame.
                if (frame != null) {
                    try {
                        frame.dispose();
                    } catch (Exception e) {
                        if (!disposed) LOG.warn("Unable to release happyu frame", e);
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        synchronized (pending) {
            disposed = true;
            pending.clear();
        }
    }

    private record Target(Object id, boolean main, String url) {
    }
}
