import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Play, Loader2, CheckCircle, AlertCircle } from 'lucide-react'
import { flowApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

interface ManualFlowExecutionModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}


export const ManualFlowExecutionModal: React.FC<ManualFlowExecutionModalProps> = ({
  open,
  onOpenChange,
}) => {
  const [selectedFlowId, setSelectedFlowId] = useState<string>('')
  const queryClient = useQueryClient()

  // Fetch all flows
  const { data: flowsResponse, isLoading: flowsLoading } = useQuery({
    queryKey: ['flows'],
    queryFn: flowApi.getAllFlows,
    enabled: open, // Only fetch when modal is open
  })

  const flows = flowsResponse?.data || []

  // Execute flow mutation
  const executeFlowMutation = useMutation({
    mutationFn: (flowId: string) => flowApi.executeFlow(flowId),
    onSuccess: () => {
      // Refresh dashboard data after execution
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      onOpenChange(false) // Close modal on success
      setSelectedFlowId('') // Reset selection
    },
    onError: (error) => {
      console.error('Failed to execute flow:', error)
    },
  })

  const handleExecute = () => {
    if (selectedFlowId) {
      executeFlowMutation.mutate(selectedFlowId)
    }
  }

  const activeFlows = Array.isArray(flows) ? flows.filter(flow => 
    flow.active === true && 
    flow.deployed === true &&
    flow.deploymentStatus === 'DEPLOYED'
  ) : []

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center space-x-2">
            <Play className="h-5 w-5 text-primary" />
            <span>Manually Execute Flow</span>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4 pt-4">
          {flowsLoading ? (
            <div className="flex items-center justify-center py-8">
              <div className="flex items-center space-x-3">
                <Loader2 className="h-5 w-5 animate-spin" />
                <span className="text-muted-foreground">Loading flows...</span>
              </div>
            </div>
          ) : activeFlows.length === 0 ? (
            <div className="flex items-center justify-center py-8">
              <div className="text-center">
                <AlertCircle className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
                <p className="text-muted-foreground">No active flows available</p>
                <p className="text-sm text-muted-foreground mt-1">
                  Create and activate flows to enable manual execution
                </p>
              </div>
            </div>
          ) : (
            <>
              <div className="space-y-2">
                <label className="text-sm font-medium text-foreground">
                  Select Flow to Execute
                </label>
                <Select value={selectedFlowId} onValueChange={setSelectedFlowId}>
                  <SelectTrigger>
                    <SelectValue placeholder="Choose a flow..." />
                  </SelectTrigger>
                  <SelectContent>
                    {activeFlows.map((flow) => (
                      <SelectItem key={flow.id} value={flow.id}>
                        <div className="flex items-center justify-between w-full">
                          <span>{flow.name}</span>
                          {flow.deploymentStatus === 'DEPLOYED' && (
                            <CheckCircle className="h-4 w-4 text-success ml-2" />
                          )}
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {selectedFlowId && (
                <div className="bg-secondary rounded-lg p-3">
                  <div className="text-sm">
                    <p className="text-muted-foreground">Selected Flow:</p>
                    <p className="font-medium text-foreground">
                      {activeFlows.find(f => f.id === selectedFlowId)?.name}
                    </p>
                    {activeFlows.find(f => f.id === selectedFlowId)?.description && (
                      <p className="text-xs text-muted-foreground mt-1">
                        {activeFlows.find(f => f.id === selectedFlowId)?.description}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {executeFlowMutation.error && (
                <div className="bg-destructive/10 border border-destructive/20 rounded-lg p-3">
                  <div className="flex items-center space-x-2">
                    <AlertCircle className="h-4 w-4 text-destructive" />
                    <span className="text-sm text-destructive">
                      Failed to execute flow: {executeFlowMutation.error.message || 'Unknown error'}
                    </span>
                  </div>
                </div>
              )}

              {executeFlowMutation.isSuccess && (
                <div className="bg-success/10 border border-success/20 rounded-lg p-3">
                  <div className="flex items-center space-x-2">
                    <CheckCircle className="h-4 w-4 text-success" />
                    <span className="text-sm text-success">
                      Flow executed successfully!
                    </span>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <div className="flex justify-between pt-4">
          <Button
            variant="outline"
            onClick={() => {
              onOpenChange(false)
              setSelectedFlowId('')
              executeFlowMutation.reset()
            }}
          >
            Cancel
          </Button>
          <Button
            onClick={handleExecute}
            disabled={!selectedFlowId || executeFlowMutation.isPending || flowsLoading}
            className="flex items-center space-x-2"
          >
            {executeFlowMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Play className="h-4 w-4" />
            )}
            <span>
              {executeFlowMutation.isPending ? 'Executing...' : 'Execute'}
            </span>
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}