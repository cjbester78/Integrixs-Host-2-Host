import axios from 'axios'

// Determine API base URL - production ready
const getApiBaseUrl = () => {
  // If VITE_API_URL is explicitly set and not empty, use it
  if (import.meta.env.VITE_API_URL && import.meta.env.VITE_API_URL.trim() !== '') {
    return import.meta.env.VITE_API_URL
  }
  
  // Production: Use same origin as frontend
  return window.location.origin
}

const API_BASE_URL = getApiBaseUrl()

// Create axios instance with interceptors
export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor to add auth token
api.interceptors.request.use((config) => {
  // Try sessionStorage first (cleared on browser close), fallback to localStorage
  const token = sessionStorage.getItem('h2h_token') || localStorage.getItem('h2h_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor for error handling  
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const url = error.config?.url || ''
      console.warn('[API] 401 response received:', {
        url,
        method: error.config?.method,
      })
      
      // TEMPORARILY DISABLED - Don't aggressively clear tokens on 401
      // This was causing refresh loops. Let the auth initialization handle token validation
      // if (!url.includes('/auth/login') && !url.includes('/auth/verify')) {
      //   sessionStorage.removeItem('h2h_token')
      //   sessionStorage.removeItem('h2h_user')  
      //   localStorage.removeItem('h2h_token')
      //   localStorage.removeItem('h2h_user')
      // }
    }
    return Promise.reject(error)
  }
)

// Auth API endpoints
export const authApi = {
  login: async (credentials: { username: string; password: string; rememberMe?: boolean }) => {
    const response = await api.post('/api/auth/login', credentials)
    return response.data
  },
  
  refreshToken: async (refreshToken: string) => {
    const response = await api.post('/api/auth/refresh', { refreshToken })
    return response.data
  },
  
  verifyToken: async () => {
    const response = await api.get('/api/auth/verify')
    return response.data
  },
  
  logout: async () => {
    const response = await api.post('/api/auth/logout')
    return response.data
  },
}

// User API endpoints
export const userApi = {
  getCurrentUser: async () => {
    const response = await api.get('/api/users/me')
    return response.data?.data || response.data
  },

  getAllUsers: async () => {
    const response = await api.get('/api/users')
    return response.data?.data || []
  },
  
  createUser: async (userData: any) => {
    const response = await api.post('/api/users', userData)
    return response.data
  },
  
  updateUser: async (userId: string, userData: any) => {
    const response = await api.put(`/api/users/${userId}`, userData)
    return response.data
  },
  
  deleteUser: async (userId: string) => {
    const response = await api.delete(`/api/users/${userId}`)
    return response.data
  },
  
  changePassword: async (userId: string, currentPassword: string, newPassword: string) => {
    const response = await api.put(`/api/users/${userId}/password`, {
      currentPassword,
      newPassword
    })
    return response.data
  },
}

// Dashboard API endpoints
export const dashboardApi = {
  getSystemHealth: async () => {
    const response = await api.get('/api/dashboard/health')
    return response.data
  },
  
  getAdapterStatistics: async () => {
    const response = await api.get('/api/dashboard/adapter-stats')
    return response.data
  },
  
  getRecentExecutions: async (limit = 10) => {
    const response = await api.get(`/api/dashboard/recent-executions?limit=${limit}`)
    return response.data
  },
}

// Configuration API endpoints
export const configApi = {
  getDashboardIntervals: async () => {
    const response = await api.get('/api/configuration/dashboard/intervals')
    return response.data
  },
  
  getAllConfigurations: async () => {
    const response = await api.get('/api/configuration/all')
    return response.data
  },
  
  getConfigurationsByCategory: async (category: string) => {
    const response = await api.get(`/api/configuration/category/${category}`)
    return response.data
  },
  
  updateConfigurationValue: async (configKey: string, value: string) => {
    const response = await api.put(`/api/configuration/key/${configKey}/value`, { value })
    return response.data
  },
  
  resetConfigurationToDefault: async (configKey: string) => {
    const response = await api.put(`/api/configuration/key/${configKey}/reset`)
    return response.data
  },
  
  getConfigurationMetadata: async () => {
    const response = await api.get('/api/configuration/metadata')
    return response.data
  },
  
  getConfigurationStatistics: async () => {
    const response = await api.get('/api/configuration/statistics')
    return response.data
  },
}

