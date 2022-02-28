package com.gradle.upgrade.wrapper

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class GradleWrapperUpgradePluginFuncTest extends Specification {

    @Shared
    String latestGradleVersion

    @TempDir
    File testProjectDir
    File settingsFile
    File buildFile

    def setupSpec() {
        latestGradleVersion = BuildToolStrategy.GRADLE.lookupLatestVersion()
    }

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')

        settingsFile << "rootProject.name = 'wrapper-upgrade-gradle-plugin-example'"
        buildFile << """

plugins {
    id 'base'
    id 'com.gradle.wrapper-upgrade'
}

wrapperUpgrade {
    gradle {
        'common-custom-user-data-gradle-plugin' {
            repo = 'gradle/common-custom-user-data-gradle-plugin'
            baseBranch = 'wrapper-upgrade-gradle-plugin-func-test-do-not-delete'
        }
    }
}
        """
    }

    def "upgrade wrapper on CCUD plugin with dry run"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments('clean', 'upgradeGradleWrapperAll', '-PdryRun', '-PunsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'gwbot/common-custom-user-data-gradle-plugin/gradle-wrapper-${latestGradleVersion}")

        and:
        def gitDir = new File(testProjectDir, 'build/git-clones/common-custom-user-data-gradle-plugin')
        def proc = 'git show --oneline --name-only HEAD'.execute(null, gitDir)
        def output = proc.in.text
        output.contains "gradle/wrapper/gradle-wrapper.jar"
        output.contains "gradle/wrapper/gradle-wrapper.properties"
        output.contains "gradlew"

        and:
        def proc2 = 'git show --oneline HEAD'.execute(null, gitDir)
        def output2 = proc2.in.text
        output2.contains "Bump Gradle Wrapper from 7.0.2 to ${latestGradleVersion}"
        output2.contains "Binary files a/gradle/wrapper/gradle-wrapper.jar and b/gradle/wrapper/gradle-wrapper.jar differ"
        output2.contains "-distributionUrl=https\\://services.gradle.org/distributions/gradle-7.0.2-bin.zip"
        output2.contains "+distributionUrl=https\\://services.gradle.org/distributions/gradle-${latestGradleVersion}-bin.zip"
    }

    def "upgrade wrapper on CCUD plugin with dry run and configuration cache"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments('clean', 'upgradeGradleWrapperAll', '--configuration-cache', '-PdryRun', '-PunsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'gwbot/common-custom-user-data-gradle-plugin/gradle-wrapper-${latestGradleVersion}")
        result.output.contains('Configuration cache entry stored.')

        when:
        result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments('clean', 'upgradeGradleWrapperAll', '--configuration-cache', '-PdryRun', '-PunsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'gwbot/common-custom-user-data-gradle-plugin/gradle-wrapper-${latestGradleVersion}")
        result.output.contains('Reusing configuration cache.')
    }

}
