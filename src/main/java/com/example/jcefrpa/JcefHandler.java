package com.example.jcefrpa;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class JcefHandler {
    private CefBrowser browser;
    private CompletableFuture<List<SearchResult>> resultFuture;
    private static final Gson gson = new Gson();

    public List<SearchResult> performBaiduSearch(String keyword) throws Exception {
        resultFuture = new CompletableFuture<>();

        // 初始化JCEF
        CefApp.startup(new String[]{});
        CefSettings settings = new CefSettings();
        CefApp cefApp = CefApp.getInstance(settings);
        CefClient client = cefApp.createClient();

        // 创建浏览器窗口
        JFrame frame = new JFrame("Baidu Search");
        frame.setSize(1024, 768);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        browser = client.createBrowser("https://www.baidu.com", false, false);
        frame.add(browser.getUIComponent(), BorderLayout.CENTER);
        frame.setVisible(true);

        // 添加加载监听器
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            private boolean isSearchPerformed = false;

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                if (!isLoading && !isSearchPerformed) {
                    // 确保在首页完全加载后执行搜索
                    System.out.println("页面加载完成，开始搜索");
                    performSearch(keyword);
                    isSearchPerformed = true;
                }
            }

            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame cefFrame, int httpStatusCode) {
                // 搜索结果页加载完成后提取数据
                System.out.println("加载完成");
                if (cefBrowser.getURL().contains("baidu.com/s")) {
                    System.out.println("提取搜索结果");
                    extractSearchResults();
                }
            }

            @Override
            public void onLoadError(CefBrowser cefBrowser, CefFrame cefFrame, ErrorCode errorCode, String failedUrl, String errorText) {
                resultFuture.completeExceptionally(new RuntimeException("页面加载错误: " + errorText));
            }
        });

        // 添加显示处理器，监听控制台消息
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
                if (message.startsWith("EXTRACTED_RESULTS:")) {
                    String jsonData = message.substring("EXTRACTED_RESULTS:".length());
                    try {
                        List<SearchResult> results = parseSearchResults(jsonData);
                        resultFuture.complete(results);
                    } catch (Exception ex) {
                        resultFuture.completeExceptionally(ex);
                    }
                    return true;
                }
                return false;
            }
        });

        // 等待结果，设置超时
        try {
            List<SearchResult> results = resultFuture.get(30, TimeUnit.SECONDS);

            // 关闭浏览器
            browser.close(true);
            cefApp.dispose();

            return results;
        } catch (Exception e) {
            throw new Exception("搜索超时或发生错误: " + e.getMessage());
        }
    }

    private void performSearch(String keyword) {
        // 执行搜索的JavaScript
        String searchScript =
                "var input = document.getElementById('kw');" +
                        "var searchBtn = document.getElementById('su');" +
                        "if (input && searchBtn) {" +
                        "   input.value = '" + keyword + "';" +
                        "   searchBtn.click();" +
                        "} else {" +
                        "   console.error('无法找到搜索输入框或按钮');" +
                        "}";

        SwingUtilities.invokeLater(() -> {
            browser.executeJavaScript(searchScript, browser.getURL(), 0);
        });
    }

    private void extractSearchResults() {
        String extractScript =
                "setTimeout(function() {" +
                        "   var results = [];" +
                        "   var searchResults = document.querySelectorAll('div.result, div.result-op');" +
                        "   searchResults.forEach(function(result) {" +
                        "       var titleElement = result.querySelector('h3 a');" +
                        "       if (titleElement) {" +
                        "           results.push({" +
                        "               title: titleElement.innerText.trim()," +
                        "               link: titleElement.href" +
                        "           });" +
                        "       }" +
                        "   });" +
                        "   console.log('EXTRACTED_RESULTS:' + JSON.stringify(results));" +
                        "}, 2000);"; // 延迟1秒，确保内容加载完成

        SwingUtilities.invokeLater(() -> {
            browser.executeJavaScript(extractScript, browser.getURL(), 0);
        });
    }

    private List<SearchResult> parseSearchResults(String jsonResult) {
        try {
            System.out.println("解析搜索结果: " + jsonResult);
            if (jsonResult == null || jsonResult.equals("null")) {
                return List.of();
            }

            Type listType = new TypeToken<List<SearchResult>>(){}.getType();
            return gson.fromJson(jsonResult, listType);
        } catch (Exception e) {
            System.err.println("解析搜索结果失败: " + e.getMessage());
            return List.of();
        }
    }

    // 搜索结果内部类
    public static class SearchResult {
        public String title;
        public String link;

        @Override
        public String toString() {
            return "标题: " + title + "\n链接: " + link;
        }
    }
}
