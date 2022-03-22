package org.gradle.wrapperupgrade;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.internal.ExecException;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.wrapperupgrade.BuildToolStrategy.VersionInfo;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import javax.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.Boolean.parseBoolean;
import static org.gradle.wrapperupgrade.ExecUtils.execGitCmd;

@DisableCachingByDefault(because = "Produces no cacheable output")
public abstract class UpgradeWrapper extends DefaultTask {

    private static final String GIT_TOKEN_ENV_VAR = "WRAPPER_UPGRADE_GIT_TOKEN";

    private static final String UNSIGNED_COMMITS_SYS_PROP = "wrapperUpgrade.unsignedCommits";
    private static final String DRY_RUN_SYS_PROP = "wrapperUpgrade.dryRun";

    private final WrapperUpgradeDomainObject upgrade;
    private final BuildToolStrategy buildToolStrategy;
    private final ProjectLayout layout;
    private final ObjectFactory objects;
    private final ExecOperations execOperations;

    @Inject
    public UpgradeWrapper(WrapperUpgradeDomainObject upgrade, BuildToolStrategy buildToolStrategy, ProjectLayout layout, ObjectFactory objects, ExecOperations execOperations, ProviderFactory providers) {
        this.upgrade = upgrade;
        this.buildToolStrategy = buildToolStrategy;
        this.layout = layout;
        this.objects = objects;
        this.execOperations = execOperations;
    }

    @TaskAction
    void upgrade() throws IOException {
        var gitHub = createGitHub();
        var latestBuildToolVersion = buildToolStrategy.lookupLatestVersion();
        var params = Params.create(upgrade, buildToolStrategy, latestBuildToolVersion, layout.getProjectDirectory(), layout.getBuildDirectory(), gitHub);

        if (!prExists(params)) {
            createPrIfWrapperUpgradeAvailable(params);
        } else {
            getLogger().lifecycle(String.format("PR '%s' to upgrade %s Wrapper to %s already exists for project '%s'",
                params.prBranch, buildToolStrategy.buildToolName(), params.latestBuildToolVersion.version, params.project));
        }
    }

    private static GitHub createGitHub() throws IOException {
        var gitHub = new GitHubBuilder();
        Optional.ofNullable(System.getenv(GIT_TOKEN_ENV_VAR)).ifPresent(gitHub::withOAuthToken);
        return gitHub.build();
    }

    private static boolean prExists(Params params) throws IOException {
        return params.gitHub.getRepository(params.repository).getPullRequests(GHIssueState.OPEN).stream().anyMatch(pr -> pr.getHead().getRef().equals(params.prBranch));
    }

    private void createPrIfWrapperUpgradeAvailable(Params params) throws IOException {
        cloneGitProject(params);
        var usedBuildToolVersion = buildToolStrategy.extractCurrentVersion(params.rootProjectDir);
        runWrapperWithLatestBuildToolVersion(params);
        createPrIfWrapperChanged(params, usedBuildToolVersion.version);
    }

    private void cloneGitProject(Params params) {
        var gitUrl = isUrl(params.repository) ? params.repository : "https://github.com/" + params.repository + ".git";
        execGitCmd(execOperations, params.executionRootDir, "clone", "--quiet", "--depth", "1", "-b", params.baseBranch, gitUrl, params.gitCheckoutDir);
        if (isUnsignedCommits()) {
            execGitCmd(execOperations, params.gitCheckoutDir, "config", "--local", "commit.gpgsign", "false");
        }
    }

    private void runWrapperWithLatestBuildToolVersion(Params params) {
        buildToolStrategy.runWrapper(execOperations, params.rootProjectDir, params.latestBuildToolVersion);
        buildToolStrategy.runWrapper(execOperations, params.rootProjectDir, params.latestBuildToolVersion);
    }

    private void createPrIfWrapperChanged(Params params, String usedBuildToolVersion) throws IOException {
        if (isWrapperChanged(params.gitCheckoutDir)) {
            createPr(params, usedBuildToolVersion);
        } else {
            getLogger().lifecycle(String.format("No PR created to upgrade %s Wrapper to %s since already on latest version for project '%s'",
                buildToolStrategy.buildToolName(), params.latestBuildToolVersion.version, params.project));
        }
    }

    private boolean isWrapperChanged(Path gitCheckoutDir) {
        try {
            // `git diff --exit-code` returns exit code 0 when there's no diff, 1 when there's a diff (in which case execOperations throws an exception)
            execGitCmd(execOperations, gitCheckoutDir, "diff", "--quiet", "--exit-code");
            return false;
        } catch (ExecException e) {
            return true;
        }
    }

    private void createPr(Params params, String usedBuildToolVersion) throws IOException {
        var shortDesc = createShortDescription(params, usedBuildToolVersion);
        var longDesc = createLongDescription(params, usedBuildToolVersion);
        gitCommitAndPush(params, shortDesc);
        gitCreatePr(params, shortDesc, longDesc);
    }

