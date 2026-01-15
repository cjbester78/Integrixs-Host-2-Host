import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Pencil, FileText, Server, Mail, Trash2 } from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';

interface ViewAdapterProps {
  packageId: string;
  adapterId: string;
  onEdit: () => void;
  onDelete: () => void;
  onBack: () => void;
}

// Helper component for read-only fields
const ReadOnlyField: React.FC<{ label: string; value?: string | number | boolean; type?: 'text' | 'password' }> = ({
  label,
  value,
  type = 'text'
}) => (
  <div className="space-y-2">
    <Label>{label}</Label>
    <Input
      value={type === 'password' && value ? '••••••••' : (value?.toString() || '-')}
      readOnly
      className="bg-muted cursor-default"
    />
  </div>
);

// Helper component for read-only checkbox
const ReadOnlyCheckbox: React.FC<{ label: string; checked?: boolean }> = ({ label, checked }) => (
  <div className="flex items-center space-x-2">
    <input
      type="checkbox"
      checked={Boolean(checked)}
      disabled
      className="w-4 h-4 cursor-default"
    />
    <Label className="text-muted-foreground">{label}</Label>
  </div>
);

// Helper component for read-only switch
const ReadOnlySwitch: React.FC<{ label: string; checked?: boolean; description?: string }> = ({ label, checked, description }) => (
  <div className="space-y-2">
    <div className="flex items-center space-x-2">
      <Switch checked={Boolean(checked)} disabled className="cursor-default" />
      <Label className="text-muted-foreground">{label}</Label>
    </div>
    {description && <p className="text-xs text-muted-foreground">{description}</p>}
  </div>
);

const ViewAdapter: React.FC<ViewAdapterProps> = ({ packageId, adapterId, onEdit, onDelete, onBack }) => {
  // Fetch adapter details
  const { data: adapter, isLoading } = useQuery({
    queryKey: ['adapter', adapterId],
    queryFn: async () => {
      const response = await api.get(`/api/adapters/${adapterId}`);
      return response.data?.data || response.data;
    },
    enabled: !!adapterId,
  });

  const getAdapterIcon = (type: string) => {
    switch (type) {
      case 'FILE': return <FileText className="h-5 w-5" />;
      case 'SFTP': return <Server className="h-5 w-5" />;
      case 'EMAIL': return <Mail className="h-5 w-5" />;
      default: return <FileText className="h-5 w-5" />;
    }
  };

  const getStatusBadge = (status: string, active: boolean) => {
    if (!active) return <Badge variant="outline">Disabled</Badge>;
    switch (status) {
      case 'STARTED': return <Badge variant="default" className="bg-green-500">Running</Badge>;
      case 'STOPPED': return <Badge variant="secondary">Stopped</Badge>;
      case 'ERROR': return <Badge variant="destructive">Error</Badge>;
      default: return <Badge variant="secondary">{status}</Badge>;
    }
  };

  const getDirectionBadge = (direction: string) => {
    return (
      <Badge variant="outline" className={direction === 'SENDER' ? 'bg-amber-100 text-amber-800' : 'bg-cyan-100 text-cyan-800'}>
        {direction === 'SENDER' ? 'Sender' : 'Receiver'}
      </Badge>
    );
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center gap-3">
          <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin" />
          <span className="text-muted-foreground">Loading adapter...</span>
        </div>
      </div>
    );
  }

  if (!adapter) {
    return (
      <div className="text-center py-12">
        <p className="text-muted-foreground">Adapter not found</p>
        <Button variant="outline" onClick={onBack} className="mt-4">
          Go Back
        </Button>
      </div>
    );
  }

  const adapterType = adapter.type || adapter.adapterType || 'FILE';
  const direction = adapter.direction || 'SENDER';
  const config = adapter.configuration || {};

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" onClick={onBack}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back
          </Button>
          <div>
            <h1 className="text-2xl font-bold">{adapter.name}</h1>
            <p className="text-muted-foreground">{adapter.description || 'View adapter configuration'}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={onEdit}>
            <Pencil className="h-4 w-4 mr-2" />
            Edit
          </Button>
          <Button variant="outline" size="sm" className="text-destructive" onClick={onDelete}>
            <Trash2 className="h-4 w-4 mr-2" />
            Delete
          </Button>
        </div>
      </div>

      {/* Status Badges */}
      <div className="flex items-center gap-3">
        {getStatusBadge(adapter.status, adapter.active)}
        <Badge variant="outline">{adapterType}</Badge>
        {getDirectionBadge(direction)}
      </div>

      {/* Basic Information */}
      <Card>
        <CardHeader>
          <CardTitle>Basic Information</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <ReadOnlyField label="Adapter Name" value={adapter.name} />
            <ReadOnlyField label="Adapter Type" value={adapterType === 'FILE' ? 'File Adapter' : adapterType === 'SFTP' ? 'SFTP Adapter' : 'Email Adapter'} />
          </div>
          <ReadOnlyField label="Description" value={adapter.description} />
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <ReadOnlyField label="Direction" value={direction === 'SENDER' ? 'Sender' : 'Receiver'} />
            <div className="flex items-center space-x-3 pt-8">
              <Switch checked={Boolean(adapter.active)} disabled className="cursor-default" />
              <Label className="text-muted-foreground">{adapter.active ? 'Active' : 'Inactive'}</Label>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Type-Specific Configuration */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            {getAdapterIcon(adapterType)}
            <span>{adapterType} Configuration</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {adapterType === 'FILE' && (
            <FileAdapterReadOnly direction={direction} config={config} />
          )}
          {adapterType === 'SFTP' && (
            <SftpAdapterReadOnly direction={direction} config={config} />
          )}
          {adapterType === 'EMAIL' && (
            <EmailAdapterReadOnly direction={direction} config={config} />
          )}
        </CardContent>
      </Card>

      {/* Metadata */}
      <Card>
        <CardHeader>
          <CardTitle>Metadata</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div>
              <div className="text-sm text-muted-foreground">Created</div>
              <div className="font-medium">
                {adapter.createdAt ? new Date(adapter.createdAt).toLocaleString() : 'N/A'}
              </div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Last Updated</div>
              <div className="font-medium">
                {adapter.updatedAt ? new Date(adapter.updatedAt).toLocaleString() : 'N/A'}
              </div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Adapter ID</div>
              <div className="font-mono text-xs">{adapter.id}</div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

