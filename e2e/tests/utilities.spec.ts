import { expect, login, test } from './support';

/**
 * Consumo por litro (UTL-001).
 *
 * <p>O indicador não tem tabela: se a rota responde, é porque o módulo de utilidades conseguiu
 * perguntar à sanitização, ao gás e ao envase na stack real. É isso que este teste exercita — a
 * montagem das portas, que nenhum teste de unidade cobre.
 */
test.describe('consumo por litro', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com um período padrão e o relatório derivado da stack real', async ({ page }) => {
    const report = page.waitForResponse(r => r.url().includes('/api/v1/utilities/indicators'));
    await page.goto('/utilities/indicators');

    const response = await report;
    expect(response.status()).toBe(200);
    // O período padrão é fechado pela tela, não pelo backend: from e to sempre viajam.
    expect(response.url()).toContain('from=');
    expect(response.url()).toContain('to=');

    await expect(page.getByRole('heading', { name: 'Consumo por litro' })).toBeVisible();
    await expect(page.getByText('litros envasados no período')).toBeVisible();
    // Aviso de período sem envase é legítimo e depende dos dados; erro não é.
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('período invertido é barrado antes de ir ao servidor', async ({ page }) => {
    await page.goto('/utilities/indicators');
    await page.getByLabel('De').fill('2026-08-31');
    await page.getByLabel('Até').fill('2026-08-01');
    await page.getByRole('button', { name: 'Aplicar' }).click();

    await expect(page.locator('.alert-danger')).toContainText('depois do fim');
  });
});