    private String createShortDescription(Params params, String usedBuildToolVersion) {
        var buildToolName = buildToolStrategy.buildToolName();
        var latestBuildToolVersion = params.latestBuildToolVersion.version;
        var relativePath = params.rootProjectDirRelativePath.normalize().toString();

        var description = new StringBuilder();
        description.append(String.format("Bump %s Wrapper from %s to %s", buildToolName, usedBuildToolVersion, latestBuildToolVersion));
        if (!relativePath.isEmpty()) {
            String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
            description.append(String.format(" in %s", path));
        }
        return description.toString();
    }

    private String createLongDescription(Params params, String usedBuildToolVersion) {
        var buildToolName = buildToolStrategy.buildToolName();
        var latestBuildToolVersion = params.latestBuildToolVersion.version;
        var releaseNotesLink = buildToolStrategy.releaseNotesLink(latestBuildToolVersion);

        var description = new StringBuilder();
        description.append(String.format("Bumps %s Wrapper from %s to %s.", buildToolName, usedBuildToolVersion, latestBuildToolVersion));
        description.append("\n\n");
        description.append(String.format("Release notes of %s %s can be found here:", buildToolName, latestBuildToolVersion));
        description.append("\n");
        description.append(releaseNotesLink);
        return description.toString();
    }

    private void gitCommitAndPush(Params params, String commitMessage) {
        var changes = objects.fileTree().from(params.gitCheckoutDir);
        buildToolStrategy.includeWrapperFiles(changes);
        changes.forEach(c -> execGitCmd(execOperations, params.gitCheckoutDir, "add", c.toPath().toString()));
        execGitCmd(execOperations, params.gitCheckoutDir, "checkout", "--quiet", "-b", params.prBranch);
        execGitCmd(execOperations, params.gitCheckoutDir, "commit", "--quiet", "-s", "-m", commitMessage);
        if (!isDryRun()) {
            execGitCmd(execOperations, params.gitCheckoutDir, "push", "--quiet", "-u", "origin", params.prBranch);
        }
    }

    private void gitCreatePr(Params params, String prTitle, String prBody) throws IOException {
        if (!isDryRun()) {
            var pr = params.gitHub.getRepository(params.repository).createPullRequest(prTitle, params.prBranch, params.baseBranch, prBody);
            getLogger().lifecycle(String.format("PR '%s' created at %s to upgrade %s Wrapper to %s for project '%s'",
                params.prBranch, pr.getHtmlUrl(), buildToolStrategy.buildToolName(), params.latestBuildToolVersion.version, params.project));
        } else {
            getLogger().lifecycle(String.format("Dry run: Skipping creation of PR '%s' that would upgrade %s Wrapper to %s for project '%s'",
                params.prBranch, buildToolStrategy.buildToolName(), params.latestBuildToolVersion.version, params.project));
        }
    }

    private static boolean isUnsignedCommits() {
        return Optional.ofNullable(System.getProperty(UNSIGNED_COMMITS_SYS_PROP)).map(p -> "".equals(p) || parseBoolean(p)).orElse(false);
    }

    private static boolean isDryRun() {
        return Optional.ofNullable(System.getProperty(DRY_RUN_SYS_PROP)).map(p -> "".equals(p) || parseBoolean(p)).orElse(false);
    }

    private static boolean isUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private static final class Params {

        private final String project;
        private final String repository;
        private final String baseBranch;
        private final String prBranch;
        private final Path executionRootDir;
        private final Path gitCheckoutDir;
        private final Path rootProjectDir;
        private final Path rootProjectDirRelativePath;
        private final VersionInfo latestBuildToolVersion;
        private final GitHub gitHub;

        private Params(String project, String repository, String baseBranch, String prBranch,
                       Path executionRootDir, Path gitCheckoutDir, Path rootProjectDir, Path rootProjectDirRelativePath,
                       VersionInfo latestBuildToolVersion, GitHub gitHub) {
            this.project = project;
            this.repository = repository;
            this.baseBranch = baseBranch;
            this.prBranch = prBranch;
            this.executionRootDir = executionRootDir;
            this.gitCheckoutDir = gitCheckoutDir;
            this.rootProjectDir = rootProjectDir;
            this.rootProjectDirRelativePath = rootProjectDirRelativePath;
            this.latestBuildToolVersion = latestBuildToolVersion;
            this.gitHub = gitHub;
        }

        private static Params create(WrapperUpgradeDomainObject upgrade, BuildToolStrategy buildToolStrategy, VersionInfo latestBuildToolVersion, Directory executionRootDirectory, DirectoryProperty buildDirectory, GitHub gitHub) {
            var project = upgrade.name;
            var repository = upgrade.getRepo().get();
            var baseBranch = upgrade.getBaseBranch().get();
            var prBranch = String.format("wrapperbot/%s/%s-wrapper-%s", project, buildToolStrategy.buildToolName().toLowerCase(), latestBuildToolVersion.version);
            var executionRootDir = executionRootDirectory.getAsFile().toPath();
            var gitCheckoutDir = buildDirectory.getAsFile().get().toPath().resolve(Path.of("git-clones", project));
            var rootProjectDir = gitCheckoutDir.resolve(upgrade.getDir().get());
            var rootProjectDirRelativePath = gitCheckoutDir.relativize(rootProjectDir);

            return new Params(project, repository, baseBranch, prBranch, executionRootDir, gitCheckoutDir, rootProjectDir, rootProjectDirRelativePath, latestBuildToolVersion, gitHub);
        }

    }

}
