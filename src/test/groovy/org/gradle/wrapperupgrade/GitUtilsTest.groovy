package org.gradle.wrapperupgrade

import spock.lang.Specification

class GitUtilsTest extends Specification {

    def 'detect Github repo from git remotes'() {
        given:
        def gitRemotes =
            '''
origin\thttps://github.com/gradle/wrapper-upgrade-gradle-plugin.git (fetch)
origin\thttps://github.com/gradle/wrapper-upgrade-gradle-plugin.git (push)
'''
        when:
        def repository = GitUtils.detectGithubRepository(gitRemotes)

        then:
        repository == Optional.of('gradle/wrapper-upgrade-gradle-plugin')
    }

    def 'detect Github repo from git remotes (ssh)'() {
        given:
        def gitRemotes =
            '''
origin\tgit@github.com:gradle/wrapper-upgrade-gradle-plugin.git (fetch)
origin\tgit@github.com:gradle/wrapper-upgrade-gradle-plugin.git (push)
'''
        when:
        def repository = GitUtils.detectGithubRepository(gitRemotes)

        then:
        repository == Optional.of('gradle/wrapper-upgrade-gradle-plugin')
    }

    def 'did not detect Github repo from git remotes'() {
        given:
        def gitRemotes = ''

        when:
        def repository = GitUtils.detectGithubRepository(gitRemotes)

        then:
        repository == Optional.empty()
    }

}
