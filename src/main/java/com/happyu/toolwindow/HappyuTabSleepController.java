package com.happyu.toolwindow;

import com.happyu.browser.HappyuBrowserPanel;
import com.happyu.settings.HappyuSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Tracks time spent in the background without polling JCEF or a page's DOM.
 */
final class HappyuTabSleepController implements Disposable {
    private final Project project;
    private final ContentManager manager;
    private final HappyuSettings settings;
    private final LongSupplier clock;
    private final Map<Content, Long> backgroundSince = new IdentityHashMap<>();
    private final Set<Content> queued = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Timer timer = new Timer(30_000, event -> checkIdleTabs());
    private Content selected;
    private boolean disposed;
    private final ContentManagerListener listener = new ContentManagerListener() {
        @Override
        public void contentAdded(@NotNull ContentManagerEvent event) {
            backgroundSince.put(event.getContent(), clock.getAsLong());
        }

        @Override
        public void contentRemoved(@NotNull ContentManagerEvent event) {
            backgroundSince.remove(event.getContent());
            queued.remove(event.getContent());
            if (selected == event.getContent()) selected = null;
        }

        @Override
        public void selectionChanged(@NotNull ContentManagerEvent event) {
            Content next = manager.getSelectedContent();
            // Switching tabs first emits a transient empty selection.
            if (next == null || next == selected) return;
            if (selected != null && backgroundSince.containsKey(selected)) {
                backgroundSince.put(selected, clock.getAsLong());
            }
            selected = next;
        }
    };

    HappyuTabSleepController(Project project, ContentManager manager) {
        this(project, manager, System::nanoTime);
    }

    HappyuTabSleepController(Project project, ContentManager manager, LongSupplier clock) {
        this.project = project;
        this.manager = manager;
        this.clock = clock;
        settings = project.getService(HappyuSettings.class);
        long now = clock.getAsLong();
        for (Content content : manager.getContents()) backgroundSince.put(content, now);
        selected = manager.getSelectedContent();
        manager.addContentManagerListener(listener);
        project.getMessageBus().connect(this).subscribe(HappyuSettings.TOPIC, this::refreshTimer);
        Disposer.register(manager, this);
        refreshTimer();
    }

    private void refreshTimer() {
        if (!disposed && settings.getState().autoSleepEnabled) timer.start();
        else timer.stop();
    }

    void checkIdleTabs() {
        if (disposed || project.isDisposed() || manager.isDisposed() || !settings.getState().autoSleepEnabled) return;
        long now = clock.getAsLong();
        for (Content content : manager.getContents()) {
            if (!isIdle(content, now) || !queued.add(content)) continue;
            // Release one browser per UI event rather than closing many in one EDT task.
            SwingUtilities.invokeLater(() -> {
                queued.remove(content);
                if (!disposed && !project.isDisposed() && !manager.isDisposed() && isIdle(content, clock.getAsLong())) {
                    HappyuToolWindowFactory.sleepTab(content);
                }
            });
        }
    }

    private boolean isIdle(Content content, long now) {
        HappyuSettings.Options options = settings.getState();
        if (!options.autoSleepEnabled || content == manager.getSelectedContent()) return false;
        HappyuBrowserPanel panel = HappyuToolWindowFactory.getBrowserPanel(content);
        Long since = backgroundSince.get(content);
        return panel != null && panel.isBrowserStarted() && !panel.isSleeping() && since != null
                && now - since >= TimeUnit.MINUTES.toNanos(options.idleMinutes);
    }

    @Override
    public void dispose() {
        disposed = true;
        timer.stop();
        manager.removeContentManagerListener(listener);
        backgroundSince.clear();
        queued.clear();
        selected = null;
    }
}
