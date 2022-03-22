package org.gradle.wrapperupgrade

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class MavenWrapperUpgradePluginFuncTest extends Specification {

    @Shared
    String latestMavenVersion

    @TempDir
    File testProjectDir
    File settingsFile
    File buildFile

    def setupSpec() {
        latestMavenVersion = BuildToolStrategy.MAVEN.lookupLatestVersion().version
    }

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')

        settingsFile << "rootProject.name = 'wrapper-upgrade-gradle-plugin-example'"
        buildFile << """

plugins {
    id 'base'
    id 'org.gradle.wrapper-upgrade'
}

wrapperUpgrade {
    maven {
        'wrapper-upgrade-gradle-plugin-for-func-tests' {
            repo = 'gradle/wrapper-upgrade-gradle-plugin'
            baseBranch = 'func-test-do-not-delete'
            dir = 'samples/maven'
        }
    }
}
        """
    }

    def "upgrade wrapper on CCUD extension with dry run"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments('clean', 'upgradeMavenWrapperAll', '-DwrapperUpgrade.dryRun', '-DwrapperUpgrade.unsignedCommits')
            .build()

        then:
        result.task(':upgradeMavenWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'wrapperbot/wrapper-upgrade-gradle-plugin-for-func-tests/maven-wrapper-${latestMavenVersion}")

        and:
        def gitDir = new File(testProjectDir, 'build/git-clones/wrapper-upgrade-gradle-plugin-for-func-tests/samples/maven')
        def proc = 'git show --oneline --name-only HEAD'.execute(null, gitDir)
        def output = proc.in.text
        output.contains ".mvn/wrapper/maven-wrapper.jar"
        output.contains ".mvn/wrapper/maven-wrapper.properties"
        output.contains "mvnw"
        output.contains "mvnw.cmd"

        and:
        def proc2 = 'git show --oneline HEAD'.execute(null, gitDir)
        def output2 = proc2.in.text
        output2.contains "Bump Maven Wrapper from 3.6.3 to ${latestMavenVersion}"
        output2.contains 'Binary files a/samples/maven/.mvn/wrapper/maven-wrapper.jar and b/samples/maven/.mvn/wrapper/maven-wrapper.jar differ'
        output2.contains "-distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.6.3/apache-maven-3.6.3-bin.zip"
        output2.contains "+distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${latestMavenVersion}/apache-maven-${latestMavenVersion}-bin.zip"
        output2.contains "-wrapperUrl=https://repo.maven.apache.org/maven2/io/takari/maven-wrapper/0.5.6/maven-wrapper-0.5.6.jar"
        output2.contains "+wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar"
    }

}
