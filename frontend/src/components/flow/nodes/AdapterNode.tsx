import React from 'react'
import { Handle, Position, type NodeProps, type Node, useReactFlow } from '@xyflow/react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'

export interface AdapterNodeData extends Record<string, unknown> {
  label: string
  adapterType?: 'FILE' | 'SFTP' | 'EMAIL' | null
  direction?: 'SENDER' | 'RECEIVER'
  adapterId?: string
  configuration?: Record<string, unknown>
  status?: 'idle' | 'running' | 'success' | 'error'
  lastExecution?: string
  showDeleteButton?: boolean
}

export type AdapterNodeType = Node<AdapterNodeData, 'adapter'>

// BPMN 2.0 Send Task Icon (filled envelope)
const SendTaskIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="currentColor">
    <path d="M2,21L23,12L2,3V10L17,12L2,14V21Z" />
  </svg>
)

// BPMN 2.0 Receive Task Icon (outlined envelope)
const ReceiveTaskIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="1.5">
    <rect x="3" y="5" width="18" height="14" rx="1" />
    <polyline points="3,5 12,13 21,5" />
  </svg>
)

// BPMN 2.0 Service Task Icon
const ServiceTaskIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="currentColor">
    <path d="M12 15.5A3.5 3.5 0 0 1 8.5 12 3.5 3.5 0 0 1 12 8.5a3.5 3.5 0 0 1 3.5 3.5 3.5 3.5 0 0 1-3.5 3.5m7.43-2.53c.04-.32.07-.64.07-.97 0-.33-.03-.66-.07-1l2.11-1.63c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.31-.61-.22l-2.49 1c-.52-.39-1.06-.73-1.69-.98l-.37-2.65A.506.506 0 0 0 14 2h-4c-.25 0-.46.18-.5.42l-.37 2.65c-.63.25-1.17.59-1.69.98l-2.49-1c-.22-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64L4.57 11c-.04.34-.07.67-.07 1 0 .33.03.65.07.97l-2.11 1.66c-.19.15-.25.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1.01c.52.4 1.06.74 1.69.99l.37 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.37-2.65c.63-.26 1.17-.59 1.69-.99l2.49 1.01c.22.08.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.66z"/>
  </svg>
)

const AdapterNode: React.FC<NodeProps<AdapterNodeType>> = ({ 
  id,
  data, 
  selected 
}) => {
  const { setNodes, setEdges } = useReactFlow()
  const nodeData = data || { label: 'Adapter' }

  const getTaskIcon = () => {
    if (nodeData.direction === 'SENDER') {
      return ReceiveTaskIcon
    } else if (nodeData.direction === 'RECEIVER') {
      return SendTaskIcon
    }
    return ServiceTaskIcon
  }

  const TaskIcon = getTaskIcon()
  
  const handleDelete = () => {
    setNodes((nodes) => nodes.filter((node) => node.id !== id))
    setEdges((edges) => edges.filter((edge) => edge.source !== id && edge.target !== id))
  }

  const getBorderColor = () => {
    if (selected) return 'border-blue-400 shadow-lg shadow-blue-500/30'
    if (nodeData.direction === 'SENDER') return 'border-green-500 hover:border-green-400'
    if (nodeData.direction === 'RECEIVER') return 'border-orange-500 hover:border-orange-400'
    return 'border-slate-500 hover:border-slate-400'
  }

  const getIconColor = () => {
    if (nodeData.direction === 'SENDER') return 'text-green-400'
    if (nodeData.direction === 'RECEIVER') return 'text-orange-400'
    return 'text-blue-400'
  }

  return (
    <div className="flex flex-col items-center relative">
      {/* Delete button */}
      {nodeData.showDeleteButton && (
        <Button
          variant="ghost"
          size="sm"
          onClick={handleDelete}
          className="absolute -top-3 -right-3 h-5 w-5 p-0 bg-red-500 text-white rounded-full shadow-md hover:bg-red-600 z-10"
          title="Delete node"
        >
          <X className="h-3 w-3" />
        </Button>
      )}
      
      {/* BPMN Task - rounded rectangle */}
      <div
        className={`relative min-w-28 h-14 rounded-lg bg-slate-800 border-2 flex items-center justify-center px-3 transition-all ${getBorderColor()}`}
      >
        {/* Task type icon in top-left corner */}
        <div className="absolute top-1 left-1">
          <TaskIcon className={`w-4 h-4 ${getIconColor()}`} />
        </div>
        
        {/* Task label */}
        <span className="text-xs font-medium text-slate-200 text-center leading-tight pt-2">
          {nodeData.label || 'Adapter'}
        </span>
      </div>
      
      {/* Type indicator */}
      <div className="mt-1 text-[10px] text-slate-500">
        {nodeData.adapterType && nodeData.adapterType !== null
          ? `${nodeData.adapterType} ${nodeData.direction}` 
          : `${nodeData.direction} (Select instance)`
        }
      </div>

      {/* Handles */}
      {nodeData.direction === 'SENDER' && (
        <Handle
          type="source"
          position={Position.Right}
          className="w-2 h-2 bg-green-500 border border-slate-800"
          style={{ right: '-4px', top: '28px' }}
        />
      )}
      
      {nodeData.direction === 'RECEIVER' && (
        <Handle
          type="target"
          position={Position.Left}
          className="w-2 h-2 bg-orange-500 border border-slate-800"
          style={{ left: '-4px', top: '28px' }}
        />
      )}

      {/* Bidirectional adapters */}
      {!nodeData.direction && (
        <>
          <Handle
            type="target"
            position={Position.Left}
            className="w-2 h-2 bg-slate-400 border border-slate-800"
            style={{ left: '-4px', top: '28px' }}
          />
          <Handle
            type="source"
            position={Position.Right}
            className="w-2 h-2 bg-slate-400 border border-slate-800"
            style={{ right: '-4px', top: '28px' }}
          />
        </>
      )}
    </div>
  )
}

export default AdapterNode