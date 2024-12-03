# 使用jcef实现RPA的学习样例

# 实现功能
- 在百度里搜索【AI编程】提取搜索结果的标题和链接

程序的目标是使用 JCEF（Java Chromium Embedded Framework）来加载百度首页，执行搜索，并提取搜索结果。

主要步骤包括：

初始化 JCEF 并创建浏览器窗口。
加载百度首页并在页面加载完成后执行搜索。
在搜索结果页面加载完成后，执行 JavaScript 脚本提取搜索结果。
通过 JavaScript 与 Java 之间的通信，将提取的结果传递回 Java 代码。
解析结果并返回给调用者。

## 总体概述

这个程序的目标是使用 **JCEF**（Java Chromium Embedded Framework）来加载百度首页，执行搜索，并提取搜索结果。主要步骤包括：

1. 初始化 JCEF 并创建浏览器窗口。
2. 加载百度首页并在页面加载完成后执行搜索。
3. 在搜索结果页面加载完成后，执行 JavaScript 脚本提取搜索结果。
4. 通过 JavaScript 与 Java 之间的通信，将提取的结果传递回 Java 代码。
5. 解析结果并返回给调用者。

下面，我将详细介绍代码中使用到的关键类和概念。

---

## 关键类和概念

### 1. **JCEF（Java Chromium Embedded Framework）**

**JCEF** 是 Chromium Embedded Framework（CEF）的 Java 语言绑定。CEF 是一个开源项目，允许开发者在应用程序中嵌入 Chromium 浏览器。

**关键类：**

- **`CefApp`**：JCEF 应用程序的主类，负责初始化和关闭浏览器环境。
- **`CefClient`**：代表浏览器的客户端，可以添加各种处理器（handler）来处理浏览器事件。
- **`CefBrowser`**：代表一个浏览器实例，用于加载网页、执行 JavaScript 等操作。

### 2. **浏览器事件处理器**

为了处理浏览器中的各种事件，如页面加载、控制台消息等，我们需要使用处理器（handler）。JCEF 提供了适配器类，方便我们只重写需要的方法。

**关键处理器：**

- **`CefLoadHandlerAdapter`**：用于处理页面加载事件的适配器类。
  - **`onLoadingStateChange`**：当加载状态改变时调用，参数 `isLoading` 表示是否正在加载。
  - **`onLoadEnd`**：当页面加载完成时调用。
  - **`onLoadError`**：当页面加载出错时调用。

- **`CefDisplayHandlerAdapter`**：用于处理显示相关事件的适配器类。
  - **`onConsoleMessage`**：当 JavaScript 中调用 `console.log` 或 `console.error` 时触发，用于接收控制台消息。

### 3. **JavaScript 与 Java 的通信**

由于 JavaScript 在浏览器中执行，而我们的 Java 代码需要获取 JavaScript 中的数据，因此需要一种通信方式。

在最新的代码中，我们使用了 **控制台消息** 的方式：

- **JavaScript 端**：使用 `console.log` 输出特定格式的消息，例如：`console.log('EXTRACTED_RESULTS:' + JSON.stringify(results));`。
- **Java 端**：在 `onConsoleMessage` 方法中监听控制台消息，当检测到消息以 `EXTRACTED_RESULTS:` 开头时，截取后面的 JSON 字符串并解析。

### 4. **`CompletableFuture`**

`CompletableFuture` 是 Java 8 引入的类，用于异步编程。

- **`CompletableFuture<List<SearchResult>> resultFuture`**：用于在异步操作完成后获取结果。
- **`resultFuture.complete(results)`**：当结果准备好时，完成 `CompletableFuture`，使等待的线程能够获取结果。
- **`resultFuture.get(30, TimeUnit.SECONDS)`**：在主线程中等待结果，最多等待 30 秒。

### 5. **Swing UI 线程和 `SwingUtilities.invokeLater`**

由于 JCEF 和 Swing 都涉及到 UI 操作，为了避免线程安全问题，我们需要在事件调度线程（Event Dispatch Thread，EDT）中执行 UI 操作。

