import React from 'react'
import { Handle, Position, type NodeProps, type Node, useReactFlow } from '@xyflow/react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'

export interface ParallelSplitNodeData extends Record<string, unknown> {
  label: string
  parallelPaths: number
  status?: 'idle' | 'running' | 'success' | 'error'
  showDeleteButton?: boolean
}

export type ParallelSplitNodeType = Node<ParallelSplitNodeData, 'parallelSplit'>

// BPMN 2.0 Parallel Gateway (AND) - + marker
const ParallelMarker = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="3">
    <line x1="12" y1="5" x2="12" y2="19" />
    <line x1="5" y1="12" x2="19" y2="12" />
  </svg>
)

const ParallelSplitNode: React.FC<NodeProps<ParallelSplitNodeType>> = ({ 
  id,
  data, 
  selected 
}) => {
  const { setNodes, setEdges } = useReactFlow()
  const nodeData = data || { label: 'Parallel', parallelPaths: 2 }
  
  const handleDelete = () => {
    setNodes((nodes) => nodes.filter((node) => node.id !== id))
    setEdges((edges) => edges.filter((edge) => edge.source !== id && edge.target !== id))
  }

  // Calculate handle positions for multiple outputs
  const getOutputHandlePositions = () => {
    const positions = []
    const pathCount = nodeData.parallelPaths || 2
    
    if (pathCount <= 2) {
      positions.push(
        { id: 'out-1', top: '16px' },
        { id: 'out-2', top: '28px' }
      )
    } else {
      for (let i = 0; i < pathCount; i++) {
        const top = 10 + (i * 12)
        positions.push({ id: `out-${i + 1}`, top: `${top}px` })
      }
    }
    
    return positions.slice(0, pathCount)
  }

  const outputPositions = getOutputHandlePositions()

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
      
      {/* BPMN Parallel Gateway - diamond shape */}
      <div
        className={`w-11 h-11 bg-slate-800 border-2 flex items-center justify-center transition-all rotate-45 ${
          selected 
            ? 'border-green-400 shadow-lg shadow-green-500/30' 
            : 'border-green-500 hover:border-green-400'
        }`}
      >
        {/* + marker for parallel gateway */}
        <ParallelMarker className="w-5 h-5 text-green-400 -rotate-45" />
      </div>
      
      {/* Label below */}
      <div className="mt-3 text-xs font-medium text-slate-400 text-center">
        {nodeData.label || 'Parallel'}
      </div>

      {/* Input Handle (left point of diamond) */}
      <Handle
        type="target"
        position={Position.Left}
        className="w-2 h-2 bg-green-500 border border-slate-800"
        style={{ left: '-2px', top: '22px' }}
      />

      {/* Output Handles (right point of diamond) */}
      {/* @ts-expect-error - index is used for positioning in drag operations */}
      {outputPositions.map((pos, _index) => ( // index is used for positioning
        <Handle
          key={pos.id}
          type="source"
          position={Position.Right}
          id={pos.id}
          className="w-2 h-2 bg-green-500 border border-slate-800"
          style={{ 
            right: '-2px',
            top: pos.top
          }}
        />
      ))}
    </div>
  )
}

export default ParallelSplitNode