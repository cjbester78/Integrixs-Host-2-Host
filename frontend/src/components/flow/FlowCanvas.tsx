import React, { useCallback, useRef, useState } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  useReactFlow,  
  reconnectEdge,
  type Node,
  type Edge,
  type Connection,
  type NodeTypes,
  type EdgeTypes,
  ConnectionMode,
  Panel,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

// Node Components
import AdapterNode from './nodes/AdapterNode'
import UtilityNode from './nodes/UtilityNode'
import DecisionNode from './nodes/DecisionNode'
import StartNode from './nodes/StartNode'
import EndNode from './nodes/EndNode'
import ParallelSplitNode from './nodes/ParallelSplitNode'
import MessageEndNode from './nodes/MessageEndNode'

// @ts-expect-error useReactFlow is used for flow functionality
const _ = useReactFlow

// Edge Components
import FlowEdge from './edges/FlowEdge'

// Flow Controls
import FlowToolbar from './FlowToolbar'
import NodeSidebar from './NodeSidebar'
import NodePropertiesPanel from './NodePropertiesPanel'

// Layout utilities
import { layoutElementsSmart } from '@/utils/flowLayout'

// Types
export interface FlowData {
  id: string
  name: string
  description: string
  nodes: Node[]
  edges: Edge[]
  active: boolean
}

interface FlowCanvasProps {
  flowData?: FlowData
  onFlowChange?: (flow: FlowData) => void
  onSave?: (flow: FlowData) => void
  onExecute?: (flow: FlowData) => void
  readOnly?: boolean
  className?: string
  isSaving?: boolean
}

// Define node types - xyflow's NodeTypes accepts ComponentType with data: any
const nodeTypes: NodeTypes = {
  start: StartNode,
  end: EndNode,
  messageEnd: MessageEndNode,
  adapter: AdapterNode,
  utility: UtilityNode,
  decision: DecisionNode,
  parallelSplit: ParallelSplitNode,
}

// Define edge types
const edgeTypes: EdgeTypes = {
  flow: FlowEdge,
}

// Initial nodes for new flows - BPMN 2.0 layout
const initialNodes: Node[] = [
  {
    id: 'start-event-1',
    type: 'start',
    position: { x: 100, y: 120 },
    data: { 
      label: 'Start',
      eventType: 'message',
      inboundAdapter: '',
      showDeleteButton: false
    },
  },
  {
    id: 'end-event-1',
    type: 'end',
    position: { x: 400, y: 120 },
    data: { 
      label: 'End',
      eventType: 'message',
      adapterType: 'end-process',
      adapterId: '',
      showDeleteButton: false
    },
  },
]

// Edge color constant - light blue for dark theme
const EDGE_COLOR = '#3b82f6'
const EDGE_COLOR_SELECTED = '#60a5fa'

const initialEdges: Edge[] = [
  {
    id: 'flow-start-to-end',
    source: 'start-event-1',
    target: 'end-event-1',
    type: 'flow',
    markerEnd: { type: 'arrowclosed', color: EDGE_COLOR },
    style: {
      stroke: EDGE_COLOR,
      strokeWidth: 1.5,
    },
  },
]

