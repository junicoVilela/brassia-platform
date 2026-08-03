package br.com.brew.brassia.metrology.domain;

/**
 * Aptidão do instrumento para medir — sempre <strong>derivada</strong> do estado cadastral e da
 * última calibração, nunca informada. Marcar "apto" na mão é como digitar o volume de um plano de
 * envase: cria um número que pode divergir da realidade que ele deveria descrever.
 */
public enum Fitness {
    /** Calibração aprovada e dentro da validade. */
    FIT,
    /** Última calibração aprovada, mas com validade expirada. */
    EXPIRED,
    /** Nunca calibrado: não há evidência nenhuma sobre o que ele mede. */
    UNCALIBRATED,
    /** Última calibração reprovou. Aprovação antiga ainda no prazo não vale — o instrumento falhou. */
    REJECTED,
    /** Tirado de circulação por decisão humana, com motivo. */
    BLOCKED,
    /** Baixado do parque; estado terminal. */
    RETIRED;

    public boolean usable() {
        return this == FIT;
    }
}
