import React, { useState, useEffect } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import FlowCanvas, { FlowData } from '@/components/flow/FlowCanvas'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { 
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ArrowLeft, Settings, X } from 'lucide-react'
import { flowApi } from '@/lib/api'
import WebSocketStatus from '@/components/WebSocketStatus'
import { useFlowValidationUpdates } from '@/hooks/useWebSocket'

const VisualFlowBuilder: React.FC = () => {
  const { id } = useParams<{ id?: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const isViewMode = searchParams.get('mode') === 'view'
  const [flowData, setFlowData] = useState<FlowData | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isSaving, setSaving] = useState(false)
  const [isExecuting, setExecuting] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const [flowName, setFlowName] = useState('')
  const [flowDescription, setFlowDescription] = useState('')
  const [bankName, setBankName] = useState('')

  // Subscribe to real-time flow validation updates
  useFlowValidationUpdates((result) => {
    if (id && result.flowId === id) {
      // Handle validation updates for current flow
      if (!result.validation.valid) {
        toast.error(`Flow validation failed: ${result.validation.errors.join(', ')}`)
      } else if (result.validation.warnings.length > 0) {
        toast.warning(`Flow validation warnings: ${result.validation.warnings.join(', ')}`)
      } else {
        toast.success('Flow validation passed')
      }
    }
  })

  // Load existing flow if editing
  useEffect(() => {
    const loadFlow = async () => {
      if (id) {
        setIsLoading(true)
        try {
          const response = await flowApi.getFlowById(id)
          const flow = response.data
          setFlowData({
            id: flow.id,
            name: flow.name,
            description: flow.description || '',
            nodes: flow.flowDefinition?.nodes || [],
            edges: flow.flowDefinition?.edges || [],
            active: flow.active
          })
          setFlowName(flow.name)
          setFlowDescription(flow.description || '')
          setBankName(flow.bankName || '')
        } catch (error) {
          console.error('Failed to load flow:', error)
          toast.error('Failed to load flow')
          navigate('/flows')
        } finally {
          setIsLoading(false)
        }
      } else {
        // Create new flow with blank data - user must fill in required fields
        setFlowData({
          id: `flow-${Date.now()}`,
          name: '',
          description: '',
          nodes: [],
          edges: [],
          active: true
        })
        setFlowName('')
        setFlowDescription('')
        setBankName('')
      }
    }

    loadFlow()
  }, [id, navigate])

  // Handle flow changes
  const handleFlowChange = (updatedFlow: FlowData) => {
    setFlowData(updatedFlow)
  }

  // Handle save
  const handleSave = async (flow: FlowData) => {
    // Validate required fields
    if (!flowName.trim()) {
      toast.error('Flow Name is required')
      return
    }
    if (!bankName.trim()) {
      toast.error('Bank Name is required')
      return
    }

    setSaving(true)
    try {
      const flowPayload = {
        name: flowName.trim(),
        description: flowDescription.trim(),
        bankName: bankName.trim(),
        flowDefinition: {
          nodes: flow.nodes,
          edges: flow.edges
        },
        active: flow.active
      }

      if (id) {
        await flowApi.updateFlow(id, flowPayload)
        toast.success('Flow updated successfully')
        navigate('/flows')
      } else {
        const response = await flowApi.createFlow(flowPayload)
        const newFlow = response.data
        setFlowData(prev => prev ? { ...prev, id: newFlow.id } : null)
        toast.success('Flow created successfully')
        navigate('/flows')
      }
    } catch (error: any) {
      console.error('Failed to save flow:', error)
      const errorMessage = error.response?.data?.message || error.message || 'Failed to save flow'
      toast.error('Cannot Save Flow', {
        description: errorMessage
      })
    } finally {
      setSaving(false)
    }
  }

  // Handle execute
  const handleExecute = async (_flow: FlowData) => {
    if (!id) {
      toast.error('Please save the flow before executing')
      return
    }

    setExecuting(true)
    try {
      await flowApi.executeFlow(id)
      toast.success('Flow execution started')
    } catch (error) {
      console.error('Failed to execute flow:', error)
      toast.error('Failed to execute flow')
    } finally {
      setExecuting(false)
    }
  }

  // Handle settings save
  const handleSettingsSave = () => {
    if (flowData) {
      setFlowData({
        ...flowData,
        name: flowName,
        description: flowDescription,
        bankName: bankName
      })
      setShowSettings(false)
      toast.success('Flow settings updated')
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto"></div>
          <p className="mt-2 text-muted-foreground">Loading flow...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="border-b bg-background relative z-10 shrink-0">
        {/* Top Row - Navigation */}
        <div className="flex items-center justify-between p-4">
          <div className="flex items-center space-x-4">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => navigate('/flows')}
              className="space-x-2"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Back to Flows</span>
            </Button>
            <h1 className="text-xl font-semibold text-foreground">
              {isViewMode ? 'View Visual Flow' : id ? 'Edit Visual Flow' : 'Create Visual Flow'}
            </h1>
          </div>

          <div className="flex items-center space-x-2">
            <WebSocketStatus size="sm" />
            {!isViewMode && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowSettings(true)}
                className="space-x-2"
              >
                <Settings className="h-4 w-4" />
                <span>Settings</span>
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/flows')}
              className="space-x-2"
            >
              <X className="h-4 w-4" />
              <span>Cancel</span>
            </Button>
          </div>
        </div>

        {/* Flow Configuration Inputs */}
        <div className="px-4 py-4 bg-muted/50 border-t border-border">
          <div className="grid grid-cols-3 gap-4 max-w-4xl">
            <div>
              <label className="text-sm font-medium text-foreground block mb-1">
                Flow Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={flowName}
                onChange={(e) => !isViewMode && setFlowName(e.target.value)}
                placeholder="Enter flow name"
                readOnly={isViewMode}
                required
                className={`w-full px-3 py-2 border rounded-md text-foreground placeholder:text-muted-foreground ${
                  !flowName.trim() && !isViewMode ? 'border-red-500' : 'border-input'
                } ${
                  isViewMode 
                    ? 'bg-muted/50 cursor-default' 
                    : 'bg-background focus:outline-none focus:ring-2 focus:ring-ring'
                }`}
              />
            </div>
            <div>
              <label className="text-sm font-medium text-foreground block mb-1">Description</label>
              <input
                type="text"
                value={flowDescription}
                onChange={(e) => !isViewMode && setFlowDescription(e.target.value)}
                placeholder="Enter description"
                readOnly={isViewMode}
                className={`w-full px-3 py-2 border border-input rounded-md text-foreground placeholder:text-muted-foreground ${
                  isViewMode 
                    ? 'bg-muted/50 cursor-default' 
                    : 'bg-background focus:outline-none focus:ring-2 focus:ring-ring'
                }`}
              />
            </div>
            <div>
              <label className="text-sm font-medium text-foreground block mb-1">
                Bank Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={bankName}
                onChange={(e) => !isViewMode && setBankName(e.target.value)}
                placeholder="Enter bank name"
                readOnly={isViewMode}
                required
                className={`w-full px-3 py-2 border rounded-md text-foreground placeholder:text-muted-foreground ${
                  !bankName.trim() && !isViewMode ? 'border-red-500' : 'border-input'
                } ${
                  isViewMode 
                    ? 'bg-muted/50 cursor-default' 
                    : 'bg-background focus:outline-none focus:ring-2 focus:ring-ring'
                }`}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Flow Canvas */}
      <div className="flex-1 min-h-0 relative">
        {flowData && (
          <FlowCanvas
            flowData={flowData}
            onFlowChange={handleFlowChange}
            onSave={handleSave}
            onExecute={handleExecute}
            readOnly={isViewMode}
            className="h-full"
          />
        )}
      </div>

      {/* Settings Dialog */}
      <Dialog open={showSettings} onOpenChange={setShowSettings}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Flow Settings</DialogTitle>
            <DialogDescription>
              Configure the basic settings for your flow
            </DialogDescription>
          </DialogHeader>
          
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="flow-name">Name</Label>
              <Input
                id="flow-name"
                value={flowName}
                onChange={(e) => setFlowName(e.target.value)}
                placeholder="Enter flow name"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="flow-description">Description</Label>
              <Input
                id="flow-description"
                value={flowDescription}
                onChange={(e) => setFlowDescription(e.target.value)}
                placeholder="Enter flow description (optional)"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="bank-name">Bank Name</Label>
              <Input
                id="bank-name"
                value={bankName}
                onChange={(e) => setBankName(e.target.value)}
                placeholder="Enter bank name"
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setShowSettings(false)}>
              Cancel
            </Button>
            <Button onClick={handleSettingsSave}>
              Save Settings
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Loading States */}
      {(isSaving || isExecuting) && (
        <div className="fixed inset-0 bg-background/80 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="text-center">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto"></div>
            <p className="mt-2 text-muted-foreground">
              {isSaving ? 'Saving flow...' : 'Executing flow...'}
            </p>
          </div>
        </div>
      )}
    </div>
  )
}

export default VisualFlowBuilder