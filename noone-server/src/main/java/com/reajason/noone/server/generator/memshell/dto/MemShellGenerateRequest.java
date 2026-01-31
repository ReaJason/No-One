package com.reajason.noone.server.generator.memshell.dto;

import com.reajason.javaweb.memshell.config.*;
import com.reajason.javaweb.packer.Packers;
import com.reajason.noone.Constants;
import lombok.Data;

import static com.reajason.javaweb.memshell.ShellTool.*;

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

    @Data
    public static class ShellToolConfigDTO {
        private Long profileId;
    }
}