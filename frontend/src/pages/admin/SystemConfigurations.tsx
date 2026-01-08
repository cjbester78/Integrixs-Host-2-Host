import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Settings, Search, Edit, Save, X, RotateCcw, AlertCircle, Database, FileText, Shield, Monitor, Activity } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useNotifications } from '@/stores/ui'
import { configApi } from '@/lib/api'
import { cn } from '@/lib/utils'
import EnvironmentConfiguration from '@/components/admin/EnvironmentConfiguration'

interface SystemConfiguration {
  id: string
  configKey: string
  configValue: string
  configType: 'STRING' | 'INTEGER' | 'BOOLEAN' | 'JSON'
  description: string
  category: 'DASHBOARD' | 'SECURITY' | 'NOTIFICATIONS' | 'FILE_PROCESSING' | 'SYSTEM' | 'LOGGING' | 'ADAPTER' | 'GENERAL'
  encrypted: boolean
  readonly: boolean
  defaultValue: string
  createdAt: string
  updatedAt: string
}

interface ConfigUpdateRequest {
  configValue: string
}

const categoryIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  DASHBOARD: Monitor,
  SECURITY: Shield,
  NOTIFICATIONS: AlertCircle,
  FILE_PROCESSING: FileText,
  SYSTEM: Settings,
  LOGGING: Activity,
  ADAPTER: Database,
  GENERAL: Settings,
}

const categoryColors: Record<string, string> = {
  DASHBOARD: 'text-blue-500',
  SECURITY: 'text-red-500',
  NOTIFICATIONS: 'text-yellow-500',
  FILE_PROCESSING: 'text-green-500',
  SYSTEM: 'text-purple-500',
  LOGGING: 'text-orange-500',
  ADAPTER: 'text-cyan-500',
  GENERAL: 'text-gray-500',
}

