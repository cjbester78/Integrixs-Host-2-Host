import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from '@/components/ui/dialog';
import {
  Play,
  Trash2,
  Plus,
  Edit,
  Clock,
  Database,
  FileText,
  CheckCircle,
  XCircle,
  AlertTriangle
} from 'lucide-react';
import { NotificationModal, NotificationType } from '@/components/ui/NotificationModal';

interface DataRetentionConfig {
  id: string;
  dataType: 'LOG_FILES' | 'SYSTEM_LOGS' | 'TRANSACTION_LOGS' | 'SCHEDULE';
  name: string;
  description: string;
  retentionDays: number;
  archiveDays?: number;
  scheduleCron?: string;
  executorClass: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  lastExecution?: string;
  lastExecutionStatus?: string;
  lastItemsProcessed?: number;
}

interface DataRetentionStatus {
  lastExecution?: string;
  status: string;
  filesProcessed: number;
  databaseRecordsDeleted: number;
  nextExecution?: string;
}

const DATA_TYPE_OPTIONS = [
  { value: 'LOG_FILES', label: 'Log Files', description: 'Manages log file archiving and deletion', icon: FileText },
  { value: 'SYSTEM_LOGS', label: 'System Logs', description: 'Manages system_logs table cleanup', icon: Database },
  { value: 'TRANSACTION_LOGS', label: 'Transaction Logs', description: 'Manages transaction_logs table cleanup', icon: Database },
  { value: 'SCHEDULE', label: 'Schedule', description: 'Defines when retention cleanup should run', icon: Clock }
];

