package com.happyu.browser;

import junit.framework.TestCase;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class HappyuCefScriptsTest extends TestCase {
    private final ArrayDeque<Runnable> workers = new ArrayDeque<>();
    private final List<String> events = new ArrayList<>();
    private final AtomicReference<String> script = new AtomicReference<>("theme");
    private int frameLookups;
    private int frameEnumerations;
    private final List<Object> lookedUpIds = new ArrayList<>();

    public void testCallbackOnlyCopiesMetadataAndWorkerAcquiresItsOwnFrame() {
        AtomicBoolean callbackAlive = new AtomicBoolean(true);
        CefFrame callback = frame(false, "https://example.com/frame", callbackAlive, false);
        HappyuCefScripts queue = queue(frame(false, "https://example.com/frame", null, true));
        queue.request(callback);
        callbackAlive.set(false);
        assertEquals(0, frameLookups);
        assertTrue(events.isEmpty());

        workers.remove().run();
        assertEquals(1, frameLookups);
        assertEquals(List.of("script:theme", "dispose"), events);
    }

    public void testRepeatedLoadEventsAreCoalescedAndUseLatestTheme() {
        HappyuCefScripts queue = queue(frame(true, "https://example.com/", null, true));
        CefFrame callback = frame(true, "https://example.com/", new AtomicBoolean(true), false);
        for (int i = 0; i < 100; i++) queue.request(callback);
        assertEquals(1, workers.size());
        script.set("new theme");
        workers.remove().run();
        assertEquals(1, frameLookups);
        assertEquals(List.of("script:new theme", "dispose"), events);
    }

    public void testNavigationMakesOldQueuedFrameWorkObsolete() {
        HappyuCefScripts queue = queue(frame(false, "https://example.com/new", null, true));
        queue.request(frame(false, "https://example.com/old", new AtomicBoolean(true), false));
        workers.remove().run();
        assertEquals(List.of("dispose"), events);
    }

    public void testClosingPanelCancelsPendingWorkWithoutNativeCalls() {
        HappyuCefScripts queue = queue(frame(true, "https://example.com/", null, true));
        queue.requestAllFrames();
        queue.dispose();
        queue.requestAllFrames();
        workers.remove().run();
        assertEquals(0, frameLookups);
        assertEquals(0, frameEnumerations);
        assertTrue(events.isEmpty());
        assertTrue(workers.isEmpty());
    }

    public void testAllFramesRefreshIsDeferredCoalescedAndIncludesNestedCrossOriginFrames() {
        Map<Object, CefFrame> frames = new LinkedHashMap<>();
        frames.put(frameId(1), frame(true, "https://example.com/", null, true));
        frames.put(frameId(2), frame(false, "https://other.example/frame", null, true));
        frames.put(frameId(3), frame(false, "https://nested.example/frame", null, true));
        HappyuCefScripts queue = queue(frames);
        for (int i = 0; i < 100; i++) queue.requestAllFrames();
        assertEquals(1, workers.size());
        assertEquals(0, frameEnumerations);
        assertEquals(0, frameLookups);
        script.set("latest theme and images off");
        workers.remove().run();
        assertEquals(1, frameEnumerations);
        assertEquals(new ArrayList<>(frames.keySet()), lookedUpIds);
        assertEquals(List.of("script:latest theme and images off", "dispose",
                "script:latest theme and images off", "dispose",
                "script:latest theme and images off", "dispose"), events);
    }

    public void testRemovedFrameDoesNotPreventUpdatingRemainingFrames() {
        Map<Object, CefFrame> frames = new LinkedHashMap<>();
        frames.put(frameId(1), null);
        frames.put(frameId(2), frame(false, "https://example.com/remaining", null, true));
        HappyuCefScripts queue = queue(frames);
        queue.requestAllFrames();
        workers.remove().run();
        assertEquals(2, frameLookups);
        assertEquals(List.of("script:theme", "dispose"), events);
    }

    public void testFrameLoadedAfterRefreshReceivesCurrentSettings() {
        Map<Object, CefFrame> frames = new LinkedHashMap<>();
        HappyuCefScripts queue = queue(frames);
        queue.requestAllFrames();
        workers.remove().run();
        assertTrue(events.isEmpty());
        script.set("new theme and images off");
        frames.put(frameId(42), frame(false, "https://new.example/frame", null, true));
        queue.request(frame(false, "https://new.example/frame", new AtomicBoolean(true), false));
        workers.remove().run();
        assertEquals(List.of("script:new theme and images off", "dispose"), events);
    }

    private HappyuCefScripts queue(Map<Object, CefFrame> frames) {
        CefBrowser browser = (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(),
                new Class<?>[]{CefBrowser.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getFrameIdentifiers" -> {
                        frameEnumerations++;
                        yield new Vector<>(frames.keySet());
                    }
                    case "getFrame", "getFrameByIdentifier" -> {
                        frameLookups++;
                        lookedUpIds.add(args[0]);
                        yield frames.get(args[0]);
                    }
                    default -> throw new AssertionError("Unexpected browser call: " + method.getName());
                });
        return new HappyuCefScripts(browser, script::get, workers::add);
    }

    private static Object frameId(int id) {
        try {
            return CefFrame.class.getMethod("getIdentifier").getReturnType() == long.class
                    ? (Object) (long) id : "frame-" + id;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private HappyuCefScripts queue(CefFrame acquired) {
        CefBrowser browser = (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(),
                new Class<?>[]{CefBrowser.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getMainFrame", "getFrame", "getFrameByIdentifier":
                            frameLookups++;
                            return acquired;
                        default: throw new AssertionError("Unexpected browser call: " + method.getName());
                    }
                });
        return new HappyuCefScripts(browser, script::get, workers::add);
    }

    private CefFrame frame(boolean main, String url, AtomicBoolean callbackAlive, boolean owned) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(),
                new Class<?>[]{CefFrame.class}, (proxy, method, args) -> {
                    if (callbackAlive != null && !callbackAlive.get()) {
                        throw new AssertionError("Callback frame accessed after callback returned");
                    }
                    switch (method.getName()) {
                        case "isValid": return true;
                        case "isMain": return main;
                        case "getURL": return url;
                        case "getIdentifier":
                            return method.getReturnType() == long.class ? (Object) 42L : "frame-42";
                        case "executeJavaScript":
                            assertTrue("RPC executed inside callback", owned);
                            events.add("script:" + args[0]);
                            return null;
                        case "dispose":
                            assertTrue("Borrowed callback frame disposed", owned);
                            events.add("dispose");
                            return null;
                        default: throw new AssertionError("Unexpected frame call: " + method.getName());
                    }
                });
    }
}
