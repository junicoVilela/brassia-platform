import { expect, login, test } from './support';

/**
 * Quarentena (FDS-002).
 *
 * <p>A tela mistura duas coisas que só se encontram na stack real: uma lista vinda da API e um
 * alcance <em>derivado do grafo</em> a cada abertura. É o tipo de resposta que o mock do teste de
 * unidade sempre devolve bem-comportada.
 */
test.describe('quarentenas', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a lista carrega e explica por onde a quarentena começa', async ({ page }) => {
    const lista = page.waitForResponse(r => r.url().includes('/api/v1/traceability/quarantines'));
    await page.goto('/traceability/quarantines');
    expect((await lista).status()).toBe(200);

    await expect(page.getByRole('heading', { name: 'Quarentenas' })).toBeVisible();
    // Alternar o escopo é a interação mais frequente, e cada uma é uma consulta nova.
    const todas = page.waitForResponse(
      r => r.url().includes('/api/v1/traceability/quarantines') && r.url().includes('onlyOpen=false'),
    );
    await page.getByRole('button', { name: 'Todas' }).click();
    expect((await todas).status()).toBe(200);
  });

  test('quarentenar exige um nó: sem lote, a genealogia não oferece o botão', async ({ page }) => {
    await page.goto('/traceability/genealogy');

    await expect(page.getByText('Escolha um lote para ver a genealogia.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Quarentenar' })).toHaveCount(0);
  });
});
