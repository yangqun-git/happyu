package com.happyu.browser;

import junit.framework.TestCase;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class HappyuBrowserUrlTest extends TestCase {
    public void testSearchTermsRoundTripWithoutCreatingExtraParametersOrFragments() {
        for (String term : List.of("C++ 教程", "a&b", "issue #123", "100% 完成", "a=b?c", "中文 🔍")) {
            URI uri = URI.create(HappyuBrowserPanel.normalizeUrl(term));
            assertNull(uri.getRawFragment());
            String query = uri.getRawQuery();
            assertTrue(query.startsWith("wd="));
            assertEquals(1, query.split("&").length);
            assertEquals(term, URLDecoder.decode(query.substring(3), StandardCharsets.UTF_8));
        }
    }

    public void testUrlsRemainUnchangedAndBlankInputUsesHome() {
        String url = "https://example.com/search?q=C%2B%2B&lang=zh#123";
        assertEquals(url, HappyuBrowserPanel.normalizeUrl("  " + url + "  "));
        assertEquals("https://example.com/path?q=a&b=c#123",
                HappyuBrowserPanel.normalizeUrl("example.com/path?q=a&b=c#123"));
        assertEquals(HappyuBrowserPanel.HOME_URL, HappyuBrowserPanel.normalizeUrl(null));
        assertEquals(HappyuBrowserPanel.HOME_URL, HappyuBrowserPanel.normalizeUrl("  "));
    }
}
