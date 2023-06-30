package org.gradle.wrapperupgrade;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.internal.ExecException;
import org.gradle.util.internal.VersionNumber;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.wrapperupgrade.BuildToolStrategy.VersionInfo;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    private final ExecOperations execOperations;

    @Inject
    public UpgradeWrapper(WrapperUpgradeDomainObject upgrade, BuildToolStrategy buildToolStrategy, ProjectLayout layout, ExecOperations execOperations) {
        this.upgrade = upgrade;
        this.buildToolStrategy = buildToolStrategy;
        this.layout = layout;
        this.execOperations = execOperations;
        getOutputs().dir(getCheckoutDir());
    }

    private Provider<Directory> getCheckoutDir() {
        return layout.getBuildDirectory().dir("git-clones" + File.separatorChar + upgrade.name);
    }

    @TaskAction
    void upgrade() throws IOException {
        GitHub gitHub = createGitHub();
        boolean allowPreRelease = upgrade.getOptions().getAllowPreRelease().orElse(Boolean.FALSE).get();
        Params params = Params.create(upgrade, buildToolStrategy, allowPreRelease, layout.getProjectDirectory(), getCheckoutDir().get(), gitHub, execOperations);
        if (!prExists(params)) {
            createPrIfWrapperUpgradeAvailable(params);
        } else {
            getLogger().lifecycle(String.format("PR '%s' to upgrade %s Wrapper to %s already exists for project '%s'",
                params.prBranch, buildToolStrategy.buildToolName(), params.latestBuildToolVersion.version, params.project));
        }
    }

    private static GitHub createGitHub() throws IOException {
        GitHubBuilder gitHub = new GitHubBuilder();
        Optional.ofNullable(System.getenv(GIT_TOKEN_ENV_VAR)).ifPresent(gitHub::withOAuthToken);
        return gitHub.build();
    }

    private static boolean prExists(Params params) throws IOException {
        return params.gitHub.getRepository(params.repository).getPullRequests(GHIssueState.OPEN).stream().anyMatch(pr -> pr.getHead().getRef().equals(params.prBranch));
    }

    private void createPrIfWrapperUpgradeAvailable(Params params) throws IOException {
        runWrapperWithLatestBuildToolVersion(params);
        createPrIfWrapperChanged(params);
    }

    private void runWrapperWithLatestBuildToolVersion(Params params) {
        buildToolStrategy.runWrapper(execOperations, params.rootProjectDir, params.latestBuildToolVersion);
        buildToolStrategy.runWrapper(execOperations, params.rootProjectDir, params.latestBuildToolVersion);
    }

    private void createPrIfWrapperChanged(Params params) throws IOException {
        if (isWrapperChanged(params.gitCheckoutDir)) {
            createPr(params);
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

    private void createPr(Params params) throws IOException {
        String shortDesc = createShortDescription(params);
        String longDesc = createLongDescription(params);
        gitCommitAndPush(params, shortDesc);
        gitCreatePr(params, shortDesc, longDesc);
    }

    private String createShortDescription(Params params) {
        String buildToolName = buildToolStrategy.buildToolName();
        String latestBuildToolVersion = params.latestBuildToolVersion.version;
        String usedBuildToolVersion = params.usedBuildToolVersion.version;
        String relativePath = params.rootProjectDirRelativePath.normalize().toString();

        StringBuilder description = new StringBuilder();
        description.append(String.format("Bump %s Wrapper from %s to %s", buildToolName, usedBuildToolVersion, latestBuildToolVersion));
        if (!relativePath.isEmpty()) {
            String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
            description.append(String.format(" in %s", path));
        }
        return description.toString();
    }

    private String createLongDescription(Params params) {
        String buildToolName = buildToolStrategy.buildToolName();
        String latestBuildToolVersion = params.latestBuildToolVersion.version;
        String usedBuildToolVersion = params.usedBuildToolVersion.version;
        String releaseNotesLink = buildToolStrategy.releaseNotesLink(latestBuildToolVersion);

        StringBuilder description = new StringBuilder();
        description.append(String.format("Bumps %s Wrapper from %s to %s.", buildToolName, usedBuildToolVersion, latestBuildToolVersion));
        description.append("\n\n");
        description.append(String.format("Release notes of %s %s can be found here:", buildToolName, latestBuildToolVersion));
        description.append("\n");
        description.append(releaseNotesLink);
        return description.toString();
    }

    private void gitCommitAndPush(Params params, String commitMessage) {
        // Git add
        List<Path> wrapperFiles = buildToolStrategy.wrapperFiles(params.rootProjectDir);
        wrapperFiles.forEach(p -> execGitCmd(execOperations, params.gitCheckoutDir, "add", p));

        // Git checkout
        execGitCmd(execOperations, params.gitCheckoutDir, "checkout", "--quiet", "-b", params.prBranch);

        // Git commit
        List<String> argsAndExtraArgs = new ArrayList<>(Arrays.asList("commit", "--quiet", "-m", commitMessage));
        argsAndExtraArgs.addAll(params.gitCommitExtraArgs);
        execGitCmd(execOperations, params.gitCheckoutDir, argsAndExtraArgs.toArray());

        // Git push
        if (!isDryRun()) {
            execGitCmd(execOperations, params.gitCheckoutDir, "push", "--quiet", "-u", "origin", params.prBranch);
        }
    }

    private void gitCreatePr(Params params, String prTitle, String prBody) throws IOException {
        if (!isDryRun()) {
            GHPullRequest pr = params.gitHub.getRepository(params.repository).createPullRequest(prTitle, params.prBranch, params.baseBranch, prBody);
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
        private final Path gitCheckoutDir;
        private final Path rootProjectDir;
        private final Path rootProjectDirRelativePath;
        private final VersionInfo latestBuildToolVersion;
        private final VersionInfo usedBuildToolVersion;
        private final List<String> gitCommitExtraArgs;
        private final GitHub gitHub;

        private Params(String project, String repository, String baseBranch, String prBranch,
                       Path gitCheckoutDir, Path rootProjectDir, Path rootProjectDirRelativePath,
                       VersionInfo latestBuildToolVersion, VersionInfo usedBuildToolVersion, List<String> gitCommitExtraArgs, GitHub gitHub) {
            this.project = project;
            this.repository = repository;
            this.baseBranch = baseBranch;
            this.prBranch = prBranch;
            this.gitCheckoutDir = gitCheckoutDir;
            this.rootProjectDir = rootProjectDir;
            this.rootProjectDirRelativePath = rootProjectDirRelativePath;
            this.latestBuildToolVersion = latestBuildToolVersion;
            this.usedBuildToolVersion = usedBuildToolVersion;
            this.gitCommitExtraArgs = gitCommitExtraArgs;
            this.gitHub = gitHub;
        }

        private static Params create(WrapperUpgradeDomainObject upgrade, BuildToolStrategy buildToolStrategy, boolean allowPreRelease, Directory executionRootDirectory, Directory gitCheckoutDirectory, GitHub gitHub, ExecOperations exec) throws IOException {
            String project = upgrade.name;
            String repository = upgrade.getRepo().get();
            String baseBranch = upgrade.getBaseBranch().get();
            Path executionRootDir = executionRootDirectory.getAsFile().toPath();
            Path gitCheckoutDir = gitCheckoutDirectory.getAsFile().toPath();
            Path rootProjectDir = gitCheckoutDir.resolve(upgrade.getDir().get());

            cloneGitProject(repository, executionRootDir, baseBranch, gitCheckoutDir, exec);
            VersionInfo usedBuildToolVersion = buildToolStrategy.extractCurrentVersion(rootProjectDir);
            VersionInfo latestBuildToolVersion = getLatestBuildToolVersion(buildToolStrategy, allowPreRelease, usedBuildToolVersion);

            String prBranch = String.format("wrapperbot/%s/%s-wrapper-%s", project, buildToolStrategy.buildToolName().toLowerCase(), latestBuildToolVersion.version);
            Path rootProjectDirRelativePath = gitCheckoutDir.relativize(rootProjectDir);
            List<String> gitCommitExtraArgs = upgrade.getOptions().getGitCommitExtraArgs().orElse(Collections.emptyList()).get();
            return new Params(project, repository, baseBranch, prBranch, gitCheckoutDir, rootProjectDir, rootProjectDirRelativePath, latestBuildToolVersion, usedBuildToolVersion, gitCommitExtraArgs, gitHub);
        }

        private static VersionInfo getLatestBuildToolVersion(BuildToolStrategy buildToolStrategy, boolean allowPreRelease, VersionInfo usedBuildToolVersion) throws IOException {
            VersionInfo latestBuildToolVersion = buildToolStrategy.lookupLatestVersion(allowPreRelease);
            if (VersionNumber.parse(usedBuildToolVersion.version)
                .compareTo(VersionNumber.parse(latestBuildToolVersion.version)) >= 0) {
                return usedBuildToolVersion;
            } else {
                return latestBuildToolVersion;
            }
        }

        private static void cloneGitProject(String repository, Path executionRootDir, String baseBranch, Path gitCheckoutDir, ExecOperations execOperations) {
            String gitUrl = isUrl(repository) ? repository : "https://github.com/" + repository + ".git";
            execGitCmd(execOperations, executionRootDir, "clone", "--quiet", "--depth", "1", "-b", baseBranch, gitUrl, gitCheckoutDir);
            if (isUnsignedCommits()) {
                execGitCmd(execOperations, gitCheckoutDir, "config", "--local", "commit.gpgsign", "false");
            }
        }

    }

}
