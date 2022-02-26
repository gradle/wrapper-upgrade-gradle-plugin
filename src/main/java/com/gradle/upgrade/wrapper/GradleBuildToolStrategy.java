package com.gradle.upgrade.wrapper;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.nio.file.Path;

public final class GradleBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String buildToolName() {
        return "Gradle";
    }

    @Override
    public String lookupLatestVersion() throws IOException {
        return GradleUtils.lookupLatestGradleVersion();
    }

    @Override
    public String extractCurrentVersion(Path rootProjectDir) throws IOException {
        return GradleUtils.extractCurrentGradleVersion(rootProjectDir);
    }

    @Override
    public void runWrapper(ExecOperations execOperations, Path rootProjectDir, String version) {
        ExecUtils.execGradleCmd(execOperations, rootProjectDir, "wrapper", "--gradle-version", version);
    }

    @Override
    public void includeWrapperFiles(ConfigurableFileTree tree) {
        tree.include("**/gradle/wrapper/**", "**/gradlew", "**/gradlew.bat");
    }

}
