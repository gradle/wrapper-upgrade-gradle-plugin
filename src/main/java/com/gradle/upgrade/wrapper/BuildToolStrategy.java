package com.gradle.upgrade.wrapper;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.process.ExecOperations;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

public interface BuildToolStrategy {

    BuildToolStrategy GRADLE = new GradleBuildToolStrategy();
    BuildToolStrategy MAVEN = new MavenBuildToolStrategy();

    String buildToolName();

    VersionInfo lookupLatestVersion() throws IOException;

    String extractCurrentVersion(Path rootProjectDir) throws IOException;

    void runWrapper(ExecOperations execOperations, Path rootProjectDir, VersionInfo version);

    void includeWrapperFiles(ConfigurableFileTree tree);

    static String extractBuildToolVersion(Path rootProjectDir, String wrapperPropertiesFile, String distributionUrlProperty, String regExp) throws IOException {
        try (var is = Files.newInputStream(rootProjectDir.resolve(wrapperPropertiesFile))) {
            var wrapperProperties = new Properties();
            wrapperProperties.load(is);
            return extractBuildToolVersion(wrapperProperties, wrapperPropertiesFile, distributionUrlProperty, regExp);
        }
    }

    private static String extractBuildToolVersion(Properties props, String wrapperPropertiesFile, String distributionUrlProperty, String regExp) {
        var distributionUrl = props.getProperty(distributionUrlProperty);
        if (distributionUrl != null) {
            var matcher = Pattern.compile(regExp).matcher(distributionUrl);
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new IllegalStateException(String.format("Could not extract version from property '%s': %s", distributionUrlProperty, distributionUrl));
            }
        } else {
            throw new IllegalStateException(String.format("Could not find property '%s' in file %s", distributionUrlProperty, wrapperPropertiesFile));
        }
    }

    final class VersionInfo {

        public String version;
        public Optional<String> checksum;

        public VersionInfo(String version, @Nullable String checksum) {
            this.version = version;
            this.checksum = Optional.ofNullable(checksum);
        }

    }

}

