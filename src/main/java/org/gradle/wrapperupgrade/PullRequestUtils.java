package org.gradle.wrapperupgrade;

import org.gradle.util.internal.VersionNumber;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;

import java.util.Set;
import java.util.stream.Collectors;

public class PullRequestUtils {

    private static final String BRANCH_PREFIX = "wrapperbot/%s/%s-wrapper-";
    private final Set<GHPullRequest> pullRequests;

    PullRequestUtils(Set<GHPullRequest> pullRequests) {
        this.pullRequests = pullRequests;
    }

    static String branchPrefix(String project, String buildTool) {
        return String.format(BRANCH_PREFIX, project, buildTool.toLowerCase());
    }

    Set<GHPullRequest> pullRequestsToClose(String project, String buildTool, String latestBuildToolVersion) {
        VersionNumber latest = VersionNumber.parse(latestBuildToolVersion);
        return pullRequests.stream()
                .filter(p -> p.getState() != GHIssueState.CLOSED)
                .filter(p -> {
                    String branch = p.getHead().getRef();
                    String prefix = branchPrefix(project, buildTool);
                    int index = branch.lastIndexOf(prefix);
                    return index == 0 && latest.compareTo(VersionNumber.parse(branch.substring(prefix.length()))) > 0;
                }
            )
            .collect(Collectors.toSet());
    }

    boolean openPrExists(String branch) {
        return prExists(branch, GHIssueState.OPEN);
    }

    boolean closedPrExists(String branch) {
        return prExists(branch, GHIssueState.CLOSED);
    }

    private boolean prExists(String branch, GHIssueState state) {
        return pullRequests.stream().anyMatch(p -> branch.equals(p.getHead().getRef()) && p.getState() == state);
    }

}
