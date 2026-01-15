/**
 * Dependency Graph Visualization Component
 * 
 * Advanced dependency graph visualization using SVG with comprehensive OOP design patterns.
 * Implements Graph Visualization Pattern, Observer Pattern, and Strategy Pattern for
 * flexible and maintainable dependency graph rendering.
 * 
 * @author Claude Code
 * @since Package Management Frontend V2.0
 */

import React, { useMemo, useCallback, useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Package,
  Settings,
  Workflow,
  ExternalLink,
  Zap,
  AlertCircle,
  CheckCircle,
  Clock,
  Search,
  Filter,
  Maximize2,
  Minimize2,
  RotateCcw,
  Download
} from 'lucide-react'

import { PackageAnalyticsService } from '@/services/packageAnalyticsService'
import type {
  PackageDependencyGraph,
  DependencyNode,
  DependencyEdge,
  DependencyStatistics
} from '@/services/packageAnalyticsService'

// UI Components
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import { cn } from '@/lib/utils'

/**
 * Props interface following interface segregation principle
 */
interface DependencyGraphVisualizationProps {
  packageId: string
  className?: string
  height?: number
  showControls?: boolean
}

/**
 * Node position interface for graph layout calculations
 */
interface NodePosition {
  id: string
  x: number
  y: number
  level: number
}

/**
 * Graph layout configuration following configuration pattern
 */
interface GraphLayoutConfig {
  width: number
  height: number
  nodeRadius: number
  levelHeight: number
  nodeSpacing: number
  marginTop: number
  marginLeft: number
}

/**
 * Graph layout calculator following strategy pattern
 */
class GraphLayoutCalculator {
  private config: GraphLayoutConfig

  constructor(config: GraphLayoutConfig) {
    this.config = config
  }

  /**
   * Calculates node positions using hierarchical layout algorithm
   */
  calculateNodePositions(nodes: DependencyNode[]): NodePosition[] {
    // Group nodes by level for hierarchical layout
    const levelGroups = this.groupNodesByLevel(nodes)
    const positions: NodePosition[] = []

    levelGroups.forEach((levelNodes, level) => {
      const yPosition = this.config.marginTop + (level * this.config.levelHeight)
      const totalWidth = levelNodes.length * this.config.nodeSpacing
      const startX = (this.config.width - totalWidth) / 2 + this.config.marginLeft

      levelNodes.forEach((node, index) => {
        positions.push({
          id: node.id,
          x: startX + (index * this.config.nodeSpacing),
          y: yPosition,
          level: level
        })
      })
    })

    return positions
  }

  /**
   * Groups nodes by their hierarchy level
   */
  private groupNodesByLevel(nodes: DependencyNode[]): Map<number, DependencyNode[]> {
    const levelGroups = new Map<number, DependencyNode[]>()
    
    nodes.forEach(node => {
      const level = node.level
      if (!levelGroups.has(level)) {
        levelGroups.set(level, [])
      }
      levelGroups.get(level)!.push(node)
    })

    return levelGroups
  }

  /**
   * Calculates edge path for connections between nodes
   */
  calculateEdgePath(source: NodePosition, target: NodePosition): string {
    const controlPointOffset = Math.abs(target.y - source.y) * 0.5
    
    return `M ${source.x} ${source.y} 
            C ${source.x} ${source.y + controlPointOffset}, 
              ${target.x} ${target.y - controlPointOffset}, 
              ${target.x} ${target.y}`
  }
}

/**
 * Node renderer following factory pattern
 */
class NodeRendererFactory {
  /**
   * Creates appropriate node renderer based on node type
   */
  static createRenderer(type: string): {
    icon: React.ElementType
    color: string
    bgColor: string
  } {
    switch (type) {
      case 'adapter':
        return {
          icon: Settings,
          color: 'text-blue-600',
          bgColor: 'bg-blue-50'
        }
      case 'flow':
        return {
          icon: Workflow,
          color: 'text-green-600',
          bgColor: 'bg-green-50'
        }
      case 'external':
        return {
          icon: ExternalLink,
          color: 'text-purple-600',
          bgColor: 'bg-purple-50'
        }
      default:
        return {
          icon: Package,
          color: 'text-gray-600',
          bgColor: 'bg-gray-50'
        }
    }
  }