export default function DataRetentionManagement() {
  const [configs, setConfigs] = useState<DataRetentionConfig[]>([]);
  const [status, setStatus] = useState<DataRetentionStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [editingConfig, setEditingConfig] = useState<DataRetentionConfig | null>(null);
  const [newConfig, setNewConfig] = useState({
    dataType: '',
    name: '',
    description: '',
    retentionDays: 30,
    archiveDays: 90,
    scheduleCron: '0 0 2 * * ?',
    executorClass: '',
    enabled: true
  });
  const [defaultConfigs, setDefaultConfigs] = useState<DataRetentionConfig[]>([]);
  const [availableJobs, setAvailableJobs] = useState<Array<{value: string, label: string, description: string}>>([]);
  const [notificationModal, setNotificationModal] = useState<{
    open: boolean;
    type: NotificationType;
    title?: string;
    message: string;
  }>({ open: false, type: 'info', message: '' });

  const showNotification = (type: NotificationType, message: string, title?: string) => {
    setNotificationModal({ open: true, type, title, message });
  };

  // Toast-compatible wrapper for easier migration
  const toast = ({ title, description, variant }: { title: string; description: string; variant?: string }) => {
    const type: NotificationType = variant === 'destructive' ? 'error' : 'success';
    showNotification(type, description, title);
  };

  // Load configurations and status
  useEffect(() => {
    loadConfigurations();
    loadStatus();
    loadDefaultConfigurations();
    loadAvailableJobs();
  }, []);

  const loadConfigurations = async () => {
    try {
      const response = await api.get('/api/admin/data-retention');
      const configs = response.data?.data?.statistics?.configurations || [];
      setConfigs(configs);
    } catch (error) {
      console.error('Error loading configurations:', error);
      toast({
        variant: 'destructive',
        title: 'Error',
        description: 'Failed to load retention configurations'
      });
    } finally {
      setLoading(false);
    }
  };

  const loadStatus = async () => {
    try {
      const response = await api.get('/api/admin/data-retention/status');
      setStatus(response.data);
    } catch (error) {
      console.error('Error loading status:', error);
    }
  };

  const loadDefaultConfigurations = async () => {
    try {
      const response = await api.get('/api/admin/data-retention/defaults');
      setDefaultConfigs(response.data);
    } catch (error) {
      console.error('Error loading default configurations:', error);
    }
  };

  const loadAvailableJobs = async () => {
    try {
      const response = await api.get('/api/admin/data-retention/available-jobs');
      const jobs = response.data?.data?.statistics?.availableJobs || [];
      setAvailableJobs(jobs);
    } catch (error) {
      console.error('Error loading available jobs:', error);
    }
  };

  const resetNewConfigForm = () => {
    // Use database defaults if available, fallback to sensible defaults
    const defaultSchedule = defaultConfigs.find(config => config.dataType === 'SCHEDULE');
    setNewConfig({
      dataType: '',
      name: '',
      description: '',
      retentionDays: defaultSchedule?.retentionDays || 30,
      archiveDays: defaultSchedule?.archiveDays || 90,
      scheduleCron: defaultSchedule?.scheduleCron || '0 0 2 * * ?',
      executorClass: '',
      enabled: true
    });
  };

  const createConfiguration = async () => {
    try {
      await api.post('/api/admin/data-retention', newConfig);
      await loadConfigurations();
      setShowCreateDialog(false);
      resetNewConfigForm();
        toast({
          title: 'Success',
          description: 'Retention configuration created successfully'
        });
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Error',
        description: 'Failed to create configuration'
      });
    }
  };

  const updateConfiguration = async () => {
    if (!editingConfig) return;

    try {
      await api.put(`/api/admin/data-retention/${editingConfig.id}`, editingConfig);
      await loadConfigurations();
      setEditingConfig(null);
      toast({
        title: 'Success',
        description: 'Configuration updated successfully'
      });
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Error',
        description: 'Failed to update configuration'
      });
    }
  };

  const deleteConfiguration = async (id: string) => {
    try {
      await api.delete(`/api/admin/data-retention/${id}`);
      await loadConfigurations();
      toast({
        title: 'Success',
        description: 'Configuration deleted successfully'
      });
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Error',
        description: 'Failed to delete configuration'
      });
    }
  };

  const toggleConfiguration = async (id: string) => {
    try {
      await api.patch(`/api/admin/data-retention/${id}/toggle`);
      await loadConfigurations();
      toast({
        title: 'Success',
        description: 'Configuration toggled successfully'
      });
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Error',
        description: 'Failed to toggle configuration'
      });
    }
  };

  const executeManualRetention = async () => {
    try {
      await api.post('/api/admin/data-retention/execute');
      toast({
        title: 'Success',
        description: 'Data retention execution started'
      });
      // Reload status after a short delay
      setTimeout(loadStatus, 2000);
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Error',
        description: 'Failed to execute retention'
      });
    }
  };

  const getStatusIcon = (status: string) => {
    if (!status || typeof status !== 'string') {
      return <Clock className="h-4 w-4 text-gray-500" />;
    }
    
    if (status.includes('Completed')) {
      return <CheckCircle className="h-4 w-4 text-green-500" />;
    } else if (status.includes('Failed')) {
      return <XCircle className="h-4 w-4 text-red-500" />;
    } else if (status.includes('Progress')) {
      return <AlertTriangle className="h-4 w-4 text-yellow-500" />;
    }
    return <Clock className="h-4 w-4 text-gray-500" />;
  };

  const formatDateTime = (dateString?: string) => {
    if (!dateString) return 'Never';
    return new Date(dateString).toLocaleString();
  };

  if (loading) {
    return <div>Loading...</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Data Retention Management</h1>
          <p className="text-gray-600">
            Configure automated cleanup policies for log files and database tables
          </p>
        </div>
        <div className="flex gap-2">
          <Button onClick={executeManualRetention} variant="outline">
            <Play className="h-4 w-4 mr-2" />
            Execute Now
          </Button>
          <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="h-4 w-4 mr-2" />
                Add Configuration
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-md">
              <DialogHeader>
                <DialogTitle>Create Retention Configuration</DialogTitle>
                <DialogDescription>
                  Add a new data retention policy
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label>Data Type</Label>
                  <Select 
                    value={newConfig.dataType} 
                    onValueChange={(value) => setNewConfig({...newConfig, dataType: value})}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select data type" />
                    </SelectTrigger>
                    <SelectContent>
                      {DATA_TYPE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          <div className="flex items-center gap-2">
                            <option.icon className="h-4 w-4" />
                            <span>{option.label}</span>
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                
                <div className="space-y-2">
                  <Label>Name</Label>
                  <Input
                    value={newConfig.name}
                    onChange={(e) => setNewConfig({...newConfig, name: e.target.value})}
                    placeholder="Configuration name"
                  />
                </div>

                <div className="space-y-2">
                  <Label>Description</Label>
                  <textarea
                    className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    value={newConfig.description}
                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setNewConfig({...newConfig, description: e.target.value})}
                    placeholder="Description"
                    rows={2}
                  />
                </div>

                <div className="space-y-2">
                  <Label>Executor Job</Label>
                  <Select 
                    value={newConfig.executorClass} 
                    onValueChange={(value) => setNewConfig({...newConfig, executorClass: value})}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select which job will execute this policy" />
                    </SelectTrigger>
                    <SelectContent>
                      {Array.isArray(availableJobs) && availableJobs.map((job) => (
                        <SelectItem key={job.value} value={job.value}>
                          <div className="flex flex-col">
                            <span className="font-medium">{job.label}</span>
                            <span className="text-xs text-muted-foreground">{job.description}</span>
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label>Retention Days</Label>
                  <Input
                    type="number"
                    value={newConfig.retentionDays}
                    onChange={(e) => setNewConfig({...newConfig, retentionDays: parseInt(e.target.value)})}
                    min="1"
                  />
                </div>

                {newConfig.dataType === 'LOG_FILES' && (
                  <div className="space-y-2">
                    <Label>Archive Days</Label>
                    <Input
                      type="number"
                      value={newConfig.archiveDays}
                      onChange={(e) => setNewConfig({...newConfig, archiveDays: parseInt(e.target.value)})}
                      min="1"
                    />
                  </div>
                )}

                {newConfig.dataType === 'SCHEDULE' && (
                  <div className="space-y-2">
                    <Label>Cron Expression</Label>
                    <Input
                      value={newConfig.scheduleCron}
                      onChange={(e) => setNewConfig({...newConfig, scheduleCron: e.target.value})}
                      placeholder="0 0 2 * * ?"
                    />
                  </div>
                )}

                <div className="flex items-center space-x-2">
                  <Switch
                    checked={newConfig.enabled}
                    onCheckedChange={(checked) => setNewConfig({...newConfig, enabled: checked})}
                  />
                  <Label>Enabled</Label>
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={() => setShowCreateDialog(false)}>
                  Cancel
                </Button>
                <Button onClick={createConfiguration}>Create</Button>
              </div>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {/* Status Overview */}
      {status && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              {getStatusIcon(status?.status || 'Unknown')}
              Execution Status
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div>
                <div className="text-sm text-gray-500">Last Execution</div>
                <div className="font-medium">{formatDateTime(status.lastExecution)}</div>
              </div>
              <div>
                <div className="text-sm text-gray-500">Status</div>
                <div className="font-medium">{status.status}</div>
              </div>
              <div>
                <div className="text-sm text-gray-500">Files Processed</div>
                <div className="font-medium">{status.filesProcessed}</div>
              </div>
              <div>
                <div className="text-sm text-gray-500">DB Records Deleted</div>
                <div className="font-medium">{status.databaseRecordsDeleted}</div>
              </div>
            </div>
            {status.nextExecution && (
              <div className="mt-4 pt-4 border-t">
                <div className="text-sm text-gray-500">Next Scheduled Execution</div>
                <div className="font-medium">{formatDateTime(status.nextExecution)}</div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Configurations List */}
      <div className="grid gap-4">
        {Array.isArray(configs) && configs.map((config) => {
          const dataTypeOption = DATA_TYPE_OPTIONS.find(opt => opt.value === config.dataType);
          const Icon = dataTypeOption?.icon || FileText;
          
          return (
            <Card key={config.id} className={config.enabled ? '' : 'opacity-50'}>
              <CardContent className="p-6">
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-3">
                    <Icon className="h-5 w-5 mt-1 text-gray-600" />
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="font-semibold">{config.name}</h3>
                        <Badge variant={config.enabled ? 'default' : 'secondary'}>
                          {config.enabled ? 'Enabled' : 'Disabled'}
                        </Badge>
                        <Badge variant="outline">{dataTypeOption?.label}</Badge>
                      </div>
                      <p className="text-sm text-gray-600 mb-2">{config.description}</p>
                      
                      <div className="flex flex-wrap gap-4 text-sm">
                        <div>
                          <span className="text-gray-500">Retention:</span> {config.retentionDays} days
                        </div>
                        {config.archiveDays && (
                          <div>
                            <span className="text-gray-500">Archive:</span> {config.archiveDays} days
                          </div>
                        )}
                        {config.scheduleCron && (
                          <div>
                            <span className="text-gray-500">Schedule:</span> {config.scheduleCron}
                          </div>
                        )}
                      </div>

                      {config.lastExecution && (
                        <div className="mt-2 text-sm text-gray-500">
                          Last run: {formatDateTime(config.lastExecution)} - {config.lastExecutionStatus}
                          {config.lastItemsProcessed !== undefined && (
                            <span> ({config.lastItemsProcessed} items)</span>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                  
                  <div className="flex items-center gap-2">
                    <Switch
                      checked={config.enabled}
                      onCheckedChange={() => toggleConfiguration(config.id)}
                    />
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setEditingConfig(config)}
                    >
                      <Edit className="h-4 w-4" />
                    </Button>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => {
                        if (confirm('Are you sure you want to delete this retention configuration? This action cannot be undone.')) {
                          deleteConfiguration(config.id);
                        }
                      }}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {(!Array.isArray(configs) || configs.length === 0) && (
        <Card>
          <CardContent className="text-center py-8">
            <FileText className="h-12 w-12 text-gray-400 mx-auto mb-4" />
            <h3 className="text-lg font-semibold mb-2">No Retention Configurations</h3>
            <p className="text-gray-600 mb-4">
              Get started by creating your first data retention policy
            </p>
            <Button onClick={() => setShowCreateDialog(true)}>
              <Plus className="h-4 w-4 mr-2" />
              Create Configuration
            </Button>
          </CardContent>
        </Card>
      )}

      {/* Edit Dialog */}
      {editingConfig && (
        <Dialog open={!!editingConfig} onOpenChange={() => setEditingConfig(null)}>
          <DialogContent className="max-w-md">
            <DialogHeader>
              <DialogTitle>Edit Configuration</DialogTitle>
              <DialogDescription>
                Update the retention configuration settings
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label>Name</Label>
                <Input
                  value={editingConfig.name}
                  onChange={(e) => setEditingConfig({...editingConfig, name: e.target.value})}
                />
              </div>

              <div className="space-y-2">
                <Label>Description</Label>
                <textarea
                  className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                  value={editingConfig.description}
                  onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setEditingConfig({...editingConfig, description: e.target.value})}
                  rows={2}
                />
              </div>

              <div className="space-y-2">
                <Label>Executor Job</Label>
                <Select 
                  value={editingConfig.executorClass} 
                  onValueChange={(value) => setEditingConfig({...editingConfig, executorClass: value})}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select which job will execute this policy" />
                  </SelectTrigger>
                  <SelectContent>
                    {Array.isArray(availableJobs) && availableJobs.map((job) => (
                      <SelectItem key={job.value} value={job.value}>
                        <div className="flex flex-col">
                          <span className="font-medium">{job.label}</span>
                          <span className="text-xs text-muted-foreground">{job.description}</span>
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>Retention Days</Label>
                <Input
                  type="number"
                  value={editingConfig.retentionDays}
                  onChange={(e) => setEditingConfig({...editingConfig, retentionDays: parseInt(e.target.value)})}
                  min="1"
                />
              </div>

              {editingConfig.dataType === 'LOG_FILES' && (
                <div className="space-y-2">
                  <Label>Archive Days</Label>
                  <Input
                    type="number"
                    value={editingConfig.archiveDays || 0}
                    onChange={(e) => setEditingConfig({...editingConfig, archiveDays: parseInt(e.target.value)})}
                    min="1"
                  />
                </div>
              )}

              {editingConfig.dataType === 'SCHEDULE' && (
                <div className="space-y-2">
                  <Label>Cron Expression</Label>
                  <Input
                    value={editingConfig.scheduleCron || ''}
                    onChange={(e) => setEditingConfig({...editingConfig, scheduleCron: e.target.value})}
                    placeholder="0 0 2 * * ?"
                  />
                </div>
              )}

              <div className="flex items-center space-x-2">
                <Switch
                  checked={editingConfig.enabled}
                  onCheckedChange={(checked) => setEditingConfig({...editingConfig, enabled: checked})}
                />
                <Label>Enabled</Label>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setEditingConfig(null)}>
                Cancel
              </Button>
              <Button onClick={updateConfiguration}>Save Changes</Button>
            </div>
          </DialogContent>
        </Dialog>
      )}

      {/* Notification Modal */}
      <NotificationModal
        open={notificationModal.open}
        onOpenChange={(open) => setNotificationModal(prev => ({ ...prev, open }))}
        type={notificationModal.type}
        title={notificationModal.title}
        message={notificationModal.message}
      />
    </div>
  );
}