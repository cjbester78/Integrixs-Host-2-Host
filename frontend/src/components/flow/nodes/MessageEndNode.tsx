import React from 'react'
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react'

export interface MessageEndNodeData extends Record<string, unknown> {
  label: string
  eventType?: string
  messagePayload?: string
  showDeleteButton?: boolean
}

export type MessageEndNodeType = Node<MessageEndNodeData, 'messageEnd'>

// BPMN 2.0 Message Icon (filled for throw/end events)
const MessageIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="currentColor" stroke="currentColor" strokeWidth="0.5">
    <rect x="3" y="5" width="18" height="14" rx="1" />
    <polyline points="3,5 12,13 21,5" fill="none" strokeWidth="1.5" />
  </svg>
)

const MessageEndNode: React.FC<NodeProps<MessageEndNodeType>> = ({ data, selected }) => {
  const nodeData = data || { 
    label: 'Message End', 
    eventType: 'MESSAGE_END', 
    messagePayload: ''
  }
  
  return (
    <div className="flex flex-col items-center relative">
      {/* System node - no delete button */}
      
      {/* Node container with handles */}
      <div className="relative">
        {/* Target handle - left */}
        <Handle
          type="target"
          position={Position.Left}
          className="w-2 h-2 bg-blue-500 border border-slate-800"
          style={{ left: '-4px', top: '50%', transform: 'translateY(-50%)' }}
        />

        {/* BPMN Message End Event - thick border circle with filled message icon */}
        <div
          className={`w-12 h-12 rounded-full bg-slate-800 border-[3px] flex items-center justify-center transition-all ${
            selected 
              ? 'border-blue-400 shadow-lg shadow-blue-500/30' 
              : 'border-blue-500 hover:border-blue-400'
          }`}
        >
          <MessageIcon className="w-5 h-5 text-blue-400" />
        </div>

        {/* Source handle - right */}
        <Handle
          type="source"
          position={Position.Right}
          className="w-2 h-2 bg-blue-500 border border-slate-800"
          style={{ right: '-4px', top: '50%', transform: 'translateY(-50%)' }}
        />
      </div>
      
      {/* Label below */}
      <div className="mt-2 text-xs font-medium text-slate-400 text-center max-w-20">
        {nodeData.label || 'Message End'}
      </div>
    </div>
  )
}

export default MessageEndNode