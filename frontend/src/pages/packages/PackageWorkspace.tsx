import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSidebar } from '@/contexts/SidebarContext';
import {
  Package,
  ArrowLeft,
  Settings,
  RefreshCw
} from 'lucide-react';

import { api } from '@/lib/api';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { AdapterTestResultModal } from '@/components/ui/AdapterTestResultModal';
import { NotificationModal, NotificationType } from '@/components/ui/NotificationModal';
import { ConfirmationModal } from '@/components/ui/ConfirmationModal';

// Components
import WorkspaceSidebar from './components/WorkspaceSidebar';
import PackageOverview from './components/PackageOverview';
import PackageAdaptersTab from './components/PackageAdaptersTab';
import PackageFlowsTab from './components/PackageFlowsTab';

// Adapter Forms
import { CreateAdapter, EditAdapter, ViewAdapter } from './components/adapters';
// Flow Forms
import { CreateFlow, EditFlow, ViewFlow } from './components/flows';

type WorkspaceAction = 'list' | 'create' | 'edit' | 'view';

interface PackageDetails {
  id: string;
  name: string;
  description: string;
  version: string;
  configuration: Record<string, unknown>;
  createdAt: string;
  updatedAt: string | null;
}

const PackageWorkspace: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [activeSection, setActiveSection] = useState('overview');
  const [currentAction, setCurrentAction] = useState<WorkspaceAction>('list');
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [testResultModalOpen, setTestResultModalOpen] = useState(false);
  const [testResult, setTestResult] = useState<any>(null);
  const [notificationModal, setNotificationModal] = useState<{
    open: boolean;
    type: NotificationType;
    title?: string;
    message: string;
  }>({ open: false, type: 'info', message: '' });
  const [confirmationModal, setConfirmationModal] = useState<{
    open: boolean;
    title: string;
    message: string;
    confirmLabel?: string;
    onConfirm: () => void;
  }>({ open: false, title: '', message: '', onConfirm: () => {} });
  const { setCollapsed } = useSidebar();

  const showNotification = (type: NotificationType, message: string, title?: string) => {
    setNotificationModal({ open: true, type, title, message });
  };

  // Collapse main sidebar when workspace opens, restore when leaving
  useEffect(() => {
    setCollapsed(true);
    return () => setCollapsed(false);
  }, [setCollapsed]);

  // Fetch package details
  const { data: packageData, isLoading, error } = useQuery({
    queryKey: ['package', id],
    queryFn: async () => {
      const response = await api.get(`/api/packages/${id}`);
      return response.data?.data || response.data;
    },
    enabled: !!id
  });

  // Fetch adapters for this package
  const { data: adaptersData } = useQuery({
    queryKey: ['package-adapters', id],
    queryFn: async () => {
      const response = await api.get(`/api/adapters/package/${id}`);
      return response.data?.data || response.data || [];
    },
    enabled: !!id
  });

  // Fetch flows for this package
  const { data: flowsData } = useQuery({
    queryKey: ['package-flows', id],
    queryFn: async () => {
      const response = await api.get(`/api/flows/package/${id}`);
      return response.data?.data || response.data || [];
    },
    enabled: !!id
  });

  const pkg: PackageDetails | null = packageData;
  const adapters = Array.isArray(adaptersData) ? adaptersData : [];
  const flows = Array.isArray(flowsData) ? flowsData : [];

  // Handle sidebar navigation
  const handleSidebarNavigate = (section: string) => {
    setActiveSection(section);
    setCurrentAction('list');
    setSelectedItemId(null);
  };


  // Loading state
  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" onClick={() => navigate('/packages')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Packages
          </Button>
        </div>
        <Card className="animate-pulse">
          <CardContent className="pt-6">
            <div className="space-y-4">
              <div className="h-8 bg-gray-200 rounded w-1/3"></div>
              <div className="h-4 bg-gray-200 rounded w-2/3"></div>
              <div className="h-4 bg-gray-200 rounded w-1/2"></div>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  // Error state
  if (error || !pkg) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" onClick={() => navigate('/packages')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Packages
          </Button>
        </div>
        <Card className="border-red-200 bg-red-50">
          <CardContent className="pt-6">
            <div className="text-center py-8">
              <Package className="h-12 w-12 text-red-400 mx-auto mb-4" />
              <div className="text-lg font-medium text-red-600 mb-2">Package not found</div>
              <div className="text-muted-foreground mb-4">
                The package you're looking for doesn't exist or has been deleted.
              </div>
              <Button onClick={() => navigate('/packages')}>
                Return to Package Library
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  // Refresh all data
  const handleRefresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['package', id] });
    await queryClient.invalidateQueries({ queryKey: ['package-adapters', id] });
    await queryClient.invalidateQueries({ queryKey: ['package-flows', id] });
  };

  // Event handlers for adapters
  const handleAddAdapter = () => {
    setActiveSection('adapters');
    setCurrentAction('create');
    setSelectedItemId(null);
  };

  const handleViewAdapter = (adapter: any) => {
    setActiveSection('adapters');
    setCurrentAction('view');
    setSelectedItemId(adapter.id);
  };

  const handleEditAdapter = (adapter: any) => {
    setActiveSection('adapters');
    setCurrentAction('edit');
    setSelectedItemId(adapter.id);
  };

  const handleDeleteAdapter = async (adapter: any) => {
    setConfirmationModal({
      open: true,
      title: 'Delete Adapter',
      message: `Are you sure you want to delete the adapter "${adapter.name}"? This action cannot be undone and will permanently remove the adapter configuration.`,
      confirmLabel: 'Delete Adapter',
      onConfirm: async () => {
        try {
          await api.delete(`/api/adapters/${adapter.id}`);
          await handleRefresh();
          setCurrentAction('list');
          setSelectedItemId(null);
          showNotification('success', `Adapter "${adapter.name}" deleted successfully`);
        } catch (error) {
          console.error('Error deleting adapter:', error);
          showNotification('error', 'Failed to delete adapter');
        }
      }
    });
  };

  const handleStartAdapter = async (adapter: any) => {
    try {
      await api.post(`/api/adapters/${adapter.id}/start`);
      await handleRefresh();
    } catch (error) {
      console.error('Error starting adapter:', error);
    }
  };

  const handleStopAdapter = async (adapter: any) => {
    try {
      await api.post(`/api/adapters/${adapter.id}/stop`);
      await handleRefresh();
    } catch (error) {
      console.error('Error stopping adapter:', error);
    }
  };

  const handleTestAdapter = async (adapter: any) => {
    try {
      const result = await api.post(`/api/adapters/${adapter.id}/test`);
      setTestResult(result);
      setTestResultModalOpen(true);
    } catch (error: any) {
      setTestResult({
        data: {
          success: false,
          error: error.response?.data?.message || error.message || 'Unknown error'
        }
      });
      setTestResultModalOpen(true);
    }
  };

  const handleAdapterFormSuccess = () => {
    handleRefresh();
    setCurrentAction('list');
    setSelectedItemId(null);
  };

  const handleAdapterFormCancel = () => {
    setCurrentAction('list');
    setSelectedItemId(null);
  };

  // Event handlers for flows
  const handleAddFlow = () => {
    setActiveSection('flows');
    setCurrentAction('create');
    setSelectedItemId(null);
  };

  const handleViewFlow = (flow: any) => {
    setActiveSection('flows');
    setCurrentAction('view');
    setSelectedItemId(flow.id);
  };

  const handleEditFlow = (flow: any) => {
    setActiveSection('flows');
    setCurrentAction('edit');
    setSelectedItemId(flow.id);
  };

  const handleDeleteFlow = async (flow: any) => {
    setConfirmationModal({
      open: true,
      title: 'Delete Flow',
      message: `Are you sure you want to delete the flow "${flow.name}"? This action cannot be undone and will permanently remove the flow configuration.`,
      confirmLabel: 'Delete Flow',
      onConfirm: async () => {
        try {
          await api.delete(`/api/flows/${flow.id}`);
          await handleRefresh();
          setCurrentAction('list');
          setSelectedItemId(null);
          showNotification('success', `Flow "${flow.name}" deleted successfully`);
        } catch (error: any) {
          console.error('Error deleting flow:', error);
          const errorMessage = error.response?.data?.message || error.response?.data?.error || error.message || 'Failed to delete flow';
          showNotification('error', errorMessage);
        }
      }
    });
  };

  const handleActivateFlow = async (flow: any) => {
    try {
      await api.put(`/api/flows/${flow.id}/active?active=true`);
      await handleRefresh();
      showNotification('success', `Flow "${flow.name}" has been activated successfully`);
    } catch (error: any) {
      console.error('Error activating flow:', error);
      const errorMessage = error.response?.data?.message || error.response?.data?.error || error.message || 'Failed to activate flow';
      showNotification('error', errorMessage);
    }
  };

  const handleDeployFlow = async (flow: any) => {
    try {
      const response = await api.post(`/api/flows/${flow.id}/deploy`);
      const result = response.data?.data || response.data;

      if (result?.status === 'SUCCESS') {
        await handleRefresh();
        showNotification('success', `Flow "${flow.name}" has been deployed successfully.`);
      } else {
        // Deployment failed - show detailed error message
        let errorMsg = result?.message || 'Unknown error';

        // Append validation errors if present
        if (result?.validationErrors && Array.isArray(result.validationErrors) && result.validationErrors.length > 0) {
          errorMsg += ':\n• ' + result.validationErrors.join('\n• ');
        } else if (result?.errors && Array.isArray(result.errors) && result.errors.length > 0) {
          errorMsg += ':\n• ' + result.errors.join('\n• ');
        }

        showNotification('error', errorMsg, 'Failed to Deploy Flow');
      }
    } catch (error: any) {
      console.error('Error deploying flow:', error);
      showNotification('error', error.response?.data?.message || error.message || 'Unknown error', 'Failed to Deploy Flow');
    }
  };

  const handleUndeployFlow = async (flow: any) => {
    try {
      const response = await api.post(`/api/flows/${flow.id}/undeploy`);
      const result = response.data?.data || response.data;

      if (result?.status === 'SUCCESS') {
        await handleRefresh();
        showNotification('success', `Flow "${flow.name}" has been undeployed.`);
      } else {
        const errorMsg = result?.message || 'Unknown error';
        showNotification('error', errorMsg, 'Failed to Undeploy Flow');
      }
    } catch (error: any) {
      console.error('Error undeploying flow:', error);
      showNotification('error', error.response?.data?.message || error.message || 'Unknown error', 'Failed to Undeploy Flow');
    }
  };

  const handleOpenFlowDesigner = (flowId: string) => {
    navigate(`/flows/${flowId}/designer`);
  };

  const handleDuplicateFlow = async (flow: any) => {
    try {
      await api.post(`/api/flows/${flow.id}/duplicate`);
      await handleRefresh();
    } catch (error) {
      console.error('Error duplicating flow:', error);
    }
  };

  const handleExportFlow = async (flow: any) => {
    try {
      const response = await api.get(`/api/flows/${flow.id}/export`);
      const dataStr = JSON.stringify(response.data, null, 2);
      const dataBlob = new Blob([dataStr], { type: 'application/json' });
      const url = URL.createObjectURL(dataBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${flow.name || 'flow'}_export.json`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Error exporting flow:', error);
      showNotification('error', 'Failed to export flow. Please try again.');
    }
  };

  const handleImportFlow = async (file: File) => {
    try {
      const fileContent = await file.text();
      const flowData = JSON.parse(fileContent);

      // Add packageId to the imported flow
      flowData.packageId = id;

      await api.post('/api/flows/import', flowData);
      await handleRefresh();
      showNotification('success', 'Flow has been imported successfully.');
    } catch (error: any) {
      console.error('Error importing flow:', error);
      showNotification('error', error.response?.data?.message || error.message || 'Unknown error', 'Failed to Import Flow');
    }
  };

  const handleFlowFormSuccess = () => {
    handleRefresh();
    setCurrentAction('list');
    setSelectedItemId(null);
  };

  const handleFlowFormCancel = () => {
    setCurrentAction('list');
    setSelectedItemId(null);
  };

  // Render the main content based on active section and action
  const renderContent = () => {
    // Handle adapter section
    if (activeSection === 'adapters') {
      switch (currentAction) {
        case 'create':
          return (
            <CreateAdapter
              packageId={id!}
              onSuccess={handleAdapterFormSuccess}
              onCancel={handleAdapterFormCancel}
            />
          );
        case 'edit':
          if (!selectedItemId) return null;
          return (
            <EditAdapter
              packageId={id!}
              adapterId={selectedItemId}
              onSuccess={handleAdapterFormSuccess}
              onCancel={handleAdapterFormCancel}
            />
          );
        case 'view':
          if (!selectedItemId) return null;
          return (
            <ViewAdapter
              packageId={id!}
              adapterId={selectedItemId}
              onEdit={() => handleEditAdapter({ id: selectedItemId })}
              onDelete={() => {
                const adapter = adapters.find((a: any) => a.id === selectedItemId);
                if (adapter) handleDeleteAdapter(adapter);
              }}
              onBack={handleAdapterFormCancel}
            />
          );
        default:
          return (
            <PackageAdaptersTab
              packageId={id!}
              adapters={adapters}
              onAddAdapter={handleAddAdapter}
              onViewAdapter={handleViewAdapter}
              onEditAdapter={handleEditAdapter}
              onDeleteAdapter={handleDeleteAdapter}
              onTestAdapter={handleTestAdapter}
            />
          );
      }
    }

    // Handle flows section
    if (activeSection === 'flows') {
      switch (currentAction) {
        case 'create':
          return (
            <CreateFlow
              packageId={id!}
              onSuccess={handleFlowFormSuccess}
              onCancel={handleFlowFormCancel}
            />
          );
        case 'edit':
          if (!selectedItemId) return null;
          return (
            <EditFlow
              packageId={id!}
              flowId={selectedItemId}
              onSuccess={handleFlowFormSuccess}
              onCancel={handleFlowFormCancel}
            />
          );
        case 'view':
          if (!selectedItemId) return null;
          return (
            <ViewFlow
              packageId={id!}
              flowId={selectedItemId}
              onEdit={() => handleEditFlow({ id: selectedItemId })}
              onDelete={() => {
                const flow = flows.find((f: any) => f.id === selectedItemId);
                if (flow) handleDeleteFlow(flow);
              }}
              onBack={handleFlowFormCancel}
              onOpenDesigner={() => handleOpenFlowDesigner(selectedItemId)}
            />
          );
        default:
          return (
            <PackageFlowsTab
              packageId={id!}
              flows={flows}
              onAddFlow={handleAddFlow}
              onViewFlow={handleViewFlow}
              onEditFlow={handleEditFlow}
              onDeleteFlow={handleDeleteFlow}
              onActivateFlow={handleActivateFlow}
              onDeployFlow={handleDeployFlow}
              onUndeployFlow={handleUndeployFlow}
              onExportFlow={handleExportFlow}
              onImportFlow={handleImportFlow}
            />
          );
      }
    }

    // Default: Overview
    return (
      <PackageOverview
        pkg={pkg}
        adapters={adapters}
        flows={flows}
      />
    );
  };

  return (
    <div className="flex h-full -m-6">
      {/* Sidebar */}
      <WorkspaceSidebar
        packageId={id!}
        onNavigate={handleSidebarNavigate}
        activeSection={activeSection}
      />

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-h-0">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 flex-shrink-0">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="sm" onClick={() => navigate('/packages')}>
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back
            </Button>
            <div>
              <div className="flex items-center gap-3">
                <Package className="h-6 w-6 text-primary" />
                <h1 className="text-xl font-bold">{pkg.name}</h1>
              </div>
              {pkg.description && (
                <p className="text-sm text-muted-foreground mt-0.5">{pkg.description}</p>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={handleRefresh}>
              <RefreshCw className="h-4 w-4 mr-2" />
              Refresh
            </Button>
            <Button variant="outline" size="sm">
              <Settings className="h-4 w-4 mr-2" />
              Settings
            </Button>
          </div>
        </div>

        {/* Content Area */}
        <div className="p-6 flex-1 overflow-y-auto">
          {renderContent()}
        </div>
      </div>

      {/* Adapter Test Result Modal */}
      <AdapterTestResultModal
        open={testResultModalOpen}
        onOpenChange={setTestResultModalOpen}
        testResult={testResult}
      />

      {/* Notification Modal */}
      <NotificationModal
        open={notificationModal.open}
        onOpenChange={(open) => setNotificationModal(prev => ({ ...prev, open }))}
        type={notificationModal.type}
        title={notificationModal.title}
        message={notificationModal.message}
      />

      <ConfirmationModal
        open={confirmationModal.open}
        onOpenChange={(open) => setConfirmationModal(prev => ({ ...prev, open }))}
        title={confirmationModal.title}
        message={confirmationModal.message}
        confirmLabel={confirmationModal.confirmLabel || "Delete"}
        variant="destructive"
        onConfirm={confirmationModal.onConfirm}
      />
    </div>
  );
};

export default PackageWorkspace;
