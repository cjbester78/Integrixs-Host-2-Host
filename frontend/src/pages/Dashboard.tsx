import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Activity, FileText, CheckCircle, Clock, TrendingUp, AlertTriangle, RefreshCw, Boxes, Play } from 'lucide-react'
import { dashboardApi, configApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { ManualFlowExecutionModal } from '@/components/ui/ManualFlowExecutionModal'
import type { SystemHealth, AdapterStats, RecentExecution } from '@/types/dashboard'

export const Dashboard: React.FC = () => {
  const [isFlowExecutionModalOpen, setIsFlowExecutionModalOpen] = useState(false)

  // Fetch configuration intervals first
  const { data: intervals } = useQuery({
    queryKey: ['configuration', 'dashboard-intervals'],
    queryFn: configApi.getDashboardIntervals,
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
  })

  // Extract intervals with defaults
  const refreshIntervals = intervals?.intervals || {
    'dashboard.refresh.health_interval': 30000,
    'dashboard.refresh.adapter_stats_interval': 60000,
    'dashboard.refresh.recent_executions_interval': 60000,
  }

  // Fetch dashboard data with configurable intervals
  const { data: systemHealth, isLoading: healthLoading } = useQuery<SystemHealth>({
    queryKey: ['dashboard', 'health'],
    queryFn: dashboardApi.getSystemHealth,
    refetchInterval: refreshIntervals['dashboard.refresh.health_interval'],
  })

  const { data: adapterStats, isLoading: statsLoading } = useQuery<AdapterStats>({
    queryKey: ['dashboard', 'adapter-stats'],
    queryFn: dashboardApi.getAdapterStatistics,
    refetchInterval: refreshIntervals['dashboard.refresh.adapter_stats_interval'],
  })

  const { data: recentExecutions, isLoading: executionsLoading } = useQuery<RecentExecution[]>({
    queryKey: ['dashboard', 'recent-executions'],
    queryFn: () => dashboardApi.getRecentExecutions(10),
    refetchInterval: refreshIntervals['dashboard.refresh.recent_executions_interval'],
  })

  const isLoading = healthLoading || statsLoading || executionsLoading
  // Loading state
  if (isLoading) {
    return (
      <div className="content-spacing">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading dashboard...</span>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">Integrixs Host 2 Host Dashboard</h1>
          <p className="text-muted-foreground">Monitor your bank file transfer operations and system health</p>
        </div>
        <div className="flex items-center space-x-3">
          <div className="flex items-center space-x-2">
            <div className={`w-3 h-3 rounded-full ${
              systemHealth?.status === 'UP' ? 'bg-green-500 animate-glow' : 'bg-red-500'
            }`} />
            <span className={`text-sm font-medium ${
              systemHealth?.status === 'UP' ? 'text-green-500' : 'text-red-500'
            }`}>
              {systemHealth?.status === 'UP' ? 'Up' : 'Down'}
            </span>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => window.location.reload()}
            className="flex items-center space-x-2"
          >
            <RefreshCw className="h-4 w-4" />
            <span>Refresh</span>
          </Button>
        </div>
      </div>

      {/* Status Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* System Health Card */}
        <div className="app-card rounded-lg p-6 border hover-scale animate-fade-in">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-muted-foreground">System Status</p>
              <p className={`text-2xl font-bold ${
                systemHealth?.status === 'UP' ? 'text-green-500' : 'text-red-500'
              }`}>
                {systemHealth?.status === 'UP' ? 'Up' : 'Down'}
              </p>
            </div>
            <Activity className={`h-8 w-8 ${
              systemHealth?.status === 'UP' ? 'text-green-500' : 'text-red-500'
            }`} />
          </div>
          <div className="mt-2">
            <span className="text-xs text-muted-foreground">
              Uptime: {systemHealth?.uptime || '0%'}
            </span>
          </div>
        </div>

        {/* Operations Today Card */}
        <div className="app-card rounded-lg p-6 border hover-scale animate-fade-in">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Operations Today</p>
              <p className="text-2xl font-bold text-foreground">
                {adapterStats?.operationsToday || 0}
              </p>
            </div>
            <TrendingUp className="h-8 w-8 text-primary" />
          </div>
          <div className="mt-2">
            <span className="text-xs text-muted-foreground">
              {adapterStats?.operationsTrend || 'No trend data'}
            </span>
          </div>
        </div>

        {/* Files Processed Card */}
        <div className="app-card rounded-lg p-6 border hover-scale animate-fade-in">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Files Processed</p>
              <p className="text-2xl font-bold text-foreground">
                {adapterStats?.filesProcessed || 0}
              </p>
            </div>
            <FileText className="h-8 w-8 text-info" />
          </div>
          <div className="mt-2">
            <span className="text-xs text-muted-foreground">
              Last hour: {adapterStats?.filesLastHour || 0} files
            </span>
          </div>
        </div>

        {/* Success Rate Card */}
        <div className="app-card rounded-lg p-6 border hover-scale animate-fade-in">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Success Rate</p>
              <p className={`text-2xl font-bold ${
                (adapterStats?.successRate || 0) >= 95 ? 'text-success' :
                (adapterStats?.successRate || 0) >= 85 ? 'text-warning' : 'text-destructive'
              }`}>
                {adapterStats?.successRate ? `${adapterStats.successRate}%` : '0%'}
              </p>
            </div>
            <CheckCircle className={`h-8 w-8 ${
              (adapterStats?.successRate || 0) >= 95 ? 'text-success' :
              (adapterStats?.successRate || 0) >= 85 ? 'text-warning' : 'text-destructive'
            }`} />
          </div>
          <div className="mt-2">
            <span className="text-xs text-muted-foreground">
              {adapterStats?.errorsToday || 0} errors today
            </span>
          </div>
        </div>
      </div>

      {/* Recent Operations */}
      <div className="app-card rounded-lg p-6 border animate-fade-in">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold text-foreground">Recent Operations</h2>
          <span className="text-sm text-muted-foreground">
            {recentExecutions?.length || 0} operations
          </span>
        </div>
        
        {recentExecutions && recentExecutions.length > 0 ? (
          <div className="space-y-3">
            {recentExecutions.map((execution, index) => {
              const getStatusIcon = (status: string) => {
                switch (status.toUpperCase()) {
                  case 'SUCCESS':
                    return <CheckCircle className="h-5 w-5 text-success" />
                  case 'FAILED':
                    return <AlertTriangle className="h-5 w-5 text-destructive" />
                  case 'IN_PROGRESS':
                  case 'RUNNING':
                    return <Clock className="h-5 w-5 text-warning animate-pulse" />
                  default:
                    return <Clock className="h-5 w-5 text-muted-foreground" />
                }
              }

              const formatRelativeTime = (timestamp: string) => {
                const now = new Date()
                const executionTime = new Date(timestamp)
                const diffMs = now.getTime() - executionTime.getTime()
                const diffMins = Math.floor(diffMs / 60000)
                
                if (diffMins < 1) return 'Just now'
                if (diffMins < 60) return `${diffMins} min ago`
                const diffHours = Math.floor(diffMins / 60)
                if (diffHours < 24) return `${diffHours}h ago`
                return `${Math.floor(diffHours / 24)}d ago`
              }

              return (
                <div key={execution.id || index} className="flex items-center justify-between p-3 rounded-md bg-secondary">
                  <div className="flex items-center space-x-3">
                    {getStatusIcon(execution.status)}
                    <div>
                      <p className="font-medium text-foreground">
                        {execution.adapterName || 'Unknown'} - {execution.operation || 'Operation'}
                      </p>
                      <p className="text-sm text-muted-foreground">
                        {execution.message || `${execution.filesProcessed || 0} files processed`}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-foreground">
                      {execution.startTime ? new Date(execution.startTime).toLocaleTimeString() : '--:--'}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {execution.startTime ? formatRelativeTime(execution.startTime) : 'Unknown'}
                    </p>
                  </div>
                </div>
              )
            })}
          </div>
        ) : (
          <div className="flex items-center justify-center py-8">
            <div className="text-center">
              <Activity className="h-12 w-12 text-muted-foreground mx-auto mb-2" />
              <p className="text-muted-foreground">No recent operations</p>
            </div>
          </div>
        )}
      </div>

      {/* Adapter Overview and Quick Actions */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Active Adapters */}
        <div className="lg:col-span-2 app-card rounded-lg p-6 border animate-fade-in">
          <h2 className="text-xl font-semibold text-foreground mb-4">Active Adapters</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {adapterStats?.adapters?.map((adapter, index) => (
              <div key={adapter.id || index} className="bg-secondary rounded-lg p-4">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center space-x-2">
                    <div className={`w-3 h-3 rounded-full ${
                      adapter.status === 'ACTIVE' ? 'bg-success animate-glow' :
                      adapter.status === 'ERROR' ? 'bg-destructive' :
                      'bg-warning'
                    }`} />
                    <h3 className="font-medium text-foreground">{adapter.name}</h3>
                  </div>
                  <span className={`text-xs px-2 py-1 rounded ${
                    adapter.status === 'ACTIVE' ? 'bg-success/20 text-success' :
                    adapter.status === 'ERROR' ? 'bg-destructive/20 text-destructive' :
                    'bg-warning/20 text-warning'
                  }`}>
                    {adapter.status}
                  </span>
                </div>
                <div className="space-y-1 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Type:</span>
                    <span className="text-foreground">{adapter.type}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Last Run:</span>
                    <span className="text-foreground">
                      {adapter.lastRun ? new Date(adapter.lastRun).toLocaleString() : 'Never'}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Files Today:</span>
                    <span className="text-foreground">{adapter.filesToday || 0}</span>
                  </div>
                </div>
              </div>
            )) || (
              <div className="md:col-span-2 flex items-center justify-center py-8">
                <div className="text-center">
                  <Boxes className="h-12 w-12 text-muted-foreground mx-auto mb-2" />
                  <p className="text-muted-foreground">No adapters configured</p>
                  <Button variant="outline" size="sm" className="mt-2">
                    Configure Adapters
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="app-card rounded-lg p-6 border animate-fade-in">
          <h2 className="text-xl font-semibold text-foreground mb-4">Quick Actions</h2>
          <div className="space-y-3">
            <Button 
              className="w-full btn-primary justify-start" 
              size="lg"
              onClick={() => setIsFlowExecutionModalOpen(true)}
            >
              <Play className="h-4 w-4 mr-2" />
              Manually Execute Flow
            </Button>
            <Button variant="outline" className="w-full justify-start" size="lg" asChild>
              <Link to="/admin?tab=logs">
                <FileText className="h-4 w-4 mr-2" />
                View System Logs
              </Link>
            </Button>
          </div>

          {/* System Info */}
          <div className="mt-6 pt-4 border-t border-border">
            <h3 className="font-medium text-foreground mb-3">System Info</h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Version:</span>
                <span className="text-foreground">v1.0.0</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Java:</span>
                <span className="text-foreground">{systemHealth?.javaVersion || 'Unknown'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Memory:</span>
                <span className="text-foreground">{systemHealth?.memoryUsage || '0%'}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Manual Flow Execution Modal */}
      <ManualFlowExecutionModal 
        open={isFlowExecutionModalOpen}
        onOpenChange={setIsFlowExecutionModalOpen}
      />
    </div>
  )
}