  /**
   * Gets status indicator properties based on node status
   */
  static getStatusIndicator(status: string): {
    icon: React.ElementType
    color: string
  } {
    switch (status.toLowerCase()) {
      case 'active':
      case 'deployed':
        return { icon: CheckCircle, color: 'text-green-500' }
      case 'error':
      case 'failed':
        return { icon: AlertCircle, color: 'text-red-500' }
      case 'inactive':
      case 'draft':
        return { icon: Clock, color: 'text-gray-500' }
      default:
        return { icon: AlertCircle, color: 'text-yellow-500' }
    }
  }
}

/**
 * Edge renderer following factory pattern
 */
class EdgeRendererFactory {
  /**
   * Gets edge styling based on edge type
   */
  static getEdgeStyle(type: string): {
    stroke: string
    strokeWidth: number
    strokeDasharray?: string
  } {
    switch (type) {
      case 'data_flow':
        return {
          stroke: '#3b82f6',
          strokeWidth: 2
        }
      case 'dependency':
        return {
          stroke: '#10b981',
          strokeWidth: 2,
          strokeDasharray: '5,5'
        }
      case 'schedule':
        return {
          stroke: '#f59e0b',
          strokeWidth: 1,
          strokeDasharray: '3,3'
        }
      default:
        return {
          stroke: '#6b7280',
          strokeWidth: 1
        }
    }
  }
}

/**
 * Graph filter service following service pattern
 */
class GraphFilterService {
  /**
   * Filters nodes based on search criteria
   */
  static filterNodes(
    nodes: DependencyNode[], 
    searchTerm: string, 
    typeFilter: string
  ): DependencyNode[] {
    let filtered = nodes

    if (searchTerm) {
      filtered = filtered.filter(node => 
        node.name.toLowerCase().includes(searchTerm.toLowerCase())
      )
    }

    if (typeFilter && typeFilter !== 'all') {
      filtered = filtered.filter(node => node.type === typeFilter)
    }

    return filtered
  }

  /**
   * Filters edges based on filtered nodes
   */
  static filterEdges(
    edges: DependencyEdge[], 
    filteredNodes: DependencyNode[]
  ): DependencyEdge[] {
    const nodeIds = new Set(filteredNodes.map(node => node.id))
    
    return edges.filter(edge => 
      nodeIds.has(edge.source) && nodeIds.has(edge.target)
    )
  }
}

/**
 * Main Dependency Graph Visualization component following composite pattern
 */
