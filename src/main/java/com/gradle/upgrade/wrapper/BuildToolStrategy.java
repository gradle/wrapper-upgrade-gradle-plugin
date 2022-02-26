package com.gradle.upgrade.wrapper;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.nio.file.Path;

public interface BuildToolStrategy {

    BuildToolStrategy GRADLE = new GradleBuildToolStrategy();
    BuildToolStrategy MAVEN = new MavenBuildToolStrategy();

    String buildToolName();

    String lookupLatestVersion() throws IOException;

    String extractCurrentVersion(Path rootProjectDir) throws IOException;

    void runWrapper(ExecOperations execOperations, Path rootProjectDir, String version);

    void includeWrapperFiles(ConfigurableFileTree tree);

}

