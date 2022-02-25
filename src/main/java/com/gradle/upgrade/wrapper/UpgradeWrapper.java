package com.gradle.upgrade.wrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.internal.ExecException;
import org.gradle.work.DisableCachingByDefault;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;

import static com.gradle.upgrade.wrapper.ExecUtils.execGitCmd;
import static com.gradle.upgrade.wrapper.ExecUtils.execGradleCmd;
import static com.gradle.upgrade.wrapper.GradleUtils.getCurrentGradleVersion;
import static java.lang.Boolean.parseBoolean;

@DisableCachingByDefault(because = "Produces no cacheable output")
public abstract class UpgradeWrapper extends DefaultTask {

    private static final String DRY_RUN_SYS_PROP = "dryRun";
    private static final String GIT_TOKEN_ENV_VAR = "WRAPPER_UPGRADER_GIT_TOKEN";

    private final UpgradeWrapperDomainObject upgrade;
    private final ProjectLayout layout;
    private final ObjectFactory objects;
    private final ExecOperations execOperations;
    private final Provider<String> githubToken;
    private final boolean dryRun;

    @Inject
    public UpgradeWrapper(UpgradeWrapperDomainObject upgrade, ProjectLayout layout, ObjectFactory objects, ExecOperations execOperations, ProviderFactory providers) {
        this.upgrade = upgrade;
        this.layout = layout;
        this.objects = objects;
        this.execOperations = execOperations;
        this.githubToken = providers.environmentVariable(GIT_TOKEN_ENV_VAR);
        this.dryRun = providers.gradleProperty(DRY_RUN_SYS_PROP).map(p -> "".equals(p) || parseBoolean(p)).orElse(false).get();
    }

    @TaskAction
    void upgrade() throws IOException {
        var github = githubToken.isPresent() ? new GitHubBuilder().withOAuthToken(githubToken.get()).build() : new GitHubBuilder().build();
        var upgradeName = upgrade.name;
        var gitDir = layout.getBuildDirectory().dir("gitClones/" + upgradeName).get();
        var workingDir = upgrade.getDir().map(gitDir::dir).orElse(gitDir).get();
        var gradleVersion = latestGradleRelease();
        var branch = String.format("bot/upgrade-gw-%s-to-%s", upgradeName, gradleVersion);
        if (dryRun || !prExists(github, branch, upgrade.getRepo().get())) {
            var currentGradleVersion = cloneAndUpgrade(gitDir, workingDir, gradleVersion);
            var message = commitMessage(upgradeName, gradleVersion, currentGradleVersion);
            if (gitCommit(gitDir, branch, message, !dryRun)) {
                createPullRequest(github, branch, upgrade.getBaseBranch().get(), upgrade.getRepo().get(), message, dryRun);
            } else {
                getLogger().lifecycle("No changes detected on " + upgradeName);
            }
        } else {
            getLogger().lifecycle("PR already exists for " + upgradeName);
        }
    }

    private String cloneAndUpgrade(Directory gitDir, Directory workingDir, String gradleVersion) throws IOException {
        clone(layout.getProjectDirectory(), upgrade.getRepo().get(), gitDir);
        var currentGradleVersion = getCurrentGradleVersion(workingDir.getAsFile().toPath());
        upgradeWrapper(workingDir, gradleVersion);
        return currentGradleVersion;
    }

    private String commitMessage(String upgradeName, String gradleVersion, String currentGradleVersion) {
        return String.format("Bump Gradle Wrapper from %s to %s in %s", currentGradleVersion, gradleVersion, upgradeName);
    }

    private void clone(Directory workingDir, String repo, Directory checkoutDir) {
        var baseBranch = upgrade.getBaseBranch().get();
        var gitUrl = "https://github.com/" + repo + ".git";
        execGitCmd(execOperations, workingDir, "clone", "--depth", "1", "-b", baseBranch, gitUrl, checkoutDir);
    }

    private void upgradeWrapper(Directory workingDir, String gradleVersion) {
        execGradleCmd(execOperations, workingDir, "wrapper", "--gradle-version", gradleVersion);
        execGradleCmd(execOperations, workingDir, "wrapper", "--gradle-version", gradleVersion);
    }

    private boolean gitCommit(Directory gitDir, String branch, String message, boolean push) {
        if (hasChanges(gitDir)) {
            var changes = objects.fileTree().from(gitDir);
            changes.include("**/gradle/wrapper/**", "**/gradlew", "**/gradlew.bat");
            changes.forEach(c -> execGitCmd(execOperations, gitDir, "add", c.toPath().toString()));
            execGitCmd(execOperations, gitDir, "checkout", "-b", branch);
            execGitCmd(execOperations, gitDir, "commit", "-m", message);
            if (push) {
                execGitCmd(execOperations, gitDir, "push", "-u", "origin", branch);
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean hasChanges(Directory gitDir) {
        try {
            // `git diff --exit-code` returns exit code 0 when there's no diff, 1 when there's a diff
            execGitCmd(execOperations, gitDir, "diff", "--quiet", "--exit-code");
            return false;
        } catch (ExecException e) {
            return true;
        }
    }

    private static String latestGradleRelease() throws IOException {
        var mapper = new ObjectMapper();
        var version = mapper.readTree(new URL("https://services.gradle.org/versions/current")).get("version");
        if (version == null) {
            throw new RuntimeException("Cannot determine latest Gradle release");
        }
        return version.asText();
    }

    private static boolean prExists(GitHub github, String branch, String repoName) throws IOException {
        return github.getRepository(repoName).getPullRequests(GHIssueState.OPEN).stream().anyMatch(pr -> pr.getHead().getRef().equals(branch));
    }

    private void createPullRequest(GitHub github, String branch, String baseBranch, String repoName, String title, boolean dryRun) throws IOException {
        if (dryRun) {
            getLogger().lifecycle("Dry run - No PR created");
        } else {
            var pr = github.getRepository(repoName).createPullRequest(title,
                branch, baseBranch != null ? baseBranch : "main", null);
            getLogger().lifecycle("Pull request created " + pr.getHtmlUrl());
        }
    }

}
