package com.gradle.upgrade.wrapper;

import java.io.IOException;

public final class GradleBuildToolStrategy implements BuildToolStrategy {

    @Override
    public String lookupLatestVersion() throws IOException {
        return GradleUtils.lookupLatestGradleVersion();
    }

}
