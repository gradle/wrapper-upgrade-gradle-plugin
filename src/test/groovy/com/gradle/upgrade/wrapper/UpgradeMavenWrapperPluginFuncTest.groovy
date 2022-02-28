package com.gradle.upgrade.wrapper

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class UpgradeMavenWrapperPluginFuncTest extends Specification {

    @Shared
    String latestMavenVersion

    @TempDir
    File testProjectDir
    File settingsFile
    File buildFile

    def setupSpec() {
        latestMavenVersion = BuildToolStrategy.MAVEN.lookupLatestVersion()
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
    maven {
        'common-custom-user-data-maven-extension' {
            repo = 'gradle/common-custom-user-data-maven-extension'
            baseBranch = 'wrapper-upgrade-gradle-plugin-func-test-do-not-delete'
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
            .withArguments('clean', 'upgradeMavenWrapperAll', '-PdryRun', '-PunsignedCommits')
            .build()

        then:
        result.task(':upgradeMavenWrapperAll').outcome == SUCCESS

        and:
        result.output.contains("Dry run: Skipping creation of PR 'gwbot/common-custom-user-data-maven-extension/maven-wrapper-${latestMavenVersion}")

        and:
        def gitDir = new File(testProjectDir, 'build/git-clones/common-custom-user-data-maven-extension')
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
        output2.contains 'Binary files a/.mvn/wrapper/maven-wrapper.jar and b/.mvn/wrapper/maven-wrapper.jar differ'
        output2.contains "-distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.6.3/apache-maven-3.6.3-bin.zip"
        output2.contains "+distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${latestMavenVersion}/apache-maven-${latestMavenVersion}-bin.zip"
        output2.contains "-wrapperUrl=https://repo.maven.apache.org/maven2/io/takari/maven-wrapper/0.5.6/maven-wrapper-0.5.6.jar"
        output2.contains "+wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar"
    }

}
