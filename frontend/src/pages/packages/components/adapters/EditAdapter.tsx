import React, { useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Save, FileText, Server, Mail } from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import toast from 'react-hot-toast';
import AdapterConfigFields from './AdapterConfigFields';

interface EditAdapterProps {
  packageId: string;
  adapterId: string;
  onSuccess: () => void;
  onCancel: () => void;
}

interface AdapterFormData {
  name: string;
  description: string;
  type: 'FILE' | 'SFTP' | 'EMAIL';
  direction: 'SENDER' | 'RECEIVER';
  active: boolean;
  configuration: Record<string, unknown>;
}

/**
 * Clean up configuration to only include fields relevant to current selections.
 * Removes fields that aren't visible on the form based on adapter type, direction, and mode selections.
 */
function cleanupConfiguration(
  config: Record<string, unknown>,
  type: string,
  direction: string
): Record<string, unknown> {
  const cleaned: Record<string, unknown> = {};

  // Helper to copy field if it has a value
  const copyIfSet = (key: string) => {
    if (config[key] !== undefined && config[key] !== '' && config[key] !== null) {
      cleaned[key] = config[key];
    }
  };

  if (type === 'FILE') {
    if (direction === 'SENDER') {
      // Core sender fields
      copyIfSet('sourceDirectory');
      copyIfSet('filePattern');
      copyIfSet('exclusionMask');

      // Processing parameters
      copyIfSet('retryInterval');
      copyIfSet('emptyFileHandling');
      copyIfSet('postProcessAction');
      copyIfSet('fileType');

      // Archive fields - only if postProcessAction is ARCHIVE
      if (config.postProcessAction === 'ARCHIVE') {
        copyIfSet('archiveDirectory');
        copyIfSet('archiveFileNaming');
        copyIfSet('createArchiveDirectory');
        copyIfSet('compressArchive');
      }

      // Error handling
      copyIfSet('archiveFaultySourceFiles');
      if (config.archiveFaultySourceFiles) {
        copyIfSet('archiveErrorDirectory');
      }
      copyIfSet('processReadOnlyFiles');
      copyIfSet('processingSequence');

      // Advanced parameters
      copyIfSet('msecsToWaitBeforeModificationCheck');
      copyIfSet('maximumFileSize');

      // Scheduler settings - SENDER only
      copyIfSet('scheduleType');
      copyIfSet('scheduleMode');

      // Schedule mode specific fields
      if (config.scheduleMode === 'OnTime') {
        copyIfSet('onTimeValue');
      } else if (config.scheduleMode === 'Every') {
        copyIfSet('everyInterval');
        copyIfSet('everyStartTime');
        copyIfSet('everyEndTime');
      }

      // Schedule type specific fields
      if (config.scheduleType === 'Weekly') {
        copyIfSet('weeklyDays');
      } else if (config.scheduleType === 'Monthly') {
        copyIfSet('monthlyDay');
      }

    } else {
      // RECEIVER fields
      copyIfSet('targetDirectory');
      copyIfSet('createTargetDirectory');
      copyIfSet('outputFilenameMode');
      copyIfSet('fileType');
      if (config.outputFilenameMode === 'Custom') {
        copyIfSet('customFilenamePattern');
      }
      copyIfSet('fileConstructionMode');
      copyIfSet('writeMode');
      copyIfSet('emptyMessageHandling');
      copyIfSet('maximumConcurrency');
      copyIfSet('msecsToWaitBeforeModificationCheck');
      copyIfSet('maximumFileSize');
    }
  } else if (type === 'SFTP') {
    // Common SFTP fields
    copyIfSet('host');
    copyIfSet('port');
    copyIfSet('username');
    copyIfSet('authenticationType');

    // Auth type specific fields
    if (config.authenticationType === 'USERNAME_PASSWORD' || config.authenticationType === 'DUAL') {
      copyIfSet('password');
    }
    if (config.authenticationType === 'SSH_KEY' || config.authenticationType === 'DUAL') {
      copyIfSet('sshKeyName');
      copyIfSet('sshKeyId');
      copyIfSet('sshKeyPassphrase');
    }

    copyIfSet('strictHostKeyChecking');
    copyIfSet('serverFingerprint');

    if (direction === 'SENDER') {
      copyIfSet('sourceDirectory');
      copyIfSet('filePattern');
      copyIfSet('postProcessAction');

      if (config.postProcessAction === 'ARCHIVE') {
        copyIfSet('archiveDirectory');
        copyIfSet('archiveWithTimestamp');
        copyIfSet('compressionType');
      }
      if (config.postProcessAction === 'KEEP_AND_MARK') {
        copyIfSet('processedDirectory');
        copyIfSet('processedFileSuffix');
      }
      if (config.postProcessAction === 'KEEP_AND_REPROCESS') {
        copyIfSet('reprocessingDelay');
      }
      if (config.postProcessAction === 'DELETE') {
        copyIfSet('confirmDelete');
        copyIfSet('deleteBackupDirectory');
      }

      // Scheduler settings - SENDER only
      copyIfSet('scheduleType');
      copyIfSet('scheduleMode');

      if (config.scheduleMode === 'OnTime') {
        copyIfSet('onTimeValue');
      } else if (config.scheduleMode === 'Every') {
        copyIfSet('everyInterval');
        copyIfSet('everyStartTime');
        copyIfSet('everyEndTime');
      }

      if (config.scheduleType === 'Weekly') {
        copyIfSet('weeklyDays');
      } else if (config.scheduleType === 'Monthly') {
        copyIfSet('monthlyDay');
      }

    } else {
      // RECEIVER fields
      copyIfSet('targetDirectory');
      copyIfSet('remoteFilePermissions');
      copyIfSet('createRemoteDirectory');
      copyIfSet('useTemporaryFileName');
      copyIfSet('temporaryFileSuffix');
      copyIfSet('outputFilenameMode');
      if (config.outputFilenameMode === 'Custom') {
        copyIfSet('customFilenamePattern');
      }
    }
  } else if (type === 'EMAIL') {
    // Email adapter fields
    copyIfSet('smtpHost');
    copyIfSet('smtpPort');
    copyIfSet('smtpUsername');
    copyIfSet('smtpPassword');
    copyIfSet('useTls');
    copyIfSet('fromAddress');

    if (direction === 'RECEIVER') {
      copyIfSet('recipients');
      copyIfSet('subject');
      copyIfSet('bodyTemplate');
      copyIfSet('attachmentFilename');
    }
  }

  return cleaned;
}

