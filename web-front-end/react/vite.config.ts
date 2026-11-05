import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

const port = Number(process.env.WEB_SERVICE_REACT_PORT || 18094);

export default defineConfig({
  plugins: [react()],
  server: { host: '0.0.0.0', port, strictPort: true },
  preview: { host: '0.0.0.0', port, strictPort: true },
  build: { outDir: 'build' },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
  },
});
