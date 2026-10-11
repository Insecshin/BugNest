import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import basicSsl from '@vitejs/plugin-basic-ssl'

export default defineConfig({
  plugins: [vue(), basicSsl()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: Object.fromEntries(['/auth', '/accounts', '/api'].map(path => [path, {
      target: 'http://localhost:8080',
      changeOrigin: true,
    }])),
  },
  preview: { port: 4173, strictPort: true },
})
