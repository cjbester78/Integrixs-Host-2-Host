export interface BankConfig {
  id: string
  name: string
  enabled: boolean
  host: string
  port: number
  username: string
  sshKeyPath: string
  uploadConfig: OperationConfig
  downloadConfigs: OperationConfig[]
}

export interface OperationConfig {
  type: 'upload' | 'download'
  name: string
  localDir: string
  remoteDir: string
  archiveDir?: string
  filePattern?: string
  enabled: boolean
}

export interface OperationResult {
  id: string
  bankName: string
  operationType: string
  startTime: string
  endTime?: string
  status: 'pending' | 'running' | 'success' | 'failed'
  filesProcessed: number
  errorMessage?: string
  duration?: number
}

export interface SystemStatus {
  status: 'healthy' | 'warning' | 'error'
  uptime: number
  lastOperationTime?: string
  activeOperations: number
  totalOperationsToday: number
  successRate: number
}

export interface LogEntry {
  timestamp: string
  level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'
  logger: string
  message: string
  operationId?: string
  bankName?: string
}

export interface FileTransferStats {
  date: string
  bank: string
  uploaded: number
  downloaded: number
  errors: number
}