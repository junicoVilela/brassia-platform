package br.com.brew.brassia.water.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Perfil de água de referência (WTR-003): água histórica de cidade/região,
 * educativa e versionada. Distinta de fonte, laudo e perfil-alvo. Nunca é
 * aplicada automaticamente a uma receita — é apenas consulta. Guarda o conjunto
 * iônico, alcalinidade/dureza/pH opcionais e a proveniência (fonte).
 */
public final class WaterReferenceProfile {

    private final WaterReferenceProfileId id;
    private final UUID breweryId;
    private final String name;
    private final String region;
    private final String edition;
    private final IonProfile ions;
    private final BigDecimal alkalinity;
    private final BigDecimal hardness;
    private final BigDecimal ph;
    private final UUID sourceId;
    private final String sourceName;
    private ReferenceProfileStatus status;
    private final long version;

    private WaterReferenceProfile(WaterReferenceProfileId id, UUID breweryId, String name, String region,
            String edition, IonProfile ions, BigDecimal alkalinity, BigDecimal hardness, BigDecimal ph, UUID sourceId,
            String sourceName, ReferenceProfileStatus status, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = breweryId;
        this.name = requireText(name, "name");
        this.region = blankToNull(region);
        this.edition = requireText(edition, "edition");
        this.ions = Objects.requireNonNull(ions, "ions");
        this.alkalinity = requireNonNegativeOrNull(alkalinity, "alkalinity");
        this.hardness = requireNonNegativeOrNull(hardness, "hardness");
        this.ph = requirePh(ph);
        this.sourceId = sourceId;
        this.sourceName = blankToNull(sourceName);
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    public static WaterReferenceProfile draft(UUID breweryId, String name, String region, String edition,
            IonProfile ions, BigDecimal alkalinity, BigDecimal hardness, BigDecimal ph, UUID sourceId,
            String sourceName) {
        return new WaterReferenceProfile(WaterReferenceProfileId.newId(), breweryId, name, region, edition, ions,
                alkalinity, hardness, ph, sourceId, sourceName, ReferenceProfileStatus.DRAFT, 1);
    }

    public static WaterReferenceProfile reconstitute(WaterReferenceProfileId id, UUID breweryId, String name,
            String region, String edition, IonProfile ions, BigDecimal alkalinity, BigDecimal hardness, BigDecimal ph,
            UUID sourceId, String sourceName, ReferenceProfileStatus status, long version) {
        return new WaterReferenceProfile(id, breweryId, name, region, edition, ions, alkalinity, hardness, ph,
                sourceId, sourceName, status, version);
    }

    public void publish() {
        if (status == ReferenceProfileStatus.PUBLISHED) {
            throw new IllegalStateException("perfil de referência já publicado");
        }
        this.status = ReferenceProfileStatus.PUBLISHED;
    }

    /** Balanço de cargas do conjunto iônico (tolerância/alerta). */
    public ChargeBalance chargeBalance() {
        return ChargeBalance.of(ions);
    }

    public boolean isGlobal() {
        return breweryId == null;
    }

    public boolean isPublished() {
        return status == ReferenceProfileStatus.PUBLISHED;
    }

    public WaterReferenceProfileId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String name() {
        return name;
    }

    public String region() {
        return region;
    }

    public String edition() {
        return edition;
    }

    public IonProfile ions() {
        return ions;
    }

    public BigDecimal alkalinity() {
        return alkalinity;
    }

    public BigDecimal hardness() {
        return hardness;
    }

    public BigDecimal ph() {
        return ph;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public String sourceName() {
        return sourceName;
    }

    public ReferenceProfileStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    private static String requireText(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return trimmed;
    }

    private static BigDecimal requireNonNegativeOrNull(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
        return value;
    }

    private static BigDecimal requirePh(BigDecimal ph) {
        if (ph != null && (ph.signum() < 0 || ph.compareTo(new BigDecimal("14")) > 0)) {
            throw new IllegalArgumentException("pH deve estar entre 0 e 14");
        }
        return ph;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
