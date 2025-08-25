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
                            recreateClosedPullRequest = true
                            allowPreRelease = ${allowPreRelease}
                            labels = ["dependencies", "java"]
                            assignees = ["wrapperbot"]
                            reviewers = ["wrapperbot"]
                        }
                    }
                }
            }
        """.stripMargin()
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
            .withEnvironment(System.getenv() + ["JAVA_HOME": determineJavaHome()])
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains "No pull request created to upgrade Gradle Wrapper to 8.2-rc-2 since already on latest version for project 'junit-func-test'"
    }

    def "upgrade wrapper on wrapper-upgrade-gradle-plugin with dry run"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withEnvironment(System.getenv() + ["JAVA_HOME": determineJavaHome()])
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of pull request 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")

        and:
        def gitDir = new File(testProjectDir, 'build/git-clones/wrapper-upgrade-gradle-plugin-for-func-tests/samples/gradle')
        def proc = 'git show --oneline --name-only HEAD'.execute(null, gitDir)
        def output = proc.in.text
        with(output) {
            contains "gradle/wrapper/gradle-wrapper.jar"
            contains "gradle/wrapper/gradle-wrapper.properties"
            contains "gradlew"
        }

        and:
        def proc2 = 'git show --oneline HEAD'.execute(null, gitDir)
        def output2 = proc2.in.text
        with(output2) {
            contains "Bump Gradle Wrapper from 7.3 to ${latestGradleVersion}"
            contains "Binary files a/samples/gradle/gradle/wrapper/gradle-wrapper.jar and b/samples/gradle/gradle/wrapper/gradle-wrapper.jar differ"
            contains "-distributionUrl=https\\://services.gradle.org/distributions/gradle-7.3-bin.zip"
            contains "+distributionUrl=https\\://services.gradle.org/distributions/gradle-${latestGradleVersion}-bin.zip"
        }

        and:
        def proc3 = 'git log --format=%B -n 1 HEAD'.execute(null, gitDir)
        def output3 = proc3.in.text
        with(output3) {
            contains "Signed-off-by:"
            contains "Release notes of Gradle ${latestGradleVersion} can be found here"
        }
    }

    def "upgrade wrapper on wrapper-upgrade-gradle-plugin configured with Kotlin (#factoryFunc) with dry run"(factoryFunc) {
        given:
        buildFile.delete()
        settingsFile.delete()
        settingsFile = new File(testProjectDir, 'settings.gradle.kts')
        buildFile = new File(testProjectDir, 'build.gradle.kts')

        settingsFile << '''rootProject.name = "wrapper-upgrade-gradle-plugin-example"'''
        plugins = """
            plugins {
                id("base")
                id("org.gradle.wrapper-upgrade")
            }
            """.stripMargin()

        buildFile.text = """
            ${plugins}
            wrapperUpgrade {
                gradle {
                    ${factoryFunc}("wrapper-upgrade-gradle-plugin-for-func-tests") {
                        repo.set("gradle/wrapper-upgrade-gradle-plugin")
                        baseBranch.set("func-test-do-not-delete")
                        dir.set("samples/gradle")
                        options {
                            allowPreRelease.set(${allowPreRelease})
                        }
                    }
                }
            }
        """.stripMargin()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withEnvironment(System.getenv() + ["JAVA_HOME": determineJavaHome()])
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of pull request 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")

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
        output2.contains "Bump Gradle Wrapper from 7.3 to ${latestGradleVersion}"
        output2.contains "Binary files a/samples/gradle/gradle/wrapper/gradle-wrapper.jar and b/samples/gradle/gradle/wrapper/gradle-wrapper.jar differ"
        output2.contains "-distributionUrl=https\\://services.gradle.org/distributions/gradle-7.3-bin.zip"
        output2.contains "+distributionUrl=https\\://services.gradle.org/distributions/gradle-${latestGradleVersion}-bin.zip"

        where:
        factoryFunc << ['create', 'register']
    }

    @Requires({ determineGradleVersion().baseVersion >= GradleVersion.version('7.1') })
    def "upgrade wrapper on wrapper-upgrade-gradle-plugin with dry run and configuration cache"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withEnvironment(System.getenv() + ["JAVA_HOME": determineJavaHome()])
            .withArguments('clean', 'upgradeGradleWrapperAll', '--configuration-cache', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of pull request 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")
        result.output.contains('Configuration cache entry stored.')

        when:
        result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withGradleVersion(determineGradleVersion().version)
            .withEnvironment(System.getenv() + ["JAVA_HOME": determineJavaHome()])
            .withArguments('clean', 'upgradeGradleWrapperAll', '--configuration-cache', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of pull request 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")
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
            .withEnvironment(System.getenv() + ["JAVA_HOME": determineJavaHome()])
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeGradleWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of pull request 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/gradle-wrapper-${latestGradleVersion}")

        and:
        def gitDir = new File(testProjectDir, 'build/git-clones/wrapper-upgrade-gradle-plugin-for-func-tests/samples/gradle')
        def proc = 'git show -s HEAD'.execute(null, gitDir)
        def output = proc.in.text
        output.contains "Bump Gradle Wrapper from 7.3 to ${latestGradleVersion}"
        output.contains 'Date:   Wed Mar 23 15:00:00 2022 +0100'
    }

    private static GradleVersion determineGradleVersion() {
        def injectedGradleVersionString = System.getProperty('testContext.gradleVersion')
        injectedGradleVersionString ? GradleVersion.version(injectedGradleVersionString) : GradleVersion.current()
    }

    private static String determineJavaHome() {
        return System.getProperty('testContext.javaHome')
    }

}
