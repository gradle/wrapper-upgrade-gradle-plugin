package com.gradle.upgrade.wrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

final class GradleUtils {

    private static final String GRADLE_VERSION_REGEXP = "distributions/gradle-(.*)-(bin|all).zip";
    private static final String DISTRIBUTION_URL = "distributionUrl";

    static Optional<String> getCurrentGradleVersion(Path workingDir) throws IOException {
        try (var is = Files.newInputStream(workingDir.resolve("gradle/wrapper/gradle-wrapper.properties"))) {
            var props = new Properties();
            props.load(is);
            var distributionUrl = props.getProperty(DISTRIBUTION_URL);
            if (distributionUrl != null) {
                var matcher = Pattern.compile(GRADLE_VERSION_REGEXP).matcher(distributionUrl);
                if (matcher.find()) {
                    return Optional.of(matcher.group(1));
                }
            }
            throw new IOException("Could not detect Gradle version from " + DISTRIBUTION_URL + " property");
        }
    }

    private GradleUtils() {
    }

}
