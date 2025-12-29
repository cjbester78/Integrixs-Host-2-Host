export interface SystemHealth {
  status: 'UP' | 'DOWN' | 'HEALTHY' | 'WARNING' | 'ERROR'
  uptime: string
  javaVersion: string
  memoryUsage: string
  cpuUsage: string
  diskUsage: string
  timestamp: string
}

export interface AdapterStats {
  operationsToday: number
  operationsTrend: string
  filesProcessed: number
  filesLastHour: number
  successRate: number
  errorsToday: number
  adapters: AdapterInfo[]
}

export interface AdapterInfo {
  id: string
  name: string
  type: 'FILE' | 'SFTP' | 'EMAIL'
  status: 'ACTIVE' | 'INACTIVE' | 'ERROR'
  lastRun?: string
  filesToday: number
  configuration: Record<string, any>
}

export interface RecentExecution {
  id: string
  adapterName: string
  operation: string
  status: 'SUCCESS' | 'FAILED' | 'IN_PROGRESS' | 'RUNNING'
  message: string
  startTime: string
  endTime?: string
  duration?: number
  filesProcessed?: number
  errorCount?: number
}

export interface DashboardData {
  systemHealth: SystemHealth
  adapterStats: AdapterStats
  recentExecutions: RecentExecution[]
}