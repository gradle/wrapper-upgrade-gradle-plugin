package org.gradle.wrapperupgrade

import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import spock.lang.Ignore
import spock.lang.Requires
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
    String plugins

    static boolean allowPreRelease = false

    def setupSpec() {
        latestGradleVersion = BuildToolStrategy.GRADLE.lookupLatestVersion(allowPreRelease).version
    }

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')

        settingsFile << "rootProject.name = 'wrapper-upgrade-gradle-plugin-example'"
        plugins = """
            plugins {
                id 'base'
                id 'org.gradle.wrapper-upgrade'
            }
            """.stripMargin()

        buildFile << """
            ${plugins}
            wrapperUpgrade {
                gradle {
                    'wrapper-upgrade-gradle-plugin-for-func-tests' {
                        repo = 'gradle/wrapper-upgrade-gradle-plugin'
                        baseBranch = 'func-test-do-not-delete'
                        dir = 'samples/gradle'
                        options {
                            allowPreRelease = ${allowPreRelease}
                        }
                    }
                }
            }
        """.stripMargin()
    }

    def "plugin requires at least Gradle 6.0"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(GradleVersion.version('5.6.4').version)
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .buildAndFail()

        then:
        result.output.contains('This version of the Wrapper Upgrade Gradle plugin is not compatible with Gradle < 6.0')
    }

    @Ignore("Hard to maintain")
    def "upgrade wrapper on junit project with dry run"() {
        when:
        buildFile.text = """
            ${plugins}
            wrapperUpgrade {
                gradle {
                    'junit-func-test' {
                        repo = 'junit-team/junit5'
                        baseBranch = 'main'
                        options {
                            allowPreRelease = ${allowPreRelease}
                        }
                    }
                }
            }
        """.stripMargin()
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output =~ /Project 'junit-func-test' Gradle Wrapper current version '(.*)' is equal or newer than latest version '(.*)' available/
    }

    def "upgrade wrapper on wrapper-upgrade-gradle-plugin with dry run"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")

        and:
        def gitDir = new File(testProjectDir, 'build/git-clones/wrapper-upgrade-gradle-plugin-for-func-tests/samples/gradle')
        def proc = 'git show --oneline --name-only HEAD'.execute(null, gitDir)
        def output = proc.in.text
        output.contains "gradle/wrapper/gradle-wrapper.jar"
        output.contains "gradle/wrapper/gradle-wrapper.properties"
        output.contains "gradlew"

        and:
        def proc2 = 'git show --oneline HEAD'.execute(null, gitDir)
        def output2 = proc2.in.text
        output2.contains "Bump Gradle Wrapper from 6.9 to ${latestGradleVersion}"
        output2.contains "Binary files a/samples/gradle/gradle/wrapper/gradle-wrapper.jar and b/samples/gradle/gradle/wrapper/gradle-wrapper.jar differ"
        output2.contains "-distributionUrl=https\\://services.gradle.org/distributions/gradle-6.9-bin.zip"
        output2.contains "+distributionUrl=https\\://services.gradle.org/distributions/gradle-${latestGradleVersion}-bin.zip"
    }

    @Requires({ determineGradleVersion().baseVersion >= GradleVersion.version('7.1') })
    def "upgrade wrapper on wrapper-upgrade-gradle-plugin with dry run and configuration cache"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withArguments('clean', 'upgradeGradleWrapperAll', '--configuration-cache', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")
        result.output.contains('Configuration cache entry stored.')

        when:
        result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withArguments('clean', 'upgradeGradleWrapperAll', '--configuration-cache', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits', '--stacktrace')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")
        result.output.contains('Reusing configuration cache.')
    }

    def "upgrade wrapper on wrapper-upgrade-gradle-plugin with dry run and optional Git arguments"() {
        given:
        buildFile.text = """

plugins {
    id 'base'
    id 'org.gradle.wrapper-upgrade'
}

wrapperUpgrade {
    gradle {
        'wrapper-upgrade-gradle-plugin-for-func-tests' {
            repo = 'gradle/wrapper-upgrade-gradle-plugin'
            baseBranch = 'func-test-do-not-delete'
            dir = 'samples/gradle'
            options {
                gitCommitExtraArgs = ['--date="Wed Mar 23 15:00:00 CET 2022"']
            }
        }
    }
}
        """
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")

        and:
        def gitDir = new File(testProjectDir, 'build/git-clones/wrapper-upgrade-gradle-plugin-for-func-tests/samples/gradle')
        def proc = 'git show -s HEAD'.execute(null, gitDir)
        def output = proc.in.text
        output.contains "Bump Gradle Wrapper from 6.9 to ${latestGradleVersion}"
        output.contains 'Date:   Wed Mar 23 15:00:00 2022 +0100'
    }

    private static GradleVersion determineGradleVersion() {
        def injectedGradleVersionString = System.getProperty('testContext.gradleVersion')
        injectedGradleVersionString ? GradleVersion.version(injectedGradleVersionString) : GradleVersion.current()
    }

}
