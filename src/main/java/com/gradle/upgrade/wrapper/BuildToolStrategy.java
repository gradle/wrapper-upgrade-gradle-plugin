package com.gradle.upgrade.wrapper;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface BuildToolStrategy {

    BuildToolStrategy GRADLE = new GradleBuildToolStrategy();
    BuildToolStrategy MAVEN = new MavenBuildToolStrategy();

    String buildToolName();

    VersionInfo lookupLatestVersion() throws IOException;

    String extractCurrentVersion(Path rootProjectDir) throws IOException;

    void runWrapper(ExecOperations execOperations, Path rootProjectDir, VersionInfo version);

    void includeWrapperFiles(ConfigurableFileTree tree);

    final class VersionInfo {

        public String version;
        public Optional<String> checksum;

        public VersionInfo(String version, @Nullable String checksum) {
            this.version = version;
            this.checksum = Optional.ofNullable(checksum);
        }

    }

}