// FILE Adapter Read-Only Configuration
const FileAdapterReadOnly: React.FC<{ direction: string; config: Record<string, any> }> = ({ direction, config }) => (
  <div className="space-y-6">
    {/* Directory Configuration */}
    <div>
      <h4 className="text-sm font-medium mb-4">{direction === 'SENDER' ? 'Source' : 'Target'} Configuration</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {direction === 'SENDER' ? (
          <>
            <ReadOnlyField label="Source Directory" value={config.sourceDirectory} />
            <ReadOnlyField label="File Name Mask" value={config.filePattern || '*'} />
            <ReadOnlyField label="Exclusion Mask" value={config.exclusionMask} />
          </>
        ) : (
          <>
            <ReadOnlyField label="Target Directory" value={config.targetDirectory} />
            <ReadOnlyCheckbox label="Create Target Directory" checked={config.createTargetDirectory} />
          </>
        )}
      </div>
    </div>

    {/* Processing Parameters - SENDER */}
    {direction === 'SENDER' && (
      <div>
        <h4 className="text-sm font-medium mb-4">Processing Parameters</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ReadOnlyField label="Retry Interval (secs)" value={config.retryInterval || '60'} />
          <ReadOnlyField label="Empty File Handling" value={config.emptyFileHandling || 'Do Not Create Message'} />
          <ReadOnlyField label="Post Process Action" value={config.postProcessAction || 'ARCHIVE'} />
          <ReadOnlyField label="File Type" value={config.fileType || 'Binary'} />
        </div>
      </div>
    )}

    {/* File Output Configuration - RECEIVER */}
    {direction === 'RECEIVER' && (
      <div>
        <h4 className="text-sm font-medium mb-4">File Output Configuration</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ReadOnlyField label="Output Filename Mode" value={config.outputFilenameMode || 'UseOriginal'} />
          <ReadOnlyField label="File Type" value={config.fileType || 'Binary'} />
        </div>
        {config.outputFilenameMode === 'Custom' && (
          <div className="mt-4">
            <ReadOnlyField label="Custom Filename Pattern" value={config.customFilenamePattern} />
          </div>
        )}
      </div>
    )}

    {/* Archive Configuration - SENDER when Archive mode */}
    {direction === 'SENDER' && config.postProcessAction === 'ARCHIVE' && (
      <div>
        <h4 className="text-sm font-medium mb-4">Archive Configuration</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ReadOnlyField label="Archive Directory" value={config.archiveDirectory} />
          <ReadOnlyField label="Archive File Naming" value={config.archiveFileNaming || 'Original'} />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          <ReadOnlyCheckbox label="Create Archive Directory if it doesn't exist" checked={config.createArchiveDirectory} />
          <ReadOnlyCheckbox label="Compress archived files" checked={config.compressArchive} />
        </div>
      </div>
    )}

    {/* Error Handling - SENDER */}
    {direction === 'SENDER' && (
      <div>
        <h4 className="text-sm font-medium mb-4">Error Handling</h4>
        <div className="space-y-4">
          <ReadOnlyCheckbox label="Archive Faulty Source Files" checked={config.archiveFaultySourceFiles} />
          {config.archiveFaultySourceFiles && (
            <ReadOnlyField label="Directory for Archiving Files with Errors" value={config.archiveErrorDirectory} />
          )}
          <ReadOnlyCheckbox label="Process Read-Only Files" checked={config.processReadOnlyFiles} />
          <ReadOnlyField label="Processing Sequence" value={config.processingSequence || 'By Name'} />
        </div>
      </div>
    )}

    {/* File Construction - RECEIVER */}
    {direction === 'RECEIVER' && (
      <div>
        <h4 className="text-sm font-medium mb-4">File Construction</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ReadOnlyField label="File Construction Mode" value={config.fileConstructionMode || 'Create'} />
          <ReadOnlyField label="Write Mode" value={config.writeMode || 'Directly'} />
          <ReadOnlyField label="Empty Message Handling" value={config.emptyMessageHandling || 'Write Empty File'} />
          <ReadOnlyField label="Maximum Concurrency" value={config.maximumConcurrency || '1'} />
        </div>
      </div>
    )}

    {/* Advanced Parameters */}
    <div>
      <h4 className="text-sm font-medium mb-4">Advanced Parameters</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ReadOnlyField label="Msecs to Wait Before Modification Check" value={config.msecsToWaitBeforeModificationCheck || '0'} />
        <ReadOnlyField label="Maximum File Size (Bytes)" value={config.maximumFileSize || '0'} />
      </div>
    </div>

    {/* Scheduler Settings - SENDER only */}
    {direction === 'SENDER' && (
      <div>
        <h4 className="text-sm font-medium mb-4">Scheduler Settings</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ReadOnlyField label="Schedule Type" value={config.scheduleType || 'Daily'} />
          <ReadOnlyField label="Schedule Mode" value={config.scheduleMode === 'Every' ? 'Every Interval' : 'On Time'} />
        </div>
        {config.scheduleMode === 'OnTime' && (
          <div className="mt-4">
            <ReadOnlyField label="Execution Time" value={config.onTimeValue || '20:27'} />
          </div>
        )}
        {config.scheduleMode === 'Every' && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
            <ReadOnlyField label="Every Interval" value={config.everyInterval || '1 min'} />
            <ReadOnlyField label="Start Time" value={config.everyStartTime || '00:00'} />
            <ReadOnlyField label="End Time" value={config.everyEndTime || '24:00'} />
          </div>
        )}
        {config.scheduleType === 'Weekly' && config.weeklyDays && (
          <div className="mt-4 space-y-2">
            <Label>Days of Week</Label>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'].map((day) => (
                <ReadOnlyCheckbox key={day} label={day.charAt(0).toUpperCase() + day.slice(1)} checked={config.weeklyDays?.[day]} />
              ))}
            </div>
          </div>
        )}
        {config.scheduleType === 'Monthly' && (
          <div className="mt-4">
            <ReadOnlyField label="Day of Month" value={config.monthlyDay || '1'} />
          </div>
        )}
      </div>
    )}
  </div>
);

