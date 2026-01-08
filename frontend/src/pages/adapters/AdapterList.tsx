import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Settings, Play, Trash2, FileText, Server, Mail, AlertTriangle, CheckCircle, Clock } from 'lucide-react'
import { Link } from 'react-router-dom'
import { adapterApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { usePermissions } from '@/hooks/auth'
import { useEnvironment } from '@/hooks/useEnvironment'
import WebSocketStatus from '@/components/WebSocketStatus'
import { useNotifications } from '@/stores/ui'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { AdapterTestResultModal } from '@/components/ui/AdapterTestResultModal'

interface AdapterInterface {
  id: string
  name: string
  bank: string
  description: string
  adapterType: 'FILE' | 'SFTP' | 'EMAIL'
  direction: 'SENDER' | 'RECEIVER'
  active: boolean
  status: 'STARTED' | 'STOPPED' | 'ERROR' | 'STARTING' | 'STOPPING'
  lastExecution?: string
  lastExecutionStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING'
  createdAt: string
  configuration: Record<string, any>
}

const AdapterList: React.FC = () => {
  const { isAdmin } = usePermissions()
  const { data: environment } = useEnvironment()
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [selectedAdapter, setSelectedAdapter] = useState<AdapterInterface | null>(null)
  const [testResultModalOpen, setTestResultModalOpen] = useState(false)
  const [testResult, setTestResult] = useState<any>(null)

  const { data: apiResponse, isLoading, error: loadError } = useQuery({
    queryKey: ['adapters'],
    queryFn: adapterApi.getAllAdapters,
  })

  // Mutations
  const deleteAdapterMutation = useMutation({
    mutationFn: adapterApi.deleteAdapter,
    onSuccess: () => {
      success('Adapter Deleted', 'Adapter deleted successfully')
      queryClient.invalidateQueries({ queryKey: ['adapters'] })
      setDeleteDialogOpen(false)
      setSelectedAdapter(null)
    },
    onError: (err: any) => {
      error('Deletion Failed', err.response?.data?.message || 'Failed to delete adapter')
    },
  })

  const testAdapterMutation = useMutation({
    mutationFn: adapterApi.testAdapter,
    onSuccess: (result) => {
      // Set test result and show modal
      setTestResult(result)
      setTestResultModalOpen(true)
    },
    onError: (err: any) => {
      // Set error result and show modal
      setTestResult({
        data: {
          success: false,
          error: err.response?.data?.message || 'Failed to test adapter configuration',
          testType: 'ERROR'
        }
      })
      setTestResultModalOpen(true)
    },
  })

  // Event handlers
  const handleDeleteClick = (adapter: AdapterInterface) => {
    setSelectedAdapter(adapter)
    setDeleteDialogOpen(true)
  }

  const handleDeleteConfirm = () => {
    if (selectedAdapter) {
      deleteAdapterMutation.mutate(selectedAdapter.id)
    }
  }

  const handleTestClick = (adapterId: string) => {
    testAdapterMutation.mutate(adapterId)
  }

  // Extract the interfaces array from the API response
  const interfaces = apiResponse?.data || []

  // Check if creating adapters is allowed based on admin permissions and environment restrictions
  const canCreateAdapters = isAdmin() && (environment?.permissions?.canCreateAdapters ?? true)

  const getAdapterIcon = (adapterType: string) => {
    switch (adapterType) {
      case 'FILE':
        return <FileText className="h-5 w-5" />
      case 'SFTP':
        return <Server className="h-5 w-5" />
      case 'EMAIL':
        return <Mail className="h-5 w-5" />
      default:
        return <Settings className="h-5 w-5" />
    }
  }

  const getStatusIcon = (status?: string) => {
    switch (status) {
      case 'SUCCESS':
        return <CheckCircle className="h-4 w-4 text-success" />
      case 'FAILED':
        return <AlertTriangle className="h-4 w-4 text-destructive" />
      case 'RUNNING':
        return <Clock className="h-4 w-4 text-warning animate-pulse" />
      default:
        return <Clock className="h-4 w-4 text-muted-foreground" />
    }
  }

  if (isLoading) {
    return (
      <div className="content-spacing">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading adapters...</span>
          </div>
        </div>
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="content-spacing">
        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center space-x-3 text-destructive">
            <AlertTriangle className="h-5 w-5" />
            <p>Failed to load adapters. Please try again.</p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">Adapter Configuration</h1>
          <div className="flex items-center space-x-4">
            <p className="text-muted-foreground">Configure and manage your file transfer adapters</p>
            <WebSocketStatus size="sm" />
          </div>
        </div>
        {canCreateAdapters && (
          <Button asChild className="btn-primary">
            <Link to="/adapters/create">
              <Plus className="h-4 w-4 mr-2" />
              Create Adapter
            </Link>
          </Button>
        )}
      </div>

      {/* Adapter Type Overview */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center space-x-3 mb-3">
            <FileText className="h-8 w-8 text-primary" />
            <div>
              <h3 className="font-semibold text-foreground">File Adapters</h3>
              <p className="text-sm text-muted-foreground">Local file system operations</p>
            </div>
          </div>
          <div className="text-2xl font-bold text-foreground">
            {interfaces?.filter((i: AdapterInterface) => i.adapterType === 'FILE').length || 0}
          </div>
        </div>

        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center space-x-3 mb-3">
            <Server className="h-8 w-8 text-info" />
            <div>
              <h3 className="font-semibold text-foreground">SFTP Adapters</h3>
              <p className="text-sm text-muted-foreground">Secure file transfer protocol</p>
            </div>
          </div>
          <div className="text-2xl font-bold text-foreground">
            {interfaces?.filter((i: AdapterInterface) => i.adapterType === 'SFTP').length || 0}
          </div>
        </div>

        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center space-x-3 mb-3">
            <Mail className="h-8 w-8 text-success" />
            <div>
              <h3 className="font-semibold text-foreground">Email Adapters</h3>
              <p className="text-sm text-muted-foreground">Email-based file transfer</p>
            </div>
          </div>
          <div className="text-2xl font-bold text-foreground">
            {interfaces?.filter((i: AdapterInterface) => i.adapterType === 'EMAIL').length || 0}
          </div>
        </div>
      </div>

      {/* Adapter List */}
      {interfaces && interfaces.length > 0 ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {interfaces.map((adapter: AdapterInterface) => (
            <Card key={adapter.id} className="app-card border">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    {getAdapterIcon(adapter.adapterType)}
                    <div>
                      <CardTitle className="text-lg text-foreground">{adapter.name}</CardTitle>
                      <p className="text-sm font-medium text-primary">{adapter.bank}</p>
                      <p className="text-sm text-muted-foreground">{adapter.description}</p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-2">
                    <div className={`w-3 h-3 rounded-full ${
                      adapter.active ? 'bg-success animate-glow' : 'bg-muted-foreground'
                    }`} />
                    <span className={`text-xs px-2 py-1 rounded ${
                      adapter.active ? 'bg-success/20 text-success' : 'bg-muted/20 text-muted-foreground'
                    }`}>
                      {adapter.active ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">Type:</span>
                      <span className="ml-2 text-foreground">{adapter.adapterType}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Direction:</span>
                      <span className="ml-2 text-foreground">{adapter.direction}</span>
                    </div>
                  </div>
                  
                  {adapter.lastExecution && (
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">Last Execution:</span>
                      <div className="flex items-center space-x-2">
                        {getStatusIcon(adapter.lastExecutionStatus)}
                        <span className="text-foreground">
                          {new Date(adapter.lastExecution).toLocaleString()}
                        </span>
                      </div>
                    </div>
                  )}

                  <div className="flex items-center space-x-2 pt-4">
                    {isAdmin() && (
                      <Button variant="outline" size="sm" asChild>
                        <Link to={`/adapters/${adapter.id}/edit`}>
                          <Settings className="h-4 w-4 mr-2" />
                          Configure
                        </Link>
                      </Button>
                    )}
                    {/* Show test button for all adapter types */}
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => handleTestClick(adapter.id)}
                      disabled={testAdapterMutation.isPending}
                    >
                      <Play className="h-4 w-4 mr-2" />
                      {testAdapterMutation.isPending ? 'Testing...' : 'Test'}
                    </Button>
                    {isAdmin() && (
                      <Button 
                        variant="outline" 
                        size="sm" 
                        className="text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(adapter)}
                        disabled={deleteAdapterMutation.isPending}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <div className="app-card rounded-lg p-12 border text-center">
          <Settings className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
          <h3 className="text-xl font-semibold text-foreground mb-2">No Adapters Configured</h3>
          <p className="text-muted-foreground mb-6">Get started by creating your first adapter interface</p>
          {canCreateAdapters && (
            <Button asChild className="btn-primary">
              <Link to="/adapters/create">
                <Plus className="h-4 w-4 mr-2" />
                Create Your First Adapter
              </Link>
            </Button>
          )}
        </div>
      )}

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Adapter</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete the adapter "{selectedAdapter?.name}"? 
              This action cannot be undone and will permanently remove the adapter configuration.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button 
              variant="outline" 
              onClick={() => setDeleteDialogOpen(false)}
              disabled={deleteAdapterMutation.isPending}
            >
              Cancel
            </Button>
            <Button 
              variant="destructive" 
              onClick={handleDeleteConfirm}
              disabled={deleteAdapterMutation.isPending}
            >
              {deleteAdapterMutation.isPending ? 'Deleting...' : 'Delete Adapter'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Test Results Modal */}
      <AdapterTestResultModal 
        open={testResultModalOpen}
        onOpenChange={setTestResultModalOpen}
        testResult={testResult}
      />
    </div>
  )
}

export default AdapterList