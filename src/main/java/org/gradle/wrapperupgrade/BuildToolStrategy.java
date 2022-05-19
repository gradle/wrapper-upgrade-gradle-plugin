package org.gradle.wrapperupgrade;

import org.gradle.process.ExecOperations;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface BuildToolStrategy {

    BuildToolStrategy GRADLE = new GradleBuildToolStrategy();
    BuildToolStrategy MAVEN = new MavenBuildToolStrategy();

    String buildToolName();

    VersionInfo lookupLatestVersion() throws IOException;

    VersionInfo extractCurrentVersion(Path rootProjectDir) throws IOException;

    void runWrapper(ExecOperations execOperations, Path rootProjectDir, VersionInfo version);

    List<Path> wrapperFiles(Path rootProjectDir);

    String releaseNotesLink(String buildToolVersion);

    static VersionInfo extractBuildToolVersion(Path rootProjectDir, String wrapperPropertiesFile,
                                               String distributionUrlProperty, String distributionChecksumProperty,
                                               String versionRegExp) throws IOException {
        try (InputStream is = Files.newInputStream(rootProjectDir.resolve(wrapperPropertiesFile))) {
            Properties wrapperProperties = new Properties();
            wrapperProperties.load(is);
            return extractBuildToolVersion(wrapperProperties, wrapperPropertiesFile, distributionUrlProperty, distributionChecksumProperty, versionRegExp);
        }
    }

    static VersionInfo extractBuildToolVersion(Properties props, String wrapperPropertiesFile,
                                                       String distributionUrlProperty, String distributionChecksumProperty,
                                                       String versionRegExp) {
        String distributionUrl = props.getProperty(distributionUrlProperty);
        if (distributionUrl != null) {
            Matcher matcher = Pattern.compile(versionRegExp).matcher(distributionUrl);
            if (matcher.find()) {
                return new VersionInfo(matcher.group(1), distributionChecksumProperty != null ? props.getProperty(distributionChecksumProperty) : null);
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
