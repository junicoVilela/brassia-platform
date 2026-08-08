import { expect, login, test } from './support';

/**
 * Base de conhecimento (RAG-001).
 *
 * <p>O que só aparece aqui é a jornada inteira contra a stack real: indexar um documento, encontrá-lo por
 * uma pergunta escrita como gente escreve — sem acento, com flexão diferente — e ver a versão substituída
 * continuar no acervo. A busca textual em português é do PostgreSQL, então nenhum teste de unidade a
 * exercita.
 */
test.describe('base de conhecimento', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com o acervo vindo da stack real', async ({ page }) => {
    const documents = page.waitForResponse(r => r.url().includes('/api/v1/knowledge/documents'));
    await page.goto('/knowledge');

    expect((await documents).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Base de conhecimento' })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('indexar e depois encontrar por pergunta sem acento', async ({ page }) => {
    // "peracetico" sem acento é como se digita de verdade. Sem a configuração `unaccent` no banco, a busca
    // devolveria nada e quem perguntou concluiria que o documento não existe.
    const code = `E2E-FISPQ-${Date.now()}`;
    await page.goto('/knowledge');

    await page.getByRole('button', { name: 'Novo documento' }).click();
    await page.getByLabel('Código').fill(code);
    await page.getByLabel('Título').fill('FISPQ — Ácido peracético (E2E)');
    await page.getByLabel('Vigente a partir de').fill('2026-04-01');
    await page
      .getByLabel('Texto do documento')
      .fill(
        'O ácido peracético é utilizado na sanitização de tanques e tubulações. ' +
          'A concentração recomendada é de 0,15% em volume, com tempo de contato de vinte minutos.',
      );

    const indexed = page.waitForResponse(
      r => r.url().includes('/api/v1/knowledge/documents') && r.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Indexar', exact: true }).click();
    expect((await indexed).status()).toBe(201);

    const found = page.waitForResponse(r => r.url().includes('/api/v1/knowledge/search'));
    await page.getByLabel('Pergunta').fill('como sanitizar tanque com peracetico');
    await page.getByRole('button', { name: 'Buscar' }).click();
    expect((await found).status()).toBe(200);

    await expect(page.getByText('Trechos encontrados')).toBeVisible();
    await expect(page.getByText('0,15% em volume', { exact: false }).first()).toBeVisible();
    // O trecho é apresentado como citação de terceiro, não como fala do sistema.
    await expect(page.locator('blockquote').first()).toBeVisible();
  });

  test('pergunta sem fonte não é erro: a tela diz que não há fonte', async ({ page }) => {
    await page.goto('/knowledge');

    const searched = page.waitForResponse(r => r.url().includes('/api/v1/knowledge/search'));
    await page.getByLabel('Pergunta').fill('criogenia supercondutora e levitacao magnetica');
    await page.getByRole('button', { name: 'Buscar' }).click();
    expect((await searched).status()).toBe(200);

    await expect(page.getByText('Nenhuma fonte na base responde a isso.')).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('nova versão encerra a anterior, e a anterior continua no acervo', async ({ page }) => {
    const code = `E2E-VER-${Date.now()}`;
    await page.goto('/knowledge');

    // Título único, e não só o código: o banco local acumula documentos entre execuções, e um título
    // repetido casaria com as linhas da execução anterior.
    const v1 = `FISPQ v1 ${code}`;
    const v2 = `FISPQ v2 ${code}`;

    await page.getByRole('button', { name: 'Novo documento' }).click();
    await indexVersion(page, code, v1, '2026-04-01', 'A concentração é de 0,15% em volume.');
    await indexVersion(page, code, v2, '2026-06-01', 'A concentração passou a 0,20%.');

    // A v2 vigente e a v1 substituída, as duas visíveis: é o que permite investigar um lote antigo.
    await expect(page.getByRole('row', { name: new RegExp(v2) })).toContainText('vigente');
    await expect(page.getByRole('row', { name: new RegExp(v1) })).toContainText('substituída');
  });
});

async function indexVersion(
  page: import('@playwright/test').Page,
  code: string,
  title: string,
  from: string,
  text: string,
): Promise<void> {
  await page.getByLabel('Código').fill(code);
  await page.getByLabel('Título').fill(title);
  await page.getByLabel('Vigente a partir de').fill(from);
  await page.getByLabel('Texto do documento').fill(text);

  const indexed = page.waitForResponse(
    r => r.url().includes('/api/v1/knowledge/documents') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Indexar', exact: true }).click();
  expect((await indexed).status()).toBe(201);
}
