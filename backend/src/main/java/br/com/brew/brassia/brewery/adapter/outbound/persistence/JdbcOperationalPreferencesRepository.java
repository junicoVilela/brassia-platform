package br.com.brew.brassia.brewery.adapter.outbound.persistence;

import br.com.brew.brassia.brewery.application.port.outbound.OperationalPreferencesRepository;
import br.com.brew.brassia.brewery.domain.OperationalPreferences;
import br.com.brew.brassia.brewery.domain.OperationalPreferencesSnapshot;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOperationalPreferencesRepository implements OperationalPreferencesRepository {
    private final JdbcClient jdbc;

    JdbcOperationalPreferencesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OperationalPreferences> findByBreweryId(UUID breweryId) {
        return jdbc.sql("""
                SELECT brewery_id, volume_unit, mass_unit, temperature_unit, currency_code,
                       max_batch_volume, allow_negative_stock, stock_policy, version
                FROM brewery_operational_preferences WHERE brewery_id = :id
                """)
                .param("id", breweryId)
                .query((rs, n) -> OperationalPreferences.reconstitute(
                        rs.getObject("brewery_id", UUID.class),
                        rs.getString("volume_unit"),
                        rs.getString("mass_unit"),
                        rs.getString("temperature_unit"),
                        rs.getString("currency_code"),
                        rs.getBigDecimal("max_batch_volume"),
                        rs.getBoolean("allow_negative_stock"),
                        rs.getString("stock_policy"),
                        rs.getLong("version")))
                .optional();
    }

    @Override
    public void save(OperationalPreferences preferences) {
        int updated = jdbc.sql("""
                UPDATE brewery_operational_preferences
                SET volume_unit = :volume, mass_unit = :mass, temperature_unit = :temp,
                    currency_code = :currency, max_batch_volume = :maxBatch,
                    allow_negative_stock = :neg, stock_policy = :policy,
                    version = :version, updated_at = :updatedAt
                WHERE brewery_id = :id
                """)
                .param("id", preferences.breweryId())
                .param("volume", preferences.volumeUnit())
                .param("mass", preferences.massUnit())
                .param("temp", preferences.temperatureUnit())
                .param("currency", preferences.currencyCode())
                .param("maxBatch", preferences.maxBatchVolume())
                .param("neg", preferences.allowNegativeStock())
                .param("policy", preferences.stockPolicy())
                .param("version", preferences.version())
                .param("updatedAt", Timestamp.from(Instant.now()))
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO brewery_operational_preferences (
                        brewery_id, volume_unit, mass_unit, temperature_unit, currency_code,
                        max_batch_volume, allow_negative_stock, stock_policy, version, updated_at)
                    VALUES (:id, :volume, :mass, :temp, :currency, :maxBatch, :neg, :policy, :version, :updatedAt)
                    """)
                    .param("id", preferences.breweryId())
                    .param("volume", preferences.volumeUnit())
                    .param("mass", preferences.massUnit())
                    .param("temp", preferences.temperatureUnit())
                    .param("currency", preferences.currencyCode())
                    .param("maxBatch", preferences.maxBatchVolume())
                    .param("neg", preferences.allowNegativeStock())
                    .param("policy", preferences.stockPolicy())
                    .param("version", preferences.version())
                    .param("updatedAt", Timestamp.from(Instant.now()))
                    .update();
        }
    }

    @Override
    public void appendRevision(OperationalPreferencesSnapshot snapshot, UUID recordedBy) {
        jdbc.sql("""
                INSERT INTO brewery_operational_preferences_revision (
                    brewery_id, version, volume_unit, mass_unit, temperature_unit, currency_code,
                    max_batch_volume, allow_negative_stock, stock_policy, recorded_at, recorded_by)
                VALUES (:id, :version, :volume, :mass, :temp, :currency, :maxBatch, :neg, :policy, :at, :by)
                ON CONFLICT (brewery_id, version) DO NOTHING
                """)
                .param("id", snapshot.breweryId())
                .param("version", snapshot.preferenceVersion())
                .param("volume", snapshot.volumeUnit())
                .param("mass", snapshot.massUnit())
                .param("temp", snapshot.temperatureUnit())
                .param("currency", snapshot.currencyCode())
                .param("maxBatch", snapshot.maxBatchVolume())
                .param("neg", snapshot.allowNegativeStock())
                .param("policy", snapshot.stockPolicy())
                .param("at", Timestamp.from(Instant.now()))
                .param("by", recordedBy)
                .update();
    }

    @Override
    public Optional<OperationalPreferencesSnapshot> findRevision(UUID breweryId, long version) {
        return jdbc.sql("""
                SELECT brewery_id, version, volume_unit, mass_unit, temperature_unit, currency_code,
                       max_batch_volume, allow_negative_stock, stock_policy
                FROM brewery_operational_preferences_revision
                WHERE brewery_id = :id AND version = :version
                """)
                .param("id", breweryId)
                .param("version", version)
                .query((rs, n) -> new OperationalPreferencesSnapshot(
                        rs.getObject("brewery_id", UUID.class),
                        rs.getLong("version"),
                        rs.getString("volume_unit"),
                        rs.getString("mass_unit"),
                        rs.getString("temperature_unit"),
                        rs.getString("currency_code"),
                        rs.getObject("max_batch_volume", BigDecimal.class),
                        rs.getBoolean("allow_negative_stock"),
                        rs.getString("stock_policy")))
                .optional();
    }
}
