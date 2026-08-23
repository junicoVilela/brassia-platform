import { type Page } from '@playwright/test';
import { CONFERENTE, expect, get, login, seedBatch, test } from './support';

/**
 * Jornada da comunidade de ponta a ponta: publicar, esconder, compartilhar e ser denunciado.
 *
 * <p>Fecha o `DEB-COM-001`. A Sprint 18 foi entregue sem jornada nenhuma pela tela — era a única sprint
 * do projeto cuja costura entre frontend e backend nunca tinha sido atravessada por um teste. A ausência
 * não apareceu em relatório nenhum até a passagem de evidência do aceite, em 22/08.
 *
 * <p><strong>Por que aqui importa mais que em outros módulos.</strong> Este é o único lugar do sistema em
 * que dado de receita sai da cervejaria. O erro possível não é um número errado numa tela: é uma receita
 * privada aparecendo na vitrine, e isso não se desfaz depois que alguém leu. Quando a mesma ausência foi
 * fechada nas sprints 19 e 20, a jornada encontrou oito stores lendo o erro no nível errado — com o
 * backend verde o tempo todo, porque o defeito morava exatamente na costura que só o E2E atravessa.
 *
 * <p>O preparo da receita vai pela API: `seedBatch` já cria e publica uma receita, e clicar por aquele
 * formulário testaria assunto alheio. O que esta jornada faz <strong>na interface</strong> é o que só
 * existe de verdade se a pessoa enxergar: escolher a visibilidade, ver a privada ficar fora da vitrine,
 * abrir a pública, comentar, e ler a denúncia de outra pessoa sem descobrir quem a fez.
 */
