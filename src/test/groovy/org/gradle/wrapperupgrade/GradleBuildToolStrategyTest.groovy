package org.gradle.wrapperupgrade

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
        createGradleWrapperProperties().text = standard('7.3.3', 'bin', '123')

        when:
        def version = gradleBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        version.version == '7.3.3'
        version.checksum == Optional.of('123')
    }

    def "extract current Gradle version all"() {
        given:
        createGradleWrapperProperties().text = standard('7.2', 'all', '456')

        when:
        def version = gradleBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        version.version == '7.2'
        version.checksum == Optional.of('456')
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

    def "extract current Gradle version snapshot"() {
        given:
        createGradleWrapperProperties().text = standard('7.6-20221024231219+0000', 'bin', '789', true)

        when:
        def version = gradleBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        version.version == '7.6-20221024231219+0000'
        version.checksum == Optional.of('789')
    }

    def "resolve release notes links"() {
        given:
        def version = '7.4.1'

        when:
        def releaseNotesLink = gradleBuildToolStrategy.releaseNotesLink(version)

        then:
        releaseNotesLink == 'https://docs.gradle.org/7.4.1/release-notes.html'
    }

    private static String standard(String gradleVersion, String gradleDistro, String distributionChecksum, boolean isSnapshot = false) {
        """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionSha256Sum=${distributionChecksum}
distributionUrl=https\\://services.gradle.org/distributions${isSnapshot ? "-snapshots": ""}/gradle-${gradleVersion}-${gradleDistro}.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
    }

    private Path createGradleWrapperProperties() {
        Files.createDirectories(workingDir.resolve('gradle/wrapper/'))
        Files.createFile(workingDir.resolve('gradle/wrapper/gradle-wrapper.properties'))
    }

}
