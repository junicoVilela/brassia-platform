package br.com.brew.brassia.container.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Um vasilhame retornável, com identidade própria e um ciclo que se repete por anos (CON-001).
 *
 * <p><strong>A identidade é do contêiner, e não da etiqueta.</strong> Um keg reetiquetado continua sendo
 * o mesmo keg, com a mesma inspeção e o mesmo histórico — é por isso que o identificador é objeto
 * separado, e não um campo aqui dentro. A alternativa (o código como chave) faria trocar um adesivo
 * descolado apagar cinco anos de vida do vasilhame.
 *
 * <p><strong>O que voltou do cliente não está pronto.</strong> {@code RETURNED} e {@code EMPTY} são
 * estados diferentes de propósito: o keg que chegou de volta está sujo até que alguém <em>diga</em> que
 * está limpo. Derivar isso da chegada — "voltou, logo está disponível" — encheria com cerveja um
 * vasilhame que ninguém lavou, e o problema apareceria na boca do cliente.
 */
public final class Container {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final ContainerKind kind;
    private final BigDecimal nominalCapacityLiters;
    private Ownership ownership;
    private ContainerCondition condition;
    private ContainerState state;
    private ContainerInspection inspection;
    private Instant retiredAt;
    private String retirementReason;
    /**
     * A versão com que este vasilhame foi lido do banco (DEB-CON-003).
     *
     * <p><strong>Não é dado de negócio</strong>, e por isso não tem regra: é o que permite à escrita
     * recusar gravar por cima de quem alterou o mesmo keg no meio do caminho. A coluna já existia e era
     * incrementada, mas nunca conferida — condenar e despachar ao mesmo tempo devolvia ao depósito um
     * vasilhame que estava no caminhão, sem erro nenhum.
     */
    private final long version;