const DependencyGraphVisualization: React.FC<DependencyGraphVisualizationProps> = ({
  packageId,
  className,
  height = 600,
  showControls = true
}) => {
  // State management following encapsulation principle
  const [searchTerm, setSearchTerm] = useState('')
  const [typeFilter, setTypeFilter] = useState<string>('all')
  const [selectedNode, setSelectedNode] = useState<string | null>(null)
  const [isFullscreen, setIsFullscreen] = useState(false)

  // Fetch dependency data with analytics service
  const { data: dependencyResult, isLoading, refetch } = useQuery({
    queryKey: ['package-dependencies', packageId],
    queryFn: () => PackageAnalyticsService.getPackageDependencies(packageId),
    refetchInterval: 60000, // Refresh every minute
    staleTime: 30000
  })

  const dependencyData = dependencyResult?.data
  const nodes = dependencyData?.nodes || []
  const edges = dependencyData?.edges || []
  const statistics = dependencyData?.statistics

  // Graph layout configuration following configuration pattern
  const layoutConfig: GraphLayoutConfig = useMemo(() => ({
    width: isFullscreen ? 1200 : 800,
    height: height,
    nodeRadius: 40,
    levelHeight: 120,
    nodeSpacing: 150,
    marginTop: 60,
    marginLeft: 60
  }), [height, isFullscreen])

  // Layout calculator instance following strategy pattern
  const layoutCalculator = useMemo(() => 
    new GraphLayoutCalculator(layoutConfig), [layoutConfig]
  )

  // Filter nodes and edges using service pattern
  const { filteredNodes, filteredEdges } = useMemo(() => {
    const filteredNodes = GraphFilterService.filterNodes(nodes, searchTerm, typeFilter)
    const filteredEdges = GraphFilterService.filterEdges(edges, filteredNodes)
    
    return { filteredNodes, filteredEdges }
  }, [nodes, edges, searchTerm, typeFilter])

  // Calculate node positions using layout calculator
  const nodePositions = useMemo(() => 
    layoutCalculator.calculateNodePositions(filteredNodes), 
    [layoutCalculator, filteredNodes]
  )

  // Create position lookup map for efficient edge rendering
  const positionMap = useMemo(() => {
    const map = new Map<string, NodePosition>()
    nodePositions.forEach(pos => map.set(pos.id, pos))
    return map
  }, [nodePositions])

  /**
   * Node click handler following event handling pattern
   */
  const handleNodeClick = useCallback((nodeId: string) => {
    setSelectedNode(prev => prev === nodeId ? null : nodeId)
  }, [])

  /**
   * Export graph as SVG following command pattern
   */
  const exportGraph = useCallback(() => {
    // Implementation for SVG export would go here
    console.log('Exporting dependency graph...')
  }, [])

  /**
   * Reset graph view following command pattern
   */
  const resetView = useCallback(() => {
    setSearchTerm('')
    setTypeFilter('all')
    setSelectedNode(null)
  }, [])

  if (isLoading) {
    return (
      <Card className={className}>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Zap className="h-5 w-5" />
            Dependency Graph
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center" style={{ height }}>
            <div className="flex items-center space-x-3">
              <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
              <span className="text-muted-foreground">Loading dependency graph...</span>
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  if (!dependencyData || nodes.length === 0) {
    return (
      <Card className={className}>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Zap className="h-5 w-5" />
            Dependency Graph
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center" style={{ height }}>
            <div className="text-center">
              <Package className="h-12 w-12 text-muted-foreground mx-auto mb-4 opacity-50" />
              <p className="text-muted-foreground">No dependencies found</p>
              <p className="text-sm text-muted-foreground mt-1">
                Create adapters and flows to see dependency relationships
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className={cn(className, isFullscreen && "fixed inset-4 z-50")}>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2">
            <Zap className="h-5 w-5" />
            Dependency Graph
            {statistics && (
              <Badge variant="secondary" className="ml-2">
                {statistics.totalNodes} nodes, {statistics.totalEdges} connections
              </Badge>
            )}
          </CardTitle>
          
          {showControls && (
            <div className="flex items-center gap-2">
              <Button 
                variant="outline" 
                size="sm"
                onClick={() => setIsFullscreen(!isFullscreen)}
              >
                {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
              </Button>
              <Button 
                variant="outline" 
                size="sm"
                onClick={resetView}
              >
                <RotateCcw className="h-4 w-4" />
              </Button>
              <Button 
                variant="outline" 
                size="sm"
                onClick={exportGraph}
                disabled
              >
                <Download className="h-4 w-4" />
              </Button>
            </div>
          )}
        </div>

        {showControls && (
          <div className="flex items-center gap-4 mt-4">
            <div className="flex-1 max-w-sm">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search nodes..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
            </div>
            
            <Select value={typeFilter} onValueChange={setTypeFilter}>
              <SelectTrigger className="w-40">
                <Filter className="h-4 w-4 mr-2" />
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Types</SelectItem>
                <SelectItem value="adapter">Adapters</SelectItem>
                <SelectItem value="flow">Flows</SelectItem>
                <SelectItem value="external">External</SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}
      </CardHeader>

      <CardContent>
        <div className="relative overflow-auto border rounded-lg">
          <svg
            width={layoutConfig.width}
            height={layoutConfig.height}
            className="bg-gray-50"
          >
            {/* Render edges */}
            <g className="edges">
              {filteredEdges.map((edge, index) => {
                const sourcePos = positionMap.get(edge.source)
                const targetPos = positionMap.get(edge.target)
                
                if (!sourcePos || !targetPos) return null
                
                const style = EdgeRendererFactory.getEdgeStyle(edge.type)
                const path = layoutCalculator.calculateEdgePath(sourcePos, targetPos)
                
                return (
                  <path
                    key={`edge-${index}`}
                    d={path}
                    fill="none"
                    stroke={style.stroke}
                    strokeWidth={style.strokeWidth}
                    strokeDasharray={style.strokeDasharray}
                    className="transition-all duration-200"
                  />
                )
              })}
            </g>

            {/* Render nodes */}
            <g className="nodes">
              {nodePositions.map((position) => {
                const node = filteredNodes.find(n => n.id === position.id)
                if (!node) return null
                
                const renderer = NodeRendererFactory.createRenderer(node.type)
                const statusIndicator = NodeRendererFactory.getStatusIndicator(node.status)
                const isSelected = selectedNode === node.id
                
                return (
                  <g
                    key={node.id}
                    transform={`translate(${position.x}, ${position.y})`}
                    className="cursor-pointer"
                    onClick={() => handleNodeClick(node.id)}
                  >
                    {/* Node background */}
                    <circle
                      r={layoutConfig.nodeRadius}
                      className={cn(
                        "transition-all duration-200",
                        renderer.bgColor,
                        isSelected ? 'stroke-primary stroke-2' : 'stroke-gray-300 hover:stroke-gray-400'
                      )}
                      fill="currentColor"
                    />
                    
                    {/* Node icon */}
                    <foreignObject
                      x={-12}
                      y={-12}
                      width={24}
                      height={24}
                    >
                      <renderer.icon className={cn("h-6 w-6", renderer.color)} />
                    </foreignObject>
                    
                    {/* Status indicator */}
                    <foreignObject
                      x={layoutConfig.nodeRadius - 16}
                      y={-layoutConfig.nodeRadius + 4}
                      width={12}
                      height={12}
                    >
                      <statusIndicator.icon className={cn("h-3 w-3", statusIndicator.color)} />
                    </foreignObject>
                    
                    {/* Node label */}
                    <text
                      y={layoutConfig.nodeRadius + 20}
                      textAnchor="middle"
                      className="text-xs font-medium fill-gray-700"
                    >
                      {node.name.length > 12 ? `${node.name.substring(0, 12)}...` : node.name}
                    </text>
                  </g>
                )
              })}
            </g>
          </svg>
        </div>

        {/* Statistics panel */}
        {statistics && (
          <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="text-center p-3 bg-muted rounded">
              <p className="text-lg font-semibold">{statistics.totalNodes}</p>
              <p className="text-xs text-muted-foreground">Total Nodes</p>
            </div>
            <div className="text-center p-3 bg-muted rounded">
              <p className="text-lg font-semibold">{statistics.totalEdges}</p>
              <p className="text-xs text-muted-foreground">Connections</p>
            </div>
            <div className="text-center p-3 bg-muted rounded">
              <p className="text-lg font-semibold">{statistics.maxDepth}</p>
              <p className="text-xs text-muted-foreground">Max Depth</p>
            </div>
            <div className="text-center p-3 bg-muted rounded">
              <p className={cn(
                "text-lg font-semibold",
                statistics.isolatedNodes > 0 ? 'text-yellow-600' : 'text-green-600'
              )}>
                {statistics.isolatedNodes}
              </p>
              <p className="text-xs text-muted-foreground">Isolated</p>
            </div>
          </div>
        )}

        {/* Selected node details */}
        {selectedNode && (
          <div className="mt-4 p-4 bg-muted rounded-lg">
            {(() => {
              const node = filteredNodes.find(n => n.id === selectedNode)
              if (!node) return null
              
              const connectedEdges = filteredEdges.filter(e => 
                e.source === selectedNode || e.target === selectedNode
              )
              
              return (
                <div>
                  <h4 className="font-medium mb-2">{node.name}</h4>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">Type:</span>
                      <span className="ml-2 font-medium">{node.type}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Status:</span>
                      <span className="ml-2 font-medium">{node.status}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Level:</span>
                      <span className="ml-2 font-medium">{node.level}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Connections:</span>
                      <span className="ml-2 font-medium">{connectedEdges.length}</span>
                    </div>
                  </div>
                </div>
              )
            })()}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export default DependencyGraphVisualization