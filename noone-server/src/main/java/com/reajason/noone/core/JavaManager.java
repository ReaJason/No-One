package com.reajason.noone.core;

import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import com.reajason.noone.core.client.Client;
import com.reajason.noone.core.client.HttpClient;
import com.reajason.noone.core.plugin.SystemInfoCollector;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ReaJason
 * @since 2025/12/13
 */
public class JavaManager {
    private static final String ACTION = "action";
    private static final String CLASSNAME = "className";
    private static final String PLUGIN = "plugin";
    private static final String CLASS_BYTES = "classBytes";
    private static final String ARGS = "args";
    private static final String METHOD_NAME = "methodName";

    private static final String REFRESH = "refresh";
    private static final String CLASS_DEFINE = "classDefine";
    private static final String CLASS_RUN = "classRun";
    private static final String PLUGIN_CACHES = "pluginCaches";

    private static final String ACTION_STATUS = "status";
    private static final String ACTION_RUN = "run";
    private static final String ACTION_CLEAN = "clean";

    private static final String CODE = "code";
    private static final String ERROR = "error";
    private static final String DATA = "data";
    private static final int SUCCESS = 0;
    private static final int FAILURE = 1;

    @Setter
    @Getter
    private Client client;

    private Map<String, String> serverPluginCaches = new HashMap<>();

    public JavaManager() {
        this.client = new HttpClient();
    }

    public JavaManager(Client client) {
        this.client = client;
    }

    /**
     * 设置服务器地址
     *
     * @param url 服务器地址
     */
    public void setUrl(String url) {
        client.setUrl(url);
    }

    /**
     * 获取服务器地址
     *
     * @return 服务器地址
     */
    public String getUrl() {
        return client.getUrl();
    }

    /**
     * 连接到服务器
     *
     * @return 是否连接成功
     */
    public boolean connect() {
        return client.connect();
    }

    /**
     * 断开与服务器的连接
     */
    public void disconnect() {
        client.disconnect();
    }

    /**
     * 检查是否已连接
     *
     * @return 是否已连接
     */
    public boolean isConnected() {
        return client.isConnected();
    }

    /**
     * 发送请求到服务器（通用方法）
     *
     * @param requestMap 请求参数
     * @return 响应结果，如果请求失败返回包含错误信息的 Map
     */
    private Map<String, Object> sendRequest(Map<String, Object> requestMap) {
        byte[] bytes = serialize(requestMap);
        byte[] result = client.send(bytes);
        if (result != null) {
            return deserialize(result);
        }

        Map<String, Object> error = new HashMap<>();
        error.put(ERROR, "Request failed");
        return error;
    }

    /**
     * 执行插件方法的通用方法
     *
     * @param plugin     插件名称
     * @param className  类名
     * @param classType  类类型（用于 ByteBuddy redefine）
     * @param methodName 方法名
     * @param methodArgs 方法参数
     * @return 操作结果
     */
    private Map<String, Object> executePlugin(String plugin, String className, Class<?> classType,
                                              String methodName, Map<String, Object> methodArgs) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(ACTION, ACTION_RUN);
        requestMap.put(PLUGIN, plugin);
        requestMap.put(METHOD_NAME, methodName);
        if (methodArgs != null) {
            requestMap.put(ARGS, methodArgs);
        }

        // 如果插件未加载，添加类定义
        if (serverPluginCaches.get(plugin) == null) {
            byte[] classBytes = new ByteBuddy().redefine(classType)
                    .visit(new TargetJreVersionVisitorWrapper(Opcodes.V1_8))
                    .name(className).make().getBytes();
            requestMap.put(CLASSNAME, className);
            requestMap.put(CLASS_BYTES, classBytes);
        }

