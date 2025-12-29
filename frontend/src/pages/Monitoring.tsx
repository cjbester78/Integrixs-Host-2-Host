import React from 'react'
import { Link } from 'react-router-dom'
import { 
  Wifi, 
  HardDrive, 
  Cpu, 
  Clock, 
  Activity, 
  BarChart3, 
  TrendingUp,
  ArrowRight,
  Eye
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { usePermissions } from '@/hooks/auth'
import { useWebSocketConnection } from '@/hooks/useWebSocket'
import WebSocketStatus from '@/components/WebSocketStatus'
import { executionApi } from '@/lib/api'

const Monitoring: React.FC = () => {
  const { isAdmin } = usePermissions()

  // WebSocket connection status
  useWebSocketConnection()

  // Real-time data queries
  const { data: runningExecutionsResponse } = useQuery({
    queryKey: ['runningExecutions'],
    queryFn: executionApi.getRunningExecutions,
    refetchInterval: 5000,
    enabled: isAdmin(),
  })

  const { data: statisticsResponse } = useQuery({
    queryKey: ['executionStatistics'],
    queryFn: executionApi.getExecutionStatistics,
    refetchInterval: 15000,
    enabled: isAdmin(),
  })

  const runningExecutions = runningExecutionsResponse?.data || []
  const statistics = statisticsResponse?.data || {}
  return (
    <div className="content-spacing">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">System Monitoring</h1>
          <div className="flex items-center space-x-4">
            <p className="text-muted-foreground">Monitor system health, performance metrics, and connection status</p>
            <WebSocketStatus size="sm" />
          </div>
        </div>
        {isAdmin() && (
          <div className="flex items-center space-x-2">
            <Button asChild variant="outline">
              <Link to="/executions">
                <Activity className="h-4 w-4 mr-2" />
                Execution Monitor
              </Link>
            </Button>
            <Button asChild>
              <Link to="/performance">
                <BarChart3 className="h-4 w-4 mr-2" />
                Performance Dashboard
              </Link>
            </Button>
          </div>
        )}
      </div>

      {/* Quick Access Cards for Admin */}
      {isAdmin() && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <Card className="app-card border hover:shadow-md transition-shadow cursor-pointer">
            <Link to="/executions">
              <CardContent className="p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-foreground mb-2">Execution Monitoring</h3>
                    <p className="text-muted-foreground text-sm">
                      Monitor flow executions, view history, and manage failures
                    </p>
                    <div className="flex items-center space-x-4 mt-3">
                      <div className="flex items-center space-x-1">
                        <span className="text-sm font-medium text-foreground">{runningExecutions.length}</span>
                        <span className="text-xs text-muted-foreground">running</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        <span className="text-sm font-medium text-foreground">{statistics.totalExecutions || 0}</span>
                        <span className="text-xs text-muted-foreground">total</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex flex-col items-center">
                    <Activity className="h-8 w-8 text-primary mb-2" />
                    <ArrowRight className="h-4 w-4 text-muted-foreground" />
                  </div>
                </div>
              </CardContent>
            </Link>
          </Card>

          <Card className="app-card border hover:shadow-md transition-shadow cursor-pointer">
            <Link to="/performance">
              <CardContent className="p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-foreground mb-2">Performance Dashboard</h3>
                    <p className="text-muted-foreground text-sm">
                      Analyze system performance, trends, and optimization opportunities
                    </p>
                    <div className="flex items-center space-x-4 mt-3">
                      <div className="flex items-center space-x-1">
                        <span className="text-sm font-medium text-success">{statistics.successRate ? Math.round(statistics.successRate) : 0}%</span>
                        <span className="text-xs text-muted-foreground">success rate</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        <TrendingUp className="h-3 w-3 text-success" />
                        <span className="text-xs text-success">trending up</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex flex-col items-center">
                    <BarChart3 className="h-8 w-8 text-info mb-2" />
                    <ArrowRight className="h-4 w-4 text-muted-foreground" />
                  </div>
                </div>
              </CardContent>
            </Link>
          </Card>

          <Card className="app-card border hover:shadow-md transition-shadow cursor-pointer">
            <Link to="/flows">
              <CardContent className="p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-foreground mb-2">Flow Management</h3>
                    <p className="text-muted-foreground text-sm">
                      Manage visual flows, monitor execution status, and configure settings
                    </p>
                    <div className="flex items-center space-x-4 mt-3">
                      <div className="flex items-center space-x-1">
                        <span className="text-sm font-medium text-foreground">{statistics.activeFlows || 0}</span>
                        <span className="text-xs text-muted-foreground">active flows</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        <Eye className="h-3 w-3 text-info" />
                        <span className="text-xs text-info">real-time</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex flex-col items-center">
                    <Activity className="h-8 w-8 text-success mb-2" />
                    <ArrowRight className="h-4 w-4 text-muted-foreground" />
                  </div>
                </div>
              </CardContent>
            </Link>
          </Card>
        </div>
      )}

      {/* System Health Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-muted-foreground">CPU Usage</p>
              <p className="text-2xl font-bold text-foreground">23%</p>
            </div>
            <Cpu className="h-8 w-8 text-primary" />
          </div>
          <div className="mt-2">
            <div className="w-full bg-secondary rounded-full h-2">
              <div className="bg-primary h-2 rounded-full" style={{ width: '23%' }}></div>
            </div>
          </div>
        </div>

        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Memory Usage</p>
              <p className="text-2xl font-bold text-foreground">68%</p>
            </div>
            <HardDrive className="h-8 w-8 text-info" />
          </div>
          <div className="mt-2">
            <div className="w-full bg-secondary rounded-full h-2">
              <div className="bg-info h-2 rounded-full" style={{ width: '68%' }}></div>
            </div>
          </div>
        </div>

        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Network</p>
              <p className="text-2xl font-bold text-foreground">Stable</p>
            </div>
            <Wifi className="h-8 w-8 text-success" />
          </div>
          <div className="mt-2">
            <span className="text-xs text-muted-foreground">Latency: 12ms</span>
          </div>
        </div>

        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Uptime</p>
              <p className="text-2xl font-bold text-foreground">7d 12h</p>
            </div>
            <Clock className="h-8 w-8 text-accent" />
          </div>
          <div className="mt-2">
            <span className="text-xs text-muted-foreground">99.98% availability</span>
          </div>
        </div>
      </div>

      {/* Connection Status */}
      <div className="app-card rounded-lg p-6 border">
        <h2 className="text-xl font-semibold text-foreground mb-4">Bank Connection Status</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="bg-secondary rounded-lg p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-medium text-foreground">First National Bank (FNB)</h3>
              <div className="flex items-center space-x-2">
                <div className="w-3 h-3 bg-success rounded-full animate-glow"></div>
                <span className="text-sm text-success">Connected</span>
              </div>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Host:</span>
                <span className="text-foreground">196.11.129.67:22</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Last Test:</span>
                <span className="text-foreground">2 minutes ago</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Response Time:</span>
                <span className="text-foreground">145ms</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Auth Method:</span>
                <span className="text-foreground">SSH Key</span>
              </div>
            </div>
          </div>

          <div className="bg-secondary rounded-lg p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-medium text-foreground">Standard Bank (Stanbic)</h3>
              <div className="flex items-center space-x-2">
                <div className="w-3 h-3 bg-success rounded-full animate-glow"></div>
                <span className="text-sm text-success">Connected</span>
              </div>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Host:</span>
                <span className="text-foreground">stanbic.sftp.host:22</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Last Test:</span>
                <span className="text-foreground">1 minute ago</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Response Time:</span>
                <span className="text-foreground">98ms</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Auth Method:</span>
                <span className="text-foreground">SSH Key</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Performance Metrics */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="app-card rounded-lg p-6 border">
          <h2 className="text-xl font-semibold text-foreground mb-4">Transfer Performance</h2>
          <div className="space-y-4">
            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Average Transfer Time</span>
                <span className="text-lg font-semibold text-foreground">45s</span>
              </div>
            </div>
            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Success Rate (24h)</span>
                <span className="text-lg font-semibold text-success">98.7%</span>
              </div>
            </div>
            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Files Processed Today</span>
                <span className="text-lg font-semibold text-foreground">156</span>
              </div>
            </div>
            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Average File Size</span>
                <span className="text-lg font-semibold text-foreground">2.3 MB</span>
              </div>
            </div>
          </div>
        </div>

        <div className="app-card rounded-lg p-6 border">
          <h2 className="text-xl font-semibold text-foreground mb-4">System Alerts</h2>
          <div className="space-y-3">
            <div className="bg-secondary rounded-lg p-3 flex items-start space-x-3">
              <div className="w-2 h-2 bg-warning rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm font-medium text-foreground">High Memory Usage</p>
                <p className="text-xs text-muted-foreground">Memory usage is at 68%. Consider restarting the application.</p>
                <p className="text-xs text-muted-foreground">5 minutes ago</p>
              </div>
            </div>
            
            <div className="bg-secondary rounded-lg p-3 flex items-start space-x-3">
              <div className="w-2 h-2 bg-info rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm font-medium text-foreground">Connection Test Passed</p>
                <p className="text-xs text-muted-foreground">All bank connections tested successfully.</p>
                <p className="text-xs text-muted-foreground">2 minutes ago</p>
              </div>
            </div>

            <div className="bg-secondary rounded-lg p-3 flex items-start space-x-3">
              <div className="w-2 h-2 bg-success rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm font-medium text-foreground">Transfer Completed</p>
                <p className="text-xs text-muted-foreground">FNB payment upload completed successfully (5 files).</p>
                <p className="text-xs text-muted-foreground">7 minutes ago</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Real-time Monitoring */}
      <div className="app-card rounded-lg p-6 border">
        <h2 className="text-xl font-semibold text-foreground mb-4">Real-time Activity</h2>
        <div className="space-y-2">
          <div className="flex items-center justify-between p-2 bg-secondary rounded">
            <div className="flex items-center space-x-3">
              <div className="w-2 h-2 bg-primary rounded-full animate-pulse"></div>
              <span className="text-sm text-foreground">Monitoring SFTP connections...</span>
            </div>
            <span className="text-xs text-muted-foreground">Active</span>
          </div>
          
          <div className="flex items-center justify-between p-2 bg-secondary rounded">
            <div className="flex items-center space-x-3">
              <div className="w-2 h-2 bg-success rounded-full"></div>
              <span className="text-sm text-foreground">File system monitoring active</span>
            </div>
            <span className="text-xs text-muted-foreground">Healthy</span>
          </div>
          
          <div className="flex items-center justify-between p-2 bg-secondary rounded">
            <div className="flex items-center space-x-3">
              <div className="w-2 h-2 bg-info rounded-full"></div>
              <span className="text-sm text-foreground">Log cleanup scheduled for 2:00 AM</span>
            </div>
            <span className="text-xs text-muted-foreground">Scheduled</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Monitoring