- **`SwingUtilities.invokeLater(Runnable r)`**：将任务 `r` 提交到 EDT 中执行，确保线程安全。

### 6. **JavaScript 脚本的执行**

- **`browser.executeJavaScript(String script, String url, int line)`**：在当前页面中执行 JavaScript 脚本。
  - **`script`**：要执行的 JavaScript 代码。
  - **`url`**：脚本所在的 URL，可以使用当前页面的 URL。
  - **`line`**：脚本的起始行号，一般传 `0`。

### 7. **页面元素选择器和 DOM 操作**

在 JavaScript 脚本中，我们需要访问页面的 DOM 元素，进行搜索和结果提取。

- **`document.querySelector`**：返回匹配指定 CSS 选择器的第一个元素。
- **`document.querySelectorAll`**：返回匹配指定 CSS 选择器的所有元素的列表。
- **`Element.innerText`**：获取元素的文本内容。
- **`Element.href`**：对于 `a` 标签，获取其 `href` 属性。

### 8. **异常处理和日志输出**

为了提高程序的健壮性，我们在关键部分添加了异常处理和日志输出。

- **`try-catch`**：捕获异常，防止程序崩溃，并输出错误信息。
- **`System.out.println`**：输出调试信息，帮助跟踪程序的执行流程。
- **`System.err.println`**：输出错误信息。

---

## 代码详解

下面，我们按照代码的顺序，详细讲解每个部分。

### **1. 类声明和成员变量**

```java
public class JcefHandler {
    private CefBrowser browser;
    private CompletableFuture<List<SearchResult>> resultFuture;
    private static final Gson gson = new Gson();
}
```

- **`CefBrowser browser`**：浏览器实例，用于加载网页和执行 JavaScript。
- **`CompletableFuture<List<SearchResult>> resultFuture`**：用于异步获取搜索结果。
- **`Gson gson`**：用于 JSON 序列化和反序列化。

### **2. 主方法 `performBaiduSearch`**

```java
public List<SearchResult> performBaiduSearch(String keyword) throws Exception {
    // 初始化 CompletableFuture
    resultFuture = new CompletableFuture<>();

    // 初始化 JCEF
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
    client.addLoadHandler(new CefLoadHandlerAdapter() { ... });

    // 添加显示处理器，监听控制台消息
    client.addDisplayHandler(new CefDisplayHandlerAdapter() { ... });

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
```

- **初始化 JCEF**：调用 `CefApp.startup` 和 `CefApp.getInstance` 初始化浏览器环境。
- **创建浏览器窗口**：使用 Swing 创建一个 JFrame，并将浏览器的 UI 组件添加到窗口中。
- **添加加载监听器**：使用 `CefLoadHandlerAdapter` 监听页面加载事件。
- **添加显示处理器**：使用 `CefDisplayHandlerAdapter` 监听控制台消息。
- **等待结果**：使用 `resultFuture.get` 方法，等待搜索结果，设置超时时间为 30 秒。
- **异常处理**：如果发生异常，抛出异常信息。

### **3. 页面加载监听器**

```java
client.addLoadHandler(new CefLoadHandlerAdapter() {
    private boolean isSearchPerformed = false;

    @Override
    public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        if (!isLoading && !isSearchPerformed) {
            // 首页加载完成，执行搜索
            System.out.println("首页加载完成，开始搜索");
            performSearch(keyword);
            isSearchPerformed = true;
        }
    }

    @Override
    public void onLoadEnd(CefBrowser cefBrowser, CefFrame cefFrame, int httpStatusCode) {
        // 当搜索结果页加载完成，提取搜索结果
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
```

- **`onLoadingStateChange`**：当页面加载状态改变时调用。
  - **`isLoading` 为 `false`** 且 **`isSearchPerformed` 为 `false`** 时，表示首页加载完成，执行搜索操作。
  - 设置 **`isSearchPerformed = true`**，防止重复执行搜索。
- **`onLoadEnd`**：当页面加载完成时调用。
  - 检查当前 URL 是否包含 `"baidu.com/s"`，如果是，则表示搜索结果页加载完成，调用 `extractSearchResults` 提取结果。
