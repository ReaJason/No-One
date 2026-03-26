package com.reajason.noone.server.generator.webshell;

import com.reajason.noone.server.generator.webshell.dto.WebShellGenerateRequest;
import com.reajason.noone.server.generator.webshell.dto.WebShellGenerateResponse;
import com.reajason.noone.server.profile.ProfileEntity;
import com.reajason.noone.server.profile.ProfileMapperImpl;
import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.core.profile.config.HttpProtocolConfig;
import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import com.reajason.noone.core.profile.config.HttpResponseBodyType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebShellGeneratorControllerTest {

    private final ProfileMapperImpl profileMapper = new ProfileMapperImpl();

    @Test
    void shouldRejectInvalidLanguageFormatCombination() {

        ProfileRepository repository = mock(ProfileRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(createProfile()));
        WebShellGeneratorController controller = new WebShellGeneratorController(repository, profileMapper);

        WebShellGenerateRequest request = new WebShellGenerateRequest();
        request.setProfileId(1L);
        request.setLanguage("java");
        request.setFormat("ASMX");

        assertThrows(IllegalArgumentException.class, () -> controller.generate(request));
    }

    @Test
    void shouldGenerateSoapWithSoapExtension() {
        ProfileRepository repository = mock(ProfileRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(createProfile()));
        WebShellGeneratorController controller = new WebShellGeneratorController(repository, profileMapper);

        WebShellGenerateRequest request = new WebShellGenerateRequest();
        request.setProfileId(1L);
        request.setLanguage("dotnet");
        request.setFormat("SOAP");

        WebShellGenerateResponse response = controller.generate(request);

        assertEquals("SOAP", response.getFormat());
        assertEquals("shell.soap", response.getFileName());
        assertTrue(response.getContent().contains("SoapExtensionAttribute"));
    }

    private static ProfileEntity createProfile() {
        ProfileEntity profile = new ProfileEntity();
        profile.setName("demo");
        profile.setPassword("secret");

        HttpProtocolConfig protocol = new HttpProtocolConfig();
        protocol.setRequestMethod("POST");
        protocol.setRequestBodyType(HttpRequestBodyType.TEXT);
        protocol.setRequestTemplate("{{payload}}");
        protocol.setResponseBodyType(HttpResponseBodyType.TEXT);
        protocol.setResponseTemplate("{{payload}}");
        protocol.setResponseStatusCode(200);
        profile.setProtocolConfig(protocol);
        return profile;
    }
}
