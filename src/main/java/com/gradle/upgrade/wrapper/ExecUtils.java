package com.gradle.upgrade.wrapper;

import org.gradle.api.file.Directory;
import org.gradle.process.ExecOperations;

import java.util.Arrays;
import java.util.LinkedList;

public class ExecUtils {
    private ExecUtils() {}

    static private void execCmd(ExecOperations execOperations, Directory workingDir, String cmd, Object... args) {
        var cmdLine = new LinkedList<>();
        cmdLine.add(0, cmd);
        cmdLine.addAll(Arrays.asList(args));
        execOperations.exec(
            execSpec -> {
                if (workingDir != null) {
                    execSpec.workingDir(workingDir);
                }
                execSpec.commandLine(cmdLine);
            });
    }

    static void execGradleCmd(ExecOperations execOperations, Directory gitDir, Object... args) {
        execCmd(execOperations, gitDir, "./gradlew", args);
    }

    static void execGitCmd(ExecOperations execOperations, Directory gitDir, Object... args) {
        execCmd(execOperations, gitDir, "git", args);
    }

    static void execGitCmd(ExecOperations execOperations, Object... args) {
        execCmd(execOperations, null, "git", args);
    }
}