// SFTP Adapter Read-Only Configuration
const SftpAdapterReadOnly: React.FC<{ direction: string; config: Record<string, any> }> = ({ direction, config }) => (
  <div className="space-y-6">
    {/* Connection Settings */}
    <div>
      <h4 className="text-sm font-medium mb-4">Connection Settings</h4>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <ReadOnlyField label="Host" value={config.host} />
        <ReadOnlyField label="Port" value={config.port || '22'} />
        <ReadOnlyField label="Authentication Type" value={
          config.authenticationType === 'USERNAME_PASSWORD' ? 'Username/Password' :
          config.authenticationType === 'PUBLIC_KEY' ? 'Public Key' :
          config.authenticationType === 'DUAL' ? 'Dual Authentication' : config.authenticationType
        } />
      </div>

      {/* Authentication Fields */}
      <div className="space-y-4 mt-4">
        {(config.authenticationType === 'USERNAME_PASSWORD' || config.authenticationType === 'DUAL') && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <ReadOnlyField label="Username" value={config.username} />
            <ReadOnlyField label="Password" value={config.password} type="password" />
          </div>
        )}
        <ReadOnlyField label="Server Fingerprint" value={config.serverFingerprint} />
        {(config.authenticationType === 'PUBLIC_KEY' || config.authenticationType === 'DUAL') && (
          <ReadOnlyField label="SSH Key" value={config.sshKeyId || 'Not selected'} />
        )}
      </div>
    </div>

    {/* RECEIVER - Remote Destination Settings */}
    {direction === 'RECEIVER' && (
      <>
        <div>
          <h4 className="text-sm font-medium mb-4">Remote Destination Settings</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <ReadOnlyField label="Target Directory" value={config.targetDirectory} />
            <ReadOnlyField label="Remote File Permissions" value={config.remoteFilePermissions || '644'} />
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
            <ReadOnlyCheckbox label="Create Remote Directory" checked={config.createRemoteDirectory} />
            <ReadOnlyCheckbox label="Use Temporary File Name" checked={config.useTemporaryFileName} />
          </div>
          <div className="mt-4">
            <ReadOnlyField label="Temporary File Suffix" value={config.temporaryFileSuffix || '.tmp'} />
          </div>
        </div>

        {/* Output Filename Configuration */}
        <div>
          <h4 className="text-sm font-medium mb-4">Output Filename Configuration</h4>
          <ReadOnlyField label="Output Filename Mode" value={config.outputFilenameMode || 'UseOriginal'} />
          {config.outputFilenameMode === 'Custom' && (
            <div className="mt-4">
              <ReadOnlyField label="Custom Filename Pattern" value={config.customFilenamePattern} />
            </div>
          )}
        </div>
      </>
    )}

    {/* Connection Properties */}
    <div>
      <h4 className="text-sm font-medium mb-4">Connection Properties</h4>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <ReadOnlyField label="Connection Timeout (ms)" value={config.connectionTimeout || '30000'} />
        <ReadOnlyField label="Socket Timeout (ms)" value={config.socketTimeout || '60000'} />
        <ReadOnlyField label="Keep Alive" value={config.keepAlive ? 'Enabled' : 'Disabled'} />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
        <ReadOnlyField label="Session Timeout (ms)" value={config.sessionTimeout || '60000'} />
        <ReadOnlyField label="Channel Timeout (ms)" value={config.channelTimeout || '30000'} />
        <ReadOnlyField label="Connection Pool Size" value={config.connectionPoolSize || '5'} />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
        <ReadOnlyField label="Strict Host Key Checking" value={config.strictHostKeyChecking ? 'Enabled' : 'Disabled'} />
        <ReadOnlyField label="Connection Mode" value={config.connectionMode || 'Permanently'} />
      </div>
    </div>

    {/* Transfer Settings */}
    <div>
      <h4 className="text-sm font-medium mb-4">Transfer Settings</h4>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <ReadOnlyField label="Max Retry Attempts" value={config.maxRetryAttempts || '3'} />
        <ReadOnlyField label="Retry Delay (ms)" value={config.retryDelayMs || '5000'} />
        <ReadOnlyField label="Max File Size (MB)" value={config.maxFileSize || '100'} />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
        <ReadOnlyField label="Transfer Buffer Size (KB)" value={config.transferBufferSize || '32'} />
        <ReadOnlyField label="Transfer Mode" value={config.transferMode || 'BINARY'} />
      </div>
    </div>

    {/* SENDER - Remote Pickup Settings */}
    {direction === 'SENDER' && (
      <>
        <div>
          <h4 className="text-sm font-medium mb-4">Remote Pickup Settings</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <ReadOnlyField label="Source Directory" value={config.sourceDirectory} />
            <ReadOnlyField label="Filename Pattern" value={config.filename} />
          </div>
          <div className="mt-4">
            <ReadOnlyField label="Exclude Pattern" value={config.excludePattern} />
          </div>
        </div>

        {/* Scheduler Settings */}
        <div>
          <h4 className="text-sm font-medium mb-4">Scheduler Settings</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <ReadOnlyField label="Schedule Type" value={config.scheduleType || 'Daily'} />
            <ReadOnlyField label="Schedule Mode" value={config.scheduleMode === 'Every' ? 'Every Interval' : 'On Time'} />
          </div>
          {config.scheduleMode === 'OnTime' && (
            <div className="mt-4">
              <ReadOnlyField label="Execution Time" value={config.onTimeValue || '20:27'} />
            </div>
          )}
          {config.scheduleMode === 'Every' && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
              <ReadOnlyField label="Every Interval" value={config.everyInterval || '1 min'} />
              <ReadOnlyField label="Start Time" value={config.everyStartTime || '00:00'} />
              <ReadOnlyField label="End Time" value={config.everyEndTime || '24:00'} />
            </div>
          )}
          {config.scheduleType === 'Weekly' && config.weeklyDays && (
            <div className="mt-4 space-y-2">
              <Label>Days of Week</Label>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'].map((day) => (
                  <ReadOnlyCheckbox key={day} label={day.charAt(0).toUpperCase() + day.slice(1)} checked={config.weeklyDays?.[day]} />
                ))}
              </div>
            </div>
          )}
          {config.scheduleType === 'Monthly' && (
            <div className="mt-4">
              <ReadOnlyField label="Day of Month" value={config.monthlyDay || '1'} />
            </div>
          )}
        </div>

        {/* Empty File Handling */}
        <div>
          <h4 className="text-sm font-medium mb-4">Empty File Handling</h4>
          <ReadOnlyField label="Empty File Handling" value={config.emptyFileHandling || 'Do Not Create Message'} />
        </div>

        {/* Post-Processing */}
        <div>
          <h4 className="text-sm font-medium mb-4">Post-Processing</h4>
          <ReadOnlyField label="Post Process Action" value={
            config.postProcessAction === 'ARCHIVE' ? 'Archive' :
            config.postProcessAction === 'KEEP_AND_MARK' ? 'Keep File and Mark as Processed' :
            config.postProcessAction === 'KEEP_AND_REPROCESS' ? 'Keep File and Process Again' :
            config.postProcessAction === 'DELETE' ? 'Delete File' : config.postProcessAction || 'Archive'
          } />

          {config.postProcessAction === 'ARCHIVE' && (
            <div className="space-y-4 mt-4">
              <ReadOnlyField label="Archive Directory" value={config.archiveDirectory} />
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <ReadOnlyCheckbox label="Archive with Timestamp" checked={config.archiveWithTimestamp} />
                <ReadOnlyField label="Compression Type" value={config.compressionType || 'NONE'} />
              </div>
            </div>
          )}

          {config.postProcessAction === 'KEEP_AND_MARK' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
              <ReadOnlyField label="Processed Directory" value={config.processedDirectory} />
              <ReadOnlyField label="Processed File Suffix" value={config.processedFileSuffix} />
            </div>
          )}

          {config.postProcessAction === 'KEEP_AND_REPROCESS' && (
            <div className="mt-4">
              <ReadOnlyField label="Reprocessing Delay (ms)" value={config.reprocessingDelay} />
            </div>
          )}

          {config.postProcessAction === 'DELETE' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
              <ReadOnlyField label="Delete Backup Directory" value={config.deleteBackupDirectory} />
              <ReadOnlyCheckbox label="Confirm Delete" checked={config.confirmDelete} />
            </div>
          )}
        </div>
      </>
    )}
  </div>
);