// Adapter API endpoints  
export const adapterApi = {
  getAllAdapters: async () => {
    const response = await api.get('/api/adapters')
    return response.data
  },
  
  getAdaptersByPackage: async (packageId: string) => {
    const response = await api.get(`/api/adapters/package/${packageId}`)
    return response.data
  },
  
  getAdapter: async (id: string) => {
    const response = await api.get(`/api/adapters/${id}`)
    return response.data
  },
  
  createAdapter: async (adapterData: any) => {
    const response = await api.post('/api/adapters', adapterData)
    return response.data
  },
  
  updateAdapter: async (id: string, adapterData: any) => {
    const response = await api.put(`/api/adapters/${id}`, adapterData)
    return response.data
  },
  
  deleteAdapter: async (id: string) => {
    const response = await api.delete(`/api/adapters/${id}`)
    return response.data
  },
  
  testAdapter: async (id: string) => {
    const response = await api.post(`/api/adapters/${id}/test`)
    return response.data
  },
  
  executeAdapter: async (id: string) => {
    const response = await api.post(`/api/adapters/${id}/execute`)
    return response.data
  },

  // Adapter Control APIs for monitoring
  startAdapter: async (id: string) => {
    const response = await api.post(`/api/adapters/${id}/start`)
    return response.data
  },

  stopAdapter: async (id: string) => {
    const response = await api.post(`/api/adapters/${id}/stop`)
    return response.data
  },

  getAdapterStatus: async (id: string) => {
    const response = await api.get(`/api/adapters/${id}/status`)
    return response.data
  },

  getAdapterLogs: async (params?: { filter?: string; level?: string; adapterId?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.filter) searchParams.append('filter', params.filter)
    if (params?.level && params.level !== 'all') searchParams.append('level', params.level)
    if (params?.adapterId) searchParams.append('adapterId', params.adapterId)
    
    const url = `/api/adapters/logs${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url)
    return response.data
  },

  // Adapter execution history - get recent executions for specific adapter
  getAdapterExecutionHistory: async (id: string, params?: { limit?: number }) => {
    const searchParams = new URLSearchParams()
    if (params?.limit) searchParams.append('limit', params.limit.toString())
    
    const url = `/api/adapters/${id}/executions${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url)
    return response.data
  },
}

// Interface API endpoints (for flows - where adapters act as interfaces)
export const interfaceApi = {
  getAllInterfaces: async () => {
    const response = await api.get('/api/interfaces')
    return response.data
  },
  
  getInterface: async (id: string) => {
    const response = await api.get(`/api/interfaces/${id}`)
    return response.data
  },
  
  createInterface: async (interfaceData: any) => {
    const response = await api.post('/api/interfaces', interfaceData)
    return response.data
  },
  
  updateInterface: async (id: string, interfaceData: any) => {
    const response = await api.put(`/api/interfaces/${id}`, interfaceData)
    return response.data
  },
  
  deleteInterface: async (id: string) => {
    const response = await api.delete(`/api/interfaces/${id}`)
    return response.data
  },
  
  testInterface: async (id: string) => {
    const response = await api.post(`/api/interfaces/${id}/test`)
    return response.data
  },
  
  executeInterface: async (id: string) => {
    const response = await api.post(`/api/interfaces/${id}/execute`)
    return response.data
  },
}

// File Adapter API endpoints
export const fileAdapterApi = {
  execute: async (adapterId: string, operation: string, parameters: any) => {
    const response = await api.post(`/api/file-adapters/${adapterId}/execute`, {
      operation,
      parameters,
    })
    return response.data
  },
  
  test: async (adapterId: string) => {
    const response = await api.post(`/api/file-adapters/${adapterId}/test`)
    return response.data
  },
  
  getExecutionHistory: async (adapterId: string, limit = 50) => {
    const response = await api.get(`/api/file-adapters/${adapterId}/executions?limit=${limit}`)
    return response.data
  },
}

// SFTP Adapter API endpoints
export const sftpAdapterApi = {
  execute: async (adapterId: string, operation: string, parameters: any) => {
    const response = await api.post(`/api/sftp-adapters/${adapterId}/execute`, {
      operation,
      parameters,
    })
    return response.data
  },
  
  test: async (adapterId: string) => {
    const response = await api.post(`/api/sftp-adapters/${adapterId}/test`)
    return response.data
  },
  
  upload: async (adapterId: string, files: string[]) => {
    const response = await api.post(`/api/sftp-adapters/${adapterId}/upload`, { files })
    return response.data
  },
  
  download: async (adapterId: string, files: string[]) => {
    const response = await api.post(`/api/sftp-adapters/${adapterId}/download`, { files })
    return response.data
  },
}

