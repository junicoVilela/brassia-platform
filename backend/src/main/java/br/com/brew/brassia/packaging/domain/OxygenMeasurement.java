package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Medição de oxigênio do envase (FSL-001): oxigênio dissolvido (DO), oxigênio total da embalagem
 * (TPO), purga e vedação.
 *
 * <p>Invariante central: TPO ≥ DO. O oxigênio total inclui o dissolvido mais o do espaço livre —
 * um TPO abaixo do DO é erro de leitura ou de unidade, não uma embalagem melhor que a cerveja.
 *
 * @param purgeMethod     como a embalagem foi purgada antes do enchimento
 * @param sealCheckMethod como a vedação foi verificada (recravação, torque, imersão)
 */
public record OxygenMeasurement(BigDecimal dissolvedOxygenPpb, BigDecimal totalPackageOxygenPpb,
        String purgeMethod, boolean purgeVerified, String sealCheckMethod, boolean sealCheckPassed) {

    public OxygenMeasurement {
        dissolvedOxygenPpb = requireNonNegative(dissolvedOxygenPpb, "oxigênio dissolvido");
        totalPackageOxygenPpb = requireNonNegative(totalPackageOxygenPpb, "oxigênio total da embalagem");
        if (totalPackageOxygenPpb.compareTo(dissolvedOxygenPpb) < 0) {
            throw new IllegalArgumentException(
                    "TPO não pode ser menor que o oxigênio dissolvido: o total inclui o dissolvido");
        }
        purgeMethod = requireText(purgeMethod, "método de purga", 120);
        sealCheckMethod = requireText(sealCheckMethod, "método de verificação da vedação", 120);
    }

    /** Oxigênio do espaço livre: o que o TPO tem além do já dissolvido na cerveja. */
    public BigDecimal headspaceOxygenPpb() {
        return totalPackageOxygenPpb.subtract(dissolvedOxygenPpb);
    }

    /** Evidência completa: purga conferida e vedação aprovada. */
    public boolean evidenceComplete() {
        return purgeVerified && sealCheckPassed;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " é obrigatório");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
        return value;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }
}
