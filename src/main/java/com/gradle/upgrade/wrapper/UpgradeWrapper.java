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
import java.nio.file.Path;

import static com.gradle.upgrade.wrapper.ExecUtils.execGitCmd;
import static java.lang.Boolean.parseBoolean;

@DisableCachingByDefault(because = "Produces no cacheable output")
public abstract class UpgradeWrapper extends DefaultTask {

    private static final String DRY_RUN_GRADLE_PROP = "dryRun";
    private static final String UNSIGNED_COMMITS_GRADLE_PROP = "unsignedCommits";

    private static final String GIT_TOKEN_ENV_VAR = "WRAPPER_UPGRADER_GIT_TOKEN";

    private final UpgradeWrapperDomainObject upgrade;
    private final BuildToolStrategy buildToolStrategy;
    private final ProjectLayout layout;
    private final ObjectFactory objects;
    private final ExecOperations execOperations;
    private final Provider<String> gitHubToken;
    private final boolean dryRun;
    private final boolean unsignedCommits;

    @Inject
    public UpgradeWrapper(UpgradeWrapperDomainObject upgrade, BuildToolStrategy buildToolStrategy, ProjectLayout layout, ObjectFactory objects, ExecOperations execOperations, ProviderFactory providers) {
        this.upgrade = upgrade;
        this.buildToolStrategy = buildToolStrategy;
        this.layout = layout;
        this.objects = objects;
        this.execOperations = execOperations;
        this.gitHubToken = providers.environmentVariable(GIT_TOKEN_ENV_VAR);
        this.dryRun = providers.gradleProperty(DRY_RUN_GRADLE_PROP).map(p -> "".equals(p) || parseBoolean(p)).orElse(false).get();
        this.unsignedCommits = providers.gradleProperty(UNSIGNED_COMMITS_GRADLE_PROP).map(p -> "".equals(p) || parseBoolean(p)).orElse(false).get();
    }

    @TaskAction
    void upgrade() throws IOException {
        var gitHub = createGitHub(gitHubToken);
        var latestBuildToolVersion = buildToolStrategy.lookupLatestVersion();
        var params = Params.create(upgrade, latestBuildToolVersion, layout.getBuildDirectory(), layout.getProjectDirectory(), gitHub);

        if (!prExists(params)) {
            createPrIfWrapperUpgradeAvailable(params);
        } else {
            getLogger().lifecycle(String.format("PR '%s' to upgrade %s Wrapper to %s already exists for project '%s'",
                params.prBranch, buildToolStrategy.buildToolName(), params.latestGradleVersion, params.project));
        }
    }

    private static GitHub createGitHub(Provider<String> gitHubToken) throws IOException {
        var gitHub = new GitHubBuilder();
        if (gitHubToken.isPresent()) {
            gitHub.withOAuthToken(gitHubToken.get());
        }
        return gitHub.build();
    }

    private static boolean prExists(Params params) throws IOException {
        return params.gitHub.getRepository(params.repository).getPullRequests(GHIssueState.OPEN).stream().anyMatch(pr -> pr.getHead().getRef().equals(params.prBranch));
    }

    private void createPrIfWrapperUpgradeAvailable(Params params) throws IOException {
        cloneGitProject(params);
        var usedBuildToolVersion = buildToolStrategy.extractCurrentVersion(params.gradleProjectDir);
        runWrapperWithLatestBuildToolVersion(params);
        createPrIfWrapperChanged(params, usedBuildToolVersion);
    }

    private void cloneGitProject(Params params) {
        var gitUrl = "https://github.com/" + params.repository + ".git";
        execGitCmd(execOperations, params.upgraderRootDirectory, "clone", "--depth", "1", "-b", params.baseBranch, gitUrl, params.gitCheckoutDir);
        if (unsignedCommits) {
            execGitCmd(execOperations, params.gitCheckoutDir, "config", "--local", "commit.gpgsign", "false");
        }
    }

    private void runWrapperWithLatestBuildToolVersion(Params params) {
        buildToolStrategy.runWrapper(execOperations, params.gradleProjectDir, params.latestGradleVersion);
        buildToolStrategy.runWrapper(execOperations, params.gradleProjectDir, params.latestGradleVersion);
    }

    private void createPrIfWrapperChanged(Params params, String usedBuildToolVersion) throws IOException {
        if (isWrapperChanged(params.gitCheckoutDir)) {
            createPr(params, usedBuildToolVersion);
        } else {
            getLogger().lifecycle(String.format("No PR created to upgrade %s Wrapper to %s since already on latest version for project '%s'",
                buildToolStrategy.buildToolName(), params.latestGradleVersion, params.project));
        }
    }

