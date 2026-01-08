import React, { useState } from 'react'
import { 
  Play,
  CheckCircle,
  GitBranch,
  Split,
  Mail,
  Lock,
  Unlock,
  FileArchive,
  ChevronDown,
  ChevronRight,
  Plus,
  Download,
  Upload
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'

interface NodeSidebarProps {
  onNodeAdd?: (nodeType: string, nodeData: any) => void
  className?: string
}

interface NodeTemplate {
  id: string
  type: string
  label: string
  icon: React.ComponentType<{ className?: string }>
  category: string
  description: string
  data: any
}

const NodeSidebar: React.FC<NodeSidebarProps> = ({
  onNodeAdd,
  className = ''
}) => {
  const [expandedCategories, setExpandedCategories] = useState<string[]>(['adapters', 'gateways', 'utilities'])

  const nodeTemplates: NodeTemplate[] = [
    // Adapters - Just 2 configurable nodes
    {
      id: 'sender-adapter',
      type: 'adapter',
      label: 'Sender Adapter',
      icon: Download,
      category: 'adapters',
      description: 'Configure sender adapter type (File, SFTP)',
      data: { 
        label: 'Sender Adapter',
        adapterType: null,
        direction: 'SENDER',
        availableTypes: ['FILE', 'SFTP']
        // REMOVED: configuration embedding - adapters should only store IDs
      }
    },
    {
      id: 'receiver-adapter',
      type: 'adapter',
      label: 'Receiver Adapter',
      icon: Upload,
      category: 'adapters',
      description: 'Configure receiver adapter type (File, SFTP, Email)',
      data: { 
        label: 'Receiver Adapter',
        adapterType: null,
        direction: 'RECEIVER',
        availableTypes: ['FILE', 'SFTP', 'EMAIL']
        // REMOVED: configuration embedding - adapters should only store IDs
      }
    },
    // Events
    {
      id: 'start-node',
      type: 'start',
      label: 'Start Event',
      icon: Play,
      category: 'events',
      description: 'BPMN message start event',
      data: { label: 'Start', eventType: 'message' }
    },
    {
      id: 'end-node',
      type: 'end',
      label: 'End Event',
      icon: CheckCircle,
      category: 'events',
      description: 'BPMN message end event',
      data: { label: 'End', eventType: 'message' }
    },
    {
      id: 'message-end-node',
      type: 'messageEnd',
      label: 'Message End',
      icon: Mail,
      category: 'events',
      description: 'Throw message end event',
      data: { 
        label: 'Message End',
        eventType: 'MESSAGE_END',
        messagePayload: ''
      }
    },
    // Gateways
    {
      id: 'decision-node',
      type: 'decision',
      label: 'Exclusive Gateway',
      icon: GitBranch,
      category: 'gateways',
      description: 'BPMN XOR gateway - conditional branching',
      data: { 
        label: 'Gateway',
        conditionType: 'ALWAYS_TRUE',
        condition: 'true'
      }
    },
    {
      id: 'parallel-split-node',
      type: 'parallelSplit',
      label: 'Parallel Gateway',
      icon: Split,
      category: 'gateways',
      description: 'BPMN AND gateway - parallel execution',
      data: { 
        label: 'Parallel',
        parallelPaths: 2
      }
    },
    // Service Tasks / Utilities
    {
      id: 'pgp-encrypt',
      type: 'utility',
      label: 'PGP Encrypt',
      icon: Lock,
      category: 'utilities',
      description: 'Encrypt files with PGP',
      data: { 
        label: 'PGP Encrypt',
        utilityType: 'PGP_ENCRYPT',
        configuration: {
          algorithm: 'RSA-4096'
        }
      }
    },
    {
      id: 'pgp-decrypt',
      type: 'utility',
      label: 'PGP Decrypt',
      icon: Unlock,
      category: 'utilities',
      description: 'Decrypt files with PGP',
      data: { 
        label: 'PGP Decrypt',
        utilityType: 'PGP_DECRYPT',
        configuration: {
          algorithm: 'RSA-4096'
        }
      }
    },
    {
      id: 'unzip-extract',
      type: 'utility',
      label: 'Unzip',
      icon: FileArchive,
      category: 'utilities',
      description: 'Extract files from ZIP archives',
      data: { 
        label: 'Unzip',
        utilityType: 'ZIP_EXTRACT',
        configuration: {
          extractToDirectory: '',
          preserveStructure: true
        }
      }
    }
  ]

  const categories = [
    { id: 'adapters', label: 'Adapters', color: 'bg-blue-500/20 text-blue-400' },
    { id: 'events', label: 'Events', color: 'bg-green-500/20 text-green-400' },
    { id: 'gateways', label: 'Gateways', color: 'bg-amber-500/20 text-amber-400' },
    { id: 'utilities', label: 'Service Tasks', color: 'bg-purple-500/20 text-purple-400' }
  ]

  const toggleCategory = (categoryId: string) => {
    setExpandedCategories(prev =>
      prev.includes(categoryId)
        ? prev.filter(id => id !== categoryId)
        : [...prev, categoryId]
    )
  }

  const handleNodeDragStart = (event: React.DragEvent, nodeTemplate: NodeTemplate) => {
    event.dataTransfer.setData('application/reactflow', JSON.stringify({
      nodeType: nodeTemplate.type,
      nodeData: nodeTemplate.data
    }))
    event.dataTransfer.effectAllowed = 'move'
  }

  const handleNodeAdd = (nodeTemplate: NodeTemplate) => {
    if (onNodeAdd) {
      onNodeAdd(nodeTemplate.type, nodeTemplate.data)
    }
  }

  return (
    <div className={`w-80 bg-slate-900 border-r border-slate-700 flex flex-col ${className}`}>
      {/* Header */}
      <div className="p-4 border-b border-slate-700">
        <h3 className="font-semibold text-lg text-slate-100">BPMN Elements</h3>
        <p className="text-sm text-slate-400">
          Drag nodes to the canvas or click to add
        </p>
      </div>

      {/* Node Categories */}
      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {categories.map(category => {
          const categoryNodes = nodeTemplates.filter(node => node.category === category.id)
          const isExpanded = expandedCategories.includes(category.id)

          return (
            <Card key={category.id} className="bg-slate-800 border-slate-700">
              <Collapsible open={isExpanded} onOpenChange={() => toggleCategory(category.id)}>
                <CollapsibleTrigger asChild>
                  <CardHeader className="py-2 px-3 cursor-pointer hover:bg-slate-700/50 transition-colors">
                    <CardTitle className="flex items-center justify-between text-sm">
                      <div className="flex items-center space-x-2">
                        <Badge variant="secondary" className={category.color}>
                          {categoryNodes.length}
                        </Badge>
                        <span className="text-slate-200">{category.label}</span>
                      </div>
                      {isExpanded ? (
                        <ChevronDown className="h-4 w-4 text-slate-400" />
                      ) : (
                        <ChevronRight className="h-4 w-4 text-slate-400" />
                      )}
                    </CardTitle>
                  </CardHeader>
                </CollapsibleTrigger>

                <CollapsibleContent>
                  <CardContent className="pt-0 px-2 pb-2 space-y-1">
                    {categoryNodes.map(node => {
                      const IconComponent = node.icon
                      return (
                        <div
                          key={node.id}
                          draggable
                          onDragStart={(e) => handleNodeDragStart(e, node)}
                          className="group p-2 border border-slate-600 rounded-lg cursor-grab active:cursor-grabbing hover:bg-slate-700/50 hover:border-slate-500 transition-all"
                        >
                          <div className="flex items-start space-x-2">
                            <div className="w-7 h-7 bg-slate-700 rounded flex items-center justify-center flex-shrink-0">
                              <IconComponent className="h-4 w-4 text-slate-300" />
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center justify-between">
                                <h4 className="font-medium text-xs text-slate-200 truncate">
                                  {node.label}
                                </h4>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => handleNodeAdd(node)}
                                  className="opacity-0 group-hover:opacity-100 transition-opacity h-5 w-5 p-0 text-slate-400 hover:text-slate-200"
                                >
                                  <Plus className="h-3 w-3" />
                                </Button>
                              </div>
                              <p className="text-[10px] text-slate-500 mt-0.5 leading-tight">
                                {node.description}
                              </p>
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </CardContent>
                </CollapsibleContent>
              </Collapsible>
            </Card>
          )
        })}
      </div>

      {/* Footer */}
      <div className="p-3 border-t border-slate-700 bg-slate-800/50">
        <p className="text-[10px] text-slate-500 text-center">
          Drag nodes to canvas or click + to add
        </p>
      </div>
    </div>
  )
}

export default NodeSidebar