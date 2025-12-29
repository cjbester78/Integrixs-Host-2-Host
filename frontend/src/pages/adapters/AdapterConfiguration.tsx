import React, { useState, useEffect } from 'react'
import { useForm, Controller, useWatch } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Save, TestTube, FileText, Server, Mail, AlertTriangle, CheckCircle } from 'lucide-react'
import { adapterApi, configApi, sshKeyApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useNotifications } from '@/stores/ui'
import WebSocketStatus from '@/components/WebSocketStatus'

interface AdapterFormData {
  name: string
  bank: string
  description: string
  adapterType: 'FILE' | 'SFTP' | 'EMAIL'
  direction: 'SENDER' | 'RECEIVER'
  active: boolean
  configuration: Record<string, any>
}

const normalizeEmailTlsConfig = (config: Record<string, any>) => {
  const port = Number(config.smtpPort)
  const preferSsl = port === 465

  const sslEnabled = Boolean(config.sslEnabled)
  const startTlsEnabled = Boolean(config.startTlsEnabled)

  // Enforce strict mutual exclusion: exactly one must be true
  if (sslEnabled === startTlsEnabled) {
    return {
      ...config,
      sslEnabled: preferSsl,
      startTlsEnabled: !preferSsl,
    }
  }

  return config
}