test.describe('jornada da comunidade', () => {
  test('da receita privada à vitrine, com link, comentário e denúncia', async ({ page, browser }) => {
    await login(page);
    const sfx = Date.now().toString().slice(-8);
    const { recipeId } = await seedBatch(page);
    const titulo = `Receita da casa ${sfx}`;

    await page.goto('/community/library');
    await expect(page.getByRole('heading', { name: 'Minhas publicações' })).toBeVisible();

    // --- 1. Publicar como PRIVADA, pelo formulário ---
    // Nasce fechado: publicar é o ato menos frequente da tela, e o mais difícil de desfazer.
    await page.getByRole('button', { name: 'Publicar', exact: true }).click();
    await page.locator('#recipeId').fill(recipeId);
    await page.locator('#title').fill(titulo);
    await page.locator('#license').selectOption('CC_BY');
    await page.locator('#visibility').selectOption('PRIVATE');
    await page.getByRole('button', { name: 'Publicar' }).click();

    // Aparece na estante do autor — onde o título é texto, com a versão publicada ao lado.
    const naEstante = page.getByRole('listitem').filter({ hasText: titulo });
    await expect(naEstante).toBeVisible();
    await expect(naEstante).toContainText('versão 1');

    // --- 2. …e NÃO na vitrine. É o item de aceite que mais custa se estiver errado ---
    // Na vitrine o título é BOTÃO (abre a publicação); na estante é texto. A diferença de papel é o
    // que separa "está na minha lista" de "está exposta" sem depender de como as caixas se aninham.
    // A asserção é sobre ESTA receita, e não sobre a vitrine estar vazia. A primeira versão afirmava o
    // estado vazio da tela e passava sozinha e falhava na suíte: a jornada anterior deixava uma
    // publicação pública no banco, que é compartilhado e não se limpa entre specs. Um teste que depende
    // de ninguém ter publicado antes falha por motivo alheio ao que ele investiga — e o dia em que ele
    // apontar um vazamento de verdade, ninguém vai acreditar nele.
    await expect(page.getByRole('button', { name: titulo })).toHaveCount(0);

    // A prova independente da tela: a listagem pública não a traz de volta por outro caminho.
    const publicas = await get(page, '/api/v1/community/library');
    expect(
      JSON.stringify(publicas),
      'a receita privada não pode existir para quem está de fora',
    ).not.toContain(titulo);

    // --- 3. Abrir para o público, pela tela ---
    await naEstante.getByRole('combobox').selectOption('PUBLIC');
    await expect(page.getByRole('button', { name: titulo })).toBeVisible();

    // --- 4. O link compartilhado, e o token que aparece uma vez só ---
    const publicacao = await publicacaoPorTitulo(page, titulo);
    await naEstante.getByRole('button', { name: 'Links' }).click();
    await page.locator('#label').fill(`Para o concurso ${sfx}`);
    await page.getByRole('button', { name: 'Criar link' }).click();

    const token = (await page.locator('code').first().innerText()).trim();
    expect(token, 'o token precisa aparecer para quem compartilha').not.toEqual('');

    // Ele abre a publicação — e continua exigindo sessão: o link decide O QUE se vê, não QUEM se é.
    const aberta = await get(page, `/api/v1/community/shared?token=${encodeURIComponent(token)}`);
    expect(aberta.title).toBe(titulo);
    expect(aberta.licenseLabel).toBe('CC BY 4.0');
    // O retrato público não carrega o que é da casa. A asserção é sobre o corpo inteiro, e não campo a
    // campo: um campo novo com custo dentro passaria por uma lista de campos conhecidos.
    const corpo = JSON.stringify(aberta).toLowerCase();
    expect(corpo, 'a exportação pública não leva custo').not.toContain('cost');
    expect(corpo, 'a exportação pública não leva fornecedor').not.toContain('supplier');
    expect(corpo, 'a exportação pública não leva a cervejaria').not.toContain('breweryid');

    // Um token inventado não abre nada, e responde 404 — e não 403, que confirmaria a existência.
    const inventado = await page.request.get('/api/v1/community/shared?token=nao-existe-este-token');
    expect(inventado.status(), 'token inventado não pode distinguir "não existe" de "não é seu"').toBe(
      404,
    );

    // --- 5. Outra pessoa lê, comenta e denuncia ---
    // Precisa ser outra: o autor não avalia nem denuncia a própria receita, e é a segunda pessoa que
    // torna a moderação um fato em vez de um formulário.
    const contexto = await browser.newContext();
    const outra = await contexto.newPage();
    await login(outra, CONFERENTE);
    await outra.goto('/community/library');

    await outra.getByRole('button', { name: titulo }).click();

    await outra.locator('#cBody').fill('Sugiro reduzir o lúpulo de fervura.');
    await outra.getByRole('button', { name: 'Enviar' }).click();

    await denuncia(outra, 'Copiei esta receita de outro lugar.');
    await contexto.close();

    // --- 6. O autor vê a denúncia contra si, e não descobre quem denunciou ---
    await page.reload();
    await page.getByRole('listitem').filter({ hasText: titulo }).first()
      .getByRole('button', { name: 'Denúncias' }).click();

    const denuncias = await get(page, `/api/v1/community/library/${publicacao}/reports`);
    expect(denuncias.length, 'a denúncia precisa chegar a quem foi denunciado').toBeGreaterThan(0);
    expect(
      JSON.stringify(denuncias).toLowerCase(),
      'o autor vê a denúncia, não o denunciante — senão denunciar vira exposição',
    ).not.toContain('conferente');

    // E o comentário está lá, com o texto que a outra pessoa escreveu.
    const contribuicoes = await get(page, `/api/v1/community/library/${publicacao}/contributions`);
    expect(JSON.stringify(contribuicoes)).toContain('reduzir o lúpulo');
  });
});

/** O identificador da publicação recém-criada, pela estante do autor. */
async function publicacaoPorTitulo(page: Page, titulo: string): Promise<string> {
  const minhas = (await get(page, '/api/v1/community/library/mine')) as { id: string; title: string }[];
  const achada = minhas.find(p => p.title === titulo);
  if (!achada) {
    throw new Error(`publicação "${titulo}" não apareceu na estante do autor`);
  }
  return achada.id;
}

/**
 * Denunciar, pelo formulário que só aparece quando alguém decide denunciar.
 *
 * <p>Não é botão de primeira: a tela mostra a receita, e a denúncia fica atrás de um clique deliberado.
 */
async function denuncia(page: Page, texto: string): Promise<void> {
  await page.getByRole('button', { name: 'Denunciar esta publicação' }).click();
  await page.locator('#rReason').selectOption('PLAGIARISM');
  await page.locator('#rNote').fill(texto);
  await page.getByRole('button', { name: 'Denunciar', exact: true }).click();
}
