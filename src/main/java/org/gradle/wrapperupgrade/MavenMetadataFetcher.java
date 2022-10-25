package org.gradle.wrapperupgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.gradle.util.internal.VersionNumber;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class MavenMetadataFetcher {

    private final URL mavenMetadataUrl;

    MavenMetadataFetcher(URL mavenMetadataUrl) {
        this.mavenMetadataUrl = mavenMetadataUrl;
    }

    MavenMetadataFetcher() {
        try {
            this.mavenMetadataUrl = new URL("https://repo.maven.apache.org/maven2/org/apache/maven/maven-core/maven-metadata.xml");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    Optional<VersionNumber> fetchLatestVersion(boolean allowPreRelease) throws IOException {
        XmlMapper mapper = new XmlMapper();
        JsonNode mavenMetadata = mapper.readTree(mavenMetadataUrl).path("versioning").path("versions").path("version");
        if (mavenMetadata.isMissingNode()) {
            return Optional.empty();
        }
        Predicate<VersionNumber> isReleaseOrIdentity = allowPreRelease ? v -> true : v -> v.getQualifier() == null;
        return StreamSupport.stream(mavenMetadata.spliterator(), false)
            .map(n -> VersionNumber.parse(n.asText()))
            .sorted(Comparator.reverseOrder())
            .filter(isReleaseOrIdentity)
            .findFirst();
    }

}
