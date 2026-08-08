import { expect, login, test } from './support';

/**
 * Copiloto de IA — gateway de modelos (AIA-001).
 *
 * <p>Na stack de desenvolvimento o provedor está desligado, que é o default do produto. Isso é o que
 * este teste exercita, e não uma limitação dele: a promessa da história é que uma instalação sem IA
 * continue inteira — o status responde, a recusa é explícita e o teto de gasto é administrável antes
 * de existir qualquer provedor. É a promessa que vale para todo mundo; a do caminho configurado
 * dependeria de chave de terceiro na CI.
 *
 * <p>O que nenhum teste de unidade cobre e este cobre: a montagem real das portas — provedor
 * escolhido no boot conforme a configuração, ledger gravando em transação própria, permissões
 * separadas chegando ao controller.
 */
test.describe('copiloto de IA', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com o estado do gateway vindo da stack real', async ({ page }) => {
    const status = page.waitForResponse(r => r.url().includes('/api/v1/ai/gateway'));
    await page.goto('/ai/gateway');

    expect((await status).status()).toBe(200);

    await expect(page.getByRole('heading', { name: 'Copiloto de IA' })).toBeVisible();
    // Sem provedor é estado normal: aviso neutro, e nenhum alarme vermelho.
    await expect(page.getByText('Esta instalação não tem copiloto habilitado.')).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
    // Há sempre um teto — o próprio ou o padrão da instalação.
    await expect(page.getByText('Orçamento do mês')).toBeVisible();
  });

  test('a verificação recusa com explicação, e a tentativa aparece no histórico', async ({ page }) => {
    await page.goto('/ai/gateway');

    const probe = page.waitForResponse(r => r.url().includes('/api/v1/ai/gateway/probe'));
    await page.getByRole('button', { name: 'Verificar conectividade' }).click();

    // 501: decisão de instalação, não falha transitória — nada a repetir.
    expect((await probe).status()).toBe(501);
    await expect(page.locator('.alert-warning')).toContainText('não tem copiloto de IA habilitado');

    // A tentativa recusada virou linha no ledger: é assim que quem opera vê que alguém tentou.
    await expect(page.getByText('Últimas chamadas', { exact: true })).toBeVisible();
    await expect(page.getByRole('row', { name: /CONNECTIVITY_PROBE/ }).first()).toBeVisible();
  });

  test('o teto de gasto é administrável antes de existir provedor', async ({ page }) => {
    await page.goto('/ai/gateway');
    await page.getByRole('button', { name: 'Alterar teto' }).click();

    const saved = page.waitForResponse(
      r => r.url().includes('/api/v1/ai/gateway/budget') && r.request().method() === 'PUT',
    );
    await page.getByLabel(/Teto mensal/).fill('42');
    await page.getByRole('button', { name: 'Salvar teto' }).click();

    expect((await saved).status()).toBe(200);
    await expect(page.locator('.alert-danger')).toHaveCount(0);
    // Separador decimal aceito nos dois formatos: a aplicação ainda não fixa locale, e o teto do
    // gasto é o que esta história promete — não a formatação, que é a mesma de todas as telas.
    await expect(page.getByText(/de 42[.,]00 USD/)).toBeVisible();
  });
});
