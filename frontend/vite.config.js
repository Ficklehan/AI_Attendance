import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

const API_BASE_PATH = '/attendance/api'
const API_DEV_SERVER_ORIGIN = 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '~': path.resolve(__dirname, 'src'),
    },
  },
  
  server: {
    port: 5175,
    strictPort: true,
    host: '0.0.0.0',
    proxy: {
      [API_BASE_PATH]: {
        target: API_DEV_SERVER_ORIGIN,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            if (proxyRes.headers['content-type'] && 
                proxyRes.headers['content-type'].includes('text/event-stream')) {
              proxyRes.headers['cache-control'] = 'no-cache';
              proxyRes.headers['x-accel-buffering'] = 'no';
            }
          });
        },
      },
    },
  },
  
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
  },
  
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`,
      },
    },
  },
})