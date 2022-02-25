package com.gradle.upgrade.wrapper;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
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

import static com.gradle.upgrade.wrapper.ExecUtils.execGitCmd;
import static com.gradle.upgrade.wrapper.ExecUtils.execGradleCmd;
import static com.gradle.upgrade.wrapper.GradleUtils.extractCurrentGradleVersion;
import static com.gradle.upgrade.wrapper.GradleUtils.lookupLatestGradleVersion;
import static java.lang.Boolean.parseBoolean;

@DisableCachingByDefault(because = "Produces no cacheable output")
public abstract class UpgradeWrapper extends DefaultTask {

    private static final String DRY_RUN_SYS_PROP = "dryRun";
    private static final String GIT_TOKEN_ENV_VAR = "WRAPPER_UPGRADER_GIT_TOKEN";

    private final UpgradeWrapperDomainObject upgrade;
    private final ProjectLayout layout;
    private final ObjectFactory objects;
    private final ExecOperations execOperations;
    private final Provider<String> gitHubToken;
    private final boolean dryRun;

    @Inject
    public UpgradeWrapper(UpgradeWrapperDomainObject upgrade, ProjectLayout layout, ObjectFactory objects, ExecOperations execOperations, ProviderFactory providers) {
        this.upgrade = upgrade;
        this.layout = layout;
        this.objects = objects;
        this.execOperations = execOperations;
        this.gitHubToken = providers.environmentVariable(GIT_TOKEN_ENV_VAR);
        this.dryRun = providers.gradleProperty(DRY_RUN_SYS_PROP).map(p -> "".equals(p) || parseBoolean(p)).orElse(false).get();
    }

    @TaskAction
    void upgrade() throws IOException {
        var gitHub = createGitHub(gitHubToken);
        var latestGradleVersion = lookupLatestGradleVersion();
        var params = Params.create(upgrade, latestGradleVersion, layout.getBuildDirectory());

        if (!prExists(params, gitHub)) {
            tryUpgradeGradleWrapper(params, gitHub);
        } else {
            getLogger().lifecycle(String.format("PR '%s' to upgrade Gradle Wrapper to %s already exists for project '%s'", params.prBranch, params.latestGradleVersion, params.project));
        }
    }

    private static GitHub createGitHub(Provider<String> gitHubToken) throws IOException {
        GitHubBuilder gitHub = new GitHubBuilder();
        if (gitHubToken.isPresent()) {
            gitHub.withOAuthToken(gitHubToken.get());
        }
        return gitHub.build();
    }

    private static boolean prExists(Params params, GitHub gitHub) throws IOException {
        return gitHub.getRepository(params.repository).getPullRequests(GHIssueState.OPEN).stream().anyMatch(pr -> pr.getHead().getRef().equals(params.prBranch));
    }

    private void tryUpgradeGradleWrapper(Params params, GitHub gitHub) throws IOException {
        clone(params, layout.getProjectDirectory());
        var currentGradleVersion = extractCurrentGradleVersion(params.workingDir.getAsFile().toPath());
        upgradeWrapper(params.workingDir, params.latestGradleVersion);

        var message = commitMessage(params.project, params.latestGradleVersion, currentGradleVersion);
        if (gitCommit(params.gitDir, params.prBranch, message, !dryRun)) {
            createPullRequest(gitHub, params.prBranch, upgrade.getBaseBranch().get(), params.repository, message, dryRun);
        } else {
            getLogger().lifecycle("No changes detected on " + params.project);
        }
    }

    private void clone(Params params, Directory workingDir) {
        var gitUrl = "https://github.com/" + params.repository + ".git";
        execGitCmd(execOperations, workingDir, "clone", "--depth", "1", "-b", params.baseBranch, gitUrl, params.gitDir);
    }

    private String commitMessage(String upgradeName, String gradleVersion, String currentGradleVersion) {
        return String.format("Bump Gradle Wrapper from %s to %s in %s", currentGradleVersion, gradleVersion, upgradeName);
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

    private void createPullRequest(GitHub github, String branch, String baseBranch, String repoName, String title, boolean dryRun) throws IOException {
        if (dryRun) {
            getLogger().lifecycle("Dry run - No PR created");
        } else {
            var pr = github.getRepository(repoName).createPullRequest(title,
                branch, baseBranch != null ? baseBranch : "main", null);
            getLogger().lifecycle("Pull request created " + pr.getHtmlUrl());
        }
    }

    private static final class Params {

        private final String project;
        private final String repository;
        private final String baseBranch;
        private final String prBranch;
        private final Directory gitDir;
        private final Directory workingDir;
        private final String latestGradleVersion;

        private Params(String project, String repository, String baseBranch, String prBranch, Directory gitDir, Directory workingDir, String latestGradleVersion) {
            this.project = project;
            this.repository = repository;
            this.baseBranch = baseBranch;
            this.prBranch = prBranch;
            this.gitDir = gitDir;
            this.workingDir = workingDir;
            this.latestGradleVersion = latestGradleVersion;
        }

        private static Params create(UpgradeWrapperDomainObject upgrade, String latestGradleVersion, DirectoryProperty buildDirectory) {
            var project = upgrade.name;
            var repository = upgrade.getRepo().get();
            var baseBranch = upgrade.getBaseBranch().get();
            var prBranch = String.format("gwbot/%s/gradle-wrapper-%s", project, latestGradleVersion);
            var gitDir = buildDirectory.dir("gitClones/" + project).get();
            var workingDir = gitDir.dir(upgrade.getDir().get());

            return new Params(project, repository, baseBranch, prBranch, gitDir, workingDir, latestGradleVersion);
        }

    }

}
