import { expect, login, test } from './support';

/**
 * Otimização assistida (OPT-001).
 *
 * <p>O que só aparece aqui: a procedência exibida junto do resultado contra a stack real. Um número sem
 * método e sem versão convida a ser citado meses depois como se ainda valesse — e é na tela que ele é
 * citado.
 */
test.describe('otimização assistida', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com as corridas da stack real', async ({ page }) => {
    const list = page.waitForResponse(r => r.url().includes('/api/v1/optimizations'));
    await page.goto('/optimization');

    expect((await list).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Otimização assistida', level: 1 })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('o objetivo diz o que sacrifica, e meia faixa avisa', async ({ page }) => {
    await page.goto('/optimization');
    await page.waitForResponse(r => r.url().includes('/api/v1/optimizations'));

    // Escolher um objetivo é abrir mão dos outros; o texto muda junto.
    await page.getByLabel('Objetivo').selectOption('COST');
    await expect(page.getByText('Pode mudar cor e amargor')).toBeVisible();
    await page.getByLabel('Objetivo').selectOption('TECHNICAL_TARGET');
    await expect(page.getByText('mesmo custando mais')).toBeVisible();

    // Meia faixa não restringe nada e seria ignorada em silêncio — a tela avisa.
    await page.getByLabel('IBU mínimo').fill('30');
    await expect(page.getByText('Informe os dois extremos')).toBeVisible();
    await page.getByLabel('IBU máximo').fill('40');
    await expect(page.getByText('Informe os dois extremos')).toBeHidden();
  });

  test('O RESULTADO MOSTRA COMO SE REPRODUZ', async ({ page }) => {
    await page.goto('/optimization');
    await page.waitForResponse(r => r.url().includes('/api/v1/optimizations'));

    const recipes = page.getByLabel('Receita publicada');
    const options = await recipes.locator('option').count();
    test.skip(options < 2, 'ambiente sem receita publicada');

    await recipes.selectOption({ index: 1 });
    const executed = page.waitForResponse(
      r => r.url().includes('/api/v1/optimizations') && r.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Otimizar' }).click();
    await executed;

    // Método, versão da receita e marca do catálogo aparecem junto do resultado, não num rodapé.
    await expect(page.getByText('Como este resultado se reproduz:')).toBeVisible();
    await expect(page.getByText('EXHAUSTIVE_SINGLE_SUBSTITUTION')).toBeVisible();
  });
});
