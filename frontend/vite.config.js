import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // 使用官方 Vue 插件处理 .vue 单文件组件。
  plugins: [vue()],
  server: {
    // 本地开发服务器端口，并将 API 请求转发到独立的 Spring Boot 后端。
    port: 4173,
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        // 将浏览器客户端 IP 传给后端登录保护；后端只信任本机/私网代理。
        xfwd: true
      }
    }
  }
})
