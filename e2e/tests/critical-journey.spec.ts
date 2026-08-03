import { expect, login, test } from './support';

/**
 * Jornada crítica: entrar e alcançar as telas de operação, com os dados vindo da
 * API real. Não cria dado de produção — o objetivo é provar que a integração
 * frontend ↔ API ↔ banco está de pé ponta a ponta, que é o que os testes de
 * unidade e de integração, cada um do seu lado, não conseguem provar.
 */
test.describe('jornada crítica', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('navega até planos de envase e a tela responde com dados da API', async ({ page }) => {
    // Espera a resposta real da API, não só o render do componente.
    const listagem = page.waitForResponse(
      r => r.url().includes('/api/v1/packaging/plans') && r.request().method() === 'GET',
    );
    await page.getByRole('link', { name: 'Planos de envase' }).click();
    const resposta = await listagem;

    expect(resposta.status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Planos de envase' })).toBeVisible();
    // Banco recém-criado: a tela precisa mostrar o estado vazio, não um erro.
    await expect(page.getByText('Nenhum plano de envase.')).toBeVisible();

    // Os selects de referência vêm de endpoints paginados. Se voltarem como
    // objeto e não array, a lista quebra e o campo fica vazio — o formulário de
    // abrir plano fica inutilizável mesmo com a tela "carregada".
    await expect(page.getByLabel('Embalagem')).toBeVisible();
    await expect(page.getByLabel('Linha de envase')).toBeVisible();
  });

  test('navega até gases e a tela responde com dados da API', async ({ page }) => {
    const listagem = page.waitForResponse(
      r => r.url().includes('/api/v1/gas/') && r.request().method() === 'GET',
    );
    await page.getByRole('link', { name: 'Gases e CO₂' }).click();
    await expect((await listagem).status()).toBe(200);

    await expect(page.getByRole('heading', { name: 'Gases e rede de CO₂' })).toBeVisible();
    await expect(page.getByText('Nenhum cilindro cadastrado.')).toBeVisible();
  });

  test('navega até instrumentos e a tela responde com dados da API', async ({ page }) => {
    const listagem = page.waitForResponse(
      r => r.url().includes('/api/v1/metrology/instruments') && r.request().method() === 'GET',
    );
    await page.getByRole('link', { name: 'Instrumentos' }).click();
    expect((await listagem).status()).toBe(200);

    await expect(page.getByRole('heading', { name: 'Instrumentos e calibração' })).toBeVisible();
    await expect(page.getByText('Nenhum instrumento cadastrado.')).toBeVisible();
    // Os selects de referência do formulário vêm da mesma API: se a lista quebrar, ficam vazios.
    await expect(page.getByLabel('Tipo')).toBeVisible();
  });

  test('receitas carrega e a sessão sobrevive a recarregar a página', async ({ page }) => {
    await page.getByRole('link', { name: 'Receitas' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    // A sessão vive em cookie: recarregar não pode derrubar para o login.
    await page.reload();
    await expect(page.locator('#sidebar-area')).toBeVisible();
    await expect(page).not.toHaveURL(/\/login/);
  });
});
