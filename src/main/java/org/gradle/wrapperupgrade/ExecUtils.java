package org.gradle.wrapperupgrade;

import org.gradle.process.ExecOperations;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;

final class ExecUtils {

    static void execGradleCmd(ExecOperations execOperations, Path workingDir, Object... args) {
        execCmd(execOperations, workingDir, "./gradlew", args);
    }

    static void execMavenCmd(ExecOperations execOperations, Path workingDir, Object... args) {
        execCmd(execOperations, workingDir, "./mvnw", args);
    }

    static String execGitCmd(ExecOperations execOperations, Path workingDir, Object... args) {
        return execCmd(execOperations, workingDir, "git", args);
    }

    private static String execCmd(ExecOperations execOperations, Path workingDir, String cmd, Object... args) {
        var cmdLine = new LinkedList<>();
        cmdLine.add(cmd);
        cmdLine.addAll(Arrays.asList(args));
        var out = new ByteArrayOutputStream();
        execOperations.exec(
            execSpec -> {
                execSpec.workingDir(workingDir);
                execSpec.commandLine(cmdLine);
                execSpec.setStandardOutput(out);
            });
        return out.toString();
    }

    private ExecUtils() {
    }

}
