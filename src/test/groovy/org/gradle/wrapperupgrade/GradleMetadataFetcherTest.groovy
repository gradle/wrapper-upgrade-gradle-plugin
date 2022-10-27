package org.gradle.wrapperupgrade

import spock.lang.Shared
import spock.lang.Specification

class GradleMetadataFetcherTest extends Specification {

    @Shared
    def gradleMetadataFetcher = new GradleMetadataFetcher(getClass().getResource('/gradle-metadata-all.json'),
        getClass().getResource('/gradle-metadata-current.json'))

    def "fetch latest version allowing pre-releases"() {
        when:
        def version = gradleMetadataFetcher.fetchLatestVersion(true)

        then:
        version.map(node -> node.get('version').asText()).orElse(null) == '8.0-milestone-2'
    }

    def "fetch latest version ignoring pre-releases"() {
        when:
        def version = gradleMetadataFetcher.fetchLatestVersion(false)

        then:
        version.map(node -> node.get('version').asText()).orElse(null) == '7.5.1'
    }

    def "fetch unknown latest version allowing pre-releases"() {
        when:
        def version = new GradleMetadataFetcher(getClass().getResource('/gradle-metadata-all-unknown.json'),
            getClass().getResource('/gradle-metadata-current.json')).fetchLatestVersion(true)

        then:
        version == Optional.empty()
    }

    def "fetch unknown latest version ignoring pre-releases"() {
        when:
        def version = new GradleMetadataFetcher(getClass().getResource('/gradle-metadata-all.json'),
            getClass().getResource('/gradle-metadata-current-unknown.json')).fetchLatestVersion(false)

        then:
        version == Optional.empty()
    }
}
