import { expect, login, test } from './support';

/**
 * Feedback de campo (FLD-001).
 *
 * <p>O que só aparece aqui: o formulário antecipando o que a classificação vai exigir, contra a stack
 * real. Ver "isto exigirá quarentena" enquanto se escolhe a categoria é o que faz alguém classificar
 * sabendo a consequência — em vez de aprender, depois, a evitar a classificação que dá trabalho.
 */
test.describe('feedback de campo', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com as reclamações da stack real', async ({ page }) => {
    const list = page.waitForResponse(r => r.url().includes('/api/v1/field-feedback/complaints'));
    await page.goto('/field-feedback');

    expect((await list).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Feedback de campo', level: 1 })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('A CLASSIFICAÇÃO ANTECIPA o que será exigido', async ({ page }) => {
    await page.goto('/field-feedback');
    await page.waitForResponse(r => r.url().includes('/api/v1/field-feedback/complaints'));
    await page.getByRole('button', { name: 'Registrar reclamação' }).click();

    // Preferência não exige nada.
    await page.getByLabel('Severidade').selectOption('PREFERENCE');
    await expect(page.getByText('Esta classificação exigirá')).toBeHidden();

    // Suspeita de falha de processo exige investigação.
    await page.getByLabel('Severidade').selectOption('SYSTEMIC');
    await expect(page.getByText('Abrir investigação de causa (CAPA)')).toBeVisible();

    // Corpo estranho exige quarentena MESMO com severidade baixa — a categoria prevalece.
    await page.getByLabel('Severidade').selectOption('PREFERENCE');
    await page.getByLabel('Categoria').selectOption('FOREIGN_BODY');
    await expect(page.getByText('Quarentenar o lote')).toBeVisible();
  });

  test('amostra retida pede onde ela está', async ({ page }) => {
    await page.goto('/field-feedback');
    await page.waitForResponse(r => r.url().includes('/api/v1/field-feedback/complaints'));
    await page.getByRole('button', { name: 'Registrar reclamação' }).click();

    // O campo de local só existe quando faz sentido, e aí é obrigatório.
    await expect(page.getByLabel('Onde está')).toBeHidden();
    await page.getByLabel('Situação').selectOption('RETAINED');
    await expect(page.getByLabel('Onde está')).toBeVisible();
  });
});
