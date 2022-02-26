package com.gradle.upgrade.wrapper;

import java.io.IOException;
import java.nio.file.Path;

public interface BuildToolStrategy {

    BuildToolStrategy GRADLE = new GradleBuildToolStrategy();
    BuildToolStrategy MAVEN = new MavenBuildToolStrategy();

    String lookupLatestVersion() throws IOException;

    String extractCurrentVersion(Path rootProjectDir) throws IOException;

}

