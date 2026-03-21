package com.reajason.noone.plugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP repeater plugin. Sends arbitrary HTTP requests from the target machine
 * and returns the full response including status, headers, and body.
 * Supports json, form-urlencoded, form-data (with file upload), xml, and raw (with file upload) body types.
 * Supports query params appended to the URL.
 *
 * @author ReaJason
 * @since 2026/3/21
 */
public class HttpRepeater {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> ctx = (Map<String, Object>) obj;
        HashMap<String, Object> result = new HashMap<String, Object>();
        ctx.put("result", result);

        String url = asString(ctx.get("url"));
        if (url == null || url.isEmpty()) {
            result.put("error", "url is required");
            return true;
        }

        String method = asString(ctx.get("method"));
        if (method == null || method.isEmpty()) {
            method = "GET";
        }
        method = method.toUpperCase();

        Map<String, Object> headers = null;
        Object headersObj = ctx.get("headers");
        if (headersObj instanceof Map) {
            headers = (Map<String, Object>) headersObj;
        }

        String contentType = asString(ctx.get("contentType"));
        String body = asString(ctx.get("body"));
        String bodyBase64 = asString(ctx.get("bodyBase64"));
        List<Object> formData = null;
        Object formDataObj = ctx.get("formData");
        if (formDataObj instanceof List) {
            formData = (List<Object>) formDataObj;
        }

        List<Object> params = null;
        Object paramsObj = ctx.get("params");
        if (paramsObj instanceof List) {
            params = (List<Object>) paramsObj;
        }

        boolean followRedirects = asBool(ctx.get("followRedirects"), true);
        int timeout = asInt(ctx.get("timeout"), 30000);
        int maxRedirects = asInt(ctx.get("maxRedirects"), 5);

        url = appendQueryParams(url, params);

