import { type Page } from '@playwright/test';
import { expect, get, login, post, put, seedSellableLot, test } from './support';

/**
 * Custo e relatório do lote: os números fecham com o que foi produzido.
 *
 * <p>Fecha a linha do roteiro de homologação que estava provada **só pelo backend**. `BatchCostIT`,
 * `BatchVarianceIT` e `BatchReportIT` provam a apuração pela API; as telas tinham teste, e o que ele
 * provava era que elas **carregam** — não que o número que aparece é o número apurado.
 *
 * <p>A diferença não é acadêmica. O custo sai desta tela para planilha, para o preço e para um auditor,
 * e um total certo na API que chega torto à tela é indistinguível, para quem lê, de um total errado.
 * Foi assim que o `LOCALE_ID` ficou meses mostrando `200.00` onde a casa lê `200,00`, com todos os
 * testes de backend verdes.
 *
 * <p><strong>O teste persegue três acordos, e nenhum deles é "a tela abriu":</strong> a tela concorda com
 * a API; a tela de custo concorda com a de relatório; e o número se move quando a produção muda.
 */
test.describe('custo e relatório do lote', () => {
  test('o total apurado é o total exibido, as duas telas concordam, e a lacuna é declarada', async ({
    page,
  }) => {
    await login(page);
    const sfx = Date.now().toString().slice(-8);

    // --- 1. Um lote de verdade: transferido e envasado ---
    const { batchId } = await seedSellableLot(page, sfx);
    const codigo = await codigoDoLote(page, batchId);

    // --- 2. O consumo da brassagem, confirmado ---
    // Sem ele o insumo NÃO entra no custo — e o custeio diz isso, em vez de somar zero. Confirmar é o que
    // faz o total desta jornada ser sobre "o que foi produzido", e não só sobre a embalagem.
    const gastoEmInsumo = await confirmaOConsumo(page, batchId);
    expect(gastoEmInsumo, 'a brassagem consumiu insumo reservado').toBeGreaterThan(0);

    // --- 3. O que a API apurou, antes de olhar tela nenhuma ---
    const apurado = await get(page, `/api/v1/costing/batches/${batchId}`);
    expect(apurado.currency).toBe('BRL');
    // As duas parcelas que a produção gerou, cada uma pelo seu caminho: o insumo pelo consumo da
    // brassagem, a embalagem pelo envase.
    expect(Number(apurado.totalByCategory.INGREDIENT)).toBeCloseTo(gastoEmInsumo, 2);
    expect(Number(apurado.totalByCategory.PACKAGING)).toBeGreaterThan(0);
    // E o total é a soma delas — a conta fecha, e não é só "um número apareceu".
    const soma = Object.values(apurado.totalByCategory as Record<string, number>)
      .reduce((a, b) => a + Number(b), 0);
    expect(Number(apurado.total)).toBeCloseTo(soma, 2);
    // O divisor é o volume que existiu de fato — o transferido ao fermentador, e não o planejado.
    expect(Number(apurado.volumeLiters)).toBe(390);
    expect(Number(apurado.costPerLiter)).toBeCloseTo(Number(apurado.total) / 390, 3);

    // --- 4. A tela mostra ESSE total, e no formato que a casa lê ---
    // A vírgula é asserção de propósito: é ela que segura o `LOCALE_ID` no lugar. Sem ela a aplicação
    // volta ao `en-US` padrão do Angular sem que nenhum teste reclame — foi assim que a venda ficou
    // meses mostrando `200.00`.
    await abreOCusto(page, batchId, codigo);
    const painel = page.locator('.card-body').filter({ hasText: 'BRL total' }).first();
    await expect(painel).toContainText(moedaBr(apurado.total));
    await expect(painel).toContainText('BRL total');
    await expect(painel).toContainText('390 L transferidos');

    // --- 5. A lacuna é declarada, e é ela que impede o total de mentir por omissão ---
    // Este é o contraponto do teste inteiro: um custeio que somasse zero de mão de obra mostraria um
    // número menor com cara de completo, e alguém precificaria em cima dele.
    expect(apurado.incomplete, 'sem taxa de hora cadastrada, o custo é incompleto').toBe(true);
    const lacuna = page.locator('.alert-warning').filter({ hasText: 'Este total é menor que a verdade.' });
    await expect(lacuna).toBeVisible();
    await expect(lacuna).toContainText('Mão de obra');
    // E o insumo NÃO é mais lacuna, porque o consumo foi confirmado: a tela distingue as duas ausências.
    await expect(lacuna).not.toContainText('consumo do dia de brassa ainda não foi confirmado');

    // --- 6. O relatório do lote concorda com a tela de custo ---
    // Dois documentos derivados da mesma apuração. Se divergirem, um dos dois vai para fora da casa
    // com o número errado — e não há como saber qual, olhando só um deles.
    await abreORelatorio(page, batchId, codigo);
    const custoNoDossie = page.locator('.card').filter({ hasText: 'Custo' }).last();
    await expect(custoNoDossie).toContainText(moedaBr(apurado.total));
    await expect(custoNoDossie).toContainText('Aberto — ainda muda');
    // E o dossiê repete a ressalva, porque quem recebe um relatório o lê como se afirmasse tudo.
    await expect(page.getByText('O que este relatório não prova.')).toBeVisible();

    // --- 7. O número se move quando a produção muda ---
    // Sem isto, um total gravado uma vez e nunca mais recalculado passaria nas asserções acima. Fechar
    // a lacuna da mão de obra é a forma mais barata de mexer no total sem mexer no lote.
    await put(page, '/api/v1/costing/labor-rate', { costPerHour: 50 });
    await apontaDuasHoras(page, batchId);

    const comMaoDeObra = await get(page, `/api/v1/costing/batches/${batchId}`);
    expect(Number(comMaoDeObra.total)).toBeGreaterThan(Number(apurado.total));
    expect(comMaoDeObra.totalByCategory.LABOR, 'a mão de obra virou parcela').toBeTruthy();

    await abreOCusto(page, batchId, codigo);
    const depois = page.locator('.card-body').filter({ hasText: 'BRL total' }).first();
    await expect(depois).toContainText(moedaBr(comMaoDeObra.total));
    // A lacuna de mão de obra saiu; a de utilidade continua, e continua dizendo isso.
    await expect(page.locator('.alert-warning')).not.toContainText('Mão de obra');

    // --- 8. Fechar congela, e o dossiê passa a dizer isso ---
    // O estado atravessa as duas telas: quem lê o relatório precisa saber se está diante da soma de
    // agora ou da apuração assinada — são documentos com peso diferente.
    await post(page, `/api/v1/costing/batches/${batchId}/close`, { note: `jornada ${sfx}` });

    await abreORelatorio(page, batchId, codigo);
    const fechado = page.locator('.card').filter({ hasText: 'Custo' }).last();
    await expect(fechado).toContainText('Fechado');
    await expect(fechado).toContainText(moedaBr(comMaoDeObra.total));
  });
});

