package com.trung;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho CsvExporter — Kiểm tra xuất CSV có đủ cột Stock,
 * UTF-8 BOM, và escapeCsv hoạt động đúng.
 */
class CsvExporterTest {

    @Test
    @DisplayName("Export CSV có đủ 5 cột bao gồm Stock + UTF-8 BOM")
    void testExportContainsStockColumn(@TempDir Path tempDir) throws Exception {
        List<Product> products = List.of(
                new Product("B001", "Test Product", "¥1,000", "http://img.jpg", "In Stock"),
                new Product("B002", "Another \"Product\"", "¥2,500", "http://img2.jpg", "Only 3 left in stock")
        );

        File csvFile = tempDir.resolve("test.csv").toFile();
        CsvExporter.export(products, csvFile);

        List<String> lines = Files.readAllLines(csvFile.toPath());

        // Header phải có 5 cột bao gồm Stock
        assertTrue(lines.get(0).contains("Stock"), "Header phải chứa cột Stock");
        assertEquals("ASIN,Title,Price,Image URL,Stock", lines.get(0).replace("\ufeff", ""));

        // BOM check (byte level)
        byte[] bytes = Files.readAllBytes(csvFile.toPath());
        assertEquals((byte) 0xEF, bytes[0], "UTF-8 BOM byte 1");
        assertEquals((byte) 0xBB, bytes[1], "UTF-8 BOM byte 2");
        assertEquals((byte) 0xBF, bytes[2], "UTF-8 BOM byte 3");

        // Tổng 3 dòng: header + 2 rows
        assertEquals(3, lines.size());

        // Stock value phải xuất hiện trong CSV
        assertTrue(lines.get(1).contains("In Stock"));
        assertTrue(lines.get(2).contains("Only 3 left in stock"));
    }

    @Test
    @DisplayName("Export CSV: title có dấu phẩy được escape đúng")
    void testExportWithCommaInTitle(@TempDir Path tempDir) throws Exception {
        List<Product> products = List.of(
                new Product("B003", "iPhone 15, 128GB", "¥100,000", "http://img.jpg", "In Stock")
        );

        File csvFile = tempDir.resolve("test_comma.csv").toFile();
        CsvExporter.export(products, csvFile);

        List<String> lines = Files.readAllLines(csvFile.toPath());
        // Title chứa dấu phẩy phải được bọc ngoặc kép
        assertTrue(lines.get(1).contains("\"iPhone 15, 128GB\""));
    }

    @Test
    @DisplayName("escapeCsv xử lý đúng các trường hợp đặc biệt")
    void testEscapeCsv() {
        assertEquals("simple", CsvExporter.escapeCsv("simple"));
        assertEquals("\"has,comma\"", CsvExporter.escapeCsv("has,comma"));
        assertEquals("\"has\"\"quote\"", CsvExporter.escapeCsv("has\"quote"));
        assertEquals("", CsvExporter.escapeCsv(null));
    }
}
