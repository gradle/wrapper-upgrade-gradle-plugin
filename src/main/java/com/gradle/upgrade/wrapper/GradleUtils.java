package com.gradle.upgrade.wrapper;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.ReplaceRegExp;
import org.apache.tools.ant.types.FileSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

class GradleUtils {

    private static final String GRADLE_VERSION_REGEXP = "distributions/gradle-(.*)-(bin|all).zip";

    static Optional<String> getCurrentGradleVersion(Path workingDir) {
        try (var is = Files.newInputStream(workingDir.resolve("gradle/wrapper/gradle-wrapper.properties"))) {
            var props = new Properties();
            props.load(is);
            var distributionUrl = props.getProperty("distributionUrl");
            var matcher = Pattern.compile(GRADLE_VERSION_REGEXP).matcher(distributionUrl);
            return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private GradleUtils() {
    }

}
