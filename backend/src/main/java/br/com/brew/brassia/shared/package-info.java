/**
 * Capacidades técnicas mínimas compartilhadas entre módulos.
 *
 * <p>Declarado como módulo {@code OPEN} do Spring Modulith: é um módulo de
 * apoio técnico cujos tipos podem ser usados diretamente pelos demais módulos,
 * sem exigir porta ou interface nomeada. Não deve conter regra de negócio de
 * nenhum domínio.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Compartilhado")
package br.com.brew.brassia.shared;
