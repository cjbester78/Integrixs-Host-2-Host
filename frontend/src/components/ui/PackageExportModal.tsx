import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Package, Download, X, Loader2, FileJson } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { api } from '@/lib/api';
import { Badge } from '@/components/ui/badge';

interface PackageExportModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  packageId: string;
  packageName: string;
}

interface Flow {
  id: string;
  name: string;
  description?: string;
  active: boolean;
  flowDefinition?: string;
}

interface Adapter {
  id: string;
  name: string;
  type: string;
  direction: string;
  active: boolean;
  configuration?: Record<string, any>;
}

/**
 * Package Export Modal
 * Allows users to select which flows to export along with their relevant adapters
 */
export const PackageExportModal: React.FC<PackageExportModalProps> = ({
  open,
  onOpenChange,
  packageId,
  packageName
}) => {
  const [selectedFlowIds, setSelectedFlowIds] = useState<Set<string>>(new Set());
  const [isExporting, setIsExporting] = useState(false);

  // Fetch package container with all assets
  const { data: containerData, isLoading } = useQuery({
    queryKey: ['package-container', packageId],
    queryFn: async () => {
      const response = await api.get(`/api/packages/${packageId}/container`);
      return response.data?.data || response.data;
    },
    enabled: open && !!packageId
  });

  // Fetch flows for this package
  const { data: flowsData } = useQuery({
    queryKey: ['package-flows-export', packageId],
    queryFn: async () => {
      const response = await api.get(`/api/flows/package/${packageId}`);
      return response.data?.data || response.data || [];
    },
    enabled: open && !!packageId
  });

  // Fetch adapters for this package
  const { data: adaptersData } = useQuery({
    queryKey: ['package-adapters-export', packageId],
    queryFn: async () => {
      const response = await api.get(`/api/adapters/package/${packageId}`);
      return response.data?.data || response.data || [];
    },
    enabled: open && !!packageId
  });

  const flows: Flow[] = Array.isArray(flowsData) ? flowsData : [];
  const adapters: Adapter[] = Array.isArray(adaptersData) ? adaptersData : [];

  // Reset selections when modal opens
  useEffect(() => {
    if (open) {
      setSelectedFlowIds(new Set());
    }
  }, [open]);

  // Toggle flow selection
  const toggleFlow = (flowId: string) => {
    setSelectedFlowIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(flowId)) {
        newSet.delete(flowId);
      } else {
        newSet.add(flowId);
      }
      return newSet;
    });
  };

  // Select all flows
  const selectAll = () => {
    setSelectedFlowIds(new Set(flows.map(f => f.id)));
  };

  // Deselect all flows
  const deselectAll = () => {
    setSelectedFlowIds(new Set());
  };

  // Extract adapter IDs used by selected flows
  const getAdapterIdsFromFlows = (selectedFlows: Flow[]): Set<string> => {
    const adapterIds = new Set<string>();

    selectedFlows.forEach(flow => {
      if (flow.flowDefinition) {
        try {
          const definition = typeof flow.flowDefinition === 'string'
            ? JSON.parse(flow.flowDefinition)
            : flow.flowDefinition;

          if (definition.nodes && Array.isArray(definition.nodes)) {
            definition.nodes.forEach((node: any) => {
              const nodeType = node.type;
              if (nodeType && (nodeType.includes('SENDER') || nodeType.includes('RECEIVER'))) {
                const adapterId = node.data?.adapterId;
                if (adapterId) {
                  adapterIds.add(adapterId);
                }
              }
            });
          }
        } catch (error) {
          console.error(`Failed to parse flow definition for flow ${flow.id}:`, error);
        }
      }
    });

    return adapterIds;
  };

  // Handle export
  const handleExport = async () => {
    if (selectedFlowIds.size === 0) {
      return;
    }

    setIsExporting(true);

    try {
      // Call backend to export and encrypt the package
      const response = await api.post(`/api/packages/${packageId}/export`, {
        selectedFlowIds: Array.from(selectedFlowIds)
      });

      const encryptedData = response.data?.data || response.data;

      // Create and download encrypted JSON file
      const dataStr = JSON.stringify(encryptedData, null, 2);
      const dataBlob = new Blob([dataStr], { type: 'application/json' });

      const url = URL.createObjectURL(dataBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${packageName}_export_encrypted_${new Date().toISOString().split('T')[0]}.h2hpkg`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);

      // Close modal and show success
      onOpenChange(false);
    } catch (error: any) {
      console.error('Failed to export package:', error);
      alert('Failed to export package: ' + (error.response?.data?.message || error.message));
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Package className="h-5 w-5 text-primary" />
            Export Package: {packageName}
          </DialogTitle>
          <DialogDescription>
            Select which flows you want to export. The relevant adapters for each flow will be included automatically.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-auto">
          {isLoading ? (
            <div className="flex items-center justify-center h-40">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : flows.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              <FileJson className="h-12 w-12 mx-auto mb-3 opacity-50" />
              <p>No flows available to export</p>
            </div>
          ) : (
            <div className="space-y-4">
              {/* Selection controls */}
              <div className="flex items-center justify-between pb-3 border-b">
                <div className="text-sm font-medium">
                  {selectedFlowIds.size} of {flows.length} flow{flows.length !== 1 ? 's' : ''} selected
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={selectAll}
                    disabled={selectedFlowIds.size === flows.length}
                  >
                    Select All
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={deselectAll}
                    disabled={selectedFlowIds.size === 0}
                  >
                    Deselect All
                  </Button>
                </div>
              </div>

              {/* Flow list */}
              <div className="space-y-2">
                {flows.map(flow => {
                  const isSelected = selectedFlowIds.has(flow.id);

                  return (
                    <div
                      key={flow.id}
                      className={`p-3 border rounded-lg cursor-pointer transition-colors hover:bg-accent ${
                        isSelected ? 'bg-accent border-primary' : ''
                      }`}
                      onClick={() => toggleFlow(flow.id)}
                    >
                      <div className="flex items-start gap-3">
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={() => toggleFlow(flow.id)}
                          onClick={(e) => e.stopPropagation()}
                        />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="font-medium text-sm">{flow.name}</span>
                            {flow.active && (
                              <Badge variant="outline" className="text-xs">
                                Active
                              </Badge>
                            )}
                          </div>
                          {flow.description && (
                            <p className="text-xs text-muted-foreground line-clamp-1">
                              {flow.description}
                            </p>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Footer actions */}
        <div className="flex items-center justify-between pt-4 border-t">
          <div className="text-sm text-muted-foreground">
            {selectedFlowIds.size > 0 && (
              <span>
                Exporting {selectedFlowIds.size} flow{selectedFlowIds.size !== 1 ? 's' : ''} with their adapters
              </span>
            )}
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isExporting}
            >
              <X className="h-4 w-4 mr-2" />
              Cancel
            </Button>
            <Button
              onClick={handleExport}
              disabled={selectedFlowIds.size === 0 || isExporting}
            >
              {isExporting ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  Exporting...
                </>
              ) : (
                <>
                  <Download className="h-4 w-4 mr-2" />
                  Export Selected
                </>
              )}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default PackageExportModal;
