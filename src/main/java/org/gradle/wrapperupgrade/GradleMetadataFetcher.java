package org.gradle.wrapperupgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.util.internal.VersionNumber;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class GradleMetadataFetcher {
    private final URL gradleAllVersionsMetadata;
    private final URL gradleCurrentVersionMetadata;

    GradleMetadataFetcher(URL gradleAllVersionsMetadata, URL gradleCurrentVersionMetadata) {
        this.gradleAllVersionsMetadata = gradleAllVersionsMetadata;
        this.gradleCurrentVersionMetadata = gradleCurrentVersionMetadata;
    }

    GradleMetadataFetcher() {
        try {
            this.gradleAllVersionsMetadata = new URL("https://services.gradle.org/versions/all");
            this.gradleCurrentVersionMetadata = new URL("https://services.gradle.org/versions/current");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    Optional<JsonNode> fetchLatestVersion(boolean allowPreRelease) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        if (allowPreRelease) {
            return StreamSupport.stream(mapper.readTree(gradleAllVersionsMetadata).spliterator(), false)
                .max(Comparator.comparing(n -> VersionNumber.parse(n.path("version").asText())))
                .flatMap(n -> n.path("version").isMissingNode() ? Optional.empty() : Optional.of(n));
        } else {
            JsonNode gradleMetadata = mapper.readTree(gradleCurrentVersionMetadata);
            return gradleMetadata.path("version").isMissingNode() ? Optional.empty() : Optional.of(gradleMetadata);
        }
    }
}
