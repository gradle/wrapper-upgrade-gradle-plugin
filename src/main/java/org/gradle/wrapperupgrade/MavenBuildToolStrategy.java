package org.gradle.wrapperupgrade;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import static org.gradle.wrapperupgrade.BuildToolStrategy.extractBuildToolVersion;

public final class MavenBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String buildToolName() {
        return "Maven";
    }

    @Override
    public VersionInfo lookupLatestVersion() throws IOException {
        var mapper = new XmlMapper();
        var mavenMetadata = mapper.readTree(new URL("https://repo.maven.apache.org/maven2/org/apache/maven/maven-core/maven-metadata.xml"));
        var version = mavenMetadata.get("versioning").get("release");
        if (version != null) {
            return new VersionInfo(version.asText(), null);
        } else {
            throw new IllegalStateException("Could not determine latest Maven version");
        }
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
    public void includeWrapperFiles(ConfigurableFileTree tree) {
        tree.include("**/.mvn/wrapper/**", "**/mvnw", "**/mvnw.cmd");
    }

    @Override
    public String releaseNotesLink(String buildToolVersion) {
        return "https://maven.apache.org/docs/$VERSION/release-notes.html".replace("$VERSION", buildToolVersion);
    }

}
