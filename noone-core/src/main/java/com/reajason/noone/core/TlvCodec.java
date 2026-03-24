package com.reajason.noone.core;

import lombok.SneakyThrows;

import java.io.*;
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
    static final byte MAP = 0x10;

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
        int size = dis.readInt();
        Map<String, Object> map = new HashMap<>(size);
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
            case SET:
                int setSize = dis.readInt();
                Set<Object> set = new LinkedHashSet<>(setSize);
                for (int i = 0; i < setSize; i++) {
                    set.add(readObject(dis));
                }
                return set;
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
