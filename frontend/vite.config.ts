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
    chunkSizeWarningLimit: 1000, // Increase warning limit to 1000kb
    rollupOptions: {
      output: {
        manualChunks: {
          // Core React libraries
          vendor: ['react', 'react-dom'],
          
          // Routing
          router: ['react-router-dom'],
          
          // Charts and visualization
          charts: ['recharts'],
          
          // React Flow (large library)
          reactflow: ['@xyflow/react', 'dagre'],
          
          // React Query and data management
          query: ['@tanstack/react-query', '@tanstack/react-query-devtools', 'axios'],
          
          // UI component libraries (Radix UI is large)
          ui: [
            '@radix-ui/react-accordion',
            '@radix-ui/react-alert-dialog', 
            '@radix-ui/react-dialog',
            '@radix-ui/react-dropdown-menu',
            '@radix-ui/react-label',
            '@radix-ui/react-select',
            '@radix-ui/react-separator',
            '@radix-ui/react-slot',
            '@radix-ui/react-switch',
            '@radix-ui/react-tabs',
            '@radix-ui/react-toast',
            '@radix-ui/react-tooltip'
          ],
          
          // Form handling and utilities
          forms: ['react-hook-form', 'zustand'],
          
          // WebSocket and real-time communication
          websocket: ['@stomp/stompjs', 'sockjs-client'],
          
          // Utilities and smaller libraries
          utils: [
            'lucide-react',
            'clsx',
            'class-variance-authority',
            'tailwind-merge',
            'date-fns',
            'react-hot-toast',
            'sonner',
            'cmdk'
          ]
        },
      },
    },
  },
  server: {
    host: '::',
    port: 3000,
    proxy: {
      '/api': {
        target: 'https://localhost:8443',
        changeOrigin: true,
        secure: false, // Allow self-signed certificates in development
      },
      '/ws': {
        target: 'wss://localhost:8443',
        ws: true,
        secure: false, // Allow self-signed certificates in development
      },
    },
  },
}))
