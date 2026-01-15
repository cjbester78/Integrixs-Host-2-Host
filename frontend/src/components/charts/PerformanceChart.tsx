/**
 * Performance Chart Component
 * 
 * Advanced performance visualization component using SVG charts with comprehensive
 * OOP design patterns. Implements Chart Strategy Pattern, Observer Pattern, and
 * Data Visualization Pattern for flexible and maintainable chart rendering.
 * 
 * @author Claude Code
 * @since Package Management Frontend V2.0
 */

import React, { useMemo, useState } from 'react'
import { 
  TrendingUp, 
  TrendingDown, 
  BarChart3, 
  Activity,
  Clock
} from 'lucide-react'

// UI Components
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

/**
 * Performance data interfaces
 */
export interface TrendData {
  date: string
  executions: number
  successRate: number
  avgTime: number
}

export interface PackagePerformanceData {
  totalExecutions: number
  successRate: number
  avgExecutionTime: number
  failedExecutions: number
  executionTrend: TrendData[]
}

/**
 * Chart configuration interface following configuration pattern
 */
interface ChartConfig {
  width: number
  height: number
  margin: {
    top: number
    right: number
    bottom: number
    left: number
  }
  gridLines: boolean
  showTooltip: boolean
}

/**
 * Chart data point interface for type safety
 */
interface ChartDataPoint {
  x: number
  y: number
  label: string
  value: number
  date: string
}

/**
 * Chart type enumeration following strategy pattern
 */
type ChartType = 'line' | 'bar' | 'area'

/**
 * Metric type for different performance measurements
 */
type MetricType = 'executions' | 'successRate' | 'avgTime'

/**
 * Props interface following interface segregation principle
 */
interface PerformanceChartProps {
  performance?: PackagePerformanceData
  height?: number
  className?: string
  showControls?: boolean
}

/**
 * Chart scale calculator following utility pattern
 */
class ChartScaleCalculator {
  /**
   * Calculates Y-axis scale for given data range
   */
  static calculateYScale(values: number[], height: number, margin: { top: number; bottom: number }): {
    min: number
    max: number
    scale: (value: number) => number
  } {
    const min = Math.min(...values)
    const max = Math.max(...values)
    const padding = (max - min) * 0.1 || 1
    
    const adjustedMin = Math.max(0, min - padding)
    const adjustedMax = max + padding
    
    const scale = (value: number) => {
      const chartHeight = height - margin.top - margin.bottom
      return margin.top + chartHeight * (1 - (value - adjustedMin) / (adjustedMax - adjustedMin))
    }
    
    return { min: adjustedMin, max: adjustedMax, scale }
  }

  /**
   * Calculates X-axis scale for time-based data
   */
  static calculateXScale(dataLength: number, width: number, margin: { left: number; right: number }): {
    scale: (index: number) => number
    step: number
  } {
    const chartWidth = width - margin.left - margin.right
    const step = chartWidth / Math.max(1, dataLength - 1)
    
    const scale = (index: number) => margin.left + (index * step)
    
    return { scale, step }
  }
}

/**
 * Chart path generator following factory pattern
 */
class ChartPathGenerator {
  /**
   * Generates SVG path for line chart
   */
  static generateLinePath(points: ChartDataPoint[]): string {
    if (points.length === 0) return ''
    
    let path = `M ${points[0].x} ${points[0].y}`
    
    for (let i = 1; i < points.length; i++) {
      path += ` L ${points[i].x} ${points[i].y}`
    }
    
    return path
  }

  /**
   * Generates SVG path for area chart
   */
  static generateAreaPath(points: ChartDataPoint[], baselineY: number): string {
    if (points.length === 0) return ''
    
    let path = `M ${points[0].x} ${baselineY}`
    path += ` L ${points[0].x} ${points[0].y}`
    
    for (let i = 1; i < points.length; i++) {
      path += ` L ${points[i].x} ${points[i].y}`
    }
    
    path += ` L ${points[points.length - 1].x} ${baselineY}`
    path += ' Z'
    
    return path
  }
}

/**
 * Data transformer following transformer pattern
 */
