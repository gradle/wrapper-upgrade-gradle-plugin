package com.gradle.upgrade.wrapper

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GradleBuildToolStrategyTest extends Specification {

    @Shared
    BuildToolStrategy gradleBuildToolStrategy = BuildToolStrategy.GRADLE

    @TempDir
    Path workingDir

    def "extract current Gradle version bin"() {
        given:
        createGradleWrapperProperties().text = standard('7.3.3', 'bin')

        when:
        def version = gradleBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        version == '7.3.3'
    }

    def "extract current Gradle version all"() {
        given:
        createGradleWrapperProperties().text = standard('7.2', 'all')

        when:
        def version = gradleBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        version == '7.2'
    }

    def "extract current Gradle distributionUrl not found"() {
        given:
        createGradleWrapperProperties().text = 'unexpected'

        when:
        gradleBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Could not find property 'distributionUrl' in file gradle/wrapper/gradle-wrapper.properties"
    }

    def "extract current Gradle version unknown"() {
        given:
        createGradleWrapperProperties().text = 'distributionUrl=unknown'

        when:
        gradleBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Could not extract version from property 'distributionUrl': unknown"
    }

    private static String standard(String gradleVersion, String gradleDistro) {
        """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion}-${gradleDistro}.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
    }

    private Path createGradleWrapperProperties() {
        Files.createDirectories(workingDir.resolve('gradle/wrapper/'))
        Files.createFile(workingDir.resolve('gradle/wrapper/gradle-wrapper.properties'))
    }

}
