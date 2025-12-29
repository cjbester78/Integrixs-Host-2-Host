import React from 'react'
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react'

export interface EndNodeData extends Record<string, unknown> {
  label: string
  eventType?: 'none' | 'message' | 'terminate' | 'error'
  showDeleteButton?: boolean
}

export type EndNodeType = Node<EndNodeData, 'end'>

// BPMN 2.0 Message Icon (filled for throw events)
const MessageIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="currentColor" stroke="currentColor" strokeWidth="0.5">
    <rect x="3" y="5" width="18" height="14" rx="1" />
    <polyline points="3,5 12,13 21,5" fill="none" strokeWidth="1.5" />
  </svg>
)

const EndNode: React.FC<NodeProps<EndNodeType>> = ({ data, selected }) => {
  const nodeData = data || { label: 'End', eventType: 'message' }
  
  return (
    <div className="flex flex-col items-center relative">
      {/* System node - no delete button */}
      
      {/* Node container with handles */}
      <div className="relative">
        {/* Target handle - left */}
        <Handle
          type="target"
          position={Position.Left}
          className="w-2 h-2 bg-red-500 border border-slate-800"
          style={{ left: '-4px', top: '50%', transform: 'translateY(-50%)' }}
        />

        {/* BPMN End Event - thick border circle */}
        <div
          className={`w-12 h-12 rounded-full bg-slate-800 border-[3px] flex items-center justify-center transition-all ${
            selected 
              ? 'border-red-400 shadow-lg shadow-red-500/30' 
              : 'border-red-500 hover:border-red-400'
          }`}
        >
          {/* Message icon (filled) for message end event */}
          <MessageIcon className="w-5 h-5 text-red-400" />
        </div>

        {/* Source handle - right (for receiver adapter connection) */}
        <Handle
          type="source"
          position={Position.Right}
          className="w-2 h-2 bg-red-500 border border-slate-800"
          style={{ right: '-4px', top: '50%', transform: 'translateY(-50%)' }}
        />
      </div>
      
      {/* Label below */}
      <div className="mt-2 text-xs font-medium text-slate-400">
        {nodeData.label || 'End'}
      </div>
    </div>
  )
}

export default EndNode