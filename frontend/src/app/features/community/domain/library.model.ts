/** A biblioteca de receitas (COM-001). */

export type RecipeLicense = 'CC0' | 'CC_BY' | 'CC_BY_SA' | 'CC_BY_NC' | 'ALL_RIGHTS_RESERVED';

/** Do mais fechado para o mais aberto. `LINK` e `UNLISTED` são alcançáveis e não listados. */
export type Visibility = 'PRIVATE' | 'BREWERY' | 'LINK' | 'UNLISTED' | 'PUBLIC';

export interface PublicRecipeItem {
  /** O nome, e nunca o identificador — ele é a chave do catálogo da cervejaria. */
  ingredientName: string;
  stage: string;
  quantity: number;
  unit: string;
  timingMinutes: number | null;
  percentage: number | null;
}

export interface PublicRecipeSnapshot {
  name: string;
  style: string | null;
  batchVolumeLiters: number;
  boilTimeMinutes: number | null;
  targets: {
    originalGravity: number | null;
    finalGravity: number | null;
    ibu: number | null;
    colorSrm: number | null;
    abvPercent: number | null;
  } | null;
  items: PublicRecipeItem[];
}

/** Sem cervejaria e sem id de receita: o retrato público não carrega nem um nem outro. */
export interface LibraryPublication {
  id: string;
  title: string;
  summary: string | null;
  author: string;
  license: RecipeLicense;
  licenseLabel: string;
  recipeVersion: number;
  publishedAt: string;
  forkable: boolean;
  recipe: PublicRecipeSnapshot;
}

export interface OwnedPublication {
  id: string;
  title: string;
  recipeId: string;
  recipeVersion: number;
  license: RecipeLicense;
  visibility: Visibility;
  published: boolean;
  publishedAt: string;
}

export const VISIBILITY_LABELS: Record<Visibility, string> = {
  PRIVATE: 'Só eu',
  BREWERY: 'Minha cervejaria',
  LINK: 'Quem tem o link',
  UNLISTED: 'Não listada',
  PUBLIC: 'Pública',
};

/**
 * O que cada nível significa na prática.
 *
 * O rótulo sozinho deixa a leitura por conta de quem lê — e aqui a leitura errada publica dado que não
 * devia sair. A frase diz o efeito.
 */
export const VISIBILITY_HELP: Record<Visibility, string> = {
  PRIVATE: 'Ninguém além de você alcança.',
  BREWERY: 'Sua cervejaria enxerga; ninguém de fora.',
  LINK: 'Quem tiver o endereço abre — e o endereço circula sem controle depois de compartilhado.',
  UNLISTED: 'Abre por endereço direto, e não aparece na busca.',
  PUBLIC: 'Na vitrine, na busca e no feed, para qualquer cervejaria.',
};

/** O que o link autoriza. Nenhum nível permite editar a receita (COM-002). */
export type SharePermission = 'READ' | 'COMMENT';

/**
 * Um link, como o autor o vê.
 *
 * Sem o token: ele aparece uma vez, na criação, e o servidor guarda só o hash.
 */
export interface ShareLink {
  id: string;
  label: string | null;
  permission: SharePermission;
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
  /** Se o link, por si, ainda vale — ele ainda pode não abrir nada se a publicação foi fechada. */
  usable: boolean;
}

/** O token só existe aqui, e só uma vez. */
export interface CreatedShareLink {
  id: string;
  token: string;
}

/** O resultado de copiar uma receita publicada (COM-003). */
export interface ForkedRecipe {
  recipeId: string;
  /** Pronta para a tela: "IPA da Casa, de Ana (CC BY 4.0)". */
  attribution: string;
  sourceLicense: RecipeLicense;
  /**
   * Não nulo quando a licença de origem se propaga (CC BY-SA).
   *
   * Vem na resposta para o forkador não descobrir a obrigação só na hora de publicar.
   */
  requiredLicense: RecipeLicense | null;
}
