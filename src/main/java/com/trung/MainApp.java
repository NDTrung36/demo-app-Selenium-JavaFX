package com.trung;

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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

//        JCheckBox cb = new JCheckBox("Tick");

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

        TableColumn<Product, String> colStock = new TableColumn<>("Stock");
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));

        table.getColumns().addAll(colAsin, colTitle, colPrice, colImage, colStock);
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
                try {
                    CsvExporter.export(productList, file);
                    lblStatus.setText("Đã xuất CSV thành công!");
                } catch (Exception e) {
                    e.printStackTrace();
                    lblStatus.setText("Lỗi khi lưu file: " + e.getMessage());
                }
            }
        });

        btnCrawl.setOnAction(event -> {
            String url = txtUrl.getText();
            if (url.isEmpty()) return;

            btnCrawl.setDisable(true);
            btnExport.setDisable(true);
            productList.clear();
            lblStatus.setText("Đang khởi động Chrome...");

            Thread scrapeThread = new Thread(() -> {
                DriverManager driverMgr = null;
                try {
                    ChromeOptions options = new ChromeOptions();
                    driverMgr = new DriverManager(options,
                            msg -> Platform.runLater(() -> lblStatus.setText(msg)));
                    driverMgr.createDriver();
                    boolean opened = driverMgr.executeWithRetry(url, d -> d.get(url));
                    if (!opened) {
                        throw new RuntimeException("Khong the mo URL sau khi restart Chrome");
                    }

                    // ========== GIAI ĐOẠN 1: Gom ASIN từ tất cả các trang ==========
                    Set<String> asinSet = new LinkedHashSet<>();
                    int pageNum = 1;
                    String currentPageUrl = url;

                    while (true) {
                        final int currentPage = pageNum;
                        final String scanPageUrl = currentPageUrl;
                        final boolean[] hasNextPage = {false};
                        final String[] nextPageUrl = {scanPageUrl};
                        boolean success = driverMgr.executeWithRetry(scanPageUrl, d -> {
                            Platform.runLater(() -> lblStatus.setText("Đang quét trang " + currentPage + "..."));

                            List<WebElement> itemElements = d.findElements(
                                    By.xpath("//div[@data-asin and string-length(@data-asin)>0]")
                            );
                            for (WebElement el : itemElements) {
                                String asin = el.getAttribute("data-asin");
                                if (asin != null && !asin.isBlank()) {
                                    asinSet.add(asin);
                                }
                            }

                            // Tìm nút Next
                            List<WebElement> nextBtns = d.findElements(
                                    By.cssSelector("a.s-pagination-next")
                            );

                            // Dừng nếu: không có nút Next, hoặc Next bị disabled
                            if (nextBtns.isEmpty()
                                    || nextBtns.get(0).getAttribute("class").contains("s-pagination-disabled")) {
                                hasNextPage[0] = false;
                                return;
                            }

                            WebElement nextBtn = nextBtns.get(0);
                            String href = nextBtn.getAttribute("href");
                            hasNextPage[0] = true;
                            nextPageUrl[0] = (href != null && !href.isBlank()) ? href : scanPageUrl;

                            nextBtn.click();
                            Thread.sleep(2000); // Chờ trang load
//                            if (href == null || href.isBlank()) {
//                                nextPageUrl[0] = d.getCurrentUrl();
//                            }
                        });

                        if (!success) {
                            Platform.runLater(() -> lblStatus.setText(
                                    "Dừng quét ASIN: không thể khôi phục Chrome sau khi bị đóng"
                            ));
                            break;
                        }

                        if (!hasNextPage[0]) {
                            break;
                        }

                        currentPageUrl = nextPageUrl[0];
                        pageNum++;
                    }

                    final int totalPages = pageNum;
                    final int totalAsins = asinSet.size();
                    Platform.runLater(() -> lblStatus.setText(
                            "Tìm thấy " + totalAsins + " ASIN từ " + totalPages + " trang. Đang lấy chi tiết..."
                    ));

                    // ========== GIAI ĐOẠN 2: Vào từng /dp/{asin} lấy chi tiết ==========
                    List<String> asinList = new ArrayList<>(asinSet);
                    final DriverManager mgr = driverMgr; // effectively final for lambda

                    for (int i = 0; i < Math.min(asinList.size(), 10); i++) {
                        String asin = asinList.get(i);
                        int idx = i + 1;
                        Platform.runLater(() -> lblStatus.setText(
                                "Đang lấy " + idx + "/" + asinList.size() + ": " + asin
                        ));

                        String productUrl = "https://www.amazon.co.jp/dp/" + asin;

                        boolean success = mgr.executeWithRetry(productUrl, d -> {
                            d.get(productUrl);

                            if (d.getTitle().toLowerCase().contains("captcha")) {
                                throw new RuntimeException("Bị chặn Captcha ở ASIN: " + asin);
                            }

                            String title = AmazonParserService.extractTitle(d);
                            String price = AmazonParserService.extractPrice(d);
                            String imageUrl = AmazonParserService.extractImage(d);
                            String stock = AmazonParserService.extractStock(d);

                            Product p = new Product(asin, title, price, imageUrl, stock);
                            Platform.runLater(() -> {
                                productList.add(p);
                                lblStatus.setText("Đã lấy: " + asin);
                            });
                        });

                        if (!success) {
                            Platform.runLater(() -> lblStatus.setText(
                                    "Dừng crawl: không thể khôi phục Chrome sau khi bị đóng"
                            ));
                            break;
                        }

                        Thread.sleep(2000);
                    }

                    Platform.runLater(() -> lblStatus.setText("Hoàn tất! Đã lấy " + productList.size() + " sản phẩm."));

                } catch (Exception e) {
                    System.out.println("Lỗi crawl: " + e.getMessage());
                    e.printStackTrace();
                    Platform.runLater(() -> lblStatus.setText("Lỗi: " + e.getMessage()));
                } finally {
                    if (driverMgr != null) driverMgr.quitSafely();
                    Platform.runLater(() -> {
                        btnCrawl.setDisable(false);
                        btnExport.setDisable(false);
                    });
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


    public static void main(String[] args) {
        launch(args);
    }
}
