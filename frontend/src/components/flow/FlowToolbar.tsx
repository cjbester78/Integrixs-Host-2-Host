import React from 'react'
import { 
  Save, 
  Play, 
  Trash2, 
  RotateCcw, 
  Download,
  Upload,
  Eye,
  EyeOff,
  LayoutGrid
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { 
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'

interface FlowToolbarProps {
  onSave?: () => void
  onExecute?: () => void
  onClear?: () => void
  onDelete?: () => void
  onExport?: () => void
  onImport?: () => void
  onToggleNodeSidebar?: () => void
  onAutoLayout?: () => void
  isNodeSidebarVisible?: boolean
  readOnly?: boolean
  canExecute?: boolean
  canSave?: boolean
  canDelete?: boolean
  isExecuting?: boolean
  isSaving?: boolean
  className?: string
}

const FlowToolbar: React.FC<FlowToolbarProps> = ({
  onSave,
  onExecute,
  onClear,
  onDelete,
  onExport,
  onImport,
  onToggleNodeSidebar,
  onAutoLayout,
  isNodeSidebarVisible = false,
  readOnly = false,
  canExecute = true,
  canSave = true,
  canDelete = true,
  isExecuting = false,
  isSaving = false,
  className = ''
}) => {
  return (
    <TooltipProvider>
      <div className={`flex items-center space-x-2 p-3 bg-background border-b ${className}`}>
        {/* Primary Actions */}
        {!readOnly && (
          <>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="default"
                  size="sm"
                  onClick={onSave}
                  disabled={!canSave || isSaving}
                  className="space-x-2"
                >
                  <Save className={`h-4 w-4 ${isSaving ? 'animate-pulse' : ''}`} />
                  <span>{isSaving ? 'Saving...' : 'Save'}</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>{isSaving ? 'Saving flow to database...' : 'Save flow configuration'}</p>
              </TooltipContent>
            </Tooltip>

            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={onExecute}
                  disabled={!canExecute || isExecuting}
                  className="space-x-2"
                >
                  <Play className={`h-4 w-4 ${isExecuting ? 'animate-pulse' : ''}`} />
                  <span>{isExecuting ? 'Executing...' : 'Execute'}</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Execute flow with current configuration</p>
              </TooltipContent>
            </Tooltip>

            <Separator orientation="vertical" className="h-6" />
          </>
        )}

        {/* View Actions */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="sm"
              onClick={onToggleNodeSidebar}
            >
              {isNodeSidebarVisible ? (
                <EyeOff className="h-4 w-4" />
              ) : (
                <Eye className="h-4 w-4" />
              )}
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>{isNodeSidebarVisible ? 'Hide' : 'Show'} node panel</p>
          </TooltipContent>
        </Tooltip>

        {/* Layout Actions */}
        {!readOnly && (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="sm"
                onClick={onAutoLayout}
              >
                <LayoutGrid className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <p>Auto-arrange flow layout</p>
            </TooltipContent>
          </Tooltip>
        )}

        <Separator orientation="vertical" className="h-6" />

        {/* Import/Export Actions */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="sm"
              onClick={onExport}
            >
              <Download className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>Export flow</p>
          </TooltipContent>
        </Tooltip>

        {!readOnly && (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="sm"
                onClick={onImport}
              >
                <Upload className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <p>Import flow</p>
            </TooltipContent>
          </Tooltip>
        )}

        <Separator orientation="vertical" className="h-6" />

        {/* Destructive Actions */}
        {!readOnly && (
          <>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onClear}
                >
                  <RotateCcw className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Clear canvas</p>
              </TooltipContent>
            </Tooltip>

            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onDelete}
                  disabled={!canDelete}
                  className="text-destructive hover:text-destructive"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Delete flow</p>
              </TooltipContent>
            </Tooltip>
          </>
        )}
      </div>
    </TooltipProvider>
  )
}

export default FlowToolbar