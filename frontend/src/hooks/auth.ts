import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi, userApi } from '@/lib/api'
import { useAuthStore } from '@/stores/auth'
import { useNotifications } from '@/stores/ui'

export const useAuth = () => {
  const { user, token, isAuthenticated, setAuth, clearAuth, setLoading } = useAuthStore()
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()

  // Login mutation
  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onMutate: () => {
      setLoading(true)
    },
    onSuccess: (response) => {
      // Backend returns: { accessToken, refreshToken, username, fullName, role }
      console.log('[Auth] Login response:', response)
      
      if (response.accessToken && response.username) {
        const user = {
          id: response.userId || response.username, // fallback to username if no ID
          username: response.username,
          firstName: response.fullName?.split(' ')[0] || response.username,
          lastName: response.fullName?.split(' ')[1] || '',
          email: response.email || `${response.username}@h2h.com`, // fallback email
          role: response.role,
          enabled: true,
          createdAt: new Date().toISOString(),
          lastLogin: new Date().toISOString()
        }
        
        setAuth(user, response.accessToken)
        success('Welcome back!', `Hello ${user.firstName}`)
        queryClient.invalidateQueries({ queryKey: ['currentUser'] })
      } else {
        console.error('[Auth] Invalid login response structure:', response)
        error('Login Failed', 'Invalid response from server')
      }
      setLoading(false)
    },
    onError: (err: any) => {
      const message = err.response?.data?.message || 'Login failed. Please check your credentials.'
      error('Login Failed', message)
      setLoading(false)
    },
  })

  // Logout mutation
  const logoutMutation = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      clearAuth()
      queryClient.clear()
      success('Logged out', 'Successfully logged out')
    },
    onError: () => {
      // Still clear auth even if logout API fails
      clearAuth()
      queryClient.clear()
      error('Logout Error', 'There was an issue logging out, but you have been signed out locally')
    },
  })

  // Refresh token mutation
  const refreshMutation = useMutation({
    mutationFn: (refreshToken: string) => authApi.refreshToken(refreshToken),
    onSuccess: (response) => {
      if (response.success && response.data.user && response.data.token) {
        setAuth(response.data.user, response.data.token)
      }
    },
    onError: () => {
      clearAuth()
      queryClient.clear()
    },
  })

  // Get current user query
  const currentUserQuery = useQuery({
    queryKey: ['currentUser'],
    queryFn: userApi.getCurrentUser,
    enabled: !!token && isAuthenticated,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
  })

  const login = (credentials: { username: string; password: string }) => {
    return loginMutation.mutate(credentials)
  }

  const logout = () => {
    return logoutMutation.mutate()
  }

  const refreshToken = (refreshToken: string) => {
    return refreshMutation.mutate(refreshToken)
  }

  return {
    // State
    user,
    token,
    isAuthenticated,
    isLoading: loginMutation.isPending || logoutMutation.isPending || refreshMutation.isPending,
    
    // Actions
    login,
    logout,
    refreshToken,
    
    // Mutations
    loginMutation,
    logoutMutation,
    refreshMutation,
    
    // Queries
    currentUserQuery,
  }
}

// Hook for checking if user has specific roles
export const usePermissions = () => {
  const { user } = useAuthStore()

  const hasRole = (role: string | string[]) => {
    if (!user) return false
    
    const roles = Array.isArray(role) ? role : [role]
    return roles.includes(user.role)
  }

  const isAdmin = () => hasRole('ADMINISTRATOR')
  const isViewer = () => hasRole('VIEWER')

  return {
    hasRole,
    isAdmin,
    isViewer,
    currentRole: user?.role,
  }
}