import { Save, TestTube, RefreshCw } from 'lucide-react'

export function Configuration() {
  return (
    <div className="content-spacing">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-foreground mb-2">System Configuration</h1>
        <p className="text-muted-foreground">Configure bank connections, file paths, and operation settings</p>
      </div>

      {/* Environment Selection */}
      <div className="app-card rounded-lg p-6 border animate-fade-in">
        <h2 className="text-xl font-semibold text-foreground mb-4">Environment Settings</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <button className="btn-secondary rounded-md px-4 py-2 text-center">
            Development
          </button>
          <button className="btn-secondary rounded-md px-4 py-2 text-center">
            QAS/Testing
          </button>
          <button className="btn-primary rounded-md px-4 py-2 text-center">
            Production
          </button>
        </div>
        <p className="text-sm text-muted-foreground mt-2">Current environment: <span className="text-warning">Production</span></p>
      </div>

      {/* Bank Configurations */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* FNB Configuration */}
        <div className="app-card rounded-lg p-6 border animate-fade-in">
          <h3 className="text-lg font-semibold text-foreground mb-4">First National Bank (FNB)</h3>
          
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-foreground mb-1">SFTP Host</label>
              <input 
                type="text" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="196.11.129.67"
              />
            </div>
            
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-foreground mb-1">Port</label>
                <input 
                  type="text" 
                  className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                  value="22"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-foreground mb-1">Username</label>
                <input 
                  type="text" 
                  className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                  value="XK044E"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">SSH Private Key Path</label>
              <div className="flex space-x-2">
                <input 
                  type="text" 
                  className="flex-1 px-3 py-2 bg-input border border-border rounded-md text-foreground"
                  value="/ssh/id_rsa"
                />
                <button className="btn-secondary rounded px-3 py-2">Browse</button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Local Directory (Outbound)</label>
              <input 
                type="text" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="E:\\DEV\\FNB\\Outbound\\"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Remote Directory (Payments)</label>
              <input 
                type="text" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="/payments/pain00100103/in/"
              />
            </div>

            <div className="flex space-x-2 pt-4">
              <button className="btn-primary rounded px-4 py-2 flex items-center space-x-2">
                <TestTube className="h-4 w-4" />
                <span>Test Connection</span>
              </button>
              <button className="btn-secondary rounded px-4 py-2 flex items-center space-x-2">
                <Save className="h-4 w-4" />
                <span>Save</span>
              </button>
            </div>
          </div>
        </div>

        {/* Stanbic Configuration */}
        <div className="app-card rounded-lg p-6 border animate-fade-in">
          <h3 className="text-lg font-semibold text-foreground mb-4">Standard Bank (Stanbic)</h3>
          
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-foreground mb-1">SFTP Host</label>
              <input 
                type="text" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="stanbic.sftp.host.com"
              />
            </div>
            
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-foreground mb-1">Port</label>
                <input 
                  type="text" 
                  className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                  value="22"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-foreground mb-1">Username</label>
                <input 
                  type="text" 
                  className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                  value="stanbic_user"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">SSH Private Key Path</label>
              <div className="flex space-x-2">
                <input 
                  type="text" 
                  className="flex-1 px-3 py-2 bg-input border border-border rounded-md text-foreground"
                  value="/ssh/stanbic_id_rsa"
                />
                <button className="btn-secondary rounded px-3 py-2">Browse</button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Local Directory (Outbound)</label>
              <input 
                type="text" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="E:\\DEV\\Stanbic\\Outbound\\"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Remote Directory (Payments)</label>
              <input 
                type="text" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="/payments/in/"
              />
            </div>

            <div className="flex space-x-2 pt-4">
              <button className="btn-primary rounded px-4 py-2 flex items-center space-x-2">
                <TestTube className="h-4 w-4" />
                <span>Test Connection</span>
              </button>
              <button className="btn-secondary rounded px-4 py-2 flex items-center space-x-2">
                <Save className="h-4 w-4" />
                <span>Save</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* System Settings */}
      <div className="app-card rounded-lg p-6 border animate-fade-in">
        <h2 className="text-xl font-semibold text-foreground mb-4">System Settings</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Session Timeout (ms)</label>
              <input 
                type="number" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="60000"
              />
            </div>
            
            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Channel Timeout (ms)</label>
              <input 
                type="number" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="60000"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Retry Count</label>
              <input 
                type="number" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="3"
              />
            </div>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Retry Delay (ms)</label>
              <input 
                type="number" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="5000"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Bank Operation Delay (seconds)</label>
              <input 
                type="number" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="15"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Log Retention (days)</label>
              <input 
                type="number" 
                className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                value="30"
              />
            </div>
          </div>
        </div>

        <div className="flex space-x-2 pt-6">
          <button className="btn-primary-gradient rounded px-6 py-2 flex items-center space-x-2">
            <Save className="h-4 w-4" />
            <span>Save All Settings</span>
          </button>
          <button className="btn-secondary rounded px-6 py-2 flex items-center space-x-2">
            <RefreshCw className="h-4 w-4" />
            <span>Reset to Defaults</span>
          </button>
        </div>
      </div>
    </div>
  )
}