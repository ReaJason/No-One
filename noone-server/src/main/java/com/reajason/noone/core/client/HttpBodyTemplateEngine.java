package com.reajason.noone.core.client;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpBodyTemplateEngine {
    public static final String PAYLOAD_PLACEHOLDER = "{{payload}}";
    public static final String BOUNDARY_PLACEHOLDER = "{{boundary}}";

    private static final Pattern BINARY_TAG_PATTERN =
            Pattern.compile("(?is)<(hex|base64)>(.*?)</\\1>");

    private HttpBodyTemplateEngine() {
    }

    public static String defaultRequestTemplate(HttpRequestBodyType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case FORM_URLENCODED -> "username=admin&action=login&q={{payload}}&token=123456";
            case TEXT -> "hello{{payload}}world";
            case MULTIPART_FORM_DATA -> """
                    --{{boundary}}
                    Content-Disposition: form-data; name="username"
                    
                    admin
                    --{{boundary}}
                    Content-Disposition: form-data; name="file"; filename="test.png"
                    Content-Type: image/png
                    
                    <hex>89504E470D0A1A0A0000000D4948445200000001000000010802000000907753DE0000000C49444154789C63F8CFC000000301010018DD8D000000000049454E44AE426082</hex>{{payload}}
                    --{{boundary}}--
                    """;
            case JSON -> "{\"hello\": \"{{payload}}\"}";
            case XML -> "<hello>{{payload}}</hello>";
            case BINARY -> "<base64>aGVsbG8=</base64>{{payload}}";
        };
    }

    public static EncodedBody encodeRequestBody(
            HttpRequestBodyType type,
            String template,
            String payload
    ) {
        HttpRequestBodyType resolvedType = type != null ? type : HttpRequestBodyType.FORM_URLENCODED;
        String resolvedTemplate = isBlank(template) ? defaultRequestTemplate(resolvedType) : template;

        return switch (resolvedType) {
            case FORM_URLENCODED -> {
                String encodedPayload = urlEncode(payload);
                String body = replacePayloadPlaceholder(resolvedTemplate, encodedPayload);
                yield EncodedBody.text(body, "application/x-www-form-urlencoded; charset=utf-8");
            }
            case TEXT -> EncodedBody.text(
                    replacePayloadPlaceholder(resolvedTemplate, payload),
                    "text/plain; charset=utf-8"
            );
            case JSON -> EncodedBody.text(
                    replacePayloadPlaceholder(resolvedTemplate, payload),
                    "application/json; charset=utf-8"
            );
            case XML -> EncodedBody.text(
                    replacePayloadPlaceholder(resolvedTemplate, payload),
                    "application/xml; charset=utf-8"
            );
            case BINARY -> {
                String replaced = replacePayloadPlaceholder(resolvedTemplate, payload);
                byte[] bytes = renderBinaryLiteral(replaced);
                yield new EncodedBody(bytes, "application/octet-stream");
            }
            case MULTIPART_FORM_DATA -> {
                String boundary = generateBoundary();
                String replaced = resolvedTemplate.replace(BOUNDARY_PLACEHOLDER, boundary);
                replaced = replacePayloadPlaceholder(replaced, payload);

                String normalized = normalizeMultipartNewlines(replaced);
                byte[] bytes = renderBinaryLiteral(normalized);
                yield new EncodedBody(bytes, "multipart/form-data; boundary=" + boundary);
            }
        };
    }

    public static EncodedBody encodeRequestBody(
            HttpRequestBodyType type,
            String template,
            byte[] payloadBytes
    ) {
        HttpRequestBodyType resolvedType = type != null ? type : HttpRequestBodyType.FORM_URLENCODED;
        String resolvedTemplate = isBlank(template) ? defaultRequestTemplate(resolvedType) : template;
        byte[] payload = payloadBytes != null ? payloadBytes : new byte[0];

        return switch (resolvedType) {
            case FORM_URLENCODED -> {
                String payloadText = new String(payload, StandardCharsets.UTF_8);
                String encodedPayload = urlEncode(payloadText);
                String body = replacePayloadPlaceholder(resolvedTemplate, encodedPayload);
                yield EncodedBody.text(body, "application/x-www-form-urlencoded; charset=utf-8");
            }
            case TEXT -> EncodedBody.text(
                    replacePayloadPlaceholder(resolvedTemplate, new String(payload, StandardCharsets.UTF_8)),
                    "text/plain; charset=utf-8"
            );
            case JSON -> EncodedBody.text(
                    replacePayloadPlaceholder(resolvedTemplate, new String(payload, StandardCharsets.UTF_8)),
                    "application/json; charset=utf-8"
            );
            case XML -> EncodedBody.text(
                    replacePayloadPlaceholder(resolvedTemplate, new String(payload, StandardCharsets.UTF_8)),
                    "application/xml; charset=utf-8"
            );
            case BINARY -> {
                byte[] bytes = renderBinaryTemplateWithPayload(resolvedTemplate, payload);
                yield new EncodedBody(bytes, "application/octet-stream");
            }
            case MULTIPART_FORM_DATA -> {
                String boundary = generateBoundary();
                String withBoundary = resolvedTemplate.replace(BOUNDARY_PLACEHOLDER, boundary);
                byte[] bytes = renderMultipartTemplateWithPayload(withBoundary, payload);
                yield new EncodedBody(bytes, "multipart/form-data; boundary=" + boundary);
            }
        };
    }

    public static String extractResponsePayload(
            HttpResponseBodyType type,
            String template,
            byte[] responseBytes
    ) {
        if (responseBytes == null) {
            return null;
        }

        HttpResponseBodyType resolvedType = type != null ? type : HttpResponseBodyType.TEXT;
        if (isBlank(template)) {
            return new String(responseBytes, StandardCharsets.UTF_8);
        }

        if (resolvedType == HttpResponseBodyType.BINARY) {
            return extractBinaryByTemplate(template, responseBytes);
        }

        String responseText = new String(responseBytes, StandardCharsets.UTF_8);
        return extractTextByTemplate(template, responseText);
    }

    public static byte[] extractResponsePayloadBytes(
            HttpResponseBodyType type,
            String template,
            byte[] responseBytes
    ) {
        if (responseBytes == null) {
            return null;
        }

        HttpResponseBodyType resolvedType = type != null ? type : HttpResponseBodyType.TEXT;
        if (isBlank(template)) {
            return responseBytes;
        }

        if (resolvedType == HttpResponseBodyType.BINARY) {
            return extractBinaryPayloadBytes(template, responseBytes);
        }

        String extracted = extractResponsePayload(type, template, responseBytes);
        if (extracted == null) {
            return null;
        }
        return extracted.getBytes(StandardCharsets.UTF_8);
    }

    public record EncodedBody(byte[] bytes, String contentType) {
        public static EncodedBody text(String body, String contentType) {
            byte[] bytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return new EncodedBody(bytes, contentType);
        }
    }

    private static String extractTextByTemplate(String template, String responseText) {
        if (isBlank(template)) {
            return responseText;
        }

        String normalizedTemplate = normalizeNewlines(template);
        String normalizedResponse = normalizeNewlines(responseText);

        int placeholderIndex = normalizedTemplate.indexOf(PAYLOAD_PLACEHOLDER);
        if (placeholderIndex < 0) {
            return normalizedResponse;
        }

        String prefix = normalizedTemplate.substring(0, placeholderIndex);
        String suffix = normalizedTemplate.substring(placeholderIndex + PAYLOAD_PLACEHOLDER.length());

        int start = prefix.isEmpty() ? 0 : normalizedResponse.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        start += prefix.length();

        if (suffix.isEmpty()) {
            return normalizedResponse.substring(start);
        }

        int end = normalizedResponse.indexOf(suffix, start);
        if (end < 0) {
            return null;
        }
        return normalizedResponse.substring(start, end);
    }

    private static String extractBinaryByTemplate(String template, byte[] responseBytes) {
        if (isBlank(template)) {
            return new String(responseBytes, StandardCharsets.UTF_8);
        }

        int placeholderIndex = template.indexOf(PAYLOAD_PLACEHOLDER);
        if (placeholderIndex < 0) {
            return new String(responseBytes, StandardCharsets.UTF_8);
        }

        String prefixTemplate = template.substring(0, placeholderIndex);
        String suffixTemplate = template.substring(placeholderIndex + PAYLOAD_PLACEHOLDER.length());

        byte[] prefixBytes = renderBinaryLiteral(prefixTemplate);
        byte[] suffixBytes = renderBinaryLiteral(suffixTemplate);

        int start = prefixBytes.length == 0 ? 0 : indexOf(responseBytes, prefixBytes, 0);
        if (start < 0) {
            return null;
        }
        start += prefixBytes.length;

        int end;
        if (suffixBytes.length == 0) {
            end = responseBytes.length;
        } else {
            end = indexOf(responseBytes, suffixBytes, start);
            if (end < 0) {
                return null;
            }
        }

        byte[] payloadBytes = new byte[end - start];
        System.arraycopy(responseBytes, start, payloadBytes, 0, payloadBytes.length);
        return new String(payloadBytes, StandardCharsets.UTF_8);
    }

    private static byte[] extractBinaryPayloadBytes(String template, byte[] responseBytes) {
        if (isBlank(template)) {
            return responseBytes;
        }

        int placeholderIndex = template.indexOf(PAYLOAD_PLACEHOLDER);
        if (placeholderIndex < 0) {
            return responseBytes;
        }

        String prefixTemplate = template.substring(0, placeholderIndex);
        String suffixTemplate = template.substring(placeholderIndex + PAYLOAD_PLACEHOLDER.length());

        byte[] prefixBytes = renderBinaryLiteral(prefixTemplate);
        byte[] suffixBytes = renderBinaryLiteral(suffixTemplate);

        int start = prefixBytes.length == 0 ? 0 : indexOf(responseBytes, prefixBytes, 0);
        if (start < 0) {
            return null;
        }
        start += prefixBytes.length;

        int end;
        if (suffixBytes.length == 0) {
            end = responseBytes.length;
        } else {
            end = indexOf(responseBytes, suffixBytes, start);
            if (end < 0) {
                return null;
            }
        }

        byte[] payloadBytes = new byte[end - start];
        System.arraycopy(responseBytes, start, payloadBytes, 0, payloadBytes.length);
        return payloadBytes;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
        if (needle.length == 0) {
            return fromIndex;
        }
        if (haystack.length < needle.length || fromIndex >= haystack.length) {
            return -1;
        }
        outer:
        for (int i = Math.max(0, fromIndex); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static byte[] renderBinaryLiteral(String template) {
        if (template == null || template.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Matcher matcher = BINARY_TAG_PATTERN.matcher(template);
        int cursor = 0;
        while (matcher.find()) {
            String before = template.substring(cursor, matcher.start());
            if (!before.isEmpty()) {
                out.writeBytes(before.getBytes(StandardCharsets.UTF_8));
            }

            String type = matcher.group(1).toLowerCase();
            String content = matcher.group(2);
            byte[] decoded = switch (type) {
                case "hex" -> decodeHex(content);
                case "base64" -> decodeBase64(content);
                default -> throw new IllegalStateException("Unsupported binary tag: " + type);
            };
            out.writeBytes(decoded);
            cursor = matcher.end();
        }

        String tail = template.substring(cursor);
        if (!tail.isEmpty()) {
            out.writeBytes(tail.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static byte[] renderBinaryTemplateWithPayload(String template, byte[] payload) {
        if (template == null || template.isEmpty()) {
            return payload != null ? payload : new byte[0];
        }
        if (payload == null) {
            payload = new byte[0];
        }
        if (!template.contains(PAYLOAD_PLACEHOLDER)) {
            return renderBinaryLiteral(template);
        }

        List<String> parts = splitByPayloadPlaceholder(template);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < parts.size(); i++) {
            byte[] partBytes = renderBinaryLiteral(parts.get(i));
            if (partBytes.length > 0) {
                out.write(partBytes, 0, partBytes.length);
            }
            if (i < parts.size() - 1 && payload.length > 0) {
                out.write(payload, 0, payload.length);
            }
        }
        return out.toByteArray();
    }

    private static byte[] renderMultipartTemplateWithPayload(String template, byte[] payload) {
        if (template == null || template.isEmpty()) {
            return payload != null ? payload : new byte[0];
        }
        if (payload == null) {
            payload = new byte[0];
        }

        List<String> parts = splitByPayloadPlaceholder(template);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < parts.size(); i++) {
            String normalized = normalizeMultipartNewlines(parts.get(i));
            byte[] partBytes = renderBinaryLiteral(normalized);
            if (partBytes.length > 0) {
                out.write(partBytes, 0, partBytes.length);
            }
            if (i < parts.size() - 1 && payload.length > 0) {
                out.write(payload, 0, payload.length);
            }
        }
        return out.toByteArray();
    }

    private static List<String> splitByPayloadPlaceholder(String template) {
        List<String> parts = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int idx = template.indexOf(PAYLOAD_PLACEHOLDER, cursor);
            if (idx < 0) {
                parts.add(template.substring(cursor));
                return parts;
            }
            parts.add(template.substring(cursor, idx));
            cursor = idx + PAYLOAD_PLACEHOLDER.length();
        }
    }

    private static byte[] decodeBase64(String content) {
        if (content == null) {
            return new byte[0];
        }
        String normalized = content.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static byte[] decodeHex(String content) {
        if (content == null) {
            return new byte[0];
        }
        String normalized = content.replaceAll("\\s+", "");
        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex content length must be even");
        }
        int len = normalized.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = Character.digit(normalized.charAt(i * 2), 16);
            int lo = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character");
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private static String replacePayloadPlaceholder(String template, String payload) {
        if (template == null) {
            return null;
        }
        return template.replace(PAYLOAD_PLACEHOLDER, payload != null ? payload : "");
    }

    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String generateBoundary() {
        return "NoOneBoundary" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalizeMultipartNewlines(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        return normalized.replace("\n", "\r\n");
    }

    private static String normalizeNewlines(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
