import { expect, login, test } from './support';

/**
 * Planejado versus real (CST-002).
 *
 * <p>A tela cruza a lista de lotes com uma consulta que atravessa quatro módulos — plano,
 * estoque, produção e envase. Se alguma das portas não estivesse montada, é aqui que apareceria.
 */
test.describe('planejado versus real', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a lista de lotes carrega e a tela explica que a comparação é sobre um lote', async ({ page }) => {
    const batches = page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));
    await page.goto('/costing/variance');

    expect((await batches).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Planejado × real' })).toBeVisible();
    await expect(page.getByText('Escolha um lote para comparar.')).toBeVisible();
    await expect(page.locator('.alert-warning')).toHaveCount(0);
  });
});
