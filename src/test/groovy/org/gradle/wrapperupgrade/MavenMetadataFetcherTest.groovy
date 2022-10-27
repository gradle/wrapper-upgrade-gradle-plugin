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
        version.map(v -> v as String).orElse(null) == '4.0.0-alpha-2'
    }

    def "fetch latest version ignoring pre-releases"() {
        when:
        def version = mavenMetadataFetcher.fetchLatestVersion(false)

        then:
        version.map(v -> v as String).orElse(null) == '3.8.6'
    }

    def "fetch unknown latest version"() {
        when:
        def version = new MavenMetadataFetcher(getClass().getResource('/maven-metadata-none.xml')).fetchLatestVersion(false)

        then:
        version == Optional.empty()
    }

}
