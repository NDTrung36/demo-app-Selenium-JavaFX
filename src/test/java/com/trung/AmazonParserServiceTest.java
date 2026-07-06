package com.trung;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho hàm parsePrice() - Kiểm tra logic Regex trích xuất giá.
 * Không cần Selenium/WebDriver, chỉ test logic xử lý chuỗi thuần.
 */
class AmazonParserServiceTest {

    // =================== TEST CASE: Giá có ký hiệu tiền tệ đứng trước ===================
    @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
    @DisplayName("Ký hiệu tiền tệ đứng TRƯỚC số")
    @CsvSource({
            "'¥10,800',         '¥10,800'",
            "'￥12,500',        '￥12,500'",
            "'$29.99',          '$29.99'",
            "'€1,234.56',       '€1,234.56'",
            "'£999',            '£999'",
            "'¥ 150,000',       '¥ 150,000'",
    })
    void testCurrencyBeforeNumber(String input, String expected) {
        assertEquals(expected, AmazonParserService.parsePrice(input));
    }

    // =================== TEST CASE: Giá có ký hiệu tiền tệ đứng sau ===================
    @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
    @DisplayName("Ký hiệu tiền tệ đứng SAU số")
    @CsvSource({
            "'150,000 VND',     '150,000 VND'",
            "'29.99 USD',       '29.99 USD'",
            "'10800 JPY',       '10800 JPY'",
    })
    void testCurrencyAfterNumber(String input, String expected) {
        assertEquals(expected, AmazonParserService.parsePrice(input));
    }

    // =================== TEST CASE QUAN TRỌNG: Chuỗi có số đếm + giá ===================
    @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
    @DisplayName("BUG FIX: Không bắt nhầm số đếm (1, 2, 3...) ở đầu câu")
    @CsvSource({
            "'1 option from ¥150,000',                  '¥150,000'",
            "'2 used & new offers from ￥12,500',        '￥12,500'",
            "'3 new from ¥8,900',                        '¥8,900'",
            "'5 offers starting from $19.99',            '$19.99'",
            "'See 10 options from ¥1,200',               '¥1,200'",
    })
    void testSkipCountingNumbers(String input, String expected) {
        assertEquals(expected, AmazonParserService.parsePrice(input));
    }

    // =================== TEST CASE: Fallback khi KHÔNG có ký hiệu tiền tệ ===================
    @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
    @DisplayName("Fallback: lấy số LỚN NHẤT khi không có ký hiệu tiền tệ")
    @CsvSource({
            "'1 option 150000',     '150000'",
            "'3 offers 12500',      '12500'",
            "'10800',               '10800'",
            "'150,000',             '150,000'",
    })
    void testFallbackLargestNumber(String input, String expected) {
        assertEquals(expected, AmazonParserService.parsePrice(input));
    }

    // =================== TEST CASE: Edge cases ===================
    @Test
    @DisplayName("Chuỗi rỗng → N/A (xử lý ở extractPrice, parsePrice nhận chuỗi không rỗng)")
    void testEmptyAndNull() {
        // parsePrice không bao giờ nhận null/empty (đã check ở extractPrice)
        // nhưng test để đảm bảo robust
        assertEquals("", AmazonParserService.parsePrice(""));
        assertEquals("abc", AmazonParserService.parsePrice("abc")); // trả về rawPrice gốc
    }

    @Test
    @DisplayName("Giá Amazon JP thường thấy")
    void testCommonAmazonJPPrices() {
        assertEquals("¥2,480", AmazonParserService.parsePrice("¥2,480"));
        assertEquals("￥39,800", AmazonParserService.parsePrice("￥39,800"));
        assertEquals("¥980", AmazonParserService.parsePrice("¥980"));
        assertEquals("¥1,298,000", AmazonParserService.parsePrice("¥1,298,000"));
    }
}
