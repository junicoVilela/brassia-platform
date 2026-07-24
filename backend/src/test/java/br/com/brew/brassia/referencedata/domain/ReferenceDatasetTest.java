package br.com.brew.brassia.referencedata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReferenceDatasetTest {

    private static final String CHECKSUM = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    private static ReferenceDataset draft() {
        return ReferenceDataset.draft(ReferenceSourceId.newId(), new DatasetVersion("2021"), new Checksum(CHECKSUM),
                new Provenance("BJCP", "beer-2021", "https://bjcp.org", NOW), "{\"styles\":[]}", NOW, null);
    }

    @Test
    void startsAsDraftPendingReview() {
        var dataset = draft();

        assertThat(dataset.status()).isEqualTo(DatasetStatus.DRAFT);
        assertThat(dataset.reviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(dataset.publishedAt()).isNull();
        assertThat(dataset.isPublished()).isFalse();
    }

    @Test
    void publishBlockedWhenPermissionDoesNotAllow() {
        for (var status : new PermissionStatus[] {PermissionStatus.UNKNOWN, PermissionStatus.PENDING,
                PermissionStatus.DENIED}) {
            var dataset = draft();
            assertThatThrownBy(() -> dataset.publish(status, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(status.name());
            assertThat(dataset.status()).isEqualTo(DatasetStatus.DRAFT);
            assertThat(dataset.publishedAt()).isNull();
        }
    }

    @Test
    void publishesWhenPermissionAllows() {
        for (var status : new PermissionStatus[] {PermissionStatus.LIMITED_PERMISSION, PermissionStatus.GRANTED}) {
            var dataset = draft();
            dataset.publish(status, NOW);

            assertThat(dataset.status()).isEqualTo(DatasetStatus.PUBLISHED);
            assertThat(dataset.reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
            assertThat(dataset.publishedAt()).isEqualTo(NOW);
            assertThat(dataset.isPublished()).isTrue();
        }
    }

    @Test
    void republishIsBlocked() {
        var dataset = draft();
        dataset.publish(PermissionStatus.GRANTED, NOW);

        assertThatThrownBy(() -> dataset.publish(PermissionStatus.GRANTED, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já publicado");
    }

    @Test
    void preservesImmutableRawPayloadAndChecksum() {
        var dataset = draft();
        dataset.publish(PermissionStatus.GRANTED, NOW);

        assertThat(dataset.rawPayload()).isEqualTo("{\"styles\":[]}");
        assertThat(dataset.checksum().value()).isEqualTo(CHECKSUM);
    }

    @Test
    void rejectsEffectiveToBeforeEffectiveFrom() {
        assertThatThrownBy(() -> ReferenceDataset.draft(ReferenceSourceId.newId(), new DatasetVersion("2021"),
                new Checksum(CHECKSUM), new Provenance("BJCP", null, null, NOW), "{}", NOW, NOW.minusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checksumMustBeSha256Hex() {
        assertThatThrownBy(() -> new Checksum("xyz")).isInstanceOf(IllegalArgumentException.class);
    }
}
