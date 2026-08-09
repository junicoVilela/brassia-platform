package br.com.brew.brassia.sensor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O dispositivo e o que ele aceita receber (INT-001). */
class SensorDeviceTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T10:00:00Z");

    private static SensorDevice termometro() {
        return SensorDevice.register(CERVEJARIA, "TANK-01-TEMP", "Termômetro do tanque 1",
                Measure.TEMPERATURE, "C", null, Duration.ofMinutes(5), OPERADOR, AGORA);
    }

    @Test
    @DisplayName("código é normalizado para maiúscula: a etiqueta e o firmware escrevem diferente")
    void normalizaCodigo() {
        // Sem isso, "ispindel-01" e "ISPINDEL-01" viram dois cadastros, duas séries e nenhuma completa.
        var device = SensorDevice.register(CERVEJARIA, "  ispindel-01 ", "iSpindel", Measure.DENSITY,
                "sg", null, null, OPERADOR, AGORA);

        assertThat(device.code()).isEqualTo("ISPINDEL-01");
        assertThat(device.unit()).isEqualTo("SG");
        assertThat(device.status()).isEqualTo(DeviceStatus.ACTIVE);
    }

    @Test
    @DisplayName("código com caractere fora do alfabeto é recusado")
    void recusaCodigoInvalido() {
        assertThatThrownBy(() -> SensorDevice.register(CERVEJARIA, "tank 01", "x", Measure.TEMPERATURE,
                "C", null, null, OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SensorDevice.register(CERVEJARIA, "A", "x", Measure.TEMPERATURE,
                "C", null, null, OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("unidade incompatível com a grandeza é recusada no cadastro")
    void recusaUnidadeIncompativelNoCadastro() {
        assertThatThrownBy(() -> SensorDevice.register(CERVEJARIA, "TANK-01", "x", Measure.TEMPERATURE,
                "PSI", null, null, OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("intervalo esperado não pode ser zero nem negativo")
    void recusaIntervaloNaoPositivo() {
        assertThatThrownBy(() -> SensorDevice.register(CERVEJARIA, "TANK-01", "x", Measure.TEMPERATURE,
                "C", null, Duration.ZERO, OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("aceita a leitura que corresponde ao cadastro")
    void aceitaLeituraCorrespondente() {
        termometro().requireAccepts(Measure.TEMPERATURE, "c");
    }

    @Test
    @DisplayName("recusa grandeza divergente: erro de configuração não é medição ruim")
    void recusaGrandezaDivergente() {
        // Uma temperatura absurda é sinalizada e guardada porque instante e dispositivo são fatos. Uma
        // leitura que diz ser pressão vinda de um termômetro não descreve fato nenhum — guardá-la
        // sinalizada contaminaria a série com uma linha que ninguém sabe ler.
        assertThatThrownBy(() -> termometro().requireAccepts(Measure.PRESSURE, "PSI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mede TEMPERATURE");
    }

    @Test
    @DisplayName("recusa unidade divergente: firmware que passou a mandar Fahrenheit sem avisar")
    void recusaUnidadeDivergente() {
        // Este é o caso que motiva a unidade morar no cadastro e não na mensagem: converter silenciosamente
        // trocaria a escala da série histórica inteira sem nenhum sinal.
        assertThatThrownBy(() -> termometro().requireAccepts(Measure.TEMPERATURE, "F"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reporta em C");
    }

    @Test
    @DisplayName("dispositivo pausado não aceita leitura")
    void pausadoNaoAceita() {
        var pausado = termometro().changeStatusTo(DeviceStatus.PAUSED);

        assertThatThrownBy(() -> pausado.requireAccepts(Measure.TEMPERATURE, "C"))
                .isInstanceOf(InactiveDeviceException.class);
    }

    @Test
    @DisplayName("pausado volta a operar; revogado não")
    void revogadoETerminal() {
        var pausado = termometro().changeStatusTo(DeviceStatus.PAUSED);
        assertThat(pausado.changeStatusTo(DeviceStatus.ACTIVE).status()).isEqualTo(DeviceStatus.ACTIVE);

        var revogado = termometro().changeStatusTo(DeviceStatus.REVOKED);
        assertThatThrownBy(() -> revogado.changeStatusTo(DeviceStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revogado");
    }

    @Test
    @DisplayName("mudar para o estado atual é recusado em vez de virar no-op silencioso")
    void recusaTransicaoParaOMesmoEstado() {
        assertThatThrownBy(() -> termometro().changeStatusTo(DeviceStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
    }
}
