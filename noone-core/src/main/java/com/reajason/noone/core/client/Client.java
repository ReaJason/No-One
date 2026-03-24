package com.reajason.noone.core.client;

/**
 * 通信客户端抽象接口
 * 支持多种协议实现（HTTP、WebSocket、TCP 等）
 *
 * @author ReaJason
 * @since 2025/12/13
 */
public interface Client {

    /**
     * 连接到服务器
     *
     * @return 是否连接成功
     */
    boolean connect();

    /**
     * 断开与服务器的连接
     */
    void disconnect();

    /**
     * 检查是否已连接
     *
     * @return 是否已连接
     */
    boolean isConnected();

    /**
     * 发送请求并获取响应
     *
     * @param payload 请求负载（Base64 编码的序列化数据）
     * @return 响应负载（Base64 编码的序列化数据），失败时返回 null
     */
    byte[] send(String payload);

    byte[] send(byte[] payload);

    /**
     * 获取服务器地址
     *
     * @return 服务器地址
     */
    String getUrl();

    /**
     * 设置服务器地址
     *
     * @param url 服务器地址
     */
    void setUrl(String url);
}
