import { useQuery } from '@tanstack/react-query'
import { environmentApi } from '@/lib/api'
import { useAuthStore } from '@/stores/auth'

interface Environment {
  type: string
  displayName: string
  description: string
  enforceRestrictions: boolean
  restrictionMessage: string
  permissions: {
    canCreateFlows: boolean
    canCreateAdapters: boolean
    canModifyAdapterConfig: boolean
    canImportExportFlows: boolean
    canDeployFlows: boolean
  }
}

export const useEnvironment = () => {
  const { isAuthenticated } = useAuthStore()
  
  return useQuery({
    queryKey: ['environment'],
    queryFn: async () => {
      const response = await environmentApi.getCurrentEnvironment()
      return response.data as Environment
    },
    enabled: isAuthenticated, // Only run query if user is authenticated
    refetchInterval: 30000, // Refetch every 30 seconds
    staleTime: 10000, // Consider data stale after 10 seconds
    retry: (failureCount, error: any) => {
      // Don't retry on authentication errors
      if (error?.response?.status === 401 || error?.response?.status === 403) {
        return false
      }
      return failureCount < 3
    }
  })
}

// Helper function to get environment-specific styles
export const getEnvironmentStyles = (envType: string) => {
  const baseClasses = "inline-flex items-center gap-1 rounded-md border font-mono font-bold uppercase tracking-wide"
  
  switch (envType?.toUpperCase()) {
    case 'PRODUCTION':
      return {
        className: `${baseClasses} bg-red-100 text-red-800 border-red-200`,
        pulseClass: 'animate-pulse bg-red-500'
      }
    case 'QUALITY_ASSURANCE':
      return {
        className: `${baseClasses} bg-purple-100 text-purple-800 border-purple-200`,
        pulseClass: 'animate-pulse bg-purple-500'
      }
    case 'DEVELOPMENT':
    default:
      return {
        className: `${baseClasses} bg-green-100 text-green-800 border-green-200`,
        pulseClass: 'animate-pulse bg-green-500'
      }
  }
}