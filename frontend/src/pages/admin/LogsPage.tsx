import React, { useState } from 'react'
import { FileText, Database, Activity } from 'lucide-react'
import { cn } from '@/lib/utils'
import { usePermissions } from '@/hooks/auth'

// Import existing log components
import SystemLogs from './SystemLogs'
import TransactionalLogs from './TransactionalLogs'

type LogTab = 'system' | 'transactional'

const logTabs: { id: LogTab; label: string; icon: React.ComponentType<{ className?: string }>; description: string; adminOnly?: boolean }[] = [
  {
    id: 'system',
    label: 'System Logs',
    icon: Activity,
    description: 'Application logs, errors, and system events',
    adminOnly: true
  },
  {
    id: 'transactional',
    label: 'Transactional Logs',
    icon: Database,
    description: 'Transaction audit trails and business process logs'
    // No adminOnly - available to viewers
  }
]

const LogsPage: React.FC = () => {
  const { isAdmin } = usePermissions()
  
  // Filter tabs based on user role
  const availableTabs = logTabs.filter(tab => !tab.adminOnly || isAdmin())
  
  // Set default tab based on role
  const defaultTab: LogTab = isAdmin() ? 'system' : 'transactional'
  const [activeTab, setActiveTab] = useState<LogTab>(defaultTab)

  const renderTabContent = () => {
    switch (activeTab) {
      case 'system':
        return <SystemLogs />
      case 'transactional':
        return <TransactionalLogs />
      default:
        return <SystemLogs />
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center space-x-3 mb-2">
          <FileText className="h-8 w-8 text-primary" />
          <h1 className="text-3xl font-bold text-foreground">Logs</h1>
        </div>
        <p className="text-muted-foreground">
          Monitor system and transactional logs for application insights and debugging
        </p>
      </div>

      {/* Tab Navigation - only show if there are multiple tabs */}
      {availableTabs.length > 1 && (
        <div className="mb-8">
          <div className="flex space-x-4 border-b border-border">
            {availableTabs.map((tab) => {
            const Icon = tab.icon
            const isActive = activeTab === tab.id
            
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={cn(
                  "flex items-center space-x-2 px-4 py-3 text-sm font-medium transition-colors border-b-2 -mb-px",
                  isActive
                    ? "text-primary border-primary bg-primary/5"
                    : "text-muted-foreground border-transparent hover:text-foreground hover:border-border"
                )}
              >
                <Icon className="h-4 w-4" />
                <span>{tab.label}</span>
              </button>
            )
          })}
          </div>
          <div className="mt-4">
            <p className="text-sm text-muted-foreground">
              {availableTabs.find(tab => tab.id === activeTab)?.description}
            </p>
          </div>
        </div>
      )}

      {/* Tab Content */}
      <div className="transition-all duration-300">
        {renderTabContent()}
      </div>
    </div>
  )
}

export default LogsPage