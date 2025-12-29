import { useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { Toaster } from 'react-hot-toast'

import useAuthInit from './hooks/useAuthInit'

// Components
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'

// Pages
import LoginPage from './pages/auth/LoginPage'
import { Dashboard } from './pages/Dashboard'
import AdapterConfiguration from './pages/adapters/AdapterConfiguration'
import AdapterList from './pages/adapters/AdapterList'
import CreateSshKey from './pages/ssh-keys/CreateSshKey'
import FlowManagement from './pages/flows/FlowManagement'
import VisualFlowBuilder from './pages/flows/VisualFlowBuilder'
import AdapterMonitoring from './pages/AdapterMonitoring'
import FlowMonitoring from './pages/FlowMonitoring'
import ExecutionLogsViewer from './pages/ExecutionLogsViewer'
import AdminPage from './pages/AdminPage'
import { Settings } from './pages/Settings'

// Initialize theme on app load
const initializeTheme = () => {
  const savedPreferences = localStorage.getItem('h2h_user_preferences')
  let theme: 'light' | 'dark' | 'system' = 'light' // Default to light
  
  if (savedPreferences) {
    try {
      const parsed = JSON.parse(savedPreferences)
      theme = parsed.theme || 'light'
    } catch (error) {
      console.warn('Failed to parse saved preferences for theme')
    }
  }
  
  const root = document.documentElement
  if (theme === 'system') {
    const systemPrefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    root.classList.toggle('dark', systemPrefersDark)
  } else if (theme === 'dark') {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
}

function App() {
  useAuthInit()
  
  useEffect(() => {
    initializeTheme()
  }, [])

  return (
    <div className="min-h-screen bg-background">
      <Routes>
            {/* Public routes */}
            <Route path="/login" element={<LoginPage />} />
            
            {/* Protected routes */}
            <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<Dashboard />} />
              
              {/* Adapter management */}
              <Route path="adapters" element={<AdapterList />} />
              <Route path="adapters/create" element={<AdapterConfiguration />} />
              <Route path="adapters/:id/edit" element={<AdapterConfiguration />} />
              
              
              {/* Admin page - centralized administration */}
              <Route path="admin" element={<AdminPage />} />
              
              {/* SSH key management - redirect to admin */}
              <Route path="ssh-keys" element={<Navigate to="/admin" replace />} />
              <Route path="ssh-keys/create" element={<CreateSshKey />} />
              
              {/* User management - redirect to admin */}
              <Route path="users" element={<Navigate to="/admin" replace />} />
              
              {/* Flow management (admin only) */}
              <Route path="flows" element={<FlowManagement />} />
              <Route path="flows/create" element={<VisualFlowBuilder />} />
              <Route path="flows/:id/visual" element={<VisualFlowBuilder />} />
              
              {/* Monitoring */}
              <Route path="adapter-monitoring" element={<AdapterMonitoring />} />
              <Route path="flow-monitoring" element={<FlowMonitoring />} />
              <Route path="execution-logs/:executionId" element={<ExecutionLogsViewer />} />
              
              {/* User Settings */}
              <Route path="settings" element={<Settings />} />
              
              {/* Legacy route redirects */}
              <Route path="monitoring" element={<Navigate to="/adapter-monitoring" replace />} />
              <Route path="executions" element={<Navigate to="/flow-monitoring" replace />} />
              <Route path="execution-monitoring" element={<Navigate to="/flow-monitoring" replace />} />
              <Route path="performance" element={<Navigate to="/dashboard" replace />} />
              <Route path="logs" element={<Navigate to="/admin" replace />} />
            </Route>
            
            {/* Catch all - redirect to dashboard */}
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
          
          {/* Global notifications */}
          <Toaster 
            position="top-right"
            toastOptions={{
              duration: 4000,
              style: {
                background: 'hsl(215, 25%, 10%)',
                color: 'hsl(210, 40%, 98%)',
              },
              success: {
                style: {
                  background: 'hsl(142, 76%, 36%)',
                  color: 'hsl(210, 40%, 98%)',
                },
              },
              error: {
                style: {
                  background: 'hsl(0, 84%, 60%)',
                  color: 'hsl(210, 40%, 98%)',
                },
              },
            }}
          />
        
        {/* React Query DevTools - only in development */}
        {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
      </div>
  )
}

export default App