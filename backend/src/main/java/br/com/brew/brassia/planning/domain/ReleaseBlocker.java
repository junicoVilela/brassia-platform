package br.com.brew.brassia.planning.domain;

/** Um impedimento à liberação da OP (código estável + mensagem segura). */
public record ReleaseBlocker(String code, String message) {

    public static ReleaseBlocker notDraft() {
        return new ReleaseBlocker("not_draft", "A ordem não está em rascunho.");
    }

    public static ReleaseBlocker missingResponsible() {
        return new ReleaseBlocker("missing_responsible", "Informe o responsável pela liberação.");
    }

    public static ReleaseBlocker equipmentMissing() {
        return new ReleaseBlocker("equipment_missing", "O equipamento da ordem não está mais disponível.");
    }
}
