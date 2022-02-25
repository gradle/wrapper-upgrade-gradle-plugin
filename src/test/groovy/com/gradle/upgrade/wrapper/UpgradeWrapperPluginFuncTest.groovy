package com.gradle.upgrade.wrapper

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class UpgradeWrapperPluginFuncTest extends Specification {

    @TempDir
    File testProjectDir
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')

        settingsFile << "rootProject.name = 'gradle-wrapper-upgrader-example'"
        buildFile << """

plugins {
    id 'base'
    id 'com.gradle.upgrade.wrapper'
}

wrapperUpgrades {
    'common-custom-user-data-gradle-plugin' {
        repo = 'gradle/common-custom-user-data-gradle-plugin'
        baseBranch = 'gradle-wrapper-upgrader-func-test-do-not-delete'
    }
}
        """
    }

    def "upgrade wrapper on CCUD plugin with dry run"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments('clean', 'upgradeWrapperAll', '-PdryRun', '-PunsignedCommits')
            .build()

        then:
        result.output.contains('Dry run: Not creating PR')
        result.task(':upgradeWrapperAll').outcome == SUCCESS

        def gitDir = testProjectDir.toPath().resolve('build/gitClones/common-custom-user-data-gradle-plugin').toFile()
        def proc = 'git show --oneline HEAD'.execute(null, gitDir)
        def output = proc.in.text
        output.contains 'Bump Gradle Wrapper from 7.3.3 to'
        output.contains 'Binary files a/gradle/wrapper/gradle-wrapper.jar and b/gradle/wrapper/gradle-wrapper.jar differ'
        output.contains '-distributionUrl=https\\://services.gradle.org/distributions/gradle-7.3.3-bin.zip'
        output.contains '+distributionUrl=https\\://services.gradle.org/distributions/gradle-'
    }

    def "upgrade wrapper on CCUD plugin with dry run and configuration cache"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments('clean', 'upgradeWrapperAll', '-PdryRun', '-PunsignedCommits', '--configuration-cache')
            .build()

        then:
        result.output.contains('Dry run: Not creating PR')
        result.task(':upgradeWrapperAll').outcome == SUCCESS
        result.output.contains('Configuration cache entry stored.')

        when:
        result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments('clean', 'upgradeWrapperAll', '-PdryRun', '-PunsignedCommits', '--configuration-cache')
            .build()

        then:
        result.output.contains("Dry run: Not creating PR 'gwbot/common-custom-user-data-gradle-plugin/gradle-wrapper-")
        result.task(':upgradeWrapperAll').outcome == SUCCESS
        result.output.contains('Reusing configuration cache.')
    }

}
