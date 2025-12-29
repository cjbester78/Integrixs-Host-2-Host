/**
 * Session management utilities for handling authentication tokens
 * and browser close detection
 */

export interface SessionConfig {
  persistLogin: boolean
  sessionTimeout: number // in milliseconds
  heartbeatInterval: number // in milliseconds
}

const DEFAULT_CONFIG: SessionConfig = {
  persistLogin: false,
  sessionTimeout: 24 * 60 * 60 * 1000, // 24 hours
  heartbeatInterval: 5 * 60 * 1000, // 5 minutes
}

class SessionManager {
  private config: SessionConfig
  private heartbeatTimer?: number
  private sessionTimeoutTimer?: number
  private isVerifying: boolean = false // Mutex for token verification

  constructor(config: Partial<SessionConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config }
    this.setupSessionManagement()
  }

  private setupSessionManagement() {
    // Simple session management without complex refresh detection
    window.addEventListener('beforeunload', this.handleBeforeUnload.bind(this))
    
    // Page visibility change (tab switching, minimizing)
    document.addEventListener('visibilitychange', this.handleVisibilityChange.bind(this))
    
    // Start heartbeat if user is logged in
    if (this.isLoggedIn()) {
      this.startHeartbeat()
    }
  }

  private handleBeforeUnload(_event: BeforeUnloadEvent) {
    console.log('[SessionManager] beforeunload triggered, persistLogin:', this.config.persistLogin)
    
    // Simple approach: sessionStorage automatically handles browser termination
    // We don't need to clear anything here - let browser handle it naturally
    // - sessionStorage: cleared automatically on browser termination
    // - localStorage: persists only when user explicitly chose "Remember Me"
  }

  private handleVisibilityChange() {
    if (document.hidden) {
      // Tab became hidden, pause heartbeat
      this.stopHeartbeat()
    } else {
      // Tab became visible, resume heartbeat if logged in
      if (this.isLoggedIn()) {
        this.startHeartbeat()
      }
    }
  }

  public setToken(token: string, user: any, persistLogin: boolean = false) {
    console.log('[SessionManager] setToken called with persistLogin:', persistLogin)
    this.config.persistLogin = persistLogin
    
    if (persistLogin) {
      // Store in localStorage for persistence across browser sessions
      console.log('[SessionManager] Storing in localStorage (persistent)')
      localStorage.setItem('h2h_token', token)
      localStorage.setItem('h2h_user', JSON.stringify(user))
      localStorage.setItem('h2h_persist_login', 'true')
      // Clear sessionStorage to avoid conflicts
      sessionStorage.removeItem('h2h_token')
      sessionStorage.removeItem('h2h_user')
    } else {
      // Store in sessionStorage (cleared on browser close)
      console.log('[SessionManager] Storing in sessionStorage (temporary)')
      sessionStorage.setItem('h2h_token', token)
      sessionStorage.setItem('h2h_user', JSON.stringify(user))
      // Clear any persistent data
      localStorage.removeItem('h2h_token')
      localStorage.removeItem('h2h_user')
      localStorage.removeItem('h2h_persist_login')
    }
    
    this.startHeartbeat()
    this.startSessionTimeout()
  }

  public getToken(): string | null {
    return sessionStorage.getItem('h2h_token') || localStorage.getItem('h2h_token')
  }

  public getUser(): any | null {
    const userStr = sessionStorage.getItem('h2h_user') || localStorage.getItem('h2h_user')
    return userStr ? JSON.parse(userStr) : null
  }

  public isLoggedIn(): boolean {
    return !!this.getToken()
  }

  public isPersistentLogin(): boolean {
    return localStorage.getItem('h2h_persist_login') === 'true'
  }

  public logout() {
    this.clearAllTokens()
    this.stopHeartbeat()
    this.stopSessionTimeout()
    
    // Redirect to login
    window.location.href = '/login'
  }

  public clearTokens() {
    this.clearAllTokens()
    this.stopHeartbeat()
    this.stopSessionTimeout()
  }

  public forceLogout(reason: string = 'Session expired') {
    console.warn('Force logout:', reason)
    this.logout()
  }


  private clearAllTokens() {
    // Clear both session and persistent tokens
    sessionStorage.removeItem('h2h_token')
    sessionStorage.removeItem('h2h_user')
    localStorage.removeItem('h2h_token')
    localStorage.removeItem('h2h_user')
    localStorage.removeItem('h2h_persist_login')
  }

  private startHeartbeat() {
    this.stopHeartbeat() // Clear any existing timer
    
    this.heartbeatTimer = window.setInterval(() => {
      // Use mutex pattern to prevent concurrent heartbeat calls
      if (!this.isVerifying) {
        this.performHeartbeat()
      }
    }, this.config.heartbeatInterval)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      window.clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = undefined
    }
  }

  private startSessionTimeout() {
    this.stopSessionTimeout() // Clear any existing timer
    
    this.sessionTimeoutTimer = window.setTimeout(() => {
      this.forceLogout('Session timeout')
    }, this.config.sessionTimeout)
  }

  private stopSessionTimeout() {
    if (this.sessionTimeoutTimer) {
      window.clearTimeout(this.sessionTimeoutTimer)
      this.sessionTimeoutTimer = undefined
    }
  }

  private async performHeartbeat() {
    // Use mutex to prevent concurrent heartbeat operations
    if (this.isVerifying) {
      return
    }
    
    this.isVerifying = true
    
    try {
      const token = this.getToken()
      if (!token) {
        this.forceLogout('No token found')
        return
      }

      // Make a lightweight API call to verify token validity
      const response = await fetch('/api/auth/verify', {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      })

      if (!response.ok) {
        if (response.status === 401) {
          this.forceLogout('Invalid token')
        }
        return
      }

      // Reset session timeout on successful heartbeat
      this.startSessionTimeout()
      
    } catch (error) {
      console.warn('Heartbeat failed:', error)
      // Don't force logout on network errors, just log
    } finally {
      this.isVerifying = false
    }
  }

  public refreshSession() {
    // Reset session timeout when user is active
    if (this.isLoggedIn()) {
      this.startSessionTimeout()
    }
  }
}

// Create singleton instance
export const sessionManager = new SessionManager()

// Activity detection for session refresh
const ACTIVITY_EVENTS = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart']
let lastActivity = Date.now()

ACTIVITY_EVENTS.forEach(event => {
  document.addEventListener(event, () => {
    const now = Date.now()
    // Only refresh session if there's been significant inactivity (> 1 minute)
    if (now - lastActivity > 60000) {
      sessionManager.refreshSession()
    }
    lastActivity = now
  }, true)
})

export default sessionManager