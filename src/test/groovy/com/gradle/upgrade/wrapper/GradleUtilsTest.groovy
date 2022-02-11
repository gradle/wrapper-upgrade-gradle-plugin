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

    def "get current gradle version unknown"() {
        given:
        createGradleWrapperProperties().text = 'unexpected'

        when:
        def version = GradleUtils.getCurrentGradleVersion(workingDir)

        then:
        version == Optional.empty()
    }

    def "replace gradle version bin"() {
        given:
        def prop = createGradleWrapperProperties()
        prop.text = standard('7.3.3', 'bin')

        when:
        GradleUtils.replaceInProperties(workingDir, '7.4')

        then:
        prop.text == standard('7.4', 'bin')
    }

    def "replace gradle version all"() {
        given:
        def prop = createGradleWrapperProperties()
        prop.text = 'unexpected'

        when:
        GradleUtils.replaceInProperties(workingDir, '7.4')

        then:
        prop.text == 'unexpected'
    }

    def "replace gradle version unknown"() {
        given:
        def prop = createGradleWrapperProperties()
        prop.text = standard('7.2', 'all')

        when:
        GradleUtils.replaceInProperties(workingDir, '7.4')

        then:
        prop.text == standard('7.4', 'all')
    }

    private static String standard(String gradleVersion, String gradleDistrib) {
        """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion}-${gradleDistrib}.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
    }

    private Path createGradleWrapperProperties() {
        Files.createDirectories(workingDir.resolve('gradle/wrapper/'))
        Files.createFile(workingDir.resolve('gradle/wrapper/gradle-wrapper.properties'))
    }

}
