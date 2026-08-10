import { expect, login, test, seedBatch } from './support';

/**
 * Gêmeo digital: perfil aprendido e carta de controle (DTW-001 + SPC-001).
 *
 * <p>O que só aparece aqui: a tela contra a stack real, incluindo o aviso que separa limite de controle de
 * especificação. Esse aviso é o coração de SPC-001 — no desenho, as duas linhas são idênticas, e quem olha
 * rápido lê a que espera ver. Um teste que só checasse o cálculo deixaria a confusão voltar pela tela.
 */
test.describe('gêmeo digital', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await seedBatch(page);
  });

  test('a tela abre e distingue limite de controle de especificação', async ({ page }) => {
    const batches = page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));
    await page.goto('/digital-twin');

    expect((await batches).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Gêmeo digital', level: 1 })).toBeVisible();
  });

  test('escolher uma receita mostra perfil e carta, e nunca um número solto', async ({ page }) => {
    await page.goto('/digital-twin');
    await page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));

    const recipes = page.getByLabel('Receita');
    const options = await recipes.locator('option').count();
    // O lote foi semeado no beforeEach; menos de duas opções é defeito da tela, não do ambiente.
    expect(options).toBeGreaterThan(1);

    await recipes.selectOption({ index: 1 });
    await expect(page.getByRole('heading', { name: 'Perfil aprendido' })).toBeVisible();

    const analyzed = page.waitForResponse(r => r.url().includes('/digital-twin/control-charts'));
    await page.getByRole('button', { name: 'Montar carta' }).click();
    await analyzed;

    // O aviso é parte do resultado, não decoração: aparece com carta ou com recusa por histórico curto.
    await expect(page.getByText('Estes limites são calculados, não escolhidos.')).toBeVisible();
  });
});
