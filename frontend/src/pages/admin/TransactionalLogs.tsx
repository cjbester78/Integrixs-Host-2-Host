import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FileText, Search, Download, RefreshCw, Info, AlertTriangle, XCircle, Bug, Activity, Database, Shield, Settings, User } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { transactionLogsApi } from '@/lib/api'
import { cn } from '@/lib/utils'

interface TransactionLog {
  id: string
  timestamp: string
  level: 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL'
  category: string
  component: string
  source: string
  message: string
  username?: string
  ipAddress?: string
  userAgent?: string
  sessionId?: string
  correlationId?: string
  adapterId?: string
  executionId?: string
  fileName?: string
  details?: string
  executionTimeMs?: number
  createdAt: string
}

const logLevelIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  TRACE: Bug,
  DEBUG: Info,
  INFO: Info,
  WARN: AlertTriangle,
  ERROR: XCircle,
  FATAL: XCircle,
}

const logLevelColors: Record<string, string> = {
  TRACE: 'text-slate-500',
  DEBUG: 'text-blue-500',
  INFO: 'text-green-500',
  WARN: 'text-yellow-500',
  ERROR: 'text-red-500',
  FATAL: 'text-red-700',
}

const categoryIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  AUTHENTICATION: Shield,
  FILE_PROCESSING: FileText,
  ADAPTER_EXECUTION: Activity,
  FLOW_EXECUTION: Activity,
  SYSTEM_OPERATION: Settings,
  USER_MANAGEMENT: User,
  CONFIGURATION_CHANGE: Settings,
  SECURITY: Shield,
}

const categoryColors: Record<string, string> = {
  AUTHENTICATION: 'text-red-500',
  FILE_PROCESSING: 'text-blue-500',
  ADAPTER_EXECUTION: 'text-green-500',
  FLOW_EXECUTION: 'text-purple-500',
  SYSTEM_OPERATION: 'text-orange-500',
  USER_MANAGEMENT: 'text-indigo-500',
  CONFIGURATION_CHANGE: 'text-yellow-600',
  SECURITY: 'text-red-600',
}

