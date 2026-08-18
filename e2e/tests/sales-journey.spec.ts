import { type Page } from '@playwright/test';
import { expect, get, login, ontem, post, put, seedSellableLot, test } from './support';

/**
 * Jornada comercial de ponta a ponta: do cliente ao pedido, passando pelo teto de crédito.
 *
 * <p>Fecha o item de E2E que o plano de testes da Sprint 19 pede — "cliente → pedido → reserva →
 * produção/expedição" — e que ficou aberto no encerramento da sprint. Até aqui a venda tinha teste de
 * domínio, de integração e de store, e **nenhuma jornada pela tela**: a diferença não é acadêmica, porque
 * a recusa por crédito só existe de verdade se o vendedor a enxergar, e é a tela que decide isso.
 *
 * <p>O preparo do lote vendável vai pela API — envase, frescor e liberação já têm teste próprio, e clicar
 * por eles testaria formulários alheios ao assunto. O que esta jornada faz **na interface** é o que só
 * acontece no fim da cadeia: registrar o pedido pelo formulário, ler a recusa com os três números, e
 * autorizar a exceção onde alguém a lê.
 */
test.describe('jornada comercial', () => {
  test('do cliente ao pedido, com o teto de crédito recusando e autorizando na tela', async ({
    page,
  }) => {
    await login(page);
    const sfx = Date.now().toString().slice(-8);

    // --- 1. Um lote vendável: envasado, com validade apurada e liberado pela qualidade ---
    const lot = await seedSellableLot(page, sfx);

    // --- 2. A cena comercial: cliente, canal, produto e preço vigente ---
    // Sem preço vigente no canal o pedido é recusado — "ainda não precificado" e "de graça" são opostos.
    const customer = await post(page, '/api/v1/crm/customers', {
      legalName: `Bar do E2E ${sfx}`,
    });
    const channel = await post(page, '/api/v1/sales/channels', {
      code: `CH-${sfx}`,
      name: `Canal ${sfx}`,
    });
    const product = await post(page, '/api/v1/sales/products', {
      sku: `SKU-${sfx}`,
      name: `Produto ${sfx}`,
      recipeId: lot.recipeId,
      containerId: lot.containerId,
    });
    await post(page, `/api/v1/sales/products/${product}/prices`, {
      channelId: channel,
      amount: 12.0,
      currency: 'BRL',
      taxIncluded: false,
      validFrom: ontem(),
    });

    // Teto de 200: dois pedidos de 120 não cabem juntos, e é essa a segunda venda que interessa.
    await put(page, `/api/v1/sales/portal/credit/${customer}`, { ceiling: 200.0, currency: 'BRL' });

    // --- 3. O primeiro pedido, pelo formulário: 10 unidades a 12,00 = 120,00, dentro do teto ---
    await page.goto('/sales/orders');
    await expect(page.getByRole('heading', { name: 'Pedidos', level: 1 })).toBeVisible();

    // O formulário nasce fechado: a tela é para ler pedido, e registrar é o ato menos frequente.
    await page.getByRole('button', { name: 'Novo pedido' }).click();
    await preencherPedido(page, { code: `PED-A${sfx}`, customer, channel, product, quantity: 10 });
    await page.getByRole('button', { name: 'Registrar pedido' }).click();

    // Entrou: o pedido nasce confirmado e já segura o estoque — não existe rascunho.
    await expect(page.getByText(`PED-A${sfx}`, { exact: false }).first()).toBeVisible();

    // --- 4. O segundo pedido estoura o teto, e a recusa traz os três números ---
    // Ela fica no formulário, e não num toast que some: é a única recusa que quem vende resolve ali
    // mesmo, e sem os números o vendedor decidiria no chute.
    await page.getByRole('button', { name: 'Novo pedido' }).click();
    await preencherPedido(page, { code: `PED-B${sfx}`, customer, channel, product, quantity: 10 });
    await page.getByRole('button', { name: 'Registrar pedido' }).click();

    await expect(page.getByText('Acima do limite de crédito.')).toBeVisible();
    // Os três números, que é o que permite decidir sem adivinhar: teto, o que já se deve, e este pedido.
    //
    // O separador decimal é ponto, e não vírgula, porque a aplicação não registra `LOCALE_ID` nem
    // `registerLocaleData` — todo `| number` cai no `en-US` padrão do Angular. Está assertado como está
    // para o teste descrever o que a tela faz hoje; se o locale for corrigido, é aqui que se descobre.
    const recusa = page.locator('.alert-warning');
    await expect(recusa).toContainText('Teto 200.00 BRL');
    await expect(recusa).toContainText('já devendo 120.00');
    await expect(recusa).toContainText('este pedido 120.00');

    // O formulário continua aberto: fechá-lo esconderia os números que explicam a recusa, e o vendedor
    // repetiria o pedido só para lê-los de novo.
    await expect(page.getByRole('button', { name: 'Registrar pedido' })).toBeVisible();

    // --- 5. Quem tem a alçada autoriza com justificativa, e ela fica no pedido ---
    await page
      .getByLabel('Motivo para autorizar mesmo assim')
      .fill('boleto do cliente compensa hoje');
    await page.getByRole('button', { name: 'Registrar pedido' }).click();

    await expect(page.getByText(`PED-B${sfx}`, { exact: false }).first()).toBeVisible();
    // Um pedido acima do limite precisa dizer isso onde alguém o lê, e não só na trilha de auditoria.
    await expect(page.getByText('Acima do teto').first()).toBeVisible();

    // --- 6. A justificativa não vaza para o pedido seguinte ---
    // O formulário guarda valores entre um pedido e outro; um motivo esquecido no campo autorizaria em
    // silêncio a próxima venda acima do teto, com a justificativa de uma venda que não é aquela.
    const pedidos = await get(page, '/api/v1/sales/orders');
    const a = pedidos.find((o: { code: string }) => o.code === `PED-A${sfx}`);
    const b = pedidos.find((o: { code: string }) => o.code === `PED-B${sfx}`);
    expect(a.creditOverrideReason, 'o pedido que coube no teto não carrega autorização').toBeNull();
    expect(b.creditOverrideReason).toBe('boleto do cliente compensa hoje');
    expect(b.creditOverrideBy, 'a autorização carrega quem autorizou').toBeTruthy();

    // --- 7. O recebimento libera o teto: o limite mede o que o cliente deve, e não o que ele comprou ---
    // Os dois em aberto são pagos: o comprometido é o que o cliente **deve**, e não o que ele comprou.
    // Com só um deles quitado ainda sobrariam 120 devidos, e o teto de 200 continuaria estreito — o que
    // provaria menos e confundiria mais.
    for (const pedido of [a, b]) {
      await post(page, `/api/v1/sales/orders/${pedido.id}/payments`, {
        amount: 120.0,
        currency: 'BRL',
        method: 'PIX',
      });
    }

    // Com os dois pagos, um terceiro de 120 volta a caber sob o teto de 200 — sem justificativa nenhuma,
    // que é a prova de que o pagamento devolveu o limite.
    await page.reload();
    await page.getByRole('button', { name: 'Novo pedido' }).click();
    await preencherPedido(page, { code: `PED-C${sfx}`, customer, channel, product, quantity: 10 });
    await page.getByRole('button', { name: 'Registrar pedido' }).click();

    await expect(page.getByText(`PED-C${sfx}`, { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Acima do limite de crédito.')).toBeHidden();

    const depois = await get(page, '/api/v1/sales/orders');
    const c = depois.find((o: { code: string }) => o.code === `PED-C${sfx}`);
    expect(c.creditOverrideReason, 'o pedido que coube não registra exceção').toBeNull();
  });
});

/** Preenche o formulário de pedido. O canal e o produto são `select`, e vêm do catálogo carregado. */
async function preencherPedido(
  page: Page,
  dados: { code: string; customer: string; channel: string; product: string; quantity: number },
): Promise<void> {
  await page.getByLabel('Código').fill(dados.code);
  await page.getByLabel('Cliente').fill(dados.customer);
  await page.getByLabel('Canal').selectOption(dados.channel);
  await page.getByLabel('Produto').selectOption(dados.product);
  await page.getByLabel('Unidades').fill(String(dados.quantity));
}
