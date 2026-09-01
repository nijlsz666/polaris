import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { port: 8091, host: true, proxy: { '/api': 'http://localhost:8090' } },
  define: { __APP_VERSION__: JSON.stringify('0.1.0') }
})
