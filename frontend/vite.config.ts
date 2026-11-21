import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // yunshu 专用端口：luohuo 占用 5173，后端占用 8000
    host: '0.0.0.0',
    port: 5180,
    // 固定端口：被占用时直接报错，不自动漂移到其他端口
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 5181,
    strictPort: true
  }
})
