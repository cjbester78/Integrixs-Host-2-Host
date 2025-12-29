import React from 'react'
import { Handle, Position, type NodeProps, type Node, useReactFlow } from '@xyflow/react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'

export interface UtilityNodeData extends Record<string, unknown> {
  label: string
  utilityType?: 'PGP_ENCRYPT' | 'PGP_DECRYPT' | 'ZIP_COMPRESS' | 'ZIP_EXTRACT' | 
                'FILE_SPLIT' | 'FILE_MERGE' | 'DATA_TRANSFORM' | 'FILE_VALIDATE' | 'CUSTOM_SCRIPT'
  configuration?: Record<string, unknown>
  status?: 'idle' | 'running' | 'success' | 'error'
  processingTime?: number
  showDeleteButton?: boolean
}

export type UtilityNodeType = Node<UtilityNodeData, 'utility'>

// BPMN 2.0 Service Task Icon (gear/cog)
const ServiceTaskIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="currentColor">
    <path d="M12 15.5A3.5 3.5 0 0 1 8.5 12 3.5 3.5 0 0 1 12 8.5a3.5 3.5 0 0 1 3.5 3.5 3.5 3.5 0 0 1-3.5 3.5m7.43-2.53c.04-.32.07-.64.07-.97 0-.33-.03-.66-.07-1l2.11-1.63c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.31-.61-.22l-2.49 1c-.52-.39-1.06-.73-1.69-.98l-.37-2.65A.506.506 0 0 0 14 2h-4c-.25 0-.46.18-.5.42l-.37 2.65c-.63.25-1.17.59-1.69.98l-2.49-1c-.22-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64L4.57 11c-.04.34-.07.67-.07 1 0 .33.03.65.07.97l-2.11 1.66c-.19.15-.25.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1.01c.52.4 1.06.74 1.69.99l.37 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.37-2.65c.63-.26 1.17-.59 1.69-.99l2.49 1.01c.22.08.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.66z"/>
  </svg>
)

// BPMN 2.0 Script Task Icon
const ScriptTaskIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="currentColor">
    <path d="M14.6,16.6L19.2,12L14.6,7.4L16,6L22,12L16,18L14.6,16.6M9.4,16.6L4.8,12L9.4,7.4L8,6L2,12L8,18L9.4,16.6Z"/>
  </svg>
)

const UtilityNode: React.FC<NodeProps<UtilityNodeType>> = ({ 
  id,
  data, 
  selected 
}) => {
  const { setNodes, setEdges } = useReactFlow()
  const nodeData = data || { label: 'Service Task' }
  
  const handleDelete = () => {
    setNodes((nodes) => nodes.filter((node) => node.id !== id))
    setEdges((edges) => edges.filter((edge) => edge.source !== id && edge.target !== id))
  }

  const isScript = nodeData.utilityType === 'CUSTOM_SCRIPT'

  const formatUtilityType = () => {
    if (!nodeData.utilityType) return ''
    return nodeData.utilityType.replace(/_/g, ' ').toLowerCase()
      .split(' ')
      .map((word: string) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ')
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
      
      {/* BPMN Service/Script Task - rounded rectangle */}
      <div
        className={`relative min-w-28 h-14 rounded-lg bg-slate-800 border-2 flex items-center justify-center px-3 transition-all ${
          selected 
            ? 'border-amber-400 shadow-lg shadow-amber-500/30' 
            : 'border-slate-500 hover:border-slate-400'
        }`}
      >
        {/* Task type icon in top-left corner */}
        <div className="absolute top-1 left-1">
          {isScript ? (
            <ScriptTaskIcon className="w-4 h-4 text-amber-400" />
          ) : (
            <ServiceTaskIcon className="w-4 h-4 text-amber-400" />
          )}
        </div>
        
        {/* Task label */}
        <span className="text-xs font-medium text-slate-200 text-center leading-tight pt-2">
          {nodeData.label || formatUtilityType() || 'Service Task'}
        </span>

        {/* Handles positioned on the task box */}
        <Handle
          type="target"
          position={Position.Left}
          className="w-2 h-2 bg-slate-400 border border-slate-800"
          style={{ left: '-4px', top: '50%', transform: 'translateY(-50%)' }}
        />
        <Handle
          type="source"
          position={Position.Right}
          className="w-2 h-2 bg-slate-400 border border-slate-800"
          style={{ right: '-4px', top: '50%', transform: 'translateY(-50%)' }}
        />
      </div>
    </div>
  )
}

export default UtilityNode