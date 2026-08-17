package br.com.brew.brassia.brewery;

import java.util.UUID;

/**
 * A moeda em que a cervejaria opera (DEB-SAL-001).
 *
 * <p><strong>Publicada por quem tem o dado.</strong> A moeda já existia em
 * {@code brewery_operational_preferences} desde a Sprint 01 — o que faltava era ela sair do módulo. Quem
 * precisa dela é o custeio, que guardava {@code BigDecimal} nu: enquanto a casa opera numa moeda só nada
 * quebra, mas a primeira exportação soma real com dólar sem que nada reclame, e o erro aparece no
 * fechamento do mês, longe da causa.
 *
 * <p><strong>Nunca devolve vazio, e isso é decisão.</strong> A linha de preferências nasce
 * preguiçosamente — só quando alguém abre a tela —, e o próprio módulo já responde com
 * {@code OperationalPreferences.defaults} enquanto ela não existe. Se esta porta devolvesse vazio, uma
 * cervejaria que nunca abriu aquela tela ficaria sem custo de lote, e a resposta ao "quanto custou esta
 * brassa?" viraria um erro de configuração. <strong>O padrão não é um palpite do custeio: é o mesmo
 * padrão que o módulo dono aplica</strong> — e aqui ele é só lido, sem gravar linha nenhuma.
 *
 * <p><strong>Por que uma porta, e não um campo em {@code BreweryRef}.</strong> Aquele registro é a
 * identidade da cervejaria, usada pelo módulo de segurança para resolver a sessão. Pendurar moeda ali
 * faria a autenticação carregar preferência operacional para responder quem é quem.
 */
public interface BreweryCurrencyLookup {

    /** O código ISO da moeda configurada, ou o padrão da plataforma quando ainda não há preferências. */
    String currencyOf(UUID breweryId);
}
