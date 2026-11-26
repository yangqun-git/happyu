package com.happyu.browser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;

/**
 * One lazily created client per project; each tab still owns its browser.
 */
@Service(Service.Level.PROJECT)
public final class HappyuBrowserClient implements Disposable {
    private JBCefClient client;
    private boolean disposed;

    synchronized JBCefBrowser createBrowser(String url) {
        if (disposed) throw new IllegalStateException("happyu browser client is disposed");
        if (client == null) {
            client = JBCefApp.getInstance().createClient();
            Disposer.register(this, client);
        }
        JBCefBrowser browser = JBCefBrowser.createBuilder().setClient(client).setUrl(url).build();
        // Children are disposed in reverse order: all browsers close before the shared client.
        Disposer.register(this, browser);
        return browser;
    }

    @Override
    public synchronized void dispose() {
        disposed = true;
        client = null;
    }
}