// Email Adapter API endpoints
export const emailAdapterApi = {
  execute: async (adapterId: string, operation: string, parameters: any) => {
    const response = await api.post(`/api/email-adapters/${adapterId}/execute`, {
      operation,
      parameters,
    })
    return response.data
  },
  
  sendEmail: async (adapterId: string, emailData: any) => {
    const response = await api.post(`/api/email-adapters/${adapterId}/send`, emailData)
    return response.data
  },
  
  sendNotification: async (adapterId: string, notificationData: any) => {
    const response = await api.post(`/api/email-adapters/${adapterId}/notify`, notificationData)
    return response.data
  },
  
  test: async (adapterId: string) => {
    const response = await api.post(`/api/email-adapters/${adapterId}/test`)
    return response.data
  },
}

// SSH Key Management API endpoints
export const sshKeyApi = {
  getAllKeys: async () => {
    const response = await api.get('/api/ssh-keys')
    return response.data.data || []
  },
  
  generateRSAKey: async (keyData: any) => {
    const response = await api.post('/api/ssh-keys/generate/rsa', keyData)
    return response.data
  },
  
  generateDSAKey: async (keyData: any) => {
    const response = await api.post('/api/ssh-keys/generate/dsa', keyData)
    return response.data
  },
  
  importKey: async (keyData: any) => {
    const response = await api.post('/api/ssh-keys/import', keyData)
    return response.data
  },
  
  downloadPrivateKey: async (keyId: string) => {
    const response = await api.get(`/api/ssh-keys/${keyId}/download/private`, {
      responseType: 'blob',
    })
    return response.data
  },
  
  downloadPublicKey: async (keyId: string) => {
    const response = await api.get(`/api/ssh-keys/${keyId}/download/public`, {
      responseType: 'blob',
    })
    return response.data
  },
  
  updateKey: async (keyId: string, keyData: any) => {
    const response = await api.put(`/api/ssh-keys/${keyId}`, keyData)
    return response.data
  },
  
  toggleKeyStatus: async (keyId: string, enabled: boolean) => {
    const response = await api.put(`/api/ssh-keys/${keyId}/status`, { enabled })
    return response.data
  },
  
  deleteKey: async (keyId: string) => {
    const response = await api.delete(`/api/ssh-keys/${keyId}`)
    return response.data
  },
}

// PGP Key Management API endpoints
export const pgpKeyApi = {
  getAllKeys: async () => {
    const response = await api.get('/api/admin/pgp-keys')
    return response.data
  },
  
  getKeyById: async (keyId: string) => {
    const response = await api.get(`/api/admin/pgp-keys/${keyId}`)
    return response.data
  },
  
  generateKey: async (keyData: any) => {
    const response = await api.post('/api/admin/pgp-keys/generate', keyData)
    return response.data
  },
  
  importKey: async (keyData: any) => {
    const response = await api.post('/api/admin/pgp-keys/import', keyData)
    return response.data
  },
  
  exportPublicKey: async (keyId: string) => {
    const response = await api.get(`/api/admin/pgp-keys/${keyId}/export/public`, {
      responseType: 'text',
    })
    return response.data
  },
  
  exportPrivateKey: async (keyId: string) => {
    const response = await api.get(`/api/admin/pgp-keys/${keyId}/export/private`, {
      responseType: 'text',
    })
    return response.data
  },
  
  revokeKey: async (keyId: string, reason: string) => {
    const response = await api.post(`/api/admin/pgp-keys/${keyId}/revoke`, { reason })
    return response.data
  },
  
  deleteKey: async (keyId: string) => {
    const response = await api.delete(`/api/admin/pgp-keys/${keyId}`)
    return response.data
  },
  
  getExpiredKeys: async () => {
    const response = await api.get('/api/admin/pgp-keys/expired')
    return response.data
  },
}



