import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, Filter, Search, RefreshCw, AlertTriangle, Info, CheckCircle } from 'lucide-react'
import { interfaceApi } from '@/lib/api'

interface LogEntry {
  id: string
  timestamp: string
  level: 'ERROR' | 'WARN' | 'INFO' | 'DEBUG'
  logger: string
  message: string
  operationId?: string
  bankName?: string
  details?: string
}

interface LogStats {
  errors: number
  warnings: number
  info: number
  total: number
}

const Logs: React.FC = () => {
  const [filters, setFilters] = useState({
    level: 'ALL',
    bank: 'ALL',
    operation: 'ALL',
    timeRange: 'LAST_HOUR'
  })
  const [searchTerm, setSearchTerm] = useState('')

  // Fetch adapters to populate bank dropdown dynamically
  const { data: adaptersResponse } = useQuery({
    queryKey: ['interfaces'],
    queryFn: interfaceApi.getAllInterfaces,
  })

  // Fetch logs from API
  const { data: logsResponse, isLoading, refetch } = useQuery({
    queryKey: ['system-logs', filters, searchTerm],
    queryFn: async () => {
      const params = new URLSearchParams()
      params.append('limit', '100')
      if (filters.bank !== 'ALL') params.append('bankName', filters.bank)
      if (filters.level !== 'ALL') params.append('level', filters.level)
      if (searchTerm) params.append('search', searchTerm)
      
      const response = await fetch(`/api/system/logs/recent?${params}`)
      return response.json()
    },
    refetchInterval: 30000, // Auto-refresh every 30 seconds
  })

  // Fetch log statistics
  const { data: statsResponse } = useQuery({
    queryKey: ['system-stats', filters],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (filters.bank !== 'ALL') params.append('bankName', filters.bank)
      params.append('hours', '24')
      
      const response = await fetch(`/api/system/statistics?${params}`)
      return response.json()
    },
    refetchInterval: 60000, // Refresh stats every minute
  })

  const adapters = adaptersResponse?.data || []
  const logs: LogEntry[] = logsResponse || []
  const stats: LogStats = statsResponse || { errors: 0, warnings: 0, info: 0, total: 0 }

  // Get unique bank names for dropdown
  const uniqueBanks = [...new Set(adapters.map((adapter: any) => adapter.bank))].sort() as string[]

  const handleFilterChange = (key: string, value: string) => {
    setFilters(prev => ({ ...prev, [key]: value }))
  }

  const handleExport = async () => {
    try {
      const params = new URLSearchParams()
      if (filters.bank !== 'ALL') params.append('bankName', filters.bank)
      if (filters.level !== 'ALL') params.append('level', filters.level)
      params.append('format', 'csv')
      
      const response = await fetch(`/api/system/logs/export?${params}`)
      const blob = await response.blob()
      
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `system-logs-${new Date().toISOString().split('T')[0]}.csv`
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
    } catch (error) {
      console.error('Export failed:', error)
    }
  }

  const getLogIcon = (level: string) => {
    switch (level) {
      case 'ERROR': return <AlertTriangle className="h-4 w-4 text-destructive mt-0.5" />
      case 'WARN': return <AlertTriangle className="h-4 w-4 text-warning mt-0.5" />
      case 'INFO': return <Info className="h-4 w-4 text-info mt-0.5" />
      case 'DEBUG': return <CheckCircle className="h-4 w-4 text-success mt-0.5" />
      default: return <Info className="h-4 w-4 text-info mt-0.5" />
    }
  }

  const getLogStyle = (level: string) => {
    switch (level) {
      case 'ERROR': return 'bg-destructive/10 border border-destructive/20'
      case 'WARN': return 'bg-warning/10 border border-warning/20'
      case 'INFO': return 'bg-secondary'
      case 'DEBUG': return 'bg-secondary'
      default: return 'bg-secondary'
    }
  }

  const getBadgeStyle = (level: string) => {
    switch (level) {
      case 'ERROR': return 'bg-destructive/20 text-destructive'
      case 'WARN': return 'bg-warning/20 text-warning'
      case 'INFO': return 'bg-info/20 text-info'
      case 'DEBUG': return 'bg-success/20 text-success'
      default: return 'bg-info/20 text-info'
    }
  }

  return (
    <div className="content-spacing">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-foreground mb-2">System Logs</h1>
        <p className="text-muted-foreground">View and analyze application logs, errors, and operational events</p>
      </div>

      {/* Log Controls */}
      <div className="app-card rounded-lg p-6 border">
        <h2 className="text-xl font-semibold text-foreground mb-4">Log Filters</h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Log Level</label>
            <select 
              value={filters.level}
              onChange={(e) => handleFilterChange('level', e.target.value)}
              className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
            >
              <option value="ALL">All Levels</option>
              <option value="ERROR">ERROR</option>
              <option value="WARN">WARN</option>
              <option value="INFO">INFO</option>
              <option value="DEBUG">DEBUG</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Bank</label>
            <select 
              value={filters.bank}
              onChange={(e) => handleFilterChange('bank', e.target.value)}
              className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
            >
              <option value="ALL">All Banks</option>
              {uniqueBanks.map((bank: string) => (
                <option key={bank} value={bank}>{bank}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Operation</label>
            <select 
              value={filters.operation}
              onChange={(e) => handleFilterChange('operation', e.target.value)}
              className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
            >
              <option value="ALL">All Operations</option>
              <option value="UPLOAD">Upload</option>
              <option value="DOWNLOAD">Download</option>
              <option value="CONNECTION">Connection</option>
              <option value="CONFIGURATION">Configuration</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Time Range</label>
            <select 
              value={filters.timeRange}
              onChange={(e) => handleFilterChange('timeRange', e.target.value)}
              className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
            >
              <option value="LAST_HOUR">Last Hour</option>
              <option value="LAST_6_HOURS">Last 6 Hours</option>
              <option value="LAST_24_HOURS">Last 24 Hours</option>
              <option value="LAST_WEEK">Last Week</option>
            </select>
          </div>
        </div>

        <div className="mt-4 flex space-x-2">
          <button 
            onClick={() => refetch()}
            className="btn-primary rounded px-4 py-2 flex items-center space-x-2"
          >
            <Filter className="h-4 w-4" />
            <span>Apply Filters</span>
          </button>
          <button 
            onClick={() => refetch()}
            className="btn-secondary rounded px-4 py-2 flex items-center space-x-2"
          >
            <RefreshCw className="h-4 w-4" />
            <span>Refresh</span>
          </button>
          <button 
            onClick={handleExport}
            className="btn-secondary rounded px-4 py-2 flex items-center space-x-2"
          >
            <Download className="h-4 w-4" />
            <span>Export</span>
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="app-card rounded-lg p-4 border">
        <div className="flex space-x-2">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input 
              type="text" 
              placeholder="Search logs..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-3 py-2 bg-input border border-border rounded-md text-foreground"
            />
          </div>
          <button 
            onClick={() => refetch()}
            className="btn-primary rounded px-4 py-2"
          >
            Search
          </button>
        </div>
      </div>

      {/* Log Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="app-card rounded-lg p-4 border">
          <div className="flex items-center space-x-3">
            <AlertTriangle className="h-6 w-6 text-destructive" />
            <div>
              <p className="text-lg font-semibold text-foreground">{stats.errors}</p>
              <p className="text-sm text-muted-foreground">Errors</p>
            </div>
          </div>
        </div>

        <div className="app-card rounded-lg p-4 border">
          <div className="flex items-center space-x-3">
            <AlertTriangle className="h-6 w-6 text-warning" />
            <div>
              <p className="text-lg font-semibold text-foreground">{stats.warnings}</p>
              <p className="text-sm text-muted-foreground">Warnings</p>
            </div>
          </div>
        </div>

        <div className="app-card rounded-lg p-4 border">
          <div className="flex items-center space-x-3">
            <Info className="h-6 w-6 text-info" />
            <div>
              <p className="text-lg font-semibold text-foreground">{stats.info}</p>
              <p className="text-sm text-muted-foreground">Info</p>
            </div>
          </div>
        </div>

        <div className="app-card rounded-lg p-4 border">
          <div className="flex items-center space-x-3">
            <CheckCircle className="h-6 w-6 text-success" />
            <div>
              <p className="text-lg font-semibold text-foreground">{stats.total}</p>
              <p className="text-sm text-muted-foreground">Total Entries</p>
            </div>
          </div>
        </div>
      </div>

      {/* Log Entries */}
      <div className="app-card rounded-lg border">
        <div className="p-4 border-b border-border">
          <h2 className="text-lg font-semibold text-foreground">Log Entries</h2>
        </div>
        
        {isLoading ? (
          <div className="p-8 text-center">
            <div className="inline-flex items-center space-x-3">
              <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin" />
              <span className="text-muted-foreground">Loading logs...</span>
            </div>
          </div>
        ) : logs.length === 0 ? (
          <div className="p-8 text-center">
            <Info className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
            <h3 className="text-lg font-semibold text-foreground mb-2">No Logs Found</h3>
            <p className="text-muted-foreground">No log entries match the current filters</p>
          </div>
        ) : (
          <div className="max-h-96 overflow-y-auto">
            <div className="space-y-1 p-4">
              {logs.map((log, index) => (
                <div key={log.id || index} className={`flex items-start space-x-3 p-3 rounded ${getLogStyle(log.level)}`}>
                  {getLogIcon(log.level)}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center space-x-2 mb-1">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${getBadgeStyle(log.level)}`}>
                        {log.level}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {new Date(log.timestamp).toLocaleTimeString()}
                      </span>
                      <span className="text-xs text-muted-foreground">{log.logger}</span>
                      {log.bankName && (
                        <span className="text-xs px-2 py-0.5 rounded bg-primary/20 text-primary">
                          {log.bankName}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-foreground font-mono">{log.message}</p>
                    {log.details && (
                      <p className="text-xs text-muted-foreground mt-1">{log.details}</p>
                    )}
                    {log.operationId && (
                      <p className="text-xs text-muted-foreground mt-1">Operation ID: {log.operationId}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="p-4 border-t border-border">
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">
              Showing {logs.length} of {stats.total} entries
            </span>
            <div className="flex space-x-2">
              <button className="btn-secondary rounded px-3 py-1 text-sm">Previous</button>
              <button className="btn-primary rounded px-3 py-1 text-sm">Next</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Logs