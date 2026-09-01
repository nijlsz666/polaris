import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    host: true,
    proxy: { '/api': process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080' }
  },
  build: { outDir: 'dist' }
})