const TransactionalLogs: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL')
  const [selectedLevel, setSelectedLevel] = useState<string>('ALL')
  const [pageSize, setPageSize] = useState(50)
  const [expandedLog, setExpandedLog] = useState<string | null>(null)

  // Fetch transaction logs with filters
  const { data: logsResponse, isLoading, refetch } = useQuery({
    queryKey: ['transaction-logs', selectedCategory, selectedLevel, searchTerm, pageSize],
    queryFn: () => transactionLogsApi.getRecentLogs({
      level: selectedLevel !== 'ALL' ? selectedLevel : undefined,
      category: selectedCategory !== 'ALL' ? selectedCategory : undefined,
      search: searchTerm || undefined,
      limit: pageSize
    }),
    refetchInterval: 30000, // Auto-refresh every 30 seconds
  })

  const logs: TransactionLog[] = logsResponse || []

  const handleExport = async () => {
    try {
      await transactionLogsApi.exportLogs({
        level: selectedLevel !== 'ALL' ? selectedLevel : undefined,
        category: selectedCategory !== 'ALL' ? selectedCategory : undefined,
        limit: 1000,
        format: 'csv'
      })
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
          level === 'FATAL' && 'bg-red-100 text-red-700',
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
          {category.replace(/_/g, ' ')}
        </span>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <span className="text-muted-foreground">Loading transaction logs...</span>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-xl font-bold text-foreground mb-2">Transactional Logs</h3>
          <p className="text-muted-foreground">Monitor business transactions, authentication, and file processing events</p>
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
                  placeholder="Search logs by message, username, or correlation ID..."
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
                  <SelectItem value="AUTHENTICATION">Authentication</SelectItem>
                  <SelectItem value="FILE_PROCESSING">File Processing</SelectItem>
                  <SelectItem value="ADAPTER_EXECUTION">Adapter Execution</SelectItem>
                  <SelectItem value="FLOW_EXECUTION">Flow Execution</SelectItem>
                  <SelectItem value="SYSTEM_OPERATION">System Operation</SelectItem>
                  <SelectItem value="USER_MANAGEMENT">User Management</SelectItem>
                  <SelectItem value="CONFIGURATION_CHANGE">Configuration Change</SelectItem>
                  <SelectItem value="SECURITY">Security</SelectItem>
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
          Showing {logs.length} transaction logs
          {selectedCategory !== 'ALL' && ` in ${selectedCategory} category`}
          {selectedLevel !== 'ALL' && ` at ${selectedLevel} level`}
        </span>
      </div>

      {/* Transaction Logs List */}
      {logs.length > 0 ? (
        <div className="space-y-2">
          {logs.map((log: TransactionLog) => (
            <Card key={log.id} className="app-card border hover:border-primary/30 transition-all duration-200">
              <CardContent className="p-4">
                <div className="flex items-start space-x-4">
                  <div className="flex flex-col items-center space-y-2 min-w-0">
                    <div className="text-xs font-mono text-muted-foreground">
                      {formatTimestamp(log.timestamp)}
                    </div>
                    {renderLogLevel(log.level)}
                  </div>
                  
                  <div className="flex-1 min-w-0 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2 min-w-0">
                        {renderLogCategory(log.category)}
                        <span className="text-xs font-mono text-muted-foreground truncate">
                          {log.component}
                        </span>
                        <span className="text-xs px-2 py-1 bg-muted rounded">
                          {log.source}
                        </span>
                      </div>
                      {(log.details || log.correlationId || log.adapterId || log.executionId || log.fileName) && (
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
                      {log.message}
                    </div>

                    {/* Context Information */}
                    <div className="flex flex-wrap gap-2 text-xs">
                      {log.username && (
                        <span className="px-2 py-1 bg-blue-100 text-blue-700 rounded">
                          User: {log.username}
                        </span>
                      )}
                      {log.correlationId && (
                        <span className="px-2 py-1 bg-primary/10 text-primary rounded">
                          ID: {log.correlationId}
                        </span>
                      )}
                      {log.fileName && (
                        <span className="px-2 py-1 bg-green-100 text-green-700 rounded">
                          File: {log.fileName}
                        </span>
                      )}
                      {log.ipAddress && (
                        <span className="px-2 py-1 bg-orange-100 text-orange-700 rounded">
                          IP: {log.ipAddress}
                        </span>
                      )}
                      {log.executionTimeMs && (
                        <span className="px-2 py-1 bg-purple-100 text-purple-700 rounded">
                          {log.executionTimeMs}ms
                        </span>
                      )}
                    </div>

                    {/* Expanded Details */}
                    {expandedLog === log.id && (
                      <div className="mt-4 space-y-3 border-t border-border pt-3">
                        {log.details && (
                          <div>
                            <span className="text-sm font-medium text-foreground">Additional Details:</span>
                            <pre className="mt-1 font-mono text-xs bg-secondary/50 p-3 rounded overflow-x-auto max-h-48">
                              {log.details}
                            </pre>
                          </div>
                        )}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                          {log.sessionId && (
                            <div>
                              <span className="text-muted-foreground">Session ID:</span>
                              <span className="ml-2 font-mono text-foreground">{log.sessionId}</span>
                            </div>
                          )}
                          {log.adapterId && (
                            <div>
                              <span className="text-muted-foreground">Adapter ID:</span>
                              <span className="ml-2 font-mono text-foreground">{log.adapterId}</span>
                            </div>
                          )}
                          {log.executionId && (
                            <div>
                              <span className="text-muted-foreground">Execution ID:</span>
                              <span className="ml-2 font-mono text-foreground">{log.executionId}</span>
                            </div>
                          )}
                          {log.userAgent && (
                            <div>
                              <span className="text-muted-foreground">User Agent:</span>
                              <span className="ml-2 text-foreground">{log.userAgent}</span>
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
              <Database className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">No Transaction Logs Found</h3>
              <p className="text-muted-foreground">
                No transaction logs match your current filter criteria
              </p>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

export default TransactionalLogs