import { useEffect, useState, useCallback, useRef } from 'react'
import { 
  getWebSocketService, 
  FlowExecutionUpdate, 
  FlowStepUpdate, 
  FlowDefinitionUpdate, 
  SystemHealthUpdate,
  WebSocketMessage,
  MessageHandler 
} from '@/services/websocket'
import { useNotifications } from '@/stores/ui'

export interface WebSocketConnectionState {
  isConnected: boolean
  status: 'connected' | 'connecting' | 'disconnected' | 'reconnecting'
  error: string | null
}

/**
 * Hook for managing WebSocket connection state
 */
export function useWebSocketConnection(): WebSocketConnectionState {
  const [state, setState] = useState<WebSocketConnectionState>({
    isConnected: false,
    status: 'disconnected',
    error: null
  })

  useEffect(() => {
    const service = getWebSocketService()
    
    // Check initial connection state
    const updateState = () => {
      setState({
        isConnected: service.isConnectionActive(),
        status: service.getConnectionStatus(),
        error: null
      })
    }

    // Update state immediately
    updateState()

    // Set up periodic status checks
    const interval = setInterval(updateState, 1000)

    // Try to connect if not connected
    if (!service.isConnectionActive()) {
      service.connect().catch((error) => {
        setState(prev => ({
          ...prev,
          error: error.message,
          status: 'disconnected'
        }))
      })
    }

    return () => {
      clearInterval(interval)
    }
  }, [])

  return state
}

/**
 * Hook for subscribing to flow execution updates
 */
export function useFlowExecutionUpdates(options?: {
  flowId?: string
  executionId?: string
  onUpdate?: (update: FlowExecutionUpdate) => void
}) {
  const { success, error } = useNotifications()
  const handlerRef = useRef(options?.onUpdate)
  
  // Keep handler ref updated
  handlerRef.current = options?.onUpdate

  useEffect(() => {
    const service = getWebSocketService()
    const unsubscribeFunctions: (() => void)[] = []

    const handleUpdate = (update: FlowExecutionUpdate) => {
      // Call the provided handler
      if (handlerRef.current) {
        handlerRef.current(update)
      }

      // Show toast notifications for important updates
      switch (update.status) {
        case 'COMPLETED':
          success(
            'Flow Completed',
            `Flow "${update.flowName}" executed successfully`
          )
          break
        case 'FAILED':
          error(
            'Flow Failed',
            update.errorMessage || `Flow "${update.flowName}" execution failed`
          )
          break
        case 'CANCELLED':
          error(
            'Flow Cancelled',
            `Flow "${update.flowName}" was cancelled`
          )
          break
      }
    }

    // Subscribe based on options
    if (options?.executionId) {
      unsubscribeFunctions.push(
        service.subscribeToSpecificExecution(options.executionId, (update) => {
          if (update.type === 'FLOW_EXECUTION_UPDATE') {
            handleUpdate(update as FlowExecutionUpdate)
          }
        })
      )
    } else if (options?.flowId) {
      unsubscribeFunctions.push(
        service.subscribeToSpecificFlowExecutions(options.flowId, handleUpdate)
      )
    } else {
      unsubscribeFunctions.push(
        service.subscribeToFlowExecutions(handleUpdate)
      )
    }

    return () => {
      unsubscribeFunctions.forEach(unsubscribe => unsubscribe())
    }
  }, [options?.flowId, options?.executionId, success, error])
}

/**
 * Hook for subscribing to flow step updates
 */
export function useFlowStepUpdates(
  executionId: string | undefined,
  onUpdate?: (update: FlowStepUpdate) => void
) {
  const handlerRef = useRef(onUpdate)
  
  // Keep handler ref updated
  handlerRef.current = onUpdate

  useEffect(() => {
    if (!executionId) return

    const service = getWebSocketService()
    
    const unsubscribe = service.subscribeToExecutionSteps(executionId, (update) => {
      if (handlerRef.current) {
        handlerRef.current(update)
      }
    })

    return unsubscribe
  }, [executionId])
}

/**
 * Hook for subscribing to flow validation updates
 */
