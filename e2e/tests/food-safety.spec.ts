import { expect, login, test } from './support';

/**
 * Matriz de alergênicos (FDS-001).
 *
 * A tela cruza três fontes numa leitura só — a matriz, o cadastro de equipamentos (endpoint
 * **paginado**) e os POPs da sanitização. É exatamente a forma de defeito que os testes de unidade
 * não pegam e que já atravessou as telas de envase e gases: um `@for` sobre valor não-iterável
 * quebra a lista em silêncio e o estado vazio disfarça. Aqui a jornada roda contra a stack real.
 *
 * <p>Nada aqui assume banco vazio: o vocabulário é da cervejaria e sobrevive entre execuções.
 */
test.describe('matriz de alergênicos', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('cadastrar um alergênico liga os três eixos da matriz', async ({ page }) => {
    const matriz = page.waitForResponse(r => r.url().includes('/api/v1/food-safety/matrix'));
    await page.goto('/food-safety/allergens');
    expect((await matriz).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Matriz de alergênicos' })).toBeVisible();

    const codigo = `E2E${Date.now()}`;
    await page.getByLabel('Código').fill(codigo);
    await page.getByLabel('Nome no rótulo').fill(`Alergênico ${codigo}`);
    const cadastro = page.waitForResponse(
      r => r.url().includes('/api/v1/food-safety/allergens') && r.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Cadastrar' }).click();
    expect((await cadastro).status()).toBe(201);

    // Com vocabulário, os três eixos aparecem — inclusive os que dependem de endpoint paginado.
    await expect(page.getByText(`Alergênico ${codigo}`).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Ingredientes' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Equipamentos' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'POPs de limpeza' })).toBeVisible();
  });
});
