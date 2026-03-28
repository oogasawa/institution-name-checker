package com.github.oogasawa.checker;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BrowserService {

    /**
     * Open 3 tabs in the system browser:
     *   Tab 1: DuckDuckGo search for the Japanese name
     *   Tab 2: The institution's URL
     *   Tab 3: DuckDuckGo search for "Japanese name English name"
     */
    public void openForReview(String nameJa, String url) {
        String encodedName  = URLEncoder.encode(nameJa, StandardCharsets.UTF_8);
        String encodedQuery = URLEncoder.encode(nameJa + " English name", StandardCharsets.UTF_8);
        String url1 = "https://duckduckgo.com/?q=" + encodedName;
        String url3 = "https://duckduckgo.com/?q=" + encodedQuery;

        List<String> urls = new ArrayList<>();
        urls.add(url1);
        if (url != null && !url.isEmpty()) urls.add(url);
        urls.add(url3);

        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            openOnWindows(urls);
        } else if (os.contains("mac")) {
            openOnMac(urls);
        } else {
            openOnLinux(urls);
        }
    }

    private void openOnWindows(List<String> urls) {
        // Try Chrome first (multiple URLs = multiple tabs in one window)
        for (String chrome : new String[]{
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe"}) {
            if (chrome != null && new java.io.File(chrome).exists()) {
                List<String> cmd = new ArrayList<>();
                cmd.add(chrome);
                cmd.addAll(urls);
                if (tryLaunch(cmd)) return;
            }
        }
        // Try Firefox
        for (String ff : new String[]{
                "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
                "C:\\Program Files (x86)\\Mozilla Firefox\\firefox.exe"}) {
            if (new java.io.File(ff).exists()) {
                List<String> cmd = new ArrayList<>();
                cmd.add(ff);
                cmd.addAll(urls);
                if (tryLaunch(cmd)) return;
            }
        }
        // Fallback: open each URL with default browser via cmd /c start
        for (String u : urls) {
            tryLaunch(List.of("cmd", "/c", "start", "", u));
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private void openOnMac(List<String> urls) {
        // Try Chrome
        String chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
        if (new java.io.File(chrome).exists()) {
            List<String> cmd = new ArrayList<>();
            cmd.add(chrome);
            cmd.addAll(urls);
            if (tryLaunch(cmd)) return;
        }
        // Fallback: open each URL with default browser
        for (String u : urls) {
            tryLaunch(List.of("open", u));
        }
    }

    private void openOnLinux(List<String> urls) {
        // Try Chrome/Chromium (multiple URLs = multiple tabs)
        for (String browser : new String[]{"google-chrome", "google-chrome-stable", "chromium", "chromium-browser"}) {
            List<String> cmd = new ArrayList<>();
            cmd.add(browser);
            cmd.addAll(urls);
            if (tryLaunch(cmd)) return;
        }
        // Fallback: xdg-open each URL
        for (String u : urls) {
            tryLaunch(List.of("xdg-open", u));
        }
    }

    private boolean tryLaunch(List<String> cmd) {
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
