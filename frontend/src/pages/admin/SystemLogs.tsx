import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FileText, Search, Download, RefreshCw, Info, AlertTriangle, XCircle, Bug, Activity, Database, Shield, Settings, Clock } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { systemLogsApi } from '@/lib/api'
import { cn } from '@/lib/utils'

interface SystemLog {
  id: string
  timestamp: string
  logLevel: 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
  logCategory: 'SYSTEM' | 'ADAPTER_EXECUTION' | 'FLOW_EXECUTION' | 'AUTHENTICATION' | 'DATABASE' | 'SCHEDULER' | 'API'
  loggerName: string
  threadName: string
  message: string
  formattedMessage: string
  correlationId?: string
  userId?: string
  adapterId?: string
  adapterName?: string
  flowId?: string
  flowName?: string
  executionId?: string
  requestMethod?: string
  requestUri?: string
  remoteAddress?: string
  applicationName: string
  environment: string
  exceptionClass?: string
  exceptionMessage?: string
  stackTrace?: string
}

// LogsResponse interface removed as it's not used

const logLevelIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  TRACE: Bug,
  DEBUG: Info,
  INFO: Info,
  WARN: AlertTriangle,
  ERROR: XCircle,
}

const logLevelColors: Record<string, string> = {
  TRACE: 'text-slate-500',
  DEBUG: 'text-blue-500',
  INFO: 'text-green-500',
  WARN: 'text-yellow-500',
  ERROR: 'text-red-500',
}

const categoryIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  SYSTEM: Settings,
  ADAPTER_EXECUTION: Activity,
  FLOW_EXECUTION: Activity,
  AUTHENTICATION: Shield,
  DATABASE: Database,
  SCHEDULER: Clock,
  API: Activity,
}

const categoryColors: Record<string, string> = {
  SYSTEM: 'text-purple-500',
  ADAPTER_EXECUTION: 'text-blue-500',
  FLOW_EXECUTION: 'text-green-500',
  AUTHENTICATION: 'text-red-500',
  DATABASE: 'text-yellow-500',
  SCHEDULER: 'text-orange-500',
  API: 'text-indigo-500',
}

