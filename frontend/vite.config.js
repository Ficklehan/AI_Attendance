import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { vitePluginSharedCjs } from './vite-plugin-shared-cjs.mjs'

const API_BASE_PATH = '/clockai/api'
const API_DEV_SERVER_ORIGIN = 'http://localhost:8080'

function redirectClockaiRoot() {
  return {
    name: 'redirect-clockai-root',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url?.split('?')[0] ?? ''
        const query = req.url?.includes('?') ? req.url.slice(req.url.indexOf('?')) : ''
        if (url === '/clockai' || url === '/clockai/') {
          res.writeHead(301, { Location: `/clockai/home${query}` })
          res.end()
          return
        }
        if (url === '/attendance' || url === '/attendance/') {
          res.writeHead(301, { Location: `/clockai/home${query}` })
          res.end()
          return
        }
        next()
      })
    },
    configurePreviewServer(server) {
      redirectClockaiRoot().configureServer(server)
    },
  }
}

const sharedJsDir = path.resolve(__dirname, '../shared/js')

export default defineConfig({
  plugins: [vitePluginSharedCjs(sharedJsDir), vue(), redirectClockaiRoot()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '~': path.resolve(__dirname, 'src'),
      '@shared': path.resolve(__dirname, '../shared/js'),
    },
  },
  
  server: {
    port: 5175,
    strictPort: true,
    host: '0.0.0.0',
    fs: {
      allow: [path.resolve(__dirname, '..')],
    },
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
  
  base: '/clockai/',

  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
    commonjsOptions: {
      include: [/shared\/js/, /node_modules/],
    },
  },
  
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`,
      },
    },
  },
})