const FlowCanvas: React.FC<FlowCanvasProps> = ({
  flowData,
  onFlowChange,
  onSave,
  onExecute,
  readOnly = false,
  className = '',
  isSaving = false,
}) => {
  const [nodes, setNodes, onNodesChange] = useNodesState(
    flowData?.nodes?.length ? flowData.nodes : initialNodes
  )
  const [edges, setEdges, onEdgesChange] = useEdgesState(
    flowData?.edges?.length ? flowData.edges : initialEdges
  )

  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const [selectedEdge, setSelectedEdge] = useState<Edge | null>(null)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [propertiesPanelOpen, setPropertiesPanelOpen] = useState(false)
  const [nodeWithDeleteButton, setNodeWithDeleteButton] = useState<string | null>(null)
  const reactFlowWrapper = useRef<HTMLDivElement>(null)
  const [reactFlowInstance, setReactFlowInstance] = useState<any>(null)

  // Validate connections to ensure proper flow direction
  const isValidConnection = useCallback((edge: Connection | Edge) => {
    const sourceNode = nodes.find(node => node.id === edge.source)
    const targetNode = nodes.find(node => node.id === edge.target)
    
    if (!sourceNode || !targetNode) return false
    
    // Prevent self-connections
    if (edge.source === edge.target) return false
    
    // Sender adapters can connect to start nodes
    if (sourceNode.type === 'adapter' && sourceNode.data?.direction === 'SENDER') {
      return targetNode.type === 'start'
    }
    
    // Start nodes can connect to utility, decision, parallelSplit, end, or messageEnd nodes
    if (sourceNode.type === 'start') {
      return targetNode.type === 'utility' || targetNode.type === 'decision' || targetNode.type === 'parallelSplit' || targetNode.type === 'end' || targetNode.type === 'messageEnd'
    }
    
    // End nodes can connect to receiver adapters
    if (sourceNode.type === 'end') {
      return targetNode.type === 'adapter' && targetNode.data?.direction === 'RECEIVER'
    }
    
    // Message end nodes can connect to receiver adapters
    if (sourceNode.type === 'messageEnd') {
      return targetNode.type === 'adapter' && targetNode.data?.direction === 'RECEIVER'
    }
    
    // Utility, decision, and parallelSplit nodes can connect to other utilities, decisions, parallelSplits, end, or messageEnd nodes
    if (sourceNode.type === 'utility' || sourceNode.type === 'decision' || sourceNode.type === 'parallelSplit') {
      return targetNode.type === 'utility' || targetNode.type === 'decision' || targetNode.type === 'parallelSplit' || targetNode.type === 'end' || targetNode.type === 'messageEnd'
    }
    
    // Receiver adapters should not connect to anything (they are final output points)
    if (sourceNode.type === 'adapter' && sourceNode.data?.direction === 'RECEIVER') {
      return false
    }
    
    // Allow other standard connections
    return true
  }, [nodes])

  // Handle connection creation
  const onConnect = useCallback(
    (params: Connection) => {
      if (isValidConnection(params)) {
        const edge = {
          ...params,
          type: 'flow',
          markerEnd: { type: 'arrowclosed', color: EDGE_COLOR },
          style: { stroke: EDGE_COLOR, strokeWidth: 2, strokeDasharray: 'none' },
        }
        setEdges((eds) => addEdge(edge, eds))
      }
    },
    [setEdges, isValidConnection]
  )

  // Handle edge reconnection (drag arrowhead to new node)
  const onReconnect = useCallback(
    (oldEdge: Edge, newConnection: Connection) => {
      if (isValidConnection(newConnection)) {
        setEdges((eds) => reconnectEdge(oldEdge, newConnection, eds))
      }
    },
    [setEdges, isValidConnection]
  )

  // Handle node selection
  const onNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      if (!readOnly) {
        // System nodes (start, end, messageEnd) should not be configurable
        const isSystemNode = ['start', 'end', 'messageEnd'].includes(node.type || '')
        
        if (isSystemNode) {
          // Clear any selections when clicking system nodes
          setSelectedNode(null)
          setSelectedEdge(null)
          setPropertiesPanelOpen(false)
          setNodeWithDeleteButton(null)
        } else {
          setSelectedNode(node)
          setSelectedEdge(null) // Clear edge selection when node is selected
          setPropertiesPanelOpen(true)
          setSidebarOpen(false) // Close node sidebar when properties panel opens
          
          // Show delete button for 3 seconds (like Flow Bridge)
          setNodeWithDeleteButton(node.id)
          setTimeout(() => setNodeWithDeleteButton(null), 3000)
        }
      }
    },
    [readOnly]
  )

  // Handle edge selection
  const onEdgeClick = useCallback(
    (_event: React.MouseEvent, edge: Edge) => {
      if (!readOnly) {
        setSelectedEdge(edge)
        setSelectedNode(null) // Clear node selection when edge is selected
        setNodeWithDeleteButton(null) // Clear node delete button
        setPropertiesPanelOpen(false) // Close properties panel for edges
        setSidebarOpen(false)
      }
    },
    [readOnly]
  )

  // Handle canvas click (clear selections when clicking empty space)
  const onPaneClick = useCallback(() => {
    if (!readOnly) {
      setSelectedNode(null)
      setSelectedEdge(null)
      setNodeWithDeleteButton(null)
      setPropertiesPanelOpen(false)
    }
  }, [readOnly])


  // Handle drag over for node drop
  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  // Handle node drop from sidebar
  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault()

      if (!reactFlowInstance) return

      try {
        const dropData = JSON.parse(event.dataTransfer.getData('application/reactflow'))
        
        // Use screenToFlowPosition to get exact drop position accounting for zoom/pan
        const position = reactFlowInstance.screenToFlowPosition({
          x: event.clientX,
          y: event.clientY,
        })

        const newNode: Node = {
          id: `${dropData.nodeType}-${Date.now()}`,
          type: dropData.nodeType,
          position,
          data: dropData.nodeData,
        }

        setNodes((nds) => nds.concat(newNode))
      } catch (error) {
        console.error('Error parsing dropped node data:', error)
      }
    },
    [setNodes, reactFlowInstance]
  )

  // Delete selected node/edge
  const onDeleteSelected = useCallback(() => {
    if (selectedNode) {
      // Prevent deletion of system nodes
      const isSystemNode = ['start', 'end', 'messageEnd'].includes(selectedNode.type || '')
      if (isSystemNode) {
        console.warn('System nodes cannot be deleted')
        return
      }
      
      setNodes((nds) => nds.filter((node) => node.id !== selectedNode.id))
      setEdges((eds) => 
        eds.filter((edge) => 
          edge.source !== selectedNode.id && edge.target !== selectedNode.id
        )
      )
      setSelectedNode(null)
    } else if (selectedEdge) {
      setEdges((eds) => eds.filter((edge) => edge.id !== selectedEdge.id))
      setSelectedEdge(null)
    }
  }, [selectedNode, selectedEdge, setNodes, setEdges])

  // Clear all nodes and edges
  const onClear = useCallback(() => {
    setNodes(initialNodes)
    setEdges(initialEdges)
    setSelectedNode(null)
  }, [setNodes, setEdges])

  // Save flow
  const handleSave = useCallback(() => {
    const currentFlow: FlowData = {
      id: flowData?.id || `flow-${Date.now()}`,
      name: flowData?.name || 'Untitled Flow',
      description: flowData?.description || '',
      nodes,
      edges,
      active: flowData?.active || true,
    }
    
    onSave?.(currentFlow)
    onFlowChange?.(currentFlow)
  }, [flowData, nodes, edges, onSave, onFlowChange])

  // Execute flow
  const handleExecute = useCallback(() => {
    const currentFlow: FlowData = {
      id: flowData?.id || `flow-${Date.now()}`,
      name: flowData?.name || 'Untitled Flow',
      description: flowData?.description || '',
      nodes,
      edges,
      active: flowData?.active || true,
    }
    
    onExecute?.(currentFlow)
  }, [flowData, nodes, edges, onExecute])

  // Handle direct node addition from sidebar
  const handleNodeAdd = useCallback(
    (nodeType: string, nodeData: any) => {
      // Position all new nodes at the same fixed central location
      const fixedPosition = { x: 250, y: 200 }

      const newNode: Node = {
        id: `${nodeType}-${Date.now()}`,
        type: nodeType,
        position: fixedPosition,
        data: nodeData,
      }

      setNodes((nds) => nds.concat(newNode))
    },
    [setNodes]
  )

  // Handle node property updates
  const handleNodeUpdate = useCallback(
    (nodeId: string, updates: any) => {
      setNodes((nds) =>
        nds.map((node) =>
          node.id === nodeId
            ? { ...node, data: { ...node.data, ...updates } }
            : node
        )
      )
      setSelectedNode(null)
      setPropertiesPanelOpen(false)
    },
    [setNodes]
  )

  // Handle auto-layout
  const handleAutoLayout = useCallback(() => {
    const { nodes: layoutedNodes, edges: layoutedEdges } = layoutElementsSmart(nodes, edges)
    setNodes(layoutedNodes)
    setEdges(layoutedEdges)
  }, [nodes, edges, setNodes, setEdges])

  // Close properties panel
  const handleClosePropertiesPanel = useCallback(() => {
    setSelectedNode(null)
    setPropertiesPanelOpen(false)
  }, [])

  // Handle keyboard shortcuts
  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (!readOnly && event.key === 'Delete' && (selectedNode || selectedEdge)) {
        event.preventDefault()
        onDeleteSelected()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [selectedNode, selectedEdge, onDeleteSelected, readOnly])

  // Auto-save on changes
  React.useEffect(() => {
    if (onFlowChange) {
      const currentFlow: FlowData = {
        id: flowData?.id || `flow-${Date.now()}`,
        name: flowData?.name || 'Untitled Flow',
        description: flowData?.description || '',
        nodes,
        edges,
        active: flowData?.active || true,
      }
      
      onFlowChange(currentFlow)
    }
  }, [nodes, edges, flowData, onFlowChange])

  return (
    <div className={`flex h-full ${className}`}>
      {/* Node Sidebar */}
      {!readOnly && sidebarOpen && (
        <NodeSidebar
          onNodeAdd={handleNodeAdd}
        />
      )}

      {/* Node Properties Panel */}
      {!readOnly && propertiesPanelOpen && (
        <NodePropertiesPanel
          selectedNode={selectedNode}
          onNodeUpdate={handleNodeUpdate}
          onClose={handleClosePropertiesPanel}
        />
      )}

      {/* Flow Canvas */}
      <div className="flex-1 relative" ref={reactFlowWrapper}>
        <ReactFlow
          nodes={nodes.map(node => ({
            ...node,
            data: {
              ...node.data,
              showDeleteButton: nodeWithDeleteButton === node.id
            }
          }))}
          edges={edges.map(edge => ({
            ...edge,
            style: {
              ...edge.style,
              stroke: selectedEdge?.id === edge.id ? EDGE_COLOR_SELECTED : edge.style?.stroke || EDGE_COLOR,
              strokeWidth: selectedEdge?.id === edge.id ? 3 : edge.style?.strokeWidth || 2,
            }
          }))}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onReconnect={onReconnect}
          isValidConnection={isValidConnection}
          onNodeClick={onNodeClick}
          onEdgeClick={onEdgeClick}
          onPaneClick={onPaneClick}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onInit={setReactFlowInstance}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          connectionMode={ConnectionMode.Strict}
          fitView
          fitViewOptions={{ padding: 0.4, includeHiddenNodes: false, minZoom: 0.8, maxZoom: 1.5 }}
          defaultViewport={{ x: 0, y: 0, zoom: 1 }}
          className="bg-slate-900"
          snapToGrid
          snapGrid={[20, 20]}
          deleteKeyCode="Delete"
          multiSelectionKeyCode="Control"
          defaultEdgeOptions={{
            type: 'flow',
            markerEnd: { type: 'arrowclosed', color: EDGE_COLOR },
            style: { stroke: EDGE_COLOR, strokeWidth: 1.5, strokeDasharray: 'none' },
          }}
        >
          <Background color="#334155" gap={20} size={1} />
          <Controls className="bg-slate-800 border border-slate-700 rounded shadow-lg [&>button]:bg-slate-800 [&>button]:border-slate-700 [&>button]:text-slate-300 [&>button:hover]:bg-slate-700" />
          <MiniMap 
            className="bg-slate-800 border border-slate-700 rounded shadow-lg"
            nodeColor={(node: Node) => {
              switch (node.type) {
                case 'start': return '#1e293b'
                case 'end': return '#1e293b'
                case 'messageEnd': return '#1e293b'
                case 'adapter': return '#1e293b'
                case 'utility': return '#1e293b'
                case 'decision': return '#1e293b'
                case 'parallelSplit': return '#1e293b'
                default: return '#1e293b'
              }
            }}
          />

          {/* Flow Toolbar */}
          {!readOnly && (
            <Panel position="top-left">
              <FlowToolbar
                onSave={handleSave}
                onExecute={handleExecute}
                onClear={onClear}
                onDelete={onDeleteSelected}
                onAutoLayout={handleAutoLayout}
                onToggleNodeSidebar={() => {
                  setSidebarOpen(!sidebarOpen)
                  if (!sidebarOpen) {
                    setPropertiesPanelOpen(false)
                    setSelectedNode(null)
                  }
                }}
                isNodeSidebarVisible={sidebarOpen}
                canSave={true}
                canExecute={nodes.length > 0}
                canDelete={!!selectedNode || !!selectedEdge}
                isSaving={isSaving}
              />
            </Panel>
          )}

          {/* Flow Info */}
          <Panel position="top-right" className="bg-card border border-border rounded-md p-2">
            <div className="text-sm space-y-1">
              <div className="font-medium text-foreground">
                {flowData?.name || 'Untitled Flow'}
              </div>
              {flowData?.description && (
                <div className="text-muted-foreground text-xs">
                  {flowData.description}
                </div>
              )}
              <div className="text-xs text-muted-foreground">
                {nodes.length} nodes, {edges.length} connections
              </div>
            </div>
          </Panel>
        </ReactFlow>
      </div>
    </div>
  )
}

export default FlowCanvas