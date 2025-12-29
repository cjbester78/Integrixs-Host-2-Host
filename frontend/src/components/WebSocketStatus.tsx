import React from 'react'
import { Wifi, WifiOff, RotateCw } from 'lucide-react'
import { useWebSocketConnection } from '@/hooks/useWebSocket'

interface WebSocketStatusProps {
  className?: string
  showLabel?: boolean
  size?: 'sm' | 'md' | 'lg'
}

const WebSocketStatus: React.FC<WebSocketStatusProps> = ({ 
  className = '', 
  showLabel = true,
  size = 'md' 
}) => {
  const { status } = useWebSocketConnection()

  const getIcon = () => {
    const iconSize = size === 'sm' ? 'h-3 w-3' : size === 'lg' ? 'h-5 w-5' : 'h-4 w-4'
    
    switch (status) {
      case 'connected':
        return <Wifi className={`${iconSize} text-success`} />
      case 'connecting':
        return <RotateCw className={`${iconSize} text-warning animate-spin`} />
      case 'reconnecting':
        return <RotateCw className={`${iconSize} text-warning animate-spin`} />
      case 'disconnected':
      default:
        return <WifiOff className={`${iconSize} text-destructive`} />
    }
  }

  const getStatusText = () => {
    switch (status) {
      case 'connected':
        return 'Live Updates'
      case 'connecting':
        return 'Connecting...'
      case 'reconnecting':
        return 'Reconnecting...'
      case 'disconnected':
      default:
        return 'Offline'
    }
  }

  const getStatusColor = () => {
    switch (status) {
      case 'connected':
        return 'text-success'
      case 'connecting':
      case 'reconnecting':
        return 'text-warning'
      case 'disconnected':
      default:
        return 'text-destructive'
    }
  }

  const textSize = size === 'sm' ? 'text-xs' : size === 'lg' ? 'text-sm' : 'text-xs'

  return (
    <div className={`flex items-center space-x-2 ${className}`}>
      {getIcon()}
      {showLabel && (
        <span className={`${textSize} ${getStatusColor()}`}>
          {getStatusText()}
        </span>
      )}
    </div>
  )
}

export default WebSocketStatus