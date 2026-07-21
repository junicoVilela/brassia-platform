// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

// Fronteiras da arquitetura feature-first (sem path aliases: imports são
// relativos, então a referência a outra camada aparece como ".../core/..."
// ou ".../features/..." no especificador do import).
const coreForbidsFeatures = {
  patterns: [
    {
      group: ['**/features/**'],
      message: 'A camada core não pode depender de features: mantenha o núcleo genérico e reutilizável.',
    },
  ],
};

module.exports = tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', '.angular/**', 'coverage/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
  },
  {
    // core (e shared) são camadas de base: não podem importar features.
    files: ['src/app/core/**/*.ts', 'src/app/shared/**/*.ts'],
    rules: {
      'no-restricted-imports': ['error', coreForbidsFeatures],
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended],
  },
);
