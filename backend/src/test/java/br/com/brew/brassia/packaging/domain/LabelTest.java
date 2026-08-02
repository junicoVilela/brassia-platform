package br.com.brew.brassia.packaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabelTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-08-21T10:00:00Z");

    private static LabelTemplate template(LabelField... fields) {
        return LabelTemplate.firstVersion(BREWERY, "RTL-01", "Rótulo lata 355", List.of(fields), null, ACTOR,
                AT);
    }

    private static LabelRegulatoryRule rule(LabelField... fields) {
        return new LabelRegulatoryRule(EnumSet.copyOf(List.of(fields)));
    }

    private static Map<LabelField, String> values() {
        return Map.of(
                LabelField.BEER_NAME, "IPA da Casa",
                LabelField.BATCH_CODE, "L-2026-014",
                LabelField.VOLUME_ML, "355",
                LabelField.ABV, "6.2",
                LabelField.BEST_BEFORE, "2026-12-18",
                LabelField.QR_PAYLOAD, "brassia://lote/L-2026-014");
    }

    private static Map<LabelField, String> sources() {
        return Map.of(
                LabelField.BEER_NAME, "lote L-2026-014 (nome congelado na abertura)",
                LabelField.BATCH_CODE, "lote de produção",
                LabelField.VOLUME_ML, "embalagem do plano de envase",
                LabelField.ABV, "receita publicada v3",
                LabelField.BEST_BEFORE, "controle de frescor (FSL-001)",
                LabelField.QR_PAYLOAD, "rastreabilidade do lote");
    }

    // --- template versionado ---

    @Test
    void templateStartsAtVersionOneAndNextVersionKeepsTheCode() {
        var first = template(LabelField.BEER_NAME, LabelField.BATCH_CODE);
        var second = first.nextVersion("Rótulo lata 355 v2",
                List.of(LabelField.BEER_NAME, LabelField.BATCH_CODE, LabelField.ABV), "acrescentei o ABV",
                ACTOR, AT);

        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.code()).isEqualTo(first.code());
        // Identidades distintas: a versão anterior continua consultável.
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.fields()).contains(LabelField.ABV);
        assertThat(first.fields()).doesNotContain(LabelField.ABV);
    }

    @Test
    void templateRefusesEmptyOrRepeatedFields() {
        assertThatThrownBy(() -> template())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem campo");
        assertThatThrownBy(() -> template(LabelField.ABV, LabelField.ABV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetido");
    }

    // --- regra regulatória, separada do template ---

    @Test
    void ruleIsSeparateFromLayoutAndCatchesFieldsTheTemplateDropped() {
        // Layout sem a validade, mas a lei exige: a separação é o que denuncia isso.
        var layout = template(LabelField.BEER_NAME, LabelField.BATCH_CODE);
        var regulation = rule(LabelField.BEER_NAME, LabelField.BATCH_CODE, LabelField.BEST_BEFORE);

        assertThat(layout.missingRequiredFields(regulation)).containsExactly(LabelField.BEST_BEFORE);
    }

    @Test
    void ruleWithoutAnyRequiredFieldRegulatesNothing() {
        assertThatThrownBy(() -> new LabelRegulatoryRule(EnumSet.noneOf(LabelField.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- prévia acusa campo ausente ---

    @Test
    void previewResolvesEveryFieldWithItsSource() {
        var preview = LabelPreview.of(
                template(LabelField.BEER_NAME, LabelField.BATCH_CODE, LabelField.ABV, LabelField.BEST_BEFORE),
                rule(LabelField.BEER_NAME, LabelField.BATCH_CODE, LabelField.BEST_BEFORE),
                values(), sources());

        assertThat(preview.printable()).isTrue();
        assertThat(preview.lines()).hasSize(4);
        assertThat(preview.lines().getFirst().value()).isEqualTo("IPA da Casa");
        // Cada valor carrega de onde veio: é isso que torna o rótulo rastreável.
        assertThat(preview.lines().getFirst().source()).contains("lote");
        assertThat(preview.lines().get(2).source()).isEqualTo("receita publicada v3");
    }

    @Test
    void missingRequiredFieldBlocksPrinting() {
        // Alergênicos ainda não têm fonte no sistema: se a lei exige, a prévia barra a impressão.
        var preview = LabelPreview.of(
                template(LabelField.BEER_NAME, LabelField.ALLERGENS),
                rule(LabelField.BEER_NAME, LabelField.ALLERGENS),
                values(), sources());

        assertThat(preview.printable()).isFalse();
        assertThat(preview.missingRequired()).containsExactly(LabelField.ALLERGENS);
    }

    @Test
    void missingOptionalFieldIsOnlyAWarning() {
        var preview = LabelPreview.of(
                template(LabelField.BEER_NAME, LabelField.ALLERGENS),
                rule(LabelField.BEER_NAME),
                values(), sources());

        assertThat(preview.printable()).isTrue();
        assertThat(preview.missingOptional()).containsExactly(LabelField.ALLERGENS);
        assertThat(preview.missingRequired()).isEmpty();
    }

    @Test
    void requiredFieldTheLayoutDoesNotDrawAlsoBlocksPrinting() {
        var preview = LabelPreview.of(
                template(LabelField.BEER_NAME),
                rule(LabelField.BEER_NAME, LabelField.BEST_BEFORE),
                values(), sources());

        // O valor existe, mas o layout não o desenha: sairia um lote de rótulos irregulares.
        assertThat(preview.printable()).isFalse();
        assertThat(preview.requiredNotDrawn()).containsExactly(LabelField.BEST_BEFORE);
        assertThat(preview.missingRequired()).isEmpty();
    }

    @Test
    void blankValueCountsAsMissing() {
        var preview = LabelPreview.of(
                template(LabelField.BEER_NAME),
                rule(LabelField.BEER_NAME),
                Map.of(LabelField.BEER_NAME, "   "), Map.of());

        assertThat(preview.printable()).isFalse();
        assertThat(preview.missingRequired()).containsExactly(LabelField.BEER_NAME);
    }

    // --- impressão e reimpressão ---

    @Test
    void firstPrintNeedsNoReasonAndKeepsTheTemplateVersion() {
        var print = LabelPrint.record(PLAN, BREWERY, template(LabelField.BEER_NAME), 800, false, null, ACTOR,
                AT);

        assertThat(print.reprint()).isFalse();
        assertThat(print.reason()).isNull();
        assertThat(print.quantity()).isEqualTo(800);
        assertThat(print.templateVersion()).isEqualTo(1);
        assertThat(print.templateCode()).isEqualTo("RTL-01");
    }

    @Test
    void reprintRequiresReasonAndQuantity() {
        var template = template(LabelField.BEER_NAME);

        assertThatThrownBy(() -> LabelPrint.record(PLAN, BREWERY, template, 40, true, null, ACTOR, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo da reimpressão");
        assertThatThrownBy(() -> LabelPrint.record(PLAN, BREWERY, template, 40, true, "  ", ACTOR, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LabelPrint.record(PLAN, BREWERY, template, 0, true, "borrou", ACTOR, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantidade");

        var reprint = LabelPrint.record(PLAN, BREWERY, template, 40, true, "impressora borrou 40 rótulos",
                ACTOR, AT);
        assertThat(reprint.reprint()).isTrue();
        assertThat(reprint.reason()).isEqualTo("impressora borrou 40 rótulos");
        assertThat(reprint.quantity()).isEqualTo(40);
    }
}
