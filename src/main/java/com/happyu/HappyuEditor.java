package com.happyu;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.intellij.util.ui.JBUI;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.EventFlags;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class HappyuEditor extends UserDataHolderBase implements FileEditor {
    private static final String HOME_URL = "https://www.baidu.com/";

    private final HappyuVirtualFile file;
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private final JPanel root = new JPanel(new BorderLayout());
    private final JBTextField addressField = new JBTextField(HOME_URL);
    private final JButton backButton = new JButton("<");
    private final JButton forwardButton = new JButton(">");
    private final JButton reloadButton = new JButton("Reload");
    private final JButton toggleImagesButton = new JButton();
    private final LafManagerListener lafListener = manager -> SwingUtilities.invokeLater(this::applyIdeTheme);
    private final List<String> navigationHistory = new ArrayList<>();

    private volatile boolean imagesBlocked = true;
    private int navigationIndex = -1;
    private boolean loadingFromHistory = false;
    private JBCefBrowser browser;
    private JBCefClient client;

    HappyuEditor(Project project, HappyuVirtualFile file) {
        this.file = file;
        root.setBorder(BorderFactory.createEmptyBorder());
        root.add(createToolbar(), BorderLayout.NORTH);

        if (JBCefApp.isSupported()) {
            createBrowser();
        } else {
            root.add(new JBLabel("JCEF is not supported in this IDE runtime."), BorderLayout.CENTER);
        }

        project.getMessageBus().connect(this).subscribe(LafManagerListener.TOPIC, lafListener);
        updateToggleButton();
        updateNavigationButtons();
        applyIdeTheme();
    }

    private JComponent createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        toolbar.setBorder(JBUI.Borders.empty(6, 8));

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        navigation.add(backButton);
        navigation.add(forwardButton);
        navigation.add(reloadButton);
        navigation.add(toggleImagesButton);

        backButton.setToolTipText("Back");
        forwardButton.setToolTipText("Forward");
        reloadButton.setToolTipText("Reload");
        toggleImagesButton.setToolTipText("Ctrl+Alt+I");
        addressField.setToolTipText("URL");

        backButton.addActionListener(event -> goBack());
        forwardButton.addActionListener(event -> goForward());
        reloadButton.addActionListener(event -> {
            if (browser != null) {
                browser.getCefBrowser().reload();
            }
        });
        toggleImagesButton.addActionListener(event -> toggleImages());
        addressField.addActionListener(event -> load(addressField.getText()));

        toolbar.add(navigation, BorderLayout.WEST);
        toolbar.add(addressField, BorderLayout.CENTER);
        return toolbar;
    }

    private void createBrowser() {
        client = JBCefApp.getInstance().createClient();
        browser = JBCefBrowser.createBuilder()
                .setClient(client)
                .setUrl(HOME_URL)
                .build();
        browser.setOpenLinksInExternalBrowser(false);
        updateBrowserPageBackground();

        client.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onOpenURLFromTab(CefBrowser cefBrowser, CefFrame frame, String targetUrl, boolean userGesture) {
                openInCurrentTab(targetUrl);
                return true;
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser cefBrowser,
                                                                       CefFrame frame,
                                                                       CefRequest request,
                                                                       boolean isNavigation,
                                                                       boolean isDownload,
                                                                       String requestInitiator,
                                                                       BoolRef disableDefaultHandling) {
                return new CefResourceRequestHandlerAdapter() {
                    @Override
                    public boolean onBeforeResourceLoad(CefBrowser cefBrowser, CefFrame frame, CefRequest request) {
                        return imagesBlocked && isBlockedResource(request);
                    }
                };
            }
        }, browser.getCefBrowser());

        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser cefBrowser, CefFrame frame, String targetUrl, String targetFrameName) {
                openInCurrentTab(targetUrl);
                return true;
            }
        }, browser.getCefBrowser());

        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser cefBrowser, CefFrame frame, CefRequest.TransitionType transitionType) {
                if (frame != null) {
                    SwingUtilities.invokeLater(() -> scheduleContentMode(frame));
                }
            }

            @Override
            public void onLoadingStateChange(CefBrowser cefBrowser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                if (!isLoading) {
                    SwingUtilities.invokeLater(HappyuEditor.this::scheduleContentMode);
                }
            }

            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                if (frame != null) {
                    SwingUtilities.invokeLater(() -> {
                        if (frame.isMain()) {
                            String url = Objects.toString(cefBrowser.getURL(), "");
                            addressField.setText(url);
                            recordNavigation(url);
                        }
                        scheduleContentMode(frame);
                    });
                }
            }
        }, browser.getCefBrowser());

        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser cefBrowser, CefFrame frame, String url) {
                if (frame != null && frame.isMain()) {
                    SwingUtilities.invokeLater(() -> {
                        addressField.setText(Objects.toString(url, ""));
                        recordAddressChange(url);
                    });
                }
            }
        }, browser.getCefBrowser());

        client.addKeyboardHandler(new CefKeyboardHandlerAdapter() {
            @Override
            public boolean onPreKeyEvent(CefBrowser cefBrowser, CefKeyboardHandler.CefKeyEvent event, BoolRef isKeyboardShortcut) {
                if (isToggleShortcut(event)) {
                    SwingUtilities.invokeLater(HappyuEditor.this::toggleImages);
                    return true;
                }
                return false;
            }
        }, browser.getCefBrowser());

        root.add(browser.getComponent(), BorderLayout.CENTER);
    }

    void toggleImages() {
        imagesBlocked = !imagesBlocked;
        updateToggleButton();

        if (browser == null) {
            return;
        }

        if (imagesBlocked) {
            applyContentMode();
        } else {
            browser.getCefBrowser().reloadIgnoreCache();
        }
    }

    private void load(String rawUrl) {
        if (browser == null) {
            return;
        }

        String url = normalizeUrl(rawUrl);
        loadUrl(url, false);
    }

    private void loadUrl(String url, boolean fromHistory) {
        if (browser == null) {
            return;
        }

        loadingFromHistory = fromHistory;
        addressField.setText(url);
        updateBrowserPageBackground();
        browser.loadURL(url);
        updateNavigationButtons();
    }

    private void openInCurrentTab(String rawUrl) {
        String url = normalizeLoadedUrl(rawUrl);
        if (url == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> loadUrl(url, false));
    }

    private void goBack() {
        if (navigationIndex <= 0) {
            return;
        }

        navigationIndex--;
        loadUrl(navigationHistory.get(navigationIndex), true);
    }

    private void goForward() {
        if (navigationIndex >= navigationHistory.size() - 1) {
            return;
        }

        navigationIndex++;
        loadUrl(navigationHistory.get(navigationIndex), true);
    }

    private void recordNavigation(String rawUrl) {
        String url = normalizeLoadedUrl(rawUrl);
        if (url == null) {
            updateNavigationButtons();
            return;
        }

        if (loadingFromHistory) {
            loadingFromHistory = false;
            if (navigationIndex >= 0 && navigationIndex < navigationHistory.size()) {
                navigationHistory.set(navigationIndex, url);
            }
            updateNavigationButtons();
            return;
        }

        if (navigationIndex >= 0 && url.equals(navigationHistory.get(navigationIndex))) {
            updateNavigationButtons();
            return;
        }

        if (navigationIndex < navigationHistory.size() - 1) {
            navigationHistory.subList(navigationIndex + 1, navigationHistory.size()).clear();
        }
        navigationHistory.add(url);
        navigationIndex = navigationHistory.size() - 1;
        updateNavigationButtons();
    }

    private void recordAddressChange(String rawUrl) {
        String url = normalizeLoadedUrl(rawUrl);
        if (url == null) {
            updateNavigationButtons();
            return;
        }

        if (loadingFromHistory) {
            updateNavigationButtons();
            return;
        }

        recordNavigation(url);
    }

    private static String normalizeLoadedUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }

        String value = rawUrl.trim();
        if (value.isEmpty() || value.equals("about:blank") || value.startsWith("data:")) {
            return null;
        }
        return value;
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(navigationIndex > 0);
        forwardButton.setEnabled(navigationIndex >= 0 && navigationIndex < navigationHistory.size() - 1);
    }

    private static String normalizeUrl(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.isEmpty()) {
            return HOME_URL;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return value;
        }
        if (value.contains(".") && !value.contains(" ")) {
            return "https://" + value;
        }
        return "https://www.baidu.com/s?wd=" + value.replace(" ", "+");
    }

    private void updateToggleButton() {
        toggleImagesButton.setText(imagesBlocked ? "Images Off" : "Images On");
    }

    private void applyIdeTheme() {
        Color background = UIUtil.getPanelBackground();
        Color foreground = UIUtil.getLabelForeground();
        root.setBackground(background);
        addressField.setBackground(UIUtil.getTextFieldBackground());
        addressField.setForeground(UIUtil.getTextFieldForeground());
        setComponentColors(root, background, foreground);
        updateBrowserPageBackground();
        scheduleContentMode();
    }

    private void updateBrowserPageBackground() {
        if (browser != null) {
            browser.setPageBackgroundColor(toCssColor(UIUtil.getPanelBackground()));
        }
    }

    private void setComponentColors(JComponent component, Color background, Color foreground) {
        component.setBackground(background);
        component.setForeground(foreground);
        for (java.awt.Component child : component.getComponents()) {
            child.setBackground(background);
            child.setForeground(foreground);
            if (child instanceof JComponent childComponent) {
                setComponentColors(childComponent, background, foreground);
            }
        }
    }

    private void applyContentMode() {
        if (browser == null) {
            return;
        }

        applyContentMode(browser.getCefBrowser().getMainFrame());
    }

    private void scheduleContentMode() {
        if (browser == null) {
            return;
        }

        scheduleContentMode(browser.getCefBrowser().getMainFrame());
    }

    private void scheduleContentMode(@Nullable CefFrame frame) {
        applyContentMode(frame);
        int[] delays = {150, 500, 1000, 2000};
        for (int delay : delays) {
            Timer timer = new Timer(delay, event -> applyContentMode(frame));
            timer.setRepeats(false);
            timer.start();
        }
    }

    private void applyContentMode(@Nullable CefFrame frame) {
        if (browser == null || frame == null || !frame.isValid()) {
            return;
        }

        Color background = UIUtil.getPanelBackground();
        Color foreground = UIUtil.getLabelForeground();
        Color fieldBackground = UIUtil.getTextFieldBackground();
        Color fieldForeground = UIUtil.getTextFieldForeground();
        boolean dark = ColorUtil.isDark(background);
        Color link = dark ? new Color(0x6CA8FF) : new Color(0x1750A6);
        Color border = dark ? new Color(0x4A4D50) : new Color(0xC9CCD0);
        Color card = dark ? ColorUtil.brighter(background, 1) : ColorUtil.darker(background, 1);

        String css = """
                :root { color-scheme: %s; }
                html, body {
                  background: %s !important;
                  color: %s !important;
                }
                *, *::before, *::after {
                  background-color: transparent !important;
                  background-image: none !important;
                  border-color: %s !important;
                  color: %s !important;
                  text-shadow: none !important;
                  box-shadow: none !important;
                  caret-color: %s !important;
                }
                header, nav, aside, main, article, section, table, thead, tbody, tfoot, tr, td, th,
                [class*='card'], [class*='Card'], [class*='item'], [class*='Item'],
                [class*='list'], [class*='List'], [class*='content'], [class*='Content'],
                [class*='container'], [class*='Container'], [class*='wrapper'], [class*='Wrapper'],
                [class*='panel'], [class*='Panel'], [class*='box'], [class*='Box'] {
                  background-color: %s !important;
                }
                a, a *, a:visited, a:visited * { color: %s !important; }
                input, textarea, select, button {
                  background: %s !important;
                  color: %s !important;
                  border-color: %s !important;
                }
                %s
                """.formatted(
                dark ? "dark" : "light",
                toCssColor(background),
                toCssColor(foreground),
                toCssColor(border),
                toCssColor(foreground),
                toCssColor(foreground),
                toCssColor(card),
                toCssColor(link),
                toCssColor(fieldBackground),
                toCssColor(fieldForeground),
                toCssColor(border),
                imagesBlocked ? imageBlockCss() : imageRestoreCss()
        );

        String script = """
                (() => {
                  const styleId = 'happyu-style';
                  const css = '%s';
                  const background = '%s';
                  const foreground = '%s';
                  const card = '%s';
                  const border = '%s';
                  const link = '%s';
                  const imagesBlocked = %s;

                  function forceElement(element) {
                    if (!element || !element.style) return;
                    const tag = (element.tagName || '').toLowerCase();
                    const className = String(element.className || '');

                    if (imagesBlocked && (tag === 'img' || tag === 'picture' || tag === 'video' ||
                        tag === 'audio' || tag === 'canvas' || tag === 'svg' || tag === 'source')) {
                      element.style.setProperty('display', 'none', 'important');
                      element.style.setProperty('visibility', 'hidden', 'important');
                      return;
                    }

                    element.style.setProperty('background-image', 'none', 'important');
                    element.style.setProperty('border-color', border, 'important');
                    element.style.setProperty('color', tag === 'a' ? link : foreground, 'important');

                    const looksLikeCard = /card|item|list|content|container|wrapper|panel|box|rank|hot/i.test(className) ||
                      ['header', 'nav', 'aside', 'main', 'article', 'section', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th'].includes(tag);
                    element.style.setProperty('background-color', looksLikeCard ? card : 'transparent', 'important');
                  }

                  function forceTree() {
                    const root = document.documentElement;
                    if (!root) return;
                    root.style.setProperty('background-color', background, 'important');
                    root.style.setProperty('color', foreground, 'important');
                    if (document.body) {
                      document.body.style.setProperty('background-color', background, 'important');
                      document.body.style.setProperty('color', foreground, 'important');
                    }
                    document.querySelectorAll('*').forEach(forceElement);
                  }

                  function install() {
                    const root = document.documentElement || document.body;
                    if (!root) {
                      setTimeout(install, 50);
                      return;
                    }

                    let style = document.getElementById(styleId);
                    if (!style) {
                      style = document.createElement('style');
                      style.id = styleId;
                      (document.head || root).appendChild(style);
                    }
                    style.textContent = css;

                    forceTree();

                    if (!window.__happyuHappyuObserver) {
                      window.__happyuHappyuObserver = new MutationObserver(() => {
                        window.clearTimeout(window.__happyuHappyuTimer);
                        window.__happyuHappyuTimer = window.setTimeout(forceTree, 30);
                      });
                      window.__happyuHappyuObserver.observe(root, {
                        childList: true,
                        subtree: true
                      });
                    }
                  }

                  install();
                })();
                """.formatted(
                escapeJavaScript(css),
                toCssColor(background),
                toCssColor(foreground),
                toCssColor(card),
                toCssColor(border),
                toCssColor(link),
                imagesBlocked ? "true" : "false"
        );

        frame.executeJavaScript(script, frame.getURL(), 0);
    }

    private static boolean isBlockedResource(CefRequest request) {
        CefRequest.ResourceType type = request.getResourceType();
        if (type == CefRequest.ResourceType.RT_IMAGE ||
                type == CefRequest.ResourceType.RT_FAVICON ||
                type == CefRequest.ResourceType.RT_MEDIA) {
            return true;
        }

        String url = request.getURL();
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(png|jpe?g|gif|webp|svg|ico|bmp|avif)(\\?.*)?$");
    }

    private static boolean isToggleShortcut(CefKeyboardHandler.CefKeyEvent event) {
        if (event.type != CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN &&
                event.type != CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_KEYDOWN) {
            return false;
        }

        boolean controlOrCommand = hasFlag(event.modifiers, EventFlags.EVENTFLAG_CONTROL_DOWN) ||
                hasFlag(event.modifiers, EventFlags.EVENTFLAG_COMMAND_DOWN);
        boolean alt = hasFlag(event.modifiers, EventFlags.EVENTFLAG_ALT_DOWN);
        return controlOrCommand && alt && event.windows_key_code == KeyEvent.VK_I;
    }

    private static boolean hasFlag(int value, int flag) {
        return (value & flag) == flag;
    }

    private static String imageBlockCss() {
        return """
                img, picture, source, video, audio, canvas, svg, iframe[src*='video'] {
                  display: none !important;
                  visibility: hidden !important;
                }
                * {
                  background-image: none !important;
                }
                """;
    }

    private static String imageRestoreCss() {
        return "";
    }

    private static String toCssColor(Color color) {
        return "#%02x%02x%02x".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String escapeJavaScript(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    @Override
    public @NotNull JComponent getComponent() {
        return root;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return addressField;
    }

    @Override
    public @NotNull String getName() {
        return "happyu";
    }

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return new TextEditorState();
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    @Override
    public @Nullable FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public void dispose() {
        if (browser != null) {
            browser.dispose();
            browser = null;
        }
        if (client != null) {
            client.dispose();
            client = null;
        }
        file.invalidate();
    }
}
