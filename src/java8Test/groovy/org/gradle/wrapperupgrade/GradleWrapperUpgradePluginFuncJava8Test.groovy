package org.gradle.wrapperupgrade

import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.TempDir

class GradleWrapperUpgradePluginFuncJava8Test extends Specification {

    @TempDir
    File testProjectDir
    File settingsFile
    File buildFile
    String plugins

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
                            allowPreRelease = false
                            labels = ["dependencies", "java"]
                            assignees = ["wrapperbot"]
                            reviewers = ["wrapperbot"]
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
            .withEnvironment(System.getenv() + ["JAVA_HOME": determineJavaHome()])
            .withArguments('clean', 'upgradeGradleWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .buildAndFail()

        then:
        println("Output: ${result.output}")
        result.output.contains('This version of the Wrapper Upgrade Gradle plugin is not compatible with Gradle < 6.0')
    }

    private static String determineJavaHome() {
        return System.getProperty('testContext.javaHome')
    }

}
