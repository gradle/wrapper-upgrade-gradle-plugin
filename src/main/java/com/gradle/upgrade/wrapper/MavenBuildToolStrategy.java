package com.gradle.upgrade.wrapper;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

public final class MavenBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String buildToolName() {
        return "Maven";
    }

    @Override
    public String lookupLatestVersion() throws IOException {
        var mapper = new XmlMapper();
        var mavenMetadata = mapper.readTree(new URL("https://repo.maven.apache.org/maven2/org/apache/maven/maven-core/maven-metadata.xml"));
        var version = mavenMetadata.get("versioning").get("release");
        if (version != null) {
            return version.asText();
        } else {
            throw new IllegalStateException("Could not determine latest Maven version");
        }
    }

    @Override
    public String extractCurrentVersion(Path rootProjectDir) throws IOException {
        return WrapperUtils.extractBuildToolVersion(rootProjectDir,
            ".mvn/wrapper/maven-wrapper.properties",
            "distributionUrl",
            "apache-maven-(.*)-bin.zip"
        );
    }

    @Override
    public void runWrapper(ExecOperations execOperations, Path rootProjectDir, String version) {
        ExecUtils.execMavenCmd(execOperations, rootProjectDir, "-B", "-N", "wrapper:wrapper", "-Dmaven=" + version);
    }

    @Override
    public void includeWrapperFiles(ConfigurableFileTree tree) {
        tree.include("**/.mvn/wrapper/**", "**/mvnw", "**/mvnw.cmd");
    }

}
