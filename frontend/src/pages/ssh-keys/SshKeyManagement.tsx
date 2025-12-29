import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Key, Download, Trash2, Eye, EyeOff, CheckCircle, XCircle, Copy, ShieldCheck, ShieldX } from 'lucide-react'
import { sshKeyApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { useNotifications } from '@/stores/ui'
import { usePermissions } from '@/hooks/auth'

interface SshKey {
  id: string
  name: string
  description: string
  keyType: 'RSA' | 'DSA'
  keySize: number
  fingerprint: string
  publicKey: string
  enabled: boolean
  createdAt: string
  expiresAt: string
}

const SshKeyManagement: React.FC = () => {
  const navigate = useNavigate()
  const { isAdmin } = usePermissions()
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()
  
  const [showPrivateKeys, setShowPrivateKeys] = useState<Record<string, boolean>>({})

  // Fetch SSH keys
  const { data: sshKeys, isLoading } = useQuery<SshKey[]>({
    queryKey: ['ssh-keys'],
    queryFn: sshKeyApi.getAllKeys,
  })

  // Mutations
  const toggleKeyStatusMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => 
      sshKeyApi.toggleKeyStatus(id, enabled),
    onSuccess: () => {
      success('Status Updated', 'SSH key status changed successfully')
      queryClient.invalidateQueries({ queryKey: ['ssh-keys'] })
    },
    onError: (err: any) => {
      error('Status Change Failed', err.response?.data?.message || 'Failed to change key status')
    },
  })

  const deleteKeyMutation = useMutation({
    mutationFn: sshKeyApi.deleteKey,
    onSuccess: () => {
      success('Key Deleted', 'SSH key deleted successfully')
      queryClient.invalidateQueries({ queryKey: ['ssh-keys'] })
    },
    onError: (err: any) => {
      error('Deletion Failed', err.response?.data?.message || 'Failed to delete SSH key')
    },
  })

  const handleDownloadPrivateKey = async (keyId: string, keyName: string) => {
    try {
      const blob = await sshKeyApi.downloadPrivateKey(keyId)
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${keyName}_private_key`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      success('Download Complete', 'Private key downloaded successfully')
    } catch (err: any) {
      error('Download Failed', err.response?.data?.message || 'Failed to download private key')
    }
  }

  const handleDownloadPublicKey = async (keyId: string, keyName: string) => {
    try {
      const blob = await sshKeyApi.downloadPublicKey(keyId)
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${keyName}_public_key.pub`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      success('Download Complete', 'Public key downloaded successfully')
    } catch (err: any) {
      error('Download Failed', err.response?.data?.message || 'Failed to download public key')
    }
  }

  const handleCopyPublicKey = (publicKey: string) => {
    navigator.clipboard.writeText(publicKey).then(() => {
      success('Copied', 'Public key copied to clipboard')
    }).catch(() => {
      error('Copy Failed', 'Failed to copy public key to clipboard')
    })
  }

  const togglePrivateKeyVisibility = (keyId: string) => {
    setShowPrivateKeys(prev => ({
      ...prev,
      [keyId]: !prev[keyId]
    }))
  }

  if (isLoading) {
    return (
      <div className="content-spacing">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading SSH keys...</span>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">SSH Key Management</h1>
          <p className="text-muted-foreground">Generate and manage SSH keys for secure SFTP connections</p>
        </div>
        <div className="flex items-center space-x-4">
          {sshKeys && sshKeys.length > 0 && (
            <div className="text-sm text-muted-foreground">
              {sshKeys.filter(k => k.enabled).length} of {sshKeys.length} keys active
            </div>
          )}
          {isAdmin() && (
            <Button 
              className="btn-primary"
              onClick={() => navigate('/ssh-keys/create')}
            >
              <Plus className="h-4 w-4 mr-2" />
              Generate New Key
            </Button>
          )}
        </div>
      </div>

      {/* SSH Keys List */}
      {sshKeys && sshKeys.length > 0 ? (
        <div className="space-y-4">
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
            <Card className="app-card border">
              <CardContent className="p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm text-muted-foreground">Total Keys</p>
                    <p className="text-2xl font-bold text-foreground">{sshKeys.length}</p>
                  </div>
                  <Key className="h-8 w-8 text-primary" />
                </div>
              </CardContent>
            </Card>
            <Card className="app-card border">
              <CardContent className="p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm text-muted-foreground">Valid & Active</p>
                    <p className="text-2xl font-bold text-green-500">{sshKeys.filter(k => k.enabled).length}</p>
                  </div>
                  <ShieldCheck className="h-8 w-8 text-green-500" />
                </div>
              </CardContent>
            </Card>
            <Card className="app-card border">
              <CardContent className="p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm text-muted-foreground">Disabled</p>
                    <p className="text-2xl font-bold text-muted-foreground">{sshKeys.filter(k => !k.enabled).length}</p>
                  </div>
                  <ShieldX className="h-8 w-8 text-muted-foreground" />
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Keys Grid */}
          <div className="grid grid-cols-1 gap-4">
            {sshKeys.map((key) => (
              <Card key={key.id} className={`app-card border ${key.enabled ? 'border-l-4 border-l-green-500' : 'border-l-4 border-l-muted-foreground'}`}>
                <CardHeader className="pb-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-3">
                      <div className={`p-2 rounded-lg ${key.enabled ? 'bg-green-500/10' : 'bg-muted/20'}`}>
                        <Key className={`h-5 w-5 ${key.enabled ? 'text-green-500' : 'text-muted-foreground'}`} />
                      </div>
                      <div>
                        <CardTitle className="text-lg text-foreground">{key.name}</CardTitle>
                        <p className="text-sm text-muted-foreground">{key.description}</p>
                      </div>
                    </div>
                    <div className="flex items-center space-x-3">
                      {/* Validity Badge */}
                      <div className={`flex items-center space-x-2 px-3 py-1.5 rounded-full ${
                        key.enabled 
                          ? 'bg-green-500/10 border border-green-500/20' 
                          : 'bg-muted/20 border border-border'
                      }`}>
                        {key.enabled ? (
                          <>
                            <ShieldCheck className="h-4 w-4 text-green-500" />
                            <span className="text-sm font-medium text-green-500">Valid</span>
                          </>
                        ) : (
                          <>
                            <ShieldX className="h-4 w-4 text-muted-foreground" />
                            <span className="text-sm font-medium text-muted-foreground">Disabled</span>
                          </>
                        )}
                      </div>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {/* Key Details */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                      <div>
                        <span className="text-muted-foreground">Type:</span>
                        <span className="ml-2 text-foreground font-medium">{key.keyType} {key.keySize}</span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Created:</span>
                        <span className="ml-2 text-foreground">{new Date(key.createdAt).toLocaleDateString()}</span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Expires:</span>
                        <span className={`ml-2 font-medium ${
                          new Date(key.expiresAt) < new Date() ? 'text-red-500' : 
                          new Date(key.expiresAt) < new Date(Date.now() + 30 * 24 * 60 * 60 * 1000) ? 'text-yellow-500' : 
                          'text-foreground'
                        }`}>
                          {new Date(key.expiresAt).toLocaleDateString()}
                        </span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Status:</span>
                        <span className={`ml-2 font-medium ${
                          new Date(key.expiresAt) < new Date() ? 'text-red-500' : 'text-green-500'
                        }`}>
                          {new Date(key.expiresAt) < new Date() ? 'Expired' : 'Valid'}
                        </span>
                      </div>
                    </div>

                    {/* Fingerprint */}
                    <div>
                      <label className="text-sm font-medium text-foreground">Fingerprint:</label>
                      <div className="mt-1 font-mono text-xs bg-secondary p-2 rounded">
                        {key.fingerprint}
                      </div>
                    </div>

                    {/* Public Key */}
                    <div>
                      <div className="flex items-center justify-between">
                        <label className="text-sm font-medium text-foreground">Public Key:</label>
                        <div className="flex items-center space-x-2">
                          <Button 
                            variant="outline" 
                            size="sm"
                            onClick={() => handleCopyPublicKey(key.publicKey)}
                          >
                            <Copy className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => togglePrivateKeyVisibility(key.id)}
                          >
                            {showPrivateKeys[key.id] ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                          </Button>
                        </div>
                      </div>
                      <div className="mt-1 font-mono text-xs bg-secondary p-2 rounded break-all">
                        {showPrivateKeys[key.id] ? key.publicKey : `${key.publicKey.substring(0, 50)}...`}
                      </div>
                    </div>

                    {/* Action Buttons */}
                    <div className="flex items-center flex-wrap gap-2 pt-4 border-t border-border">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleDownloadPublicKey(key.id, key.name)}
                      >
                        <Download className="h-4 w-4 mr-2" />
                        Download Public
                      </Button>
                      
                      {isAdmin() && (
                        <>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleDownloadPrivateKey(key.id, key.name)}
                          >
                            <Download className="h-4 w-4 mr-2" />
                            Download Private
                          </Button>
                          
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => 
                              toggleKeyStatusMutation.mutate({ id: key.id, enabled: !key.enabled })
                            }
                            disabled={toggleKeyStatusMutation.isPending}
                            className={key.enabled ? '' : 'text-green-500 hover:text-green-600'}
                          >
                            {key.enabled ? (
                              <>
                                <XCircle className="h-4 w-4 mr-2" />
                                Disable
                              </>
                            ) : (
                              <>
                                <CheckCircle className="h-4 w-4 mr-2" />
                                Enable
                              </>
                            )}
                          </Button>
                          
                          
                          <Button
                            variant="outline"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => deleteKeyMutation.mutate(key.id)}
                            disabled={deleteKeyMutation.isPending}
                          >
                            <Trash2 className="h-4 w-4 mr-2" />
                            Delete
                          </Button>
                        </>
                      )}
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      ) : (
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Key className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">No SSH Keys</h3>
              <p className="text-muted-foreground mb-6">Generate your first SSH key pair for secure SFTP connections</p>
              {isAdmin() && (
                <Button 
                  className="btn-primary"
                  onClick={() => navigate('/ssh-keys/create')}
                >
                  <Plus className="h-4 w-4 mr-2" />
                  Generate SSH Key
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

export default SshKeyManagement