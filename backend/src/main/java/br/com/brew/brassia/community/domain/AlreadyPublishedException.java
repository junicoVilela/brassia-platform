package br.com.brew.brassia.community.domain;

/**
 * Esta versão da receita já está na biblioteca (COM-001).
 *
 * <p>A garantia é o índice único {@code ux_community_recipe_version}; esta exceção existe para a resposta
 * dizer que já existe, em vez de um erro de banco. Duas entradas da mesma versão concorreriam na busca —
 * possivelmente com títulos diferentes —, e ninguém saberia qual é a boa.
 */
public class AlreadyPublishedException extends RuntimeException {

    public AlreadyPublishedException(long version) {
        super("a versão " + version + " desta receita já está publicada");
    }
}
