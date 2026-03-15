package com.reajason.noone.server.generator.memshell.dto;

import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.packer.Packers;
import lombok.Data;

/**
 * @author ReaJason
 * @since 2024/12/18
 */
@Data
public class MemShellGenerateRequest {
    private ShellConfig shellConfig;
    private ShellToolConfigDTO shellToolConfig;
    private InjectorConfig injectorConfig;
    private Packers packer;
    private PackerRequestSpecDTO packerSpec;

    @Data
    public static class ShellToolConfigDTO {
        private String shellClassName;
        private Long coreProfileId;
        private Long loaderProfileId;
        private Boolean staging;
    }
}