    private Container(UUID id, UUID breweryId, String code, ContainerKind kind,
            BigDecimal nominalCapacityLiters, Ownership ownership, ContainerCondition condition,
            ContainerState state, ContainerInspection inspection, Instant retiredAt,
            String retirementReason, long version) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.code = requireCode(code);
        this.kind = Objects.requireNonNull(kind, "tipo");
        this.nominalCapacityLiters = requirePositive(nominalCapacityLiters);
        this.ownership = Objects.requireNonNull(ownership, "propriedade");
        this.version = version;
        this.condition = Objects.requireNonNull(condition);
        this.state = Objects.requireNonNull(state);
        this.inspection = inspection;
        this.retiredAt = retiredAt;
        this.retirementReason = retirementReason;
    }

    /** Nasce vazio e em boa condição — mas <strong>sem inspeção</strong>, que é ato de alguém. */
    public static Container register(UUID id, UUID breweryId, String code, ContainerKind kind,
            BigDecimal nominalCapacityLiters, Ownership ownership) {
        return new Container(id, breweryId, code, kind, nominalCapacityLiters, ownership,
                ContainerCondition.GOOD, ContainerState.EMPTY, null, null, null, 0L);
    }

    public static Container reconstitute(UUID id, UUID breweryId, String code, ContainerKind kind,
            BigDecimal nominalCapacityLiters, Ownership ownership, ContainerCondition condition,
            ContainerState state, ContainerInspection inspection, Instant retiredAt,
            String retirementReason, long version) {
        return new Container(id, breweryId, code, kind, nominalCapacityLiters, ownership, condition,
                state, inspection, retiredAt, retirementReason, version);
    }

    // --- inspeção ---

    /** Inspecionar não muda estado nem condição: ele diz que o vasilhame foi olhado, e até quando vale. */
    public void inspect(ContainerInspection inspection) {
        requireAlive();
        this.inspection = Objects.requireNonNull(inspection);
    }

    /**
     * A inspeção em dia é <strong>pré-requisito de encher</strong>, e não um alerta.
     *
     * <p>Um contêiner sem inspeção nenhuma também não passa: "nunca foi inspecionado" é pior que
     * "venceu", e tratar a ausência como aprovação deixaria toda a frota nova fora de qualquer controle.
     */
    public boolean inspectionValidAt(Instant moment) {
        return inspection != null && inspection.validAt(moment);
    }

    // --- ciclo ---

    /**
     * Diz se dá para encher, e explode com o motivo quando não dá.
     *
     * <p>Chamado antes do enchimento (CON-002) e pela tela, que precisa do motivo para mostrar.
     */
    public void requireFillableAt(Instant moment) {
        requireAlive();
        if (condition == ContainerCondition.CONDEMNED) {
            throw ContainerNotFillableException.condemned();
        }
        if (condition == ContainerCondition.DAMAGED) {
            throw ContainerNotFillableException.damaged();
        }
        if (state != ContainerState.EMPTY) {
            throw ContainerNotFillableException.notReady(state);
        }
        if (!inspectionValidAt(moment)) {
            throw ContainerNotFillableException.inspectionExpired();
        }
    }

    public boolean fillableAt(Instant moment) {
        try {
            requireFillableAt(moment);
            return true;
        } catch (ContainerNotFillableException | IllegalStateException recusa) {
            return false;
        }
    }

    public void fill(Instant moment) {
        requireFillableAt(moment);
        this.state = ContainerState.FILLED;
    }

    /**
     * O conteúdo saiu do vasilhame (DEB-CON-003 #2).
     *
     * <p><strong>Esvaziar na fábrica move o keg; esvaziar na rua, não.</strong> Um keg que foi enchido e
     * teve o conteúdo baixado sem sair daqui — descarte de lote, envase interrompido, transferência —
     * ficava preso em {@code FILLED}: não podia ser enchido de novo (o estado não é {@code EMPTY}) nem
     * liberado ({@code releaseToStock} exige {@code RETURNED}). O único jeito de tirá-lo de lá era forjar
     * uma viagem inteira ao cliente, que é o que o próprio {@code ContainerFillIT} fazia — e isso fabrica
     * histórico de posição que nunca aconteceu.
     *
     * <p><strong>Vai para {@code RETURNED}, e não para {@code EMPTY}.</strong> Um vasilhame que teve
     * cerveja dentro está sujo, tenha ele viajado ou não. Mandá-lo direto para a enchedeira é exatamente
     * o que a distinção entre os dois estados existe para impedir.
     *
     * <p>Nos outros estados não faz nada de propósito: o keg esvaziado no bar continua no bar, e mover o
     * inventário por causa disso diria que ele voltou quando ninguém o buscou.
     *
     * <p><strong>E não recusa o baixado</strong>, ao contrário das outras mutações. Um keg pode ser
     * baixado ou dado como perdido estando cheio — {@code retire} só barra quem está na rua, e
     * {@code declareLost} não barra ninguém —, e o conteúdo dele continua precisando ser fechado no
     * histórico. Recusar aqui desfaria o fechamento do período junto com a transação, e o vínculo com
     * aquele lote ficaria aberto para sempre. O estado terminal já basta como guarda: {@code RETIRED}
     * nunca é {@code FILLED}, então o vasilhame baixado não se move de qualquer forma.
     */
    public void emptyContents() {
        if (state == ContainerState.FILLED) {
            this.state = ContainerState.RETURNED;
        }
    }

    public void dispatch() {
        transition(ContainerState.FILLED, ContainerState.IN_TRANSIT);
    }

    public void deliver() {
        transition(ContainerState.IN_TRANSIT, ContainerState.AT_CUSTOMER);
    }

    /** Coletado no cliente: volta para casa <strong>sujo</strong>, e não disponível. */
    public void collect() {
        transition(ContainerState.AT_CUSTOMER, ContainerState.RETURNED);
    }

    /**
     * Alguém declara que o vasilhame está limpo e pronto.
     *
     * <p>É <strong>ato explícito</strong>, como a liberação do lote pela qualidade: derivar a limpeza da
     * chegada tornaria "voltou" e "está pronto" a mesma coisa, e elas não são.
     */
    public void releaseToStock() {
        transition(ContainerState.RETURNED, ContainerState.EMPTY);
    }

    public void sendToMaintenance() {
        requireAlive();
        if (state == ContainerState.AT_CUSTOMER || state == ContainerState.IN_TRANSIT) {
            // Não se manda para a oficina o que está na rua: primeiro ele volta.
            throw new IllegalContainerTransitionException(state, ContainerState.IN_MAINTENANCE);
        }
        this.state = ContainerState.IN_MAINTENANCE;
    }

    /** Sai da manutenção consertado e <strong>vazio</strong>, e a condição volta a boa. */
    public void returnFromMaintenance() {
        transition(ContainerState.IN_MAINTENANCE, ContainerState.EMPTY);
        this.condition = ContainerCondition.GOOD;
    }

    // --- condição ---

    public void markDamaged() {
        requireAlive();
        this.condition = ContainerCondition.DAMAGED;
    }

    public void condemn() {
        requireAlive();
        this.condition = ContainerCondition.CONDEMNED;
    }

    // --- fim de vida ---

    /**
     * Baixa: terminal, com motivo, e <strong>não apaga nada</strong>.
     *
     * <p>Não se dá baixa no que está com o cliente ou na rua. O vasilhame que não voltou é perda, que é
     * outro fato e tem outro dono (CON-003) — tratá-los como o mesmo botão faria "sumiu" e "descartei"
     * virarem a mesma linha no inventário.
     */
    public void retire(String reason, Instant at) {
        requireAlive();
        if (state == ContainerState.AT_CUSTOMER || state == ContainerState.IN_TRANSIT) {
            throw new IllegalContainerTransitionException(state, ContainerState.RETIRED);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a baixa precisa de motivo");
        }
        this.state = ContainerState.RETIRED;
        this.retiredAt = Objects.requireNonNull(at);
        this.retirementReason = reason.trim();
    }

    /**
     * Baixa por PERDA: o vasilhame não volta mais, e sai do inventário onde quer que estivesse.
     *
     * <p>É a exceção deliberada à recusa de {@link #retire}, e existe pelo mesmo motivo que a recusa:
     * "sumiu" e "descartei" não podem ser o mesmo botão. Este <strong>é</strong> o botão do "sumiu" —
     * vem de um empréstimo aberto, com motivo e alçada crítica, e o motivo viaja junto para que o
     * inventário nunca precise adivinhar qual dos dois aconteceu.
     */
    public void declareLost(String reason, Instant at) {
        requireAlive();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a perda precisa de motivo");
        }
        this.state = ContainerState.RETIRED;
        this.retiredAt = Objects.requireNonNull(at);
        this.retirementReason = "perdido: " + reason.trim();
    }

    /**
     * O vasilhame dado como perdido reapareceu e volta ao inventário (DUV-CON-002).
     *
     * <p><strong>Volta como {@code RETURNED}, e não disponível.</strong> Ele passou meses fora de vista:
     * tratá-lo como pronto para encher seria confiar num vasilhame que ninguém olhou. Alguém precisa
     * higienizá-lo e liberá-lo, exatamente como o que volta do cliente.
     *
     * <p><strong>Só volta o que saiu por perda.</strong> Um contêiner descartado por avaria não
     * reaparece — ele foi para o ferro-velho, e permitir a volta faria "descartei" virar reversível, que
     * é justamente a distinção que a CON-003 construiu.
     *
     * <p><strong>A condição não volta a boa</strong> (DEB-CON-003 #3). Ela zerava para {@code GOOD}, e
     * com isso um keg avariado, perdido e reaparecido voltava como se alguém o tivesse consertado.
     * Ninguém consertou nada: ele estava no depósito de um cliente esse tempo todo. Quem conserta é a
     * oficina, e é {@code returnFromMaintenance} que registra isso.
     */
    public void recover(String reason, Instant at) {
        if (state != ContainerState.RETIRED || retirementReason == null
                || !retirementReason.startsWith("perdido:")) {
            throw new IllegalStateException(
                    "só volta ao inventário o vasilhame que saiu por perda");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a volta precisa de motivo");
        }
        this.state = ContainerState.RETURNED;
        this.retiredAt = null;
        // O motivo da baixa é preservado no histórico do empréstimo; aqui ele sai porque o vasilhame
        // deixou de estar baixado, e um contêiner ativo com motivo de baixa mente sobre o próprio estado.
        this.retirementReason = null;
    }

    /** A versão lida — o que a escrita confere para não gravar por cima de outra operação. */
    public long version() {
        return version;
    }

    public boolean isRetired() {
        return state == ContainerState.RETIRED;
    }

    public void changeOwnership(Ownership ownership) {
        requireAlive();
        this.ownership = Objects.requireNonNull(ownership);
    }

    private void transition(ContainerState from, ContainerState to) {
        requireAlive();
        if (state != from) {
            throw new IllegalContainerTransitionException(state, to);
        }
        this.state = to;
    }

    private void requireAlive() {
        if (state == ContainerState.RETIRED) {
            throw new ContainerRetiredException();
        }
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String code() {
        return code;
    }

    public ContainerKind kind() {
        return kind;
    }

    public BigDecimal nominalCapacityLiters() {
        return nominalCapacityLiters;
    }

    public Ownership ownership() {
        return ownership;
    }

    public ContainerCondition condition() {
        return condition;
    }

    public ContainerState state() {
        return state;
    }

    public Optional<ContainerInspection> inspection() {
        return Optional.ofNullable(inspection);
    }

    public Optional<Instant> retiredAt() {
        return Optional.ofNullable(retiredAt);
    }

    public Optional<String> retirementReason() {
        return Optional.ofNullable(retirementReason);
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("o contêiner precisa de um código");
        }
        return code.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("a capacidade nominal deve ser positiva");
        }
        return value;
    }
}
