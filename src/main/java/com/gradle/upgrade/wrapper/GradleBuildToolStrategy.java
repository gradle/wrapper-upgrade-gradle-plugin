package com.gradle.upgrade.wrapper;

import java.io.IOException;
import java.nio.file.Path;

public final class GradleBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String lookupLatestVersion() throws IOException {
        return GradleUtils.lookupLatestGradleVersion();
    }

    @Override
    public String extractCurrentVersion(Path rootProjectDir) throws IOException {
        return GradleUtils.extractCurrentGradleVersion(rootProjectDir);
    }

}
