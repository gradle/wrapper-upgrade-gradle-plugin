package com.gradle.upgrade.wrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
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

@DisableCachingByDefault(because = "Produces no cacheable output")
public abstract class UpgradeWrapper extends DefaultTask {

    private final UpgradeWrapperDomainObject upgrade;
    private final ExecOperations execOperations;
    private final Provider<PasswordCredentials> githubToken;

    @Input
    private final Property<String> gradleVersion;

    @Inject
    public UpgradeWrapper(UpgradeWrapperDomainObject upgrade, ExecOperations execOperations, ProviderFactory providers, ObjectFactory objects) {
        this.upgrade = upgrade;
        this.execOperations = execOperations;
        this.githubToken = providers.credentials(PasswordCredentials.class, "github");
        this.gradleVersion = objects.property(String.class).convention(providers.provider(UpgradeWrapper::latestGradleRelease));
    }

    public Property<String> getGradleVersion() {
        return gradleVersion;
    }

    @TaskAction
    void upgrade() throws IOException {
        var github = new GitHubBuilder().withOAuthToken(githubToken.get().getPassword()).build();
        var upgradeName = upgrade.name;
        var gitDir = getProject().getLayout().getBuildDirectory().dir("gitClones/" + upgradeName).get();
        var workingDir = upgrade.getDir().map(gitDir::dir).orElse(gitDir).get();
        try {
            var branch = String.format("bot/upgrade-gw-%s-to-%s", upgradeName, gradleVersion.get());
            if (!prExists(github, branch, upgrade.getRepo().get())) {
                clone(gitDir, upgrade.getRepo().get());
                var currentGradleVersion = getCurrentGradleVersion(workingDir.getAsFile().toPath());
                upgradeWrapper(workingDir);
                var message = "Bump Gradle wrapper " + currentGradleVersion.map(v -> "from " + v).orElse("") + " to " + gradleVersion.get() + " in " + upgradeName;
                if (gitCommit(gitDir, branch, message)) {
                    createPullRequest(github, branch, upgrade.getBaseBranch().get(), upgrade.getRepo().get(), message);
                } else {
                    getLogger().lifecycle("::notice ::No changes detected on " + upgradeName);
                }
            } else {
                getLogger().warn("::warning ::PR already exists for " + upgradeName);
            }
        } catch (GradleException | IOException e) {
            getLogger().warn("::error ::Failed to upgrade " + upgradeName, e);
        }
    }

    private void clone(Directory gitDir, String repo) {
        var gitUrl = "https://github.com/" + repo + ".git";
        execGitCmd(execOperations, getProject().getLayout().getProjectDirectory(), "clone", "--depth", "1", gitUrl, gitDir);
    }

    private void upgradeWrapper(Directory workingDir) {
        execGradleCmd(execOperations, workingDir, "wrapper", "--gradle-version", gradleVersion.get());
        execGradleCmd(execOperations, workingDir, "wrapper", "--gradle-version", gradleVersion.get());
    }

    private boolean gitCommit(Directory gitDir, String branch, String message) {
        if (hasChanges(gitDir)) {
            var changes = getProject().fileTree(gitDir);
            changes.include("**/gradle/wrapper/**", "**/gradlew", "**/gradlew.bat");
            changes.forEach(c -> execGitCmd(execOperations, gitDir, "add", c.toPath().toString()));
            execGitCmd(execOperations, gitDir, "checkout", "-b", branch);
            execGitCmd(execOperations, gitDir, "commit", "-m", message);
            execGitCmd(execOperations, gitDir, "push", "-u", "origin", branch);
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

    private void createPullRequest(GitHub github, String branch, String baseBranch, String repoName, String title) throws IOException {
        var pr = github.getRepository(repoName).createPullRequest(title,
            branch, baseBranch != null ? baseBranch : "main", null);
        getLogger().lifecycle("::notice ::Pull request created " + pr.getHtmlUrl());
    }

}
