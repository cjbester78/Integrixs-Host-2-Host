import { Play, Pause, RotateCcw, Download, Upload, Settings } from 'lucide-react'

export function Operations() {
  return (
    <div className="content-spacing">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-foreground mb-2">File Transfer Operations</h1>
        <p className="text-muted-foreground">Manage and monitor your bank file transfer operations</p>
      </div>

      {/* Operation Controls */}
      <div className="app-card rounded-lg p-6 border animate-fade-in">
        <h2 className="text-xl font-semibold text-foreground mb-4">Operation Controls</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <button className="btn-primary-gradient rounded-md px-6 py-3 flex items-center justify-center space-x-2 hover-scale">
            <Play className="h-5 w-5" />
            <span>Start All Operations</span>
          </button>
          <button className="btn-secondary rounded-md px-6 py-3 flex items-center justify-center space-x-2 hover-scale">
            <Pause className="h-5 w-5" />
            <span>Pause Operations</span>
          </button>
          <button className="btn-secondary rounded-md px-6 py-3 flex items-center justify-center space-x-2 hover-scale">
            <RotateCcw className="h-5 w-5" />
            <span>Retry Failed</span>
          </button>
        </div>
      </div>

      {/* Bank Operations */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* FNB Operations */}
        <div className="app-card rounded-lg p-6 border animate-fade-in">
          <h3 className="text-lg font-semibold text-foreground mb-4 flex items-center">
            <div className="w-3 h-3 bg-success rounded-full mr-3"></div>
            First National Bank (FNB)
          </h3>
          
          <div className="space-y-4">
            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center space-x-2">
                  <Upload className="h-4 w-4 text-primary" />
                  <span className="font-medium text-foreground">Payment Upload</span>
                </div>
                <span className="badge-success px-2 py-1 rounded text-xs">Ready</span>
              </div>
              <p className="text-sm text-muted-foreground mb-3">Upload payment files to FNB SFTP server</p>
              <div className="flex space-x-2">
                <button className="btn-primary rounded px-3 py-1 text-xs">Run Now</button>
                <button className="btn-secondary rounded px-3 py-1 text-xs">
                  <Settings className="h-3 w-3" />
                </button>
              </div>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center space-x-2">
                  <Download className="h-4 w-4 text-info" />
                  <span className="font-medium text-foreground">Audit Download</span>
                </div>
                <span className="badge-success px-2 py-1 rounded text-xs">Ready</span>
              </div>
              <p className="text-sm text-muted-foreground mb-3">Download audit files from FNB</p>
              <div className="flex space-x-2">
                <button className="btn-primary rounded px-3 py-1 text-xs">Run Now</button>
                <button className="btn-secondary rounded px-3 py-1 text-xs">
                  <Settings className="h-3 w-3" />
                </button>
              </div>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center space-x-2">
                  <Download className="h-4 w-4 text-info" />
                  <span className="font-medium text-foreground">POP Download</span>
                </div>
                <span className="badge-warning px-2 py-1 rounded text-xs">Waiting</span>
              </div>
              <p className="text-sm text-muted-foreground mb-3">Download POP confirmation files</p>
              <div className="flex space-x-2">
                <button className="btn-primary rounded px-3 py-1 text-xs">Run Now</button>
                <button className="btn-secondary rounded px-3 py-1 text-xs">
                  <Settings className="h-3 w-3" />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Stanbic Operations */}
        <div className="app-card rounded-lg p-6 border animate-fade-in">
          <h3 className="text-lg font-semibold text-foreground mb-4 flex items-center">
            <div className="w-3 h-3 bg-success rounded-full mr-3"></div>
            Standard Bank (Stanbic)
          </h3>
          
          <div className="space-y-4">
            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center space-x-2">
                  <Upload className="h-4 w-4 text-primary" />
                  <span className="font-medium text-foreground">Payment Upload</span>
                </div>
                <span className="badge-success px-2 py-1 rounded text-xs">Ready</span>
              </div>
              <p className="text-sm text-muted-foreground mb-3">Upload payment files to Stanbic SFTP server</p>
              <div className="flex space-x-2">
                <button className="btn-primary rounded px-3 py-1 text-xs">Run Now</button>
                <button className="btn-secondary rounded px-3 py-1 text-xs">
                  <Settings className="h-3 w-3" />
                </button>
              </div>
            </div>

            <div className="bg-secondary rounded-lg p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center space-x-2">
                  <Download className="h-4 w-4 text-info" />
                  <span className="font-medium text-foreground">Audit Download</span>
                </div>
                <span className="badge-success px-2 py-1 rounded text-xs">Ready</span>
              </div>
              <p className="text-sm text-muted-foreground mb-3">Download audit files from Stanbic</p>
              <div className="flex space-x-2">
                <button className="btn-primary rounded px-3 py-1 text-xs">Run Now</button>
                <button className="btn-secondary rounded px-3 py-1 text-xs">
                  <Settings className="h-3 w-3" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Operation History */}
      <div className="app-card rounded-lg p-6 border animate-fade-in">
        <h2 className="text-xl font-semibold text-foreground mb-4">Operation History</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="text-left py-3 px-2 text-muted-foreground">Time</th>
                <th className="text-left py-3 px-2 text-muted-foreground">Bank</th>
                <th className="text-left py-3 px-2 text-muted-foreground">Operation</th>
                <th className="text-left py-3 px-2 text-muted-foreground">Files</th>
                <th className="text-left py-3 px-2 text-muted-foreground">Status</th>
                <th className="text-left py-3 px-2 text-muted-foreground">Duration</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-border">
                <td className="py-3 px-2 text-foreground">14:30</td>
                <td className="py-3 px-2 text-foreground">FNB</td>
                <td className="py-3 px-2 text-foreground">Payment Upload</td>
                <td className="py-3 px-2 text-foreground">5</td>
                <td className="py-3 px-2">
                  <span className="badge-success px-2 py-1 rounded text-xs">Success</span>
                </td>
                <td className="py-3 px-2 text-muted-foreground">45s</td>
              </tr>
              <tr className="border-b border-border">
                <td className="py-3 px-2 text-foreground">14:25</td>
                <td className="py-3 px-2 text-foreground">Stanbic</td>
                <td className="py-3 px-2 text-foreground">Audit Download</td>
                <td className="py-3 px-2 text-foreground">3</td>
                <td className="py-3 px-2">
                  <span className="badge-success px-2 py-1 rounded text-xs">Success</span>
                </td>
                <td className="py-3 px-2 text-muted-foreground">32s</td>
              </tr>
              <tr className="border-b border-border">
                <td className="py-3 px-2 text-foreground">14:20</td>
                <td className="py-3 px-2 text-foreground">FNB</td>
                <td className="py-3 px-2 text-foreground">POP Download</td>
                <td className="py-3 px-2 text-foreground">0</td>
                <td className="py-3 px-2">
                  <span className="badge-warning px-2 py-1 rounded text-xs">Pending</span>
                </td>
                <td className="py-3 px-2 text-muted-foreground">-</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}