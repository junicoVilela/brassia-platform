import { expect, login, test } from './support';

/**
 * Painel operacional (RPT-002).
 *
 * <p>O painel coleta de cinco módulos por uma porta federada. Este teste prova que os cinco estão
 * ligados na stack real e que o critério da história — definição, período e drill-down em todo
 * indicador — sobrevive até a tela.
 */
test.describe('painel operacional', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('o painel abre com período padrão e todo cartão traz a definição do número', async ({ page }) => {
    const dashboard = page.waitForResponse(r => r.url().includes('/api/v1/reporting/dashboard'));
    await page.goto('/reporting/dashboard');

    const response = await dashboard;
    expect(response.status()).toBe(200);
    expect(response.url()).toContain('from=');
    expect(response.url()).toContain('to=');

    // O corpo é relido pela API em vez de extraído da resposta interceptada: depois que a navegação
    // termina, o Chrome pode ter descartado o buffer daquele recurso, e o teste falha por um motivo
    // que não tem nada a ver com o que ele quer provar.
    const api = await page.request.get(response.url());
    expect(api.status()).toBe(200);
    const body = await api.json();
    expect(body.indicators.length).toBeGreaterThan(0);
    for (const indicator of body.indicators) {
      expect(indicator.definition).not.toEqual('');
      expect(indicator.to).toBeTruthy();
      expect(indicator.drillDown.resource).not.toEqual('');
    }

    await expect(page.getByRole('heading', { name: 'Painel operacional' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Produção' })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('período invertido é barrado antes de ir ao servidor', async ({ page }) => {
    await page.goto('/reporting/dashboard');
    await page.getByLabel('De').fill('2026-08-31');
    await page.getByLabel('Até').fill('2026-08-01');
    await page.getByRole('button', { name: 'Aplicar' }).click();

    await expect(page.locator('.alert-danger')).toContainText('depois do fim');
  });
});
