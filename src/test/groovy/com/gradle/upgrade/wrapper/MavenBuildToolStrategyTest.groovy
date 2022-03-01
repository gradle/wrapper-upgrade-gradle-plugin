package com.gradle.upgrade.wrapper

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class MavenBuildToolStrategyTest extends Specification {

    @Shared
    BuildToolStrategy mavenBuildToolStrategy = BuildToolStrategy.MAVEN

    @TempDir
    Path workingDir

    def "extract current Maven version"() {
        given:
        createMavenWrapperProperties().text = standard('3.8.2')

        when:
        def version = mavenBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        version.version == '3.8.2'
        version.checksum == Optional.empty()
    }

    def "extract current Maven distributionUrl not found"() {
        given:
        createMavenWrapperProperties().text = 'unexpected'

        when:
        mavenBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Could not find property 'distributionUrl' in file .mvn/wrapper/maven-wrapper.properties"
    }

    def "extract current Maven version unknown"() {
        given:
        createMavenWrapperProperties().text = 'distributionUrl=unknown'

        when:
        mavenBuildToolStrategy.extractCurrentVersion(workingDir)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Could not extract version from property 'distributionUrl': unknown"
    }

    private static String standard(String mavenVersion) {
        """
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${mavenVersion}/apache-maven-${mavenVersion}-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar
"""
    }

    private Path createMavenWrapperProperties() {
        Files.createDirectories(workingDir.resolve('.mvn/wrapper/'))
        Files.createFile(workingDir.resolve('.mvn/wrapper/maven-wrapper.properties'))
    }

}
