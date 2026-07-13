package com.trung;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Quản lý lifecycle của ChromeDriver — tạo, restart, retry, và quit.
 * Khi user đóng Chrome giữa lúc crawl, class này tự khởi động lại
 * và retry bước hiện tại 1 lần, tránh loop vô hạn.
 */
public class DriverManager {

    private WebDriver driver;
    private final ChromeOptions options;
    private final Consumer<String> statusCallback;

    public DriverManager(ChromeOptions options, Consumer<String> statusCallback) {
        this.options = options;
        this.statusCallback = statusCallback;
    }

    /** Tạo driver mới */
    public WebDriver createDriver() {
        WebDriverManager.chromedriver().setup();
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        return driver;
    }

    /** Quit driver cũ (nếu có) → tạo mới */
    public WebDriver restartDriver() {
        quitSafely();
        statusCallback.accept("Chrome đã đóng — đang khởi động lại...");
        return createDriver();
    }

    /** Quit an toàn, không throw */
    public void quitSafely() {
        if (driver != null) {
            try {
                driver.quit();
            }
            catch (Exception ignored) {
            }
            driver = null;
        }
    }

    public WebDriver getDriver() {
        return driver;
    }

    /**
     * Thực thi action với retry 1 lần khi Chrome bị đóng.
     *
     * @param url    URL đang xử lý (để mở lại sau restart)
     * @param action logic cần thực thi với driver
     * @return true nếu thành công, false nếu fail sau retry
     */
    public boolean executeWithRetry(String url, DriverAction action) {
        try {
            action.execute(driver);
            return true;
        }
        catch (NoSuchWindowException | NoSuchSessionException e) {
            return retryOnce(url, action, e);
        }
        catch (WebDriverException e) {
            if (isSessionError(e)) {
                return retryOnce(url, action, e);
            }
            throw e; // Lỗi khác (timeout, element not found...) → ném lại
        }
        catch (Exception e) {
            // Checked exception từ action (ví dụ RuntimeException từ Captcha check)
            throw new RuntimeException(e);
        }
    }

    /**
     * Restart Chrome và retry action đúng 1 lần.
     * Nếu vẫn lỗi → trả false, không retry nữa.
     */
    private boolean retryOnce(String url, DriverAction action, Exception originalError) {
        statusCallback.accept("Chrome bị mất session — đang restart...");
        try {
            restartDriver();
            driver.get(url);
            action.execute(driver);
            statusCallback.accept("Retry thành công!");
            return true;
        } catch (Exception retryEx) {
            statusCallback.accept("Retry thất bại: " + retryEx.getMessage());
            return false;
        }
    }

    private boolean isSessionError(WebDriverException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("no such window")
                || msg.contains("no such session")
                || msg.contains("session deleted")
                || msg.contains("target window already closed")
                || msg.contains("unable to evaluate script");
    }

    @FunctionalInterface
    public interface DriverAction {
        void execute(WebDriver driver) throws Exception;
    }
}
