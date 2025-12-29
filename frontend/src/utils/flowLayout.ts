import dagre from 'dagre'
import { type Node, type Edge } from '@xyflow/react'

// Layout configuration for different flow types
export interface LayoutConfig {
  direction: 'TB' | 'LR' | 'BT' | 'RL'
  nodeWidth: number
  nodeHeight: number
  rankSeparation: number
  nodeSeparation: number
}

// Default layout configuration optimized for integration flows
const DEFAULT_LAYOUT_CONFIG: LayoutConfig = {
  direction: 'LR', // Left to Right for integration pipeline flows
  nodeWidth: 180,  // Standard node width
  nodeHeight: 80,  // Standard node height
  rankSeparation: 120, // Horizontal spacing between ranks
  nodeSeparation: 80,  // Vertical spacing between nodes
}

// Node type specific dimensions
const NODE_DIMENSIONS: Record<string, { width: number; height: number }> = {
  start: { width: 120, height: 60 },
  end: { width: 120, height: 60 },
  messageEnd: { width: 140, height: 60 },
  adapter: { width: 200, height: 100 },
  utility: { width: 160, height: 80 },
  decision: { width: 140, height: 80 },
  parallelSplit: { width: 160, height: 80 },
}

/**
 * Apply Dagre layout algorithm to React Flow nodes and edges
 */
export function layoutElements(
  nodes: Node[],
  edges: Edge[],
  config: Partial<LayoutConfig> = {}
): { nodes: Node[]; edges: Edge[] } {
  const layoutConfig = { ...DEFAULT_LAYOUT_CONFIG, ...config }
  
  // Create a new directed graph
  const graph = new dagre.graphlib.Graph()
  
  // Set graph configuration
  graph.setDefaultEdgeLabel(() => ({}))
  graph.setGraph({
    rankdir: layoutConfig.direction,
    ranksep: layoutConfig.rankSeparation,
    nodesep: layoutConfig.nodeSeparation,
    marginx: 40,
    marginy: 40,
  })
  
  // Add nodes to graph with type-specific dimensions
  nodes.forEach((node) => {
    const dimensions = NODE_DIMENSIONS[node.type || 'default'] || {
      width: layoutConfig.nodeWidth,
      height: layoutConfig.nodeHeight,
    }
    
    graph.setNode(node.id, {
      width: dimensions.width,
      height: dimensions.height,
    })
  })
  
  // Add edges to graph
  edges.forEach((edge) => {
    graph.setEdge(edge.source, edge.target)
  })
  
  // Run the layout algorithm
  dagre.layout(graph)
  
  // Apply calculated positions to nodes
  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = graph.node(node.id)
    
    return {
      ...node,
      position: {
        x: nodeWithPosition.x - nodeWithPosition.width / 2,
        y: nodeWithPosition.y - nodeWithPosition.height / 2,
      },
      // Add computed width/height to node data for consistent rendering
      data: {
        ...node.data,
        computedWidth: nodeWithPosition.width,
        computedHeight: nodeWithPosition.height,
      },
    }
  })
  
  return {
    nodes: layoutedNodes,
    edges, // Edges don't need position updates
  }
}

/**
 * Apply vertical layout optimized for simple linear flows
 */
export function layoutElementsVertical(
  nodes: Node[],
  edges: Edge[]
): { nodes: Node[]; edges: Edge[] } {
  return layoutElements(nodes, edges, {
    direction: 'TB',
    rankSeparation: 100,
    nodeSeparation: 60,
  })
}

/**
 * Apply horizontal layout optimized for integration pipeline flows
 */
export function layoutElementsHorizontal(
  nodes: Node[],
  edges: Edge[]
): { nodes: Node[]; edges: Edge[] } {
  return layoutElements(nodes, edges, {
    direction: 'LR',
    rankSeparation: 120,
    nodeSeparation: 80,
  })
}

/**
 * Apply compact layout for flows with many nodes
 */
export function layoutElementsCompact(
  nodes: Node[],
  edges: Edge[]
): { nodes: Node[]; edges: Edge[] } {
  return layoutElements(nodes, edges, {
    direction: 'LR',
    nodeWidth: 140,
    nodeHeight: 60,
    rankSeparation: 80,
    nodeSeparation: 40,
  })
}

/**
 * Smart layout selection based on flow characteristics
 */
export function layoutElementsSmart(
  nodes: Node[],
  edges: Edge[]
): { nodes: Node[]; edges: Edge[] } {
  const nodeCount = nodes.length
  const edgeCount = edges.length
  
  // For simple linear flows (few branches), use horizontal layout
  if (nodeCount <= 6 && edgeCount <= nodeCount + 2) {
    return layoutElementsHorizontal(nodes, edges)
  }
  
  // For complex flows with many nodes, use compact layout
  if (nodeCount > 10) {
    return layoutElementsCompact(nodes, edges)
  }
  
  // Default to horizontal layout for integration flows
  return layoutElementsHorizontal(nodes, edges)
}

/**
 * Get layout statistics for the current flow
 */
export function getLayoutStats(nodes: Node[], edges: Edge[]): {
  nodeCount: number
  edgeCount: number
  hasAdapters: boolean
  hasDecisions: boolean
  maxDepth: number
  suggestedLayout: string
} {
  const nodeCount = nodes.length
  const edgeCount = edges.length
  const hasAdapters = nodes.some(node => node.type === 'adapter')
  const hasDecisions = nodes.some(node => node.type === 'decision')
  
  // Calculate max depth (simplified - actual implementation would need proper graph traversal)
  const maxDepth = Math.ceil(Math.sqrt(nodeCount))
  
  let suggestedLayout = 'horizontal'
  if (nodeCount > 10) {
    suggestedLayout = 'compact'
  } else if (hasDecisions || maxDepth > 3) {
    suggestedLayout = 'smart'
  }
  
  return {
    nodeCount,
    edgeCount,
    hasAdapters,
    hasDecisions,
    maxDepth,
    suggestedLayout,
  }
}