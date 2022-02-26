package com.gradle.upgrade.wrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

public final class GradleBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String buildToolName() {
        return "Gradle";
    }

    @Override
    public String lookupLatestVersion() throws IOException {
        var mapper = new ObjectMapper();
        var version = mapper.readTree(new URL("https://services.gradle.org/versions/current")).get("version");
        if (version != null) {
            return version.asText();
        } else {
            throw new IllegalStateException("Could not determine latest Gradle version");
        }
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
