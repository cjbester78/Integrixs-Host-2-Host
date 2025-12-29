import React from 'react'
import {
  EdgeProps,
  getBezierPath,
  EdgeLabelRenderer,
  BaseEdge,
  useReactFlow,
  type Edge,
} from '@xyflow/react'
import { X } from 'lucide-react'

export interface FlowEdgeData extends Record<string, unknown> {
  label?: string
  condition?: 'true' | 'false' | 'default'
  animated?: boolean
  onDelete?: (edgeId: string) => void
}

export type FlowEdgeType = Edge<FlowEdgeData, 'flow'>

const FlowEdge: React.FC<EdgeProps<FlowEdgeType>> = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  selected,
  markerEnd,
}) => {
  const { setEdges } = useReactFlow()
  const edgeData = data || {}
  const [isHovered, setIsHovered] = React.useState(false)
  
  const handleDelete = () => {
    setEdges((edges) => edges.filter((edge) => edge.id !== id))
  }
  
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  })

  const getEdgeColor = () => {
    switch (edgeData.condition) {
      case 'true':
        return 'hsl(142, 76%, 36%)' // success green
      case 'false':
        return 'hsl(0, 84%, 60%)' // destructive red
      default:
        return 'hsl(217, 91%, 60%)' // primary blue
    }
  }

  const getEdgeWidth = () => {
    return selected ? 3 : 2
  }

  return (
    <>
      <BaseEdge
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          stroke: getEdgeColor(),
          strokeWidth: getEdgeWidth(),
          strokeDasharray: 'none',
        }}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
      />
      
      <EdgeLabelRenderer>
        <div
          style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            pointerEvents: 'all',
          }}
          className="nodrag nopan"
        >
        <div className="flex items-center">
            {(selected || isHovered) && (
              <button
                onClick={handleDelete}
                className={`w-6 h-6 bg-destructive text-white rounded-full flex items-center justify-center hover:bg-destructive/80 transition-all shadow-md z-10 ${
                  selected ? 'opacity-100 scale-110' : 'opacity-90 hover:opacity-100 hover:scale-105'
                }`}
                title="Delete connection"
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
        </div>
      </EdgeLabelRenderer>
    </>
  )
}

export default FlowEdge