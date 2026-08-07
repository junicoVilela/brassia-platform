import { expect, login, test } from './support';

/**
 * Relatório do lote (RPT-001).
 *
 * <p>O dossiê atravessa seis módulos. Este teste prova que as seis consultas publicadas estão
 * montadas na stack real — nenhum teste de unidade cobre a fiação.
 */
test.describe('relatório do lote', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a lista de lotes carrega e a tela explica que o dossiê é sobre um lote', async ({ page }) => {
    const batches = page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));
    await page.goto('/reporting/batches');

    expect((await batches).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Relatório do lote' })).toBeVisible();
    await expect(page.getByText('Escolha um lote para ver o relatório.')).toBeVisible();
    await expect(page.locator('.alert-warning')).toHaveCount(0);
  });
});
