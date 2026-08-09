package br.com.brew.brassia.production.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/** Registra uma medição imutável no lote (PRD-003), com idempotência para a fila offline (PWA-002). */
public interface RecordMeasurementUseCase {
    Result handle(Command command);

    /**
     * @param clientRequestId identidade do apontamento, gerada NO APARELHO no instante do registro — não no
     *                        envio. É o que torna a repetição reconhecível: a fila offline reenvia até
     *                        receber confirmação, e uma chave gerada no envio seria diferente a cada
     *                        tentativa, criando duas medições da mesma leitura. Nulo quando o registro veio
     *                        direto da tela, com rede.
     */
    record Command(UUID actorId, UUID breweryId, UUID batchId, UUID stepId, String kind, BigDecimal value,
            String unit, BigDecimal temperatureC, String method, String source, String clientRequestId) {}

    /**
     * @param duplicate verdadeiro quando aquele apontamento já havia sido registrado. É resposta, não erro:
     *                  a fila que reenviou por não ter recebido a confirmação fez a coisa certa.
     */
    record Result(UUID id, boolean duplicate) {}
}
