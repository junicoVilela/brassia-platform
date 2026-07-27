package br.com.brew.brassia.sanitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompatibilityRuleTest {

    @Test
    void normalizesPreviousProductAndTrimsText() {
        var r = CompatibilityRule.create(UUID.randomUUID(), EquipmentMaterial.INOX, SoilingLevel.PESADA,
                RiskLevel.ALTO, "  Lúpulo  ", null, "  CIP cáustico  ", "enzimático", "não misturar");
        assertThat(r.previousProduct()).isEqualTo("lúpulo");
        assertThat(r.method()).isEqualTo("CIP cáustico");
    }

    @Test
    void genericRuleHasNullPreviousProduct() {
        var r = CompatibilityRule.create(UUID.randomUUID(), EquipmentMaterial.MADEIRA, SoilingLevel.LEVE,
                RiskLevel.BAIXO, "  ", null, "manual", null, null);
        assertThat(r.previousProduct()).isNull();
    }

    @Test
    void rejectsInvalidVocabularyAndBlankMethod() {
        assertThatThrownBy(() -> EquipmentMaterial.of("aço")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SoilingLevel.of("enorme")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskLevel.of("extremo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompatibilityRule.create(UUID.randomUUID(), EquipmentMaterial.INOX,
                SoilingLevel.LEVE, RiskLevel.BAIXO, null, null, "  ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
