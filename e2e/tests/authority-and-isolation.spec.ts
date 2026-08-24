import { type Browser, type Page } from '@playwright/test';
import {
  ADMIN,
  OPERADOR,
  VIZINHA,
  csrfHeaders,
  expect,
  get,
  login,
  seedBatch,
  test,
} from './support';

/**
 * As duas linhas que o roteiro de homologação chama de "as que costumam faltar por não serem o fluxo":
 * uma operação sem permissão, e a tentativa de outra cervejaria.
 *
 * <p><strong>Elas faltavam por uma causa, e não por esquecimento.</strong> O backend tem 167 asserções de
 * 403 e 26 testes de isolamento — a regra nunca esteve em dúvida. O que não existia era **alguém para
 * logar**: as duas contas do perfil `local` estavam no mesmo grupo `ADMINISTRATORS`, e o bootstrap criava
 * uma cervejaria só. Sem uma pessoa de pouca alçada e sem uma segunda casa, as duas linhas não tinham como
 * ser encenadas em ambiente nenhum, e sobravam para a homologação todo release.
 *
 * <p>Cada conta de bootstrap varia <strong>um eixo só</strong>. O operador é a mesma cervejaria com
 * permissões diferentes; a vizinha é a mesma alçada em cervejaria diferente. Uma conta que variasse os
 * dois provaria menos: ao levar 403, não se saberia se foi por permissão ou por casa errada — e são
 * recusas diferentes, com correções diferentes.
 */
test.describe('alçada e isolamento', () => {
  test('a alçada mostra o botão, a falta dela esconde — e a API recusa mesmo sem botão', async ({
    page,
    browser,
  }) => {
    // --- 1. Um lote para custear, semeado por quem pode ---
    // O operador não semeia: ele não cria receita nem ordem, que é o ponto de ele existir. E o lote não
    // pode vir "de alguma execução anterior" — a suíte compartilha banco, e um teste que depende do que
    // outro deixou passa sozinho e falha na fila, ou pior, o contrário.
    // --- 2. Quem TEM a alçada vê o botão ---
    // Esta metade não é decoração: "o botão não está lá" só significa "falta alçada" se alguém o vir
    // presente. Sem ela, um botão renomeado, movido ou quebrado deixaria a outra metade verde para
    // sempre, afirmando uma permissão que ninguém mais estaria verificando.
    const { batchId, codigo } = await loteComBotaoDeFechar(browser);

    // --- 3. O contraponto do outro lado: o operador LÊ ---
    // Uma tela em branco não distingue "não tenho alçada" de "a tela quebrou", e um grupo sem permissão
    // nenhuma faria as duas parecerem a mesma coisa. É a leitura funcionando que dá sentido à ausência.
    await login(page, OPERADOR);
    await abreOCustoDoLote(page, batchId, codigo);

    // --- 4. A tela não oferece o que ele não pode fazer ---
    await expect(page.getByRole('button', { name: 'Fechar custo' })).toHaveCount(0);

    // --- 5. E a porta continua fechada quando ninguém clica no botão ---
    // Esconder o botão é cortesia com quem opera, e não autorização: quem chama a API direto não passa
    // por tela nenhuma. Se a regra morasse só no `@if` do template, este POST entraria — e a diferença
    // entre as duas coisas é a diferença entre uma interface arrumada e um sistema seguro.
    const recusa = await page.request.post(`/api/v1/costing/batches/${batchId}/close`, {
      headers: await csrfHeaders(page),
      data: { note: 'tentativa sem alçada' },
    });
    expect(recusa.status()).toBe(403);

    // Problem Details (RFC 9457), que é o que o roteiro cobra: o corpo diz qual regra recusou.
    const problema = await recusa.json();
    expect(problema.code).toBe('forbidden');
    expect(problema.detail).toBe('Você não tem permissão para esta operação.');
    expect(problema.traceId, 'a recusa é rastreável até o log').toBeTruthy();

    // E não fechou nada: a recusa é sobre o efeito, e não sobre o código de resposta.
    const depois = await get(page, '/api/v1/costing/batch-costs');
    expect(
      depois.some((c: { batchId: string }) => c.batchId === batchId),
      'o custo recusado não foi fechado por baixo',
    ).toBe(false);
  });

  test('a cervejaria vizinha não enxerga o lote desta casa, e enxerga o próprio', async ({
    page,
    browser,
  }) => {
    // --- 1. Um lote nesta casa, visto por quem é daqui ---
    await login(page, ADMIN);
    const { batchId } = await seedBatch(page);
    const codigo = await codigoDoLote(page, batchId);

    await page.goto('/production/batches');
    await page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));
    await expect(page.getByText(codigo, { exact: false }).first()).toBeVisible();

    // --- 2. A vizinha, em sessão própria ---
    // Contexto próprio, e não a mesma sessão com outro cabeçalho: o isolamento que se quer provar é o de
    // quem entra pela porta, com o cookie que o login dela devolveu.
    const contexto = await browser.newContext();
    const vizinha = await contexto.newPage();
    try {
      await login(vizinha, VIZINHA);

      // A associação dela é ESCOPADA: ela alcança uma casa, e não é esta. Associação global — cervejaria
      // nula, como a do admin — daria acesso a todas, e a conta que deveria demonstrar o isolamento
      // passaria a demonstrar o oposto, com o teste verde.
      const sessao = await get(vizinha, '/api/v1/security/session');
      expect(sessao.accessibleBreweries).toHaveLength(1);
      expect(sessao.activeBrewery.code).toBe('VIZINHA');

      // --- 3. A tela dela funciona, e o lote daqui não está nela ---
      await vizinha.goto('/production/batches');
      await vizinha.waitForResponse(r => r.url().includes('/api/v1/production/batches'));
      await expect(vizinha.getByRole('heading', { name: 'Lotes de produção' })).toBeVisible();
      await expect(vizinha.getByText(codigo, { exact: false })).toHaveCount(0);

      // Nem pelo endereço direto, que é o caminho de quem tem o id e tenta assim mesmo.
      //
      // A asserção é de INDISTINGUIBILIDADE, e não sobre um código específico: o lote da outra casa
      // responde exatamente como um id sorteado que não existe em lugar nenhum. É essa igualdade que
      // fecha o vazamento — um 403 onde o inexistente dá 404, ou uma mensagem diferente, confirmaria a
      // existência do lote para quem só tem o id. Comparar as duas respostas prova isso sem depender de
      // qual código o backend escolheu, que é decisão dele e pode mudar.
      const alheio = await vizinha.request.get(`/api/v1/production/batches/${batchId}`);
      const inexistente = await vizinha.request.get(
        `/api/v1/production/batches/${crypto.randomUUID()}`,
      );
      expect(alheio.status(), 'o lote de outra casa responde como um que não existe').toBe(
        inexistente.status(),
      );
      const corpoAlheio = await alheio.json();
      const corpoInexistente = await inexistente.json();
      expect(corpoAlheio.code).toBe(corpoInexistente.code);
      expect(corpoAlheio.detail).toBe(corpoInexistente.detail);
      // E o corpo não carrega nada do lote — nem o código, que é o que a tela mostraria.
      expect(JSON.stringify(corpoAlheio)).not.toContain(codigo);

      // --- 4. O contraponto: ela enxerga o LOTE DELA ---
      // Sem isto, uma tela quebrada, uma sessão sem cervejaria ativa ou uma listagem que devolvesse vazio
      // para todo mundo passariam neste teste — e passariam parecendo isolamento.
      const dela = await seedBatch(vizinha);
      const codigoDela = await codigoDoLote(vizinha, dela.batchId);
      expect(codigoDela, 'os dois lotes são objetos diferentes').not.toBe(codigo);

      await vizinha.goto('/production/batches');
      await vizinha.waitForResponse(r => r.url().includes('/api/v1/production/batches'));
      await expect(vizinha.getByText(codigoDela, { exact: false }).first()).toBeVisible();
      // E o daqui continua fora, agora com a lista comprovadamente cheia.
      await expect(vizinha.getByText(codigo, { exact: false })).toHaveCount(0);
    } finally {
      await contexto.close();
    }
  });
});

