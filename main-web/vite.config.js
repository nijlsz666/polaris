import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: { '/api': process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080' }
  },
  define: { __APP_VERSION__: JSON.stringify('0.2.0') }
})