/** O código do lote, que é o que as duas telas mostram — o id não aparece na interface. */
async function codigoDoLote(page: Page, batchId: string): Promise<string> {
  const lotes = await get(page, '/api/v1/production/batches?page=0&size=100');
  const lote = lotes.content.find((b: { id: string }) => b.id === batchId);
  expect(lote, 'o lote semeado deve aparecer na listagem').toBeTruthy();
  return lote.code;
}

/**
 * Formata como a casa lê dinheiro: milhar com ponto, decimal com vírgula, duas casas.
 *
 * <p>Espelha o `number: '1.2-2'` do template sob `pt-BR`. É deliberado que o valor esperado seja
 * calculado aqui e não copiado da tela: comparar a tela com ela mesma passaria sempre.
 */
function moedaBr(valor: number | string): string {
  return Number(valor).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** Abre o custo de um lote pela tela: a escolha é um clique na lista, não um parâmetro de endereço. */
async function abreOCusto(page: Page, batchId: string, codigo: string): Promise<void> {
  await page.goto('/costing/batches');
  await expect(page.getByRole('heading', { name: 'Custo do lote' })).toBeVisible();
  const custo = page.waitForResponse(r => r.url().includes(`/api/v1/costing/batches/${batchId}`));
  await page.getByRole('button').filter({ hasText: codigo }).first().click();
  expect((await custo).status()).toBe(200);
}

/** Abre o dossiê do lote pela tela, pelo mesmo caminho de quem vai mandá-lo a um auditor. */
async function abreORelatorio(page: Page, batchId: string, codigo: string): Promise<void> {
  await page.goto('/reporting/batches');
  await expect(page.getByRole('heading', { name: 'Relatório do lote' })).toBeVisible();
  const dossie = page.waitForResponse(r => r.url().includes(`/api/v1/reporting/batches/${batchId}`));
  await page.getByRole('button').filter({ hasText: codigo }).first().click();
  expect((await dossie).status()).toBe(200);
}

/** Duas horas de uma pessoa: o suficiente para a mão de obra deixar de ser lacuna e virar parcela. */
async function apontaDuasHoras(page: Page, batchId: string): Promise<void> {
  const fim = new Date();
  const inicio = new Date(fim.getTime() - 2 * 60 * 60 * 1000);
  await post(page, `/api/v1/production/batches/${batchId}/labor`, {
    activity: 'brassagem',
    startedAt: inicio.toISOString(),
    endedAt: fim.toISOString(),
    people: 1,
  });
}

/**
 * Confirma o consumo da brassagem a partir da proposta, e devolve o que isso custou.
 *
 * <p>A proposta é o que a tela do dia de brassa oferece para conferência: as linhas **reservadas** pela
 * ordem, com o lote de estoque de cada uma. Confirmar sobre elas é o caminho de quem opera — inventar
 * quantidades aqui testaria uma brassagem que nunca aconteceu.
 *
 * <p>O valor devolvido é calculado do preço de entrada semeado (1,50 por unidade de compra), e não lido
 * do custeio: conferir o custeio contra ele mesmo passaria sempre.
 */
async function confirmaOConsumo(page: Page, batchId: string): Promise<number> {
  const proposta = await get(page, `/api/v1/production/batches/${batchId}/consumption/proposal`);
  expect(proposta.alreadyRegistered, 'o consumo ainda não foi registrado').toBe(false);
  expect(proposta.reserved.length, 'a ordem reservou insumo').toBeGreaterThan(0);

  const linhas = proposta.reserved.map((r: { lotId: string; reserved: number; unit: string }) => ({
    lotId: r.lotId,
    quantity: r.reserved,
    unit: r.unit,
  }));
  await post(page, `/api/v1/production/batches/${batchId}/consumption`, { lines: linhas });

  return linhas.reduce((total: number, l: { quantity: number }) => total + l.quantity * PRECO_DE_ENTRADA, 0);
}

/** O `unitCost` com que `seedSellableLot` dá entrada em todo insumo. */
const PRECO_DE_ENTRADA = 1.5;
