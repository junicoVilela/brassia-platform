package br.com.brew.brassia.recipe.adapter.inbound.web.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.recipe.adapter.inbound.web.exchange.RecipeExchangeCodec.Format;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeImportPreviewTest {

    private final RecipeExchangeCodec codec = new RecipeExchangeCodec();
    private static final UUID EQUIPMENT = UUID.randomUUID();
    private static final UUID MALT = UUID.randomUUID();

    private RecipeDocument sample() {
        return new RecipeDocument("Hoppy Lager", EQUIPMENT, new BigDecimal("400"), 60, null, new BigDecimal("30"),
                null, null,
                List.of(new RecipeDocument.Item(MALT, "MASH", new BigDecimal("20"), "KG", null, null)));
    }

    @Test
    void validBeerJsonIsImportable() {
        var preview = codec.preview(Format.BEERJSON, codec.write(Format.BEERJSON, sample()));

        assertThat(preview.importable()).isTrue();
        assertThat(preview.itemCount()).isEqualTo(1);
        assertThat(preview.name()).isEqualTo("Hoppy Lager");
        assertThat(preview.blockingIssues()).isEmpty();
    }

    @Test
    void warnsWhenBeerJsonVersionAbsentAndNotWhenPresent() {
        // write() não inclui "version" → aviso; documento com version:1 → sem aviso.
        var absent = codec.preview(Format.BEERJSON, codec.write(Format.BEERJSON, sample()));
        assertThat(absent.warnings()).anyMatch(w -> w.contains("version"));

        var withVersion = "{\"version\":1,\"name\":\"X\",\"equipmentId\":\"" + EQUIPMENT
                + "\",\"batchVolumeLiters\":100,\"items\":[{\"ingredientId\":\"" + MALT
                + "\",\"stage\":\"MASH\",\"quantity\":10,\"unit\":\"KG\"}]}";
        var present = codec.preview(Format.BEERJSON, withVersion);
        assertThat(present.warnings()).noneMatch(w -> w.contains("version"));
        assertThat(present.unknownFields()).doesNotContain("version");
        assertThat(present.importable()).isTrue();
    }

    @Test
    void flagsBlockingIssuesWhenRequiredFieldsMissing() {
        var preview = codec.preview(Format.BEERJSON, "{\"items\":[]}");

        assertThat(preview.importable()).isFalse();
        assertThat(preview.blockingIssues())
                .anySatisfy(i -> assertThat(i).contains("nome"))
                .anySatisfy(i -> assertThat(i).contains("equipmentId"))
                .anySatisfy(i -> assertThat(i).contains("item"));
    }

    @Test
    void beerXmlPreviewWarnsAboutDataLoss() {
        var preview = codec.preview(Format.BEERXML, codec.write(Format.BEERXML, sample()));

        assertThat(preview.warnings()).anyMatch(w -> w.contains("menos dados"));
        assertThat(preview.importable()).isTrue();
    }
}
