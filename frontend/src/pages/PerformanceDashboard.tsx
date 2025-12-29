import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { 
  TrendingUp,
  BarChart3,
  Activity,
  AlertTriangle,
  RefreshCw,
  Download,
  Zap,
  Target,
  Gauge
} from 'lucide-react'
import { executionApi, flowApi, interfaceApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { usePermissions } from '@/hooks/auth'
import { useWebSocketConnection, useSystemHealthUpdates } from '@/hooks/useWebSocket'
import WebSocketStatus from '@/components/WebSocketStatus'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

interface PerformanceMetrics {
  totalExecutions: number
  successfulExecutions: number
  failedExecutions: number
  averageExecutionTime: number
  successRate: number
  executionsToday: number
  executionsThisWeek: number
  executionsThisMonth: number
  peakExecutionHour: number
  slowestExecution: number
  fastestExecution: number
  mostActiveFlow: string
  errorRate: number
  retryRate: number
}

interface FlowPerformance {
  flowId: string
  flowName: string
  totalExecutions: number
  successfulExecutions: number
  failedExecutions: number
  averageExecutionTime: number
  successRate: number
  lastExecution: string
  trend: 'up' | 'down' | 'stable'
}

interface AdapterPerformance {
  adapterId: string
  adapterName: string
  adapterType: string
  totalExecutions: number
  successfulExecutions: number
  failedExecutions: number
  averageExecutionTime: number
  successRate: number
  lastExecution: string
  connectionStatus: 'online' | 'offline' | 'degraded'
}

interface SystemHealth {
  cpuUsage: number
  memoryUsage: number
  diskUsage: number
  networkLatency: number
  activeConnections: number
  queueSize: number
  uptime: number
  healthStatus: 'healthy' | 'warning' | 'critical'
}

const PerformanceDashboard: React.FC = () => {
  const { isAdmin } = usePermissions()
  const [timeRange, setTimeRange] = useState('24h')
  const [refreshInterval, setRefreshInterval] = useState(30000) // 30 seconds

  // WebSocket connection status
  useWebSocketConnection()

  // Subscribe to system health updates
  useSystemHealthUpdates((healthUpdate) => {
    // Health updates handled in real-time
    console.log('System health update:', healthUpdate)
  })

  // Queries with auto-refresh
  const { data: performanceData } = useQuery({
    queryKey: ['executionPerformance', timeRange],
    queryFn: executionApi.getExecutionPerformance,
    refetchInterval: refreshInterval,
  })

  const { data: statisticsData } = useQuery({
    queryKey: ['executionStatistics', timeRange],
    queryFn: executionApi.getExecutionStatistics,
    refetchInterval: refreshInterval,
  })

  const { data: flowsData } = useQuery({
    queryKey: ['flows'],
    queryFn: flowApi.getAllFlows,
  })

  const { data: adaptersData } = useQuery({
    queryKey: ['adapterStatistics'],
    queryFn: interfaceApi.getAllInterfaces,
    refetchInterval: refreshInterval,
  })

  // Event handlers
  const handleTimeRangeChange = (range: string) => {
    setTimeRange(range)
  }

  const handleRefreshIntervalChange = (interval: string) => {
    setRefreshInterval(parseInt(interval))
  }

  const exportPerformanceReport = () => {
    const report = {
      generatedAt: new Date().toISOString(),
      timeRange,
      performance: performanceData,
      statistics: statisticsData,
      flows: flowsData,
      adapters: adaptersData,
    }

    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `performance-report-${new Date().toISOString().split('T')[0]}.json`
    link.click()
    URL.revokeObjectURL(url)
  }

  // Helper functions
  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
    const minutes = Math.floor(ms / 60000)
    const seconds = Math.floor((ms % 60000) / 1000)
    return `${minutes}m ${seconds}s`
  }

  const formatUptime = (ms: number) => {
    const days = Math.floor(ms / (24 * 60 * 60 * 1000))
    const hours = Math.floor((ms % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000))
    return `${days}d ${hours}h`
  }

  const getHealthColor = (status: string) => {
    switch (status) {
      case 'healthy':
        return 'text-success'
      case 'warning':
        return 'text-warning'
      case 'critical':
        return 'text-destructive'
      default:
        return 'text-muted-foreground'
    }
  }

  const getTrendIcon = (trend: string) => {
    switch (trend) {
      case 'up':
        return <TrendingUp className="h-4 w-4 text-success" />
      case 'down':
        return <TrendingUp className="h-4 w-4 text-destructive rotate-180" />
      default:
        return <TrendingUp className="h-4 w-4 text-muted-foreground rotate-90" />
    }
  }

  // Mock data for demonstration (would come from API)
  const mockPerformanceData: PerformanceMetrics = {
    totalExecutions: 2456,
    successfulExecutions: 2389,
    failedExecutions: 67,
    averageExecutionTime: 4500,
    successRate: 97.3,
    executionsToday: 156,
    executionsThisWeek: 1024,
    executionsThisMonth: 4567,
    peakExecutionHour: 14,
    slowestExecution: 45000,
    fastestExecution: 234,
    mostActiveFlow: 'FNB Payment Processing',
    errorRate: 2.7,
    retryRate: 1.2,
  }

  const mockSystemHealth: SystemHealth = {
    cpuUsage: 23,
    memoryUsage: 68,
    diskUsage: 45,
    networkLatency: 12,
    activeConnections: 8,
    queueSize: 3,
    uptime: 7 * 24 * 60 * 60 * 1000, // 7 days
    healthStatus: 'healthy',
  }

  const mockFlowPerformance: FlowPerformance[] = [
    {
      flowId: '1',
      flowName: 'FNB Payment Processing',
      totalExecutions: 856,
      successfulExecutions: 842,
      failedExecutions: 14,
      averageExecutionTime: 3200,
      successRate: 98.4,
      lastExecution: new Date().toISOString(),
      trend: 'up',
    },
    {
      flowId: '2',
      flowName: 'Standard Bank File Transfer',
      totalExecutions: 623,
      successfulExecutions: 601,
      failedExecutions: 22,
      averageExecutionTime: 5600,
      successRate: 96.5,
      lastExecution: new Date().toISOString(),
      trend: 'stable',
    },
    {
      flowId: '3',
      flowName: 'ABSA Daily Reconciliation',
      totalExecutions: 234,
      successfulExecutions: 230,
      failedExecutions: 4,
      averageExecutionTime: 7800,
      successRate: 98.3,
      lastExecution: new Date().toISOString(),
      trend: 'down',
    },
  ]

  const mockAdapterPerformance: AdapterPerformance[] = [
    {
      adapterId: '1',
      adapterName: 'FNB SFTP Production',
      adapterType: 'SFTP',
      totalExecutions: 456,
      successfulExecutions: 448,
      failedExecutions: 8,
      averageExecutionTime: 2100,
      successRate: 98.2,
      lastExecution: new Date().toISOString(),
      connectionStatus: 'online',
    },
    {
      adapterId: '2',
      adapterName: 'Standard Bank Email Gateway',
      adapterType: 'EMAIL',
      totalExecutions: 234,
      successfulExecutions: 229,
      failedExecutions: 5,
      averageExecutionTime: 890,
      successRate: 97.9,
      lastExecution: new Date().toISOString(),
      connectionStatus: 'online',
    },
    {
      adapterId: '3',
      adapterName: 'Local File Processor',
      adapterType: 'FILE',
      totalExecutions: 567,
      successfulExecutions: 556,
      failedExecutions: 11,
      averageExecutionTime: 1450,
      successRate: 98.1,
      lastExecution: new Date().toISOString(),
      connectionStatus: 'degraded',
    },
  ]

  if (!isAdmin()) {
    return (
      <div className="content-spacing">
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <BarChart3 className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">Access Denied</h3>
              <p className="text-muted-foreground">Administrator privileges required to view performance dashboard</p>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">Performance Dashboard</h1>
          <div className="flex items-center space-x-4">
            <p className="text-muted-foreground">Real-time system performance metrics and analytics</p>
            <WebSocketStatus size="sm" />
          </div>
        </div>
        <div className="flex items-center space-x-2">
          <Select value={timeRange} onValueChange={handleTimeRangeChange}>
            <SelectTrigger className="w-auto">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="1h">Last Hour</SelectItem>
              <SelectItem value="24h">Last 24 Hours</SelectItem>
              <SelectItem value="7d">Last 7 Days</SelectItem>
              <SelectItem value="30d">Last 30 Days</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={exportPerformanceReport}>
            <Download className="h-4 w-4 mr-2" />
            Export Report
          </Button>
        </div>
      </div>

      {/* Key Performance Indicators */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Activity className="h-8 w-8 text-primary" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Executions</p>
                <p className="text-2xl font-bold text-foreground">
                  {mockPerformanceData.totalExecutions.toLocaleString()}
                </p>
                <p className="text-xs text-muted-foreground mt-1">
                  {mockPerformanceData.executionsToday} today
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Target className="h-8 w-8 text-success" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Success Rate</p>
                <p className="text-2xl font-bold text-foreground">
                  {mockPerformanceData.successRate}%
                </p>
                <div className="flex items-center mt-1">
                  <TrendingUp className="h-3 w-3 text-success mr-1" />
                  <p className="text-xs text-success">+2.1% vs last week</p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Zap className="h-8 w-8 text-warning" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Avg Execution Time</p>
                <p className="text-2xl font-bold text-foreground">
                  {formatDuration(mockPerformanceData.averageExecutionTime)}
                </p>
                <div className="flex items-center mt-1">
                  <TrendingUp className="h-3 w-3 text-success mr-1 rotate-180" />
                  <p className="text-xs text-success">-340ms vs last week</p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <AlertTriangle className="h-8 w-8 text-destructive" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Error Rate</p>
                <p className="text-2xl font-bold text-foreground">
                  {mockPerformanceData.errorRate}%
                </p>
                <div className="flex items-center mt-1">
                  <TrendingUp className="h-3 w-3 text-success mr-1 rotate-180" />
                  <p className="text-xs text-success">-0.8% vs last week</p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* System Health Overview */}
      <Card className="app-card border mb-8">
        <CardHeader>
          <CardTitle className="flex items-center space-x-2">
            <Gauge className="h-5 w-5" />
            <span>System Health</span>
            <span className={`text-sm ${getHealthColor(mockSystemHealth.healthStatus)}`}>
              ({mockSystemHealth.healthStatus.toUpperCase()})
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">CPU Usage</span>
                <span className="text-sm font-medium text-foreground">{mockSystemHealth.cpuUsage}%</span>
              </div>
              <div className="w-full bg-secondary rounded-full h-2">
                <div 
                  className="bg-primary h-2 rounded-full transition-all duration-300" 
                  style={{ width: `${mockSystemHealth.cpuUsage}%` }}
                />
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Memory Usage</span>
                <span className="text-sm font-medium text-foreground">{mockSystemHealth.memoryUsage}%</span>
              </div>
              <div className="w-full bg-secondary rounded-full h-2">
                <div 
                  className="bg-warning h-2 rounded-full transition-all duration-300" 
                  style={{ width: `${mockSystemHealth.memoryUsage}%` }}
                />
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Disk Usage</span>
                <span className="text-sm font-medium text-foreground">{mockSystemHealth.diskUsage}%</span>
              </div>
              <div className="w-full bg-secondary rounded-full h-2">
                <div 
                  className="bg-info h-2 rounded-full transition-all duration-300" 
                  style={{ width: `${mockSystemHealth.diskUsage}%` }}
                />
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Network Latency</span>
                <span className="text-sm font-medium text-foreground">{mockSystemHealth.networkLatency}ms</span>
              </div>
              <div className="w-full bg-secondary rounded-full h-2">
                <div className="bg-success h-2 rounded-full transition-all duration-300" style={{ width: '15%' }} />
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-6">
            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Active Connections</span>
                <span className="text-lg font-semibold text-foreground">{mockSystemHealth.activeConnections}</span>
              </div>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Queue Size</span>
                <span className="text-lg font-semibold text-foreground">{mockSystemHealth.queueSize}</span>
              </div>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">System Uptime</span>
                <span className="text-lg font-semibold text-foreground">{formatUptime(mockSystemHealth.uptime)}</span>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Performance Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Flow Performance */}
        <Card className="app-card border">
          <CardHeader>
            <CardTitle className="flex items-center space-x-2">
              <BarChart3 className="h-5 w-5" />
              <span>Flow Performance</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {mockFlowPerformance.map((flow) => (
                <div key={flow.flowId} className="border rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center space-x-2">
                      {getTrendIcon(flow.trend)}
                      <h4 className="font-medium text-foreground">{flow.flowName}</h4>
                    </div>
                    <span className="text-sm font-medium text-success">
                      {flow.successRate}% success
                    </span>
                  </div>

                  <div className="grid grid-cols-3 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">Executions:</span>
                      <span className="ml-1 text-foreground font-medium">{flow.totalExecutions}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Avg Time:</span>
                      <span className="ml-1 text-foreground font-medium">{formatDuration(flow.averageExecutionTime)}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Failed:</span>
                      <span className="ml-1 text-destructive font-medium">{flow.failedExecutions}</span>
                    </div>
                  </div>

                  <div className="mt-3">
                    <div className="w-full bg-secondary rounded-full h-2">
                      <div 
                        className="bg-success h-2 rounded-full transition-all duration-300" 
                        style={{ width: `${flow.successRate}%` }}
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Adapter Performance */}
        <Card className="app-card border">
          <CardHeader>
            <CardTitle className="flex items-center space-x-2">
              <Activity className="h-5 w-5" />
              <span>Adapter Performance</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {mockAdapterPerformance.map((adapter) => (
                <div key={adapter.adapterId} className="border rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center space-x-2">
                      <div className={`w-3 h-3 rounded-full ${
                        adapter.connectionStatus === 'online' ? 'bg-success animate-glow' :
                        adapter.connectionStatus === 'degraded' ? 'bg-warning' : 'bg-destructive'
                      }`} />
                      <h4 className="font-medium text-foreground">{adapter.adapterName}</h4>
                      <span className="text-xs px-2 py-1 rounded bg-secondary text-muted-foreground">
                        {adapter.adapterType}
                      </span>
                    </div>
                    <span className="text-sm font-medium text-success">
                      {adapter.successRate}% success
                    </span>
                  </div>

                  <div className="grid grid-cols-3 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">Executions:</span>
                      <span className="ml-1 text-foreground font-medium">{adapter.totalExecutions}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Avg Time:</span>
                      <span className="ml-1 text-foreground font-medium">{formatDuration(adapter.averageExecutionTime)}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Failed:</span>
                      <span className="ml-1 text-destructive font-medium">{adapter.failedExecutions}</span>
                    </div>
                  </div>

                  <div className="mt-3">
                    <div className="w-full bg-secondary rounded-full h-2">
                      <div 
                        className="bg-success h-2 rounded-full transition-all duration-300" 
                        style={{ width: `${adapter.successRate}%` }}
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Performance Insights */}
      <Card className="app-card border">
        <CardHeader>
          <CardTitle className="flex items-center space-x-2">
            <TrendingUp className="h-5 w-5" />
            <span>Performance Insights</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <div className="bg-secondary rounded-lg p-4">
              <h3 className="font-medium text-foreground mb-2">Peak Activity</h3>
              <p className="text-sm text-muted-foreground">
                Most executions occur at <span className="text-foreground font-medium">{mockPerformanceData.peakExecutionHour}:00</span>
              </p>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <h3 className="font-medium text-foreground mb-2">Performance Range</h3>
              <p className="text-sm text-muted-foreground">
                Fastest: <span className="text-success font-medium">{formatDuration(mockPerformanceData.fastestExecution)}</span><br />
                Slowest: <span className="text-destructive font-medium">{formatDuration(mockPerformanceData.slowestExecution)}</span>
              </p>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <h3 className="font-medium text-foreground mb-2">Most Active Flow</h3>
              <p className="text-sm text-muted-foreground">
                <span className="text-foreground font-medium">{mockPerformanceData.mostActiveFlow}</span><br />
                Leading with highest execution count
              </p>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <h3 className="font-medium text-foreground mb-2">Retry Rate</h3>
              <p className="text-sm text-muted-foreground">
                <span className="text-foreground font-medium">{mockPerformanceData.retryRate}%</span> of executions require retry
              </p>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <h3 className="font-medium text-foreground mb-2">Weekly Trend</h3>
              <p className="text-sm text-muted-foreground">
                <span className="text-foreground font-medium">{mockPerformanceData.executionsThisWeek}</span> executions this week<br />
                <span className="text-success">+12%</span> vs last week
              </p>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <h3 className="font-medium text-foreground mb-2">Monthly Total</h3>
              <p className="text-sm text-muted-foreground">
                <span className="text-foreground font-medium">{mockPerformanceData.executionsThisMonth.toLocaleString()}</span> executions this month<br />
                On track for <span className="text-success">record month</span>
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Auto-refresh indicator */}
      <div className="fixed bottom-4 right-4">
        <div className="bg-background border rounded-lg px-3 py-2 shadow-lg">
          <div className="flex items-center space-x-2 text-sm">
            <RefreshCw className="h-4 w-4 text-muted-foreground" />
            <span className="text-muted-foreground">Auto-refresh: {refreshInterval / 1000}s</span>
            <Select value={refreshInterval.toString()} onValueChange={handleRefreshIntervalChange}>
              <SelectTrigger className="w-auto h-auto border-0 p-0 bg-transparent">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="10000">10s</SelectItem>
                <SelectItem value="30000">30s</SelectItem>
                <SelectItem value="60000">1m</SelectItem>
                <SelectItem value="300000">5m</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>
    </div>
  )
}

export default PerformanceDashboard