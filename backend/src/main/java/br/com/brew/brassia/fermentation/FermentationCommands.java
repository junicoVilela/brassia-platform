package br.com.brew.brassia.fermentation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Porta de comando publicada da fermentação (FER-002).
 *
 * <p><strong>É a primeira porta de comando do projeto, e a ausência dela era um débito com três nomes.</strong>
 * Até aqui os módulos só publicavam consultas: qualquer módulo podia <em>ler</em> o lote de outro, nenhum
 * podia <em>pedir</em> nada a outro. O efeito prático estava registrado em `DEB-INT-001` (a leitura do
 * sensor não alimentava a curva de fermentação), e o mesmo obstáculo aparece em `DEB-AIA-002` para
 * `costing`, `quality` e `sanitation`.
 *
 * <p><strong>Deliberadamente estreita.</strong> Publica um comando, não o módulo. Quem chama não planeja
 * agenda, não colhe levedura e não avalia estabilidade de FG — essas operações nascem de decisão humana com
 * ator, alçada e auditoria, e expô-las a chamada entre módulos criaria caminhos onde alguém age sem que se
 * saiba quem agiu. Registrar telemetria é o oposto: não tem ator humano, é a máquina relatando o que mediu.
 *
 * <p><strong>Não audita, e isso é escolha.</strong> Um dispositivo de 30 segundos gera 2.880 leituras por
 * dia; auditar cada uma encheria a trilha de ruído até esconder a liberação de lote e a alteração de custo
 * que ela existe para guardar — o mesmo raciocínio que já vale na ingestão do sensor. O registro da leitura
 * é a própria leitura: imutável, com instante de medição e origem gravados.
 */
public interface FermentationCommands {

    /**
     * Registra uma leitura de origem SENSOR no lote.
     *
     * <p><strong>Idempotente pela chave natural</strong> {@code (lote, grandeza, origem, instante)}: um
     * gateway que reentrega a mesma medição não duplica ponto na curva. Isso vem da restrição única da
     * tabela, não de uma consulta prévia — a diferença importa porque a janela entre perguntar e escrever é
     * exatamente onde cai o reenvio em milissegundos.
     *
     * @param kind grandeza no vocabulário da fermentação ({@code DENSITY}, {@code TEMPERATURE},
     *             {@code PRESSURE}, {@code PH}). Quem chama traduz do seu vocabulário para este; a
     *             fermentação não conhece o do sensor, e é por isso que as duas enums seguem separadas.
     * @throws IllegalArgumentException lote inexistente, grandeza desconhecida ou unidade incompatível com
     *             a grandeza
     */
    Recorded recordSensorReading(SensorReading reading);

    /** Uma medição vinda de dispositivo. Sem ator: não há humano nesta operação. */
    record SensorReading(UUID breweryId, UUID batchId, String kind, BigDecimal value, String unit,
            Instant measuredAt) {}

    /**
     * @param created {@code false} quando a leitura já existia — reentrega, não erro
     * @param valid   {@code false} quando o valor está fora da faixa plausível da grandeza. A leitura é
     *                gravada assim mesmo: um buraco na curva é indistinguível de "não mediu", e um ponto
     *                sinalizado conta duas verdades — que o dispositivo reportou e que não se deve
     *                acreditar no número.
     */
    record Recorded(UUID readingId, boolean created, boolean valid) {}
}
