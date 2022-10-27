package org.gradle.wrapperupgrade;

import com.fasterxml.jackson.databind.JsonNode;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import static org.gradle.wrapperupgrade.BuildToolStrategy.extractBuildToolVersion;

public final class GradleBuildToolStrategy implements BuildToolStrategy {

    private final GradleMetadataFetcher gradleMetadataFetcher = new GradleMetadataFetcher();

    @Override
    public String buildToolName() {
        return "Gradle";
    }

    @Override
    public VersionInfo lookupLatestVersion(boolean allowPreRelease) throws IOException {
        JsonNode latestVersion = gradleMetadataFetcher.fetchLatestVersion(allowPreRelease)
            .orElseThrow(() -> new IllegalStateException("Could not determine latest Gradle version"));

        JsonNode checksumUrl = latestVersion.get("checksumUrl");
        if (checksumUrl != null) {
            URL url = new URL(checksumUrl.asText());
            String checksum = new Scanner(url.openStream()).useDelimiter("\\A").next(); //unhandled stream
            return new VersionInfo(latestVersion.get("version").asText(), checksum);
        } else {
            return new VersionInfo(latestVersion.get("version").asText(), null);
        }
    }

    @Override
    public VersionInfo extractCurrentVersion(Path rootProjectDir) throws IOException {
        return extractBuildToolVersion(rootProjectDir,
            "gradle/wrapper/gradle-wrapper.properties",
            "distributionUrl", "distributionSha256Sum",
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
    public List<Path> wrapperFiles(Path rootProjectDir) {
        List<Path> paths = new LinkedList<>();
        paths.add(rootProjectDir.resolve("gradlew"));
        paths.add(rootProjectDir.resolve("gradlew.bat"));
        paths.add(rootProjectDir.resolve("gradle").resolve("wrapper"));
        return paths;
    }

    @Override
    public String releaseNotesLink(String buildToolVersion) {
        return "https://docs.gradle.org/$VERSION/release-notes.html".replace("$VERSION", buildToolVersion);
    }

}
