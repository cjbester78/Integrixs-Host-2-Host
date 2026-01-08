import React, { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Users, Key, Shield, Settings, FileText, Database, Lock, Clock } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { usePermissions } from '@/hooks/auth'
import { useAuthStore } from '@/stores/auth'
import { cn } from '@/lib/utils'

// Import existing components
import UserManagement from './users/UserManagement'
import SshKeyManagement from './ssh-keys/SshKeyManagement'

// New components for the admin page
import SystemConfigurations from './admin/SystemConfigurations'
import LogsPage from './admin/LogsPage'
import PgpKeys from './admin/PgpKeys'
import DataRetentionManagement from './admin/DataRetentionManagement'

type AdminTab = 'users' | 'ssh-keys' | 'pgp-keys' | 'system-config' | 'logs' | 'data-retention'

const adminTabs: { id: AdminTab; label: string; icon: React.ComponentType<{ className?: string }>; description: string; adminOnly?: boolean }[] = [
  {
    id: 'users',
    label: 'Users',
    icon: Users,
    description: 'Manage user accounts, roles, and permissions',
    adminOnly: true
  },
  {
    id: 'ssh-keys',
    label: 'SSH Keys',
    icon: Key,
    description: 'Generate and manage SSH key pairs for SFTP adapters',
    adminOnly: true
  },
  {
    id: 'pgp-keys',
    label: 'PGP Keys',
    icon: Lock,
    description: 'Manage PGP encryption keys for secure file transfers',
    adminOnly: true
  },
  {
    id: 'system-config',
    label: 'System Configuration',
    icon: Settings,
    description: 'Configure system settings, logging, and performance parameters',
    adminOnly: true
  },
  {
    id: 'logs',
    label: 'Logs',
    icon: FileText,
    description: 'View and manage system and transactional logs'
    // No adminOnly - available to viewers
  },
  {
    id: 'data-retention',
    label: 'Data Retention',
    icon: Clock,
    description: 'Configure automated cleanup policies for logs and database tables',
    adminOnly: true
  }
]

const AdminPage: React.FC = () => {
  const { isAdmin } = usePermissions()
  const { user } = useAuthStore()
  const [searchParams, setSearchParams] = useSearchParams()
  
  // Filter tabs based on user role
  const availableTabs = adminTabs.filter(tab => !tab.adminOnly || isAdmin())
  
  // Set default tab based on role
  const defaultTab: AdminTab = isAdmin() ? 'users' : 'logs'
  const [activeTab, setActiveTab] = useState<AdminTab>(defaultTab)

  // Handle URL parameters to set active tab
  useEffect(() => {
    const tabParam = searchParams.get('tab') as AdminTab
    if (tabParam && availableTabs.some(tab => tab.id === tabParam)) {
      setActiveTab(tabParam)
    } else if (!isAdmin()) {
      // Redirect viewers to logs if they try to access admin-only tabs
      setActiveTab('logs')
      setSearchParams({ tab: 'logs' })
    }
  }, [searchParams, availableTabs, isAdmin])

  // Update URL when tab changes
  const handleTabChange = (tabId: AdminTab) => {
    setActiveTab(tabId)
    setSearchParams({ tab: tabId })
  }

  // Check if user has any access (admin or viewer with logs access)
  if (!user) {
    return (
      <div className="content-spacing">
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Shield className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">Access Denied</h3>
              <p className="text-muted-foreground">Authentication required to access this section</p>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  const renderTabContent = () => {
    switch (activeTab) {
      case 'users':
        return <UserManagement />
      case 'ssh-keys':
        return <SshKeyManagement />
      case 'pgp-keys':
        return <PgpKeys />
      case 'system-config':
        return <SystemConfigurations />
      case 'logs':
        return <LogsPage />
      case 'data-retention':
        return <DataRetentionManagement />
      default:
        return <UserManagement />
    }
  }

  return (
    <div className="content-spacing">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center space-x-3 mb-2">
          <Database className="h-8 w-8 text-primary" />
          <h1 className="text-3xl font-bold text-foreground">
            {isAdmin() ? 'System Administration' : 'System Logs'}
          </h1>
        </div>
        <p className="text-muted-foreground">
          {isAdmin() 
            ? 'Centralized administration for users, security, configuration, and monitoring'
            : 'Monitor system and transactional logs for application insights and debugging'
          }
        </p>
      </div>

      {/* Tab Navigation */}
      <div className="mb-8">
        <div className={`grid gap-4 ${availableTabs.length === 1 ? 'grid-cols-1 max-w-md' : availableTabs.length <= 3 ? 'grid-cols-1 md:grid-cols-3' : 'grid-cols-2 md:grid-cols-6'}`}>
          {availableTabs.map((tab) => {
            const Icon = tab.icon
            const isActive = activeTab === tab.id
            
            return (
              <button
                key={tab.id}
                onClick={() => handleTabChange(tab.id)}
                className={cn(
                  "text-left p-4 rounded-lg border transition-all duration-300 hover:scale-[1.02]",
                  "flex flex-col space-y-2 min-h-[120px]",
                  isActive
                    ? "bg-primary/10 border-primary text-primary shadow-elegant"
                    : "bg-card border-border hover:bg-accent/50 hover:border-primary/30"
                )}
              >
                <div className="flex items-center space-x-2">
                  <Icon className={cn(
                    "h-5 w-5 transition-colors",
                    isActive ? "text-primary" : "text-muted-foreground"
                  )} />
                  <span className={cn(
                    "font-medium transition-colors",
                    isActive ? "text-primary" : "text-foreground"
                  )}>
                    {tab.label}
                  </span>
                </div>
                <p className={cn(
                  "text-xs leading-relaxed transition-colors",
                  isActive ? "text-primary/80" : "text-muted-foreground"
                )}>
                  {tab.description}
                </p>
              </button>
            )
          })}
        </div>
      </div>

      {/* Tab Content */}
      <div className="transition-all duration-300">
        {renderTabContent()}
      </div>
    </div>
  )
}

export default AdminPage