import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Edit, X } from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import FlowCanvas, { FlowData } from '@/components/flow/FlowCanvas';

interface ViewFlowProps {
  packageId: string;
  flowId: string;
  onEdit: () => void;
  onDelete: () => void;
  onBack: () => void;
  onOpenDesigner: () => void;
}

const ViewFlow: React.FC<ViewFlowProps> = ({ packageId, flowId, onEdit, onBack }) => {
  const [flowName, setFlowName] = useState('');
  const [flowDescription, setFlowDescription] = useState('');
  const [flowData, setFlowData] = useState<FlowData | null>(null);

  // Fetch flow details
  const { data: flow, isLoading } = useQuery({
    queryKey: ['flow', flowId],
    queryFn: async () => {
      const response = await api.get(`/api/flows/${flowId}`);
      return response.data?.data || response.data;
    },
    enabled: !!flowId,
  });

  // Populate data when flow loads
  useEffect(() => {
    if (flow) {
      setFlowData({
        id: flow.id,
        name: flow.name,
        description: flow.description || '',
        nodes: flow.flowDefinition?.nodes || [],
        edges: flow.flowDefinition?.edges || [],
        active: flow.active
      });
      setFlowName(flow.name || '');
      setFlowDescription(flow.description || '');
    }
  }, [flow]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto"></div>
          <p className="mt-2 text-muted-foreground">Loading flow...</p>
        </div>
      </div>
    );
  }

  if (!flow) {
    return (
      <div className="text-center py-12">
        <p className="text-muted-foreground">Flow not found</p>
        <Button variant="outline" onClick={onBack} className="mt-4">
          Go Back
        </Button>
      </div>
    );
  }

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
              onClick={onBack}
              className="space-x-2"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Back to Flows</span>
            </Button>
            <h1 className="text-xl font-semibold text-foreground">
              View Visual Flow
            </h1>
          </div>

          <div className="flex items-center space-x-2">
            {!flow.deployed && (
              <Button
                variant="outline"
                size="sm"
                onClick={onEdit}
                className="space-x-2"
              >
                <Edit className="h-4 w-4" />
                <span>Edit Flow</span>
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={onBack}
              className="space-x-2"
            >
              <X className="h-4 w-4" />
              <span>Close</span>
            </Button>
          </div>
        </div>

        {/* Flow Configuration Inputs (Read-Only) */}
        <div className="px-4 py-4 bg-muted/50 border-t border-border">
          <div className="grid grid-cols-2 gap-4 max-w-2xl">
            <div>
              <label className="text-sm font-medium text-foreground block mb-1">
                Flow Name
              </label>
              <input
                type="text"
                value={flowName}
                readOnly
                className="w-full px-3 py-2 border border-input rounded-md text-foreground bg-muted/50 cursor-default"
              />
            </div>
            <div>
              <label className="text-sm font-medium text-foreground block mb-1">Description</label>
              <input
                type="text"
                value={flowDescription}
                readOnly
                className="w-full px-3 py-2 border border-input rounded-md text-foreground bg-muted/50 cursor-default"
              />
            </div>
          </div>
        </div>
      </div>

      {/* Flow Canvas (Read-Only) */}
      <div className="flex-1 min-h-0 relative">
        {flowData && (
          <FlowCanvas
            flowData={flowData}
            onFlowChange={() => {}}
            onSave={() => {}}
            onExecute={() => {}}
            readOnly={true}
            className="h-full"
          />
        )}
      </div>
    </div>
  );
};

export default ViewFlow;
