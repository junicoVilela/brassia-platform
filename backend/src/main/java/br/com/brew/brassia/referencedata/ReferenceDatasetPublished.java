package br.com.brew.brassia.referencedata;

import java.time.Instant;
import java.util.UUID;

/** Evento de domínio: um dataset de referência foi publicado. */
public record ReferenceDatasetPublished(UUID breweryId, UUID datasetId, UUID sourceId, Instant occurredAt) {}
