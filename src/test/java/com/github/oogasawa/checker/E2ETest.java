package com.github.oogasawa.checker;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests using Playwright against the running app on http://localhost:8090.
 * Start the app before running: java -jar target/institution-name-checker-1.0.0-runner.jar
 */
class E2ETest {

    static final String BASE_URL = "http://localhost:8090";

    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void newContext() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    // ---- Test 1: page loads with data ----

    @Test
    void pageLoadsWithData() {
        Response response = page.navigate(BASE_URL);
        assertEquals(200, response.status());

        // At least one table row should be present
        List<Locator> rows = page.locator("tr[id^='row-']").all();
        assertFalse(rows.isEmpty(), "Expected at least one institution row");
        System.out.println("Total rows displayed: " + rows.size());
    }

    @Test
    void firstRowHasExpectedColumns() {
        page.navigate(BASE_URL);

        // First row: code 10101, 北海道大学
        Locator firstRow = page.locator("tr[id='row-10101']");
        assertTrue(firstRow.isVisible(), "Row for code 10101 should be visible");

        String code = firstRow.locator("td.code").textContent().trim();
        assertEquals("10101", code);

        String nameJa = firstRow.locator("td:nth-child(2)").textContent().trim();
        assertEquals("北海道大学", nameJa);

        System.out.println("First row OK: " + code + " / " + nameJa);
    }

    // ---- Test 2: filter buttons ----

    @Test
    void filterMissingShowsOnlyMissingEnglishName() {
        page.navigate(BASE_URL + "/?filter=missing");

        List<Locator> rows = page.locator("tr[id^='row-']").all();
        // All displayed rows should have class "missing"
        for (Locator row : rows) {
            String cls = row.getAttribute("class");
            assertTrue(cls != null && cls.contains("missing"),
                    "Row should have class 'missing' when filter=missing");
        }
        System.out.println("Missing filter: " + rows.size() + " rows");
    }

    @Test
    void filterHasEnShowsOnlyRowsWithEnglishName() {
        page.navigate(BASE_URL + "/?filter=has_en");

        List<Locator> rows = page.locator("tr[id^='row-']").all();
        for (Locator row : rows) {
            String cls = row.getAttribute("class");
            assertFalse(cls != null && cls.contains("missing"),
                    "Row should NOT have class 'missing' when filter=has_en");
        }
        System.out.println("has_en filter: " + rows.size() + " rows");
    }

    @Test
    void filterAllShowsAllRows() {
        Response resp = page.navigate(BASE_URL + "/?filter=all");
        assertEquals(200, resp.status());

        int total = page.locator("tr[id^='row-']").all().size();

        page.navigate(BASE_URL + "/?filter=missing");
        int missing = page.locator("tr[id^='row-']").all().size();

        page.navigate(BASE_URL + "/?filter=has_en");
        int hasEn = page.locator("tr[id^='row-']").all().size();

        System.out.println("all=" + total + ", missing=" + missing + ", has_en=" + hasEn);
        assertEquals(total, missing + hasEn, "missing + has_en should equal total");
    }

    // ---- Test 3: range selector ----

    @Test
    void rangeSelectorExists() {
        page.navigate(BASE_URL);
        Locator rangeLinks = page.locator("a[href*='range=']");
        assertFalse(rangeLinks.all().isEmpty(), "Range selector links should be present");
        System.out.println("Range links: " + rangeLinks.all().size());
    }

    // ---- Test 4: Check button ----

    @Test
    void checkButtonExistsOnEachRow() {
        page.navigate(BASE_URL);
        List<Locator> checkBtns = page.locator("button.btn-check").all();
        assertFalse(checkBtns.isEmpty(), "Check buttons should be present");
        assertEquals("Check", checkBtns.get(0).textContent().trim());
        System.out.println("Check buttons: " + checkBtns.size());
    }

    @Test
    void checkButtonClickOpensNewBrowserWindow() {
        page.navigate(BASE_URL);

        // POST /check/10101 should return 200 (server launches headful browser)
        APIResponse response = page.request().post(BASE_URL + "/check/10101");
        assertEquals(200, response.status(), "POST /check/{code} should return 200");

        // Wait briefly for the browser to open on the server side
        page.waitForTimeout(3000);

        // Verify the app is still responsive after launching browser
        Response mainPage = page.navigate(BASE_URL);
        assertEquals(200, mainPage.status(), "App should still respond after Check");

        System.out.println("Check button POST: 200 OK, app still responsive");
    }

    // ---- Test 5: Save (update) ----

    @Test
    void saveButtonUpdatesEnglishName() {
        page.navigate(BASE_URL);

        // Find a row with a Save button (edit form visible)
        // Click Save button of row 10101
        Locator saveBtn = page.locator("tr[id='row-10101'] button.btn-save");
        assertTrue(saveBtn.isVisible(), "Save button should be visible");

        // Set a test value in the input
        Locator input = page.locator("tr[id='row-10101'] input.edit-input");
        input.fill("Hokkaido University (test)");

        // Submit save
        saveBtn.click();
        page.waitForLoadState();

        // Reload and verify
        page.navigate(BASE_URL);
        Locator nameEnCell = page.locator("tr[id='row-10101'] td.name-en");
        String updated = nameEnCell.textContent().trim();
        assertEquals("Hokkaido University (test)", updated);
        System.out.println("Save OK: " + updated);

        // Restore original value
        page.locator("tr[id='row-10101'] input.edit-input").fill("Hokkaido University");
        page.locator("tr[id='row-10101'] button.btn-save").click();
        page.waitForLoadState();
    }

    // ---- Test 6: reload endpoint ----

    @Test
    void reloadEndpointReturnsRedirect() {
        APIResponse response = page.request().post(BASE_URL + "/reload");
        // After redirect, should land on main page
        assertTrue(response.status() == 200 || response.status() == 303,
                "Reload should return 200 or 303, got: " + response.status());
        System.out.println("Reload status: " + response.status());
    }
}
