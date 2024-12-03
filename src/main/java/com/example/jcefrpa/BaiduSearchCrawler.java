package com.example.jcefrpa;

import java.util.List;

public class BaiduSearchCrawler {
    public static void main(String[] args) {
        try {
            JcefHandler handler = new JcefHandler();
            List<JcefHandler.SearchResult> results = handler.performBaiduSearch("AI编程");

            System.out.println("搜索结果：");
            for (int i = 0; i < results.size(); i++) {
                System.out.println("结果 " + (i + 1) + ":");
                System.out.println(results.get(i));
                System.out.println("---");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
