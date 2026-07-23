package br.com.brew.brassia.recipe.adapter.inbound.web.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.recipe.adapter.inbound.web.exchange.RecipeExchangeCodec.Format;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeExchangeCodecTest {

    private final RecipeExchangeCodec codec = new RecipeExchangeCodec();
    private static final UUID EQUIPMENT = UUID.randomUUID();
    private static final UUID MALT = UUID.randomUUID();

    private RecipeDocument sample() {
        return new RecipeDocument("Hoppy Lager", EQUIPMENT, new BigDecimal("400"), 60, null, new BigDecimal("30"),
                null, null,
                List.of(new RecipeDocument.Item(MALT, "MASH", new BigDecimal("20"), "KG", null, null)));
    }

    @Test
    void jsonRoundTrip() {
        var parsed = codec.parse(Format.BEERJSON, codec.write(Format.BEERJSON, sample()));
        assertThat(parsed.unknownFields()).isEmpty();
        var d = parsed.document();
        assertThat(d.name()).isEqualTo("Hoppy Lager");
        assertThat(d.equipmentId()).isEqualTo(EQUIPMENT);
        assertThat(d.batchVolumeLiters()).isEqualByComparingTo("400");
        assertThat(d.targetIbu()).isEqualByComparingTo("30");
        assertThat(d.items()).singleElement().satisfies(i -> {
            assertThat(i.ingredientId()).isEqualTo(MALT);
            assertThat(i.stage()).isEqualTo("MASH");
            assertThat(i.quantity()).isEqualByComparingTo("20");
        });
    }

    @Test
    void xmlRoundTrip() {
        var xml = codec.write(Format.BEERXML, sample());
        assertThat(xml).contains("<recipe>").contains("<name>Hoppy Lager</name>");
        var parsed = codec.parse(Format.BEERXML, xml);
        assertThat(parsed.unknownFields()).isEmpty();
        assertThat(parsed.document().batchVolumeLiters()).isEqualByComparingTo("400");
        assertThat(parsed.document().items()).hasSize(1);
    }

    @Test
    void reportsUnknownJsonFields() {
        var json = """
                {"name":"X","equipmentId":"%s","batchVolumeLiters":400,"colorMethod":"morey",
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG","origin":"DE"}]}
                """.formatted(EQUIPMENT, MALT);
        var parsed = codec.parse(Format.BEERJSON, json);
        assertThat(parsed.unknownFields()).contains("colorMethod", "items[0].origin");
    }

    @Test
    void reportsUnknownXmlElements() {
        var xml = """
                <recipe><name>X</name><equipmentId>%s</equipmentId><batchVolumeLiters>400</batchVolumeLiters>
                <style>Pilsner</style><items><item><ingredientId>%s</ingredientId><stage>MASH</stage>
                <quantity>20</quantity><unit>KG</unit><lot>A1</lot></item></items></recipe>
                """.formatted(EQUIPMENT, MALT);
        var parsed = codec.parse(Format.BEERXML, xml);
        assertThat(parsed.unknownFields()).contains("style", "items[0].lot");
    }

    @Test
    void rejectsMalformedAndInvalidFormat() {
        assertThatThrownBy(() -> codec.parse(Format.BEERJSON, "{not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.parse(Format.BEERXML, "<recipe>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.format("yaml")).isInstanceOf(IllegalArgumentException.class);
    }
}
