package com.reajason.noone.server;

import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.admin.user.UserSession;
import com.reajason.noone.server.admin.user.UserSessionRepository;
import com.reajason.noone.server.audit.AuditLogRepository;
import com.reajason.noone.server.plugin.PluginRepository;
import com.reajason.noone.server.profile.ProfileEntity;
import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.server.shell.Shell;
import com.reajason.noone.server.shell.ShellConnectionPool;
import com.reajason.noone.server.shell.ShellLanguage;
import com.reajason.noone.server.shell.ShellRepository;
import com.reajason.noone.server.shell.ShellStatus;
import com.reajason.noone.server.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class SQLiteIntegrationTest {

    @TempDir
    static Path databaseDirectory;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:"
                + databaseDirectory.resolve("integration-test.db").toAbsolutePath()
                + "?busy_timeout=5000&journal_mode=WAL&foreign_keys=true");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PluginRepository pluginRepository;

    @Autowired
    private ShellRepository shellRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private ShellConnectionPool shellConnectionPool;

    @Test
    void servesSpaIndexForClientSideRoutesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        for (String path : List.of("/auth/login", "/shells/42/files")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("\"isSpaMode\":true")));
        }
    }

    @Test
    void startsWithSqliteAndPersistsEntities() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("SQLite");
        }

        assertThat(userRepository.findByUsernameAndDeletedFalse("admin")).isPresent();
        assertThat(pluginRepository.count()).isPositive();
        assertThat(profileRepository.findAll())
                .singleElement()
                .satisfies(profile -> {
                    assertThat(profile.getIdentifier()).isNotNull();
                    assertThat(profile.getProtocolConfig()).isNotNull();
                    assertThat(profile.getRequestTransformations()).containsExactly("Gzip", "XOR", "Base64");
                    assertThat(profile.getResponseTransformations()).containsExactly("Gzip", "TripleDES", "Hex");
                });

        ProfileEntity profile = profileRepository.findAll().getFirst();
        Shell shell = new Shell();
        shell.setName("SQLite test shell");
        shell.setUrl("https://example.com/shell.jsp");
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setStatus(ShellStatus.DISCONNECTED);
        shell.setProfileId(profile.getId());
        Shell savedShell = shellRepository.save(shell);

        assertThat(savedShell.getId()).isNotNull();
        assertThat(shellRepository.findById(savedShell.getId())).isPresent();
    }

    @Test
    void servesConcurrentPageRequestsWithoutSqliteLockingErrors() throws Exception {
        User admin = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow();
        String sessionId = jwtUtil.newTokenId();
        userSessionRepository.save(UserSession.builder()
                .user(admin)
                .sessionId(sessionId)
                .refreshTokenHash("integration-test-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build());
        String accessToken = jwtUtil.generateAccessToken(
                "admin", "ROLE_ADMIN", sessionId, jwtUtil.newTokenId());
        List<String> pageEndpoints = List.of(
                "/api/shells",
                "/api/memshell/config",
                "/api/memshell/config/servers");
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(12)) {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                String endpoint = pageEndpoints.get(index % pageEndpoints.size());
                Callable<Integer> request = () -> {
                    start.await();
                    return mockMvc.perform(get(endpoint)
                                    .header("Authorization", "Bearer " + accessToken))
                            .andReturn().getResponse().getStatus();
                };
                responses.add(executor.submit(request));
            }
            start.countDown();

            for (Future<Integer> response : responses) {
                assertThat(response.get(15, TimeUnit.SECONDS)).isEqualTo(200);
            }
        }
    }

    @Test
    void auditedControllerWriteAlsoPersistsAuditLog() throws Exception {
        long auditCount = auditLogRepository.count();

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + createAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SQLite audit test\",\"code\":\"SQLITE-AUDIT-TEST\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(auditLogRepository.count()).isEqualTo(auditCount + 1);
    }

    @Test
    void shellDispatchPersistsStatusAndSystemInfoWithoutNestedWriteLock() throws Exception {
        Shell shell = new Shell();
        shell.setName("SQLite dispatch test");
        shell.setUrl("https://example.com/dispatch.jsp");
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setStatus(ShellStatus.DISCONNECTED);
        shell.setProfileId(profileRepository.findAll().getFirst().getId());
        Shell savedShell = shellRepository.save(shell);

        ShellConnection connection = mock(ShellConnection.class);
        when(connection.isPluginCacheInitialized()).thenReturn(true);
        when(shellConnectionPool.getOrCreateCached(any(Shell.class))).thenReturn(connection);
        when(connection.runPlugin(anyString(), any())).thenReturn(Map.of(
                "code", 0,
                "data", Map.of("os", Map.of("name", "Linux", "arch", "amd64"))));

        mockMvc.perform(post("/api/shells/{id}/dispatch", savedShell.getId())
                        .header("Authorization", "Bearer " + createAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginId\":\"system-info\",\"args\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Shell dispatchedShell = shellRepository.findById(savedShell.getId()).orElseThrow();
        assertThat(dispatchedShell.getStatus()).isEqualTo(ShellStatus.CONNECTED);
        assertThat(dispatchedShell.getOs()).isEqualTo("linux");
        assertThat(dispatchedShell.getArch()).isEqualTo("amd64");
    }

    @Test
    void requiredPasswordChangeEndpointReachesControllerWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/password/change-required")
                        .header("Password-Change-Token", createAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isForbidden());
    }

    private String createAccessToken() {
        User admin = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow();
        String sessionId = jwtUtil.newTokenId();
        userSessionRepository.save(UserSession.builder()
                .user(admin)
                .sessionId(sessionId)
                .refreshTokenHash("integration-test-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build());
        return jwtUtil.generateAccessToken(
                "admin", "ROLE_ADMIN", sessionId, jwtUtil.newTokenId());
    }
}
