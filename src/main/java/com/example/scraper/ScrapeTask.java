package com.example.scraper;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.*;

public class ScrapeTask implements Runnable {
    private final String url;
    private final List<Map<String, Object>> sharedResultList;

    public ScrapeTask(String url, List<Map<String, Object>> sharedResultList) {
        this.url = url;
        this.sharedResultList = sharedResultList;
    }

    @Override
    public void run() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu");
        options.addArguments("user-agent=Mozilla/5.0");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            driver.get(url);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            Map<String, Object> pageData = new HashMap<>();
            pageData.put("url", url);
            pageData.put("headings", extractText(driver, "h1, h2, h3, h4, h5, h6"));
            pageData.put("paragraphs", extractText(driver, "p"));
            pageData.put("links", extractText(driver, "a"));
            pageData.put("buttons", extractText(driver, "button"));

            sharedResultList.add(pageData);
        } catch (Exception e) {
            Map<String, Object> errorPage = new HashMap<>();
            errorPage.put("url", url);
            errorPage.put("error", "Failed to scrape: " + e.getMessage());
            sharedResultList.add(errorPage);
        } finally {
            driver.quit();
        }
    }

    private List<String> extractText(WebDriver driver, String selector) {
        List<String> texts = new ArrayList<>();
        List<WebElement> elements = driver.findElements(By.cssSelector(selector));

        for (WebElement el : elements) {
            try {
                String tagName = el.getTagName();
                String text;

                if ("a".equalsIgnoreCase(tagName)) {
                    text = el.getAttribute("href");  // Get href for anchor tags
                } else {
                    text = el.getAttribute("innerText");
                    if (text == null || text.trim().isEmpty()) {
                        text = el.getAttribute("textContent");
                    }
                }

                if (text != null && !text.trim().isEmpty()) {
                    texts.add(text.trim());
                }
            } catch (StaleElementReferenceException ignored) {
                // Ignore elements that are no longer attached to the DOM
            }
        }
        return texts;
    }

}
