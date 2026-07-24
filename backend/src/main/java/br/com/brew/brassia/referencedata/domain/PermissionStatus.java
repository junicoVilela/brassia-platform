package br.com.brew.brassia.referencedata.domain;

/**
 * Estado de permissão de uso do conteúdo de uma fonte (gate de licença).
 * Somente {@code LIMITED_PERMISSION} e {@code GRANTED} autorizam publicação;
 * {@code LIMITED_PERMISSION} restringe o conteúdo publicado a metadados permitidos
 * (código, nome, faixas e impressão geral) — ver docs/42_STYLE_GUIDELINES_LICENSING.md.
 */
public enum PermissionStatus {
    UNKNOWN,
    PENDING,
    LIMITED_PERMISSION,
    GRANTED,
    DENIED;

    public boolean allowsPublish() {
        return this == LIMITED_PERMISSION || this == GRANTED;
    }
}
