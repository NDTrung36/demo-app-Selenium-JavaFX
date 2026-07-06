package com.trung;

import com.trung.AmazonParserService;
import io.github.bonigarcia.wdm.WebDriverManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {

    private ObservableList<Product> productList = FXCollections.observableArrayList();

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        HBox topBox = new HBox(10);
        TextField txtUrl = new TextField("https://www.amazon.co.jp/s?k=iphone...");
        txtUrl.setPrefWidth(500);

        Button btnCrawl = new Button("Lấy Dữ Liệu");
        Button btnExport = new Button("Xuất CSV");

        Label lblStatus = new Label("Trạng thái: Sẵn sàng");

        topBox.getChildren().addAll(txtUrl, btnCrawl, btnExport, lblStatus);
        root.setTop(topBox);

        TableView<Product> table = new TableView<>();
        table.setItems(productList);

        TableColumn<Product, String> colAsin = new TableColumn<>("ASIN");
        colAsin.setCellValueFactory(new PropertyValueFactory<>("asin"));
        colAsin.setMinWidth(120);
        colAsin.setMaxWidth(120);

        TableColumn<Product, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));

        TableColumn<Product, String> colPrice = new TableColumn<>("Price");
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colPrice.setMinWidth(100);
        colPrice.setMaxWidth(100);

        TableColumn<Product, String> colImage = new TableColumn<>("Image URL");
        colImage.setCellValueFactory(new PropertyValueFactory<>("imageUrl"));

        table.getColumns().addAll(colAsin, colTitle, colPrice, colImage);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                var posList = table.getSelectionModel().getSelectedCells();
                if (!posList.isEmpty()) {
                    var pos = posList.get(0);
                    int row = pos.getRow();
                    Object cellData = pos.getTableColumn().getCellData(row);
                    if (cellData != null) {
                        ClipboardContent content = new ClipboardContent();
                        content.putString(cellData.toString());
                        Clipboard.getSystemClipboard().setContent(content);
                    }
                }
            }
        });

        root.setCenter(table);

        btnExport.setOnAction(event -> {
            if (productList.isEmpty()) {
                lblStatus.setText("Không có dữ liệu để xuất!");
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Lưu file CSV");
            fileChooser.setInitialFileName("amazon_products.csv");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

            File file = fileChooser.showSaveDialog(primaryStage);

            if (file != null) {
                try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                    writer.write('\ufeff');

                    // cột
                    writer.println("ASIN,Title,Price,Image URL");

                    // hàng
                    for (Product p : productList) {
                        String asin = escapeCsv(p.getAsin());
                        String title = escapeCsv(p.getTitle());
                        String price = escapeCsv(p.getPrice());
                        String imageUrl = escapeCsv(p.getImageUrl());

                        writer.println(asin + "," + title + "," + price + "," + imageUrl);
                    }
                    lblStatus.setText("Đã xuất CSV thành công!");
                }
                catch (Exception e) {
                    e.printStackTrace();
                    lblStatus.setText("Lỗi khi lưu file: " + e.getMessage());
                }
            }
        });

        btnCrawl.setOnAction(event -> {
            String url = txtUrl.getText();
            if (url.isEmpty()) return;

            btnCrawl.setDisable(true);
            productList.clear();
            lblStatus.setText("Đang khởi động Chrome...");

            Thread scrapeThread = new Thread(() -> {
                WebDriver driver = null;
                try {
                    WebDriverManager.chromedriver().setup();
                    ChromeOptions options = new ChromeOptions();
                    driver = new ChromeDriver(options);

                    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
                    driver.get(url);

                    Platform.runLater(() -> lblStatus.setText("Đang lấy list ASIN..."));

                    List<WebElement> itemElements = driver.findElements(By.xpath("//div[@data-asin and string-length(@data-asin)>0]"));

                    List<String> asinList = new ArrayList<>();
                    for (WebElement el : itemElements) {
                        asinList.add(el.getAttribute("data-asin"));
                    }

                    Platform.runLater(() -> lblStatus.setText("Tìm thấy " + asinList.size() + " ASIN. Đang lấy chi tiết..."));

                    for (int i = 0; i < Math.min(asinList.size(), 5); i++) {
                        String asin = asinList.get(i);
                        driver.get("https://www.amazon.co.jp/dp/" + asin);

                        if (driver.getTitle().toLowerCase().contains("captcha")) {
                            throw new RuntimeException("Bị chặn Captcha ở ASIN: " + asin);
                        }

                        String title = AmazonParserService.extractTitle(driver);
                        String price = AmazonParserService.extractPrice(driver);
                        String imageUrl = AmazonParserService.extractImage(driver);

                        Product p = new Product(asin, title, price, imageUrl);

                        Platform.runLater(() -> {
                            productList.add(p);
                            lblStatus.setText("Đã lấy: " + asin);
                        });

                        Thread.sleep(2000);
                    }

                    Platform.runLater(() -> lblStatus.setText("Hoàn tất!"));

                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> lblStatus.setText("Lỗi: " + e.getMessage()));
                } finally {
                    try {
                        if (driver != null) {
                            driver.quit();
                        }
                    } catch (Exception ex) {
                        System.err.println("Lỗi khi giải phóng ChromeDriver: " + ex.getMessage());
                    } finally {
                        Platform.runLater(() -> btnCrawl.setDisable(false));
                    }
                }
            });

            scrapeThread.setDaemon(true);
            scrapeThread.start();
        });

        Scene scene = new Scene(root, 1000, 500);
        primaryStage.setTitle("Amazon Scraper");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Hàm phụ trợ (Helper method) giúp làm sạch chuỗi trước khi ném vào CSV.
     * Nếu chuỗi có chứa dấu phẩy, nháy kép hoặc xuống dòng, nó sẽ bọc ngoặc kép lại.
     */
    private String escapeCsv(String data) {
        if (data == null) return "";
        // Nếu trong chuỗi có sẵn dấu ngoặc kép, phải nhân đôi nó lên theo quy tắc CSV
        String escapedData = data.replace("\"", "\"\"");

        // Nếu có chứa ký tự đặc biệt làm vỡ cấu trúc CSV, bọc toàn bộ bằng ngoặc kép
        if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("\n")) {
            escapedData = "\"" + escapedData + "\"";
        }
        return escapedData;
    }

    public static void main(String[] args) {
        launch(args);
    }
}