- **`onLoadError`**：当页面加载出错时调用。
  - 完成 `resultFuture`，并抛出异常。

### **4. 显示处理器，监听控制台消息**

```java
client.addDisplayHandler(new CefDisplayHandlerAdapter() {
    @Override
    public boolean onConsoleMessage(CefBrowser browser, String message, String source, int line) {
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
```

- **`onConsoleMessage`**：当 JavaScript 中有控制台输出时调用。
  - 检查消息是否以 `"EXTRACTED_RESULTS:"` 开头。
  - 如果是，截取后面的 JSON 字符串，解析为 `List<SearchResult>`。
  - 完成 `resultFuture`，使主线程能够获取结果。
  - 返回 `true`，表示消息已被处理。

### **5. 执行搜索**

```java
private void performSearch(String keyword) {
    // 执行搜索的 JavaScript
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
```

- **构建 JavaScript 脚本**：
  - 获取搜索输入框（`id='kw'`）和搜索按钮（`id='su'`）。
  - 如果找到，设置输入框的值为关键词，点击搜索按钮。
  - 如果未找到，输出错误信息到控制台。
- **执行 JavaScript 脚本**：
  - 使用 `SwingUtilities.invokeLater` 确保在 EDT 中执行。
  - 调用 `browser.executeJavaScript` 执行脚本。

### **6. 提取搜索结果**

```java
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
                    "}, 1000);"; // 延迟1秒，确保内容加载完成

    SwingUtilities.invokeLater(() -> {
        browser.executeJavaScript(extractScript, browser.getURL(), 0);
    });
}
```

- **构建 JavaScript 脚本**：
  - 使用 `setTimeout` 延迟执行，等待页面内容完全加载。
  - 定义一个空数组 `results`，用于存储搜索结果。
  - 使用 `document.querySelectorAll('div.result, div.result-op')` 获取所有搜索结果项。
    - **`div.result`**：普通搜索结果的容器。
    - **`div.result-op`**：特殊结果（如百科、资讯）的容器。
  - 遍历搜索结果项，提取标题和链接。
    - 使用 `result.querySelector('h3 a')` 获取标题链接。
    - 将标题文本和链接地址添加到 `results` 数组中。
  - 使用 `console.log` 输出结果，前缀为 `"EXTRACTED_RESULTS:"`，便于 Java 端识别。
- **执行 JavaScript 脚本**：
  - 使用 `SwingUtilities.invokeLater` 确保在 EDT 中执行。
  - 调用 `browser.executeJavaScript` 执行脚本。

### **7. 解析搜索结果**

```java
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
```

- **参数**：`jsonResult`，从 JavaScript 端传递过来的 JSON 字符串。
- **解析过程**：
  - 检查 `jsonResult` 是否为 `null` 或 `"null"`，如果是，返回空列表。
  - 使用 Gson 库，将 JSON 字符串解析为 `List<SearchResult>` 对象。
  - 如果解析过程中发生异常，捕获异常，输出错误信息，返回空列表。

### **8. 搜索结果内部类**

```java
public static class SearchResult {
    public String title;
    public String link;

    @Override
    public String toString() {
        return "标题: " + title + "\n链接: " + link;
    }
}
```

- **`SearchResult`**：用于存储单个搜索结果的类。
  - **`title`**：搜索结果的标题。
  - **`link`**：搜索结果的链接。
- **`toString` 方法**：方便打印输出搜索结果。

---

## 总结

通过上述代码，我们实现了在 Java 应用程序中使用 JCEF 嵌入浏览器，自动化执行百度搜索，并提取搜索结果。关键点包括：

- **使用 JCEF 创建和控制浏览器**。
- **使用处理器监听浏览器的事件，如页面加载和控制台消息**。
- **通过执行 JavaScript 脚本，操纵网页 DOM，执行搜索和提取结果**。
- **使用控制台消息在 JavaScript 和 Java 之间传递数据**。
- **使用 `CompletableFuture` 进行异步编程，等待搜索结果**。

希望以上详解能够帮助您理解代码的工作原理和关键概念。如有疑问，请随时提问。
