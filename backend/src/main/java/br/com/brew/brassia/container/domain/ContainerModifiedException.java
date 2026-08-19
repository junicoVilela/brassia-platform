package br.com.brew.brassia.container.domain;

import java.util.UUID;

/**
 * Outra operação alterou este vasilhame entre a leitura e a escrita (DEB-CON-003).
 *
 * <p><strong>O que ela impede é perda silenciosa.</strong> A coluna `version` já era incrementada antes
 * desta exceção existir, mas nunca conferida: duas operações que lessem o mesmo keg em `FILLED` — uma
 * para despachá-lo, outra para condená-lo — gravavam uma por cima da outra sem erro nenhum, e a segunda
 * devolvia ao depósito um vasilhame que estava no caminhão.
 *
 * <p><strong>Recusar é melhor que resolver sozinho.</strong> Não há como o sistema saber qual das duas
 * intenções vale: quem condenou viu uma avaria, quem despachou viu a carga sair. Devolver o conflito faz
 * a pessoa reler o estado atual e decidir com o que é verdade agora — que é o que a versão otimista
 * promete ao operador no manual mínimo de operação.
 */
public class ContainerModifiedException extends RuntimeException {

    private final UUID containerId;

    public ContainerModifiedException(UUID containerId) {
        super("este vasilhame foi alterado por outra operação; recarregue e refaça");
        this.containerId = containerId;
    }

    public UUID containerId() {
        return containerId;
    }
}
