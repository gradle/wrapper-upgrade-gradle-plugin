package com.gradle.upgrade.wrapper;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.ReplaceRegExp;
import org.apache.tools.ant.types.FileSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

public class GradleUtils {
    private static final String GRADLE_VERSION_REGEXP = "distributions/gradle-(.*)-(bin|all).zip";

    private GradleUtils() {}

    static Optional<String> getCurrentGradleVersion(Path workingDir) {
        try (var is = Files.newInputStream(workingDir.resolve("gradle/wrapper/gradle-wrapper.properties"))) {
            var props = new Properties();
            props.load(is);
            var distributionUrl = props.getProperty("distributionUrl");
            var matcher = Pattern.compile(GRADLE_VERSION_REGEXP).matcher(distributionUrl);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static void replaceInProperties(Path workingDir, String gradleVersion) {
        var replaceRegExp = new ReplaceRegExp();
        var project = new Project();
        replaceRegExp.setProject(new Project());
        replaceRegExp.setMatch(GRADLE_VERSION_REGEXP);
        replaceRegExp.setReplace("distributions/gradle-" + gradleVersion + "-\\2.zip");
        var fileSet = new FileSet();
        fileSet.setProject(project);
        fileSet.setDir(workingDir.toFile());
        fileSet.setIncludes("**/gradle-wrapper.properties");
        replaceRegExp.addFileset(fileSet);
        replaceRegExp.execute();
    }
}
