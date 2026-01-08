import React from 'react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { useEnvironment } from '@/hooks/useEnvironment'

interface EnvironmentBadgeProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
  showName?: boolean
}

const sizeClasses = {
  sm: 'text-xs px-2 py-0.5',
  md: 'text-sm px-2.5 py-1',
  lg: 'text-base px-3 py-1.5'
}

const getEnvironmentDetails = (envType?: string) => {
  const type = envType?.toUpperCase() || 'DEVELOPMENT'
  
  switch (type) {
    case 'DEVELOPMENT':
      return {
        id: 'H2H-DEV',
        name: 'Development',
        color: '#22c55e', // green-500
        className: 'bg-green-100 text-green-800 border-green-200'
      }
    case 'QUALITY_ASSURANCE':
      return {
        id: 'H2H-QAS',
        name: 'Quality Assurance', 
        color: '#8b5cf6', // purple-500
        className: 'bg-purple-100 text-purple-800 border-purple-200'
      }
    case 'PRODUCTION':
      return {
        id: 'H2H-PRD',
        name: 'Production',
        color: '#ef4444', // red-500
        className: 'bg-red-100 text-red-800 border-red-200'
      }
    default:
      return {
        id: 'H2H-UNK',
        name: 'Unknown',
        color: '#6b7280', // gray-500
        className: 'bg-gray-100 text-gray-800 border-gray-200'
      }
  }
}

const EnvironmentBadge: React.FC<EnvironmentBadgeProps> = ({
  size = 'md',
  className,
  showName = false
}) => {
  const { data: environment, isLoading } = useEnvironment()

  if (isLoading || !environment) {
    return (
      <div className="animate-pulse bg-gray-200 rounded-md h-6 w-16 inline-flex items-center justify-center">
        <div className="w-2 h-2 bg-gray-300 rounded-full mr-1" />
        <div className="w-8 h-4 bg-gray-300 rounded" />
      </div>
    )
  }

  const envDetails = getEnvironmentDetails(environment.type)

  return (
    <Badge 
      className={cn(
        envDetails.className,
        sizeClasses[size],
        "inline-flex items-center gap-1.5 transition-all duration-200 font-mono font-bold",
        className
      )}
      title={`Environment: ${environment.displayName} (${environment.type})`}
    >
      {/* Animated pulsing dot indicator */}
      <div 
        className="w-2 h-2 rounded-full animate-pulse"
        style={{ 
          backgroundColor: envDetails.color,
          filter: 'brightness(1.2)'
        }}
      />
      
      {/* Environment ID */}
      <span className="font-mono font-bold tracking-wide">
        {envDetails.id}
      </span>
      
      {/* Optional environment name */}
      {showName && (
        <span className="font-sans font-normal ml-0.5">
          {envDetails.name}
        </span>
      )}
    </Badge>
  )
}

// Loading skeleton for environment badge
export const EnvironmentBadgeSkeleton: React.FC<{ size?: 'sm' | 'md' | 'lg' }> = ({ 
  size = 'md' 
}) => {
  return (
    <div className={cn(
      'animate-pulse bg-gray-200 rounded-md inline-flex items-center gap-1',
      sizeClasses[size]
    )}>
      <div className="w-2 h-2 bg-gray-300 rounded-full" />
      <div className="w-8 h-4 bg-gray-300 rounded" />
    </div>
  )
}

export default EnvironmentBadge