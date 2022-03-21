package org.gradle.wrapperupgrade;

import org.gradle.process.ExecOperations;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GitUtils {

    public static Optional<String> detectGithubRepository(ExecOperations execOperations, Path workingDir) {
        var result = ExecUtils.execGitCmd(execOperations, workingDir, "remote", "-v");
        return detectGithubRepository(result);
    }

    static Optional<String> detectGithubRepository(String gitRemotes) {
        var pattern = Pattern.compile("(https:\\/\\/github\\.com\\/|git@github.com:)(.*)\\.git\\s+\\(push\\)");
        return gitRemotes.lines().flatMap(line -> {
            var matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Stream.of(matcher.group(2));
            } else {
                return Stream.empty();
            }
        }).findFirst();
    }

}
