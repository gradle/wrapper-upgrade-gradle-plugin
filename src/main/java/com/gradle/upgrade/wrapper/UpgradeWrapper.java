package com.gradle.upgrade.wrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
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
abstract class UpgradeWrapper extends DefaultTask {

    private final ExecOperations execOperations;

    @Input
    abstract Property<UpgradeWrapperPlugin.Upgrade> getUpgrade();

    @Input
    final Property<String> gradleVersion;

    final Property<PasswordCredentials> githubToken;

    @Inject
    public UpgradeWrapper(ProviderFactory providers, ObjectFactory objects, ExecOperations execOperations) {
        this.execOperations = execOperations;
        this.gradleVersion = objects.property(String.class).convention(providers.provider(UpgradeWrapper::latestGradleRelease));
        this.githubToken = objects.property(PasswordCredentials.class);
        this.githubToken.set(providers.credentials(PasswordCredentials.class, "github"));
    }

    public Property<String> getGradleVersion() {
        return gradleVersion;
    }

    @TaskAction
    void upgrade() throws IOException {
        var github = new GitHubBuilder().withOAuthToken(githubToken.get().getPassword()).build();
        var upgrade = getUpgrade().get();
        var upgradeName = upgrade.name;
        var gitDir = getProject().getLayout().getBuildDirectory().dir("gitClones/" + upgradeName).get();
        var workingDir = upgrade.getDir().isPresent() ? gitDir.dir(upgrade.getDir().get()) : gitDir;
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
        execGitCmd(execOperations, "clone", "--depth", "1", gitUrl, gitDir);
    }

    private void upgradeWrapper(Directory workingDir) {
        execGradleCmd(execOperations, workingDir, "wrapper", "--gradle-version", gradleVersion.get());
        execGradleCmd(execOperations, workingDir, "wrapper", "--gradle-version", gradleVersion.get());
    }

    private boolean gitCommit(Directory gitDir, String branch, String message) {
        var changes = getProject().fileTree(gitDir);
        changes.include("**/gradle/wrapper/**", "**/gradlew", "**/gradlew.bat");
        if (checkChanges(gitDir)) {
            changes.forEach(c -> execGitCmd(execOperations, gitDir, "add", c.toPath().toString()));
            execGitCmd(execOperations, gitDir, "checkout", "-b", branch);
            execGitCmd(execOperations, gitDir, "commit", "-m", message);
            execGitCmd(execOperations, gitDir, "push", "-u", "origin", branch);
            return true;
        }
        return false;
    }

    private boolean checkChanges(Directory gitDir) {
        try {
            execGitCmd(execOperations, gitDir, "diff", "--quiet", "--exit-code");
        } catch (ExecException e) {
            return true;
        }
        return false;
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