// Flow Configuration API endpoints
export const flowApi = {
  getAllFlows: async () => {
    const response = await api.get('/api/flows')
    return response.data
  },

  getFlowsByPackage: async (packageId: string) => {
    const response = await api.get(`/api/flows/package/${packageId}`)
    return response.data
  },

  getFlowById: async (id: string) => {
    const response = await api.get(`/api/flows/${id}`)
    return response.data
  },

  createFlow: async (flowData: any) => {
    const response = await api.post('/api/flows', flowData)
    return response.data
  },

  updateFlow: async (id: string, flowData: any) => {
    try {
      const response = await api.put(`/api/flows/${id}`, flowData)
      return response.data
    } catch (error: any) {
      console.error('❌ Frontend: Flow update failed:', error)
      // Improve error message for user experience
      if (error.response?.data?.message) {
        const backendMessage = error.response.data.message
        if (backendMessage.includes('deployed')) {
          throw new Error(backendMessage) // Use the improved backend message
        }
      }
      throw error
    }
  },

  deleteFlow: async (id: string) => {
    const response = await api.delete(`/api/flows/${id}`)
    return response.data
  },

  executeFlow: async (id: string) => {
    const response = await api.post(`/api/flows/${id}/execute`)
    return response.data
  },

  exportFlow: async (id: string) => {
    const response = await api.get(`/api/flows/${id}/export`)
    return response.data
  },

  importFlow: async (flowData: any) => {
    const response = await api.post('/api/flows/import', flowData)
    return response.data
  },

  getStatistics: async () => {
    const response = await api.get('/api/flows/statistics')
    return response.data
  },

  validateFlow: async (id: string) => {
    try {
      const response = await api.post(`/api/flows/${id}/validate`)
      return response.data
    } catch (error: any) {
      console.error('❌ Frontend: Flow validation failed:', error)
      console.error('❌ Frontend: Validation error response:', error.response?.data)
      throw error
    }
  },

  setFlowActive: async (id: string, active: boolean) => {
    const response = await api.put(`/api/flows/${id}/active?active=${active}`)
    return response.data
  },

  // Deployment API functions
  deployFlow: async (id: string) => {
    try {
      const response = await api.post(`/api/flows/${id}/deploy`)
      return response.data
    } catch (error: any) {
      console.error('❌ Frontend: Flow deployment failed:', error)
      console.error('❌ Frontend: Error response:', error.response?.data)
      console.error('❌ Frontend: Error status:', error.response?.status)
      throw error
    }
  },

  undeployFlow: async (id: string) => {
    const response = await api.post(`/api/flows/${id}/undeploy`)
    return response.data
  },

  getFlowDeploymentStatus: async (id: string) => {
    const response = await api.get(`/api/flows/${id}/deployment`)
    return response.data
  },

  validateFlowDeployment: async (id: string) => {
    const response = await api.post(`/api/flows/${id}/validate-deployment`)
    return response.data
  }
}

// End-to-End Flows API endpoints
export const endToEndFlowApi = {
  getAllFlows: async () => {
    const response = await api.get('/api/end-to-end-flows')
    return response.data
  },

  getFlowById: async (id: string) => {
    const response = await api.get(`/api/end-to-end-flows/${id}`)
    return response.data
  },

  createFlow: async (flowData: any) => {
    const response = await api.post('/api/end-to-end-flows', flowData)
    return response.data
  },

  updateFlow: async (id: string, flowData: any) => {
    const response = await api.put(`/api/end-to-end-flows/${id}`, flowData)
    return response.data
  },

  deleteFlow: async (id: string) => {
    const response = await api.delete(`/api/end-to-end-flows/${id}`)
    return response.data
  },

  executeFlow: async (id: string) => {
    const response = await api.post(`/api/end-to-end-flows/${id}/execute`)
    return response.data
  },

  configureFlow: async (id: string, config: any) => {
    const response = await api.put(`/api/end-to-end-flows/${id}/config`, config)
    return response.data
  }
}

