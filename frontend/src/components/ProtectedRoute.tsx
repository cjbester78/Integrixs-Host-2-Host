import React from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/auth'

interface ProtectedRouteProps {
  children: React.ReactNode
  roles?: string[]
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, roles }) => {
  const { isAuthenticated, user, isLoading, hasInitialized } = useAuthStore()

  console.log('[ProtectedRoute] render', {
    isAuthenticated,
    isLoading,
    hasInitialized,
    userRole: user?.role,
  })

  // CRITICAL: Prevent premature redirect - wait for initialization to complete
  // This prevents the redirect loop on browser refresh described in research
  if (isLoading || !hasInitialized) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-slate-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Verifying authentication...</p>
        </div>
      </div>
    )
  }

  // Only redirect after auth state is fully loaded and verified
  if (!isAuthenticated) {
    console.log('[ProtectedRoute] Authentication verification complete - not authenticated, redirecting to login')
    return <Navigate to="/login" replace />
  }

  // Check role-based access if roles are specified
  if (roles && user && !roles.includes(user.role)) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-red-600 mb-2">Access Denied</h1>
          <p className="text-gray-600">You don't have permission to access this page.</p>
        </div>
      </div>
    )
  }

  return <>{children}</>
}

export default ProtectedRoute