        result.put("url", url);
        result.put("method", method);
        HttpURLConnection conn = null;
        try {
            String currentUrl = url;
            String currentMethod = method;
            boolean sendBody = true;
            int redirectCount = 0;

            while (true) {
                URL targetUrl = new URL(currentUrl);
                conn = (HttpURLConnection) targetUrl.openConnection();
                conn.setRequestMethod(currentMethod);
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
                conn.setInstanceFollowRedirects(false);

                if (headers != null) {
                    for (Map.Entry<String, Object> entry : headers.entrySet()) {
                        String key = entry.getKey();
                        Object val = entry.getValue();
                        if (key != null && val != null) {
                            conn.setRequestProperty(key.trim(), String.valueOf(val).trim());
                        }
                    }
                }

                if (sendBody) {
                    byte[] bodyBytes = buildRequestBody(contentType, body, bodyBase64, formData, conn);
                    if (bodyBytes != null && bodyBytes.length > 0) {
                        conn.setDoOutput(true);
                        OutputStream os = null;
                        try {
                            os = conn.getOutputStream();
                            os.write(bodyBytes);
                            os.flush();
                        } finally {
                            if (os != null) {
                                try {
                                    os.close();
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }

                int statusCode = conn.getResponseCode();

                if (followRedirects && isRedirect(statusCode) && redirectCount < maxRedirects) {
                    String location = conn.getHeaderField("Location");
                    if (location != null && !location.isEmpty()) {
                        conn.disconnect();
                        conn = null;
                        if (location.startsWith("/")) {
                            URL base = new URL(currentUrl);
                            location = base.getProtocol() + "://" + base.getAuthority() + location;
                        }
                        currentUrl = location;
                        if (statusCode == 303) {
                            currentMethod = "GET";
                            sendBody = false;
                        } else if (statusCode == 301 || statusCode == 302) {
                            if ("POST".equals(currentMethod)) {
                                currentMethod = "GET";
                                sendBody = false;
                            }
                        }
                        redirectCount++;
                        continue;
                    }
                }

                result.put("statusCode", Integer.valueOf(statusCode));
                result.put("statusMessage", conn.getResponseMessage() != null ? conn.getResponseMessage() : "");
                if (redirectCount > 0) {
                    result.put("redirectCount", Integer.valueOf(redirectCount));
                    result.put("finalUrl", currentUrl);
                }

                HashMap<String, Object> respHeaders = new HashMap<String, Object>();
                int headerIdx = 0;
                while (true) {
                    String headerName = conn.getHeaderFieldKey(headerIdx);
                    String headerValue = conn.getHeaderField(headerIdx);
                    if (headerValue == null) {
                        break;
                    }
                    if (headerName != null) {
                        Object existing = respHeaders.get(headerName);
                        if (existing != null) {
                            respHeaders.put(headerName, String.valueOf(existing) + ", " + headerValue);
                        } else {
                            respHeaders.put(headerName, headerValue);
                        }
                    }
                    headerIdx++;
                }
                result.put("responseHeaders", respHeaders);

                InputStream is = null;
                try {
                    if (statusCode >= 400) {
                        is = conn.getErrorStream();
                    }
                    if (is == null) {
                        is = conn.getInputStream();
                    }
                    byte[] respBytes = readAllBytes(is);
                    String respBody = new String(respBytes, "UTF-8");
                    result.put("body", respBody);
                    result.put("contentLength", Integer.valueOf(respBytes.length));
                } catch (Exception e) {
                    result.put("body", "");
                    result.put("contentLength", Integer.valueOf(0));
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                break;
            }
        } catch (Exception e) {
            result.put("error", "HTTP request failed: " + safeMessage(e));
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }

        return true;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    @SuppressWarnings("unchecked")
    private static String appendQueryParams(String url, List<Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        try {
            StringBuilder qs = new StringBuilder();
            for (int i = 0; i < params.size(); i++) {
                Object item = params.get(i);
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> entry = (Map<String, Object>) item;
                String key = asString(entry.get("key"));
                String value = asString(entry.get("value"));
                if (key == null || key.isEmpty()) {
                    continue;
                }
                if (qs.length() > 0) {
                    qs.append('&');
                }
                qs.append(URLEncoder.encode(key, "UTF-8"));
                qs.append('=');
                qs.append(URLEncoder.encode(value != null ? value : "", "UTF-8"));
            }
            if (qs.length() == 0) {
                return url;
            }
            if (url.contains("?")) {
                return url + "&" + qs.toString();
            }
            return url + "?" + qs.toString();
        } catch (Exception e) {
            return url;
        }
    }

    @SuppressWarnings("unchecked")
    private static byte[] buildRequestBody(String contentType, String body, String bodyBase64,
                                           List<Object> formData, HttpURLConnection conn) {
        if (contentType == null || contentType.isEmpty() || "none".equals(contentType)) {
            return null;
        }

        try {
            if ("json".equals(contentType)) {
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                return body != null ? body.getBytes("UTF-8") : null;
            }

            if ("xml".equals(contentType)) {
                conn.setRequestProperty("Content-Type", "application/xml; charset=UTF-8");
                return body != null ? body.getBytes("UTF-8") : null;
            }

            if ("raw".equals(contentType)) {
                String existing = conn.getRequestProperty("Content-Type");
                if (existing == null || existing.isEmpty()) {
                    conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                }
                if (bodyBase64 != null && !bodyBase64.isEmpty()) {
                    return decodeBase64(bodyBase64);
                }
                return body != null ? body.getBytes("UTF-8") : null;
            }

            if ("form-urlencoded".equals(contentType)) {
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                if (formData == null || formData.isEmpty()) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < formData.size(); i++) {
                    Object item = formData.get(i);
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> entry = (Map<String, Object>) item;
                    String key = asString(entry.get("key"));
                    String value = asString(entry.get("value"));
                    if (key == null || key.isEmpty()) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append('&');
                    }
                    sb.append(URLEncoder.encode(key, "UTF-8"));
                    sb.append('=');
                    sb.append(URLEncoder.encode(value != null ? value : "", "UTF-8"));
                }
                return sb.toString().getBytes("UTF-8");
            }

            if ("form-data".equals(contentType)) {
                if (formData == null || formData.isEmpty()) {
                    return null;
                }
                String boundary = "----NoOneBoundary" + System.currentTimeMillis();
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] crlf = "\r\n".getBytes("UTF-8");
                byte[] dashdash = "--".getBytes("UTF-8");
                byte[] boundaryBytes = boundary.getBytes("UTF-8");

                for (int i = 0; i < formData.size(); i++) {
                    Object item = formData.get(i);
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> entry = (Map<String, Object>) item;
                    String key = asString(entry.get("key"));
                    if (key == null || key.isEmpty()) {
                        continue;
                    }
                    String fieldType = asString(entry.get("type"));
                    boolean isFile = "file".equals(fieldType);

                    baos.write(dashdash);
                    baos.write(boundaryBytes);
                    baos.write(crlf);

                    if (isFile) {
                        String filename = asString(entry.get("filename"));
                        if (filename == null || filename.isEmpty()) {
                            filename = "upload";
                        }
                        String fileContentType = asString(entry.get("fileContentType"));
                        if (fileContentType == null || fileContentType.isEmpty()) {
                            fileContentType = "application/octet-stream";
                        }
                        String disposition = "Content-Disposition: form-data; name=\"" + key + "\"; filename=\"" + filename + "\"";
                        baos.write(disposition.getBytes("UTF-8"));
                        baos.write(crlf);
                        String ctHeader = "Content-Type: " + fileContentType;
                        baos.write(ctHeader.getBytes("UTF-8"));
                        baos.write(crlf);
                        baos.write(crlf);
                        String fileBase64 = asString(entry.get("value"));
                        if (fileBase64 != null && !fileBase64.isEmpty()) {
                            baos.write(decodeBase64(fileBase64));
                        }
                    } else {
                        String value = asString(entry.get("value"));
                        String disposition = "Content-Disposition: form-data; name=\"" + key + "\"";
                        baos.write(disposition.getBytes("UTF-8"));
                        baos.write(crlf);
                        baos.write(crlf);
                        baos.write((value != null ? value : "").getBytes("UTF-8"));
                    }
                    baos.write(crlf);
                }
                baos.write(dashdash);
                baos.write(boundaryBytes);
                baos.write(dashdash);
                baos.write(crlf);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }

        return body != null ? safeGetBytes(body) : null;
    }

    private static byte[] decodeBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new byte[0];
        }
        String clean = encoded.replace("\r", "").replace("\n", "").replace(" ", "");
        int pad = 0;
        if (clean.endsWith("==")) {
            pad = 2;
        } else if (clean.endsWith("=")) {
            pad = 1;
        }
        int len = clean.length();
        int outLen = (len * 3) / 4 - pad;
        byte[] out = new byte[outLen];
        int outIdx = 0;
        for (int i = 0; i < len; i += 4) {
            int b0 = b64val(clean.charAt(i));
            int b1 = b64val(clean.charAt(i + 1));
            int b2 = (i + 2 < len) ? b64val(clean.charAt(i + 2)) : 0;
            int b3 = (i + 3 < len) ? b64val(clean.charAt(i + 3)) : 0;
            int triple = (b0 << 18) | (b1 << 12) | (b2 << 6) | b3;
            if (outIdx < outLen) {
                out[outIdx++] = (byte) ((triple >> 16) & 0xFF);
            }
            if (outIdx < outLen) {
                out[outIdx++] = (byte) ((triple >> 8) & 0xFF);
            }
            if (outIdx < outLen) {
                out[outIdx++] = (byte) (triple & 0xFF);
            }
        }
        return out;
    }

    private static int b64val(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return 0;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private static byte[] safeGetBytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (Exception e) {
            return s.getBytes();
        }
    }

    private static String asString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return ((String) obj).trim();
        return obj.toString().trim();
    }

    private static int asInt(Object obj, int defaultVal) {
        if (obj instanceof Integer) return ((Integer) obj).intValue();
        if (obj instanceof Long) return (int) ((Long) obj).longValue();
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    private static boolean asBool(Object obj, boolean defaultVal) {
        if (obj instanceof Boolean) return ((Boolean) obj).booleanValue();
        if (obj instanceof String) {
            String s = ((String) obj).trim().toLowerCase();
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
            if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        }
        return defaultVal;
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }
}
