package org.gradle.wrapperupgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Scanner;

import static org.gradle.wrapperupgrade.BuildToolStrategy.extractBuildToolVersion;

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
            var checksumUrl = gradleMetadata.get("checksumUrl");
            if (checksumUrl != null) {
                URL url = new URL(checksumUrl.asText());
                String checksum = new Scanner(url.openStream()).useDelimiter("\\A").next();
                return new VersionInfo(version.asText(), checksum);
            } else {
                return new VersionInfo(version.asText(), null);
            }
        } else {
            throw new IllegalStateException("Could not determine latest Gradle version");
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
    public void includeWrapperFiles(ConfigurableFileTree tree) {
        tree.include("**/gradle/wrapper/**", "**/gradlew", "**/gradlew.bat");
    }

    @Override
    public String releaseNotesLink(String buildToolVersion) {
        return "https://docs.gradle.org/$VERSION/release-notes.html".replace("$VERSION", buildToolVersion);
    }

}
