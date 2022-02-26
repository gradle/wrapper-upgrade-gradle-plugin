package com.gradle.upgrade.wrapper;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.nio.file.Path;

public final class MavenBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String buildToolName() {
        return "Maven";
    }

    @Override
    public String lookupLatestVersion() throws IOException {
        return null;
    }

    @Override
    public String extractCurrentVersion(Path rootProjectDir) throws IOException {
        return null;
    }

    @Override
    public void runWrapper(ExecOperations execOperations, Path rootProjectDir, String version) {
    }

    @Override
    public void includeWrapperFiles(ConfigurableFileTree tree) {
    }

}