class PerformanceDataTransformer {
  /**
   * Transforms trend data for chart visualization
   */
  static transformTrendData(
    trendData: TrendData[], 
    metricType: MetricType,
    xScale: (index: number) => number,
    yScale: (value: number) => number
  ): ChartDataPoint[] {
    return trendData.map((trend, index) => {
      let value: number
      
      switch (metricType) {
        case 'executions':
          value = trend.executions
          break
        case 'successRate':
          value = trend.successRate
          break
        case 'avgTime':
          value = trend.avgTime / 1000 // Convert to seconds
          break
        default:
          value = 0
      }
      
      return {
        x: xScale(index),
        y: yScale(value),
        label: new Date(trend.date).toLocaleDateString(),
        value: value,
        date: trend.date
      }
    })
  }

  /**
   * Gets metric display information
   */
  static getMetricInfo(metricType: MetricType): {
    label: string
    unit: string
    color: string
    icon: React.ElementType
  } {
    switch (metricType) {
      case 'executions':
        return {
          label: 'Executions',
          unit: '',
          color: '#3b82f6',
          icon: Activity
        }
      case 'successRate':
        return {
          label: 'Success Rate',
          unit: '%',
          color: '#10b981',
          icon: TrendingUp
        }
      case 'avgTime':
        return {
          label: 'Avg Time',
          unit: 's',
          color: '#f59e0b',
          icon: Clock
        }
      default:
        return {
          label: 'Metric',
          unit: '',
          color: '#6b7280',
          icon: BarChart3
        }
    }
  }
}

/**
 * Chart grid component following component composition pattern
 */
interface ChartGridProps {
  config: ChartConfig
  yScale: { min: number; max: number; scale: (value: number) => number }
  xScale: { scale: (index: number) => number; step: number }
  dataLength: number
}

const ChartGrid: React.FC<ChartGridProps> = ({ config, yScale, xScale, dataLength }) => {
  if (!config.gridLines) return null
  
  const yGridLines = 5
  const yStep = (yScale.max - yScale.min) / yGridLines
  
  return (
    <g className="chart-grid opacity-20">
      {/* Y-axis grid lines */}
      {Array.from({ length: yGridLines + 1 }).map((_, i) => {
        const value = yScale.min + (i * yStep)
        const y = yScale.scale(value)
        
        return (
          <line
            key={`y-grid-${i}`}
            x1={config.margin.left}
            x2={config.width - config.margin.right}
            y1={y}
            y2={y}
            stroke="#374151"
            strokeWidth="0.5"
          />
        )
      })}
      
      {/* X-axis grid lines */}
      {Array.from({ length: dataLength }).map((_, i) => {
        const x = xScale.scale(i)
        
        return (
          <line
            key={`x-grid-${i}`}
            x1={x}
            x2={x}
            y1={config.margin.top}
            y2={config.height - config.margin.bottom}
            stroke="#374151"
            strokeWidth="0.5"
          />
        )
      })}
    </g>
  )
}

/**
 * Chart tooltip component following component composition pattern
 */
interface ChartTooltipProps {
  point: ChartDataPoint | null
  metricInfo: ReturnType<typeof PerformanceDataTransformer.getMetricInfo>
}

const ChartTooltip: React.FC<ChartTooltipProps> = ({ point, metricInfo }) => {
  if (!point) return null
  
  return (
    <div
      className="absolute bg-background border rounded-lg shadow-lg p-3 pointer-events-none z-10"
      style={{
        left: point.x + 10,
        top: point.y - 10,
        transform: 'translateY(-100%)'
      }}
    >
      <div className="text-sm font-medium">{point.label}</div>
      <div className="text-xs text-muted-foreground">
        {metricInfo.label}: {point.value.toFixed(metricInfo.unit === '%' ? 1 : 0)}{metricInfo.unit}
      </div>
    </div>
  )
}

/**
 * Main Performance Chart component following composite pattern
 */
