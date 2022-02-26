package com.gradle.upgrade.wrapper;

import java.io.IOException;

public interface BuildToolStrategy {

    BuildToolStrategy GRADLE = new GradleBuildToolStrategy();
    BuildToolStrategy MAVEN = new MavenBuildToolStrategy();

    String lookupLatestVersion() throws IOException;

}

