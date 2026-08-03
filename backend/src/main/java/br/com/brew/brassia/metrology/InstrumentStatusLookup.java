package br.com.brew.brassia.metrology;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta publicada da aptidão de um instrumento (MTR-001), para outros módulos condicionarem
 * uma medição à evidência de calibração sem acessar as tabelas de metrologia.
 *
 * <p>É o "instrument status port" da sprint 11: quando QLT-001 for definir plano de controle, é
 * por aqui que ele pergunta se o instrumento serve ao ponto — e é aqui que "instrumento vencido
 * bloqueia ponto crítico" se torna verificável de fora do módulo.
 *
 * <p>A aptidão é derivada na data consultada, não armazenada: um instrumento apto hoje pode estar
 * vencido amanhã sem que ninguém tenha tocado no cadastro.
 */
public interface InstrumentStatusLookup {

    Optional<Status> status(UUID breweryId, UUID instrumentId, LocalDate on);

    /**
     * @param fitness         aptidão derivada: FIT, EXPIRED, UNCALIBRATED, REJECTED, BLOCKED ou RETIRED
     * @param criticalUse     designado para ponto crítico de controle
     * @param fitForCritical  serve para medir em ponto crítico na data consultada
     * @param calibrationDueOn vencimento da última calibração; ausente quando nunca foi calibrado
     * @param restriction     restrição do certificado quando aprovado com restrição; o consumidor
     *                        decide o que fazer com ela — o sistema não estreita a faixa sozinho
     */
    record Status(UUID instrumentId, String code, String name, String fitness, boolean criticalUse,
            boolean fitForCritical, LocalDate calibrationDueOn, String restriction) {}
}
