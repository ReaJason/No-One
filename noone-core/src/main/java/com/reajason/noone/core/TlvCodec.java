package com.reajason.noone.core;

import lombok.SneakyThrows;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Shared TLV (Type-Length-Value) codec for serializing/deserializing maps
 * used by both {@link JavaConnection} and {@link NodeJsConnection}.
 *
 * @author ReaJason
 * @since 2025/12/13
 */
public final class TlvCodec {

    static final byte NULL = 0x00;
    static final byte STRING = 0x01;
    static final byte INTEGER = 0x02;
    static final byte LONG = 0x03;
    static final byte DOUBLE = 0x04;
    static final byte BOOLEAN = 0x05;
    static final byte BYTE_ARRAY = 0x06;
    static final byte LIST = 0x07;
    static final byte OBJECT_ARRAY = 0x08;
    static final byte SET = 0x09;
    // Strings whose modified UTF-8 representation exceeds MAX_UTF_LENGTH bytes
    // are written with an int length and standard UTF-8 instead of writeUTF.
    static final byte LONG_STRING = 0x0A;
    static final byte MAP = 0x10;
    static final int MAX_UTF_LENGTH = 0xffff;
    // Initial capacity cap for collections read from the wire, so a malicious
    // size prefix cannot trigger a huge eager allocation.
    private static final int INITIAL_CAPACITY_LIMIT = 1024;

    private TlvCodec() {
    }

    @SneakyThrows
    public static byte[] serialize(Map<String, Object> map) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        writeMap(dos, map);
        return baos.toByteArray();
    }

    @SneakyThrows
    public static Map<String, Object> deserialize(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        byte type = dis.readByte();
        if (type == MAP) {
            return readMap(dis);
        } else {
            throw new IOException("Root object is not a Map.");
        }
    }

    static void writeMap(DataOutputStream dos, Map<String, Object> map) throws IOException {
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

    static void writeObject(DataOutputStream dos, Object obj) throws IOException {
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

    static Map<String, Object> readMap(DataInputStream dis) throws IOException {
        int size = readCollectionSize(dis, "map");
        Map<String, Object> map = new HashMap<>(Math.min(size, INITIAL_CAPACITY_LIMIT));
        for (int i = 0; i < size; i++) {
            String key = dis.readUTF();
            Object value = readObject(dis);
            map.put(key, value);
        }
        return map;
    }

    static Object readObject(DataInputStream dis) throws IOException {
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

    static void writeStringObject(DataOutputStream dos, String value) throws IOException {
        if (modifiedUtfLength(value) <= MAX_UTF_LENGTH) {
            dos.writeByte(STRING);
            dos.writeUTF(value);
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeByte(LONG_STRING);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    static String readLongString(DataInputStream dis) throws IOException {
        int length = readLength(dis, "string");
        byte[] bytes = new byte[length];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static int modifiedUtfLength(String value) {
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

    static int readLength(DataInputStream dis, String what) throws IOException {
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

    static int readCollectionSize(DataInputStream dis, String what) throws IOException {
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
}
