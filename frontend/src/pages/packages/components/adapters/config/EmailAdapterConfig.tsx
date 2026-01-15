import React from 'react';
import { Controller } from 'react-hook-form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';

interface EmailAdapterConfigProps {
  direction: 'SENDER' | 'RECEIVER';
  control: any;
  register: any;
  errors: any;
  setValue: any;
}

const EmailAdapterConfig: React.FC<EmailAdapterConfigProps> = ({ direction, control, register, errors, setValue }) => (
  <div className="space-y-6">
    {/* SMTP Settings */}
    <div>
      <h4 className="text-sm font-medium mb-4">SMTP Settings</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="smtpHost">SMTP Host *</Label>
          <Input
            id="smtpHost"
            placeholder="smtp.gmail.com"
            {...register('configuration.smtpHost', { required: 'SMTP host is required' })}
          />
          {errors.configuration?.smtpHost && (
            <p className="text-sm text-destructive">{errors.configuration.smtpHost.message}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="smtpPort">SMTP Port *</Label>
          <Input
            id="smtpPort"
            type="number"
            placeholder="587"
            {...register('configuration.smtpPort', { required: 'SMTP port is required' })}
          />
          {errors.configuration?.smtpPort && (
            <p className="text-sm text-destructive">{errors.configuration.smtpPort.message}</p>
          )}
        </div>
      </div>
    </div>

    {/* SSL/TLS Settings */}
    <div>
      <h4 className="text-sm font-medium mb-4">SSL/TLS Settings</h4>
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
                    field.onChange(checked);
                    setValue('configuration.startTlsEnabled', !checked);
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
                    field.onChange(checked);
                    setValue('configuration.sslEnabled', !checked);
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

    {/* From Address */}
    <div className="space-y-2">
      <Label htmlFor="fromAddress">From Address (Sender Email) *</Label>
      <Input
        id="fromAddress"
        placeholder="noreply@company.com"
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

    {/* Authentication */}
    <div>
      <h4 className="text-sm font-medium mb-4">Authentication</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="smtpUsername">Email Username *</Label>
          <Input
            id="smtpUsername"
            placeholder="notifications@company.com"
            {...register('configuration.smtpUsername', { required: 'Email username is required' })}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="smtpPassword">Email Password *</Label>
          <Input
            id="smtpPassword"
            type="password"
            placeholder="Enter email password"
            {...register('configuration.smtpPassword', { required: 'Email password is required' })}
          />
        </div>
      </div>
    </div>

    {/* RECEIVER specific */}
    {direction === 'RECEIVER' && (
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="recipients">Recipients (comma-separated) *</Label>
          <Input
            id="recipients"
            placeholder="user1@example.com, user2@example.com"
            {...register('configuration.recipients', { required: 'Recipients are required for receiver email' })}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="subject">Email Subject</Label>
          <Input
            id="subject"
            placeholder="File Transfer Notification"
            {...register('configuration.subject')}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="body">Email Body</Label>
          <textarea
            id="body"
            placeholder="Enter the email body text..."
            className="flex min-h-[120px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
            {...register('configuration.body')}
          />
          <p className="text-xs text-muted-foreground">
            Files will be attached directly from the flow execution context.
          </p>
        </div>
      </div>
    )}
  </div>
);

export default EmailAdapterConfig;
