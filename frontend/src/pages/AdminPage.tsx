import React, { useState } from 'react'
import { Users, Key, Shield, Settings, FileText, Database, Lock } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { usePermissions } from '@/hooks/auth'
import { cn } from '@/lib/utils'

// Import existing components
import UserManagement from './users/UserManagement'
import SshKeyManagement from './ssh-keys/SshKeyManagement'

// New components for the admin page
import SystemConfigurations from './admin/SystemConfigurations'
import LogsPage from './admin/LogsPage'
import PgpKeys from './admin/PgpKeys'

type AdminTab = 'users' | 'ssh-keys' | 'pgp-keys' | 'system-config' | 'logs'

const adminTabs: { id: AdminTab; label: string; icon: React.ComponentType<{ className?: string }>; description: string }[] = [
  {
    id: 'users',
    label: 'Users',
    icon: Users,
    description: 'Manage user accounts, roles, and permissions'
  },
  {
    id: 'ssh-keys',
    label: 'SSH Keys',
    icon: Key,
    description: 'Generate and manage SSH key pairs for SFTP adapters'
  },
  {
    id: 'pgp-keys',
    label: 'PGP Keys',
    icon: Lock,
    description: 'Manage PGP encryption keys for secure file transfers'
  },
  {
    id: 'system-config',
    label: 'System Configuration',
    icon: Settings,
    description: 'Configure system settings, logging, and performance parameters'
  },
  {
    id: 'logs',
    label: 'Logs',
    icon: FileText,
    description: 'View and manage system and transactional logs'
  }
]

const AdminPage: React.FC = () => {
  const { isAdmin } = usePermissions()
  const [activeTab, setActiveTab] = useState<AdminTab>('users')

  if (!isAdmin) {
    return (
      <div className="content-spacing">
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Shield className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">Access Denied</h3>
              <p className="text-muted-foreground">Administrator privileges required to access this section</p>
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
          <h1 className="text-3xl font-bold text-foreground">System Administration</h1>
        </div>
        <p className="text-muted-foreground">
          Centralized administration for users, security, configuration, and monitoring
        </p>
      </div>

      {/* Tab Navigation */}
      <div className="mb-8">
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
          {adminTabs.map((tab) => {
            const Icon = tab.icon
            const isActive = activeTab === tab.id
            
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
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