        Map<String, Object> response = sendRequest(requestMap);
        if (response != null && SUCCESS == (Integer) response.get(CODE)) {
            serverPluginCaches.put(plugin, className);
            return (Map<String, Object>) response.get(DATA);
        }
        return response;
    }

    // ==================== 基础功能 ====================

    public boolean test() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(ACTION, ACTION_STATUS);

        Map<String, Object> response = sendRequest(requestMap);
        if (response != null && SUCCESS == (Integer) response.get(CODE)) {
            serverPluginCaches = (Map<String, String>) response.get(PLUGIN_CACHES);
            return true;
        }
        return false;
    }

    public Map<String, Object> getBasicInfo() {
        return executePlugin("basicInfo", "BasicInfoCollector",
                SystemInfoCollector.class, "run", null);
    }

    // ==================== 序列化/反序列化 ====================

    static final byte NULL = 0x00;
    static final byte STRING = 0x01;
    static final byte INTEGER = 0x02;
    static final byte LONG = 0x03;
    static final byte DOUBLE = 0x04;
    static final byte BOOLEAN = 0x05;
    static final byte BYTE_ARRAY = 0x06;
    static final byte LIST = 0x7;
    static final byte OBJECT_ARRAY = 0x8;
    static final byte MAP = 0x10;

    @SneakyThrows
    public byte[] serialize(Map<String, Object> map) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        writeMap(dos, map);
        return baos.toByteArray();
    }

    @SneakyThrows
    public Map<String, Object> deserialize(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        byte type = dis.readByte();
        if (type == MAP) {
            return readMap(dis);
        } else {
            throw new IOException("Root object is not a Map.");
        }
    }

    private void writeMap(DataOutputStream dos, Map<String, Object> map) throws IOException {
        dos.writeByte(MAP);
        if (map == null) {
            dos.writeInt(0);
            return;
        }
        dos.writeInt(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            dos.writeUTF(entry.getKey());
            writeObject(dos, entry.getValue());
        }
    }

    private void writeObject(DataOutputStream dos, Object obj) throws IOException {
        if (obj == null) {
            dos.writeByte(NULL);
        } else if (obj instanceof String) {
            dos.writeByte(STRING);
            dos.writeUTF((String) obj);
        } else if (obj instanceof Integer) {
            dos.writeByte(INTEGER);
            dos.writeInt((Integer) obj);
        } else if (obj instanceof Long) {
            dos.writeByte(LONG);
            dos.writeLong((Long) obj);
        } else if (obj instanceof Double) {
            dos.writeByte(DOUBLE);
            dos.writeDouble((Double) obj);
        } else if (obj instanceof Boolean) {
            dos.writeByte(BOOLEAN);
            dos.writeBoolean((Boolean) obj);
        } else if (obj instanceof byte[]) {
            dos.writeByte(BYTE_ARRAY);
            byte[] bytes = (byte[]) obj;
            dos.writeInt(bytes.length);
            dos.write(bytes);
        } else if (obj instanceof List) {
            dos.writeByte(LIST);
            List<?> list = (List<?>) obj;
            dos.writeInt(list.size());
            for (Object item : list) {
                writeObject(dos, item);
            }
        } else if (obj instanceof Object[]) {
            dos.writeByte(OBJECT_ARRAY);
            Object[] array = (Object[]) obj;
            dos.writeInt(array.length);
            for (Object item : array) {
                writeObject(dos, item);
            }
        } else if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> subMap = (Map<String, Object>) obj;
            writeMap(dos, subMap);
        } else {
            throw new IllegalArgumentException("Unsupported type for serialization: " + obj.getClass().getName());
        }
    }

    private Map<String, Object> readMap(DataInputStream dis) throws IOException {
        int size = dis.readInt();
        Map<String, Object> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = dis.readUTF();
            Object value = readObject(dis);
            map.put(key, value);
        }
        return map;
    }

    private Object readObject(DataInputStream dis) throws IOException {
        byte type = dis.readByte();
        switch (type) {
            case NULL:
                return null;
            case STRING:
                return dis.readUTF();
            case INTEGER:
                return dis.readInt();
            case LONG:
                return dis.readLong();
            case DOUBLE:
                return dis.readDouble();
            case BOOLEAN:
                return dis.readBoolean();
            case BYTE_ARRAY:
                int len = dis.readInt();
                byte[] bytes = new byte[len];
                dis.readFully(bytes);
                return bytes;
            case LIST:
                int listSize = dis.readInt();
                List<Object> list = new ArrayList<>(listSize);
                for (int i = 0; i < listSize; i++) {
                    list.add(readObject(dis));
                }
                return list;
            case OBJECT_ARRAY:
                int arrayLength = dis.readInt();
                Object[] array = new Object[arrayLength];
                for (int i = 0; i < arrayLength; i++) {
                    array[i] = readObject(dis);
                }
                return array;
            case MAP:
                return readMap(dis);
            default:
                throw new IOException("Unknown data type found in stream: " + type);
        }
    }
}
