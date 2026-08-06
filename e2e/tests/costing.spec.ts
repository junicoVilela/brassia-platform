import { expect, login, test } from './support';

/**
 * Custo do lote (CST-001).
 *
 * <p>A tela cruza duas chamadas — os custos fechados e a lista de lotes — e a segunda é a que já
 * quebrou telas antes por vir paginada. Aqui as duas chegam da stack real.
 */
test.describe('custo do lote', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela cruza custos fechados e lotes numa leitura só', async ({ page }) => {
    const costs = page.waitForResponse(r => r.url().includes('/api/v1/costing/batch-costs'));
    const batches = page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));
    await page.goto('/costing/batches');

    expect((await costs).status()).toBe(200);
    expect((await batches).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Custo do lote' })).toBeVisible();
    // Sem lote escolhido, a tela explica que o custo é sempre sobre um lote.
    await expect(page.getByText('Escolha um lote para ver o custo.')).toBeVisible();
  });
});
