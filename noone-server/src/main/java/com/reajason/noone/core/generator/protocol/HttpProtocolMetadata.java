package com.reajason.noone.core.generator.protocol;

import com.reajason.noone.core.client.HttpBodyTemplateEngine;
import com.reajason.noone.server.profile.config.HttpRequestBodyType;
import com.reajason.noone.server.profile.config.HttpResponseBodyType;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for calculating prefix/suffix indexes and extracting metadata from HTTP protocol templates.
 */
public final class HttpProtocolMetadata {
    private static final String PAYLOAD_PLACEHOLDER = "{{payload}}";
    private static final String BOUNDARY_PLACEHOLDER = "{{boundary}}";
    private static final String MAX_LENGTH_BOUNDARY = "NoOneBoundary" + "0".repeat(32);
    private static final Pattern FORM_PARAM_PATTERN = Pattern.compile("(?:^|&)([^=&]+)=([^&]*)");

    private HttpProtocolMetadata() {
    }

    /**
     * Extract parameter name from FORM_URLENCODED template.
     * Example: "q={{payload}}" -> "q"
     * Example: "username=admin&q={{payload}}&token=123" -> "q"
     */
    public static String extractParameterName(String template) {
        if (template == null || template.isEmpty()) {
            return null;
        }

        Matcher matcher = FORM_PARAM_PATTERN.matcher(template);
        while (matcher.find()) {
            String value = matcher.group(2);
            if (value.contains(PAYLOAD_PLACEHOLDER)) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Calculate prefix/suffix for FORM_URLENCODED parameter value.
     * Example: "q=prefix{{payload}}suffix" -> PrefixSuffixIndexes(6, 6)
     * Returns null if no prefix/suffix exists (simple case).
     */
    public static PrefixSuffixIndexes extractFormParameterIndexes(String template) {
        if (template == null || template.isEmpty()) {
            return new PrefixSuffixIndexes(0, 0);
        }

        Matcher matcher = FORM_PARAM_PATTERN.matcher(template);
        while (matcher.find()) {
            String value = matcher.group(2);
            if (value.contains(PAYLOAD_PLACEHOLDER)) {
                int payloadIndex = value.indexOf(PAYLOAD_PLACEHOLDER);
                String prefix = value.substring(0, payloadIndex);
                String suffix = value.substring(payloadIndex + PAYLOAD_PLACEHOLDER.length());

                if (prefix.isEmpty() && suffix.isEmpty()) {
                    return null; // Simple case - no prefix/suffix
                }

                byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
                byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);
                return new PrefixSuffixIndexes(prefixBytes.length, suffixBytes.length);
            }
        }
        return new PrefixSuffixIndexes(0, 0);
    }

    /**
     * Calculate byte-level indexes for request body extraction.
     * Handles binary literals and multipart boundaries.
     */
    public static PrefixSuffixIndexes calculateRequestBodyIndexes(
            HttpRequestBodyType bodyType,
            String template
    ) {
        if (bodyType == null || template == null || template.isEmpty()) {
            return new PrefixSuffixIndexes(0, 0);
        }

        if (bodyType == HttpRequestBodyType.FORM_URLENCODED) {
            PrefixSuffixIndexes indexes = extractFormParameterIndexes(template);
            return indexes != null ? indexes : new PrefixSuffixIndexes(0, 0);
        }

        if (bodyType == HttpRequestBodyType.MULTIPART_FORM_DATA) {
            // Substitute boundary with max-length boundary for calculation
            String substituted = template.replace(BOUNDARY_PLACEHOLDER, MAX_LENGTH_BOUNDARY);
            // Normalize newlines for multipart
            String normalized = normalizeMultipartNewlines(substituted);
            return calculateBinaryIndexes(normalized);
        }

        if (bodyType == HttpRequestBodyType.BINARY) {
            return calculateBinaryIndexes(template);
        }

        // TEXT, JSON, XML
        return calculateTextIndexes(template);
    }

    /**
     * Split response template into prefix/suffix parts.
     */
    public static ResponsePrefixSuffix calculateResponseParts(
            HttpResponseBodyType bodyType,
            String template
    ) {
        if (bodyType == null || template == null || template.isEmpty()) {
            return new ResponsePrefixSuffix(new byte[0], new byte[0]);
        }

        int payloadIndex = template.indexOf(PAYLOAD_PLACEHOLDER);
        if (payloadIndex < 0) {
            // No placeholder, return template as prefix
            byte[] bytes = bodyType == HttpResponseBodyType.BINARY || bodyType == HttpResponseBodyType.MULTIPART_FORM_DATA
                    ? HttpBodyTemplateEngine.renderBinaryLiteral(template)
                    : template.getBytes(StandardCharsets.UTF_8);
            return new ResponsePrefixSuffix(bytes, new byte[0]);
        }

        String prefixTemplate = template.substring(0, payloadIndex);
        String suffixTemplate = template.substring(payloadIndex + PAYLOAD_PLACEHOLDER.length());

        if (bodyType == HttpResponseBodyType.BINARY) {
            byte[] prefixBytes = HttpBodyTemplateEngine.renderBinaryLiteral(prefixTemplate);
            byte[] suffixBytes = HttpBodyTemplateEngine.renderBinaryLiteral(suffixTemplate);
            return new ResponsePrefixSuffix(prefixBytes, suffixBytes);
        }

        if (bodyType == HttpResponseBodyType.MULTIPART_FORM_DATA) {
            // Substitute boundary with max-length boundary
            String prefixSubstituted = prefixTemplate.replace(BOUNDARY_PLACEHOLDER, MAX_LENGTH_BOUNDARY);
            String suffixSubstituted = suffixTemplate.replace(BOUNDARY_PLACEHOLDER, MAX_LENGTH_BOUNDARY);
            // Normalize newlines
            String prefixNormalized = normalizeMultipartNewlines(prefixSubstituted);
            String suffixNormalized = normalizeMultipartNewlines(suffixSubstituted);
            byte[] prefixBytes = HttpBodyTemplateEngine.renderBinaryLiteral(prefixNormalized);
            byte[] suffixBytes = HttpBodyTemplateEngine.renderBinaryLiteral(suffixNormalized);
            return new ResponsePrefixSuffix(prefixBytes, suffixBytes);
        }

        // TEXT, JSON, XML, FORM_URLENCODED
        byte[] prefixBytes = prefixTemplate.getBytes(StandardCharsets.UTF_8);
        byte[] suffixBytes = suffixTemplate.getBytes(StandardCharsets.UTF_8);
        return new ResponsePrefixSuffix(prefixBytes, suffixBytes);
    }

    /**
     * Encode headers as null-delimited string: "Key1\0Value1\0Key2\0Value2"
     */
    public static String encodeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\0');
            }
            sb.append(entry.getKey());
            sb.append('\0');
            sb.append(entry.getValue());
        }
        return sb.toString();
    }

    private static PrefixSuffixIndexes calculateTextIndexes(String template) {
        int payloadIndex = template.indexOf(PAYLOAD_PLACEHOLDER);
        if (payloadIndex < 0) {
            return new PrefixSuffixIndexes(0, 0);
        }

        String prefix = template.substring(0, payloadIndex);
        String suffix = template.substring(payloadIndex + PAYLOAD_PLACEHOLDER.length());

        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);

        return new PrefixSuffixIndexes(prefixBytes.length, suffixBytes.length);
    }

    private static PrefixSuffixIndexes calculateBinaryIndexes(String template) {
        int payloadIndex = template.indexOf(PAYLOAD_PLACEHOLDER);
        if (payloadIndex < 0) {
            return new PrefixSuffixIndexes(0, 0);
        }

        String prefixTemplate = template.substring(0, payloadIndex);
        String suffixTemplate = template.substring(payloadIndex + PAYLOAD_PLACEHOLDER.length());

        byte[] prefixBytes = HttpBodyTemplateEngine.renderBinaryLiteral(prefixTemplate);
        byte[] suffixBytes = HttpBodyTemplateEngine.renderBinaryLiteral(suffixTemplate);

        return new PrefixSuffixIndexes(prefixBytes.length, suffixBytes.length);
    }

    private static String normalizeMultipartNewlines(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        return normalized.replace("\n", "\r\n");
    }

    public record PrefixSuffixIndexes(int prefixLength, int suffixLength) {
    }

    public record ResponsePrefixSuffix(byte[] prefixBytes, byte[] suffixBytes) {
    }
}
