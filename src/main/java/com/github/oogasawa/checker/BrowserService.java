package com.github.oogasawa.checker;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BrowserService {

    // Browser candidates tried in order (Linux, macOS, Windows)
    private static final String[] BROWSERS = {
        "google-chrome", "google-chrome-stable", "chromium", "chromium-browser",
        "firefox", "open", "xdg-open"
    };

    /**
     * Open 3 tabs in the system browser:
     *   Tab 1: DuckDuckGo search for the Japanese name
     *   Tab 2: The institution's URL
     *   Tab 3: DuckDuckGo search for "Japanese name English name"
     *
     * Chrome/Chromium accept multiple URLs as arguments and open them as tabs
     * in a single window.
     */
    public void openForReview(String nameJa, String url) {
        String encodedName  = URLEncoder.encode(nameJa, StandardCharsets.UTF_8);
        String encodedQuery = URLEncoder.encode(nameJa + " English name", StandardCharsets.UTF_8);
        String url1 = "https://duckduckgo.com/?q=" + encodedName;
        String url3 = "https://duckduckgo.com/?q=" + encodedQuery;

        // Try Chrome/Chromium first: pass all URLs as arguments → opens as tabs
        for (String browser : new String[]{"google-chrome", "google-chrome-stable", "chromium", "chromium-browser"}) {
            if (tryLaunch(browser, url1, url != null && !url.isEmpty() ? url : null, url3)) return;
        }

        // Fallback: open each URL separately with xdg-open / open
        String opener = System.getProperty("os.name", "").toLowerCase().contains("mac") ? "open" : "xdg-open";
        tryLaunch(opener, url1);
        if (url != null && !url.isEmpty()) tryLaunch(opener, url);
        tryLaunch(opener, url3);
    }

    private boolean tryLaunch(String browser, String... urls) {
        List<String> cmd = new ArrayList<>();
        cmd.add(browser);
        for (String u : urls) {
            if (u != null) cmd.add(u);
        }
        try {
            new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
