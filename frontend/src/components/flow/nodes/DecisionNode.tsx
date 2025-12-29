import React from 'react'
import { Handle, Position, type NodeProps, type Node, useReactFlow } from '@xyflow/react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'

export interface DecisionNodeData extends Record<string, unknown> {
  label: string
  conditionType?: 'ALWAYS_TRUE' | 'ALWAYS_FALSE' | 'CONTEXT_CONTAINS_KEY' | 
                  'CONTEXT_VALUE_EQUALS' | 'FILE_COUNT_GREATER_THAN'
  condition?: string
  configuration?: Record<string, unknown>
  status?: 'idle' | 'running' | 'success' | 'error'
  lastResult?: boolean
  showDeleteButton?: boolean
}

export type DecisionNodeType = Node<DecisionNodeData, 'decision'>

// BPMN 2.0 Exclusive Gateway (XOR) - X marker
const ExclusiveMarker = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="3">
    <line x1="7" y1="7" x2="17" y2="17" />
    <line x1="17" y1="7" x2="7" y2="17" />
  </svg>
)

const DecisionNode: React.FC<NodeProps<DecisionNodeType>> = ({ 
  id,
  data, 
  selected 
}) => {
  const { setNodes, setEdges } = useReactFlow()
  const nodeData = data || { label: 'Gateway' }
  
  const handleDelete = () => {
    setNodes((nodes) => nodes.filter((node) => node.id !== id))
    setEdges((edges) => edges.filter((edge) => edge.source !== id && edge.target !== id))
  }

  return (
    <div className="flex flex-col items-center relative">
      {/* Delete button */}
      {nodeData.showDeleteButton && (
        <Button
          variant="ghost"
          size="sm"
          onClick={handleDelete}
          className="absolute -top-3 right-0 h-5 w-5 p-0 bg-red-500 text-white rounded-full shadow-md hover:bg-red-600 z-10"
          title="Delete node"
        >
          <X className="h-3 w-3" />
        </Button>
      )}
      
      {/* BPMN Exclusive Gateway - diamond shape */}
      <div
        className={`w-11 h-11 bg-slate-800 border-2 flex items-center justify-center transition-all rotate-45 ${
          selected 
            ? 'border-amber-400 shadow-lg shadow-amber-500/30' 
            : 'border-amber-500 hover:border-amber-400'
        }`}
      >
        {/* X marker for exclusive gateway */}
        <ExclusiveMarker className="w-5 h-5 text-amber-400 -rotate-45" />
      </div>
      
      {/* Label below */}
      <div className="mt-3 text-xs font-medium text-slate-400 text-center max-w-20">
        {nodeData.label || 'Gateway'}
      </div>

      {/* Input handle (left point of diamond) */}
      <Handle
        type="target"
        position={Position.Left}
        className="w-2 h-2 bg-amber-500 border border-slate-800"
        style={{ left: '-2px', top: '22px' }}
      />
      
      {/* True/Yes path (right point of diamond) */}
      <Handle
        type="source"
        position={Position.Right}
        id="yes"
        className="w-2 h-2 bg-green-500 border border-slate-800"
        style={{ right: '-2px', top: '22px' }}
      />
      
      {/* False/No path (bottom point of diamond) */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="no"
        className="w-2 h-2 bg-red-500 border border-slate-800"
        style={{ bottom: '14px', left: '50%', transform: 'translateX(-50%)' }}
      />
    </div>
  )
}

export default DecisionNode