/**
 * Semeia um lote como admin e confere, na sessão dele, que o botão de fechar custo está lá.
 *
 * <p>Vive em contexto próprio, aberto e fechado aqui dentro: o teste continua na sessão do operador, e
 * duas sessões no mesmo contexto compartilhariam cookie — a segunda derrubaria a primeira.
 */
async function loteComBotaoDeFechar(
  browser: Browser,
): Promise<{ batchId: string; codigo: string }> {
  const contexto = await browser.newContext();
  try {
    const admin = await contexto.newPage();
    await login(admin, ADMIN);
    const { batchId } = await seedBatch(admin);
    const codigo = await codigoDoLote(admin, batchId);
    await abreOCustoDoLote(admin, batchId, codigo);
    await expect(admin.getByRole('button', { name: 'Fechar custo' })).toBeVisible();
    return { batchId, codigo };
  } finally {
    await contexto.close();
  }
}

/** O código do lote, que é o que a tela mostra — o id não aparece em lugar nenhum da interface. */
async function codigoDoLote(page: Page, batchId: string): Promise<string> {
  const lotes = await get(page, '/api/v1/production/batches?page=0&size=100');
  const lote = lotes.content.find((b: { id: string }) => b.id === batchId);
  expect(lote, 'o lote semeado deve aparecer na listagem de quem o semeou').toBeTruthy();
  return lote.code;
}

/**
 * Abre o custo de um lote pela tela: a página nasce sem lote escolhido, e a escolha é um clique na
 * lista — não há parâmetro de endereço que a substitua.
 */
async function abreOCustoDoLote(page: Page, batchId: string, codigo: string): Promise<void> {
  await page.goto('/costing/batches');
  await expect(page.getByRole('heading', { name: 'Custo do lote' })).toBeVisible();
  // O custo do lote chega numa segunda requisição, disparada pelo clique. Sem esperá-la, a ausência do
  // botão de fechar seria apenas a tela ainda não ter carregado — e o teste passaria por lentidão em vez
  // de por permissão, que é o pior jeito de um teste de autorização passar.
  const custo = page.waitForResponse(r => r.url().includes(`/api/v1/costing/batches/${batchId}`));
  await page.getByRole('button').filter({ hasText: codigo }).first().click();
  expect((await custo).status()).toBe(200);
}
