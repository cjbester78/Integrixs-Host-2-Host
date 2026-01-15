import React, { useState } from 'react';
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

interface CreateAdapterProps {
  packageId: string;
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

const CreateAdapter: React.FC<CreateAdapterProps> = ({ packageId, onSuccess, onCancel }) => {
  const queryClient = useQueryClient();
  const [selectedType, setSelectedType] = useState<'FILE' | 'SFTP' | 'EMAIL'>('FILE');

  const { register, handleSubmit, control, watch, setValue, formState: { errors } } = useForm<AdapterFormData>({
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

  // Fetch SSH keys for SFTP authentication
  const { data: sshKeysData = [] } = useQuery({
    queryKey: ['ssh-keys'],
    queryFn: async () => {
      const response = await api.get('/api/ssh-keys');
      return response.data?.data || response.data || [];
    },
    staleTime: 5 * 60 * 1000,
  });

  const createMutation = useMutation({
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
      return api.post('/api/adapters', payload);
    },
    onSuccess: () => {
      toast.success('Adapter created successfully');
      queryClient.invalidateQueries({ queryKey: ['package-adapters', packageId] });
      onSuccess();
    },
    onError: (err: Error) => {
      toast.error(err.message || 'Failed to create adapter');
    },
  });

  const onSubmit = (data: AdapterFormData) => {
    createMutation.mutate(data);
  };

  const getAdapterIcon = (type: string) => {
    switch (type) {
      case 'FILE': return <FileText className="h-5 w-5" />;
      case 'SFTP': return <Server className="h-5 w-5" />;
      case 'EMAIL': return <Mail className="h-5 w-5" />;
      default: return <FileText className="h-5 w-5" />;
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" onClick={onCancel}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back
          </Button>
          <div>
            <h1 className="text-2xl font-bold">Create Adapter</h1>
            <p className="text-muted-foreground">Configure a new adapter for this package</p>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
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
            disabled={createMutation.isPending}
          >
            <Save className="h-4 w-4 mr-2" />
            {createMutation.isPending ? 'Creating...' : 'Create Adapter'}
          </Button>
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
};

export default CreateAdapter;
