import React, { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { 
  Settings, 
  FileInput, 
  FileOutput, 
  Server, 
  Mail,
  Save,
  X
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { interfaceApi } from '@/lib/api'
import { Node } from '@xyflow/react'

interface NodePropertiesPanelProps {
  selectedNode: Node | null
  onNodeUpdate: (nodeId: string, updates: any) => void
  onClose: () => void
  className?: string
}

const NodePropertiesPanel: React.FC<NodePropertiesPanelProps> = ({
  selectedNode,
  onNodeUpdate,
  onClose,
  className = ''
}) => {
  const [nodeData, setNodeData] = useState<any>({})

  // Fetch available adapters for selection
  const { data: adaptersResponse } = useQuery({
    queryKey: ['interfaces'],
    queryFn: interfaceApi.getAllInterfaces,
  })

  const adapters = adaptersResponse?.data || []

  // Initialize node data when selected node changes
  useEffect(() => {
    if (selectedNode) {
      setNodeData({
        label: selectedNode.data.label || '',
        adapterType: selectedNode.data.adapterType || null,
        direction: selectedNode.data.direction || '',
        adapterId: selectedNode.data.adapterId || '',
        configuration: selectedNode.data.configuration || {},
        // New orchestration pattern fields
        inboundAdapter: selectedNode.data.inboundAdapter || '',
        ...selectedNode.data
      })
    }
  }, [selectedNode])

  const handleSave = () => {
    if (selectedNode) {
      onNodeUpdate(selectedNode.id, nodeData)
      onClose()
    }
  }

  const handleInputChange = (field: string, value: any) => {
    setNodeData((prev: any) => ({
      ...prev,
      [field]: value
    }))
  }

  const getNodeIcon = () => {
    if (selectedNode?.type === 'start') return Settings
    if (selectedNode?.type === 'end') return Settings
    if (selectedNode?.type === 'messageEnd') return Mail
    if (selectedNode?.type === 'decision') return Settings
    if (selectedNode?.type === 'utility') return Settings

    switch (nodeData.adapterType) {
      case 'FILE':
        return nodeData.direction === 'SENDER' ? FileInput : FileOutput
      case 'SFTP':
        return Server
      case 'EMAIL':
        return Mail
      default:
        return Settings
    }
  }

  const getAvailableAdapters = () => {
    // For adapter nodes, filter by direction only if no specific adapter type is set
    // This allows all adapters of the correct direction to be shown
    if (!nodeData.direction) return []
    
    return adapters.filter((adapter: any) => {
      // Always match direction
      const directionMatch = adapter.direction === nodeData.direction
      
      // If adapterType is set, also match on that
      if (nodeData.adapterType && nodeData.adapterType !== 'null') {
        return directionMatch && adapter.adapterType === nodeData.adapterType
      }
      
      // Otherwise, just match on direction
      return directionMatch
    })
  }

  if (!selectedNode) {
    return (
      <div className={`w-80 bg-background border-l flex flex-col ${className}`}>
        <div className="p-4 border-b">
          <h3 className="font-semibold text-lg">Properties</h3>
          <p className="text-sm text-muted-foreground">
            Select a node to configure its properties
          </p>
        </div>
      </div>
    )
  }

  const IconComponent = getNodeIcon()
  const availableAdapters = getAvailableAdapters()

  return (
    <div className={`w-80 bg-background border-l flex flex-col ${className}`}>
      {/* Header */}
      <div className="p-4 border-b">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <div className="w-8 h-8 bg-primary/20 rounded-lg flex items-center justify-center">
              <IconComponent className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h3 className="font-semibold text-lg">Node Properties</h3>
              <p className="text-sm text-muted-foreground">
                {selectedNode.type === 'start' ? 'Start Process Configuration' : 
                 selectedNode.type === 'end' ? 'End Process Configuration' :
                 selectedNode.type === 'messageEnd' ? 'Message End Configuration' :
                 selectedNode.type === 'adapter' ? 'Adapter Configuration' : 
                 'Node Configuration'}
              </p>
            </div>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Properties */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* Basic Properties */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm">Basic Properties</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="node-label">Label</Label>
              <Input
                id="node-label"
                value={nodeData.label || ''}
                onChange={(e) => handleInputChange('label', e.target.value)}
                placeholder="Enter node label"
                className="text-sm"
              />
            </div>

            {/* START Process Node - System Node (Non-configurable) */}
            {selectedNode.type === 'start' && (
              <div className="space-y-2">
                <Label>Node Type</Label>
                <div className="text-sm text-muted-foreground bg-muted/20 p-2 rounded">
                  Start Event - System node that initiates the flow process
                </div>
                <p className="text-xs text-muted-foreground">
                  This is a system node and cannot be configured. Use adapter nodes to connect external systems.
                </p>
              </div>
            )}

            {/* END Process Node - System Node (Non-configurable) */}
            {selectedNode.type === 'end' && (
              <div className="space-y-2">
                <Label>Node Type</Label>
                <div className="text-sm text-muted-foreground bg-muted/20 p-2 rounded">
                  End Event - System node that terminates the flow process
                </div>
                <p className="text-xs text-muted-foreground">
                  This is a system node and cannot be configured. Use adapter nodes to connect external systems.
                </p>
              </div>
            )}

            {/* MESSAGE END Node - System Node (Non-configurable) */}
            {selectedNode.type === 'messageEnd' && (
              <div className="space-y-2">
                <Label>Node Type</Label>
                <div className="text-sm text-muted-foreground bg-muted/20 p-2 rounded">
                  Message End Event - System node for message-based flow termination
                </div>
                <p className="text-xs text-muted-foreground">
                  This is a system node and cannot be configured. Use adapter nodes to connect external systems.
                </p>
              </div>
            )}

            {/* Legacy adapter node support */}
            {selectedNode.type === 'adapter' && (
              <>
                <div className="space-y-2">
                  <Label>Adapter Type</Label>
                  <div className="text-sm text-muted-foreground bg-muted/20 p-2 rounded">
                    {nodeData.adapterType && nodeData.adapterType !== null && nodeData.adapterType !== '' ? 
                      `${nodeData.adapterType} ${nodeData.direction}` : 
                      `${nodeData.direction} (Select adapter instance to set type)`
                    }
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="adapter-instance">Adapter Instance</Label>
                  <Select
                    value={nodeData.adapterId || ''}
                    onValueChange={(value) => {
                      const selectedAdapter = adapters.find((a: any) => a.id === value)
                      const updates = {
                        adapterId: value,
                        ...(selectedAdapter && {
                          label: selectedAdapter.name,
                          adapterType: selectedAdapter.adapterType,
                          configuration: selectedAdapter.configuration
                        })
                      }
                      
                      // Update local state
                      setNodeData((prev: any) => ({
                        ...prev,
                        ...updates
                      }))
                      
                      // Auto-save the changes immediately
                      if (selectedNode) {
                        onNodeUpdate(selectedNode.id, updates)
                      }
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Select adapter instance" />
                    </SelectTrigger>
                    <SelectContent>
                      {availableAdapters.map((adapter: any) => (
                        <SelectItem key={adapter.id} value={adapter.id}>
                          {adapter.name} - {adapter.adapterType} ({adapter.bank || 'No Bank'})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {availableAdapters.length === 0 && (
                    <p className="text-xs text-muted-foreground">
                      No {nodeData.direction} adapters available. 
                      Create one in Adapter Configuration first.
                    </p>
                  )}
                </div>
              </>
            )}
          </CardContent>
        </Card>

        {/* Configuration Summary */}
        {nodeData.configuration && Object.keys(nodeData.configuration).length > 0 && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm">Configuration Summary</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2 text-sm overflow-hidden">
                {nodeData.adapterType === 'FILE' && (
                  <>
                    {nodeData.configuration.sourceDirectory && (
                      <div className="flex flex-col">
                        <span className="text-muted-foreground">Source:</span>
                        <span className="font-mono text-xs truncate" title={nodeData.configuration.sourceDirectory}>{nodeData.configuration.sourceDirectory}</span>
                      </div>
                    )}
                    {nodeData.configuration.targetDirectory && (
                      <div className="flex flex-col">
                        <span className="text-muted-foreground">Target:</span>
                        <span className="font-mono text-xs truncate" title={nodeData.configuration.targetDirectory}>{nodeData.configuration.targetDirectory}</span>
                      </div>
                    )}
                    {nodeData.configuration.filePattern && (
                      <div className="flex flex-col">
                        <span className="text-muted-foreground">Pattern:</span>
                        <span className="font-mono text-xs truncate">{nodeData.configuration.filePattern}</span>
                      </div>
                    )}
                  </>
                )}
                {nodeData.adapterType === 'SFTP' && (
                  <>
                    {nodeData.configuration.host && (
                      <div className="flex flex-col">
                        <span className="text-muted-foreground">Host:</span>
                        <span className="font-mono text-xs truncate">{nodeData.configuration.host}:{nodeData.configuration.port}</span>
                      </div>
                    )}
                    {nodeData.configuration.remoteDirectory && (
                      <div className="flex flex-col">
                        <span className="text-muted-foreground">Remote Dir:</span>
                        <span className="font-mono text-xs truncate" title={nodeData.configuration.remoteDirectory}>{nodeData.configuration.remoteDirectory}</span>
                      </div>
                    )}
                  </>
                )}
                {nodeData.adapterType === 'EMAIL' && (
                  <>
                    {nodeData.configuration.smtpHost && (
                      <div className="flex flex-col">
                        <span className="text-muted-foreground">SMTP:</span>
                        <span className="font-mono text-xs truncate">{nodeData.configuration.smtpHost}:{nodeData.configuration.smtpPort}</span>
                      </div>
                    )}
                    {nodeData.configuration.recipients && (
                      <div className="flex flex-col">
                        <span className="text-muted-foreground">Recipients:</span>
                        <span className="font-mono text-xs truncate" title={
                          Array.isArray(nodeData.configuration.recipients) 
                            ? nodeData.configuration.recipients.join(', ')
                            : String(nodeData.configuration.recipients)
                        }>
                          {Array.isArray(nodeData.configuration.recipients) 
                            ? nodeData.configuration.recipients.join(', ')
                            : String(nodeData.configuration.recipients)
                          }
                        </span>
                      </div>
                    )}
                  </>
                )}
              </div>
            </CardContent>
          </Card>
        )}

        {/* Node Type Specific Properties */}
        {selectedNode.type === 'decision' && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm">Decision Logic</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-2">
                <Label htmlFor="condition-type">Condition Type</Label>
                <Select
                  value={nodeData.conditionType || 'ALWAYS_TRUE'}
                  onValueChange={(value) => handleInputChange('conditionType', value)}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select condition type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALWAYS_TRUE">Always True</SelectItem>
                    <SelectItem value="FILE_EXISTS">File Exists</SelectItem>
                    <SelectItem value="FILE_SIZE">File Size Check</SelectItem>
                    <SelectItem value="CUSTOM">Custom Expression</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {nodeData.conditionType === 'CUSTOM' && (
                <div className="space-y-2">
                  <Label htmlFor="condition">Condition Expression</Label>
                  <Input
                    id="condition"
                    value={nodeData.condition || ''}
                    onChange={(e) => handleInputChange('condition', e.target.value)}
                    placeholder="Enter condition expression"
                    className="text-sm font-mono"
                  />
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {selectedNode.type === 'utility' && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm">Utility Configuration</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-2">
                <Label>Utility Type</Label>
                <div className="text-sm text-muted-foreground bg-muted/20 p-2 rounded">
                  {nodeData.utilityType}
                </div>
              </div>
            </CardContent>
          </Card>
        )}
      </div>

      {/* Footer */}
      <div className="p-4 border-t">
        <div className="flex items-center space-x-2">
          <Button onClick={handleSave} size="sm" className="flex-1">
            <Save className="h-4 w-4 mr-2" />
            Save Changes
          </Button>
          <Button variant="outline" onClick={onClose} size="sm">
            Cancel
          </Button>
        </div>
      </div>
    </div>
  )
}

export default NodePropertiesPanel