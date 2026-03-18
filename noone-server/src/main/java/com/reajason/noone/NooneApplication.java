package com.reajason.noone;

import tools.jackson.databind.ObjectMapper;
import com.reajason.noone.server.admin.auth.TwoFactorAuthService;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.plugin.PluginService;
import com.reajason.noone.server.plugin.dto.PluginCreateRequest;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.admin.user.UserStatus;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.server.profile.config.*;
import com.reajason.noone.server.project.Project;
import com.reajason.noone.server.project.ProjectRepository;
import com.reajason.noone.server.project.ProjectStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.reajason.noone.server.config.JwtConfig;
import com.reajason.noone.server.plugin.registry.PluginRegistryProperties;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableJpaAuditing
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@EnableConfigurationProperties({JwtConfig.class, PluginRegistryProperties.class})
public class NooneApplication {

    public static void main(String[] args) {
        SpringApplication.run(NooneApplication.class, args);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * @author ReaJason
     * @since 2025/9/9
     */
    @Component
    @org.springframework.context.annotation.Profile("!test")
    @Slf4j
    @RequiredArgsConstructor
    public static class DataInitializer implements CommandLineRunner {
        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final ProjectRepository projectRepository;
        private final PermissionRepository permissionRepository;
        private final PasswordEncoder passwordEncoder;
        private final ProfileRepository profileRepository;
        private final TwoFactorAuthService twoFactorAuthService;
        private final PluginService pluginService;
        private final ObjectMapper objectMapper;

        @Override
        public void run(String... args) {
            initializeDefaultPermissions();
            initializeDefaultRoles();
            initializePlugins();
            initializeAdminAccount();
            initializeProject();
            initializeProfile();
        }

        private void initializeAdminAccount() {
            if (userRepository.findByUsernameAndDeletedFalse("admin").isPresent()) {
                return;
            }

            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setRoles(new HashSet<>(List.of(findRoleByName("Super Admin"))));
            String randomPassword = UUID.randomUUID().toString().substring(0, 16);
            adminUser.setPassword(passwordEncoder.encode(randomPassword));
            adminUser.setEmail("admin@123.com");
            adminUser.setStatus(UserStatus.UNACTIVATED);
            adminUser.setMustChangePassword(true);
            adminUser.setMfaSecret(twoFactorAuthService.generateSecret());
            userRepository.save(adminUser);
            printSummary(adminUser.getUsername(), randomPassword);
        }

        private void initializeDefaultRoles() {
            Map<String, Permission> permissionsByCode = permissionRepository.findAll().stream()
                    .collect(Collectors.toMap(Permission::getCode, permission -> permission));
            Set<Permission> allPermissions = new HashSet<>(permissionsByCode.values());

            seedRole("Super Admin", allPermissions);
            seedRole("Admin", selectPermissions(permissionsByCode,
                    "user:create", "user:read", "user:update", "user:delete", "user:list",
                    "role:create", "role:read", "role:update", "role:delete", "role:list",
                    "permission:create", "permission:read", "permission:update", "permission:delete", "permission:list",
                    "profile:create", "profile:read", "profile:update", "profile:delete", "profile:list",
                    "plugin:create", "plugin:list",
                    "auth:log:read", "auth:session:manage"));
            seedRole("System Auditor", selectPermissions(permissionsByCode,
                    "auth:log:read",
                    "user:read", "user:list",
                    "role:read", "role:list",
                    "permission:read", "permission:list",
                    "profile:read", "profile:list",
                    "plugin:list"));
            seedRole("Team Leader", selectPermissions(permissionsByCode,
                    "project:create", "project:list", "project:update", "project:delete", "project:member:manage",
                    "shell:create", "shell:list", "shell:update", "shell:delete", "shell:test",
                    "shell:dispatch", "shell:operation:read", "shell:generate",
                    "plugin:list"));
            seedRole("Team Operator", selectPermissions(permissionsByCode,
                    "project:list",
                    "shell:create", "shell:list", "shell:update", "shell:delete", "shell:test",
                    "shell:dispatch", "shell:operation:read", "shell:generate",
                    "plugin:list"));
            seedRole("Guest", selectPermissions(permissionsByCode,
                    "project:list", "shell:list", "shell:operation:read"));
        }

        private void initializePlugins() {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            try {
                Resource[] resources = resolver.getResources("classpath*:plugins/*.json");
                Arrays.sort(resources, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));
                for (Resource resource : resources) {
                    try (var inputStream = resource.getInputStream()) {
                        PluginCreateRequest request = objectMapper.readValue(inputStream, PluginCreateRequest.class);
                        pluginService.create(request);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to import plugins from noone-server resources", e);
            }
        }

        private void initializeDefaultPermissions() {
            List<Permission> defaultPermissions = Arrays.asList(
                    createPermission("user:create", "CreateUser"),
                    createPermission("user:read", "ReadUser"),
                    createPermission("user:update", "UpdateUser"),
                    createPermission("user:delete", "DeleteUser"),
                    createPermission("user:list", "ListUser"),

                    createPermission("role:create", "CreateRole"),
                    createPermission("role:read", "ReadRole"),
                    createPermission("role:update", "UpdateRole"),
                    createPermission("role:delete", "DeleteRole"),
                    createPermission("role:list", "ListRole"),

                    createPermission("permission:create", "CreatePermission"),
                    createPermission("permission:read", "ReadPermission"),
                    createPermission("permission:update", "UpdatePermission"),
                    createPermission("permission:delete", "DeletePermission"),
                    createPermission("permission:list", "ListPermission"),

                    createPermission("profile:create", "CreateProfile"),
                    createPermission("profile:read", "ReadProfile"),
                    createPermission("profile:update", "UpdateProfile"),
                    createPermission("profile:delete", "DeleteProfile"),
                    createPermission("profile:list", "ListProfile"),

                    createPermission("plugin:create", "CreatePlugin"),
                    createPermission("plugin:list", "ListPlugin"),

                    createPermission("auth:log:read", "ReadAuth"),
                    createPermission("auth:session:manage", "ManageAuth"),

                    createPermission("project:create", "CreateProject"),
                    createPermission("project:list", "ReadProject"),
                    createPermission("project:update", "UpdateProject"),
                    createPermission("project:delete", "DeleteProject"),
                    createPermission("project:member:manage", "ManageProjectMember"),
                    createPermission("shell:create", "CreateShell"),
                    createPermission("shell:list", "ReadShell"),
                    createPermission("shell:update", "UpdateShell"),
                    createPermission("shell:delete", "DeleteShell"),
                    createPermission("shell:test", "TestShell"),
                    createPermission("shell:dispatch", "DispatchShell"),
                    createPermission("shell:operation:read", "ReadShellOperation"),
                    createPermission("shell:generate", "GenerateShell")
            );

            Map<String, Permission> existing = permissionRepository.findAll().stream()
                    .collect(Collectors.toMap(Permission::getCode, permission -> permission));
            for (Permission permission : defaultPermissions) {
                Permission existingPermission = existing.get(permission.getCode());
                if (existingPermission == null) {
                    permissionRepository.save(permission);
                    continue;
                }
                existingPermission.setName(permission.getName());
                permissionRepository.save(existingPermission);
            }
        }

        private Permission createPermission(String code, String name) {
            return Permission.builder()
                    .code(code)
                    .name(name)
                    .build();
        }

        private void seedRole(String name, Set<Permission> permissions) {
            Role role = roleRepository.findAll().stream()
                    .filter(existingRole -> name.equalsIgnoreCase(existingRole.getName()))
                    .findFirst()
                    .orElseGet(Role::new);
            role.setName(name);
            role.setPermissions(new HashSet<>(permissions));
            roleRepository.save(role);
        }

        private Set<Permission> selectPermissions(Map<String, Permission> permissionsByCode, String... codes) {
            Set<String> requiredCodes = Set.of(codes);
            Set<Permission> permissions = requiredCodes.stream()
                    .map(code -> {
                        Permission permission = permissionsByCode.get(code);
                        if (permission == null) {
                            throw new IllegalStateException("Missing default permission: " + code);
                        }
                        return permission;
                    })
                    .collect(Collectors.toSet());
            if (permissions.size() != requiredCodes.size()) {
                throw new IllegalStateException("Permission selection is incomplete");
            }
            return permissions;
        }

        private Role findRoleByName(String name) {
            return roleRepository.findAll().stream()
                    .filter(role -> name.equalsIgnoreCase(role.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing role: " + name));
        }


        private void initializeProject() {
            if (projectRepository.count() > 0) {
                return;
            }

            Project project = new Project();
            project.setName("Sandbox");
            project.setCode("SANDBOX");
            project.setStatus(ProjectStatus.ACTIVE);
            project.setBizName("localhost");
            project.setDescription("System default project for payload generation, link testing, and safe experimentation. Do not use for real client engagements.");
            project.setStartedAt(LocalDateTime.now());
            project.setRemark("Auto-generated during system initialization.");
            User admin = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow();
            project.setMembers(Collections.singleton(admin));
            projectRepository.save(project);
        }

        private void initializeProfile() {
            if (profileRepository.count() > 0) {
                return;
            }
            Profile profile = new Profile();
            profile.setProtocolType(ProtocolType.HTTP);
            profile.setName("RuoYi（JSON）");
            profile.setPassword("secret");
            IdentifierConfig identifierConfig = new IdentifierConfig();
            identifierConfig.setLocation(IdentifierLocation.HEADER);
            identifierConfig.setOperator(IdentifierOperator.CONTAINS);
            identifierConfig.setName("No-One-Version");
            identifierConfig.setValue("V1");
            profile.setIdentifier(identifierConfig);
            HttpProtocolConfig httpProtocolConfig = new HttpProtocolConfig();
            httpProtocolConfig.setRequestBodyType(HttpRequestBodyType.JSON);
            httpProtocolConfig.setResponseBodyType(HttpResponseBodyType.JSON);
            httpProtocolConfig.setRequestTemplate("{\"signature\": \"{{payload}}\", \"version\": \"v1\"}");
            httpProtocolConfig.setResponseTemplate("{\"resData\": \"{{payload}}\", \"test\": \"123\"}");
            profile.setProtocolConfig(httpProtocolConfig);
            profile.setRequestTransformations(List.of("Gzip", "XOR", "Base64"));
            profile.setResponseTransformations(List.of("Gzip", "TripleDES", "Hex"));
            profileRepository.save(profile);
        }

        private void printSummary(String username, String password) {
            System.out.println();
            System.out.println(ConsoleColors.YELLOW_BOLD + "====================== INITIALIZATION COMPLETE ======================" + ConsoleColors.RESET);
            System.out.println(ConsoleColors.WHITE_BOLD + "  Red Team Platform has been successfully initialized." + ConsoleColors.RESET);
            System.out.println();
            System.out.println(ConsoleColors.WHITE + "  Default Administrator Credentials:" + ConsoleColors.RESET);
            System.out.println(ConsoleColors.WHITE + "  ------------------------------------" + ConsoleColors.RESET);
            System.out.println(ConsoleColors.WHITE + "  Username: " + ConsoleColors.BLUE_BOLD + username + ConsoleColors.RESET);
            System.out.println(ConsoleColors.WHITE + "  Password: " + ConsoleColors.BLUE_BOLD + password + ConsoleColors.RESET);
            System.out.println();
            System.out.println(ConsoleColors.RED + "  IMPORTANT: Please change this password after your first login!" + ConsoleColors.RESET);
            System.out.println(ConsoleColors.YELLOW_BOLD + "=====================================================================" + ConsoleColors.RESET);
            System.out.println();
        }

        static class ConsoleColors {
            public static final String RESET = "\033[0m";
            public static final String BLACK = "\033[0;30m";
            public static final String RED = "\033[0;31m";
            public static final String GREEN = "\033[0;32m";
            public static final String YELLOW = "\033[0;33m";
            public static final String BLUE = "\033[0;34m";
            public static final String PURPLE = "\033[0;35m";
            public static final String CYAN = "\033[0;36m";
            public static final String WHITE = "\033[0;37m";
            public static final String YELLOW_BOLD = "\033[1;33m";
            public static final String BLUE_BOLD = "\033[1;34m";
            public static final String WHITE_BOLD = "\033[1;37m";
        }
    }
}
