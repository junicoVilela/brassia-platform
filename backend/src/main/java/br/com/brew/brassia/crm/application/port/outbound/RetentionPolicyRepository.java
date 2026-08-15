package br.com.brew.brassia.crm.application.port.outbound;

import br.com.brew.brassia.crm.domain.RetentionPolicy;
import java.util.UUID;

/**
 * A política de retenção da casa (CRM-001).
 *
 * <p>{@link #find} devolve {@code RetentionPolicy.none} quando não há linha, e não vazio: "a cervejaria
 * não decidiu" é um estado da política, não a ausência dela. Quem chama nunca precisa lembrar de tratar
 * nulo, e o comportamento seguro — nada expira — vira o padrão em vez de uma checagem esquecível.
 */
public interface RetentionPolicyRepository {

    RetentionPolicy find(UUID breweryId);

    void save(UUID breweryId, int daysAfterLastInteraction, UUID actorId);
}
