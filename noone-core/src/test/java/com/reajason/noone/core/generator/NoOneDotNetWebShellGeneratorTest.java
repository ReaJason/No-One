package com.reajason.noone.core.generator;

import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import com.reajason.noone.core.profile.config.HttpResponseBodyType;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.*;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NoOneDotNetWebShellGeneratorTest {

    @Test
    void shouldGenerateAllDotNetWebShellVariants() {
        NoOneDotNetWebShellGenerator generator = new NoOneDotNetWebShellGenerator(new NoOneConfig(createProfile()));

        String aspx = generator.generateAspx();
        String ashx = generator.generateAshx();
        String asmx = generator.generateAsmx();
        String soap = generator.generateSoap();

        assertAll(
                () -> assertTrue(aspx.contains("<%@ Page Language=\"C#\" %>")),
                () -> assertTrue(aspx.contains("protected void Page_Load")),
                () -> assertTrue(ashx.contains("<%@ WebHandler Class=\"Handler\" Language=\"C#\" %>")),
                () -> assertTrue(ashx.contains("public class Handler : IHttpHandler")),
                () -> assertTrue(asmx.contains("<%@ WebService Language=\"C#\" Class=\"App\" %>")),
                () -> assertTrue(asmx.contains("[System.Web.Services.WebMethod]")),
                () -> assertTrue(soap.contains("<%@ WebService Language=\"C#\" Class=\"AppX.XService\" %>")),
                () -> assertTrue(soap.contains("public class XAttr : SoapExtensionAttribute"))
        );
    }

    @Test
    void shouldFillProtocolAndTransformationPlaceholders() {
        NoOneDotNetWebShellGenerator generator = new NoOneDotNetWebShellGenerator(new NoOneConfig(createProfile()));

        String content = generator.generateAshx();

        assertAll(
                () -> assertTrue(content.contains("Request.Headers[\"X-Test\"]")),
                () -> assertTrue(content.contains("Request.InputStream")),
                () -> assertTrue(content.contains("Response.AddHeader(\"X-Trace\", \"enabled\")")),
                () -> assertTrue(content.contains("<%@ Import Namespace=\"System.Security.Cryptography\" %>")),
                () -> assertTrue(content.contains("<%@ Import Namespace=\"System.IO.Compression\" %>")),
                () -> assertTrue(content.contains("private static void TryDispose")),
                () -> assertTrue(content.contains("private static void FillRandomBytes")),
                () -> assertTrue(content.contains("private static byte[] AesDecrypt")),
                () -> assertTrue(content.contains("private static byte[] GzipCompress")),
                () -> assertTrue(content.contains("private static byte[] DecodeBase64")),
                () -> assertTrue(content.contains("private static byte[] EncodeHex")),
                () -> assertFalse(content.contains("using (RandomNumberGenerator rng = RandomNumberGenerator.Create())")),
                () -> assertFalse(content.contains("using (Aes aes = Aes.Create())")),
                () -> assertFalse(content.contains("using (TripleDES des = TripleDES.Create())")),
                () -> assertFalse(content.contains("using (ICryptoTransform encryptor = algo.CreateEncryptor())")),
                () -> assertFalse(content.contains("using (ICryptoTransform decryptor = algo.CreateDecryptor())"))
        );
    }

    private static Profile createProfile() {
        Profile profile = new Profile();
        profile.setName("demo");
        profile.setPassword("secret");

        IdentifierConfig identifier = new IdentifierConfig();
        identifier.setLocation(IdentifierLocation.HEADER);
        identifier.setOperator(IdentifierOperator.EQUALS);
        identifier.setName("X-Test");
        identifier.setValue("demo");
        profile.setIdentifier(identifier);

        HttpProtocolConfig protocol = new HttpProtocolConfig();
        protocol.setRequestMethod("POST");
        protocol.setRequestBodyType(HttpRequestBodyType.TEXT);
        protocol.setRequestTemplate("prefix{{payload}}suffix");
        protocol.setResponseBodyType(HttpResponseBodyType.TEXT);
        protocol.setResponseTemplate("before{{payload}}after");
        protocol.setResponseStatusCode(202);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Trace", "enabled");
        protocol.setResponseHeaders(headers);
        profile.setProtocolConfig(protocol);

        profile.setRequestTransformations(List.of("Gzip", "AES", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "AES", "Hex"));
        return profile;
    }
}
