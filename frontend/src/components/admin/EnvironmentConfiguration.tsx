import React, { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Layers, Save, RefreshCw, AlertCircle, CheckCircle2 } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Input } from '@/components/ui/input'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { environmentApi } from '@/lib/api'
import { useNotifications } from '@/stores/ui'

interface EnvironmentInfo {
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

interface EnvironmentType {
  name: string
  displayName: string
  description: string
  textColorClass: string
  backgroundColorClass: string
}

const getEnvironmentStyle = (type: string) => {
  switch (type) {
    case 'DEVELOPMENT':
      return { 
        color: 'text-info', 
        bgColor: 'bg-blue-100 border-blue-200', 
        badgeClass: 'bg-blue-600 text-white border-blue-600',
        label: 'Development' 
      }
    case 'QUALITY_ASSURANCE':
      return { 
        color: 'text-warning', 
        bgColor: 'bg-yellow-100 border-yellow-200', 
        badgeClass: 'bg-purple-600 text-white border-purple-600',
        label: 'Quality Assurance' 
      }
    case 'PRODUCTION':
      return { 
        color: 'text-destructive', 
        bgColor: 'bg-red-100 border-red-200', 
        badgeClass: 'bg-red-600 text-white border-red-600',
        label: 'Production' 
      }
    default:
      return { 
        color: 'text-muted-foreground', 
        bgColor: 'bg-gray-100 border-gray-200', 
        badgeClass: 'bg-gray-600 text-white border-gray-600',
        label: 'Unknown' 
      }
  }
}

const EnvironmentConfiguration: React.FC = () => {
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()

  const [selectedType, setSelectedType] = useState<string>('')
  const [enforceRestrictions, setEnforceRestrictions] = useState<boolean>(true)
  const [restrictionMessage, setRestrictionMessage] = useState<string>('')
  const [hasChanges, setHasChanges] = useState<boolean>(false)

  // Fetch current environment configuration
  const { data: envResponse, isLoading: envLoading } = useQuery({
    queryKey: ['environment-config'],
    queryFn: () => {
      console.log('🔧 Fetching current environment...')
      return environmentApi.getCurrentEnvironment()
    },
  })

  // Fetch available environment types
  const { data: typesResponse, isLoading: typesLoading } = useQuery({
    queryKey: ['environment-types'],
    queryFn: () => {
      console.log('🔧 Fetching environment types...')
      return environmentApi.getEnvironmentTypes()
    },
  })

  const environmentInfo: EnvironmentInfo | undefined = envResponse?.data
  const environmentTypes: EnvironmentType[] = typesResponse?.data || []

  // Update environment mutation
  const updateEnvironmentMutation = useMutation({
    mutationFn: environmentApi.updateEnvironment,
    onSuccess: () => {
      success('Environment Updated', 'Environment configuration updated successfully')
      queryClient.invalidateQueries({ queryKey: ['environment-config'] })
      setHasChanges(false)
    },
    onError: (err: any) => {
      error('Update Failed', err.response?.data?.message || 'Failed to update environment configuration')
    },
  })

  // Initialize form values when data loads
  useEffect(() => {
    if (environmentInfo) {
      setSelectedType(environmentInfo.type)
      setEnforceRestrictions(environmentInfo.enforceRestrictions)
      setRestrictionMessage(environmentInfo.restrictionMessage)
      setHasChanges(false)
    }
  }, [environmentInfo])

  // Check for changes
  useEffect(() => {
    if (!environmentInfo) return
    
    const hasTypeChange = selectedType !== environmentInfo.type
    const hasEnforceChange = enforceRestrictions !== environmentInfo.enforceRestrictions
    const hasMessageChange = restrictionMessage !== environmentInfo.restrictionMessage
    
    setHasChanges(hasTypeChange || hasEnforceChange || hasMessageChange)
  }, [selectedType, enforceRestrictions, restrictionMessage, environmentInfo])

  const handleSave = () => {
    updateEnvironmentMutation.mutate({
      type: selectedType,
      enforceRestrictions,
      restrictionMessage,
    })
  }

  const handleReset = () => {
    if (environmentInfo) {
      setSelectedType(environmentInfo.type)
      setEnforceRestrictions(environmentInfo.enforceRestrictions)
      setRestrictionMessage(environmentInfo.restrictionMessage)
      setHasChanges(false)
    }
  }

  if (envLoading || typesLoading) {
    return (
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Layers className="h-5 w-5" />
            <CardTitle>Environment Configuration</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center p-8">
            <RefreshCw className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        </CardContent>
      </Card>
    )
  }

  const currentEnvStyle = getEnvironmentStyle(selectedType)
  const restrictedFeatures = !enforceRestrictions ? [] : selectedType === 'DEVELOPMENT' ? [] : ['Flow Creation', 'Adapter Creation']

  return (
    <Card>
      <CardHeader>
        <div className="flex justify-between items-start">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Layers className="h-5 w-5" />
              Environment Configuration
            </CardTitle>
            <CardDescription>
              Configure the system environment type and restrictions
            </CardDescription>
          </div>
          <Badge className={currentEnvStyle.badgeClass}>
            {currentEnvStyle.label}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Environment Type Selection */}
        <div className="space-y-2">
          <Label htmlFor="environment-type">Environment Type</Label>
          <Select value={selectedType} onValueChange={setSelectedType}>
            <SelectTrigger id="environment-type">
              <SelectValue placeholder="Select environment type" />
            </SelectTrigger>
            <SelectContent>
              {environmentTypes.map((type) => (
                <SelectItem key={type.name} value={type.name}>
                  <div className="flex items-center gap-2">
                    <div 
                      className={`w-3 h-3 rounded-full ${getEnvironmentStyle(type.name).bgColor}`}
                    />
                    {type.displayName}
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Enforce Restrictions Toggle */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label htmlFor="enforce-restrictions">Enforce Environment Restrictions</Label>
            <p className="text-sm text-muted-foreground">
              Enable or disable environment-based feature restrictions
            </p>
          </div>
          <Switch
            id="enforce-restrictions"
            checked={enforceRestrictions}
            onCheckedChange={setEnforceRestrictions}
          />
        </div>

        {/* Custom Restriction Message */}
        <div className="space-y-2">
          <Label htmlFor="restriction-message">Custom Restriction Message</Label>
          <Input
            id="restriction-message"
            value={restrictionMessage}
            onChange={(e) => setRestrictionMessage(e.target.value)}
            placeholder="This action is not allowed in %s environment"
          />
          <p className="text-sm text-muted-foreground">
            Use %s as placeholder for environment name
          </p>
        </div>

        {/* Environment Restrictions Info */}
        {enforceRestrictions && restrictedFeatures.length > 0 && (
          <Alert className={currentEnvStyle.bgColor}>
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>
              <strong>Environment Restrictions Active:</strong> The following features are restricted in {currentEnvStyle.label} environment: {restrictedFeatures.join(', ')}
            </AlertDescription>
          </Alert>
        )}

        {/* Permissions Preview */}
        {environmentInfo && (
          <div className="space-y-3">
            <Label>Current Environment Permissions</Label>
            <div className="grid grid-cols-2 gap-4">
              {Object.entries(environmentInfo.permissions).map(([permission, allowed]) => (
                <div key={permission} className="flex items-center gap-2 text-sm">
                  {allowed ? (
                    <CheckCircle2 className="h-4 w-4 text-green-600" />
                  ) : (
                    <AlertCircle className="h-4 w-4 text-red-600" />
                  )}
                  <span className={allowed ? 'text-green-700' : 'text-red-700'}>
                    {permission.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase())}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Save Actions */}
        {hasChanges && (
          <div className="flex items-center justify-between pt-4 border-t">
            <p className="text-sm text-muted-foreground">
              You have unsaved changes
            </p>
            <div className="flex gap-2">
              <Button variant="outline" onClick={handleReset} disabled={updateEnvironmentMutation.isPending}>
                Cancel
              </Button>
              <Button onClick={handleSave} disabled={updateEnvironmentMutation.isPending}>
                {updateEnvironmentMutation.isPending ? (
                  <>
                    <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="h-4 w-4 mr-2" />
                    Save Changes
                  </>
                )}
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export default EnvironmentConfiguration