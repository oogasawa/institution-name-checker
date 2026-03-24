package com.github.oogasawa.checker;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.microsoft.playwright.*;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BrowserService {

    private Playwright playwright;
    private Browser browser;

    /**
     * Open 3 tabs in a headful browser:
     *   Tab 1: DuckDuckGo search for the Japanese name
     *   Tab 2: The institution's URL
     *   Tab 3: DuckDuckGo search for "Japanese name English name"
     */
    public void openForReview(String nameJa, String url) {
        // Close previous browser if open
        close();

        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(false));

        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setLocale("en-US"));

        // Tab 1: DuckDuckGo search for Japanese name
        String encodedName = URLEncoder.encode(nameJa, StandardCharsets.UTF_8);
        Page page1 = context.newPage();
        page1.navigate("https://duckduckgo.com/?q=" + encodedName);

        // Tab 2: Institution URL (if available)
        if (url != null && !url.isEmpty()) {
            Page page2 = context.newPage();
            page2.navigate(url);
        }

        // Tab 3: DuckDuckGo search for "Japanese name English name"
        String encodedQuery = URLEncoder.encode(nameJa + " English name", StandardCharsets.UTF_8);
        Page page3 = context.newPage();
        page3.navigate("https://duckduckgo.com/?q=" + encodedQuery);
    }

    public void close() {
        try {
            if (browser != null) {
                browser.close();
                browser = null;
            }
            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
