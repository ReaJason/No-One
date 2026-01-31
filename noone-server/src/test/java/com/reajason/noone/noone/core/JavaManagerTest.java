package com.reajason.noone.noone.core;

/**
 * @author ReaJason
 * @since 2025/12/13
 */
class JavaManagerTest {

//    @Test
//    void testBasicConnection() {
//        JavaManager javaManager = new JavaManager();
//        javaManager.setUrl("http://127.0.0.1:8081/app/test");
//        boolean connected = javaManager.test();
//        assertTrue(connected, "Should connect successfully");
//    }
//
//    @Test
//    void testBasicConnectionWebsocket(){
//        WebSocketClient client = new WebSocketClient("ws://127.0.0.1:8081/app/no-one-ws");
//        JavaManager javaManager = new JavaManager(client);
//        boolean connected = javaManager.test();
//        assertTrue(connected, "Should connect successfully");
//    }
//
//    @Test
//    void testClassCacheWithPlugin() {
//        JavaManager javaManager = new JavaManager();
//        javaManager.setUrl("http://127.0.0.1:8081/app/test");
//        javaManager.test();
//
//        // 第一次调用 - 应该加载类
//        Map<String, Object> basicInfo1 = javaManager.getBasicInfo();
//        assertNotNull(basicInfo1, "First call should return basic info");
//
//        // 第二次调用 - 应该使用缓存的 plugin
//        Map<String, Object> basicInfo2 = javaManager.getBasicInfo();
//        assertNotNull(basicInfo2, "Second call should return basic info from cache");
//
//        // 验证两次调用返回的数据一致性
//        assertEquals(basicInfo1.get("osName"), basicInfo2.get("osName"),
//                "OS name should be consistent between calls");
//    }
//
//    @Test
//    void testMultiplePlugins() {
//        JavaManager javaManager = new JavaManager();
//        javaManager.setUrl("http://127.0.0.1:8081/app/test");
//        javaManager.test();
//
//        // 调用多次确保 plugin 缓存正常工作
//        for (int i = 0; i < 5; i++) {
//            Map<String, Object> basicInfo = javaManager.getBasicInfo();
//            assertNotNull(basicInfo, "Call " + i + " should succeed");
//            assertNotNull(basicInfo.get("osName"), "OS name should not be null");
//            assertNotNull(basicInfo.get("javaVersion"), "Java version should not be null");
//        }
//    }

}