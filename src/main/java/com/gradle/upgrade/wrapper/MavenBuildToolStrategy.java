package com.gradle.upgrade.wrapper;

import java.io.IOException;
import java.nio.file.Path;

public final class MavenBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String lookupLatestVersion() throws IOException {
        return null;
    }

    @Override
    public String extractCurrentVersion(Path rootProjectDir) throws IOException {
        return null;
    }

}