const AdapterConfiguration: React.FC = () => {
  const { id } = useParams()
  const navigate = useNavigate()
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()

  // Fetch system configurations for default values
  const { data: systemConfigResponse } = useQuery({
    queryKey: ['system-config', 'FILE_PROCESSING'],
    queryFn: () => configApi.getConfigurationsByCategory('FILE_PROCESSING'),
    staleTime: 5 * 60 * 1000, // 5 minutes
  })

  const systemConfig = systemConfigResponse?.configurations || []
  const isEditing = Boolean(id)
  
  const [_selectedType, setSelectedType] = useState<'FILE' | 'SFTP' | 'EMAIL'>('FILE')
  const [testResult, setTestResult] = useState<any>(null)

  const { data: existingAdapter, isLoading: adapterLoading } = useQuery({
    queryKey: ['adapter', id],
    queryFn: () => adapterApi.getAdapter(id!),
    enabled: isEditing,
  })

  // Fetch SSH keys for SFTP authentication
  const { data: sshKeysData = [] } = useQuery({
    queryKey: ['ssh-keys'],
    queryFn: () => sshKeyApi.getAllKeys(),
    staleTime: 5 * 60 * 1000, // 5 minutes
  })

  const { register, handleSubmit, control, watch, setValue, formState: { errors } } = useForm<AdapterFormData>({
    defaultValues: {
      name: '',
      bank: '',
      description: '',
      adapterType: 'FILE',
      direction: 'SENDER',
      active: true,
      configuration: {},
    },
  })

  const watchType = watch('adapterType')
  const watchDirection = watch('direction')

  useEffect(() => {
    if (existingAdapter?.data) {
      const adapter = existingAdapter.data
      setValue('name', adapter.name)
      setValue('bank', adapter.bank)
      setValue('description', adapter.description)
      setValue('adapterType', adapter.adapterType)
      setValue('direction', adapter.direction)
      setValue('active', adapter.active)

      const rawConfig = adapter.configuration || {}
      const normalizedConfig = adapter.adapterType === 'EMAIL' ? normalizeEmailTlsConfig(rawConfig) : rawConfig
      setValue('configuration', normalizedConfig)

      setSelectedType(adapter.adapterType)
    }
  }, [existingAdapter, setValue])

  // Update form values when system config loads (only for new adapters)
  useEffect(() => {
    if (systemConfig.length > 0 && !isEditing) {
      const pollInterval = systemConfig.find((c: any) => c.configKey === 'file.processing.poll_interval_seconds')?.configValue
      const retryInterval = systemConfig.find((c: any) => c.configKey === 'file.processing.retry_interval_seconds')?.configValue
      const modificationDelay = systemConfig.find((c: any) => c.configKey === 'file.processing.modification_check_delay_ms')?.configValue
      const maxFileSizeMB = systemConfig.find((c: any) => c.configKey === 'file.processing.max_file_size_mb')?.configValue
      
      if (pollInterval) setValue('configuration.pollInterval', pollInterval)
      if (retryInterval) setValue('configuration.retryInterval', retryInterval)
      if (modificationDelay) setValue('configuration.msecsToWaitBeforeModificationCheck', modificationDelay)
      if (maxFileSizeMB) {
        const maxFileSizeBytes = String(parseInt(maxFileSizeMB) * 1024 * 1024)
        setValue('configuration.maximumFileSize', maxFileSizeBytes)
      }
    }
  }, [systemConfig, isEditing, setValue])


  const createMutation = useMutation({
    mutationFn: adapterApi.createAdapter,
    onSuccess: () => {
      success('Adapter Created', 'Adapter interface created successfully')
      // Add a small delay before navigation to prevent form state rendering issues
      setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: ['adapters'] })
        navigate('/adapters')
      }, 100)
    },
    onError: (err: any) => {
      error('Creation Failed', err.response?.data?.message || 'Failed to create adapter')
    },
  })

  const updateMutation = useMutation({
    mutationFn: (data: AdapterFormData) => adapterApi.updateAdapter(id!, data),
    onSuccess: () => {
      success('Adapter Updated', 'Adapter interface updated successfully')
      // Add a small delay before navigation to prevent form state rendering issues
      setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: ['adapters'] })
        queryClient.invalidateQueries({ queryKey: ['adapter', id] })
        navigate('/adapters')
      }, 100)
    },
    onError: (err: any) => {
      error('Update Failed', err.response?.data?.message || 'Failed to update adapter')
    },
  })

  const testMutation = useMutation({
    mutationFn: (data: AdapterFormData) => {
      if (id) {
        return adapterApi.testAdapter(id)
      } else {
        // For new adapters, create a temporary test with the form data
        return adapterApi.createAdapter(data).then(response => 
          adapterApi.testAdapter(response.data.id).finally(() =>
            adapterApi.deleteAdapter(response.data.id)
          )
        )
      }
    },
    onSuccess: (result) => {
      setTestResult(result)
      if (result.data?.success) {
        success('Test Successful', 'Adapter configuration test passed')
      } else {
        error('Test Failed', result.data?.message || 'Adapter test failed')
      }
    },
    onError: (err: any) => {
      error('Test Error', err.response?.data?.message || 'Failed to test adapter configuration')
    },
  })

  const onSubmit = (data: AdapterFormData) => {
    const payload: AdapterFormData =
      data.adapterType === 'EMAIL'
        ? { ...data, configuration: normalizeEmailTlsConfig(data.configuration || {}) }
        : data

    if (isEditing) {
      updateMutation.mutate(payload)
    } else {
      createMutation.mutate(payload)
    }
  }

  const handleTest = () => {
    const formData = watch()
    testMutation.mutate(formData)
  }

  const getAdapterIcon = (type: string) => {
    switch (type) {
      case 'FILE':
        return <FileText className="h-5 w-5" />
      case 'SFTP':
        return <Server className="h-5 w-5" />
      case 'EMAIL':
        return <Mail className="h-5 w-5" />
      default:
        return <FileText className="h-5 w-5" />
    }
  }

  if (adapterLoading) {
    return (
      <div className="content-spacing">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading adapter configuration...</span>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      <div className="flex items-center space-x-4 mb-6">
        <Button variant="outline" size="sm" onClick={() => navigate('/adapters')}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Adapters
        </Button>
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">
            {isEditing ? 'Edit Adapter' : 'Create New Adapter'}
          </h1>
          <div className="flex items-center space-x-4">
            <p className="text-muted-foreground">
              {isEditing ? 'Modify your adapter configuration' : 'Configure a new file transfer adapter'}
            </p>
            <WebSocketStatus size="sm" />
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* Basic Information */}
        <Card className="app-card border">
          <CardHeader>
            <CardTitle className="text-foreground">Basic Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="name">Adapter Name</Label>
                <Input
                  id="name"
                  placeholder="Enter adapter name"
                  className="bg-input border-border text-foreground"
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
                <Label htmlFor="bank">Bank</Label>
                <Input
                  id="bank"
                  placeholder="Enter bank name (e.g., FNB, ABSA)"
                  className="bg-input border-border text-foreground"
                  {...register('bank', { 
                    required: 'Bank name is required',
                    minLength: { value: 2, message: 'Bank name must be at least 2 characters' }
                  })}
                />
                {errors.bank && (
                  <p className="text-sm text-destructive">{errors.bank.message}</p>
                )}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Input
                id="description"
                placeholder="Enter adapter description"
                className="bg-input border-border text-foreground"
                {...register('description', { 
                  required: 'Description is required' 
                })}
              />
              {errors.description && (
                <p className="text-sm text-destructive">{errors.description.message}</p>
              )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="type">Adapter Type</Label>
                <Controller
                  name="adapterType"
                  control={control}
                  rules={{ required: 'Adapter type is required' }}
                  render={({ field }) => (
                    <Select
                      value={field.value}
                      onValueChange={(value) => {
                        field.onChange(value)
                        setSelectedType(value as 'FILE' | 'SFTP' | 'EMAIL')
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
                {errors.adapterType && (
                  <p className="text-sm text-destructive">{errors.adapterType.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="direction">Direction</Label>
                <Controller
                  name="direction"
                  control={control}
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger>
                        <SelectValue placeholder="Select direction" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="SENDER">Sender (Send Files Out)</SelectItem>
                        <SelectItem value="RECEIVER">Receiver (Receive Files In)</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            </div>

            <div className="flex items-center space-x-3">
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
                    <Label htmlFor="active" className="text-sm font-medium">
                      {field.value ? 'Active' : 'Inactive'}
                    </Label>
                  </div>
                )}
              />
            </div>
          </CardContent>
        </Card>

        {/* Type-Specific Configuration */}
        <Card className="app-card border">
          <CardHeader>
            <CardTitle className="flex items-center space-x-2 text-foreground">
              {getAdapterIcon(watchType)}
              <span>{watchType} Configuration</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <TypeSpecificConfiguration
              key={`${watchType}-${watchDirection}-${isEditing ? id : 'new'}`}
              type={watchType}
              direction={watchDirection}
              control={control}
              register={register}
              errors={errors}
              systemConfig={systemConfig}
              setValue={setValue}
              sshKeysData={sshKeysData}
            />
          </CardContent>
        </Card>

        {/* Test Results */}
        {testResult && (
          <Card className="app-card border">
            <CardHeader>
              <CardTitle className="flex items-center space-x-2 text-foreground">
                <TestTube className="h-5 w-5" />
                <span>Test Results</span>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className={`flex items-start space-x-3 p-4 rounded-md ${
                testResult.data?.success ? 'bg-success/10 border border-success/20' : 'bg-destructive/10 border border-destructive/20'
              }`}>
                {testResult.data?.success ? (
                  <CheckCircle className="h-5 w-5 text-success mt-0.5" />
                ) : (
                  <AlertTriangle className="h-5 w-5 text-destructive mt-0.5" />
                )}
                <div>
                  <p className={`font-medium ${testResult.data?.success ? 'text-success' : 'text-destructive'}`}>
                    {testResult.data?.success ? 'Configuration Test Passed' : 'Configuration Test Failed'}
                  </p>
                  <p className="text-sm text-muted-foreground mt-1">
                    {testResult.data?.message || 'No additional details available'}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Action Buttons */}
        <div className="flex items-center space-x-4">
          <Button
            type="submit"
            className="btn-primary"
            disabled={createMutation.isPending || updateMutation.isPending}
          >
            <Save className="h-4 w-4 mr-2" />
            {createMutation.isPending || updateMutation.isPending 
              ? (isEditing ? 'Updating...' : 'Creating...') 
              : (isEditing ? 'Update Adapter' : 'Create Adapter')
            }
          </Button>

          {/* Only show test button for SFTP adapters */}
          {watchType === 'SFTP' && (
            <Button
              type="button"
              variant="outline"
              onClick={handleTest}
              disabled={testMutation.isPending}
            >
              <TestTube className="h-4 w-4 mr-2" />
              {testMutation.isPending ? 'Testing...' : 'Test Configuration'}
            </Button>
          )}

          <Button
            type="button"
            variant="outline"
            onClick={() => navigate('/adapters')}
          >
            Cancel
          </Button>
        </div>
      </form>
    </div>
  )
}

// Type-specific configuration components
const TypeSpecificConfiguration: React.FC<{
  type: 'FILE' | 'SFTP' | 'EMAIL'
  direction: 'SENDER' | 'RECEIVER'
  control: any
  register: any
  errors: any
  systemConfig: any
  setValue: any
  sshKeysData?: any[]
}> = ({ type, direction, control, register, errors, systemConfig, setValue, sshKeysData = [] }) => {
  switch (type) {
    case 'FILE':
      return <FileAdapterConfig direction={direction} control={control} register={register} errors={errors} systemConfig={systemConfig} />
    case 'SFTP':
      return <SftpAdapterConfig direction={direction} control={control} register={register} errors={errors} systemConfig={systemConfig} sshKeysData={sshKeysData} />
    case 'EMAIL':
      return <EmailAdapterConfig direction={direction} control={control} register={register} errors={errors} systemConfig={systemConfig} setValue={setValue} />
    default:
      return null
  }
}

// File Adapter Configuration - Enhanced to match SFTP capabilities
const FileAdapterConfig: React.FC<any> = ({ direction, control, register, errors, systemConfig }) => {
  const watchArchiveFaultyFiles = useWatch({
    control,
    name: 'configuration.archiveFaultySourceFiles',
    defaultValue: false
  })

  const watchProcessingMode = useWatch({
    control,
    name: 'configuration.processingMode',
    defaultValue: 'Test'
  })

  const watchOutputFilenameMode = useWatch({
    control,
    name: 'configuration.outputFilenameMode',
    defaultValue: 'UseOriginal'
  })

  // Scheduler watch variables
  const watchScheduleMode = useWatch({
    control,
    name: 'configuration.scheduleMode',
    defaultValue: 'OnTime'
  })

  const watchScheduleType = useWatch({
    control,
    name: 'configuration.scheduleType',
    defaultValue: 'Daily'
  })

  return (
  <div className="space-y-6">
    {/* Directory Configuration */}
    <div>
      <h3 className="text-lg font-semibold text-foreground mb-4">
        {direction === 'SENDER' ? 'Source' : 'Target'} Configuration
      </h3>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {direction === 'SENDER' ? (
          <>
            <div className="space-y-2">
              <Label htmlFor="sourceDirectory">Source Directory *</Label>
              <Input
                id="sourceDirectory"
                {...register('configuration.sourceDirectory', { required: true })}
                placeholder="/data/input"
                className={errors?.configuration?.sourceDirectory ? 'border-destructive' : ''}
              />
              {errors?.configuration?.sourceDirectory && (
                <span className="text-sm text-destructive">Source directory is required</span>
              )}
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="filePattern">File Name Mask</Label>
              <Input
                id="filePattern"
                {...register('configuration.filePattern')}
                placeholder="*.xml"
                defaultValue="*"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="exclusionMask">Exclusion Mask</Label>
              <Input
                id="exclusionMask"
                {...register('configuration.exclusionMask')}
                placeholder="*.tmp"
              />
            </div>
          </>
        ) : (
          <>
            <div className="space-y-2">
              <Label htmlFor="targetDirectory">Target Directory *</Label>
              <Input
                id="targetDirectory"
                {...register('configuration.targetDirectory', { required: true })}
                placeholder="/data/output"
                className={errors?.configuration?.targetDirectory ? 'border-destructive' : ''}
              />
              {errors?.configuration?.targetDirectory && (
                <span className="text-sm text-destructive">Target directory is required</span>
              )}
            </div>
            
            <div className="flex items-center space-x-2">
              <Controller
                name="configuration.createTargetDirectory"
                control={control}
                render={({ field }) => (
                  <input
                    type="checkbox"
                    id="createTargetDirectory"
                    checked={Boolean(field.value)}
                    onChange={field.onChange}
                    className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                  />
                )}
              />
              <Label htmlFor="createTargetDirectory">Create Target Directory</Label>
            </div>
          </>
        )}
      </div>
    </div>

    {/* Processing Parameters - Direction Specific */}
    {direction === 'SENDER' ? (
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">Processing Parameters</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="pollInterval">Poll Interval (secs)</Label>
            <Input
              id="pollInterval"
              type="number"
              {...register('configuration.pollInterval')}
              placeholder="60"
              defaultValue={systemConfig?.find((c: any) => c.configKey === 'file.processing.poll_interval_seconds')?.configValue || "60"}
              min="1"
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="retryInterval">Retry Interval (secs)</Label>
            <Input
              id="retryInterval"
              type="number"
              {...register('configuration.retryInterval')}
              placeholder="60"
              defaultValue={systemConfig?.find((c: any) => c.configKey === 'file.processing.retry_interval_seconds')?.configValue || "60"}
              min="1"
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="emptyFileHandling">Empty File Handling</Label>
            <Controller
              name="configuration.emptyFileHandling"
              control={control}
              defaultValue="Do Not Create Message"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select handling" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Do Not Create Message">Do Not Create Message</SelectItem>
                    <SelectItem value="Process Empty Files">Process Empty Files</SelectItem>
                    <SelectItem value="Skip Empty Files">Skip Empty Files</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="processingMode">Processing Mode</Label>
            <Controller
              name="configuration.processingMode"
              control={control}
              defaultValue="Test"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select mode" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Test">Test</SelectItem>
                    <SelectItem value="Archive">Archive</SelectItem>
                    <SelectItem value="Delete">Delete</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="fileType">File Type</Label>
            <Controller
              name="configuration.fileType"
              control={control}
              defaultValue="Binary"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select file type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Binary">Binary</SelectItem>
                    <SelectItem value="Text">Text</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
        </div>
      </div>
    ) : (
      /* Outbound Processing Parameters */
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">File Output Configuration</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="outputFilenameMode">Output Filename Mode</Label>
            <Controller
              name="configuration.outputFilenameMode"
              control={control}
              defaultValue="UseOriginal"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select filename mode" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="UseOriginal">Use Original Filename</SelectItem>
                    <SelectItem value="AddTimestamp">Add Timestamp to Filename</SelectItem>
                    <SelectItem value="Custom">Custom Filename Pattern</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="fileType">File Type</Label>
            <Controller
              name="configuration.fileType"
              control={control}
              defaultValue="Binary"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select file type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Binary">Binary</SelectItem>
                    <SelectItem value="Text">Text</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
        </div>
        
        {/* Custom Filename Pattern - shown when Output Filename Mode is Custom */}
        {watchOutputFilenameMode === 'Custom' && (
          <div className="mt-4">
            <div className="space-y-2">
              <Label htmlFor="customFilenamePattern">Custom Filename Pattern</Label>
              <Input
                id="customFilenamePattern"
                {...register('configuration.customFilenamePattern')}
                placeholder="{original_name}_{timestamp}.{extension}"
                className="bg-input border-border text-foreground"
              />
              <p className="text-sm text-muted-foreground">
                Available variables: {'{original_name}'}, {'{timestamp}'}, {'{date}'}, {'{extension}'}
              </p>
            </div>
          </div>
        )}
      </div>
    )}

    {/* Archive Configuration - shown when Processing Mode is Archive (Sender only) */}
    {direction === 'SENDER' && watchProcessingMode === 'Archive' && (
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">Archive Configuration</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="archiveDirectory">Archive Directory</Label>
            <Input
              id="archiveDirectory"
              {...register('configuration.archiveDirectory')}
              placeholder="/data/archive"
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="archiveFileNaming">Archive File Naming</Label>
            <Controller
              name="configuration.archiveFileNaming"
              control={control}
              defaultValue="Original"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select naming" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Original">Keep Original Name</SelectItem>
                    <SelectItem value="Timestamp">Add Timestamp</SelectItem>
                    <SelectItem value="Counter">Add Counter</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          <div className="flex items-center space-x-2">
            <Controller
              name="configuration.createArchiveDirectory"
              control={control}
              render={({ field }) => (
                <input
                  type="checkbox"
                  id="createArchiveDirectory"
                  checked={Boolean(field.value)}
                  onChange={field.onChange}
                  className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                />
              )}
            />
            <Label htmlFor="createArchiveDirectory">Create Archive Directory if it doesn't exist</Label>
          </div>
          
          <div className="flex items-center space-x-2">
            <Controller
              name="configuration.compressArchive"
              control={control}
              render={({ field }) => (
                <input
                  type="checkbox"
                  id="compressArchive"
                  checked={Boolean(field.value)}
                  onChange={field.onChange}
                  className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                />
              )}
            />
            <Label htmlFor="compressArchive">Compress archived files</Label>
          </div>
        </div>
      </div>
    )}

    {/* Archive and Error Handling */}
    {direction === 'SENDER' && (
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">Error Handling</h3>
        <div className="space-y-4">
          <div className="flex items-center space-x-2">
            <Controller
              name="configuration.archiveFaultySourceFiles"
              control={control}
              render={({ field }) => (
                <input
                  type="checkbox"
                  id="archiveFaultySourceFiles"
                  checked={Boolean(field.value)}
                  onChange={field.onChange}
                  className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                />
              )}
            />
            <Label htmlFor="archiveFaultySourceFiles">Archive Faulty Source Files</Label>
          </div>
          
          {watchArchiveFaultyFiles && (
            <div className="space-y-2">
              <Label htmlFor="archiveErrorDirectory">Directory for Archiving Files with Errors</Label>
              <Input
                id="archiveErrorDirectory"
                {...register('configuration.archiveErrorDirectory')}
                placeholder="/data/error"
              />
            </div>
          )}
          
          <div className="flex items-center space-x-2">
            <Controller
              name="configuration.processReadOnlyFiles"
              control={control}
              render={({ field }) => (
                <input
                  type="checkbox"
                  id="processReadOnlyFiles"
                  checked={Boolean(field.value)}
                  onChange={field.onChange}
                  className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                />
              )}
            />
            <Label htmlFor="processReadOnlyFiles">Process Read-Only Files</Label>
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="processingSequence">Processing Sequence</Label>
            <Controller
              name="configuration.processingSequence"
              control={control}
              defaultValue="By Name"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select sequence" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="By Name">By Name</SelectItem>
                    <SelectItem value="By Date">By Date</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
        </div>
      </div>
    )}

    {/* Outbound Specific Settings */}
    {direction === 'RECEIVER' && (
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">File Construction</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="fileConstructionMode">File Construction Mode</Label>
            <Controller
              name="configuration.fileConstructionMode"
              control={control}
              defaultValue="Create"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select mode" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Add Time Stamp">Add Time Stamp</SelectItem>
                    <SelectItem value="Create">Create</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="writeMode">Write Mode</Label>
            <Controller
              name="configuration.writeMode"
              control={control}
              defaultValue="Directly"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select write mode" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Directly">Directly</SelectItem>
                    <SelectItem value="Create Temp File">Create Temp File</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="emptyMessageHandling">Empty Message Handling</Label>
            <Controller
              name="configuration.emptyMessageHandling"
              control={control}
              defaultValue="Write Empty File"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select handling" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Write Empty File">Write Empty File</SelectItem>
                    <SelectItem value="Skip Empty Messages">Skip Empty Messages</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="maximumConcurrency">Maximum Concurrency</Label>
            <Input
              id="maximumConcurrency"
              type="number"
              {...register('configuration.maximumConcurrency')}
              placeholder="1"
              defaultValue="1"
              min="1"
              max="10"
            />
          </div>
        </div>
      </div>
    )}

    {/* Advanced Parameters */}
    <div>
      <h3 className="text-lg font-semibold text-foreground mb-4">Advanced Parameters</h3>
      <div className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="msecsToWaitBeforeModificationCheck">Msecs to Wait Before Modification Check</Label>
            <Input
              id="msecsToWaitBeforeModificationCheck"
              type="number"
              {...register('configuration.msecsToWaitBeforeModificationCheck')}
              placeholder="0"
              defaultValue={systemConfig?.find((c: any) => c.configKey === 'file.processing.modification_check_delay_ms')?.configValue || "0"}
              min="0"
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="maximumFileSize">Maximum File Size (Bytes)</Label>
            <Input
              id="maximumFileSize"
              type="number"
              {...register('configuration.maximumFileSize')}
              placeholder="0"
              defaultValue={(() => {
                const maxFileSizeMB = systemConfig?.find((c: any) => c.configKey === 'file.processing.max_file_size_mb')?.configValue;
                return maxFileSizeMB ? String(parseInt(maxFileSizeMB) * 1024 * 1024) : "0";
              })()}
              min="0"
            />
          </div>
        </div>
      </div>
    </div>

    {/* Scheduler Settings - Only for SENDER direction */}
    {direction === 'SENDER' && (
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">Scheduler Settings</h3>
        <div className="space-y-4">
          {/* Schedule Type */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="scheduleType">Schedule Type</Label>
              <Controller
                name="configuration.scheduleType"
                control={control}
                defaultValue="Daily"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="Daily">Daily</SelectItem>
                      <SelectItem value="Weekly">Weekly</SelectItem>
                      <SelectItem value="Monthly">Monthly</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="scheduleMode">Schedule Mode</Label>
              <Controller
                name="configuration.scheduleMode"
                control={control}
                defaultValue="OnTime"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="OnTime">On Time</SelectItem>
                      <SelectItem value="Every">Every Interval</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>

          {/* Time Configuration based on Schedule Mode */}
          {watchScheduleMode === 'OnTime' && (
            <div className="space-y-2">
              <Label htmlFor="onTimeValue">Execution Time</Label>
              <Input
                id="onTimeValue"
                type="time"
                defaultValue="20:27"
                className="bg-input border-border text-foreground"
                {...register('configuration.onTimeValue')}
              />
            </div>
          )}

          {watchScheduleMode === 'Every' && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label htmlFor="everyInterval">Every Interval</Label>
                <Controller
                  name="configuration.everyInterval"
                  control={control}
                  defaultValue="1 min"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="1 min">1 minute</SelectItem>
                        <SelectItem value="5 min">5 minutes</SelectItem>
                        <SelectItem value="15 min">15 minutes</SelectItem>
                        <SelectItem value="30 min">30 minutes</SelectItem>
                        <SelectItem value="1 hour">1 hour</SelectItem>
                        <SelectItem value="2 hours">2 hours</SelectItem>
                        <SelectItem value="4 hours">4 hours</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="everyStartTime">Start Time</Label>
                <Input
                  id="everyStartTime"
                  type="time"
                  defaultValue="00:00"
                  className="bg-input border-border text-foreground"
                  {...register('configuration.everyStartTime')}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="everyEndTime">End Time</Label>
                <Input
                  id="everyEndTime"
                  type="time"
                  defaultValue="24:00"
                  className="bg-input border-border text-foreground"
                  {...register('configuration.everyEndTime')}
                />
              </div>
            </div>
          )}

          {/* Weekly Days Selection - shown when Schedule Type is Weekly */}
          {watchScheduleType === 'Weekly' && (
            <div className="space-y-2">
              <Label>Days of Week</Label>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {[
                  { key: 'monday', label: 'Monday' },
                  { key: 'tuesday', label: 'Tuesday' },
                  { key: 'wednesday', label: 'Wednesday' },
                  { key: 'thursday', label: 'Thursday' },
                  { key: 'friday', label: 'Friday' },
                  { key: 'saturday', label: 'Saturday' },
                  { key: 'sunday', label: 'Sunday' }
                ].map((day) => (
                  <div key={day.key} className="flex items-center space-x-2">
                    <Controller
                      name={`configuration.weeklyDays.${day.key}`}
                      control={control}
                      defaultValue={false}
                      render={({ field }) => (
                        <input
                          type="checkbox"
                          id={`weekly_${day.key}`}
                          checked={Boolean(field.value)}
                          onChange={field.onChange}
                          className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                        />
                      )}
                    />
                    <Label htmlFor={`weekly_${day.key}`} className="text-sm">{day.label}</Label>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Monthly Day Selection - shown when Schedule Type is Monthly */}
          {watchScheduleType === 'Monthly' && (
            <div className="space-y-2">
              <Label htmlFor="monthlyDay">Day of Month</Label>
              <Controller
                name="configuration.monthlyDay"
                control={control}
                defaultValue="1"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {Array.from({ length: 31 }, (_, i) => i + 1).map(day => (
                        <SelectItem key={day} value={day.toString()}>{day}</SelectItem>
                      ))}
                      <SelectItem value="last">Last Day of Month</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          )}
        </div>
      </div>
    )}
  </div>
  )
}

// SFTP Adapter Configuration  
const SftpAdapterConfig: React.FC<any> = ({ direction, control, register, errors, systemConfig: _, sshKeysData = [] }) => {
  const watchScheduleMode = useWatch({
    control,
    name: 'configuration.scheduleMode',
    defaultValue: 'OnTime'
  })

  const watchScheduleType = useWatch({
    control,
    name: 'configuration.scheduleType',
    defaultValue: 'Daily'
  })

  return (
    <div className="space-y-6">
      {/* Connection Settings */}
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">Connection Settings</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="host">Host</Label>
            <Input
              id="host"
              placeholder="sftp.bank.com"
              className="bg-input border-border text-foreground"
              {...register('configuration.host', { 
                required: 'SFTP host is required' 
              })}
            />
            {errors.configuration?.host && (
              <p className="text-sm text-destructive">{errors.configuration.host.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="port">Port</Label>
            <Input
              id="port"
              type="number"
              placeholder="22"
              defaultValue="22"
              className="bg-input border-border text-foreground"
              {...register('configuration.port', { 
                valueAsNumber: true,
                required: 'Port is required' 
              })}
            />
            {errors.configuration?.port && (
              <p className="text-sm text-destructive">{errors.configuration.port.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="authenticationType">Authentication Type</Label>
            <Controller
              name="configuration.authenticationType"
              control={control}
              defaultValue="USERNAME_PASSWORD"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select authentication type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="USERNAME_PASSWORD">Username/Password</SelectItem>
                    <SelectItem value="PUBLIC_KEY">Public Key</SelectItem>
                    <SelectItem value="DUAL">Dual Authentication</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
        </div>

        {/* Authentication Fields - Dynamic based on type */}
        <AuthenticationFields control={control} register={register} errors={errors} sshKeysData={sshKeysData} />
      </div>

      {/* RECEIVER Configuration - Remote Destination Settings */}
      {direction === 'RECEIVER' && (
        <>
          <div>
            <h3 className="text-lg font-semibold text-foreground mb-4">Remote Destination Settings</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="remoteDirectory">Remote Directory</Label>
                <Input
                  id="remoteDirectory"
                  placeholder="/incoming/payments"
                  className="bg-input border-border text-foreground"
                  {...register('configuration.remoteDirectory', { 
                    required: 'Remote directory is required for receiver adapters' 
                  })}
                />
                {errors.configuration?.remoteDirectory && (
                  <p className="text-sm text-destructive">{errors.configuration.remoteDirectory.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="remoteFilePermissions">Remote File Permissions</Label>
                <Input
                  id="remoteFilePermissions"
                  placeholder="644"
                  defaultValue="644"
                  className="bg-input border-border text-foreground"
                  {...register('configuration.remoteFilePermissions')}
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
              <div className="flex items-center space-x-2">
                <Controller
                  name="configuration.createRemoteDirectory"
                  control={control}
                  render={({ field }) => (
                    <input
                      type="checkbox"
                      id="createRemoteDirectory"
                      checked={Boolean(field.value)}
                      onChange={field.onChange}
                      className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                    />
                  )}
                />
                <Label htmlFor="createRemoteDirectory">Create Remote Directory</Label>
              </div>

              <div className="flex items-center space-x-2">
                <Controller
                  name="configuration.useTemporaryFileName"
                  control={control}
                  render={({ field }) => (
                    <input
                      type="checkbox"
                      id="useTemporaryFileName"
                      checked={Boolean(field.value)}
                      onChange={field.onChange}
                      className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                    />
                  )}
                />
                <Label htmlFor="useTemporaryFileName">Use Temporary File Name</Label>
              </div>
            </div>

            <div className="space-y-2 mt-4">
              <Label htmlFor="temporaryFileSuffix">Temporary File Suffix</Label>
              <Input
                id="temporaryFileSuffix"
                placeholder=".uploading"
                defaultValue=".uploading"
                className="bg-input border-border text-foreground"
                {...register('configuration.temporaryFileSuffix')}
              />
            </div>
          </div>

          {/* Output Filename Configuration */}
          <OutputFilenameConfiguration control={control} register={register} />
        </>
      )}

      {/* Connection Properties */}
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">Connection Properties</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="connectionTimeout">Connection Timeout (ms)</Label>
            <Input
              id="connectionTimeout"
              type="number"
              placeholder="30000"
              defaultValue="30000"
              className="bg-input border-border text-foreground"
              {...register('configuration.connectionTimeout', { 
                valueAsNumber: true,
                min: { value: 1000, message: 'Connection timeout must be at least 1000ms' },
                max: { value: 300000, message: 'Connection timeout cannot exceed 300000ms' }
              })}
            />
            {errors.configuration?.connectionTimeout && (
              <p className="text-sm text-destructive">{errors.configuration.connectionTimeout.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="socketTimeout">Socket Timeout (ms)</Label>
            <Input
              id="socketTimeout"
              type="number"
              placeholder="60000"
              defaultValue="60000"
              className="bg-input border-border text-foreground"
              {...register('configuration.socketTimeout', { 
                valueAsNumber: true,
                min: { value: 1000, message: 'Socket timeout must be at least 1000ms' },
                max: { value: 300000, message: 'Socket timeout cannot exceed 300000ms' }
              })}
            />
            {errors.configuration?.socketTimeout && (
              <p className="text-sm text-destructive">{errors.configuration.socketTimeout.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="keepAlive">Keep Alive</Label>
            <Controller
              name="configuration.keepAlive"
              control={control}
              render={({ field }) => (
                <Select value={field.value ? "true" : "false"} onValueChange={(value) => field.onChange(value === "true")}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select keep alive" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="true">Enabled</SelectItem>
                    <SelectItem value="false">Disabled</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
          <div className="space-y-2">
            <Label htmlFor="sessionTimeout">Session Timeout (ms)</Label>
            <Input
              id="sessionTimeout"
              type="number"
              placeholder="60000"
              defaultValue="60000"
              className="bg-input border-border text-foreground"
              {...register('configuration.sessionTimeout', { 
                valueAsNumber: true,
                min: { value: 1000, message: 'Session timeout must be at least 1000ms' },
                max: { value: 600000, message: 'Session timeout cannot exceed 600000ms' }
              })}
            />
            {errors.configuration?.sessionTimeout && (
              <p className="text-sm text-destructive">{errors.configuration.sessionTimeout.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="channelTimeout">Channel Timeout (ms)</Label>
            <Input
              id="channelTimeout"
              type="number"
              placeholder="30000"
              defaultValue="30000"
              className="bg-input border-border text-foreground"
              {...register('configuration.channelTimeout', { 
                valueAsNumber: true,
                min: { value: 1000, message: 'Channel timeout must be at least 1000ms' },
                max: { value: 300000, message: 'Channel timeout cannot exceed 300000ms' }
              })}
            />
            {errors.configuration?.channelTimeout && (
              <p className="text-sm text-destructive">{errors.configuration.channelTimeout.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="connectionPoolSize">Connection Pool Size</Label>
            <Input
              id="connectionPoolSize"
              type="number"
              placeholder="5"
              defaultValue="5"
              min="1"
              max="20"
              className="bg-input border-border text-foreground"
              {...register('configuration.connectionPoolSize', { 
                valueAsNumber: true,
                min: { value: 1, message: 'Connection pool size must be at least 1' },
                max: { value: 20, message: 'Connection pool size cannot exceed 20' }
              })}
            />
            {errors.configuration?.connectionPoolSize && (
              <p className="text-sm text-destructive">{errors.configuration.connectionPoolSize.message}</p>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
          <div className="space-y-2">
            <Label htmlFor="strictHostKeyChecking">Strict Host Key Checking</Label>
            <Controller
              name="configuration.strictHostKeyChecking"
              control={control}
              defaultValue={false}
              render={({ field }) => (
                <Select value={field.value ? "true" : "false"} onValueChange={(value) => field.onChange(value === "true")}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select host key checking" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="true">Enabled</SelectItem>
                    <SelectItem value="false">Disabled</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="connectionMode">Connection Mode</Label>
            <Controller
              name="configuration.connectionMode"
              control={control}
              defaultValue="Permanently"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select connection mode" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Permanently">Permanently</SelectItem>
                    <SelectItem value="Per File Transfer">Per File Transfer</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
        </div>
      </div>

      {/* Transfer Settings */}
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">Transfer Settings</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="maxRetryAttempts">Max Retry Attempts</Label>
            <Input
              id="maxRetryAttempts"
              type="number"
              placeholder="3"
              defaultValue="3"
              min="1"
              max="10"
              className="bg-input border-border text-foreground"
              {...register('configuration.maxRetryAttempts', { 
                valueAsNumber: true,
                min: { value: 1, message: 'Max retry attempts must be at least 1' },
                max: { value: 10, message: 'Max retry attempts cannot exceed 10' }
              })}
            />
            {errors.configuration?.maxRetryAttempts && (
              <p className="text-sm text-destructive">{errors.configuration.maxRetryAttempts.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="retryDelayMs">Retry Delay (ms)</Label>
            <Input
              id="retryDelayMs"
              type="number"
              placeholder="5000"
              defaultValue="5000"
              className="bg-input border-border text-foreground"
              {...register('configuration.retryDelayMs', { 
                valueAsNumber: true,
                min: { value: 100, message: 'Retry delay must be at least 100ms' },
                max: { value: 60000, message: 'Retry delay cannot exceed 60000ms' }
              })}
            />
            {errors.configuration?.retryDelayMs && (
              <p className="text-sm text-destructive">{errors.configuration.retryDelayMs.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="maxFileSize">Max File Size (MB)</Label>
            <Input
              id="maxFileSize"
              type="number"
              placeholder="100"
              defaultValue="100"
              className="bg-input border-border text-foreground"
              {...register('configuration.maxFileSize', { 
                valueAsNumber: true,
                min: { value: 1, message: 'Max file size must be at least 1MB' },
                max: { value: 5000, message: 'Max file size cannot exceed 5000MB' }
              })}
            />
            {errors.configuration?.maxFileSize && (
              <p className="text-sm text-destructive">{errors.configuration.maxFileSize.message}</p>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
          <div className="space-y-2">
            <Label htmlFor="transferBufferSize">Transfer Buffer Size (KB)</Label>
            <Input
              id="transferBufferSize"
              type="number"
              placeholder="32"
              defaultValue="32"
              className="bg-input border-border text-foreground"
              {...register('configuration.transferBufferSize', { 
                valueAsNumber: true,
                min: { value: 1, message: 'Transfer buffer size must be at least 1KB' },
                max: { value: 1024, message: 'Transfer buffer size cannot exceed 1024KB' }
              })}
            />
            {errors.configuration?.transferBufferSize && (
              <p className="text-sm text-destructive">{errors.configuration.transferBufferSize.message}</p>
            )}
          </div>
        </div>
      </div>

      {/* File Transfer Settings */}
      <div>
        <h3 className="text-lg font-semibold text-foreground mb-4">File Transfer Settings</h3>
        <div className="grid grid-cols-1 md:grid-cols-1 gap-4">
          <div className="space-y-2">
            <Label htmlFor="transferMode">Transfer Mode</Label>
            <Controller
              name="configuration.transferMode"
              control={control}
              defaultValue="BINARY"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select transfer mode" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="BINARY">Binary</SelectItem>
                    <SelectItem value="ASCII">ASCII</SelectItem>
                    <SelectItem value="AUTO">Auto</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
        </div>
      </div>

      {/* SENDER Configuration - Remote Pickup Settings */}
      {direction === 'SENDER' && (
        <>
          <div>
            <h3 className="text-lg font-semibold text-foreground mb-4">Remote Pickup Settings</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="remoteDirectory">Remote Directory</Label>
                <Input
                  id="remoteDirectory"
                  placeholder="/outgoing/statements"
                  className="bg-input border-border text-foreground"
                  {...register('configuration.remoteDirectory', { 
                    required: 'Remote directory is required for sender adapters' 
                  })}
                />
                {errors.configuration?.remoteDirectory && (
                  <p className="text-sm text-destructive">{errors.configuration.remoteDirectory.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="filename">Filename Pattern</Label>
                <Input
                  id="filename"
                  placeholder="*.csv"
                  className="bg-input border-border text-foreground"
                  {...register('configuration.filename', { 
                    required: 'Filename pattern is required for sender adapters' 
                  })}
                />
                {errors.configuration?.filename && (
                  <p className="text-sm text-destructive">{errors.configuration.filename.message}</p>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
              <div className="space-y-2">
                <Label htmlFor="excludePattern">Exclude Pattern</Label>
                <Input
                  id="excludePattern"
                  placeholder="*.tmp,*.processing"
                  className="bg-input border-border text-foreground"
                  {...register('configuration.excludePattern')}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="temporaryFileSuffix">Temporary File Suffix</Label>
                <Input
                  id="temporaryFileSuffix"
                  placeholder=".tmp"
                  defaultValue=".tmp"
                  className="bg-input border-border text-foreground"
                  {...register('configuration.temporaryFileSuffix')}
                />
              </div>
            </div>

            <div className="flex items-center space-x-2 mt-4">
              <Controller
                name="configuration.useTemporaryFileName"
                control={control}
                render={({ field }) => (
                  <input
                    type="checkbox"
                    id="useTemporaryFileName"
                    checked={Boolean(field.value)}
                    onChange={field.onChange}
                    className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                  />
                )}
              />
              <Label htmlFor="useTemporaryFileName">Use Temporary File Name</Label>
            </div>
          </div>

          {/* Scheduler Settings */}
          <div>
            <h3 className="text-lg font-semibold text-foreground mb-4">Scheduler Settings</h3>
            <div className="space-y-4">
              {/* Schedule Type */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="scheduleType">Schedule Type</Label>
                  <Controller
                    name="configuration.scheduleType"
                    control={control}
                    defaultValue="Daily"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="Daily">Daily</SelectItem>
                          <SelectItem value="Weekly">Weekly</SelectItem>
                          <SelectItem value="Monthly">Monthly</SelectItem>
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="scheduleMode">Schedule Mode</Label>
                  <Controller
                    name="configuration.scheduleMode"
                    control={control}
                    defaultValue="OnTime"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="OnTime">On Time</SelectItem>
                          <SelectItem value="Every">Every Interval</SelectItem>
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>

              </div>

              {/* Time Configuration based on Schedule Mode */}
              {watchScheduleMode === 'OnTime' && (
                <div className="space-y-2">
                  <Label htmlFor="onTimeValue">Execution Time</Label>
                  <Input
                    id="onTimeValue"
                    type="time"
                    defaultValue="20:27"
                    className="bg-input border-border text-foreground"
                    {...register('configuration.onTimeValue')}
                  />
                </div>
              )}

              {watchScheduleMode === 'Every' && (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="everyInterval">Every Interval</Label>
                    <Controller
                      name="configuration.everyInterval"
                      control={control}
                      defaultValue="1 min"
                      render={({ field }) => (
                        <Select value={field.value} onValueChange={field.onChange}>
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="1 min">1 minute</SelectItem>
                            <SelectItem value="5 min">5 minutes</SelectItem>
                            <SelectItem value="15 min">15 minutes</SelectItem>
                            <SelectItem value="30 min">30 minutes</SelectItem>
                            <SelectItem value="1 hour">1 hour</SelectItem>
                            <SelectItem value="2 hours">2 hours</SelectItem>
                            <SelectItem value="4 hours">4 hours</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="everyStartTime">Start Time</Label>
                    <Input
                      id="everyStartTime"
                      type="time"
                      defaultValue="00:00"
                      className="bg-input border-border text-foreground"
                      {...register('configuration.everyStartTime')}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="everyEndTime">End Time</Label>
                    <Input
                      id="everyEndTime"
                      type="time"
                      defaultValue="24:00"
                      className="bg-input border-border text-foreground"
                      {...register('configuration.everyEndTime')}
                    />
                  </div>
                </div>
              )}

              {/* Weekly Days Selection - shown when Schedule Type is Weekly */}
              {watchScheduleType === 'Weekly' && (
                <div className="space-y-2">
                  <Label>Days of Week</Label>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {[
                      { key: 'monday', label: 'Monday' },
                      { key: 'tuesday', label: 'Tuesday' },
                      { key: 'wednesday', label: 'Wednesday' },
                      { key: 'thursday', label: 'Thursday' },
                      { key: 'friday', label: 'Friday' },
                      { key: 'saturday', label: 'Saturday' },
                      { key: 'sunday', label: 'Sunday' }
                    ].map((day) => (
                      <div key={day.key} className="flex items-center space-x-2">
                        <Controller
                          name={`configuration.weeklyDays.${day.key}`}
                          control={control}
                          defaultValue={false}
                          render={({ field }) => (
                            <input
                              type="checkbox"
                              id={`weekly_${day.key}`}
                              checked={Boolean(field.value)}
                              onChange={field.onChange}
                              className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                            />
                          )}
                        />
                        <Label htmlFor={`weekly_${day.key}`} className="text-sm">{day.label}</Label>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Monthly Day Selection - shown when Schedule Type is Monthly */}
              {watchScheduleType === 'Monthly' && (
                <div className="space-y-2">
                  <Label htmlFor="monthlyDay">Day of Month</Label>
                  <Controller
                    name="configuration.monthlyDay"
                    control={control}
                    defaultValue="1"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {Array.from({ length: 31 }, (_, i) => i + 1).map(day => (
                            <SelectItem key={day} value={day.toString()}>{day}</SelectItem>
                          ))}
                          <SelectItem value="last">Last Day of Month</SelectItem>
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>
              )}
            </div>
          </div>

          {/* Empty File Handling */}
          <div>
            <h3 className="text-lg font-semibold text-foreground mb-4">Empty File Handling</h3>
            <div className="space-y-2">
              <Label htmlFor="emptyFileHandling">Empty File Handling</Label>
              <Controller
                name="configuration.emptyFileHandling"
                control={control}
                defaultValue="Do Not Create Message"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select empty file handling" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="Do Not Create Message">Do Not Create Message</SelectItem>
                      <SelectItem value="Process Empty Files">Process Empty Files</SelectItem>
                      <SelectItem value="Skip Empty Files">Skip Empty Files</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>
        </>
      )}

      {/* Post-Processing - Only show for sender SFTP adapters */}
      {direction === 'SENDER' && (
        <SftpPostProcessingSettings control={control} register={register} />
      )}
    </div>
  )
}


// Output Filename Configuration Component for RECEIVER
const OutputFilenameConfiguration: React.FC<any> = ({ control, register }) => {
  const watchOutputFilenameMode = useWatch({
    control,
    name: 'configuration.outputFilenameMode',
    defaultValue: 'UseOriginal'
  })

  return (
    <div>
      <h3 className="text-lg font-semibold text-foreground mb-4">Output Filename Configuration</h3>
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="outputFilenameMode">Output Filename Mode</Label>
          <Controller
            name="configuration.outputFilenameMode"
            control={control}
            defaultValue="UseOriginal"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Select filename mode" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="UseOriginal">Use Original Filename</SelectItem>
                  <SelectItem value="AddTimestamp">Add Timestamp to Filename</SelectItem>
                  <SelectItem value="Custom">Custom Filename Pattern</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </div>

        {/* Custom Filename Pattern - shown when Output Filename Mode is Custom */}
        {watchOutputFilenameMode === 'Custom' && (
          <div className="space-y-2">
            <Label htmlFor="customFilenamePattern">Custom Filename Pattern</Label>
            <Input
              id="customFilenamePattern"
              {...register('configuration.customFilenamePattern')}
              placeholder="{original_name}_{timestamp}.{extension}"
              className="bg-input border-border text-foreground"
            />
            <p className="text-sm text-muted-foreground">
              Available variables: {'{original_name}'}, {'{timestamp}'}, {'{date}'}, {'{extension}'}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

// SFTP Post-Processing Settings Component for SENDER
const SftpPostProcessingSettings: React.FC<any> = ({ control, register }) => {
  const watchPostProcessAction = useWatch({
    control,
    name: 'configuration.postProcessAction',
    defaultValue: 'ARCHIVE'
  })

  return (
    <div>
      <h3 className="text-lg font-semibold text-foreground mb-4">Post-Processing</h3>
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="postProcessAction">Post Process Action</Label>
          <Controller
            name="configuration.postProcessAction"
            control={control}
            defaultValue="ARCHIVE"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Select post process action" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ARCHIVE">Archive</SelectItem>
                  <SelectItem value="KEEP_AND_MARK">Keep File and Mark as Processed</SelectItem>
                  <SelectItem value="KEEP_AND_REPROCESS">Keep File and Process Again</SelectItem>
                  <SelectItem value="DELETE">Delete File</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </div>

        {watchPostProcessAction === 'ARCHIVE' && (
          <>
            <div className="space-y-2">
              <Label htmlFor="archiveDirectory">Archive Directory</Label>
              <Input
                id="archiveDirectory"
                placeholder="/data/archive"
                className="bg-input border-border text-foreground"
                {...register('configuration.archiveDirectory')}
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="flex items-center space-x-2">
                <Controller
                  name="configuration.archiveWithTimestamp"
                  control={control}
                  render={({ field }) => (
                    <input
                      type="checkbox"
                      id="archiveWithTimestamp"
                      checked={Boolean(field.value)}
                      onChange={field.onChange}
                      className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                    />
                  )}
                />
                <Label htmlFor="archiveWithTimestamp">Archive with Timestamp</Label>
              </div>

              <div className="space-y-2">
                <Label htmlFor="compressionType">Compression Type</Label>
                <Controller
                  name="configuration.compressionType"
                  control={control}
                  defaultValue="NONE"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger>
                        <SelectValue placeholder="Select compression" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="NONE">None</SelectItem>
                        <SelectItem value="GZIP">GZIP</SelectItem>
                        <SelectItem value="ZIP">ZIP</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            </div>
          </>
        )}

        {watchPostProcessAction === 'KEEP_AND_MARK' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="processedDirectory">Processed Directory</Label>
              <Input
                id="processedDirectory"
                placeholder="/data/processed"
                className="bg-input border-border text-foreground"
                {...register('configuration.processedDirectory')}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="processedFileSuffix">Processed File Suffix</Label>
              <Input
                id="processedFileSuffix"
                placeholder=".processed"
                className="bg-input border-border text-foreground"
                {...register('configuration.processedFileSuffix')}
              />
            </div>
          </div>
        )}

        {watchPostProcessAction === 'KEEP_AND_REPROCESS' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="reprocessingDirectory">Reprocessing Directory</Label>
              <Input
                id="reprocessingDirectory"
                placeholder="/data/reprocess"
                className="bg-input border-border text-foreground"
                {...register('configuration.reprocessingDirectory')}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="reprocessingDelay">Reprocessing Delay (ms)</Label>
              <Input
                id="reprocessingDelay"
                type="number"
                placeholder="3600000"
                className="bg-input border-border text-foreground"
                {...register('configuration.reprocessingDelay', { valueAsNumber: true })}
              />
            </div>
          </div>
        )}

        {watchPostProcessAction === 'DELETE' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="deleteBackupDirectory">Delete Backup Directory</Label>
              <Input
                id="deleteBackupDirectory"
                placeholder="/data/deleted_backup"
                className="bg-input border-border text-foreground"
                {...register('configuration.deleteBackupDirectory')}
              />
            </div>

            <div className="flex items-center space-x-2">
              <Controller
                name="configuration.confirmDelete"
                control={control}
                render={({ field }) => (
                  <input
                    type="checkbox"
                    id="confirmDelete"
                    checked={Boolean(field.value)}
                    onChange={field.onChange}
                    className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
                  />
                )}
              />
              <Label htmlFor="confirmDelete">Confirm Delete</Label>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

// Email Adapter Configuration
const EmailAdapterConfig: React.FC<any> = ({ direction, control, register, errors, systemConfig: _, setValue }) => (
  <div className="space-y-4">
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div className="space-y-2">
        <Label htmlFor="smtpHost">SMTP Host</Label>
        <Input
          id="smtpHost"
          placeholder="smtp.gmail.com"
          className="bg-input border-border text-foreground"
          {...register('configuration.smtpHost', { 
            required: 'SMTP host is required' 
          })}
        />
        {errors.configuration?.smtpHost && (
          <p className="text-sm text-destructive">{errors.configuration.smtpHost.message}</p>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="smtpPort">SMTP Port</Label>
        <Input
          id="smtpPort"
          type="number"
          placeholder="587"
          className="bg-input border-border text-foreground"
          {...register('configuration.smtpPort', { 
            valueAsNumber: true,
            required: 'SMTP port is required' 
          })}
        />
        {errors.configuration?.smtpPort && (
          <p className="text-sm text-destructive">{errors.configuration.smtpPort.message}</p>
        )}
      </div>
    </div>

    {/* SSL/TLS Configuration */}
    <div className="space-y-4">
      <h4 className="text-sm font-medium text-foreground">SSL/TLS Settings</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-2">
          <div className="flex items-center space-x-2">
            <Controller
              name="configuration.sslEnabled"
              control={control}
              render={({ field }) => (
                <Switch
                  id="sslEnabled"
                  checked={Boolean(field.value)}
                  onCheckedChange={(checked) => {
                    field.onChange(checked)
                    setValue('configuration.startTlsEnabled', !checked, { shouldValidate: true, shouldDirty: true })
                  }}
                />
              )}
            />
            <Label htmlFor="sslEnabled">Enable SSL (Port 465)</Label>
          </div>
          <p className="text-xs text-muted-foreground">
            Use SSL for secure connection. Required for port 465.
          </p>
        </div>

        <div className="space-y-2">
          <div className="flex items-center space-x-2">
            <Controller
              name="configuration.startTlsEnabled"
              control={control}
              render={({ field }) => (
                <Switch
                  id="startTlsEnabled"
                  checked={Boolean(field.value)}
                  onCheckedChange={(checked) => {
                    field.onChange(checked)
                    setValue('configuration.sslEnabled', !checked, { shouldValidate: true, shouldDirty: true })
                  }}
                />
              )}
            />
            <Label htmlFor="startTlsEnabled">Enable STARTTLS (Port 587)</Label>
          </div>
          <p className="text-xs text-muted-foreground">
            Use STARTTLS for secure connection. Typically used with port 587.
          </p>
        </div>
      </div>
    </div>

    <div className="space-y-2">
      <Label htmlFor="fromAddress">From Address (Sender Email)</Label>
      <Input
        id="fromAddress"
        placeholder="noreply@company.com"
        className="bg-input border-border text-foreground"
        {...register('configuration.fromAddress', { 
          required: 'From address is required',
          pattern: {
            value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
            message: 'Please enter a valid email address'
          }
        })}
      />
      {errors.configuration?.fromAddress && (
        <p className="text-sm text-destructive">{errors.configuration.fromAddress.message}</p>
      )}
    </div>

    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div className="space-y-2">
        <Label htmlFor="smtpUsername">Email Username</Label>
        <Input
          id="smtpUsername"
          placeholder="notifications@company.com"
          className="bg-input border-border text-foreground"
          {...register('configuration.smtpUsername', { 
            required: 'Email username is required' 
          })}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="smtpPassword">Email Password</Label>
        <Input
          id="smtpPassword"
          type="password"
          placeholder="Enter email password"
          className="bg-input border-border text-foreground"
          {...register('configuration.smtpPassword', { 
            required: 'Email password is required' 
          })}
        />
      </div>
    </div>

    {direction === 'RECEIVER' && (
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="recipients">Recipients (comma-separated)</Label>
          <Input
            id="recipients"
            placeholder="user1@example.com, user2@example.com"
            className="bg-input border-border text-foreground"
            {...register('configuration.recipients', { 
              required: 'Recipients are required for receiver email' 
            })}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="subject">Email Subject</Label>
          <Input
            id="subject"
            placeholder="File Transfer Notification"
            className="bg-input border-border text-foreground"
            {...register('configuration.subject')}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="body">Email Body</Label>
          <textarea
            id="body"
            placeholder="Enter the email body text..."
            className="flex min-h-[120px] w-full rounded-md border border-border bg-input px-3 py-2 text-sm text-foreground ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
            {...register('configuration.body')}
          />
          <p className="text-xs text-muted-foreground">
            Files will be attached directly from the flow execution context.
          </p>
        </div>
      </div>
    )}
  </div>
)

// Authentication Fields Component - Dynamic based on authentication type
const AuthenticationFields: React.FC<any> = ({ control, register, errors, sshKeysData = [] }) => {
  const watchAuthType = useWatch({
    control,
    name: 'configuration.authenticationType',
    defaultValue: 'USERNAME_PASSWORD'
  })

  return (
    <div className="space-y-4 mt-4">
      {(watchAuthType === 'USERNAME_PASSWORD' || watchAuthType === 'DUAL') && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="username">Username</Label>
            <Input
              id="username"
              placeholder="Administrator"
              className="bg-input border-border text-foreground"
              {...register('configuration.username', { 
                required: watchAuthType === 'USERNAME_PASSWORD' ? 'Username is required' : false
              })}
            />
            {errors.configuration?.username && (
              <p className="text-sm text-destructive">{errors.configuration.username.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              placeholder="••••••••"
              className="bg-input border-border text-foreground"
              {...register('configuration.password', { 
                required: watchAuthType === 'USERNAME_PASSWORD' ? 'Password is required' : false
              })}
            />
            {errors.configuration?.password && (
              <p className="text-sm text-destructive">{errors.configuration.password.message}</p>
            )}
          </div>
        </div>
      )}

      {/* Server Fingerprint - shown for all authentication types */}
      <div className="space-y-2">
        <Label htmlFor="serverFingerprint">Server Fingerprint</Label>
        <Input
          id="serverFingerprint"
          placeholder="SHA256:AAAA... or MD5:aa:bb:cc..."
          className="bg-input border-border text-foreground"
          {...register('configuration.serverFingerprint')}
        />
        <p className="text-xs text-muted-foreground">
          Optional: SSH server host key fingerprint for additional security verification
        </p>
      </div>

      {(watchAuthType === 'PUBLIC_KEY' || watchAuthType === 'DUAL') && (
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="sshKeyId">SSH Key</Label>
            <Controller
              name="configuration.sshKeyId"
              control={control}
              rules={{ 
                required: watchAuthType === 'PUBLIC_KEY' ? 'SSH Key is required' : false
              }}
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select SSH Key" />
                  </SelectTrigger>
                  <SelectContent>
                    {sshKeysData.length > 0 ? (
                      sshKeysData.map((key: any) => (
                        <SelectItem key={key.id} value={key.id}>
                          {key.name} ({key.keyType})
                        </SelectItem>
                      ))
                    ) : (
                      <SelectItem value="no-keys" disabled>
                        No SSH keys available
                      </SelectItem>
                    )}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.configuration?.sshKeyId && (
              <p className="text-sm text-destructive">{errors.configuration.sshKeyId.message}</p>
            )}
          </div>

          <div className="flex items-center space-x-4 text-sm text-muted-foreground">
            <span>Need an SSH key?</span>
            <button
              type="button"
              onClick={() => window.open('/ssh-keys', '_blank')}
              className="text-primary hover:underline"
            >
              Generate SSH Key
            </button>
          </div>
        </div>
      )}

      {watchAuthType === 'DUAL' && (
        <div className="p-3 bg-amber-50 border border-amber-200 rounded-md">
          <p className="text-sm text-amber-800">
            <strong>Dual Authentication:</strong> Both username/password and SSH key will be used for authentication.
          </p>
        </div>
      )}
    </div>
  )
}

export default AdapterConfiguration