// Execution Management API endpoints
export const executionApi = {
  getAllExecutions: async (params?: { 
    page?: number; 
    size?: number; 
    status?: string; 
    flowId?: string; 
    dateFrom?: string; 
    dateTo?: string;
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.page) searchParams.append('page', params.page.toString())
    if (params?.size) searchParams.append('size', params.size.toString())
    if (params?.status) searchParams.append('status', params.status)
    if (params?.flowId) searchParams.append('flowId', params.flowId)
    if (params?.dateFrom) searchParams.append('dateFrom', params.dateFrom)
    if (params?.dateTo) searchParams.append('dateTo', params.dateTo)
    
    const url = `/api/executions${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url)
    return response.data
  },

  getExecutionById: async (id: string) => {
    const response = await api.get(`/api/executions/${id}`)
    return response.data
  },

  getExecutionSteps: async (id: string) => {
    const response = await api.get(`/api/executions/${id}/steps`)
    return response.data
  },

  retryExecution: async (id: string) => {
    const response = await api.post(`/api/executions/${id}/retry`)
    return response.data
  },

  cancelExecution: async (id: string) => {
    const response = await api.post(`/api/executions/${id}/cancel`)
    return response.data
  },

  getRunningExecutions: async () => {
    const response = await api.get('/api/executions/running')
    return response.data
  },

  getFailedExecutions: async () => {
    const response = await api.get('/api/executions/failed')
    return response.data
  },

  getExecutionStatistics: async () => {
    const response = await api.get('/api/executions/statistics')
    return response.data
  },

  getExecutionPerformance: async () => {
    const response = await api.get('/api/executions/performance')
    return response.data
  },

  getExecutionsByFlow: async (flowId: string, params?: { page?: number; size?: number; status?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.page) searchParams.append('page', params.page.toString())
    if (params?.size) searchParams.append('size', params.size.toString())
    if (params?.status) searchParams.append('status', params.status)
    
    const url = `/api/flows/${flowId}/executions${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url)
    return response.data
  },

  // Flow execution logging APIs
  getExecutionLogs: async (executionId: string, params?: { filter?: string; level?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.filter) searchParams.append('filter', params.filter)
    if (params?.level && params.level !== 'all') searchParams.append('level', params.level)
    
    const url = `/api/executions/${executionId}/logs${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url)
    return response.data
  }
}

// Transaction Logs API endpoints
export const transactionLogsApi = {
  getRecentLogs: async (params?: { 
    limit?: number; 
    level?: string; 
    category?: string;
    search?: string;
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.limit) searchParams.append('limit', params.limit.toString())
    if (params?.level && params.level !== 'ALL') searchParams.append('level', params.level)
    if (params?.category && params.category !== 'ALL') searchParams.append('category', params.category)
    if (params?.search) searchParams.append('search', params.search)
    
    const url = `/api/transaction-logs/recent${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url)
    return response.data
  },

  exportLogs: async (params?: { 
    limit?: number; 
    level?: string; 
    category?: string;
    format?: string;
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.limit) searchParams.append('limit', params.limit.toString())
    if (params?.level && params.level !== 'ALL') searchParams.append('level', params.level)
    if (params?.category && params.category !== 'ALL') searchParams.append('category', params.category)
    if (params?.format) searchParams.append('format', params.format)
    
    const url = `/api/transaction-logs/export${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url, { responseType: 'blob' })
    return response.data
  }
}

// Environment Configuration API
export const environmentApi = {
  getCurrentEnvironment: async () => {
    const response = await api.get('/api/system/environment')
    return response.data
  },

  updateEnvironment: async (data: {
    type?: string;
    enforceRestrictions?: boolean;
    restrictionMessage?: string;
  }) => {
    const response = await api.put('/api/system/environment', data)
    return response.data
  },

  getEnvironmentTypes: async () => {
    const response = await api.get('/api/system/environment/types')
    return response.data
  },

  checkPermission: async (action: string) => {
    const response = await api.get(`/api/system/environment/permissions/${action}`)
    return response.data
  }
}

// System Logs API endpoints
export const systemLogsApi = {
  getRecentLogs: async (params?: { 
    limit?: number; 
    level?: string; 
    search?: string;
    page?: number;
    size?: number;
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.limit) searchParams.append('limit', params.limit.toString())
    if (params?.level && params.level !== 'ALL') searchParams.append('level', params.level)
    if (params?.search) searchParams.append('search', params.search)
    if (params?.page !== undefined) searchParams.append('page', params.page.toString())
    if (params?.size) searchParams.append('size', params.size.toString())
    
    const url = `/api/logs/system${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url)
    return response.data
  },

  exportLogs: async (params?: { 
    limit?: number; 
    level?: string; 
    category?: string;
    format?: string;
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.limit) searchParams.append('limit', params.limit.toString())
    if (params?.level && params.level !== 'ALL') searchParams.append('level', params.level)
    if (params?.category && params.category !== 'ALL') searchParams.append('category', params.category)
    if (params?.format) searchParams.append('format', params.format)
    
    const url = `/api/logs/system/export${searchParams.toString() ? `?${searchParams.toString()}` : ''}`
    const response = await api.get(url, { responseType: 'blob' })
    return response.data
  }
}

export default api