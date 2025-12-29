import { useEffect } from 'react'
import { useAuthStore } from '@/stores/auth'
import { sessionManager } from '@/lib/session'
import { authApi } from '@/lib/api'
import { initializeWebSocket, disconnectWebSocket } from '@/services/websocket'

/**
 * Check if JWT token is still valid (not expired)
 */
const isTokenValid = (token: string): boolean => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    const now = Date.now() / 1000
    return payload.exp > now
  } catch (error) {
    console.warn('[JWT] Failed to parse token:', error)
    return false
  }
}

/**
 * Hook to initialize authentication state on app startup
 * Restores session from storage and validates token
 */
export const useAuthInit = () => {
  const { setAuth, setInitialized, hasInitialized } = useAuthStore()

  useEffect(() => {
    // Only run initialization once
    if (hasInitialized) {
      return
    }

    const initializeAuth = async () => {
      console.log('[useAuthInit] Starting auth initialization...')

      try {
        // Check if we have a token in storage
        const token = sessionManager.getToken()
        const user = sessionManager.getUser()

        console.log('[useAuthInit] Token exists:', !!token, 'User exists:', !!user)

        if (!token || !user) {
          // No stored authentication
          console.log('[useAuthInit] No stored credentials, marking as initialized')
          setInitialized(true)
          return
        }

        // First, try to restore user from localStorage to avoid delay during network verification
        const localUser = sessionManager.getUser()
        if (localUser && token) {
          const persistLogin = sessionManager.isPersistentLogin()
          setAuth(localUser, token, persistLogin)
          console.log('[useAuthInit] Restored user from localStorage immediately')
        }

        // Check token validity locally first (faster and more reliable)
        if (!isTokenValid(token)) {
          console.log('[useAuthInit] Token is expired, clearing storage')
          sessionManager.clearTokens()
          setInitialized(true)
          disconnectWebSocket()
          return
        }

        // Backend verification to check for signature mismatches after app restart
        // This handles cases where app restarted and JWT secret changed
        try {
          const response = await authApi.verifyToken()
          if (!response || !response.valid) {
            console.log('[useAuthInit] Token signature invalid (app restart), clearing storage')
            sessionManager.clearTokens()
            setInitialized(true)
            disconnectWebSocket()
            return
          }
          console.log('[useAuthInit] Token verified with backend successfully')
        } catch (error: any) {
          if (error?.response?.status === 401) {
            console.log('[useAuthInit] Token rejected by backend (signature mismatch), clearing storage')
            sessionManager.clearTokens()
            setInitialized(true)
            disconnectWebSocket()
            return
          }
          // For other errors (network, etc), continue with local validation
          console.warn('[useAuthInit] Backend verification failed (non-blocking):', error)
        }

        // Token is valid locally, restore authentication state
        if (localUser) {
          console.log('[useAuthInit] Token valid, auth state already restored from localStorage')
        } else {
          // Fallback: restore from token if no localStorage user
          const persistLogin = sessionManager.isPersistentLogin()
          setAuth(user, token, persistLogin)
          console.log('[useAuthInit] Token valid, auth restored from session data')
        }

        // Initialize WebSocket connection after successful auth
        try {
          await initializeWebSocket()
          console.log('[useAuthInit] WebSocket initialized')
        } catch (error) {
          console.warn('[useAuthInit] WebSocket failed:', error)
        }
        
        // Mark initialization complete
        setInitialized(true)
      } catch (error) {
        console.error('[useAuthInit] Initialization failed:', error)
        setInitialized(true)
        disconnectWebSocket()
      }
    }

    initializeAuth()
  }, [setAuth, setInitialized, hasInitialized])
}

export default useAuthInit