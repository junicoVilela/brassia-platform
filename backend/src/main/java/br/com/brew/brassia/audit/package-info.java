/**
 * Trilha de auditoria da plataforma.
 *
 * <p>Módulo de apoio: expõe a porta {@link br.com.brew.brassia.audit.AuditTrail}
 * e o valor {@link br.com.brew.brassia.audit.AuditEvent} para os demais módulos
 * registrarem comandos críticos. Eventos de auditoria são apenas acrescentados,
 * nunca apagados, e não podem conter dados sensíveis em claro.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Auditoria")
package br.com.brew.brassia.audit;
