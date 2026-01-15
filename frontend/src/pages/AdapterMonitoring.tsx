import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { 
  Play, 
  Square, 
  Activity, 
  CheckCircle, 
  XCircle, 
  Clock,
  RefreshCw,
  Eye,
  BarChart3
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useNotifications } from '@/stores/ui'
import { adapterApi } from '@/lib/api'
import { cn } from '@/lib/utils'

interface Adapter {
  id: string
  name: string
  adapterType: 'FILE' | 'SFTP' | 'EMAIL'
  direction: 'SENDER' | 'RECEIVER'
  active: boolean
  status: 'STARTED' | 'STOPPED' | 'STARTING' | 'STOPPING'
  lastExecution?: string
  executionCount: number
  errorCount: number
  lastError?: string
  configuration: any
  createdAt: string
}


const AdapterMonitoring: React.FC = () => {
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [typeFilter, setTypeFilter] = useState<string>('all')
  const [selectedAdapterId, setSelectedAdapterId] = useState<string | null>(null)
  const [pendingStartIds, setPendingStartIds] = useState<Set<string>>(new Set())
  const [pendingStopIds, setPendingStopIds] = useState<Set<string>>(new Set())

  // Fetch adapters with status
  const { data: adaptersResponse, isLoading: adaptersLoading, refetch: refetchAdapters } = useQuery({
    queryKey: ['adapters-monitoring'],
    queryFn: () => adapterApi.getAllAdapters(),
    refetchInterval: 5000, // Refresh every 5 seconds for real-time status
  })


  // Fetch adapter execution history for selected adapter
  const { data: executionHistoryResponse } = useQuery({
    queryKey: ['adapter-execution-history', selectedAdapterId],
    queryFn: () => selectedAdapterId ? adapterApi.getAdapterExecutionHistory(selectedAdapterId, { limit: 5 }) : null,
    enabled: !!selectedAdapterId,
    refetchInterval: 5000, // Refresh every 5 seconds
  })

  const adapters = adaptersResponse?.data || []
  const executionHistory = executionHistoryResponse?.data

  // Start adapter mutation (optimistic UI)
  const startAdapterMutation = useMutation({
    mutationFn: (adapterId: string) => adapterApi.startAdapter(adapterId),
    onMutate: async (adapterId) => {
      await queryClient.cancelQueries({ queryKey: ['adapters-monitoring'] })

      setPendingStartIds(prev => new Set(prev).add(adapterId))

      const previous = queryClient.getQueryData<any>(['adapters-monitoring'])

      queryClient.setQueryData(['adapters-monitoring'], (old: any) => {
        if (!old) return old
        const next = { ...old }
        const list = Array.isArray(old.data) ? old.data : []
        next.data = list.map((a: Adapter) =>
          a.id === adapterId ? { ...a, status: 'STARTING' as const } : a
        )
        return next
      })

      return { previous, adapterId }
    },
    onSuccess: () => {
      success('Adapter Started', 'Adapter started successfully')
    },
    onError: (err: any, _adapterId, ctx) => {
      if (ctx?.previous) {
        queryClient.setQueryData(['adapters-monitoring'], ctx.previous)
      }
      error('Start Failed', err.response?.data?.message || 'Failed to start adapter')
    },
    onSettled: (_data, _error, adapterId) => {
      setPendingStartIds(prev => {
        const next = new Set(prev)
        next.delete(adapterId)
        return next
      })
      queryClient.invalidateQueries({ queryKey: ['adapters-monitoring'] })
    },
  })

  // Stop adapter mutation (optimistic UI)
  const stopAdapterMutation = useMutation({
    mutationFn: (adapterId: string) => adapterApi.stopAdapter(adapterId),
    onMutate: async (adapterId) => {
      await queryClient.cancelQueries({ queryKey: ['adapters-monitoring'] })

      setPendingStopIds(prev => new Set(prev).add(adapterId))

      const previous = queryClient.getQueryData<any>(['adapters-monitoring'])

      queryClient.setQueryData(['adapters-monitoring'], (old: any) => {
        if (!old) return old
        const next = { ...old }
        const list = Array.isArray(old.data) ? old.data : []
        next.data = list.map((a: Adapter) =>
          a.id === adapterId ? { ...a, status: 'STOPPING' as const } : a
        )
        return next
      })

      return { previous, adapterId }
    },
    onSuccess: () => {
      success('Adapter Stopped', 'Adapter stopped successfully')
    },
    onError: (err: any, _adapterId, ctx) => {
      if (ctx?.previous) {
        queryClient.setQueryData(['adapters-monitoring'], ctx.previous)
      }
      error('Stop Failed', err.response?.data?.message || 'Failed to stop adapter')
    },
    onSettled: (_data, _error, adapterId) => {
      setPendingStopIds(prev => {
        const next = new Set(prev)
        next.delete(adapterId)
        return next
      })
      queryClient.invalidateQueries({ queryKey: ['adapters-monitoring'] })
    },
  })

  const getStatusLED = (status: string, errorCount: number) => {
    if (errorCount > 0) {
      return "bg-red-500"  // Red for errors
    } else if (status === 'STARTED') {
      return "bg-green-500"  // Green for started
    } else {
      return "bg-gray-400"  // Gray for stopped
    }
  }



  const filteredAdapters = adapters.filter((adapter: Adapter) => {
    const statusMatch = statusFilter === 'all' || adapter.status === statusFilter
    const typeMatch = typeFilter === 'all' || adapter.adapterType === typeFilter
    return statusMatch && typeMatch
  })


  if (adaptersLoading) {
    return (
      <div className="content-spacing">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading adapter monitoring...</span>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">Adapter Monitoring</h1>
          <p className="text-muted-foreground">Monitor and control adapter status, performance, and logs</p>
        </div>
        <Button
          variant="outline"
          onClick={() => refetchAdapters()}
        >
          <RefreshCw className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      {/* Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Activity className="h-8 w-8 text-primary" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Adapters</p>
                <p className="text-2xl font-bold text-foreground">{adapters.length}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <CheckCircle className="h-8 w-8 text-green-500" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Started</p>
                <p className="text-2xl font-bold text-foreground">
                  {adapters.filter((a: Adapter) => a.status === 'STARTED').length}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Square className="h-8 w-8 text-gray-500" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Stopped</p>
                <p className="text-2xl font-bold text-foreground">
                  {adapters.filter((a: Adapter) => a.status === 'STOPPED').length}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <XCircle className="h-8 w-8 text-red-500" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Errors</p>
                <p className="text-2xl font-bold text-foreground">
                  {adapters.filter((a: Adapter) => (a.configuration?.errorCount || 0) > 0).length}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <Card className="app-card border mb-6">
        <CardHeader>
          <CardTitle className="text-foreground">Adapter Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="text-sm font-medium text-muted-foreground">Status</label>
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Statuses</SelectItem>
                  <SelectItem value="STARTED">Started</SelectItem>
                  <SelectItem value="STOPPED">Stopped</SelectItem>
                  <SelectItem value="STARTING">Starting</SelectItem>
                  <SelectItem value="STOPPING">Stopping</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <label className="text-sm font-medium text-muted-foreground">Type</label>
              <Select value={typeFilter} onValueChange={setTypeFilter}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Types</SelectItem>
                  <SelectItem value="FILE">File</SelectItem>
                  <SelectItem value="SFTP">SFTP</SelectItem>
                  <SelectItem value="EMAIL">Email</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <label className="text-sm font-medium text-muted-foreground">Search</label>
              <Input
                placeholder="Search adapters..."
                value=""
                onChange={() => {}}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Adapters List */}
      <div className="grid grid-cols-1 gap-6 mb-8">
        {filteredAdapters.map((adapter: Adapter) => (
          <Card key={adapter.id} className="app-card border">
            <CardHeader>
              <div className="flex items-center space-x-3">
                <input type="checkbox" className="h-4 w-4 rounded border-border" />
                <div 
                  className={cn(
                    "h-3 w-3 rounded-full",
                    getStatusLED(adapter.status, adapter.configuration?.errorCount || 0)
                  )}
                />
                <div>
                  <CardTitle className="text-lg text-foreground">{adapter.name}</CardTitle>
                  <p className="text-sm text-muted-foreground">
                    {adapter.adapterType} • {adapter.direction}
                  </p>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
                <div>
                  <span className="text-muted-foreground">Executions:</span>
                  <span className="ml-2 text-foreground">{adapter.configuration?.executionCount || 0}</span>
                </div>
                <div>
                  <span className="text-muted-foreground">Last Run:</span>
                  <span className="ml-2 text-foreground">
                    {adapter.configuration?.lastExecution ? new Date(adapter.configuration.lastExecution).toLocaleString() : 'Never'}
                  </span>
                </div>
                <div>
                  <span className="text-muted-foreground">Active:</span>
                  <span className="ml-2 text-foreground">{adapter.active ? 'Yes' : 'No'}</span>
                </div>
              </div>

              {adapter.configuration?.lastError && (
                <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg">
                  <div className="flex items-center space-x-2 text-red-700 mb-1">
                    <XCircle className="h-4 w-4" />
                    <span className="font-medium">Last Error</span>
                  </div>
                  <p className="text-sm text-red-600">{adapter.configuration.lastError}</p>
                </div>
              )}

              {/* Action Buttons - Bottom Left */}
              <div className="mt-4 flex items-center space-x-1">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => startAdapterMutation.mutate(adapter.id)}
                  disabled={pendingStartIds.has(adapter.id) || adapter.status === 'STARTED' || adapter.status === 'STARTING'}
                >
                  <Play className="h-4 w-4 mr-1" />
                  Start
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => stopAdapterMutation.mutate(adapter.id)}
                  disabled={pendingStopIds.has(adapter.id) || pendingStartIds.has(adapter.id) || adapter.status === 'STOPPED' || adapter.status === 'STOPPING'}
                >
                  <Square className="h-4 w-4 mr-1" />
                  Stop
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setSelectedAdapterId(selectedAdapterId === adapter.id ? null : adapter.id)}
                  className={selectedAdapterId === adapter.id ? 'bg-primary/10 border-primary' : ''}
                >
                  <BarChart3 className="h-4 w-4 mr-1" />
                  Logs
                </Button>
              </div>

              {/* Adapter Execution History */}
              {selectedAdapterId === adapter.id && executionHistory && (
                <div className="mt-4 p-4 bg-secondary/20 border border-secondary rounded-lg">
                  <div className="flex items-center space-x-2 mb-3">
                    <BarChart3 className="h-4 w-4 text-primary" />
                    <h4 className="font-medium text-foreground">Recent Executions</h4>
                    <span className="text-xs text-muted-foreground">
                      ({executionHistory.recentExecutionsShown} of {executionHistory.totalExecutions})
                    </span>
                  </div>
                  
                  {executionHistory.executions && executionHistory.executions.length > 0 ? (
                    <div className="space-y-2">
                      {executionHistory.executions.map((execution: any) => (
                        <div 
                          key={execution.executionId}
                          className="flex items-center justify-between p-2 bg-background rounded border hover:bg-secondary/30 transition-colors cursor-pointer"
                          onClick={() => navigate(`/execution-logs/${execution.executionId}`)}
                        >
                          <div className="flex items-center space-x-3">
                            <span className="text-lg">{execution.statusIcon}</span>
                            <div className="flex flex-col">
                              <span className="font-mono text-sm text-primary hover:underline">
                                {execution.messageId}
                              </span>
                              <span className="text-xs text-muted-foreground">
                                {execution.flowName} • {execution.startedAt ? new Date(execution.startedAt).toLocaleString() : 'Unknown'}
                              </span>
                            </div>
                          </div>
                          <div className="flex items-center space-x-2 text-sm">
                            <span className="text-muted-foreground">{execution.statusText}</span>
                            <span className="text-muted-foreground">•</span>
                            <span className="text-muted-foreground">{execution.duration}</span>
                            <span className="text-muted-foreground">•</span>
                            <span className="text-muted-foreground">{execution.filesText}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="text-center py-4">
                      <Clock className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
                      <p className="text-sm text-muted-foreground">No recent executions found</p>
                    </div>
                  )}
                  
                  <div className="mt-3 flex justify-end">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => navigate(`/flow-monitoring?adapterId=${adapter.id}`)}
                    >
                      <Eye className="h-4 w-4 mr-1" />
                      View All Executions
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

    </div>
  )
}

export default AdapterMonitoring