// EMAIL Adapter Read-Only Configuration
const EmailAdapterReadOnly: React.FC<{ direction: string; config: Record<string, any> }> = ({ direction, config }) => (
  <div className="space-y-6">
    {/* SMTP Settings */}
    <div>
      <h4 className="text-sm font-medium mb-4">SMTP Settings</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ReadOnlyField label="SMTP Host" value={config.smtpHost} />
        <ReadOnlyField label="SMTP Port" value={config.smtpPort} />
      </div>
    </div>

    {/* SSL/TLS Settings */}
    <div>
      <h4 className="text-sm font-medium mb-4">SSL/TLS Settings</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ReadOnlySwitch
          label="Enable SSL (Port 465)"
          checked={config.sslEnabled}
          description="Use SSL for secure connection. Required for port 465."
        />
        <ReadOnlySwitch
          label="Enable STARTTLS (Port 587)"
          checked={config.startTlsEnabled}
          description="Use STARTTLS for secure connection. Typically used with port 587."
        />
      </div>
    </div>

    {/* From Address */}
    <ReadOnlyField label="From Address (Sender Email)" value={config.fromAddress} />

    {/* Authentication */}
    <div>
      <h4 className="text-sm font-medium mb-4">Authentication</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ReadOnlyField label="Email Username" value={config.smtpUsername} />
        <ReadOnlyField label="Email Password" value={config.smtpPassword} type="password" />
      </div>
    </div>

    {/* RECEIVER specific */}
    {direction === 'RECEIVER' && (
      <div className="space-y-4">
        <ReadOnlyField label="Recipients (comma-separated)" value={config.recipients} />
        <ReadOnlyField label="Email Subject" value={config.subject} />
        <div className="space-y-2">
          <Label>Email Body</Label>
          <div className="min-h-[120px] w-full rounded-md border border-input bg-muted px-3 py-2 text-sm">
            {config.body || '-'}
          </div>
          <p className="text-xs text-muted-foreground">
            Files will be attached directly from the flow execution context.
          </p>
        </div>
      </div>
    )}
  </div>
);

export default ViewAdapter;
