package br.com.brew.brassia.container.domain;

/** O estado físico, que é diferente do operacional: um keg avariado continua existindo. */
public enum ContainerCondition {
    GOOD,
    /** Amassado, com vazamento, com válvula ruim — recuperável. */
    DAMAGED,
    /** Sem recuperação. Não se enche, e o destino é a baixa. */
    CONDEMNED
}
