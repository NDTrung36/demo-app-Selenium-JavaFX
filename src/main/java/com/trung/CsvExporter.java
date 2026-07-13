package com.trung;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Xuất danh sách Product ra file CSV với UTF-8 BOM.
 * Tách riêng khỏi MainApp để dễ unit test.
 */
public class CsvExporter {

    private static final String HEADER = "ASIN,Title,Price,Image URL,Stock";

    /**
     * Ghi danh sách Product ra file CSV với UTF-8 BOM.
     */
    public static void export(List<Product> products, File file) throws Exception {
        try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF'); // UTF-8 BOM cho Excel mở đúng encoding
            writer.println(HEADER);
            for (Product p : products) {
                writer.println(
                        escapeCsv(p.getAsin()) + "," +
                        escapeCsv(p.getTitle()) + "," +
                        escapeCsv(p.getPrice()) + "," +
                        escapeCsv(p.getImageUrl()) + "," +
                        escapeCsv(p.getStock())
                );
            }
        }
    }

    /**
     * Hàm phụ trợ giúp làm sạch chuỗi trước khi ném vào CSV.
     * Nếu chuỗi có chứa dấu phẩy, nháy kép hoặc xuống dòng, nó sẽ bọc ngoặc kép lại.
     * Package-private để unit test được.
     */
    static String escapeCsv(String data) {
        if (data == null) return "";
        // Nếu trong chuỗi có sẵn dấu ngoặc kép, phải nhân đôi nó lên theo quy tắc CSV
        String escaped = data.replace("\"", "\"\"");
        // Nếu có chứa ký tự đặc biệt làm vỡ cấu trúc CSV, bọc toàn bộ bằng ngoặc kép
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            escaped = "\"" + escaped + "\"";
        }
        return escaped;
    }
}