const PerformanceChart: React.FC<PerformanceChartProps> = ({
  performance,
  height = 300,
  className,
  showControls = true
}) => {
  const [metricType, setMetricType] = useState<MetricType>('executions')
  const [chartType, setChartType] = useState<ChartType>('line')
  const [hoveredPoint, setHoveredPoint] = useState<ChartDataPoint | null>(null)

  // Chart configuration following configuration pattern
  const config: ChartConfig = useMemo(() => ({
    width: 800,
    height: height,
    margin: {
      top: 20,
      right: 20,
      bottom: 40,
      left: 60
    },
    gridLines: true,
    showTooltip: true
  }), [height])

  const trendData = performance?.executionTrend || []
  const metricInfo = PerformanceDataTransformer.getMetricInfo(metricType)

  // Calculate chart data points
  const chartData = useMemo(() => {
    if (trendData.length === 0) return []

    const values = trendData.map((trend: TrendData) => {
      switch (metricType) {
        case 'executions':
          return trend.executions
        case 'successRate':
          return trend.successRate
        case 'avgTime':
          return trend.avgTime / 1000
        default:
          return 0
      }
    })

    const yScale = ChartScaleCalculator.calculateYScale(values, config.height, config.margin)
    const xScale = ChartScaleCalculator.calculateXScale(trendData.length, config.width, config.margin)

    return PerformanceDataTransformer.transformTrendData(trendData, metricType, xScale.scale, yScale.scale)
  }, [trendData, metricType, config])

  // Calculate scales for grid and axes
  const { yScale, xScale } = useMemo(() => {
    if (trendData.length === 0) {
      return { 
        yScale: { min: 0, max: 100, scale: () => config.height / 2 },
        xScale: { scale: () => config.width / 2, step: 0 }
      }
    }

    const values = trendData.map((trend: TrendData) => {
      switch (metricType) {
        case 'executions':
          return trend.executions
        case 'successRate':
          return trend.successRate
        case 'avgTime':
          return trend.avgTime / 1000
        default:
          return 0
      }
    })

    const yScale = ChartScaleCalculator.calculateYScale(values, config.height, config.margin)
    const xScale = ChartScaleCalculator.calculateXScale(trendData.length, config.width, config.margin)

    return { yScale, xScale }
  }, [trendData, metricType, config])

  // Calculate trend direction for the metric
  const trend = useMemo(() => {
    if (chartData.length < 2) return { direction: 'neutral' as const, change: 0 }
    
    const current = chartData[chartData.length - 1].value
    const previous = chartData[chartData.length - 2].value
    
    if (previous === 0) return { direction: 'neutral' as const, change: 0 }
    
    const change = ((current - previous) / previous) * 100
    
    return {
      direction: change > 0 ? 'up' as const : change < 0 ? 'down' as const : 'neutral' as const,
      change: Math.abs(change)
    }
  }, [chartData])

  const renderChart = () => {
    if (chartData.length === 0) {
      return (
        <div className="flex items-center justify-center h-full text-muted-foreground">
          <div className="text-center">
            <BarChart3 className="h-8 w-8 mx-auto mb-2 opacity-50" />
            <p>No performance data available</p>
          </div>
        </div>
      )
    }

    const linePath = ChartPathGenerator.generateLinePath(chartData)
    const areaPath = ChartPathGenerator.generateAreaPath(chartData, config.height - config.margin.bottom)

    return (
      <div className="relative">
        <svg
          width={config.width}
          height={config.height}
          className="overflow-visible"
          onMouseLeave={() => setHoveredPoint(null)}
        >
          <ChartGrid 
            config={config} 
            yScale={yScale} 
            xScale={xScale} 
            dataLength={trendData.length} 
          />
          
          {/* Area fill for area charts */}
          {chartType === 'area' && (
            <path
              d={areaPath}
              fill={metricInfo.color}
              fillOpacity={0.1}
            />
          )}
          
          {/* Line path */}
          {(chartType === 'line' || chartType === 'area') && (
            <path
              d={linePath}
              fill="none"
              stroke={metricInfo.color}
              strokeWidth="2"
              className="transition-all duration-200"
            />
          )}
          
          {/* Bar charts */}
          {chartType === 'bar' && chartData.map((point, index) => {
            const barWidth = Math.max(8, xScale.step * 0.6)
            const barHeight = (config.height - config.margin.bottom) - point.y
            
            return (
              <rect
                key={index}
                x={point.x - barWidth / 2}
                y={point.y}
                width={barWidth}
                height={barHeight}
                fill={metricInfo.color}
                fillOpacity={0.8}
                className="hover:opacity-100 transition-opacity cursor-pointer"
                onMouseEnter={() => setHoveredPoint(point)}
              />
            )
          })}
          
          {/* Data points */}
          {chartData.map((point, index) => (
            <circle
              key={index}
              cx={point.x}
              cy={point.y}
              r={4}
              fill={metricInfo.color}
              className="hover:r-6 transition-all cursor-pointer"
              onMouseEnter={() => setHoveredPoint(point)}
            />
          ))}
          
          {/* Y-axis labels */}
          {Array.from({ length: 6 }).map((_, i) => {
            const value = yScale.min + (i * (yScale.max - yScale.min) / 5)
            const y = yScale.scale(value)
            
            return (
              <text
                key={i}
                x={config.margin.left - 10}
                y={y}
                textAnchor="end"
                className="text-xs fill-muted-foreground"
                dominantBaseline="middle"
              >
                {value.toFixed(metricType === 'successRate' ? 1 : 0)}{metricInfo.unit}
              </text>
            )
          })}
          
          {/* X-axis labels */}
          {chartData.map((point, index) => (
            <text
              key={index}
              x={point.x}
              y={config.height - config.margin.bottom + 20}
              textAnchor="middle"
              className="text-xs fill-muted-foreground"
            >
              {new Date(point.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
            </text>
          ))}
        </svg>
        
        {/* Tooltip */}
        {config.showTooltip && hoveredPoint && (
          <ChartTooltip point={hoveredPoint} metricInfo={metricInfo} />
        )}
      </div>
    )
  }

  return (
    <Card className={className}>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <metricInfo.icon className="h-5 w-5" style={{ color: metricInfo.color }} />
            <CardTitle>{metricInfo.label} Trend</CardTitle>
            {trend.direction !== 'neutral' && (
              <div className="flex items-center gap-1">
                {trend.direction === 'up' ? (
                  <TrendingUp className="h-4 w-4 text-green-600" />
                ) : (
                  <TrendingDown className="h-4 w-4 text-red-600" />
                )}
                <Badge 
                  variant={trend.direction === 'up' ? 'default' : 'destructive'}
                  className="text-xs"
                >
                  {trend.change.toFixed(1)}%
                </Badge>
              </div>
            )}
          </div>
          
          {showControls && (
            <div className="flex items-center gap-2">
              <Select value={metricType} onValueChange={(value) => setMetricType(value as MetricType)}>
                <SelectTrigger className="w-36">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="executions">Executions</SelectItem>
                  <SelectItem value="successRate">Success Rate</SelectItem>
                  <SelectItem value="avgTime">Avg Time</SelectItem>
                </SelectContent>
              </Select>
              
              <Select value={chartType} onValueChange={(value) => setChartType(value as ChartType)}>
                <SelectTrigger className="w-24">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="line">Line</SelectItem>
                  <SelectItem value="bar">Bar</SelectItem>
                  <SelectItem value="area">Area</SelectItem>
                </SelectContent>
              </Select>
            </div>
          )}
        </div>
      </CardHeader>
      
      <CardContent>
        <div className="overflow-x-auto">
          {renderChart()}
        </div>
        
        {/* Performance summary */}
        {performance && (
          <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div className="text-center p-3 bg-muted rounded">
              <p className="font-semibold">{performance.totalExecutions}</p>
              <p className="text-muted-foreground">Total</p>
            </div>
            <div className="text-center p-3 bg-muted rounded">
              <p className="font-semibold">{performance.successRate.toFixed(1)}%</p>
              <p className="text-muted-foreground">Success</p>
            </div>
            <div className="text-center p-3 bg-muted rounded">
              <p className="font-semibold">{(performance.avgExecutionTime / 1000).toFixed(1)}s</p>
              <p className="text-muted-foreground">Avg Time</p>
            </div>
            <div className="text-center p-3 bg-muted rounded">
              <p className="font-semibold">{performance.failedExecutions}</p>
              <p className="text-muted-foreground">Failed</p>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export default PerformanceChart