package com.gradle.upgrade.wrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Scanner;

public final class GradleBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String buildToolName() {
        return "Gradle";
    }

    @Override
    public VersionInfo lookupLatestVersion() throws IOException {
        var mapper = new ObjectMapper();
        var gradleMetadata = mapper.readTree(new URL("https://services.gradle.org/versions/current"));
        var version = gradleMetadata.get("version");
        if (version != null) {
            var wrapperChecksumUrl = gradleMetadata.get("wrapperChecksumUrl");
            if (wrapperChecksumUrl != null) {
                URL url = new URL(wrapperChecksumUrl.asText());
                String wrapperChecksum = new Scanner(url.openStream()).useDelimiter("\\A").next();
                return new VersionInfo(version.asText(), wrapperChecksum);
            } else {
                return new VersionInfo(version.asText(), null);
            }
        } else {
            throw new IllegalStateException("Could not determine latest Gradle version");
        }
    }

    @Override
    public String extractCurrentVersion(Path rootProjectDir) throws IOException {
        return WrapperUtils.extractBuildToolVersion(rootProjectDir,
            "gradle/wrapper/gradle-wrapper.properties",
            "distributionUrl",
            "distributions/gradle-(.*)-(bin|all).zip"
        );
    }

    @Override
    public void runWrapper(ExecOperations execOperations, Path rootProjectDir, VersionInfo version) {
        if (version.checksum.isPresent()) {
            ExecUtils.execGradleCmd(execOperations, rootProjectDir, "--console=plain", "wrapper", "--gradle-version", version.version,
                "--gradle-distribution-sha256-sum", version.checksum.get());
        } else {
            ExecUtils.execGradleCmd(execOperations, rootProjectDir, "--console=plain", "wrapper", "--gradle-version", version.version);
        }
    }

    @Override
    public void includeWrapperFiles(ConfigurableFileTree tree) {
        tree.include("**/gradle/wrapper/**", "**/gradlew", "**/gradlew.bat");
    }

}
