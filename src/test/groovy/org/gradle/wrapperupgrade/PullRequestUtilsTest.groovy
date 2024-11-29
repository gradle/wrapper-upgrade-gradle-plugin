package org.gradle.wrapperupgrade

import org.kohsuke.github.GHCommitPointer
import org.kohsuke.github.GHIssueState
import org.kohsuke.github.GHPullRequest
import spock.lang.Specification

class PullRequestUtilsTest extends Specification {

    def "closed pr exists"() {
        given:
        def pullRequests = [
                stub('wrapperbot/someproj/gradle-wrapper-8.10', GHIssueState.OPEN),
                stub('wrapperbot/someproj/gradle-wrapper-8.11.1', GHIssueState.OPEN),
                stub('wrapperbot/someproj/gradle-wrapper-8.11.1', GHIssueState.CLOSED)
        ] as Set

        def utils = new PullRequestUtils(pullRequests)

        when:
        def result = utils.closedPrExists(branch)

        then:
        result == exists

        where:
        branch                                      | exists
        'wrapperbot/someproj/gradle-wrapper-8.11.1' | true
        'wrapperbot/someproj/gradle-wrapper-8.10'   | false
    }

    def "pull requests to close"() {
        given:
        def pullRequests = [
                stub('somebranch', GHIssueState.OPEN),
                stub('someotherbranch', GHIssueState.CLOSED),
                stub('wrapperbot/someproj/gradle-wrapper-8.9', GHIssueState.OPEN),
                stub('wrapperbot/someproj/gradle-wrapper-8.10', GHIssueState.CLOSED),
                stub('wrapperbot/someproj/gradle-wrapper-8.11.1', GHIssueState.OPEN)
        ] as Set

        def utils = new PullRequestUtils(pullRequests)

        when:
        def result = utils.pullRequestsToClose('someproj', 'gradle', latestBuildToolVersion)

        then:
        result.collect { it.head.ref } as Set == toClose as Set

        where:
        latestBuildToolVersion | toClose
        '7.13'                 | []
        '8.10'                 | ['wrapperbot/someproj/gradle-wrapper-8.9']
        '8.11.1'               | ['wrapperbot/someproj/gradle-wrapper-8.9']
        '8.11.2'               | ['wrapperbot/someproj/gradle-wrapper-8.11.1', 'wrapperbot/someproj/gradle-wrapper-8.9']
        '8.12'                 | ['wrapperbot/someproj/gradle-wrapper-8.11.1', 'wrapperbot/someproj/gradle-wrapper-8.9']
    }

    private GHPullRequest stub(String branchName, GHIssueState state) {
        def pr = Stub(GHPullRequest)
        def pointer = Stub(GHCommitPointer)
        pointer.getRef() >> branchName
        pr.getHead() >> pointer
        pr.getState() >> state
        return pr
    }
}
