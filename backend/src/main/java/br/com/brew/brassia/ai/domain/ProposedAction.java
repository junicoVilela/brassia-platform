package br.com.brew.brassia.ai.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * As únicas ações que o copiloto pode propor (AIA-003).
 *
 * <p><strong>Esta enum é a allowlist, e ser fechada é a proteção.</strong> Até aqui nenhuma chamada de IA
 * tinha campo de comando: o schema da resposta com fonte e o da avaliação foram construídos sem ele, de
 * propósito. Esta história abre esse campo — e o abre para um conjunto nomeado, não para texto livre. Um
 * modelo que devolva qualquer coisa fora daqui não produz proposta: produz erro de contrato, e a resposta é
 * recusada inteira pelo gateway.
 *
 * <p><strong>Cada ação carrega a permissão do comando de verdade</strong>, e é ela que será exigida de quem
 * confirmar. Não é a permissão de propor, não é a de quem pediu a análise: é a do comando que a proposta
 * pretende disparar. Sem isso, "propor" viraria um caminho lateral para fazer o que a pessoa não pode fazer
 * pela porta da frente.
 *
 * <p>Os parâmetros também são fechados. Um conjunto de chaves exigidas e nada além delas: parâmetro
 * inesperado é sinal de que o modelo entendeu a ação de outra forma, e adivinhar o que ele quis dizer num
 * comando que altera custo ou qualidade não é um risco que valha correr.
 */
public enum ProposedAction {

    /**
     * Fechar o custo de um lote.
     *
     * <p>Faz sentido como proposta porque o custeio sabe quando o custo está completo e ninguém repara: o
     * lote termina, as parcelas entram, e o número fica derivado para sempre porque fechar é um ato que
     * alguém precisa lembrar de praticar.
     */
    CLOSE_BATCH_COST("costing.cost.close", Set.of("batchId"),
            "Fechar o custo do lote", "/costing/batches", true),

    /**
     * Abrir não conformidade.
     *
     * <p>A avaliação de lote encontra o caso típico: medição fora da especificação sem NC aberta. O que a
     * qualidade decide é se aquilo é não conformidade — a proposta só aponta a lacuna.
     */
    OPEN_NON_CONFORMITY("quality.nc.manage", Set.of("batchId", "title", "severity"),
            "Abrir não conformidade para o lote", "/quality/non-conformities", false),

    /**
     * Programar ciclo de limpeza.
     *
     * <p>Parâmetro de equipamento e de POP, nunca de concentração ou de tempo: o procedimento é que dita
     * isso, e inventar parâmetro químico é justamente o que o sistema proíbe.
     */
    SCHEDULE_CLEANING_CYCLE("sanitation.cycle.execute", Set.of("equipmentId", "procedureCode"),
            "Iniciar ciclo de limpeza do equipamento", "/sanitation/cycles", true);

    private final String requiredPermission;
    private final Set<String> requiredParameters;
    private final String label;
    private final String executionRoute;
    private final boolean executedOnConfirm;

    ProposedAction(String requiredPermission, Set<String> requiredParameters, String label,
            String executionRoute, boolean executedOnConfirm) {
        this.requiredPermission = requiredPermission;
        this.requiredParameters = requiredParameters;
        this.label = label;
        this.executionRoute = executionRoute;
        this.executedOnConfirm = executedOnConfirm;
    }

    /**
     * Se confirmar <strong>executa</strong> o comando, ou apenas registra a decisão (DEB-AIA-002).
     *
     * <p>Existe para que a tela diga a verdade. Enquanto nada executava, "confirmar registra a decisão e
     * leva ao comando" era exato; para as ações que passaram a executar, deixaria a pessoa consentir com
     * uma coisa e outra acontecer — o oposto do que a confirmação humana existe para garantir.
     */
    public boolean executedOnConfirm() {
        return executedOnConfirm;
    }

    /** A permissão exigida de quem confirmar — a do comando, não a de propor. */
    public String requiredPermission() {
        return requiredPermission;
    }

    public Set<String> requiredParameters() {
        return requiredParameters;
    }

    public String label() {
        return label;
    }

    /**
     * Onde o comando vive de fato.
     *
     * <p>O aceite entrega este destino em vez de executar. Nenhum módulo publica porta de comando hoje — só
     * consultas —, e criar essas portas em custeio, qualidade e sanitização pertence às histórias deles.
     * Registrado como pendência no STATUS da sprint.
     */
    public String executionRoute() {
        return executionRoute;
    }

    /**
     * Valida os parâmetros: exatamente as chaves exigidas, nenhuma faltando e nenhuma sobrando.
     *
     * @throws IllegalArgumentException com a lista do que está errado
     */
    public void validate(Map<String, String> parameters) {
        var problems = new java.util.ArrayList<String>();
        for (var required : requiredParameters) {
            var value = parameters == null ? null : parameters.get(required);
            if (value == null || value.isBlank()) {
                problems.add("falta o parâmetro " + required);
            }
        }
        if (parameters != null) {
            for (var key : parameters.keySet()) {
                if (!requiredParameters.contains(key)) {
                    problems.add("parâmetro inesperado: " + key);
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "proposta de %s inválida: %s".formatted(name(), String.join("; ", problems)));
        }
    }

    /** Os nomes das ações, para montar o prompt sem repetir a lista em texto. */
    public static List<String> names() {
        return java.util.Arrays.stream(values()).map(Enum::name).toList();
    }
}
