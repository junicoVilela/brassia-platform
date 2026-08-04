import { expect, login, test } from './support';

/**
 * Parametrização por cervejaria (PRM-001).
 *
 * A tela compõe cinco endpoints de módulos diferentes: se qualquer um falhar, o `forkJoin` derruba
 * a leitura inteira e a tela mostra erro. É exatamente o tipo de acoplamento que teste de unidade
 * com API dublada não pega, e por isso vale uma jornada aqui.
 */
test.describe('parametrização', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('carrega as cinco políticas e grava a validade do CIP', async ({ page }) => {
    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: 'Configurações' })).toBeVisible();

    const leitura = page.waitForResponse(
      r => r.url().includes('/api/v1/quality/capa-policy') && r.request().method() === 'GET',
    );
    await page.getByRole('link', { name: 'Parametrização' }).click();
    expect((await leitura).status()).toBe(200);

    await expect(page.getByRole('heading', { name: 'Parametrização' })).toBeVisible();
    // As cinco seções são de módulos distintos: todas visíveis prova que o `forkJoin` completou.
    await expect(page.getByLabel('Horas de validade')).toBeVisible();
    await expect(page.getByLabel('Meses')).toBeVisible();
    await expect(page.getByLabel('Termômetro')).toBeVisible();
    await expect(page.getByLabel('Nota máxima')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Prazos do CAPA por severidade' })).toBeVisible();

    const gravacao = page.waitForResponse(
      r => r.url().includes('/api/v1/sanitation/cleaning-policy') && r.request().method() === 'PUT',
    );
    await page.getByLabel('Horas de validade').fill('72');
    await page.getByRole('button', { name: 'Salvar' }).first().click();
    expect((await gravacao).status()).toBe(200);

    // A tela reflete a política gravada sem recarregar a página inteira.
    await expect(page.getByText('a liberação expira em 72 h')).toBeVisible();
  });
});
