import { defineConfig, loadEnv } from 'vite';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath } from 'node:url';

const envDir = fileURLToPath(new URL('..', import.meta.url));
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, envDir, '');
  const port = Number(env.FRONTEND_PORT || 4002);
  return {
    root: 'src',
    envDir,
    plugins: [tailwindcss()],
    server: { port, strictPort: true, host: 'localhost' },
    preview: { port, strictPort: true, host: 'localhost' },
    build: { outDir: '../dist', emptyOutDir: true },
  };
});
