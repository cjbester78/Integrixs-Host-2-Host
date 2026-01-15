import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, X } from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import FlowCanvas, { FlowData } from '@/components/flow/FlowCanvas';
import toast from 'react-hot-toast';

interface CreateFlowProps {
  packageId: string;
  onSuccess: () => void;
  onCancel: () => void;
}

const CreateFlow: React.FC<CreateFlowProps> = ({ packageId, onSuccess, onCancel }) => {
  const queryClient = useQueryClient();
  const [flowName, setFlowName] = useState('');
  const [flowDescription, setFlowDescription] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  // Initialize empty flow data
  const [flowData, setFlowData] = useState<FlowData>({
    id: `flow-${Date.now()}`,
    name: '',
    description: '',
    nodes: [],
    edges: [],
    active: true
  });

  const createMutation = useMutation({
    mutationFn: async (payload: any) => {
      return api.post('/api/flows', payload);
    },
    onSuccess: () => {
      toast.success('Flow created successfully');
      queryClient.invalidateQueries({ queryKey: ['package-flows', packageId] });
      onSuccess();
    },
    onError: (err: any) => {
      const errorMessage = err.response?.data?.message || err.message || 'Failed to create flow';
      toast.error(errorMessage);
    },
  });

  const handleFlowChange = (updatedFlow: FlowData) => {
    setFlowData(updatedFlow);
  };

  const handleSave = async (flow: FlowData) => {
    // Validate required fields
    if (!flowName.trim()) {
      toast.error('Flow Name is required');
      return;
    }

    setIsSaving(true);
    try {
      const flowPayload = {
        flow: {
          name: flowName.trim(),
          description: flowDescription.trim(),
          flowDefinition: {
            nodes: flow.nodes,
            edges: flow.edges
          },
          active: flow.active
        },
        packageId
      };

      await createMutation.mutateAsync(flowPayload);
    } finally {
      setIsSaving(false);
    }
  };

  const handleExecute = async (_flow: FlowData) => {
    toast.error('Please save the flow before executing');
  };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="border-b bg-background relative z-10 shrink-0">
        {/* Top Row - Navigation */}
        <div className="flex items-center justify-between p-4">
          <div className="flex items-center space-x-4">
            <Button
              variant="ghost"
              size="sm"
              onClick={onCancel}
              className="space-x-2"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Back to Flows</span>
            </Button>
            <h1 className="text-xl font-semibold text-foreground">
              Create Visual Flow
            </h1>
          </div>

          <div className="flex items-center space-x-2">
            <Button
              variant="outline"
              size="sm"
              onClick={onCancel}
              className="space-x-2"
            >
              <X className="h-4 w-4" />
              <span>Cancel</span>
            </Button>
          </div>
        </div>

        {/* Flow Configuration Inputs */}
        <div className="px-4 py-4 bg-muted/50 border-t border-border">
          <div className="grid grid-cols-2 gap-4 max-w-2xl">
            <div>
              <label className="text-sm font-medium text-foreground block mb-1">
                Flow Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={flowName}
                onChange={(e) => setFlowName(e.target.value)}
                placeholder="Enter flow name"
                required
                className={`w-full px-3 py-2 border rounded-md text-foreground placeholder:text-muted-foreground ${
                  !flowName.trim() ? 'border-red-500' : 'border-input'
                } bg-background focus:outline-none focus:ring-2 focus:ring-ring`}
              />
            </div>
            <div>
              <label className="text-sm font-medium text-foreground block mb-1">Description</label>
              <input
                type="text"
                value={flowDescription}
                onChange={(e) => setFlowDescription(e.target.value)}
                placeholder="Enter description"
                className="w-full px-3 py-2 border border-input rounded-md text-foreground placeholder:text-muted-foreground bg-background focus:outline-none focus:ring-2 focus:ring-ring"
              />
            </div>
          </div>
        </div>
      </div>

      {/* Flow Canvas */}
      <div className="flex-1 min-h-0 relative">
        <FlowCanvas
          flowData={flowData}
          onFlowChange={handleFlowChange}
          onSave={handleSave}
          onExecute={handleExecute}
          readOnly={false}
          className="h-full"
        />
      </div>

      {/* Loading State */}
      {isSaving && (
        <div className="fixed inset-0 bg-background/80 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="text-center">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto"></div>
            <p className="mt-2 text-muted-foreground">Saving flow...</p>
          </div>
        </div>
      )}
    </div>
  );
};

export default CreateFlow;
