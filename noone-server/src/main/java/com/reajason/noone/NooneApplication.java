package com.reajason.noone;

import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.server.profile.config.ProtocolType;
import com.reajason.noone.server.project.Project;
import com.reajason.noone.server.project.ProjectRepository;
import com.reajason.noone.server.project.ProjectStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableJpaAuditing
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class NooneApplication {

    public static void main(String[] args) {
        SpringApplication.run(NooneApplication.class, args);
    }

    /**
     * @author ReaJason
     * @since 2025/9/9
     */
    @Component
    @Slf4j
    @RequiredArgsConstructor
    public static class DataInitializer implements CommandLineRunner {
        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final ProjectRepository projectRepository;
        private final PermissionRepository permissionRepository;
        private final PasswordEncoder passwordEncoder;
        private final ProfileRepository profileRepository;

        @Override
        public void run(String... args) {
            initializeDefaultPermissions();
            initializeAdminRole();
            initializeAdminAccount();
            initializeProject();
            initializeProfile();
        }

        private void initializeAdminAccount() {
            if (userRepository.count() > 0) {
                log.info("Database already contains data. Skipping initialization.");
                return;
            }

            log.info("Creating default administrator user...");
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setRoles(new HashSet<>(roleRepository.findAll()));
            String randomPassword = UUID.randomUUID().toString().substring(0, 16);
            adminUser.setPassword(passwordEncoder.encode(randomPassword));
            adminUser.setEnabled(true);
            userRepository.save(adminUser);
            printSummary(adminUser.getUsername(), randomPassword);
        }

        private void initializeAdminRole() {
            if (roleRepository.count() > 0) {
                return;
            }
            Role role = new Role();
            role.setPermissions(new HashSet<>(permissionRepository.findAll()));
            role.setName("admin");
            roleRepository.save(role);

            Role role1 = new Role();
            role1.setName("user");
            role1.setPermissions(permissionRepository.findAll()
                    .stream()
                    .filter(p -> p.getCode().endsWith("list"))
                    .collect(Collectors.toSet()));
            roleRepository.save(role1);
        }

        private void initializeDefaultPermissions() {
            if (permissionRepository.count() > 0) {
                log.info("Permissions already exist, skipping initialization");
                return;
            }

            log.info("Initializing default permissions...");

            List<Permission> defaultPermissions = Arrays.asList(
                    // 用户管理权限
                    createPermission("user:create", "创建用户"),
                    createPermission("user:read", "查看用户"),
                    createPermission("user:update", "更新用户"),
                    createPermission("user:delete", "删除用户"),
                    createPermission("user:list", "用户列表"),

                    // 角色管理权限
                    createPermission("role:create", "创建角色"),
                    createPermission("role:read", "查看角色"),
                    createPermission("role:update", "更新角色"),
                    createPermission("role:delete", "删除角色"),
                    createPermission("role:list", "角色列表"),

                    // 权限管理权限
                    createPermission("permission:create", "创建权限"),
                    createPermission("permission:read", "查看权限"),
                    createPermission("permission:update", "更新权限"),
                    createPermission("permission:delete", "删除权限"),
                    createPermission("permission:list", "权限列表"),

                    // 项目管理权限
                    createPermission("project:create", "创建项目"),
                    createPermission("project:read", "查看项目"),
                    createPermission("project:update", "更新项目"),
                    createPermission("project:delete", "删除项目"),
                    createPermission("project:list", "项目列表")
            );

            permissionRepository.saveAll(defaultPermissions);
            log.info("Initialized {} default permissions", defaultPermissions.size());
        }

        private Permission createPermission(String code, String name) {
            return Permission.builder()
                    .code(code)
                    .name(name)
                    .build();
        }


        private void initializeProject() {
            if (projectRepository.count() > 0) {
                return;
            }

            Project project = new Project();
            project.setName("Local Vul Test");
            project.setCode("localVulTest");
            project.setStatus(ProjectStatus.ACTIVE);
            project.setMembers(Collections.singleton(userRepository.findByUsername("admin").get()));
            projectRepository.save(project);
        }

        private void initializeProfile() {
            if (profileRepository.count() > 0) {
                return;
            }
            Profile profile = new Profile();
            profile.setProtocolType(ProtocolType.HTTP);
            profile.setName("RuoYi");
            profile.setPassword("noone");
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
