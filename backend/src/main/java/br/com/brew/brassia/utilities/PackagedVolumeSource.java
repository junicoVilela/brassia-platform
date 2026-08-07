package br.com.brew.brassia.utilities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * O denominador do indicador (UTL-001): quantos litros foram envasados no período.
 *
 * <p><strong>A porta é das utilidades e o envase a implementa</strong>, e não o contrário. Se o
 * indicador fosse buscar o volume numa consulta publicada do envase, utilidades dependeria de
 * envase — que depende de sanitização, que implementa a outra porta daqui. O ciclo apareceria no
 * {@code ModularityTest} como apareceu no recall. Invertendo, utilidades não depende de ninguém.
 *
 * <p>O divisor é o <strong>envasado</strong>, não o produzido: o indicador de sustentabilidade que
 * a norma cobra é por litro que saiu da fábrica, e dividir pelo que ficou no tanque melhoraria o
 * número sem melhorar a cervejaria.
 */
public interface PackagedVolumeSource {

    /** Litros envasados no período; zero quando não houve envase. */
    BigDecimal packagedLitersIn(UUID breweryId, Instant from, Instant to);
}