    private boolean isWrapperChanged(Directory gitCheckoutDir) {
        try {
            // `git diff --exit-code` returns exit code 0 when there's no diff, 1 when there's a diff (in which case execOperations throws an exception)
            execGitCmd(execOperations, gitCheckoutDir, "diff", "--quiet", "--exit-code");
            return false;
        } catch (ExecException e) {
            return true;
        }
    }

    private void createPr(Params params, String usedBuildToolVersion) throws IOException {
        String description = createDescription(params, usedBuildToolVersion);
        gitCommitAndPush(params, description);
        gitPr(params, description);
    }

    private String createDescription(Params params, String usedBuildToolVersion) {
        StringBuilder description = new StringBuilder();
        description.append(String.format("Bump %s Wrapper from %s to %s", buildToolStrategy.buildToolName(), usedBuildToolVersion, params.latestGradleVersion));
        if (!params.gradleProjectDirRelativePath.normalize().toString().isEmpty()) {
            description.append(String.format(" in %s", params.gradleProjectDirRelativePath.normalize()));
        }
        return description.toString();
    }

    private void gitCommitAndPush(Params params, String message) {
        var changes = objects.fileTree().from(params.gitCheckoutDir);
        buildToolStrategy.includeWrapperFiles(changes);
        changes.forEach(c -> execGitCmd(execOperations, params.gitCheckoutDir, "add", c.toPath().toString()));
        execGitCmd(execOperations, params.gitCheckoutDir, "checkout", "-b", params.prBranch);
        execGitCmd(execOperations, params.gitCheckoutDir, "commit", "-m", message);
        if (!dryRun) {
            execGitCmd(execOperations, params.gitCheckoutDir, "push", "-u", "origin", params.prBranch);
        }
    }

    private void gitPr(Params params, String title) throws IOException {
        if (!dryRun) {
            var pr = params.gitHub.getRepository(params.repository).createPullRequest(title, params.prBranch, params.baseBranch, null);
            getLogger().lifecycle(String.format("PR '%s' created at %s to upgrade %s Wrapper to %s for project '%s'",
                params.prBranch, pr.getHtmlUrl(), buildToolStrategy.buildToolName(), params.latestGradleVersion, params.project));
        } else {
            getLogger().lifecycle(String.format("Dry run: Skipping creation of PR '%s' that would upgrade %s Wrapper to %s for project '%s'",
                params.prBranch, buildToolStrategy.buildToolName(), params.latestGradleVersion, params.project));
        }
    }

    private static final class Params {

        private final String project;
        private final String repository;
        private final String baseBranch;
        private final String prBranch;
        private final Directory upgraderRootDirectory;
        private final Directory gitCheckoutDir;
        private final Path gradleProjectDir;
        private final Path gradleProjectDirRelativePath;
        private final String latestGradleVersion;
        private final GitHub gitHub;

        private Params(String project, String repository, String baseBranch, String prBranch,
                       Directory upgraderRootDirectory, Directory gitCheckoutDir, Path gradleProjectDir, Path gradleProjectDirRelativePath,
                       String latestGradleVersion, GitHub gitHub) {
            this.project = project;
            this.repository = repository;
            this.baseBranch = baseBranch;
            this.prBranch = prBranch;
            this.upgraderRootDirectory = upgraderRootDirectory;
            this.gitCheckoutDir = gitCheckoutDir;
            this.gradleProjectDir = gradleProjectDir;
            this.gradleProjectDirRelativePath = gradleProjectDirRelativePath;
            this.latestGradleVersion = latestGradleVersion;
            this.gitHub = gitHub;
        }

        private static Params create(UpgradeWrapperDomainObject upgrade, String latestGradleVersion, DirectoryProperty buildDirectory, Directory upgraderRootDirectory, GitHub gitHub) {
            var project = upgrade.name;
            var repository = upgrade.getRepo().get();
            var baseBranch = upgrade.getBaseBranch().get();
            var prBranch = String.format("gwbot/%s/gradle-wrapper-%s", project, latestGradleVersion);
            var gitCheckoutDir = buildDirectory.dir("gitClones/" + project).get();
            var gradleProjectDir = gitCheckoutDir.dir(upgrade.getDir().get()).getAsFile().toPath();
            var gradleProjectDirRelativePath = gitCheckoutDir.getAsFile().toPath().relativize(gradleProjectDir);

            return new Params(project, repository, baseBranch, prBranch, upgraderRootDirectory, gitCheckoutDir, gradleProjectDir, gradleProjectDirRelativePath, latestGradleVersion, gitHub);
        }

    }

}
