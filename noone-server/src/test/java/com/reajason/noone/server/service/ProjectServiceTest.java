package com.reajason.noone.server.service;

import com.reajason.noone.NooneApplication;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.project.Project;
import com.reajason.noone.server.project.ProjectRepository;
import com.reajason.noone.server.project.ProjectService;
import com.reajason.noone.server.project.dto.ProjectCreateRequest;
import com.reajason.noone.server.project.dto.ProjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = NooneApplication.class)
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
@Transactional
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() {
        projectRepository.deleteAll();
    }

    @Test
    void createProject_WithArchivedStatus_ShouldPopulateArchivedAt() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setName("Archived Project");
        request.setCode("ARCHIVED-PROJECT");
        request.setStatus("ARCHIVED");

        ProjectResponse response = projectService.create(request);

        assertThat(response.getStatus()).isEqualTo("ARCHIVED");
        assertThat(response.getArchivedAt()).isNotNull();
    }

    @Test
    void deleteProject_ShouldSoftDeleteAndHideRecord() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setName("Soft Delete Project");
        request.setCode("SOFT-DELETE-PROJECT");

        ProjectResponse response = projectService.create(request);

        projectService.delete(response.getId());

        Project deletedProject = projectRepository.findById(response.getId()).orElseThrow();
        assertThat(deletedProject.getDeleted()).isTrue();
        assertThatThrownBy(() -> projectService.getById(response.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    void createProject_AfterSoftDelete_ShouldAllowReusingNameAndCode() {
        ProjectCreateRequest original = new ProjectCreateRequest();
        original.setName("Reusable Project");
        original.setCode("REUSABLE-PROJECT");

        ProjectResponse originalResponse = projectService.create(original);
        projectService.delete(originalResponse.getId());

        ProjectCreateRequest replacement = new ProjectCreateRequest();
        replacement.setName("Reusable Project");
        replacement.setCode("REUSABLE-PROJECT");

        ProjectResponse replacementResponse = projectService.create(replacement);

        assertThat(replacementResponse.getId()).isNotEqualTo(originalResponse.getId());
        assertThat(replacementResponse.getCode()).isEqualTo("REUSABLE-PROJECT");
        assertThat(replacementResponse.getName()).isEqualTo("Reusable Project");
    }
}
