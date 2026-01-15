import React, { useRef } from 'react';
import {
  Workflow,
  Plus,
  Play,
  Square,
  Trash2,
  Eye,
  Download,
  Upload,
  CheckCircle,
  Clock,
  ArrowRight,
  Power
} from 'lucide-react';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

interface Flow {
  id: string;
  name: string;
  description?: string;
  status: string;
  deployed: boolean;
  active: boolean;
  originalFlowId?: string | null;
  flowType?: string;
  flowVersion?: number;
  lastExecutedAt?: string;
  executionCount?: number;
  totalExecutions?: number;
  successfulExecutions?: number;
  failedExecutions?: number;
  averageExecutionTimeMs?: number;
  createdAt?: string;
  updatedAt?: string;
  flowDefinition?: {
    nodes?: any[];
    edges?: any[];
  };
}

interface PackageFlowsTabProps {
  packageId: string;
  flows: Flow[];
  onAddFlow?: () => void;
  onEditFlow?: (flow: Flow) => void;
  onDeleteFlow?: (flow: Flow) => void;
  onActivateFlow?: (flow: Flow) => void;
  onDeployFlow?: (flow: Flow) => void;
  onUndeployFlow?: (flow: Flow) => void;
  onViewFlow?: (flow: Flow) => void;
  onExportFlow?: (flow: Flow) => void;
  onImportFlow?: (file: File) => void;
}