export function useFlowValidationUpdates(
  onUpdate?: (update: any) => void
) {
  const handlerRef = useRef(onUpdate)
  
  // Keep handler ref updated
  handlerRef.current = onUpdate

  useEffect(() => {
    const service = getWebSocketService()
    
    const unsubscribe = service.subscribe('/topic/flow-validations', (message) => {
      if (handlerRef.current && message.type === 'FLOW_VALIDATION_RESULT') {
        handlerRef.current(message)
      }
    })

    return unsubscribe
  }, [])
}

/**
 * Hook for subscribing to flow definition updates (create, update, delete)
 */
export function useFlowDefinitionUpdates(
  onUpdate?: (update: FlowDefinitionUpdate) => void
) {
  const { success, error } = useNotifications()
  const handlerRef = useRef(onUpdate)
  
  // Keep handler ref updated
  handlerRef.current = onUpdate

  useEffect(() => {
    const service = getWebSocketService()
    
    const unsubscribe = service.subscribeToFlowDefinitionUpdates((update) => {
      // Call the provided handler
      if (handlerRef.current) {
        handlerRef.current(update)
      }

      // Show toast notifications for flow changes
      switch (update.updateType) {
        case 'CREATED':
          success(
            'Flow Created',
            `New flow "${update.flowName}" has been created`
          )
          break
        case 'UPDATED':
          success(
            'Flow Updated',
            `Flow "${update.flowName}" has been updated`
          )
          break
        case 'DELETED':
          error(
            'Flow Deleted',
            `Flow "${update.flowName}" has been deleted`
          )
          break
        case 'ENABLED':
          success(
            'Flow Enabled',
            `Flow "${update.flowName}" has been enabled`
          )
          break
        case 'DISABLED':
          error(
            'Flow Disabled',
            `Flow "${update.flowName}" has been disabled`
          )
          break
      }
    })

    return unsubscribe
  }, [success, error])
}

/**
 * Hook for subscribing to system health updates
 */
export function useSystemHealthUpdates(
  onUpdate?: (update: SystemHealthUpdate) => void
) {
  const handlerRef = useRef(onUpdate)
  
  // Keep handler ref updated
  handlerRef.current = onUpdate

  useEffect(() => {
    const service = getWebSocketService()
    
    const unsubscribe = service.subscribeToSystemHealth((update) => {
      if (handlerRef.current) {
        handlerRef.current(update)
      }
    })

    return unsubscribe
  }, [])
}

/**
 * Hook for custom WebSocket subscriptions
 */
export function useWebSocketSubscription(
  topic: string,
  handler: MessageHandler,
  enabled = true
) {
  const handlerRef = useRef(handler)
  
  // Keep handler ref updated
  handlerRef.current = handler

  useEffect(() => {
    if (!enabled) return

    const service = getWebSocketService()
    
    const unsubscribe = service.subscribe(topic, (message) => {
      handlerRef.current(message)
    })

    return unsubscribe
  }, [topic, enabled])
}

/**
 * Hook for managing real-time data updates with automatic refresh
 */
export function useRealTimeData<_T>(
  _queryKey: any[],
  refetchQuery: () => Promise<any>,
  subscriptions?: Array<{
    topic: string
    shouldRefetch: (message: WebSocketMessage) => boolean
  }>
) {
  const refetchRef = useRef(refetchQuery)
  
  // Keep refetch ref updated
  refetchRef.current = refetchQuery

  const debouncedRefetch = useCallback(() => {
    // Debounce rapid updates to avoid excessive API calls
    const timeoutId = setTimeout(() => {
      refetchRef.current()
    }, 500)
    
    return () => clearTimeout(timeoutId)
  }, [])

  useEffect(() => {
    if (!subscriptions || subscriptions.length === 0) return

    const service = getWebSocketService()
    const unsubscribeFunctions: (() => void)[] = []

    for (const subscription of subscriptions) {
      const unsubscribe = service.subscribe(subscription.topic, (message) => {
        if (subscription.shouldRefetch(message)) {
          debouncedRefetch()
        }
      })
      unsubscribeFunctions.push(unsubscribe)
    }

    return () => {
      unsubscribeFunctions.forEach(unsubscribe => unsubscribe())
    }
  }, [subscriptions, debouncedRefetch])
}