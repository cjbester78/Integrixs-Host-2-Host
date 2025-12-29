import React, { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { 
  ArrowLeft,
  Filter,
  Search,
  Download,
  RefreshCw,
  Clock,
  CheckCircle,
  AlertTriangle,
  XCircle,
  Info,
  AlertCircle,
  Eye
} from 'lucide-react'
import { executionApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

interface ExecutionLog {
  id: string
  timestamp: string
  level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'
  category: string
  message: string
  loggerName?: string
  threadName?: string
  adapterId?: string
  adapterName?: string
  flowId?: string
  flowName?: string
  executionId?: string
  exceptionClass?: string
  exceptionMessage?: string
  stackTrace?: string
  executionTimeMs?: number
  mdcData?: Record<string, string>
  requestMethod?: string
  requestUri?: string
}

const ExecutionLogsViewer: React.FC = () => {
  const { executionId } = useParams<{ executionId: string }>()
  const navigate = useNavigate()
  
  const [logFilter, setLogFilter] = useState('')
  const [logLevelFilter, setLogLevelFilter] = useState('all')
  const [autoRefresh, setAutoRefresh] = useState(true)

  // Fetch execution details
  const { data: executionResponse } = useQuery({
    queryKey: ['execution-details', executionId],
    queryFn: () => executionId ? executionApi.getExecutionById(executionId) : null,
    enabled: !!executionId,
  })

  // Fetch execution logs
  const { data: logsResponse, isLoading: logsLoading, refetch } = useQuery({
    queryKey: ['execution-logs', executionId, logFilter, logLevelFilter],
    queryFn: () => executionId ? executionApi.getExecutionLogs(executionId, { 
      filter: logFilter || undefined, 
      level: logLevelFilter !== 'all' ? logLevelFilter : undefined 
    }) : null,
    enabled: !!executionId,
    refetchInterval: autoRefresh ? 3000 : false, // Auto refresh every 3 seconds
  })

  const execution = executionResponse?.data
  const logs = logsResponse?.data || []
  const filteredLogs = logs.filter((log: ExecutionLog) => ['INFO', 'WARN', 'ERROR'].includes(log.level))

  const getLogLevelIcon = (level: string) => {
    switch (level) {
      case 'ERROR':
        return <XCircle className="h-4 w-4 text-red-500" />
      case 'WARN':
        return <AlertTriangle className="h-4 w-4 text-yellow-500" />
      case 'INFO':
        return <Info className="h-4 w-4 text-blue-500" />
      case 'DEBUG':
        return <Eye className="h-4 w-4 text-gray-500" />
      default:
        return <AlertCircle className="h-4 w-4 text-gray-500" />
    }
  }

  const getLogLevelColor = (_level: string) => {
    // All log levels use the same dark slate styling for consistency
    return 'bg-slate-950/50 text-slate-400 border-slate-800/50'
  }

  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp)
    return date.toLocaleString('en-GB', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }) + '.' + date.getMilliseconds().toString().padStart(3, '0')
  }

  const handleExportLogs = () => {
    const csvContent = filteredLogs.map((log: ExecutionLog) => 
      `"${formatTimestamp(log.timestamp)}","${log.level}","${log.category}","${log.message.replace(/"/g, '""')}"`
    ).join('\n')
    
    const header = 'Timestamp,Level,Category,Message\n'
    const blob = new Blob([header + csvContent], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)
    link.setAttribute('href', url)
    link.setAttribute('download', `execution-logs-${executionId}-${new Date().toISOString().split('T')[0]}.csv`)
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  const clearFilters = () => {
    setLogFilter('')
    setLogLevelFilter('all')
  }

  if (!executionId) {
    return (
      <div className="content-spacing">
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <AlertTriangle className="h-16 w-16 text-yellow-500 mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">Invalid Execution</h3>
              <p className="text-muted-foreground">No execution ID provided</p>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center space-x-4">
          <Button variant="outline" size="sm" onClick={() => navigate('/flow-monitoring')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Flow Monitoring
          </Button>
          <div>
            <h1 className="text-3xl font-bold text-foreground">Execution Logs</h1>
            <p className="text-muted-foreground">
              Detailed step-by-step logs for execution: {executionId?.substring(0, 8)}
            </p>
          </div>
        </div>
        <div className="flex items-center space-x-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={autoRefresh ? 'bg-primary/10 border-primary' : ''}
          >
            <RefreshCw className={`h-4 w-4 mr-2 ${autoRefresh ? 'animate-spin' : ''}`} />
            Auto Refresh
          </Button>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="h-4 w-4 mr-2" />
            Refresh
          </Button>
          <Button variant="outline" size="sm" onClick={handleExportLogs} disabled={filteredLogs.length === 0}>
            <Download className="h-4 w-4 mr-2" />
            Export CSV
          </Button>
        </div>
      </div>

      {/* Execution Summary */}
      {execution && (
        <Card className="app-card border mb-6">
          <CardHeader>
            <CardTitle className="flex items-center space-x-2">
              <Clock className="h-5 w-5" />
              <span>Execution Summary</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
              <div>
                <span className="text-muted-foreground">Flow:</span>
                <span className="ml-2 text-foreground font-medium">{execution.flowName}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Status:</span>
                <span className="ml-2">
                  <span className={`inline-flex items-center space-x-1 px-2 py-1 rounded text-xs ${
                    execution.status === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                    execution.status === 'FAILED' ? 'bg-red-100 text-red-700' :
                    execution.status === 'RUNNING' ? 'bg-blue-100 text-blue-700' :
                    'bg-gray-100 text-gray-700'
                  }`}>
                    {execution.status === 'COMPLETED' && <CheckCircle className="h-3 w-3" />}
                    {execution.status === 'FAILED' && <XCircle className="h-3 w-3" />}
                    {execution.status === 'RUNNING' && <Clock className="h-3 w-3" />}
                    <span>{execution.status}</span>
                  </span>
                </span>
              </div>
              <div>
                <span className="text-muted-foreground">Duration:</span>
                <span className="ml-2 text-foreground">{execution.duration || 'In progress'}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Started:</span>
                <span className="ml-2 text-foreground">{new Date(execution.startedAt).toLocaleString()}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Trigger:</span>
                <span className="ml-2 text-foreground">{execution.triggerType}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Files Processed:</span>
                <span className="ml-2 text-foreground">{execution.filesProcessed || 0}</span>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Filters */}
      <Card className="app-card border mb-6">
        <CardHeader>
          <CardTitle className="flex items-center space-x-2">
            <Filter className="h-5 w-5" />
            <span>Log Filters</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-muted-foreground">Search Message</label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search log messages..."
                  value={logFilter}
                  onChange={(e) => setLogFilter(e.target.value)}
                  className="pl-10"
                />
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-muted-foreground">Log Level</label>
              <Select value={logLevelFilter} onValueChange={setLogLevelFilter}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Levels</SelectItem>
                  <SelectItem value="ERROR">Error</SelectItem>
                  <SelectItem value="WARN">Warning</SelectItem>
                  <SelectItem value="INFO">Information</SelectItem>
                  <SelectItem value="DEBUG">Debug</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-end">
              <Button variant="outline" onClick={clearFilters} className="w-full">
                Clear Filters
              </Button>
            </div>
          </div>

          <div className="mt-4 text-sm text-muted-foreground">
            Showing {filteredLogs.length} log entries (INFO, WARN, ERROR only)
            {logFilter && <span> • Filtered by: "{logFilter}"</span>}
            {logLevelFilter !== 'all' && <span> • Level: {logLevelFilter}</span>}
          </div>
        </CardContent>
      </Card>

      {/* Log Entries */}
      <Card className="app-card border">
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <Eye className="h-5 w-5" />
              <span>Detailed Execution Logs</span>
            </div>
            <span className="text-sm font-normal text-muted-foreground">
              {autoRefresh && 'Auto-refreshing every 3 seconds'}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {logsLoading ? (
            <div className="flex items-center justify-center py-12">
              <div className="flex items-center space-x-3">
                <div className="w-6 h-6 border-4 border-primary border-t-transparent rounded-full animate-spin" />
                <span className="text-muted-foreground">Loading logs...</span>
              </div>
            </div>
          ) : filteredLogs.length === 0 ? (
            <div className="flex items-center justify-center py-12">
              <div className="text-center">
                <Eye className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
                <h3 className="text-lg font-semibold text-foreground mb-2">No Logs Found</h3>
                <p className="text-muted-foreground">
                  {logFilter || logLevelFilter !== 'all' 
                    ? 'No logs match your current filters'
                    : 'No detailed logs available for this execution'
                  }
                </p>
              </div>
            </div>
          ) : (
            <div className="space-y-2 max-h-[600px] overflow-y-auto">
              {filteredLogs.map((log: ExecutionLog, index: number) => (
                <div
                  key={`${log.id}-${index}`}
                  className={`flex items-start space-x-3 p-4 rounded-lg border ${getLogLevelColor(log.level)}`}
                >
                  <div className="flex-shrink-0 mt-0.5">
                    {getLogLevelIcon(log.level)}
                  </div>
                  
                  <div className="flex-1 min-w-0 space-y-2">
                    {/* Timestamp and Level Header */}
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-3 text-sm">
                        <span className="font-mono text-foreground">
                          {formatTimestamp(log.timestamp)}
                        </span>
                        <span className={`px-2 py-1 rounded text-xs font-medium ${getLogLevelColor(log.level)}`}>
                          {log.level}
                        </span>
                        <span className="text-muted-foreground">{log.category}</span>
                      </div>
                      {log.executionTimeMs && (
                        <span className="text-xs text-muted-foreground">
                          {log.executionTimeMs}ms
                        </span>
                      )}
                    </div>

                    {/* Main Message */}
                    <div className="text-sm text-foreground">
                      <p className="whitespace-pre-wrap break-words">{log.message}</p>
                    </div>

                    {/* Context Information */}
                    {(log.adapterName || log.flowName || log.loggerName || log.threadName) && (
                      <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
                        {log.adapterName && (
                          <span>Adapter: {log.adapterName}</span>
                        )}
                        {log.flowName && (
                          <span>Flow: {log.flowName}</span>
                        )}
                        {log.loggerName && (
                          <span>Logger: {log.loggerName.split('.').pop()}</span>
                        )}
                        {log.threadName && (
                          <span>Thread: {log.threadName}</span>
                        )}
                      </div>
                    )}

                    {/* Exception Details */}
                    {log.exceptionClass && (
                      <div className="mt-2 p-3 bg-red-950/30 border border-red-800/50 rounded">
                        <div className="flex items-center space-x-2 text-red-400 mb-2">
                          <AlertTriangle className="h-4 w-4" />
                          <span className="font-medium">{log.exceptionClass}</span>
                        </div>
                        {log.exceptionMessage && (
                          <p className="text-sm text-red-300 mb-2">{log.exceptionMessage}</p>
                        )}
                        {log.stackTrace && (
                          <details className="text-xs">
                            <summary className="cursor-pointer text-red-400 hover:text-red-300">
                              Show Stack Trace
                            </summary>
                            <pre className="mt-2 text-red-300 whitespace-pre-wrap overflow-x-auto">
                              {log.stackTrace}
                            </pre>
                          </details>
                        )}
                      </div>
                    )}

                    {/* MDC Data */}
                    {log.mdcData && Object.keys(log.mdcData).length > 0 && (
                      <details className="text-xs">
                        <summary className="cursor-pointer text-muted-foreground hover:text-foreground">
                          Context Data ({Object.keys(log.mdcData).length} items)
                        </summary>
                        <div className="mt-2 p-2 bg-secondary/20 rounded">
                          {Object.entries(log.mdcData).map(([key, value]) => (
                            <div key={key} className="flex">
                              <span className="font-medium text-muted-foreground w-24">{key}:</span>
                              <span className="text-foreground">{value}</span>
                            </div>
                          ))}
                        </div>
                      </details>
                    )}

                    {/* HTTP Request Details */}
                    {(log.requestMethod || log.requestUri) && (
                      <div className="text-xs text-muted-foreground">
                        {log.requestMethod && log.requestUri && (
                          <span>{log.requestMethod} {log.requestUri}</span>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

export default ExecutionLogsViewer