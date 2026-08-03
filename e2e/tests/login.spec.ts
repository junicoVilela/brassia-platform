import { ADMIN, expect, login, test } from './support';

/**
 * O login é o portão de tudo: se ele quebra, nenhuma outra jornada existe. Estes
 * testes exercitam a pilha inteira — formulário Angular, sessão por cookie, CSRF
 * e o `authGuard` — contra a API real.
 */
test.describe('login', () => {
  test('recusa senha errada sem sair da tela', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('E-mail').fill(ADMIN.email);
    await page.getByLabel('Senha').fill('senha-errada-de-proposito');
    await page.getByRole('button', { name: 'Entrar' }).click();

    // A mensagem vem do backend (Problem Details), não de validação local.
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('entra e chega na aplicação com a cervejaria de bootstrap', async ({ page }) => {
    await login(page);

    // O shell só renderiza depois do `authGuard` deixar passar.
    await expect(page.locator('#sidebar-area')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Receitas' })).toBeVisible();
  });

  test('rota protegida sem sessão volta para o login', async ({ page }) => {
    await page.goto('/packaging/plans');
    await expect(page).toHaveURL(/\/login/);
  });
});
