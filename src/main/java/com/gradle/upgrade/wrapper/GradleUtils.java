package com.gradle.upgrade.wrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

final class GradleUtils {

    private static final String GRADLE_WRAPPER_PROPERTIES_FILE = "gradle/wrapper/gradle-wrapper.properties";
    private static final String GRADLE_WRAPPER_DISTRIBUTION_URL_PROP = "distributionUrl";
    private static final String GRADLE_WRAPPER_VERSION_REGEXP = "distributions/gradle-(.*)-(bin|all).zip";

    static Optional<String> getCurrentGradleVersion(Path workingDir) throws IOException {
        try (var is = Files.newInputStream(workingDir.resolve(GRADLE_WRAPPER_PROPERTIES_FILE))) {
            var wrapperProperties = new Properties();
            wrapperProperties.load(is);
            return getCurrentGradleVersion(wrapperProperties);
        }
    }

    private static Optional<String> getCurrentGradleVersion(Properties props) {
        var distributionUrl = props.getProperty(GRADLE_WRAPPER_DISTRIBUTION_URL_PROP);
        if (distributionUrl != null) {
            var matcher = Pattern.compile(GRADLE_WRAPPER_VERSION_REGEXP).matcher(distributionUrl);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            } else {
                throw new IllegalStateException(String.format("Could not extract Gradle version from property '%s': %s", GRADLE_WRAPPER_DISTRIBUTION_URL_PROP, distributionUrl));
            }
        } else {
            throw new IllegalStateException(String.format("Could not find property '%s' in file %s", GRADLE_WRAPPER_DISTRIBUTION_URL_PROP, GRADLE_WRAPPER_PROPERTIES_FILE));
        }
    }

    private GradleUtils() {
    }

}
