import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { sessionManager } from '@/lib/session'
import { initializeWebSocket, disconnectWebSocket } from '@/services/websocket'

export interface User {
  id: string
  username: string
  firstName: string
  lastName: string
  email: string
  role: 'ADMINISTRATOR' | 'VIEWER'
  enabled: boolean
  createdAt: string
  lastLogin?: string
}

interface AuthState {
  user: User | null
  token: string | null
  isAuthenticated: boolean
  isLoading: boolean
  hasInitialized: boolean
  persistLogin: boolean
  
  // Actions
  setAuth: (user: User, token: string, persistLogin?: boolean) => void
  clearAuth: () => void
  setLoading: (loading: boolean) => void
  setInitialized: (initialized: boolean) => void
  updateUser: (user: User) => void
  setPersistLogin: (persist: boolean) => void
}

// Helper function to initialize auth state from localStorage immediately
const getInitialAuthState = () => {
  try {
    const persistLogin = localStorage.getItem('h2h_persist_login') === 'true'
    
    const token = sessionManager.getToken()
    const user = sessionManager.getUser()
    
    // Check if token is locally valid (not expired)
    let isTokenLocallyValid = false
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        const now = Date.now() / 1000
        isTokenLocallyValid = payload.exp > now
      } catch (error) {
        // Token parsing failed, will be treated as invalid
      }
    }
    
    // Based on research: Set isAuthenticated to true if we have valid tokens
    // This prevents the immediate redirect during page refresh
    const hasValidLocalAuth = !!(token && user && isTokenLocallyValid)
    
    return {
      user,
      token,
      // KEY FIX: Set to true if we have valid tokens to prevent redirect
      isAuthenticated: hasValidLocalAuth,
      persistLogin,
      hasInitialized: false, // useAuthInit will still run for backend verification
      isLoading: false, // Don't show loading if we have tokens
    }
  } catch (error) {
    console.warn('Failed to initialize auth state from storage:', error)
    return {
      user: null,
      token: null,
      isAuthenticated: false,
      persistLogin: false,
      hasInitialized: false,
      isLoading: true,
    }
  }
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      ...getInitialAuthState(), // Initialize from storage immediately
      
      setAuth: (user: User, token: string, persistLogin = false) => {
        set({
          user,
          token,
          isAuthenticated: true,
          isLoading: false,
          hasInitialized: true,
          persistLogin,
        })
        // Use session manager for token storage
        sessionManager.setToken(token, user, persistLogin)
        
        // Initialize WebSocket connection when user logs in
        initializeWebSocket().catch((error) => {
          console.warn('[Auth Store] Failed to initialize WebSocket:', error)
        })
      },
      
      clearAuth: () => {
        set({
          user: null,
          token: null,
          isAuthenticated: false,
          isLoading: false,
          hasInitialized: true,
          persistLogin: false,
        })
        // Don't call sessionManager.logout() here - it causes redirect loop
        // Just clear local state; actual logout should handle redirect
        
        // Disconnect WebSocket when user logs out
        disconnectWebSocket()
      },
      
      setLoading: (loading: boolean) => {
        set({ isLoading: loading })
      },
      
      setInitialized: (initialized: boolean) => {
        set({ hasInitialized: initialized, isLoading: false })
      },
      
      updateUser: (user: User) => {
        set({ user })
        // Update user in session manager
        const currentToken = get().token
        if (currentToken) {
          sessionManager.setToken(currentToken, user, get().persistLogin)
        }
      },
      
      setPersistLogin: (persist: boolean) => {
        set({ persistLogin: persist })
      },
    }),
    {
      name: 'h2h-auth',
      partialize: (state) => ({
        // Only persist basic auth state, let sessionManager handle tokens
        persistLogin: state.persistLogin,
      }),
    }
  )
)