const EditAdapter: React.FC<EditAdapterProps> = ({ packageId, adapterId, onSuccess, onCancel }) => {
  const queryClient = useQueryClient();
  const [selectedType, setSelectedType] = useState<'FILE' | 'SFTP' | 'EMAIL'>('FILE');

  const { register, handleSubmit, control, watch, setValue, reset, formState: { errors } } = useForm<AdapterFormData>({
    defaultValues: {
      name: '',
      description: '',
      type: 'FILE',
      direction: 'SENDER',
      active: true,
      configuration: {},
    },
  });

  const watchType = watch('type');
  const watchDirection = watch('direction');

  // Fetch existing adapter
  const { data: adapterData, isLoading: adapterLoading } = useQuery({
    queryKey: ['adapter', adapterId],
    queryFn: async () => {
      const response = await api.get(`/api/adapters/${adapterId}`);
      return response.data?.data || response.data;
    },
    enabled: !!adapterId,
  });

  // Fetch SSH keys for SFTP authentication
  const { data: sshKeysData = [] } = useQuery({
    queryKey: ['ssh-keys'],
    queryFn: async () => {
      const response = await api.get('/api/ssh-keys');
      return response.data?.data || response.data || [];
    },
    staleTime: 5 * 60 * 1000,
  });

  // Populate form when adapter data loads
  useEffect(() => {
    if (adapterData) {
      reset({
        name: adapterData.name || '',
        description: adapterData.description || '',
        type: adapterData.type || adapterData.adapterType || 'FILE',
        direction: adapterData.direction || 'SENDER',
        active: adapterData.active ?? true,
        configuration: adapterData.configuration || {},
      });
      setSelectedType(adapterData.type || adapterData.adapterType || 'FILE');
    }
  }, [adapterData, reset]);

  const updateMutation = useMutation({
    mutationFn: async (data: AdapterFormData) => {
      const payload = {
        adapter: {
          name: data.name,
          description: data.description,
          adapterType: data.type,
          direction: data.direction,
          active: data.active,
          configuration: data.configuration
        },
        packageId
      };
      return api.put(`/api/adapters/${adapterId}`, payload);
    },
    onSuccess: () => {
      toast.success('Adapter updated successfully');
      queryClient.invalidateQueries({ queryKey: ['package-adapters', packageId] });
      queryClient.invalidateQueries({ queryKey: ['adapter', adapterId] });
      onSuccess();
    },
    onError: (err: Error) => {
      toast.error(err.message || 'Failed to update adapter');
    },
  });

  const onSubmit = (data: AdapterFormData) => {
    // Clean up configuration to only include fields relevant to current selections
    const cleanedConfig = cleanupConfiguration(data.configuration, data.type, data.direction);
    updateMutation.mutate({
      ...data,
      configuration: cleanedConfig
    });
  };

  const getAdapterIcon = (type: string) => {
    switch (type) {
      case 'FILE': return <FileText className="h-5 w-5" />;
      case 'SFTP': return <Server className="h-5 w-5" />;
      case 'EMAIL': return <Mail className="h-5 w-5" />;
      default: return <FileText className="h-5 w-5" />;
    }
  };

  if (adapterLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center gap-3">
          <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin" />
          <span className="text-muted-foreground">Loading adapter...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="sm" onClick={onCancel}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        <div>
          <h1 className="text-2xl font-bold">Edit Adapter</h1>
          <p className="text-muted-foreground">Modify adapter configuration</p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {/* Basic Information */}
        <Card>
          <CardHeader>
            <CardTitle>Basic Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="name">Adapter Name *</Label>
                <Input
                  id="name"
                  placeholder="Enter adapter name"
                  {...register('name', {
                    required: 'Adapter name is required',
                    minLength: { value: 3, message: 'Name must be at least 3 characters' }
                  })}
                />
                {errors.name && (
                  <p className="text-sm text-destructive">{errors.name.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="type">Adapter Type *</Label>
                <Controller
                  name="type"
                  control={control}
                  rules={{ required: 'Adapter type is required' }}
                  render={({ field }) => (
                    <Select
                      value={field.value}
                      onValueChange={(value) => {
                        field.onChange(value);
                        setSelectedType(value as 'FILE' | 'SFTP' | 'EMAIL');
                      }}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Select adapter type" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="FILE">File Adapter</SelectItem>
                        <SelectItem value="SFTP">SFTP Adapter</SelectItem>
                        <SelectItem value="EMAIL">Email Adapter</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Input
                id="description"
                placeholder="Enter adapter description"
                {...register('description')}
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="direction">Direction *</Label>
                <Controller
                  name="direction"
                  control={control}
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger>
                        <SelectValue placeholder="Select direction" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="SENDER">Sender</SelectItem>
                        <SelectItem value="RECEIVER">Receiver</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>

              <div className="flex items-center space-x-3 pt-8">
                <Controller
                  name="active"
                  control={control}
                  render={({ field }) => (
                    <div className="flex items-center space-x-3">
                      <Switch
                        id="active"
                        checked={Boolean(field.value)}
                        onCheckedChange={field.onChange}
                      />
                      <Label htmlFor="active">
                        {field.value ? 'Active' : 'Inactive'}
                      </Label>
                    </div>
                  )}
                />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Type-Specific Configuration */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              {getAdapterIcon(watchType)}
              <span>{watchType} Configuration</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <AdapterConfigFields
              type={watchType}
              direction={watchDirection}
              control={control}
              register={register}
              errors={errors}
              setValue={setValue}
              sshKeysData={sshKeysData}
            />
          </CardContent>
        </Card>

        {/* Action Buttons */}
        <div className="flex items-center gap-4">
          <Button
            type="submit"
            disabled={updateMutation.isPending}
          >
            <Save className="h-4 w-4 mr-2" />
            {updateMutation.isPending ? 'Saving...' : 'Save Changes'}
          </Button>
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
};

export default EditAdapter;
