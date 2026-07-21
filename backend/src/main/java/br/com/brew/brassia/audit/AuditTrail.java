package br.com.brew.brassia.audit;

/**
 * Porta de auditoria usada pelos módulos para registrar comandos críticos.
 * A implementação garante persistência/registro append-only e mascaramento
 * de dados sensíveis.
 */
public interface AuditTrail {

    void record(AuditEvent event);
}
