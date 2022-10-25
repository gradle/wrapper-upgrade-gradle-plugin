package org.gradle.wrapperupgrade

import spock.lang.Shared
import spock.lang.Specification

class MavenMetadataFetcherTest extends Specification {

    @Shared
    def mavenMetadataFetcher = new MavenMetadataFetcher(getClass().getResource('/maven-metadata.xml'))

    def "fetch latest version allowing pre-releases"() {
        when:
        def version = mavenMetadataFetcher.fetchLatestVersion(true)

        then:
        version.get() as String == '4.0.0-alpha-2'
    }

    def "fetch latest version ignoring pre-releases"() {
        when:
        def version = mavenMetadataFetcher.fetchLatestVersion(false)

        then:
        version.get() as String == '3.8.6'
    }

    def "fetch unknown latest version"() {
        when:
        def version = new MavenMetadataFetcher(getClass().getResource('/maven-metadata-none.xml')).fetchLatestVersion(false)

        then:
        version == Optional.empty()
    }

}
