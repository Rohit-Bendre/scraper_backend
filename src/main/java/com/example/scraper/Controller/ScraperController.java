package com.example.scraper.Controller;

import com.example.scraper.ScrapeTask;
import org.apache.poi.ss.util.CellRangeAddress;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import org.springframework.web.bind.annotation.*;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;

import java.io.ByteArrayOutputStream;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class ScraperController {
    private List<Map<String, Object>> lastScrapeResults = new ArrayList<>();

    @PostMapping("/hi")
    public List<Map<String, Object>> Scrape_data(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        return scrapeAllPages(url);
    }

    public List<Map<String, Object>> scrapeAllPages(String url) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu");
        options.addArguments("user-agent=Mozilla/5.0");

        WebDriver driver = null;
        WebDriverWait wait = null;
        Set<String> internalLinks = new HashSet<>();
        List<Map<String, Object>> finalResults = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(10);  // Using a thread pool to manage threads
        try {
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            driver.get(url);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
     URL base = new URL(url);
            String domain = base.getHost();
            internalLinks.add(url);

            List<WebElement> anchors = driver.findElements(By.tagName("a"));
            for (WebElement anchor : anchors) {
                try {
                    String href = anchor.getAttribute("href");
                    if (href != null && href.contains(domain)) {
                        internalLinks.add(href.split("#")[0]);
                    }
                } catch (StaleElementReferenceException ignored) {}
            }
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("url", url);
            err.put("error", "Error collecting links: " + e.getMessage());
            finalResults.add(err);
        } finally {
            if (driver != null) {
                driver.quit();  // Ensure WebDriver is quit after use
            }
        }

        List<Callable<Void>> tasks = new ArrayList<>();
        for (String link : internalLinks) {
            tasks.add(() -> {
                try {
                    new ScrapeTask(link, finalResults).run();
                } catch (Exception e) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("url", link);
                    err.put("error", "Error scraping page: " + e.getMessage());
                    finalResults.add(err);
                }
                return null;
            });
        }

        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        lastScrapeResults = finalResults;
        return lastScrapeResults;
    }


    @GetMapping("/download-excel")
    public ResponseEntity<byte[]> downloadExcel() throws Exception {
        if (lastScrapeResults.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Scraped Data");

        CellStyle BoldBorder = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        BoldBorder.setFont(boldFont);
        BoldBorder.setBorderBottom(BorderStyle.MEDIUM);
        BoldBorder.setBorderTop(BorderStyle.MEDIUM);
        BoldBorder.setBorderLeft(BorderStyle.MEDIUM);
        BoldBorder.setBorderRight(BorderStyle.MEDIUM);


     CellStyle centerAlignStyle = workbook.createCellStyle();
        centerAlignStyle.setAlignment(HorizontalAlignment.CENTER);
        centerAlignStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        centerAlignStyle.setBorderBottom(BorderStyle.MEDIUM);
        centerAlignStyle.setBorderTop(BorderStyle.MEDIUM);
        centerAlignStyle.setBorderLeft(BorderStyle.MEDIUM);
        centerAlignStyle.setBorderRight(BorderStyle.MEDIUM);


        CellStyle content_border = workbook.createCellStyle();
        content_border.setWrapText(true);
        content_border.setBorderBottom(BorderStyle.MEDIUM);
        content_border.setBorderLeft(BorderStyle.MEDIUM);
        content_border.setBorderRight(BorderStyle.MEDIUM);
        content_border.setBorderTop(BorderStyle.MEDIUM);

    int rowindx = 0;

        for (Map<String, Object> pageData : lastScrapeResults) {
            String pageUrl = (String) pageData.get("url");

            sheet.createRow(rowindx++);

        Row pageUrlRow = sheet.createRow(rowindx++);
            pageUrlRow.createCell(0).setCellValue("Page: " + pageUrl);
            pageUrlRow.getCell(0).setCellStyle(BoldBorder);
            sheet.addMergedRegion(new CellRangeAddress(rowindx - 1, rowindx - 1, 0, 1));

            for (String type : Arrays.asList("headings", "paragraphs", "links", "buttons", "emails")) {
                List<?> items = (List<?>) pageData.get(type);
                if (items == null || items.isEmpty()) continue;


                Row headerRow = sheet.createRow(rowindx++);
                headerRow.createCell(0).setCellValue("• " + type.toUpperCase());
                headerRow.getCell(0).setCellStyle(BoldBorder);


                Row tableHeaderRow = sheet.createRow(rowindx++);
                Cell snoCell = tableHeaderRow.createCell(0);
                Cell contentCell = tableHeaderRow.createCell(1);

                snoCell.setCellValue("S.No.");
                contentCell.setCellValue("Content");


                snoCell.setCellStyle(centerAlignStyle);
                contentCell.setCellStyle(BoldBorder);


                sheet.setColumnWidth(0, 256 * 10);
                sheet.setColumnWidth(1, 256 * 70);

                int count = 1;
                for (Object obj : items) {
                    Row row = sheet.createRow(rowindx++);
                    Cell SnoCell = row.createCell(0);
                    Cell rowContentCell = row.createCell(1);

                    SnoCell.setCellValue(count++);
                    rowContentCell.setCellValue(obj.toString());


                    SnoCell.setCellStyle(centerAlignStyle);
                    rowContentCell.setCellStyle(content_border);
                }

                rowindx++;
            }

            rowindx++;
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        workbook.write(stream);
        workbook.close();

        byte[] excelData = stream.toByteArray();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=scraped_data.xlsx");

        return new ResponseEntity<>(excelData, headers, HttpStatus.OK);
    }


}
