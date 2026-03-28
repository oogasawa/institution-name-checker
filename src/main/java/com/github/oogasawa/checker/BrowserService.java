package com.github.oogasawa.checker;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BrowserService {

    private static final Logger LOG = Logger.getLogger(BrowserService.class.getName());

    /**
     * Open a new browser window with 3 tabs:
     *   Tab 1: DuckDuckGo search for the Japanese name
     *   Tab 2: The institution's URL
     *   Tab 3: DuckDuckGo search for "Japanese name English name"
     */
    public void openForReview(String nameJa, String url) {
        LOG.info("openForReview called: nameJa=" + nameJa + ", url=" + url);

        String encodedName  = URLEncoder.encode(nameJa, StandardCharsets.UTF_8);
        String encodedQuery = URLEncoder.encode(nameJa + " English name", StandardCharsets.UTF_8);
        String url1 = "https://duckduckgo.com/?q=" + encodedName;
        String url3 = "https://duckduckgo.com/?q=" + encodedQuery;

        List<String> urls = new ArrayList<>();
        urls.add(url1);
        if (url != null && !url.isEmpty()) urls.add(url);
        urls.add(url3);

        LOG.info("URLs to open: " + urls);

        String os = System.getProperty("os.name", "");
        LOG.info("os.name=" + os);

        if (os.toLowerCase().contains("win")) {
            openOnWindows(urls);
        } else if (os.toLowerCase().contains("mac")) {
            openOnMac(urls);
        } else {
            openOnLinux(urls);
        }
    }

    private void openOnWindows(List<String> urls) {
        // Try Chrome with --new-window
        for (String chrome : new String[]{
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe"}) {
            LOG.info("Checking Chrome path: " + chrome);
            if (chrome != null && new java.io.File(chrome).exists()) {
                LOG.info("Found Chrome at: " + chrome);
                List<String> cmd = new ArrayList<>();
                cmd.add(chrome);
                cmd.add("--new-window");
                cmd.addAll(urls);
                if (tryLaunch(cmd)) return;
            } else {
                LOG.info("Not found: " + chrome);
            }
        }
        // Try Edge with --new-window
        String edge = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
        LOG.info("Checking Edge path: " + edge);
        if (new java.io.File(edge).exists()) {
            LOG.info("Found Edge at: " + edge);
            List<String> cmd = new ArrayList<>();
            cmd.add(edge);
            cmd.add("--new-window");
            cmd.addAll(urls);
            if (tryLaunch(cmd)) return;
        } else {
            LOG.info("Not found: " + edge);
        }
        // Try Firefox
        for (String ff : new String[]{
                "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
                "C:\\Program Files (x86)\\Mozilla Firefox\\firefox.exe"}) {
            LOG.info("Checking Firefox path: " + ff);
            if (new java.io.File(ff).exists()) {
                LOG.info("Found Firefox at: " + ff);
                List<String> cmd = new ArrayList<>();
                cmd.add(ff);
                cmd.add("--new-window");
                cmd.addAll(urls);
                if (tryLaunch(cmd)) return;
            } else {
                LOG.info("Not found: " + ff);
            }
        }
        // Fallback: open each URL with default browser via cmd /c start
        LOG.warning("No browser found directly, falling back to cmd /c start");
        for (String u : urls) {
            tryLaunch(List.of("cmd", "/c", "start", "", u));
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private void openOnMac(List<String> urls) {
        // Try Chrome with --new-window
        String chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
        LOG.info("Checking Chrome path: " + chrome);
        if (new java.io.File(chrome).exists()) {
            LOG.info("Found Chrome at: " + chrome);
            List<String> cmd = new ArrayList<>();
            cmd.add(chrome);
            cmd.add("--new-window");
            cmd.addAll(urls);
            if (tryLaunch(cmd)) return;
        } else {
            LOG.info("Not found: " + chrome);
        }
        // Fallback: open each URL with default browser
        LOG.info("Falling back to 'open' command");
        for (String u : urls) {
            tryLaunch(List.of("open", u));
        }
    }

    private void openOnLinux(List<String> urls) {
        // Try Chrome/Chromium with --new-window
        for (String browser : new String[]{"google-chrome", "google-chrome-stable", "chromium", "chromium-browser"}) {
            LOG.info("Trying browser command: " + browser);
            List<String> cmd = new ArrayList<>();
            cmd.add(browser);
            cmd.add("--new-window");
            cmd.addAll(urls);
            if (tryLaunch(cmd)) return;
        }
        // Fallback: xdg-open each URL
        LOG.info("Falling back to xdg-open");
        for (String u : urls) {
            tryLaunch(List.of("xdg-open", u));
        }
    }

    private boolean tryLaunch(List<String> cmd) {
        LOG.info("Launching: " + cmd);
        try {
            Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            LOG.info("Process started successfully, pid=" + p.pid());
            return true;
        } catch (IOException e) {
            LOG.warning("Failed to launch: " + e.getMessage());
            return false;
        }
    }
}
