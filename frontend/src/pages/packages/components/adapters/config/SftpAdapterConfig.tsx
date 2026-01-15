import React from 'react';
import { Controller, useWatch } from 'react-hook-form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

interface SftpAdapterConfigProps {
  direction: 'SENDER' | 'RECEIVER';
  control: any;
  register: any;
  errors: any;
  sshKeysData?: any[];
}

const SftpAdapterConfig: React.FC<SftpAdapterConfigProps> = ({ direction, control, register, errors, sshKeysData = [] }) => {
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

  const watchAuthType = useWatch({
    control,
    name: 'configuration.authenticationType',
    defaultValue: 'USERNAME_PASSWORD'
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

  return (
    <div className="space-y-6">
      {/* Connection Settings */}
      <div>
        <h4 className="text-sm font-medium mb-4">Connection Settings</h4>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="host">Host *</Label>
            <Input
              id="host"
              placeholder="sftp.bank.com"
              {...register('configuration.host', { required: 'SFTP host is required' })}
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
              {...register('configuration.port')}
            />
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

        {/* Authentication Fields */}
        <div className="space-y-4 mt-4">
          {(watchAuthType === 'USERNAME_PASSWORD' || watchAuthType === 'DUAL') && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="username">Username</Label>
                <Input
                  id="username"
                  placeholder="Administrator"
                  {...register('configuration.username')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  placeholder="••••••••"
                  {...register('configuration.password')}
                />
              </div>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="serverFingerprint">Server Fingerprint</Label>
            <Input
              id="serverFingerprint"
              placeholder="SHA256:AAAA... or MD5:aa:bb:cc..."
              {...register('configuration.serverFingerprint')}
            />
            <p className="text-xs text-muted-foreground">
              Optional: SSH server host key fingerprint for additional security verification
            </p>
          </div>

          {(watchAuthType === 'PUBLIC_KEY' || watchAuthType === 'DUAL') && (
            <div className="space-y-2">
              <Label htmlFor="sshKeyId">SSH Key</Label>
              <Controller
                name="configuration.sshKeyId"
                control={control}
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
            </div>
          )}
        </div>
      </div>

      {/* RECEIVER - Remote Destination Settings */}
      {direction === 'RECEIVER' && (
        <>
          <div>
            <h4 className="text-sm font-medium mb-4">Remote Destination Settings</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="targetDirectory">Target Directory *</Label>
                <Input
                  id="targetDirectory"
                  placeholder="/incoming/payments"
                  {...register('configuration.targetDirectory', { required: 'Target directory is required' })}
                />
                {errors.configuration?.targetDirectory && (
                  <p className="text-sm text-destructive">{errors.configuration.targetDirectory.message}</p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="remoteFilePermissions">Remote File Permissions</Label>
                <Input
                  id="remoteFilePermissions"
                  placeholder="644"
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
                      className="w-4 h-4"
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
                      className="w-4 h-4"
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
                placeholder=".tmp"
                {...register('configuration.temporaryFileSuffix')}
              />
            </div>
          </div>

          {/* Output Filename Configuration */}
          <div>
            <h4 className="text-sm font-medium mb-4">Output Filename Configuration</h4>
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
            {watchOutputFilenameMode === 'Custom' && (
              <div className="space-y-2 mt-4">
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
        </>
      )}

      {/* Connection Properties */}
      <div>
        <h4 className="text-sm font-medium mb-4">Connection Properties</h4>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="connectionTimeout">Connection Timeout (ms)</Label>
            <Input
              id="connectionTimeout"
              type="number"
              placeholder="30000"
              defaultValue="30000"
              {...register('configuration.connectionTimeout')}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="socketTimeout">Socket Timeout (ms)</Label>
            <Input
              id="socketTimeout"
              type="number"
              placeholder="60000"
              defaultValue="60000"
              {...register('configuration.socketTimeout')}
            />
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
              {...register('configuration.sessionTimeout')}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="channelTimeout">Channel Timeout (ms)</Label>
            <Input
              id="channelTimeout"
              type="number"
              placeholder="30000"
              defaultValue="30000"
              {...register('configuration.channelTimeout')}
            />
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
              {...register('configuration.connectionPoolSize')}
            />
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
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
        <h4 className="text-sm font-medium mb-4">Transfer Settings</h4>
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
              {...register('configuration.maxRetryAttempts')}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="retryDelayMs">Retry Delay (ms)</Label>
            <Input
              id="retryDelayMs"
              type="number"
              placeholder="5000"
              defaultValue="5000"
              {...register('configuration.retryDelayMs')}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="maxFileSize">Max File Size (MB)</Label>
            <Input
              id="maxFileSize"
              type="number"
              placeholder="100"
              defaultValue="100"
              {...register('configuration.maxFileSize')}
            />
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          <div className="space-y-2">
            <Label htmlFor="transferBufferSize">Transfer Buffer Size (KB)</Label>
            <Input
              id="transferBufferSize"
              type="number"
              placeholder="32"
              defaultValue="32"
              {...register('configuration.transferBufferSize')}
            />
          </div>
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

      {/* SENDER - Remote Pickup Settings */}
      {direction === 'SENDER' && (
        <>
          <div>
            <h4 className="text-sm font-medium mb-4">Remote Pickup Settings</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="sourceDirectory">Source Directory *</Label>
                <Input
                  id="sourceDirectory"
                  placeholder="/outgoing/statements"
                  {...register('configuration.sourceDirectory', { required: 'Source directory is required' })}
                />
                {errors.configuration?.sourceDirectory && (
                  <p className="text-sm text-destructive">{errors.configuration.sourceDirectory.message}</p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="filename">Filename Pattern *</Label>
                <Input
                  id="filename"
                  placeholder="*.csv"
                  {...register('configuration.filename', { required: 'Filename pattern is required' })}
                />
                {errors.configuration?.filename && (
                  <p className="text-sm text-destructive">{errors.configuration.filename.message}</p>
                )}
              </div>
            </div>
            <div className="mt-4 space-y-2">
              <Label htmlFor="excludePattern">Exclude Pattern</Label>
              <Input
                id="excludePattern"
                placeholder="*.tmp,*.processing"
                {...register('configuration.excludePattern')}
              />
            </div>
          </div>

          {/* Scheduler Settings */}
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

          {/* Empty File Handling */}
          <div>
            <h4 className="text-sm font-medium mb-4">Empty File Handling</h4>
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

          {/* Post-Processing */}
          <div>
            <h4 className="text-sm font-medium mb-4">Post-Processing</h4>
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
                            className="w-4 h-4"
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
                      {...register('configuration.processedDirectory')}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="processedFileSuffix">Processed File Suffix</Label>
                    <Input
                      id="processedFileSuffix"
                      placeholder=".processed"
                      {...register('configuration.processedFileSuffix')}
                    />
                  </div>
                </div>
              )}

              {watchPostProcessAction === 'KEEP_AND_REPROCESS' && (
                <div className="space-y-2">
                  <Label htmlFor="reprocessingDelay">Reprocessing Delay (ms)</Label>
                  <Input
                    id="reprocessingDelay"
                    type="number"
                    placeholder="3600000"
                    {...register('configuration.reprocessingDelay')}
                  />
                </div>
              )}

              {watchPostProcessAction === 'DELETE' && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="deleteBackupDirectory">Delete Backup Directory</Label>
                    <Input
                      id="deleteBackupDirectory"
                      placeholder="/data/deleted_backup"
                      {...register('configuration.deleteBackupDirectory')}
                    />
                  </div>
                  <div className="flex items-center space-x-2 pt-8">
                    <Controller
                      name="configuration.confirmDelete"
                      control={control}
                      render={({ field }) => (
                        <input
                          type="checkbox"
                          id="confirmDelete"
                          checked={Boolean(field.value)}
                          onChange={field.onChange}
                          className="w-4 h-4"
                        />
                      )}
                    />
                    <Label htmlFor="confirmDelete">Confirm Delete</Label>
                  </div>
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default SftpAdapterConfig;
