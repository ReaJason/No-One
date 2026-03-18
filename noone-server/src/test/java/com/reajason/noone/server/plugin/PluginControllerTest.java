package com.reajason.noone.server.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.plugin.dto.PluginUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser
class PluginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PluginRepository pluginRepository;

    @Test
    void shouldGetPluginById() throws Exception {
        Plugin plugin = pluginRepository.save(buildPlugin("test-plugin", "test-id", "java"));

        mockMvc.perform(get("/api/plugins/{id}", plugin.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.name").value("test-plugin"))
                .andExpect(jsonPath("$.language").value("java"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.type").value("webshell"))
                .andExpect(jsonPath("$.source").value("BUILTIN"));
    }

    @Test
    void shouldReturn404WhenPluginNotFound() throws Exception {
        mockMvc.perform(get("/api/plugins/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Plugin not found: 99999"));
    }

    @Test
    void shouldUpdatePlugin() throws Exception {
        Plugin plugin = pluginRepository.save(buildPlugin("old-name", "update-id", "java"));

        PluginUpdateRequest request = new PluginUpdateRequest();
        request.setName("new-name");
        request.setDescription("updated description");
        request.setAuthor("new-author");

        mockMvc.perform(put("/api/plugins/{id}", plugin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new-name"))
                .andExpect(jsonPath("$.description").value("updated description"))
                .andExpect(jsonPath("$.author").value("new-author"));

        Plugin updated = pluginRepository.findById(plugin.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("new-name");
        assertThat(updated.getDescription()).isEqualTo("updated description");
        assertThat(updated.getAuthor()).isEqualTo("new-author");
    }

    @Test
    void shouldDeletePlugin() throws Exception {
        Plugin plugin = pluginRepository.save(buildPlugin("to-delete", "delete-id", "java"));

        mockMvc.perform(delete("/api/plugins/{id}", plugin.getId()))
                .andExpect(status().isNoContent());

        assertThat(pluginRepository.findById(plugin.getId())).isEmpty();
    }

    @Test
    void shouldUploadPluginFile() throws Exception {
        String pluginJson = objectMapper.writeValueAsString(Map.of(
                "id", "uploaded-plugin",
                "name", "Uploaded Plugin",
                "version", "2.0.0",
                "language", "java",
                "type", "webshell",
                "description", "An uploaded plugin",
                "author", "uploader",
                "meta", Map.of("classNames", List.of("com.example.MyPlugin"))
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "plugin.json", MediaType.APPLICATION_JSON_VALUE, pluginJson.getBytes());

        mockMvc.perform(multipart("/api/plugins/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("uploaded-plugin"))
                .andExpect(jsonPath("$.name").value("Uploaded Plugin"))
                .andExpect(jsonPath("$.version").value("2.0.0"))
                .andExpect(jsonPath("$.language").value("java"))
                .andExpect(jsonPath("$.source").value("UPLOADED"));
    }

    private Plugin buildPlugin(String name, String pluginId, String language) {
        return Plugin.builder()
                .name(name)
                .pluginId(pluginId)
                .version("1.0.0")
                .language(language)
                .type("webshell")
                .source(PluginSource.BUILTIN)
                .description("A test plugin")
                .author("tester")
                .build();
    }
}
