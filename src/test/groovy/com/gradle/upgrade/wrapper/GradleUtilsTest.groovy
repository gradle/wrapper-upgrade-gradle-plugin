package com.gradle.upgrade.wrapper

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GradleUtilsTest extends Specification {

    @TempDir
    Path workingDir

    def "get current gradle version bin"() {
        given:
        createGradleWrapperProperties().text = standard('7.3.3', 'bin')

        when:
        def version = GradleUtils.getCurrentGradleVersion(workingDir)

        then:
        version == Optional.of('7.3.3')
    }

    def "get current gradle version all"() {
        given:
        createGradleWrapperProperties().text = standard('7.2', 'all')

        when:
        def version = GradleUtils.getCurrentGradleVersion(workingDir)

        then:
        version == Optional.of('7.2')
    }

    def "get current gradle distributionUrl not found"() {
        given:
        createGradleWrapperProperties().text = 'unexpected'

        when:
        GradleUtils.getCurrentGradleVersion(workingDir)

        then:
        def e = thrown(IOException)
        e.message == 'Could not detect Gradle version from distributionUrl property'
    }

    def "get current gradle version unknown"() {
        given:
        createGradleWrapperProperties().text = 'distributionUrl=unknown'

        when:
        GradleUtils.getCurrentGradleVersion(workingDir)

        then:
        def e = thrown(IOException)
        e.message == 'Could not detect Gradle version from distributionUrl property'
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
