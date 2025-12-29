import React from 'react'
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react'

export interface StartNodeData extends Record<string, unknown> {
  label: string
  eventType?: 'none' | 'message' | 'timer' | 'signal'
  showDeleteButton?: boolean
}

export type StartNodeType = Node<StartNodeData, 'start'>

// BPMN 2.0 Message Icon
const MessageIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="1.5">
    <rect x="3" y="5" width="18" height="14" rx="1" />
    <polyline points="3,5 12,13 21,5" />
  </svg>
)

const StartNode: React.FC<NodeProps<StartNodeType>> = ({ data, selected }) => {
  const nodeData = data || { label: 'Start', eventType: 'message' }
  
  return (
    <div className="flex flex-col items-center relative">
      {/* System node - no delete button */}
      
      {/* Node container with handles */}
      <div className="relative">
        {/* Target handle - left (for sender adapter connection) */}
        <Handle
          type="target"
          position={Position.Left}
          className="w-2 h-2 bg-green-500 border border-slate-800"
          style={{ left: '-4px', top: '50%', transform: 'translateY(-50%)' }}
        />

        {/* BPMN Start Event - thin border circle */}
        <div
          className={`w-12 h-12 rounded-full bg-slate-800 border flex items-center justify-center transition-all ${
            selected 
              ? 'border-2 border-green-400 shadow-lg shadow-green-500/30' 
              : 'border-green-500 hover:border-green-400'
          }`}
        >
          {/* Message icon for message start event */}
          <MessageIcon className="w-5 h-5 text-green-400" />
        </div>

        {/* Source handle - right */}
        <Handle
          type="source"
          position={Position.Right}
          className="w-2 h-2 bg-green-500 border border-slate-800"
          style={{ right: '-4px', top: '50%', transform: 'translateY(-50%)' }}
        />
      </div>
      
      {/* Label below */}
      <div className="mt-2 text-xs font-medium text-slate-400">
        {nodeData.label || 'Start'}
      </div>
    </div>
  )
}

export default StartNode