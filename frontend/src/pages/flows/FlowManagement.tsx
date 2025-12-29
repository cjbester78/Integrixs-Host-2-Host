import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { 
  Plus, 
  Workflow, 
  ArrowRight, 
  Trash2,
  CheckCircle,
  Eye,
  GitBranch,
  Download,
  Clock,
  Play,
  Square,
  Loader2
} from 'lucide-react'
import { flowApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { useNotifications } from '@/stores/ui'
import { usePermissions } from '@/hooks/auth'
import { 
  useWebSocketConnection, 
  useFlowExecutionUpdates, 
  useFlowDefinitionUpdates 
} from '@/hooks/useWebSocket'
import WebSocketStatus from '@/components/WebSocketStatus'

interface FlowDefinition {
  id: string
  name: string
  description?: string
  flowDefinition?: {
    nodes?: any[]
    edges?: any[]
  }
  active: boolean
  deployed: boolean
  deploymentStatus: 'DEPLOYED' | 'UNDEPLOYED' | 'DEPLOYING' | 'UNDEPLOYING' | 'FAILED'
  inboundAdapterId?: string
  inboundAdapterName?: string
  inboundAdapterStatus?: 'RUNNING' | 'STOPPED' | 'ERROR'
  flowType: string
  flowVersion: number
  totalExecutions: number
  successfulExecutions: number
  failedExecutions: number
  averageExecutionTimeMs: number
  createdAt: string
  updatedAt: string
  deployedAt?: string
  scheduleEnabled?: boolean
  scheduleCron?: string
}



const FlowManagement: React.FC = () => {
  const { isAdmin } = usePermissions()
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()

  // WebSocket connection status
  const { status: _wsStatus } = useWebSocketConnection()

  // Track real-time execution states
  const [runningExecutions, setRunningExecutions] = useState<Set<string>>(new Set())

  // Subscribe to real-time flow updates
  useFlowExecutionUpdates({
    onUpdate: (update) => {
      // Update running executions state
      setRunningExecutions(prev => {
        const newSet = new Set(prev)
        if (update.status === 'RUNNING') {
          newSet.add(update.flowId)
        } else if (update.status === 'COMPLETED' || update.status === 'FAILED' || update.status === 'CANCELLED') {
          newSet.delete(update.flowId)
          // Invalidate queries to refresh data when execution finishes
          queryClient.invalidateQueries({ queryKey: ['flows'] })
          queryClient.invalidateQueries({ queryKey: ['flowStatistics'] })
        }
        return newSet
      })
    }
  })

  // Subscribe to flow definition changes
  useFlowDefinitionUpdates(() => {
    // Refresh flow list when flows are created, updated, or deleted
    queryClient.invalidateQueries({ queryKey: ['flows'] })
    queryClient.invalidateQueries({ queryKey: ['flowStatistics'] })
  })

  // Fetch integration flows (visual flows)
  const { data: flowsResponse, isLoading: flowsLoading } = useQuery({
    queryKey: ['flows'],
    queryFn: flowApi.getAllFlows,
  })

  // Fetch flow statistics
  const { data: _statisticsResponse } = useQuery({
    queryKey: ['flowStatistics'],
    queryFn: flowApi.getStatistics,
  })

  const flows = flowsResponse?.data || []

  // Mutations

  const deleteFlowMutation = useMutation({
    mutationFn: flowApi.deleteFlow,
    onSuccess: () => {
      success('Flow Deleted', 'Flow deleted successfully')
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      queryClient.invalidateQueries({ queryKey: ['flowStatistics'] })
    },
    onError: (err: any) => {
      const errorMessage = err.response?.data?.message || 'Failed to delete flow'
      error('Cannot Delete Flow', errorMessage)
    },
  })


  const exportFlowMutation = useMutation({
    mutationFn: flowApi.exportFlow,
    onSuccess: (response) => {
      // Download the exported flow as JSON file
      const dataStr = JSON.stringify(response.data, null, 2)
      const dataBlob = new Blob([dataStr], { type: 'application/json' })
      const url = URL.createObjectURL(dataBlob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${response.data.name || 'flow'}_export.json`
      link.click()
      URL.revokeObjectURL(url)
      
      success('Flow Exported', 'Flow exported successfully')
    },
    onError: (err: any) => {
      error('Export Failed', err.response?.data?.message || 'Failed to export flow')
    },
  })

  const deployFlowMutation = useMutation({
    mutationFn: flowApi.deployFlow,
    onSuccess: () => {
      success('Flow Deployed', 'Flow deployed successfully and is now executable')
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      queryClient.invalidateQueries({ queryKey: ['flowStatistics'] })
    },
    onError: (err: any) => {
      const errorMessage = err.response?.data?.message || 'Failed to deploy flow'
      error('Cannot Deploy Flow', errorMessage)
    },
  })

  const undeployFlowMutation = useMutation({
    mutationFn: flowApi.undeployFlow,
    onSuccess: () => {
      success('Flow Undeployed', 'Flow undeployed successfully and execution is now blocked')
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      queryClient.invalidateQueries({ queryKey: ['flowStatistics'] })
    },
    onError: (err: any) => {
      const errorMessage = err.response?.data?.message || 'Failed to undeploy flow'
      error('Cannot Undeploy Flow', errorMessage)
    },
  })


  // Removed unused getAdapterIcon function since we're using specific icons now

  const getFlowTypeIcon = (flowType: string) => {
    switch(flowType) {
      case 'PARALLEL': return <GitBranch className="h-4 w-4 text-info" />
      case 'CONDITIONAL': return <GitBranch className="h-4 w-4 text-warning" />
      case 'LOOP': return <ArrowRight className="h-4 w-4 text-info" />
      default: return <Workflow className="h-4 w-4 text-primary" />
    }
  }

  const formatExecutionTime = (ms: number) => {
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
    const minutes = Math.floor(ms / 60000)
    const seconds = Math.floor((ms % 60000) / 1000)
    return `${minutes}m ${seconds}s`
  }

  const calculateSuccessRate = (successful: number, total: number) => {
    if (total === 0) return 100
    return Math.round((successful / total) * 100)
  }

  if (!isAdmin) {
    return (
      <div className="content-spacing">
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Workflow className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">Access Denied</h3>
              <p className="text-muted-foreground">Administrator privileges required to manage flows</p>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (flowsLoading) {
    return (
      <div className="content-spacing">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading flows...</span>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">Visual Flow Management</h1>
          <div className="flex items-center space-x-4">
            <p className="text-muted-foreground">Create and manage visual integration flows with drag-and-drop components</p>
            <WebSocketStatus size="sm" />
          </div>
        </div>
        <div className="flex items-center space-x-2">
          <Button asChild className="btn-primary">
            <Link to="/flows/create">
              <Plus className="h-4 w-4 mr-2" />
              Create Flow
            </Link>
          </Button>
        </div>
      </div>


      {/* Flow Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Workflow className="h-8 w-8 text-primary" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Flows</p>
                <p className="text-2xl font-bold text-foreground">{flows.length}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <CheckCircle className="h-8 w-8 text-success" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Enabled Flows</p>
                <p className="text-2xl font-bold text-foreground">
                  {flows.filter((f: FlowDefinition) => f.active).length}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <CheckCircle className="h-8 w-8 text-info" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Executions</p>
                <p className="text-2xl font-bold text-foreground">
                  {flows.reduce((sum: number, f: FlowDefinition) => sum + f.totalExecutions, 0)}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Clock className="h-8 w-8 text-warning" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Avg Execution Time</p>
                <p className="text-2xl font-bold text-foreground">
                  {flows.length > 0 
                    ? formatExecutionTime(
                        flows.reduce((sum: number, f: FlowDefinition) => sum + f.averageExecutionTimeMs, 0) / flows.length
                      )
                    : '0ms'
                  }
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Visual Flows */}
      <div className="space-y-6">
        {flows.map((flow: FlowDefinition) => (
          <Card key={flow.id} className="app-card border">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  {getFlowTypeIcon(flow.flowType)}
                  <div>
                    <CardTitle className="text-lg text-foreground">
                      {flow.name}
                      <span className="ml-2 text-sm text-muted-foreground">v{flow.flowVersion}</span>
                    </CardTitle>
                    <p className="text-sm text-muted-foreground">{flow.description}</p>
                    <div className="flex items-center space-x-4 mt-1 text-xs text-muted-foreground">
                      <span>Type: {flow.flowType}</span>
                      <span>Updated: {new Date(flow.updatedAt).toLocaleDateString()}</span>
                      {flow.scheduleEnabled && (
                        <span className="flex items-center space-x-1">
                          <Clock className="h-3 w-3" />
                          <span>Scheduled</span>
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                <div className="flex items-center space-x-2">
                  {runningExecutions.has(flow.id) && (
                    <span className="text-xs px-2 py-1 rounded bg-info/20 text-info flex items-center space-x-1">
                      <div className="w-2 h-2 bg-info rounded-full animate-pulse" />
                      <span>Running</span>
                    </span>
                  )}
                  <span className={`text-xs px-2 py-1 rounded ${
                    flow.active ? 'bg-success/20 text-success' :
                    'bg-muted/20 text-muted-foreground'
                  }`}>
                    {flow.active ? 'Active' : 'Inactive'}
                  </span>
                  <span className={`text-xs px-2 py-1 rounded ${
                    flow.deployed ? 'bg-info/20 text-info' :
                    'bg-warning/20 text-warning'
                  }`}>
                    {flow.deployed ? 'Deployed' : 'Not Deployed'}
                  </span>
                  <div className={`w-3 h-3 rounded-full ${
                    runningExecutions.has(flow.id) ? 'bg-info animate-pulse' :
                    flow.active ? 'bg-success animate-glow' : 'bg-muted-foreground'
                  }`} />
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-6">
                {/* Flow Complexity Overview */}
                <div className="bg-secondary rounded-lg p-4">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div className="flex items-center space-x-2">
                      <Workflow className="h-4 w-4 text-primary" />
                      <div>
                        <span className="text-muted-foreground">Nodes:</span>
                        <span className="ml-1 text-foreground font-medium">
                          {flow.flowDefinition?.nodes?.length || 0}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      <ArrowRight className="h-4 w-4 text-info" />
                      <div>
                        <span className="text-muted-foreground">Connections:</span>
                        <span className="ml-1 text-foreground font-medium">
                          {flow.flowDefinition?.edges?.length || 0}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      <CheckCircle className="h-4 w-4 text-success" />
                      <div>
                        <span className="text-muted-foreground">Success Rate:</span>
                        <span className="ml-1 text-success font-medium">
                          {calculateSuccessRate(flow.successfulExecutions, flow.totalExecutions)}%
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      <Clock className="h-4 w-4 text-warning" />
                      <div>
                        <span className="text-muted-foreground">Avg Time:</span>
                        <span className="ml-1 text-foreground font-medium">
                          {formatExecutionTime(flow.averageExecutionTimeMs)}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Flow Statistics */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                  <div>
                    <span className="text-muted-foreground">Total Executions:</span>
                    <span className="ml-2 text-foreground font-medium">{flow.totalExecutions}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Successful:</span>
                    <span className="ml-2 text-success font-medium">{flow.successfulExecutions}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Failed:</span>
                    <span className="ml-2 text-destructive font-medium">{flow.failedExecutions}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Created:</span>
                    <span className="ml-2 text-foreground">
                      {new Date(flow.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>

                {/* Action Buttons */}
                <div className="flex items-center justify-end pt-4 border-t border-border">
                  {isAdmin() && (
                    <div className="flex items-center space-x-2">
                      <Button 
                        variant="outline" 
                        size="sm" 
                        asChild={!flow.deployed}
                        disabled={flow.deployed}
                        className={flow.deployed ? "opacity-50 cursor-not-allowed" : ""}
                        title={flow.deployed ? "Flow must be undeployed before editing" : ""}
                      >
                        {flow.deployed ? (
                          <span>
                            <Workflow className="h-4 w-4 mr-2" />
                            Edit Visual Flow
                          </span>
                        ) : (
                          <Link to={`/flows/${flow.id}/visual`}>
                            <Workflow className="h-4 w-4 mr-2" />
                            Edit Visual Flow
                          </Link>
                        )}
                      </Button>
                      
                      <Button variant="outline" size="sm" asChild>
                        <Link to={`/flows/${flow.id}/visual?mode=view`}>
                          <Eye className="h-4 w-4 mr-2" />
                          View Details
                        </Link>
                      </Button>
                      
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => exportFlowMutation.mutate(flow.id)}
                        disabled={exportFlowMutation.isPending}
                      >
                        <Download className="h-4 w-4 mr-2" />
                        Export
                      </Button>
                      
                      {/* Deploy/Undeploy buttons */}
                      {flow.deployed ? (
                        <Button
                          variant="outline"
                          size="sm"
                          className="text-warning hover:bg-warning/10 hover:text-warning"
                          onClick={() => undeployFlowMutation.mutate(flow.id)}
                          disabled={undeployFlowMutation.isPending}
                        >
                          {undeployFlowMutation.isPending ? (
                            <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                          ) : (
                            <Square className="h-4 w-4 mr-2" />
                          )}
                          Undeploy
                        </Button>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          className="text-info hover:bg-info/10 hover:text-info"
                          onClick={() => deployFlowMutation.mutate(flow.id)}
                          disabled={deployFlowMutation.isPending || !flow.active}
                        >
                          {deployFlowMutation.isPending ? (
                            <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                          ) : (
                            <Play className="h-4 w-4 mr-2" />
                          )}
                          Deploy
                        </Button>
                      )}
                      
                      <Button
                        variant="outline"
                        size="sm"
                        className={`text-destructive hover:bg-destructive/10 hover:text-destructive ${flow.deployed ? "opacity-50 cursor-not-allowed" : ""}`}
                        onClick={() => !flow.deployed && deleteFlowMutation.mutate(flow.id)}
                        disabled={deleteFlowMutation.isPending || flow.deployed}
                        title={flow.deployed ? "Flow must be undeployed before deleting" : ""}
                      >
                        <Trash2 className="h-4 w-4 mr-2" />
                        Delete
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>


      {/* No flows message */}
      {flows.length === 0 && (
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Workflow className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">No Visual Flows</h3>
              <p className="text-muted-foreground mb-6">Create your first visual integration flow using our drag-and-drop builder</p>
              <div className="flex items-center space-x-3 justify-center">
                <Button asChild className="btn-primary">
                  <Link to="/flows/create">
                    <Plus className="h-4 w-4 mr-2" />
                    Create Flow
                  </Link>
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

export default FlowManagement