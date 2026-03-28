package com.reajason.noone.test.compatibility;

import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

public class WebAppFile {
    public static final MountableFile war = MountableFile.forHostPath(
            Path.of("webapp", "vul-webapp.war").toAbsolutePath(), 0666);
    public static final MountableFile jakartaWar = MountableFile.forHostPath(
            Path.of("webapp", "vul-webapp-jakarta.war").toAbsolutePath(), 0666);

}
