import { expect, login, test } from './support';

/**
 * Relatórios salvos e entrega programada (RPT-003).
 *
 * <p>A tela cruza os relatórios salvos com a lista de usuários — dono e destinatários são pessoas
 * da plataforma, não endereços. Se a segunda chamada falhasse, a tela mostraria UUID no lugar de
 * nome sem avisar, e é isso que este teste protege.
 */
test.describe('relatórios salvos', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela carrega definições e pessoas, e explica de quem é a alçada', async ({ page }) => {
    const reports = page.waitForResponse(r => r.url().includes('/api/v1/reporting/saved-reports'));
    const users = page.waitForResponse(r => r.url().includes('/api/v1/security/users'));
    await page.goto('/reporting/saved-reports');

    expect((await reports).status()).toBe(200);
    expect((await users).status()).toBe(200);

    await expect(page.getByRole('heading', { name: 'Relatórios salvos' })).toBeVisible();
    await expect(page.getByText('alçada do proprietário técnico')).toBeVisible();
    await expect(page.getByText('Escolha um relatório salvo.')).toBeVisible();
    await expect(page.locator('.alert-warning')).toHaveCount(0);
  });

  test('o formulário oferece pessoas e não tem campo de e-mail', async ({ page }) => {
    await page.goto('/reporting/saved-reports');
    await page.getByRole('button', { name: 'Novo' }).click();

    await expect(page.getByLabel('Proprietário técnico')).toBeVisible();
    await expect(page.getByText('Não há campo de e-mail')).toBeVisible();
    // A ausência é a funcionalidade: destinatário é usuário, escolhido de uma lista.
    await expect(page.locator('input[type="email"]')).toHaveCount(0);
  });
});