const SystemConfigurations: React.FC = () => {
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()
  
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL')
  const [editingConfig, setEditingConfig] = useState<string | null>(null)
  const [editValues, setEditValues] = useState<Record<string, string>>({})

  // Fetch configurations
  const { data: configurationsResponse, isLoading } = useQuery({
    queryKey: ['system-configurations'],
    queryFn: configApi.getAllConfigurations,
  })

  const configurations: SystemConfiguration[] = configurationsResponse?.configurations || []

  // Update configuration mutation
  const updateConfigMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ConfigUpdateRequest }) => 
      configApi.updateConfigurationValue(id, data.configValue),
    onSuccess: () => {
      success('Configuration Updated', 'System configuration updated successfully')
      queryClient.invalidateQueries({ queryKey: ['system-configurations'] })
      setEditingConfig(null)
      setEditValues({})
    },
    onError: (err: any) => {
      error('Update Failed', err.response?.data?.message || 'Failed to update configuration')
    },
  })

  const handleEdit = (config: SystemConfiguration) => {
    if (config.readonly) return
    setEditingConfig(config.id)
    setEditValues({ [config.id]: config.configValue })
  }

  const handleSave = (configId: string) => {
    const newValue = editValues[configId]
    if (newValue !== undefined) {
      updateConfigMutation.mutate({
        id: configId,
        data: { configValue: newValue }
      })
    }
  }

  const handleCancel = () => {
    setEditingConfig(null)
    setEditValues({})
  }

  const handleReset = (config: SystemConfiguration) => {
    if (config.defaultValue && !config.readonly) {
      updateConfigMutation.mutate({
        id: config.id,
        data: { configValue: config.defaultValue }
      })
    }
  }

  const filteredConfigurations = configurations.filter((config: SystemConfiguration) => {
    const matchesSearch = config.configKey.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         config.description?.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesCategory = selectedCategory === 'ALL' || config.category === selectedCategory
    return matchesSearch && matchesCategory
  })

  const categories: string[] = ['ALL', ...Array.from(new Set(configurations.map((c) => c.category)))]

  const getConfigsByCategory = (category: string) => {
    return filteredConfigurations.filter((config: SystemConfiguration) => 
      category === 'ALL' || config.category === category
    )
  }

  const renderConfigValue = (config: SystemConfiguration) => {
    const isEditing = editingConfig === config.id
    const currentValue = isEditing ? editValues[config.id] : config.configValue

    if (config.encrypted && !isEditing) {
      return <span className="text-muted-foreground">••••••••</span>
    }

    if (isEditing) {
      if (config.configType === 'BOOLEAN') {
        return (
          <Select
            value={currentValue}
            onValueChange={(value) => setEditValues(prev => ({ ...prev, [config.id]: value }))}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="true">True</SelectItem>
              <SelectItem value="false">False</SelectItem>
            </SelectContent>
          </Select>
        )
      } else if (config.configKey === 'system.timezone.default') {
        return (
          <Select
            value={currentValue}
            onValueChange={(value) => setEditValues(prev => ({ ...prev, [config.id]: value }))}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UTC-12:00">UTC-12:00 (Baker Island)</SelectItem>
              <SelectItem value="UTC-11:00">UTC-11:00 (Samoa)</SelectItem>
              <SelectItem value="UTC-10:00">UTC-10:00 (Hawaii)</SelectItem>
              <SelectItem value="UTC-09:00">UTC-09:00 (Alaska)</SelectItem>
              <SelectItem value="UTC-08:00">UTC-08:00 (Pacific Time)</SelectItem>
              <SelectItem value="UTC-07:00">UTC-07:00 (Mountain Time)</SelectItem>
              <SelectItem value="UTC-06:00">UTC-06:00 (Central Time)</SelectItem>
              <SelectItem value="UTC-05:00">UTC-05:00 (Eastern Time)</SelectItem>
              <SelectItem value="UTC-04:00">UTC-04:00 (Atlantic Time)</SelectItem>
              <SelectItem value="UTC-03:30">UTC-03:30 (Newfoundland)</SelectItem>
              <SelectItem value="UTC-03:00">UTC-03:00 (Brazil, Argentina)</SelectItem>
              <SelectItem value="UTC-02:00">UTC-02:00 (Mid-Atlantic)</SelectItem>
              <SelectItem value="UTC-01:00">UTC-01:00 (Azores)</SelectItem>
              <SelectItem value="UTC+00:00">UTC+00:00 (Greenwich Mean Time)</SelectItem>
              <SelectItem value="UTC+01:00">UTC+01:00 (Central European Time)</SelectItem>
              <SelectItem value="UTC+02:00">UTC+02:00 (South Africa, Eastern Europe)</SelectItem>
              <SelectItem value="UTC+03:00">UTC+03:00 (Russia, East Africa)</SelectItem>
              <SelectItem value="UTC+03:30">UTC+03:30 (Iran)</SelectItem>
              <SelectItem value="UTC+04:00">UTC+04:00 (Gulf States, Russia)</SelectItem>
              <SelectItem value="UTC+04:30">UTC+04:30 (Afghanistan)</SelectItem>
              <SelectItem value="UTC+05:00">UTC+05:00 (Pakistan, Kazakhstan)</SelectItem>
              <SelectItem value="UTC+05:30">UTC+05:30 (India, Sri Lanka)</SelectItem>
              <SelectItem value="UTC+05:45">UTC+05:45 (Nepal)</SelectItem>
              <SelectItem value="UTC+06:00">UTC+06:00 (Bangladesh, Central Asia)</SelectItem>
              <SelectItem value="UTC+06:30">UTC+06:30 (Myanmar)</SelectItem>
              <SelectItem value="UTC+07:00">UTC+07:00 (Thailand, Vietnam)</SelectItem>
              <SelectItem value="UTC+08:00">UTC+08:00 (China, Singapore)</SelectItem>
              <SelectItem value="UTC+08:30">UTC+08:30 (North Korea)</SelectItem>
              <SelectItem value="UTC+09:00">UTC+09:00 (Japan, Korea)</SelectItem>
              <SelectItem value="UTC+09:30">UTC+09:30 (Australia Central)</SelectItem>
              <SelectItem value="UTC+10:00">UTC+10:00 (Australia Eastern)</SelectItem>
              <SelectItem value="UTC+10:30">UTC+10:30 (Lord Howe Island)</SelectItem>
              <SelectItem value="UTC+11:00">UTC+11:00 (Solomon Islands)</SelectItem>
              <SelectItem value="UTC+12:00">UTC+12:00 (New Zealand)</SelectItem>
              <SelectItem value="UTC+12:45">UTC+12:45 (Chatham Islands)</SelectItem>
              <SelectItem value="UTC+13:00">UTC+13:00 (Samoa, Tonga)</SelectItem>
              <SelectItem value="UTC+14:00">UTC+14:00 (Line Islands)</SelectItem>
            </SelectContent>
          </Select>
        )
      } else {
        return (
          <Input
            type={config.configType === 'INTEGER' ? 'number' : 'text'}
            value={currentValue}
            onChange={(e) => setEditValues(prev => ({ ...prev, [config.id]: e.target.value }))}
            className="font-mono text-sm"
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                handleSave(config.id)
              } else if (e.key === 'Escape') {
                handleCancel()
              }
            }}
          />
        )
      }
    }

    // Display value based on type
    if (config.configType === 'BOOLEAN') {
      return (
        <div className="flex items-center space-x-2">
          <div className={cn(
            "w-3 h-3 rounded-full",
            currentValue === 'true' ? 'bg-success' : 'bg-muted-foreground'
          )} />
          <span className={cn(
            "font-mono text-sm",
            currentValue === 'true' ? 'text-success' : 'text-muted-foreground'
          )}>
            {currentValue === 'true' ? 'Enabled' : 'Disabled'}
          </span>
        </div>
      )
    }

    return (
      <div className="font-mono text-sm bg-secondary/50 px-2 py-1 rounded">
        {currentValue}
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <span className="text-muted-foreground">Loading configurations...</span>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground mb-2">System Configuration</h2>
          <p className="text-muted-foreground">Manage runtime system settings and parameters</p>
        </div>
        <div className="flex items-center space-x-2">
          <Database className="h-5 w-5 text-primary" />
          <span className="text-sm text-muted-foreground">{configurations.length} configurations</span>
        </div>
      </div>

      {/* Environment Configuration */}
      <EnvironmentConfiguration />

      {/* Filters */}
      <Card className="app-card border">
        <CardContent className="p-4">
          <div className="flex flex-col sm:flex-row gap-4">
            <div className="flex-1">
              <div className="relative">
                <Search className="h-4 w-4 absolute left-3 top-3 text-muted-foreground" />
                <Input
                  placeholder="Search configurations..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
            </div>
            <div className="w-full sm:w-48">
              <Select value={selectedCategory} onValueChange={setSelectedCategory}>
                <SelectTrigger>
                  <SelectValue placeholder="All Categories" />
                </SelectTrigger>
                <SelectContent>
                  {categories.map((category) => (
                    <SelectItem key={category} value={category}>
                      {category === 'ALL' ? 'All Categories' : category.replace('_', ' ')}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Configurations by Category */}
      {categories.filter((cat) => cat !== 'ALL').map((category) => {
        const categoryConfigs = getConfigsByCategory(category)
        if (categoryConfigs.length === 0) return null

        const Icon = categoryIcons[category] || Settings
        const iconColor = categoryColors[category] || 'text-muted-foreground'

        return (
          <Card key={category} className="app-card border">
            <CardHeader>
              <CardTitle className="flex items-center space-x-2">
                <Icon className={cn("h-5 w-5", iconColor)} />
                <span>{category.replace('_', ' ')}</span>
                <span className="text-sm font-normal text-muted-foreground">
                  ({categoryConfigs.length} settings)
                </span>
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {categoryConfigs.map((config: SystemConfiguration) => (
                <div key={config.id} className="border border-border rounded-lg p-4 space-y-3">
                  <div className="flex items-start justify-between">
                    <div className="flex-1 space-y-1">
                      <div className="flex items-center space-x-2">
                        <Label className="font-mono text-sm text-primary">{config.configKey}</Label>
                        {config.readonly && (
                          <span className="text-xs px-2 py-1 bg-muted/50 text-muted-foreground rounded">
                            Read Only
                          </span>
                        )}
                        {config.encrypted && (
                          <span className="text-xs px-2 py-1 bg-warning/20 text-warning rounded">
                            Encrypted
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-muted-foreground">{config.description}</p>
                      <div className="flex items-center space-x-2 text-xs text-muted-foreground">
                        <span>Type: {config.configType}</span>
                        {config.defaultValue && (
                          <span>• Default: {config.defaultValue}</span>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center space-x-2 ml-4">
                      {editingConfig === config.id ? (
                        <>
                          <Button
                            size="sm"
                            onClick={() => handleSave(config.id)}
                            disabled={updateConfigMutation.isPending}
                          >
                            <Save className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={handleCancel}
                          >
                            <X className="h-4 w-4" />
                          </Button>
                        </>
                      ) : (
                        <>
                          {!config.readonly && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => handleEdit(config)}
                            >
                              <Edit className="h-4 w-4" />
                            </Button>
                          )}
                          {config.defaultValue && config.configValue !== config.defaultValue && !config.readonly && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => handleReset(config)}
                              title="Reset to default"
                            >
                              <RotateCcw className="h-4 w-4" />
                            </Button>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                  
                  <div className="space-y-2">
                    <Label className="text-sm">Current Value:</Label>
                    {renderConfigValue(config)}
                  </div>

                  <div className="text-xs text-muted-foreground">
                    Last updated: {new Date(config.updatedAt).toLocaleString()}
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        )
      })}

      {filteredConfigurations.length === 0 && (
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Settings className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">No Configurations Found</h3>
              <p className="text-muted-foreground">
                No system configurations match your search criteria
              </p>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

export default SystemConfigurations