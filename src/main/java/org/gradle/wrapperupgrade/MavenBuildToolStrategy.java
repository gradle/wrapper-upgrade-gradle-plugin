package org.gradle.wrapperupgrade;

import org.gradle.process.ExecOperations;
import org.gradle.util.internal.VersionNumber;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.gradle.wrapperupgrade.BuildToolStrategy.extractBuildToolVersion;

public final class MavenBuildToolStrategy implements BuildToolStrategy {

    private final MavenMetadataFetcher mavenMetadataFetcher = new MavenMetadataFetcher();
    @Override
    public String buildToolName() {
        return "Maven";
    }

    @Override
    public VersionInfo lookupLatestVersion(boolean allowPreRelease) throws IOException {
        return mavenMetadataFetcher.fetchLatestVersion(allowPreRelease)
            .map(latestVersion -> new VersionInfo(latestVersion.toString(), null))
            .orElseThrow(() -> new IllegalStateException("Could not determine latest Maven version"));
    }

    @Override
    public VersionInfo extractCurrentVersion(Path rootProjectDir) throws IOException {
        return extractBuildToolVersion(rootProjectDir,
            ".mvn/wrapper/maven-wrapper.properties",
            "distributionUrl", null,
            "apache-maven-(.*)-bin.zip"
        );
    }

    @Override
    public void runWrapper(ExecOperations execOperations, Path rootProjectDir, VersionInfo version) {
        ExecUtils.execMavenCmd(execOperations, rootProjectDir, "-B", "-N", "wrapper:wrapper", "-Dmaven=" + version.version);
    }

    @Override
    public List<Path> wrapperFiles(Path rootProjectDir) {
        List<Path> paths = new LinkedList<>();
        paths.add(rootProjectDir.resolve("mvnw"));
        paths.add(rootProjectDir.resolve("mvnw.cmd"));
        paths.add(rootProjectDir.resolve(".mvn").resolve("wrapper"));
        return paths;
    }

    @Override
    public String releaseNotesLink(String buildToolVersion) {
        return "https://maven.apache.org/docs/$VERSION/release-notes.html".replace("$VERSION", buildToolVersion);
    }

}
