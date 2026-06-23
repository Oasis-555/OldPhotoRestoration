import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 后端 API 接口代理
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 上传图片静态资源代理（图片URL格式为 /uploads/...）
      '/uploads': {
        target: 'http://localhost:8080/api',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'esnext',
    outDir: 'dist',
    assetsDir: 'assets',
  },
})
