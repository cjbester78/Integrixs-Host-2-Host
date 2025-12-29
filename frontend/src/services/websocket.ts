import { Client, IMessage, StompConfig } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export interface FlowExecutionUpdate {
  type: 'FLOW_EXECUTION_UPDATE'
  executionId: string
  flowId: string
  flowName: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progress: number
  startedAt?: string
  completedAt?: string
  durationMs?: number
  triggerType: string
  triggeredBy: string
  correlationId: string
  retryAttempt: number
  errorMessage?: string
}

export interface FlowStepUpdate {
  type: 'FLOW_STEP_UPDATE'
  stepId: string
  executionId: string
  stepName: string
  stepType: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  stepOrder: number
  startedAt?: string
  completedAt?: string
  durationMs?: number
  progress: number
  errorMessage?: string
  outputSummary?: Record<string, any>
}

export interface FlowValidationResult {
  type: 'FLOW_VALIDATION_RESULT'
  flowId: string
  validation: {
    valid: boolean
    errors: string[]
    warnings: string[]
    errorCount: number
    warningCount: number
  }
  timestamp: number
}

export interface FlowDefinitionUpdate {
  type: 'FLOW_DEFINITION_UPDATE'
  flowId: string
  flowName: string
  updateType: 'CREATED' | 'UPDATED' | 'DELETED' | 'ENABLED' | 'DISABLED'
  updatedBy: string
  timestamp: number
}

export interface SystemHealthUpdate {
  type: 'SYSTEM_HEALTH_UPDATE'
  health: Record<string, any>
  timestamp: number
}

export type WebSocketMessage = 
  | FlowExecutionUpdate 
  | FlowStepUpdate 
  | FlowValidationResult 
  | FlowDefinitionUpdate 
  | SystemHealthUpdate

export type MessageHandler = (message: WebSocketMessage) => void

export class WebSocketService {
  private client: Client | null = null
  private subscriptions: Map<string, MessageHandler[]> = new Map()
  private isConnected = false
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectInterval = 5000
  private reconnectTimer?: ReturnType<typeof setTimeout>

  constructor(private baseUrl?: string) {
    this.baseUrl = baseUrl || this.getWebSocketUrl()
  }

  private getWebSocketUrl(): string {
    // Always use HTTP/HTTPS URL for SockJS, not ws:// or wss://
    // SockJS will handle the WebSocket protocol negotiation
    return `${window.location.protocol}//${window.location.host}/ws`
  }

  private getAuthToken(): string | null {
    return sessionStorage.getItem('h2h_token') || localStorage.getItem('h2h_token')
  }

  public connect(): Promise<void> {
    if (this.isConnected) {
      return Promise.resolve()
    }

    return new Promise((resolve, reject) => {
      const token = this.getAuthToken()
      if (!token) {
        reject(new Error('No authentication token available'))
        return
      }

      const stompConfig: StompConfig = {
        webSocketFactory: () => new SockJS(this.baseUrl!),
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        reconnectDelay: this.reconnectInterval,
        logRawCommunication: import.meta.env.DEV,
        debug: import.meta.env.DEV ? (str: string) => console.log('[STOMP Debug]', str) : () => {},
        onConnect: (frame) => {
          console.log('[WebSocket] Connected successfully', frame)
          this.isConnected = true
          this.reconnectAttempts = 0
          this.clearReconnectTimer()
          this.resubscribeAll()
          resolve()
        },
        onStompError: (frame) => {
          console.error('[WebSocket] STOMP error', frame)
          this.isConnected = false
          reject(new Error(`STOMP error: ${frame.body}`))
        },
        onWebSocketClose: (event) => {
          console.warn('[WebSocket] Connection closed', event)
          this.isConnected = false
          this.handleReconnect()
        },
        onDisconnect: (receipt) => {
          console.log('[WebSocket] Disconnected', receipt)
          this.isConnected = false
        },
        onWebSocketError: (error) => {
          console.error('[WebSocket] Connection error', error)
          this.isConnected = false
          this.handleReconnect()
        }
      }

      this.client = new Client(stompConfig)
      this.client.activate()
    })
  }