const PackageFlowsTab: React.FC<PackageFlowsTabProps> = ({
  packageId,
  flows,
  onAddFlow,
  onEditFlow,
  onDeleteFlow,
  onActivateFlow,
  onDeployFlow,
  onUndeployFlow,
  onViewFlow,
  onExportFlow,
  onImportFlow
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file && onImportFlow) {
      onImportFlow(file);
    }
    // Reset the input so the same file can be selected again
    event.target.value = '';
  };

  // Calculate statistics
  const totalFlows = flows.length;
  const enabledFlows = flows.filter(f => f.active).length;
  const totalExecutions = flows.reduce((sum, f) => sum + (f.totalExecutions || f.executionCount || 0), 0);
  const avgExecutionTime = flows.length > 0
    ? flows.reduce((sum, f) => sum + (f.averageExecutionTimeMs || 0), 0) / flows.length
    : 0;

  const formatExecutionTime = (ms: number) => {
    if (ms < 1000) return `${Math.round(ms)}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  };

  const calculateSuccessRate = (successful: number, total: number) => {
    if (total === 0) return 100;
    return Math.round((successful / total) * 100);
  };

  return (
    <div className="space-y-6">
      {/* Hidden file input for import */}
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        accept=".json"
        className="hidden"
      />

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Visual Flow Management</h2>
          <p className="text-sm text-muted-foreground">
            Create and manage visual integration flows with drag-and-drop components
          </p>
        </div>
        <div className="flex items-center space-x-2">
          <Button variant="outline" size="sm" onClick={handleImportClick}>
            <Upload className="h-4 w-4 mr-2" />
            Import Flow
          </Button>
          <Button size="sm" onClick={onAddFlow}>
            <Plus className="h-4 w-4 mr-2" />
            Create Flow
          </Button>
        </div>
      </div>

      {/* Statistics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Workflow className="h-8 w-8 text-primary" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Flows</p>
                <p className="text-2xl font-bold text-foreground">{totalFlows}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <CheckCircle className="h-8 w-8 text-success" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Enabled Flows</p>
                <p className="text-2xl font-bold text-foreground">{enabledFlows}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <CheckCircle className="h-8 w-8 text-info" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Executions</p>
                <p className="text-2xl font-bold text-foreground">{totalExecutions}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Clock className="h-8 w-8 text-warning" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Avg Execution Time</p>
                <p className="text-2xl font-bold text-foreground">{formatExecutionTime(avgExecutionTime)}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Empty State */}
      {flows.length === 0 ? (
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Workflow className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">No Visual Flows</h3>
              <p className="text-muted-foreground mb-6">
                Create your first visual integration flow using our drag-and-drop builder
              </p>
              <Button onClick={onAddFlow}>
                <Plus className="h-4 w-4 mr-2" />
                Create Flow
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        /* Flow List */
        <div className="space-y-6">
          {flows.map((flow) => {
            const nodeCount = flow.flowDefinition?.nodes?.length || 0;
            const connectionCount = flow.flowDefinition?.edges?.length || 0;
            const successRate = calculateSuccessRate(flow.successfulExecutions || 0, flow.totalExecutions || 0);
            const executions = flow.totalExecutions || flow.executionCount || 0;
            const successful = flow.successfulExecutions || 0;
            const failed = flow.failedExecutions || 0;

            return (
              <Card key={flow.id} className="app-card border">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-3">
                      <Workflow className="h-5 w-5 text-primary" />
                      <div>
                        <CardTitle className="text-lg text-foreground">
                          {flow.name}
                          {flow.flowVersion && (
                            <span className="ml-2 text-sm text-muted-foreground">v{flow.flowVersion}</span>
                          )}
                        </CardTitle>
                        {flow.description && (
                          <p className="text-sm text-muted-foreground">{flow.description}</p>
                        )}
                        <div className="flex items-center space-x-4 mt-1 text-xs text-muted-foreground">
                          <span>Type: {flow.flowType || 'STANDARD'}</span>
                          {flow.updatedAt && (
                            <span>Updated: {new Date(flow.updatedAt).toLocaleDateString()}</span>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      <span className={`text-xs px-2 py-1 rounded ${
                        flow.active ? 'bg-success/20 text-success' : 'bg-muted/20 text-muted-foreground'
                      }`}>
                        {flow.active ? 'Active' : 'Inactive'}
                      </span>
                      <span className={`text-xs px-2 py-1 rounded ${
                        flow.deployed ? 'bg-info/20 text-info' : 'bg-warning/20 text-warning'
                      }`}>
                        {flow.deployed ? 'Deployed' : 'Not Deployed'}
                      </span>
                      <div className={`w-3 h-3 rounded-full ${
                        flow.active ? 'bg-success animate-glow' : 'bg-muted-foreground'
                      }`} />
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-6">
                    {/* Flow Complexity Overview */}
                    <div className="bg-secondary rounded-lg p-4">
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                        <div className="flex items-center space-x-2">
                          <Workflow className="h-4 w-4 text-primary" />
                          <div>
                            <span className="text-muted-foreground">Nodes:</span>
                            <span className="ml-1 text-foreground font-medium">{nodeCount}</span>
                          </div>
                        </div>
                        <div className="flex items-center space-x-2">
                          <ArrowRight className="h-4 w-4 text-info" />
                          <div>
                            <span className="text-muted-foreground">Connections:</span>
                            <span className="ml-1 text-foreground font-medium">{connectionCount}</span>
                          </div>
                        </div>
                        <div className="flex items-center space-x-2">
                          <CheckCircle className="h-4 w-4 text-success" />
                          <div>
                            <span className="text-muted-foreground">Success Rate:</span>
                            <span className="ml-1 text-success font-medium">{successRate}%</span>
                          </div>
                        </div>
                        <div className="flex items-center space-x-2">
                          <Clock className="h-4 w-4 text-warning" />
                          <div>
                            <span className="text-muted-foreground">Avg Time:</span>
                            <span className="ml-1 text-foreground font-medium">
                              {formatExecutionTime(flow.averageExecutionTimeMs || 0)}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Flow Statistics */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                      <div>
                        <span className="text-muted-foreground">Total Executions:</span>
                        <span className="ml-2 text-foreground font-medium">{executions}</span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Successful:</span>
                        <span className="ml-2 text-success font-medium">{successful}</span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Failed:</span>
                        <span className="ml-2 text-destructive font-medium">{failed}</span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Created:</span>
                        <span className="ml-2 text-foreground">
                          {flow.createdAt ? new Date(flow.createdAt).toLocaleDateString() : '-'}
                        </span>
                      </div>
                    </div>

                    {/* Action Buttons */}
                    <div className="flex items-center justify-end pt-4 border-t border-border">
                      <div className="flex items-center space-x-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onEditFlow?.(flow)}
                          disabled={flow.deployed}
                          className={flow.deployed ? "opacity-50 cursor-not-allowed" : ""}
                          title={flow.deployed ? "Flow must be undeployed before editing" : ""}
                        >
                          <Workflow className="h-4 w-4 mr-2" />
                          Edit Visual Flow
                        </Button>

                        {/* Show Activate button for imported inactive flows */}
                        {flow.originalFlowId && !flow.active && (
                          <Button
                            variant="outline"
                            size="sm"
                            className="text-success hover:bg-success/10 hover:text-success"
                            onClick={() => onActivateFlow?.(flow)}
                          >
                            <Power className="h-4 w-4 mr-2" />
                            Activate
                          </Button>
                        )}

                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onViewFlow?.(flow)}
                        >
                          <Eye className="h-4 w-4 mr-2" />
                          View Details
                        </Button>

                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onExportFlow?.(flow)}
                        >
                          <Download className="h-4 w-4 mr-2" />
                          Export
                        </Button>

                        {/* Deploy/Undeploy buttons */}
                        {flow.deployed ? (
                          <Button
                            variant="outline"
                            size="sm"
                            className="text-warning hover:bg-warning/10 hover:text-warning"
                            onClick={() => onUndeployFlow?.(flow)}
                          >
                            <Square className="h-4 w-4 mr-2" />
                            Undeploy
                          </Button>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            className="text-info hover:bg-info/10 hover:text-info"
                            onClick={() => onDeployFlow?.(flow)}
                            disabled={!flow.active}
                          >
                            <Play className="h-4 w-4 mr-2" />
                            Deploy
                          </Button>
                        )}

                        <Button
                          variant="outline"
                          size="sm"
                          className={`text-destructive hover:bg-destructive/10 hover:text-destructive ${flow.deployed ? "opacity-50 cursor-not-allowed" : ""}`}
                          onClick={() => !flow.deployed && onDeleteFlow?.(flow)}
                          disabled={flow.deployed}
                          title={flow.deployed ? "Flow must be undeployed before deleting" : ""}
                        >
                          <Trash2 className="h-4 w-4 mr-2" />
                          Delete
                        </Button>
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default PackageFlowsTab;
