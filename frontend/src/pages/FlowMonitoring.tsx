import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { 
  RotateCcw, 
  Activity, 
  CheckCircle, 
  XCircle, 
  Clock,
  RefreshCw,
  Eye,
  AlertTriangle
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useNotifications } from '@/stores/ui'
import { executionApi, flowApi } from '@/lib/api'
import { cn } from '@/lib/utils'

interface FlowExecution {
  id: string
  flowId: string
  flowName: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'TIMEOUT' | 'RETRY_PENDING'
  startedAt: string
  completedAt?: string
  duration?: string
  filesProcessed?: number
  bytesProcessed?: string
  errorMessage?: string
  correlationId?: string
  isRetry?: boolean
  triggerType?: string
  priority?: number
}


// Removed unused FlowExecutionStepDetailed interface

// Removed unused FlowStepLog interface

const FlowMonitoring: React.FC = () => {
  const navigate = useNavigate()
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()
  
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [flowFilter, setFlowFilter] = useState<string>('all')

  // Fetch flow executions
  const { data: executionsResponse, isLoading: executionsLoading } = useQuery({
    queryKey: ['flow-executions', statusFilter, flowFilter],
    queryFn: () => executionApi.getAllExecutions({ 
      status: statusFilter === 'all' ? undefined : statusFilter,
      flowId: flowFilter === 'all' ? undefined : flowFilter,
      size: 50 
    }),
    refetchInterval: 5000, // Refresh every 5 seconds for real-time monitoring
  })

  // Fetch all flows for filter
  const { data: flowsResponse } = useQuery({
    queryKey: ['flows-for-monitoring'],
    queryFn: () => flowApi.getAllFlows(),
  })

  const executions = executionsResponse?.data?.executions || []
  const flows = flowsResponse?.data || []

  // Restart execution mutation
  const restartExecutionMutation = useMutation({
    mutationFn: (executionId: string) => executionApi.retryExecution(executionId),
    onSuccess: () => {
      success('Execution Restarted', 'Flow execution has been restarted successfully')
      queryClient.invalidateQueries({ queryKey: ['flow-executions'] })
    },
    onError: (err: any) => {
      error('Restart Failed', err.response?.data?.message || 'Failed to restart execution')
    },
  })

  // Removed unused cancelExecutionMutation

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle className="h-4 w-4 text-green-500" />
      case 'FAILED':
        return <XCircle className="h-4 w-4 text-red-500" />
      case 'RUNNING':
      case 'RETRY_PENDING':
        return <Clock className="h-4 w-4 text-blue-500 animate-spin" />
      case 'CANCELLED':
        return <XCircle className="h-4 w-4 text-gray-500" />
      case 'TIMEOUT':
        return <AlertTriangle className="h-4 w-4 text-red-500" />
      case 'PENDING':
        return <Clock className="h-4 w-4 text-yellow-500" />
      default:
        return <AlertTriangle className="h-4 w-4 text-yellow-500" />
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'bg-green-100 text-green-700'
      case 'FAILED':
        return 'bg-red-100 text-red-700'
      case 'RUNNING':
      case 'RETRY_PENDING':
        return 'bg-blue-100 text-blue-700'
      case 'CANCELLED':
        return 'bg-gray-100 text-gray-700'
      case 'TIMEOUT':
        return 'bg-red-100 text-red-700'
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-700'
      default:
        return 'bg-yellow-100 text-yellow-700'
    }
  }



  if (executionsLoading) {
    return (
      <div className="content-spacing">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading flow monitoring...</span>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="content-spacing h-screen flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-6 flex-shrink-0">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">Flow Monitoring</h1>
          <p className="text-muted-foreground">Monitor flow executions, logs, and restart failed flows</p>
        </div>
        <Button
          variant="outline"
          onClick={() => {
            queryClient.invalidateQueries({ queryKey: ['flow-executions'] })
          }}
        >
          <RefreshCw className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      <div className="flex-1 flex flex-col space-y-6">
        {/* Executions List */}
        <div className="flex-1 flex flex-col space-y-6">
          {/* Statistics */}
          <div className="grid grid-cols-2 gap-4 flex-shrink-0">
            <Card className="app-card border">
              <CardContent className="flex items-center p-4">
                <div className="flex items-center space-x-3">
                  <Activity className="h-6 w-6 text-primary" />
                  <div>
                    <p className="text-sm font-medium text-muted-foreground">Total Executions</p>
                    <p className="text-xl font-bold text-foreground">{executions.length}</p>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card className="app-card border">
              <CardContent className="flex items-center p-4">
                <div className="flex items-center space-x-3">
                  <XCircle className="h-6 w-6 text-red-500" />
                  <div>
                    <p className="text-sm font-medium text-muted-foreground">Failed</p>
                    <p className="text-xl font-bold text-foreground">
                      {executions.filter((e: FlowExecution) => e.status === 'FAILED' || e.status === 'TIMEOUT').length}
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Filters */}
          <Card className="app-card border flex-shrink-0">
            <CardHeader>
              <CardTitle className="text-sm text-foreground">Filters</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 gap-3">
                <Select value={statusFilter} onValueChange={setStatusFilter}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Statuses</SelectItem>
                    <SelectItem value="PENDING">Pending</SelectItem>
                    <SelectItem value="RUNNING">Running</SelectItem>
                    <SelectItem value="COMPLETED">Completed</SelectItem>
                    <SelectItem value="FAILED">Failed</SelectItem>
                    <SelectItem value="CANCELLED">Cancelled</SelectItem>
                    <SelectItem value="TIMEOUT">Timeout</SelectItem>
                    <SelectItem value="RETRY_PENDING">Retry Pending</SelectItem>
                  </SelectContent>
                </Select>

                <Select value={flowFilter} onValueChange={setFlowFilter}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Flows</SelectItem>
                    {flows.map((flow: any) => (
                      <SelectItem key={flow.id} value={flow.id}>
                        {flow.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </CardContent>
          </Card>

          {/* Executions */}
          <div className="flex-1 space-y-3 overflow-y-auto">
            {executions.map((execution: FlowExecution) => (
              <Card 
                key={execution.id} 
                className="app-card border transition-all hover:shadow-md"
              >
                <CardContent className="p-4">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center space-x-2">
                      {getStatusIcon(execution.status)}
                      <span className="font-medium text-foreground">{execution.flowName}</span>
                    </div>
                    <span className={cn("text-xs px-2 py-1 rounded", getStatusColor(execution.status))}>
                      {execution.status}
                    </span>
                  </div>
                  
                  <div className="text-sm text-muted-foreground space-y-1">
                    <div className="flex items-center space-x-2">
                      <span>Started: {new Date(execution.startedAt).toLocaleString()}</span>
                      {execution.triggerType && (
                        <span>• Trigger: {execution.triggerType}</span>
                      )}
                    </div>
                    <div className="flex items-center space-x-4">
                      <span>Files: {execution.filesProcessed || 0}</span>
                      <span>Size: {execution.bytesProcessed || '0 B'}</span>
                      {execution.duration && (
                        <span>Duration: {execution.duration}</span>
                      )}
                      {execution.isRetry && (
                        <span className="text-orange-600">• Retry</span>
                      )}
                    </div>
                    
                    <div className="flex items-center space-x-2 mt-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={(e: React.MouseEvent) => {
                          e.stopPropagation()
                          navigate(`/execution-logs/${execution.id}`)
                        }}
                      >
                        <Eye className="h-3 w-3 mr-1" />
                        View Logs
                      </Button>
                      
                      {execution.status === 'FAILED' && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={(e: React.MouseEvent) => {
                            e.stopPropagation()
                            restartExecutionMutation.mutate(execution.id)
                          }}
                          disabled={restartExecutionMutation.isPending}
                        >
                          <RotateCcw className="h-3 w-3 mr-1" />
                          {restartExecutionMutation.isPending ? 'Retrying...' : 'Retry'}
                        </Button>
                      )}
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </div>

    </div>
  )
}

export default FlowMonitoring