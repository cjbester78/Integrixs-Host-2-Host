import React from 'react';
import { Controller, useWatch } from 'react-hook-form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

interface FileAdapterConfigProps {
  direction: 'SENDER' | 'RECEIVER';
  control: any;
  register: any;
  errors: any;
}

const FileAdapterConfig: React.FC<FileAdapterConfigProps> = ({ direction, control, register, errors }) => {
  const watchArchiveFaultyFiles = useWatch({
    control,
    name: 'configuration.archiveFaultySourceFiles',
    defaultValue: false
  });

  const watchPostProcessAction = useWatch({
    control,
    name: 'configuration.postProcessAction',
    defaultValue: 'ARCHIVE'
  });

  const watchOutputFilenameMode = useWatch({
    control,
    name: 'configuration.outputFilenameMode',
    defaultValue: 'UseOriginal'
  });

  const watchScheduleMode = useWatch({
    control,
    name: 'configuration.scheduleMode',
    defaultValue: 'OnTime'
  });

  const watchScheduleType = useWatch({
    control,
    name: 'configuration.scheduleType',
    defaultValue: 'Daily'
  });

  return (
    <div className="space-y-6">
      {/* Directory Configuration */}
      <div>
        <h4 className="text-sm font-medium mb-4">
          {direction === 'SENDER' ? 'Source' : 'Target'} Configuration
        </h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {direction === 'SENDER' ? (
            <>
              <div className="space-y-2">
                <Label htmlFor="sourceDirectory">Source Directory *</Label>
                <Input
                  id="sourceDirectory"
                  {...register('configuration.sourceDirectory', { required: true })}
                  placeholder="/data/input"
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
                />
                {errors?.configuration?.targetDirectory && (
                  <span className="text-sm text-destructive">Target directory is required</span>
                )}
              </div>
              <div className="flex items-center space-x-2 pt-8">
                <Controller
                  name="configuration.createTargetDirectory"
                  control={control}
                  render={({ field }) => (
                    <input
                      type="checkbox"
                      id="createTargetDirectory"
                      checked={Boolean(field.value)}
                      onChange={field.onChange}
                      className="w-4 h-4"
                    />
                  )}
                />
                <Label htmlFor="createTargetDirectory">Create Target Directory</Label>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Processing Parameters - SENDER */}
      {direction === 'SENDER' && (
        <div>
          <h4 className="text-sm font-medium mb-4">Processing Parameters</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="retryInterval">Retry Interval (secs)</Label>
              <Input
                id="retryInterval"
                type="number"
                {...register('configuration.retryInterval')}
                placeholder="60"
                defaultValue="60"
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
      )}

      {/* File Output Configuration - RECEIVER */}
      {direction === 'RECEIVER' && (
        <div>
          <h4 className="text-sm font-medium mb-4">File Output Configuration</h4>
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
          {watchOutputFilenameMode === 'Custom' && (
            <div className="mt-4 space-y-2">
              <Label htmlFor="customFilenamePattern">Custom Filename Pattern</Label>
              <Input
                id="customFilenamePattern"
                {...register('configuration.customFilenamePattern')}
                placeholder="{original_name}_{timestamp}.{extension}"
              />
              <p className="text-sm text-muted-foreground">
                Available variables: {'{original_name}'}, {'{timestamp}'}, {'{date}'}, {'{extension}'}
              </p>
            </div>
          )}
        </div>
      )}

      {/* Archive Configuration - SENDER when Archive mode */}
      {direction === 'SENDER' && watchPostProcessAction === 'ARCHIVE' && (
        <div>
          <h4 className="text-sm font-medium mb-4">Archive Configuration</h4>
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
                    className="w-4 h-4"
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
                    className="w-4 h-4"
                  />
                )}
              />
              <Label htmlFor="compressArchive">Compress archived files</Label>
            </div>
          </div>
        </div>
      )}

      {/* Error Handling - SENDER */}
      {direction === 'SENDER' && (
        <div>
          <h4 className="text-sm font-medium mb-4">Error Handling</h4>
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
                    className="w-4 h-4"
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
                    className="w-4 h-4"
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

      {/* File Construction - RECEIVER */}
      {direction === 'RECEIVER' && (
        <div>
          <h4 className="text-sm font-medium mb-4">File Construction</h4>
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
        <h4 className="text-sm font-medium mb-4">Advanced Parameters</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="msecsToWaitBeforeModificationCheck">Msecs to Wait Before Modification Check</Label>
            <Input
              id="msecsToWaitBeforeModificationCheck"
              type="number"
              {...register('configuration.msecsToWaitBeforeModificationCheck')}
              placeholder="0"
              defaultValue="0"
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
              defaultValue="0"
              min="0"
            />
          </div>
        </div>
      </div>

      {/* Scheduler Settings - SENDER only */}
      {direction === 'SENDER' && (
        <div>
          <h4 className="text-sm font-medium mb-4">Scheduler Settings</h4>
          <div className="space-y-4">
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

            {watchScheduleMode === 'OnTime' && (
              <div className="space-y-2">
                <Label htmlFor="onTimeValue">Execution Time</Label>
                <Input
                  id="onTimeValue"
                  type="time"
                  defaultValue="20:27"
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
                    {...register('configuration.everyStartTime')}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="everyEndTime">End Time</Label>
                  <Input
                    id="everyEndTime"
                    type="time"
                    defaultValue="24:00"
                    {...register('configuration.everyEndTime')}
                  />
                </div>
              </div>
            )}

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
                        name={`configuration.weeklyDays.${day.key}` as any}
                        control={control}
                        defaultValue={false}
                        render={({ field }) => (
                          <input
                            type="checkbox"
                            id={`weekly_${day.key}`}
                            checked={Boolean(field.value)}
                            onChange={field.onChange}
                            className="w-4 h-4"
                          />
                        )}
                      />
                      <Label htmlFor={`weekly_${day.key}`} className="text-sm">{day.label}</Label>
                    </div>
                  ))}
                </div>
              </div>
            )}

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
  );
};

export default FileAdapterConfig;
