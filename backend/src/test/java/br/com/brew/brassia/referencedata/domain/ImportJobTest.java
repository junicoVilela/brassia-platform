package br.com.brew.brassia.referencedata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportJobTest {

    private static final String CHECKSUM = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    private static ImportJob received() {
        return ImportJob.receive(ReferenceSourceId.newId(), UUID.randomUUID(), "2021", "application/json",
                42L, new Checksum(CHECKSUM), "{\"styles\":[]}");
    }

    private static ImportJob validating() {
        var job = received();
        job.startValidation();
        return job;
    }

    @Test
    void startsInReceivedWithoutIssues() {
        var job = received();
        assertThat(job.status()).isEqualTo(ImportJobStatus.RECEIVED);
        assertThat(job.issues()).isEmpty();
        assertThat(job.hasErrors()).isFalse();
    }

    @Test
    void startValidationOnlyFromReceived() {
        var job = validating();
        assertThat(job.status()).isEqualTo(ImportJobStatus.VALIDATING);
        assertThatThrownBy(job::startValidation).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validationWithErrorsFailsJob() {
        var job = validating();
        job.recordValidation(List.of(
                ValidationIssue.warning("units", "unidade assumida"),
                ValidationIssue.error(3, "og", "range", "OG fora do domínio")));

        assertThat(job.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(job.hasErrors()).isTrue();
        assertThat(job.issues()).hasSize(2);
    }

    @Test
    void validationWithoutErrorsRequiresReview() {
        var job = validating();
        job.recordValidation(List.of(ValidationIssue.warning("units", "unidade assumida")));

        assertThat(job.status()).isEqualTo(ImportJobStatus.REVIEW_REQUIRED);
        assertThat(job.hasErrors()).isFalse();
    }

    @Test
    void publishesReviewedJobWhenPermissionAllows() {
        var job = validating();
        job.recordValidation(List.of());
        var datasetId = ReferenceDatasetId.newId();

        job.publish(PermissionStatus.GRANTED, datasetId, NOW);

        assertThat(job.status()).isEqualTo(ImportJobStatus.PUBLISHED);
        assertThat(job.publishedDatasetId()).isEqualTo(datasetId);
    }

    @Test
    void publishBlockedWhenPermissionDenies() {
        var job = validating();
        job.recordValidation(List.of());

        assertThatThrownBy(() -> job.publish(PermissionStatus.PENDING, ReferenceDatasetId.newId(), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
        assertThat(job.status()).isEqualTo(ImportJobStatus.REVIEW_REQUIRED);
    }

    @Test
    void publishBlockedWhenNotUnderReview() {
        var job = received();
        assertThatThrownBy(() -> job.publish(PermissionStatus.GRANTED, ReferenceDatasetId.newId(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failMovesToTerminalAndBlocksFurtherTransitions() {
        var job = received();
        job.fail(ValidationIssue.error("size", "arquivo excede o limite"));

        assertThat(job.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(job.status().isTerminal()).isTrue();
        assertThatThrownBy(() -> job.fail(ValidationIssue.error("x", "y")))
                .isInstanceOf(IllegalStateException.class);
    }
}
