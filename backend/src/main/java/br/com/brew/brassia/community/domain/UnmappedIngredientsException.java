package br.com.brew.brassia.community.domain;

import java.util.List;

/**
 * O fork foi recusado porque faltam ingredientes no catálogo de quem copia (COM-003).
 *
 * <p><strong>Recusar inteiro, e não montar meia receita.</strong> O retrato público traz os ingredientes
 * pelo <em>nome</em> — o identificador é do catálogo do autor e nunca sai. Para virar receita aqui, cada
 * nome precisa encontrar um ingrediente da casa.
 *
 * <p>A alternativa seria criar a receita só com o que casou. Uma receita a que faltam três de oito
 * ingredientes não é uma receita incompleta: é uma <strong>receita errada</strong>, que alguém vai brassar
 * achando que é a do outro. Recusar com a lista do que falta é acionável; o meio-termo é silencioso.
 */
public class UnmappedIngredientsException extends RuntimeException {

    private final List<String> missing;

    public UnmappedIngredientsException(List<String> missing) {
        super("faltam ingredientes no seu catálogo: " + String.join(", ", missing));
        this.missing = List.copyOf(missing);
    }

    public List<String> missing() {
        return missing;
    }
}
