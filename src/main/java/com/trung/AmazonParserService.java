package com.trung;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmazonParserService {

    // =================== REGEX PATTERNS (compile một lần, dùng lại) ===================
    private static final String CURR = "(?:\\p{Sc}|VND|USD|JPY|EUR|GBP)";
    private static final String NUM  = "(?:[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)";

    // Bắt buộc currency symbol đứng TRƯỚC hoặc SAU con số
    private static final Pattern PRICE_WITH_CURRENCY = Pattern.compile(
            CURR + "\\s*" + NUM + "|" + NUM + "\\s*" + CURR,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    // Fallback: bắt mọi con số
    private static final Pattern NUM_ONLY = Pattern.compile(NUM);

    // ==================================================================================

    /**
     * Helper: Quét nhanh danh sách CSS selectors, trả về text đầu tiên tìm thấy.
     * Tạm đặt implicitWait = 0 để findElements trả về ngay lập tức nếu không match,
     * tránh chờ 5s/selector → tiết kiệm hàng chục giây khi scan nhiều selectors.
     */
    private static String findFirstText(WebDriver driver, String[] selectors, boolean useTextContent) {
        // Lưu lại implicit wait rồi đặt về 0 để scan nhanh
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        try {
            for (String sel : selectors) {
                try {
                    List<WebElement> elements = driver.findElements(By.cssSelector(sel));
                    if (!elements.isEmpty()) {
                        String text = useTextContent
                                ? elements.get(0).getAttribute("textContent")
                                : elements.get(0).getText();
                        if (text != null) {
                            text = text.trim();
                            if (!text.isEmpty()) return text;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } finally {
            // Khôi phục implicit wait về mặc định
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        }
        return null;
    }

    // ======================= EXTRACT TITLE =======================
    public static String extractTitle(WebDriver driver) {
        String[] selectors = {
                "#productTitle",
                "#title span",
                "h1#title span",
                "#titleSection #productTitle",
                "span#productTitle"
        };
        String result = findFirstText(driver, selectors, false);
        return result != null ? result : "N/A";
    }

    // ======================= EXTRACT PRICE =======================
    public static String extractPrice(WebDriver driver) {

        // ---------- GIAI ĐOẠN 1: Lấy giá từ BUYBOX (chính xác nhất) ----------
        // Sắp xếp theo độ chính xác giảm dần.
        // span.a-offscreen trong buybox/corePrice chứa giá sạch dạng "¥10,800"
        String[] primarySelectors = {
                "#corePriceDisplay_desktop_feature_div span.a-offscreen",
                "#corePrice_feature_div span.a-offscreen",
                "span.a-price > span.a-offscreen",
                "#priceblock_ourprice",
                "#priceblock_dealprice",
                "#priceblock_saleprice",
                "span#price_inside_buybox",
        };

        String rawPrice = findFirstText(driver, primarySelectors, true);

        // ---------- GIAI ĐOẠN 2: Fallback sang .a-price-whole ----------
        if (rawPrice == null || rawPrice.isEmpty()) {
            String[] fallbackSelectors = {
                    "#corePriceDisplay_desktop_feature_div .a-price-whole",
                    "#corePrice_desktop .a-price-whole",
                    "#corePrice_feature_div .a-price-whole",
                    // KHÔNG dùng ".a-price-whole" chung → quá rộng, dễ bắt nhầm giá từ "other sellers"
            };
            rawPrice = findFirstText(driver, fallbackSelectors, true);
        }

        // ---------- GIAI ĐOẠN 3: Fallback XPath ----------
        if (rawPrice == null || rawPrice.isEmpty()) {
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);
            try {
                List<WebElement> elements = driver.findElements(By.xpath("//*[@id='_price']/span"));
                if (!elements.isEmpty()) {
                    rawPrice = elements.get(0).getText().trim();
                }
            }
            catch (Exception ignored) {
            }
            finally {
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            }
        }

        if (rawPrice == null || rawPrice.isEmpty()) return "N/A";

        // ---------- GIAI ĐOẠN 4: Parse giá bằng Regex ----------
        return parsePrice(rawPrice);
    }

    /**
     * Trích xuất giá từ chuỗi thô.
     * Ưu tiên 1: Tìm con số đi kèm ký hiệu tiền tệ (¥10,800, $29.99, 150,000 VND)
     * Ưu tiên 2 (fallback): Nếu không có ký hiệu tiền tệ → lấy con số lớn nhất
     */
    static String parsePrice(String rawPrice) {
        // Nếu rawPrice đã sạch (chỉ có ký hiệu + số), trả về luôn
        String trimmed = rawPrice.trim();
        if (trimmed.matches("^" + CURR + "\\s*" + NUM + "$")
                || trimmed.matches("^" + NUM + "\\s*" + CURR + "$")) {
            return trimmed;
        }

        // Tìm match đầu tiên có currency symbol
        Matcher m = PRICE_WITH_CURRENCY.matcher(rawPrice);
        if (m.find()) {
            return m.group().trim();
        }

        // Fallback: Lấy con số LỚN NHẤT (giá tiền luôn > số đếm 1, 2, 3...)
        Matcher numMatcher = NUM_ONLY.matcher(rawPrice);
        String bestMatch = null;
        double maxValue = -1;

        while (numMatcher.find()) {
            String raw = numMatcher.group();
            try {
                double value = Double.parseDouble(raw.replace(",", ""));
                if (value > maxValue) {
                    maxValue = value;
                    bestMatch = raw;
                }
            } catch (NumberFormatException ignored) {}
        }

        return bestMatch != null ? bestMatch : rawPrice;
    }

    // ======================= EXTRACT STOCK =======================
    public static String extractStock(WebDriver driver) {
        String[] selectors = {
                "#availability",
                "#availabilityInsideBuyBox_feature_div",
                "#availability_feature_div",
                "#outOfStock",
        };
        String rawText = findFirstText(driver, selectors, false);

        if (rawText == null || rawText.isEmpty()) return "Unknown";
        return parseStock(rawText);
    }

    /**
     * Parse stock status từ chuỗi thô Amazon.
     * Package-private để unit test được.
     */
    static String parseStock(String rawText) {
        if (rawText == null || rawText.isEmpty()) return "Unknown";
        String lower = rawText.toLowerCase().trim();

        Pattern normalizedLeftPattern = Pattern.compile(
                "(?:only\\s*)?(\\d+)\\s*(?:left in stock|left)|(?:\\u6b8b\\u308a)\\s*(\\d+)\\s*(?:\\u70b9|\\u500b)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
        );
        Matcher normalizedLeftMatcher = normalizedLeftPattern.matcher(rawText);
        if (normalizedLeftMatcher.find()) {
            String count = normalizedLeftMatcher.group(1) != null
                    ? normalizedLeftMatcher.group(1)
                    : normalizedLeftMatcher.group(2);
            return "Only " + count + " left in stock";
        }

        if (lower.contains("cannot be shipped to your selected delivery location")
                || lower.contains("cannot ship to your selected delivery location")
                || lower.contains("this item cannot be shipped")
                || lower.contains("\u3053\u306e\u5546\u54c1\u306f\u9078\u629e\u3057\u305f\u304a\u5c4a\u3051\u5148\u306b\u306f\u767a\u9001\u3067\u304d\u307e\u305b\u3093")) {
            return "Unavailable for selected address";
        }

        if (lower.contains("currently unavailable")
                || lower.contains("temporarily out of stock")
                || lower.contains("out of stock")
                || lower.contains("\u73fe\u5728\u5728\u5eab\u5207\u308c")
                || lower.contains("\u5728\u5eab\u5207\u308c")
                || lower.contains("\u518d\u5165\u8377\u4e88\u5b9a")) {
            return "Out of Stock";
        }

        if (lower.contains("in stock")
                || lower.contains("available to ship")
                || lower.contains("usually ships")
                || lower.contains("\u5728\u5eab\u3042\u308a")) {
            return "In Stock";
        }

        // Case 1: Hết hàng
        if (lower.contains("currently unavailable")
                || lower.contains("out of stock")
                || lower.contains("在庫切れ")) {
            return "Out of Stock";
        }

        // Case 2: Chỉ còn X sản phẩm — Regex bắt số
        Pattern leftPattern = Pattern.compile(
                "(?:only|残り)\\s*(\\d+)\\s*(?:left|点)", Pattern.CASE_INSENSITIVE
        );
        Matcher m = leftPattern.matcher(rawText);
        if (m.find()) {
            return "Only " + m.group(1) + " left in stock";
        }

        // Case 3: Còn hàng
        if (lower.contains("in stock") || lower.contains("在庫あり")) {
            return "In Stock";
        }

        // Default
        return "Unknown";
    }

    // ======================= EXTRACT IMAGE =======================
    public static String extractImage(WebDriver driver) {
        String[] selectors = {
                "#landingImage",
                "#imgBlkFront",
                "#main-image",
                "#ebooksImgBlkFront",
                "img#landingImage"
        };

        // Scan nhanh không chờ implicit wait
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        try {
            for (String sel : selectors) {
                try {
                    List<WebElement> elements = driver.findElements(By.cssSelector(sel));
                    if (!elements.isEmpty()) {
                        WebElement el = elements.get(0);

                        String hiRes = el.getAttribute("data-old-hires");
                        if (hiRes != null && !hiRes.trim().isEmpty()) return hiRes;

                        String src = el.getAttribute("src");
                        if (src != null && !src.trim().isEmpty()) return src;
                    }
                } catch (Exception ignored) {}
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        }
        return "N/A";
    }
}
