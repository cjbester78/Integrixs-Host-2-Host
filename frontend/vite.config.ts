import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { componentTagger } from 'lovable-tagger'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    mode === 'development' && componentTagger(),
  ].filter(Boolean),
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom'],
          router: ['react-router-dom'],
          charts: ['recharts'],
        },
      },
    },
  },
  server: {
    host: '::',
    port: 8080,
    proxy: {
      '/api': {
        target: 'https://nonportable-astrictively-lorelai.ngrok-free.dev',
        changeOrigin: true,
        secure: false,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            // Remove Origin header to bypass CORS check
            proxyReq.removeHeader('Origin');
            proxyReq.setHeader('ngrok-skip-browser-warning', 'true');
          });
        },
      },
      '/ws': {
        target: 'wss://nonportable-astrictively-lorelai.ngrok-free.dev',
        ws: true,
        secure: false,
      },
    },
  },
}))