  private handleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('[WebSocket] Maximum reconnection attempts reached')
      return
    }

    this.reconnectAttempts++
    console.log(`[WebSocket] Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)

    this.reconnectTimer = setTimeout(() => {
      this.connect().catch((error) => {
        console.error('[WebSocket] Reconnection failed', error)
      })
    }, this.reconnectInterval * this.reconnectAttempts)
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = undefined
    }
  }

  private resubscribeAll(): void {
    // Resubscribe to all topics when reconnected
    const topics = Array.from(this.subscriptions.keys())
    for (const topic of topics) {
      this.subscribeToTopic(topic)
    }
  }

  private subscribeToTopic(topic: string): void {
    if (!this.client || !this.isConnected) {
      console.warn(`[WebSocket] Cannot subscribe to ${topic}: not connected`)
      return
    }

    this.client.subscribe(topic, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body) as WebSocketMessage
        // console.log(`[WebSocket] Received message on ${topic}:`, data)
        
        // Call all handlers for this topic
        const handlers = this.subscriptions.get(topic) || []
        for (const handler of handlers) {
          try {
            handler(data)
          } catch (error) {
            console.error(`[WebSocket] Handler error for topic ${topic}:`, error)
          }
        }
      } catch (error) {
        console.error(`[WebSocket] Failed to parse message on ${topic}:`, error)
      }
    })

    // console.log(`[WebSocket] Subscribed to topic: ${topic}`)
  }

  public subscribe(topic: string, handler: MessageHandler): () => void {
    // Add handler to subscription map
    if (!this.subscriptions.has(topic)) {
      this.subscriptions.set(topic, [])
    }
    this.subscriptions.get(topic)!.push(handler)

    // Subscribe to topic if connected
    if (this.isConnected) {
      this.subscribeToTopic(topic)
    }

    // console.log(`[WebSocket] Added handler for topic: ${topic}`)

    // Return unsubscribe function
    return () => {
      const handlers = this.subscriptions.get(topic)
      if (handlers) {
        const index = handlers.indexOf(handler)
        if (index > -1) {
          handlers.splice(index, 1)
          if (handlers.length === 0) {
            this.subscriptions.delete(topic)
            // Note: We don't unsubscribe from STOMP as other components might still need it
          }
        }
      }
      // console.log(`[WebSocket] Removed handler for topic: ${topic}`)
    }
  }

  // Convenience methods for common subscriptions
  public subscribeToFlowExecutions(handler: (update: FlowExecutionUpdate) => void): () => void {
    return this.subscribe('/topic/flow-executions', (message) => {
      if (message.type === 'FLOW_EXECUTION_UPDATE') {
        handler(message as FlowExecutionUpdate)
      }
    })
  }

  public subscribeToSpecificFlowExecutions(flowId: string, handler: (update: FlowExecutionUpdate) => void): () => void {
    return this.subscribe(`/topic/flow/${flowId}/executions`, (message) => {
      if (message.type === 'FLOW_EXECUTION_UPDATE') {
        handler(message as FlowExecutionUpdate)
      }
    })
  }

  public subscribeToSpecificExecution(executionId: string, handler: (update: FlowExecutionUpdate | FlowStepUpdate) => void): () => void {
    return this.subscribe(`/topic/execution/${executionId}`, (message) => {
      if (message.type === 'FLOW_EXECUTION_UPDATE' || message.type === 'FLOW_STEP_UPDATE') {
        handler(message as FlowExecutionUpdate | FlowStepUpdate)
      }
    })
  }

  public subscribeToExecutionSteps(executionId: string, handler: (update: FlowStepUpdate) => void): () => void {
    return this.subscribe(`/topic/execution/${executionId}/steps`, (message) => {
      if (message.type === 'FLOW_STEP_UPDATE') {
        handler(message as FlowStepUpdate)
      }
    })
  }

  public subscribeToFlowDefinitionUpdates(handler: (update: FlowDefinitionUpdate) => void): () => void {
    return this.subscribe('/topic/flow-definitions', (message) => {
      if (message.type === 'FLOW_DEFINITION_UPDATE') {
        handler(message as FlowDefinitionUpdate)
      }
    })
  }

  public subscribeToFlowValidation(flowId: string, handler: (result: FlowValidationResult) => void): () => void {
    return this.subscribe(`/topic/flow/${flowId}/validation`, (message) => {
      if (message.type === 'FLOW_VALIDATION_RESULT') {
        handler(message as FlowValidationResult)
      }
    })
  }

  public subscribeToSystemHealth(handler: (update: SystemHealthUpdate) => void): () => void {
    return this.subscribe('/topic/system-health', (message) => {
      if (message.type === 'SYSTEM_HEALTH_UPDATE') {
        handler(message as SystemHealthUpdate)
      }
    })
  }

  public disconnect(): void {
    this.clearReconnectTimer()
    this.isConnected = false
    this.subscriptions.clear()
    
    if (this.client) {
      this.client.deactivate()
      this.client = null
    }
    
    console.log('[WebSocket] Disconnected and cleaned up')
  }

  public isConnectionActive(): boolean {
    return this.isConnected && this.client?.connected === true
  }

  public getConnectionStatus(): 'connected' | 'connecting' | 'disconnected' | 'reconnecting' {
    if (!this.client) return 'disconnected'
    if (this.isConnected && this.client.connected) return 'connected'
    if (this.reconnectAttempts > 0) return 'reconnecting'
    if (this.client.active) return 'connecting'
    return 'disconnected'
  }
}

// Global WebSocket service instance
let webSocketService: WebSocketService | null = null

export function getWebSocketService(): WebSocketService {
  if (!webSocketService) {
    webSocketService = new WebSocketService()
  }
  return webSocketService
}

// Initialize WebSocket connection when user is authenticated
export function initializeWebSocket(): Promise<void> {
  const service = getWebSocketService()
  return service.connect()
}

// Cleanup WebSocket connection
export function disconnectWebSocket(): void {
  if (webSocketService) {
    webSocketService.disconnect()
    webSocketService = null
  }
}