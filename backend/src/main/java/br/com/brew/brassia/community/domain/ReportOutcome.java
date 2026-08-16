package br.com.brew.brassia.community.domain;

/**
 * Como a denúncia terminou (COM-005).
 *
 * <p>Os dois desfechos ficam registrados, e nenhum apaga a denúncia: quem revisa precisa poder ser
 * revisado, e uma denúncia improcedente apagada faria o mesmo caso voltar do zero.
 */
public enum ReportOutcome {

    /** A denúncia procede. A ação sobre o conteúdo é ato separado, e não consequência automática. */
    UPHELD,

    /** Não procede. A publicação segue como está. */
    DISMISSED
}
