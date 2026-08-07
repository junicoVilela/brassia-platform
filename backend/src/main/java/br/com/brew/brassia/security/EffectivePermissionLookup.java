package br.com.brew.brassia.security;

import java.util.Set;
import java.util.UUID;

/**
 * As permissões que um usuário tem <strong>agora</strong> numa cervejaria (RPT-003).
 *
 * <p>Existe para execução fora de sessão: um relatório programado roda sem ninguém logado, e ainda
 * assim precisa rodar com a alçada de alguém. Congelar as permissões do dono no momento em que ele
 * salvou a definição criaria um privilégio que sobrevive à demissão dele — a consulta é do agora, e
 * é por isso que ela é uma consulta e não uma cópia.
 *
 * <p>Também não serve para "rodar como sistema": um relatório que roda com privilégio implícito
 * entrega dados que ninguém autorizou a entregar.
 */
public interface EffectivePermissionLookup {

    /**
     * Permissões efetivas do usuário na cervejaria, resolvidas neste instante.
     *
     * <p>Conjunto vazio quando o usuário não existe mais, foi desativado ou perdeu o acesso àquela
     * cervejaria — e vazio é resposta, não erro: quem chama tem de decidir o que fazer com um dono
     * que perdeu a alçada.
     */
    Set<String> permissionsOf(UUID userId, UUID breweryId);
}
