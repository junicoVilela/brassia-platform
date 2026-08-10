import { expect, login, test } from './support';

/**
 * Lote dividido (EXP-001).
 *
 * <p>O que só aparece aqui: o contador de fatores que diferem reagindo enquanto se digita, contra a stack
 * real. É a peça que impede alguém de montar o experimento inteiro para só então descobrir, no envio, que
 * o desenho está confundido.
 */
test.describe('experimentos', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com os experimentos da stack real', async ({ page }) => {
    const list = page.waitForResponse(r => r.url().includes('/api/v1/experiments'));
    await page.goto('/experiments');

    expect((await list).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Experimentos', level: 1 })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('o formulário avisa sobre confundimento ANTES de enviar', async ({ page }) => {
    await page.goto('/experiments');
    await page.waitForResponse(r => r.url().includes('/api/v1/experiments'));
    await page.getByRole('button', { name: 'Planejar experimento' }).click();

    // Nenhum fator ainda: sem variável, não há experimento.
    await expect(page.getByText('Nenhum fator difere ainda')).toBeVisible();

    await page.getByLabel('Nome do fator 1').fill('Temperatura');
    await page.getByLabel('Valor no controle do fator 1').fill('20 C');
    await page.getByLabel('Valor na variante do fator 1').fill('4 C');
    await expect(page.getByText('Variável isolada:')).toBeVisible();

    // O segundo fator diferente é o caso que a história existe para impedir.
    await page.getByLabel('Nome do fator 2').fill('Levedura');
    await page.getByLabel('Valor no controle do fator 2').fill('US-05');
    await page.getByLabel('Valor na variante do fator 2').fill('S-04');
    await expect(page.getByText('2 fatores diferem.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Planejar', exact: true })).toBeDisabled();

    // Igualar o segundo lado devolve o desenho ao estado válido.
    await page.getByLabel('Valor na variante do fator 2').fill('US-05');
    await expect(page.getByText('Variável isolada:')).toBeVisible();
  });
});
