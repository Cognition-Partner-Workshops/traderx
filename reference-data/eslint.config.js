const js = require('@eslint/js');
const parser = require('@typescript-eslint/parser');
const plugin = require('@typescript-eslint/eslint-plugin');
const prettier = require('eslint-plugin-prettier/recommended');
const globals = require('globals');

module.exports = [
  { ignores: ['dist/**', 'coverage/**'] },
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser,
      parserOptions: { project: 'tsconfig.json', tsconfigRootDir: __dirname },
      globals: { ...globals.node, ...globals.jest },
    },
    plugins: { ...prettier.plugins, '@typescript-eslint': plugin },
    rules: {
      ...js.configs.recommended.rules,
      ...plugin.configs['eslint-recommended'].overrides[0].rules,
      ...plugin.configs.recommended.rules,
      ...prettier.rules,
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
];
