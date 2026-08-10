import { expect, login, test } from './support';

/**
 * União e divisão de volume (BLD-001).
 *
 * <p>O que só aparece aqui: o balanço recalculando na tela enquanto se digita, contra a stack real. Quem
 * monta uma união lida com volumes medidos em tanque — ver a diferença aparecer em tempo real é o que
 * revela um erro de leitura antes de ele virar uma operação aprovada e irreversível.
 */
test.describe('blend e reprocesso', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com as operações da stack real', async ({ page }) => {
    const list = page.waitForResponse(r => r.url().includes('/api/v1/blends'));
    await page.goto('/blends');

    expect((await list).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Blend e reprocesso', level: 1 })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('O BALANÇO RECALCULA enquanto se digita', async ({ page }) => {
    await page.goto('/blends');
    await page.waitForResponse(r => r.url().includes('/api/v1/blends'));
    await page.getByRole('button', { name: 'Simular operação' }).click();

    await page.getByLabel('Litros da origem 1').fill('400');
    await page.getByLabel('Litros da origem 2').fill('200');
    await page.getByLabel('Litros do destino 1').fill('500');

    // 600 entram, 500 saem, nenhuma perda declarada: faltam 100.
    await expect(page.getByText('Faltam 100 L.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Simular', exact: true })).toBeDisabled();

    // Declarar a perda é o caminho legítimo para a conta fechar.
    await page.getByLabel('Perda declarada (L)').fill('100');
    await expect(page.getByText('O balanço fecha.')).toBeVisible();

    // Volume aparecendo na saída é recusado do mesmo jeito.
    await page.getByLabel('Litros do destino 1').fill('700');
    await expect(page.getByText('Sobram 200 L na saída.')).toBeVisible();
  });
});