const SystemLogs: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL')
  const [selectedLevel, setSelectedLevel] = useState<string>('ALL')
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(50)
  const [expandedLog, setExpandedLog] = useState<string | null>(null)

  // Fetch logs with filters
  const { data: logsResponse, isLoading, refetch } = useQuery({
    queryKey: ['system-logs', selectedCategory, selectedLevel, searchTerm, currentPage, pageSize],
    queryFn: () => systemLogsApi.getRecentLogs({
      level: selectedLevel !== 'ALL' ? selectedLevel : undefined,
      search: searchTerm || undefined,
      limit: pageSize
    }),
    refetchInterval: 30000, // Auto-refresh every 30 seconds
  })

  const logs = logsResponse || []
  const totalPages = Math.ceil(logs.length / pageSize) || 0
  const totalElements = logs.length || 0

  const handleExport = async () => {
    try {
      // This would be implemented to export logs as CSV/JSON
      console.log('Export functionality would be implemented here')
    } catch (err) {
      console.error('Export failed:', err)
    }
  }

  const formatTimestamp = (timestamp: string) => {
    return new Date(timestamp).toLocaleString('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    })
  }

  const renderLogLevel = (level: string) => {
    const Icon = logLevelIcons[level] || Info
    const color = logLevelColors[level] || 'text-muted-foreground'

    return (
      <div className="flex items-center space-x-1">
        <Icon className={cn("h-4 w-4", color)} />
        <span className={cn("text-xs font-medium px-2 py-1 rounded", 
          level === 'ERROR' && 'bg-red-100 text-red-700',
          level === 'WARN' && 'bg-yellow-100 text-yellow-700',
          level === 'INFO' && 'bg-green-100 text-green-700',
          level === 'DEBUG' && 'bg-blue-100 text-blue-700',
          level === 'TRACE' && 'bg-slate-100 text-slate-700'
        )}>
          {level}
        </span>
      </div>
    )
  }

  const renderLogCategory = (category: string) => {
    const Icon = categoryIcons[category] || Settings
    const color = categoryColors[category] || 'text-muted-foreground'

    return (
      <div className="flex items-center space-x-1">
        <Icon className={cn("h-4 w-4", color)} />
        <span className="text-xs px-2 py-1 bg-secondary rounded">
          {category.replace('_', ' ')}
        </span>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <span className="text-muted-foreground">Loading logs...</span>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground mb-2">System Logs</h2>
          <p className="text-muted-foreground">Monitor application logs with category filtering</p>
        </div>
        <div className="flex items-center space-x-2">
          <Button variant="outline" onClick={() => refetch()}>
            <RefreshCw className="h-4 w-4 mr-2" />
            Refresh
          </Button>
          <Button variant="outline" onClick={handleExport}>
            <Download className="h-4 w-4 mr-2" />
            Export
          </Button>
        </div>
      </div>

      {/* Filters */}
      <Card className="app-card border">
        <CardContent className="p-4">
          <div className="flex flex-col lg:flex-row gap-4">
            <div className="flex-1">
              <div className="relative">
                <Search className="h-4 w-4 absolute left-3 top-3 text-muted-foreground" />
                <Input
                  placeholder="Search logs by message, logger name, or correlation ID..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
            </div>
            <div className="w-full lg:w-48">
              <Select value={selectedCategory} onValueChange={setSelectedCategory}>
                <SelectTrigger>
                  <SelectValue placeholder="All Categories" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Categories</SelectItem>
                  <SelectItem value="ADAPTER_EXECUTION">Adapter Execution</SelectItem>
                  <SelectItem value="FLOW_EXECUTION">Flow Execution</SelectItem>
                  <SelectItem value="AUTHENTICATION">Authentication</SelectItem>
                  <SelectItem value="DATABASE">Database</SelectItem>
                  <SelectItem value="API">API</SelectItem>
                  <SelectItem value="SCHEDULER">Scheduler</SelectItem>
                  <SelectItem value="SYSTEM">System</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="w-full lg:w-36">
              <Select value={selectedLevel} onValueChange={setSelectedLevel}>
                <SelectTrigger>
                  <SelectValue placeholder="All Levels" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Levels</SelectItem>
                  <SelectItem value="ERROR">Error</SelectItem>
                  <SelectItem value="WARN">Warning</SelectItem>
                  <SelectItem value="INFO">Info</SelectItem>
                  <SelectItem value="DEBUG">Debug</SelectItem>
                  <SelectItem value="TRACE">Trace</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="w-full lg:w-24">
              <Select value={pageSize.toString()} onValueChange={(value) => setPageSize(parseInt(value))}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                  <SelectItem value="100">100</SelectItem>
                  <SelectItem value="200">200</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Stats */}
      <div className="flex items-center justify-between text-sm text-muted-foreground">
        <span>
          Showing {logs.length} of {totalElements} logs
          {selectedCategory !== 'ALL' && ` in ${selectedCategory} category`}
          {selectedLevel !== 'ALL' && ` at ${selectedLevel} level`}
        </span>
        <span>Page {currentPage + 1} of {totalPages}</span>
      </div>

      {/* Logs List */}
      {logs.length > 0 ? (
        <div className="space-y-2">
          {logs.map((log: SystemLog) => (
            <Card key={log.id} className="app-card border hover:border-primary/30 transition-all duration-200">
              <CardContent className="p-4">
                <div className="flex items-start space-x-4">
                  <div className="flex flex-col items-center space-y-2 min-w-0">
                    <div className="text-xs font-mono text-muted-foreground">
                      {formatTimestamp(log.timestamp)}
                    </div>
                    {renderLogLevel(log.logLevel)}
                  </div>
                  
                  <div className="flex-1 min-w-0 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2 min-w-0">
                        {renderLogCategory(log.logCategory)}
                        <span className="text-xs font-mono text-muted-foreground truncate">
                          {log.loggerName}
                        </span>
                        {log.threadName && (
                          <span className="text-xs px-2 py-1 bg-muted rounded">
                            {log.threadName}
                          </span>
                        )}
                      </div>
                      {(log.stackTrace || log.correlationId || log.adapterId || log.flowId) && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setExpandedLog(expandedLog === log.id ? null : log.id)}
                        >
                          {expandedLog === log.id ? 'Less' : 'More'}
                        </Button>
                      )}
                    </div>

                    <div className="text-sm text-foreground">
                      {log.formattedMessage || log.message}
                    </div>

                    {/* Context Information */}
                    <div className="flex flex-wrap gap-2 text-xs">
                      {log.correlationId && (
                        <span className="px-2 py-1 bg-primary/10 text-primary rounded">
                          ID: {log.correlationId}
                        </span>
                      )}
                      {log.adapterName && (
                        <span className="px-2 py-1 bg-blue-100 text-blue-700 rounded">
                          Adapter: {log.adapterName}
                        </span>
                      )}
                      {log.flowName && (
                        <span className="px-2 py-1 bg-green-100 text-green-700 rounded">
                          Flow: {log.flowName}
                        </span>
                      )}
                      {log.requestMethod && log.requestUri && (
                        <span className="px-2 py-1 bg-indigo-100 text-indigo-700 rounded">
                          {log.requestMethod} {log.requestUri}
                        </span>
                      )}
                      {log.remoteAddress && (
                        <span className="px-2 py-1 bg-orange-100 text-orange-700 rounded">
                          IP: {log.remoteAddress}
                        </span>
                      )}
                    </div>

                    {/* Expanded Details */}
                    {expandedLog === log.id && (
                      <div className="mt-4 space-y-3 border-t border-border pt-3">
                        {log.exceptionClass && (
                          <div>
                            <span className="text-sm font-medium text-foreground">Exception:</span>
                            <div className="mt-1 font-mono text-sm bg-red-50 text-red-800 p-2 rounded">
                              {log.exceptionClass}: {log.exceptionMessage}
                            </div>
                          </div>
                        )}
                        {log.stackTrace && (
                          <div>
                            <span className="text-sm font-medium text-foreground">Stack Trace:</span>
                            <pre className="mt-1 font-mono text-xs bg-secondary/50 p-3 rounded overflow-x-auto max-h-48">
                              {log.stackTrace}
                            </pre>
                          </div>
                        )}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                          <div>
                            <span className="text-muted-foreground">Application:</span>
                            <span className="ml-2 text-foreground">{log.applicationName}</span>
                          </div>
                          <div>
                            <span className="text-muted-foreground">Environment:</span>
                            <span className="ml-2 text-foreground">{log.environment}</span>
                          </div>
                          {log.userId && (
                            <div>
                              <span className="text-muted-foreground">User ID:</span>
                              <span className="ml-2 font-mono text-foreground">{log.userId}</span>
                            </div>
                          )}
                          {log.executionId && (
                            <div>
                              <span className="text-muted-foreground">Execution ID:</span>
                              <span className="ml-2 font-mono text-foreground">{log.executionId}</span>
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <FileText className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">No Logs Found</h3>
              <p className="text-muted-foreground">
                No system logs match your current filter criteria
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <Button
            variant="outline"
            onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
            disabled={currentPage === 0}
          >
            Previous
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {currentPage + 1} of {totalPages}
          </span>
          <Button
            variant="outline"
            onClick={() => setCurrentPage(Math.min(totalPages - 1, currentPage + 1))}
            disabled={currentPage === totalPages - 1}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  )
}

export default SystemLogs