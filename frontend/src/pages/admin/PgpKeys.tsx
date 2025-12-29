import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Lock, Key, Plus, Download, Upload, Shield, AlertTriangle, Eye, EyeOff, Trash2, CheckCircle, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useNotifications } from '@/stores/ui'
import { pgpKeyApi } from '@/lib/api'
import { cn } from '@/lib/utils'

interface PgpKey {
  id: string
  keyName: string
  description: string
  keyType: 'RSA' | 'ECC' | 'DSA'
  keySize: number
  userId: string
  fingerprint: string
  keyId: string
  publicKey: string
  privateKey?: string
  algorithm: string
  expiresAt?: string
  revokedAt?: string
  revocationReason?: string
  canEncrypt: boolean
  canSign: boolean
  canCertify: boolean
  canAuthenticate: boolean
  importedFrom?: string
  exportedCount: number
  lastUsedAt?: string
  createdAt: string
  updatedAt: string
}

interface GenerateKeyRequest {
  keyName: string
  userId: string
  passphrase: string
  keyType: 'RSA' | 'ECC' | 'DSA'
  keySize: number
  expiresAt?: string
  description?: string
}

interface ImportKeyRequest {
  keyName: string
  armoredKey: string
  description?: string
}

const PgpKeys: React.FC = () => {
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()
  
  const [showGenerateForm, setShowGenerateForm] = useState(false)
  const [showImportForm, setShowImportForm] = useState(false)
  // Removed unused state variables

  // Fetch PGP keys
  const { data: keysResponse, isLoading } = useQuery({
    queryKey: ['pgp-keys'],
    queryFn: pgpKeyApi.getAllKeys,
  })

  const keys = keysResponse?.data || []

  // Generate key mutation
  const generateKeyMutation = useMutation({
    mutationFn: pgpKeyApi.generateKey,
    onSuccess: () => {
      success('PGP Key Generated', 'PGP key pair generated successfully')
      queryClient.invalidateQueries({ queryKey: ['pgp-keys'] })
      setShowGenerateForm(false)
    },
    onError: (err: any) => {
      error('Generation Failed', err.response?.data?.message || 'Failed to generate PGP key')
    },
  })

  // Import key mutation
  const importKeyMutation = useMutation({
    mutationFn: pgpKeyApi.importKey,
    onSuccess: () => {
      success('PGP Key Imported', 'PGP key imported successfully')
      queryClient.invalidateQueries({ queryKey: ['pgp-keys'] })
      setShowImportForm(false)
    },
    onError: (err: any) => {
      error('Import Failed', err.response?.data?.message || 'Failed to import PGP key')
    },
  })

  // Delete key mutation
  const deleteKeyMutation = useMutation({
    mutationFn: pgpKeyApi.deleteKey,
    onSuccess: () => {
      success('PGP Key Deleted', 'PGP key deleted successfully')
      queryClient.invalidateQueries({ queryKey: ['pgp-keys'] })
    },
    onError: (err: any) => {
      error('Deletion Failed', err.response?.data?.message || 'Failed to delete PGP key')
    },
  })

  // Revoke key mutation
  const revokeKeyMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => 
      pgpKeyApi.revokeKey(id, reason),
    onSuccess: () => {
      success('PGP Key Revoked', 'PGP key revoked successfully')
      queryClient.invalidateQueries({ queryKey: ['pgp-keys'] })
    },
    onError: (err: any) => {
      error('Revocation Failed', err.response?.data?.message || 'Failed to revoke PGP key')
    },
  })

  const handleExportPublic = async (keyId: string, keyName: string) => {
    try {
      const response = await pgpKeyApi.exportPublicKey(keyId)
      
      // Create download
      const blob = new Blob([response], { type: 'text/plain' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${keyName}-public.asc`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      
      success('Export Complete', 'Public key exported successfully')
    } catch (err: any) {
      error('Export Failed', 'Failed to export public key')
    }
  }

  const handleExportPrivate = async (keyId: string, keyName: string) => {
    try {
      const response = await pgpKeyApi.exportPrivateKey(keyId)
      
      // Create download
      const blob = new Blob([response], { type: 'text/plain' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${keyName}-private.asc`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      
      success('Export Complete', 'Private key exported successfully')
    } catch (err: any) {
      error('Export Failed', 'Failed to export private key')
    }
  }

  const getKeyStatusIcon = (key: PgpKey) => {
    if (key.revokedAt) {
      return <XCircle className="h-4 w-4 text-red-500" />
    }
    if (key.expiresAt && new Date(key.expiresAt) < new Date()) {
      return <AlertTriangle className="h-4 w-4 text-yellow-500" />
    }
    return <CheckCircle className="h-4 w-4 text-green-500" />
  }

  const getKeyStatusText = (key: PgpKey) => {
    if (key.revokedAt) {
      return 'Revoked'
    }
    if (key.expiresAt && new Date(key.expiresAt) < new Date()) {
      return 'Expired'
    }
    return 'Valid'
  }

  const getKeyTypeIcon = (keyType: string) => {
    switch (keyType) {
      case 'RSA':
        return <Key className="h-4 w-4 text-blue-500" />
      case 'ECC':
        return <Shield className="h-4 w-4 text-green-500" />
      case 'DSA':
        return <Lock className="h-4 w-4 text-purple-500" />
      default:
        return <Key className="h-4 w-4 text-gray-500" />
    }
  }

  const formatFingerprint = (fingerprint: string) => {
    if (!fingerprint || fingerprint.length !== 40) return fingerprint
    return fingerprint.replace(/(.{4})/g, '$1 ').trim()
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <span className="text-muted-foreground">Loading PGP keys...</span>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground mb-2">PGP Key Management</h2>
          <p className="text-muted-foreground">Manage PGP encryption keys for secure file transfers</p>
        </div>
        <div className="flex items-center space-x-2">
          <Button variant="outline" onClick={() => setShowImportForm(true)}>
            <Upload className="h-4 w-4 mr-2" />
            Import Key
          </Button>
          <Button className="btn-primary" onClick={() => setShowGenerateForm(true)}>
            <Plus className="h-4 w-4 mr-2" />
            Generate Key
          </Button>
        </div>
      </div>

      {/* Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card className="app-card border">
          <CardContent className="flex items-center p-4">
            <div className="flex items-center space-x-3">
              <Key className="h-8 w-8 text-primary" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Keys</p>
                <p className="text-2xl font-bold text-foreground">{keys.length}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-4">
            <div className="flex items-center space-x-3">
              <CheckCircle className="h-8 w-8 text-green-500" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Valid Keys</p>
                <p className="text-2xl font-bold text-foreground">
                  {keys.filter((k: PgpKey) => !k.revokedAt && (!k.expiresAt || new Date(k.expiresAt) > new Date())).length}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-4">
            <div className="flex items-center space-x-3">
              <AlertTriangle className="h-8 w-8 text-yellow-500" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Expired</p>
                <p className="text-2xl font-bold text-foreground">
                  {keys.filter((k: PgpKey) => !k.revokedAt && k.expiresAt && new Date(k.expiresAt) < new Date()).length}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-4">
            <div className="flex items-center space-x-3">
              <XCircle className="h-8 w-8 text-red-500" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Revoked</p>
                <p className="text-2xl font-bold text-foreground">
                  {keys.filter((k: PgpKey) => k.revokedAt).length}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Generate Form */}
      {showGenerateForm && (
        <GenerateKeyForm
          onSubmit={(data) => generateKeyMutation.mutate(data)}
          onCancel={() => setShowGenerateForm(false)}
          isLoading={generateKeyMutation.isPending}
        />
      )}

      {/* Import Form */}
      {showImportForm && (
        <ImportKeyForm
          onSubmit={(data) => importKeyMutation.mutate(data)}
          onCancel={() => setShowImportForm(false)}
          isLoading={importKeyMutation.isPending}
        />
      )}

      {/* Keys List */}
      {keys.length > 0 ? (
        <div className="space-y-4">
          {keys.map((key: PgpKey) => (
            <Card key={key.id} className="app-card border">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    {getKeyTypeIcon(key.keyType)}
                    <div>
                      <CardTitle className="text-lg text-foreground">{key.keyName}</CardTitle>
                      <p className="text-sm text-muted-foreground">{key.userId}</p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-2">
                    {getKeyStatusIcon(key)}
                    <span className={cn(
                      "text-sm px-2 py-1 rounded",
                      key.revokedAt && "bg-red-100 text-red-700",
                      !key.revokedAt && key.expiresAt && new Date(key.expiresAt) < new Date() && "bg-yellow-100 text-yellow-700",
                      !key.revokedAt && (!key.expiresAt || new Date(key.expiresAt) > new Date()) && "bg-green-100 text-green-700"
                    )}>
                      {getKeyStatusText(key)}
                    </span>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {/* Key Details */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">Type:</span>
                      <span className="ml-2 text-foreground">{key.keyType} {key.keySize}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Algorithm:</span>
                      <span className="ml-2 text-foreground">{key.algorithm}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Key ID:</span>
                      <span className="ml-2 font-mono text-foreground">{key.keyId}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Exports:</span>
                      <span className="ml-2 text-foreground">{key.exportedCount}</span>
                    </div>
                  </div>

                  {/* Usage Flags */}
                  <div>
                    <span className="text-sm text-muted-foreground">Usage:</span>
                    <div className="flex items-center space-x-2 mt-1">
                      {key.canEncrypt && (
                        <span className="text-xs px-2 py-1 bg-blue-100 text-blue-700 rounded">Encrypt</span>
                      )}
                      {key.canSign && (
                        <span className="text-xs px-2 py-1 bg-green-100 text-green-700 rounded">Sign</span>
                      )}
                      {key.canCertify && (
                        <span className="text-xs px-2 py-1 bg-purple-100 text-purple-700 rounded">Certify</span>
                      )}
                      {key.canAuthenticate && (
                        <span className="text-xs px-2 py-1 bg-orange-100 text-orange-700 rounded">Authenticate</span>
                      )}
                    </div>
                  </div>

                  {/* Fingerprint */}
                  <div>
                    <Label className="text-sm text-muted-foreground">Fingerprint:</Label>
                    <div className="mt-1 font-mono text-sm bg-secondary/50 px-3 py-2 rounded">
                      {formatFingerprint(key.fingerprint)}
                    </div>
                  </div>

                  {/* Description */}
                  {key.description && (
                    <div>
                      <Label className="text-sm text-muted-foreground">Description:</Label>
                      <p className="mt-1 text-sm text-foreground">{key.description}</p>
                    </div>
                  )}

                  {/* Expiry and Dates */}
                  <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">Created:</span>
                      <span className="ml-2 text-foreground">{new Date(key.createdAt).toLocaleDateString()}</span>
                    </div>
                    {key.expiresAt && (
                      <div>
                        <span className="text-muted-foreground">Expires:</span>
                        <span className="ml-2 text-foreground">{new Date(key.expiresAt).toLocaleDateString()}</span>
                      </div>
                    )}
                    {key.lastUsedAt && (
                      <div>
                        <span className="text-muted-foreground">Last Used:</span>
                        <span className="ml-2 text-foreground">{new Date(key.lastUsedAt).toLocaleDateString()}</span>
                      </div>
                    )}
                  </div>

                  {/* Import Source */}
                  {key.importedFrom && (
                    <div className="text-sm">
                      <span className="text-muted-foreground">Imported from:</span>
                      <span className="ml-2 text-foreground">{key.importedFrom}</span>
                    </div>
                  )}

                  {/* Revocation Info */}
                  {key.revokedAt && (
                    <div className="bg-red-50 border border-red-200 rounded-lg p-3">
                      <div className="flex items-center space-x-2 text-red-700 mb-1">
                        <XCircle className="h-4 w-4" />
                        <span className="font-medium">Key Revoked</span>
                      </div>
                      <div className="text-sm text-red-600">
                        <p>Revoked on: {new Date(key.revokedAt).toLocaleString()}</p>
                        {key.revocationReason && <p>Reason: {key.revocationReason}</p>}
                      </div>
                    </div>
                  )}

                  {/* Action Buttons */}
                  <div className="flex items-center space-x-2 pt-4 border-t border-border">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleExportPublic(key.id, key.keyName)}
                    >
                      <Download className="h-4 w-4 mr-2" />
                      Export Public
                    </Button>
                    
                    {key.privateKey && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleExportPrivate(key.id, key.keyName)}
                      >
                        <Download className="h-4 w-4 mr-2" />
                        Export Private
                      </Button>
                    )}

                    {!key.revokedAt && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="text-yellow-600"
                        onClick={() => {
                          const reason = prompt('Enter revocation reason:')
                          if (reason) {
                            revokeKeyMutation.mutate({ id: key.id, reason })
                          }
                        }}
                      >
                        <XCircle className="h-4 w-4 mr-2" />
                        Revoke
                      </Button>
                    )}
                    
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-red-600"
                      onClick={() => {
                        if (window.confirm('Are you sure you want to delete this PGP key?')) {
                          deleteKeyMutation.mutate(key.id)
                        }
                      }}
                    >
                      <Trash2 className="h-4 w-4 mr-2" />
                      Delete
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Lock className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">No PGP Keys Found</h3>
              <p className="text-muted-foreground mb-6">Generate or import your first PGP key to get started</p>
              <div className="flex items-center justify-center space-x-2">
                <Button variant="outline" onClick={() => setShowImportForm(true)}>
                  <Upload className="h-4 w-4 mr-2" />
                  Import Key
                </Button>
                <Button className="btn-primary" onClick={() => setShowGenerateForm(true)}>
                  <Plus className="h-4 w-4 mr-2" />
                  Generate Key
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

const GenerateKeyForm: React.FC<{
  onSubmit: (data: GenerateKeyRequest) => void
  onCancel: () => void
  isLoading: boolean
}> = ({ onSubmit, onCancel, isLoading }) => {
  const [formData, setFormData] = useState<GenerateKeyRequest>({
    keyName: '',
    userId: '',
    passphrase: '',
    keyType: 'RSA',
    keySize: 2048,
    expiresAt: '',
    description: '',
  })

  const [showPassphrase, setShowPassphrase] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit(formData)
  }

  return (
    <Card className="app-card border">
      <CardHeader>
        <CardTitle className="text-foreground flex items-center space-x-2">
          <Key className="h-5 w-5" />
          <span>Generate PGP Key Pair</span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="keyName">Key Name</Label>
              <Input
                id="keyName"
                value={formData.keyName}
                onChange={(e) => setFormData(prev => ({ ...prev, keyName: e.target.value }))}
                placeholder="e.g., Production Encryption Key"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="userId">User ID (Email)</Label>
              <Input
                id="userId"
                type="email"
                value={formData.userId}
                onChange={(e) => setFormData(prev => ({ ...prev, userId: e.target.value }))}
                placeholder="e.g., admin@company.com"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="keyType">Key Type</Label>
              <Select
                value={formData.keyType}
                onValueChange={(value) => setFormData(prev => ({ ...prev, keyType: value as any }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="RSA">RSA (Recommended)</SelectItem>
                  <SelectItem value="ECC" disabled>ECC (Coming Soon)</SelectItem>
                  <SelectItem value="DSA" disabled>DSA (Coming Soon)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="keySize">Key Size (bits)</Label>
              <Select
                value={formData.keySize.toString()}
                onValueChange={(value) => setFormData(prev => ({ ...prev, keySize: parseInt(value) }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="2048">2048 (Standard)</SelectItem>
                  <SelectItem value="3072">3072 (High Security)</SelectItem>
                  <SelectItem value="4096">4096 (Maximum Security)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="passphrase">Passphrase</Label>
              <div className="relative">
                <Input
                  id="passphrase"
                  type={showPassphrase ? 'text' : 'password'}
                  value={formData.passphrase}
                  onChange={(e) => setFormData(prev => ({ ...prev, passphrase: e.target.value }))}
                  placeholder="Enter secure passphrase"
                  className="pr-10"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassphrase(!showPassphrase)}
                  className="absolute right-3 top-3 text-muted-foreground"
                >
                  {showPassphrase ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="expiresAt">Expiry Date (Optional)</Label>
              <Input
                id="expiresAt"
                type="datetime-local"
                value={formData.expiresAt}
                onChange={(e) => setFormData(prev => ({ ...prev, expiresAt: e.target.value }))}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="description">Description (Optional)</Label>
            <Input
              id="description"
              value={formData.description}
              onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
              placeholder="e.g., Used for encrypting payment files"
            />
          </div>

          <div className="flex items-center space-x-4 pt-4">
            <Button type="submit" className="btn-primary" disabled={isLoading}>
              {isLoading ? 'Generating...' : 'Generate Key Pair'}
            </Button>
            <Button type="button" variant="outline" onClick={onCancel}>
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

const ImportKeyForm: React.FC<{
  onSubmit: (data: ImportKeyRequest) => void
  onCancel: () => void
  isLoading: boolean
}> = ({ onSubmit, onCancel, isLoading }) => {
  const [formData, setFormData] = useState<ImportKeyRequest>({
    keyName: '',
    armoredKey: '',
    description: '',
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit(formData)
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      const reader = new FileReader()
      reader.onload = (event) => {
        const content = event.target?.result as string
        setFormData(prev => ({ ...prev, armoredKey: content }))
      }
      reader.readAsText(file)
    }
  }

  return (
    <Card className="app-card border">
      <CardHeader>
        <CardTitle className="text-foreground flex items-center space-x-2">
          <Upload className="h-5 w-5" />
          <span>Import PGP Key</span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="importKeyName">Key Name</Label>
              <Input
                id="importKeyName"
                value={formData.keyName}
                onChange={(e) => setFormData(prev => ({ ...prev, keyName: e.target.value }))}
                placeholder="e.g., Customer Public Key"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="keyFile">Import from File (Optional)</Label>
              <Input
                id="keyFile"
                type="file"
                accept=".asc,.gpg,.pgp"
                onChange={handleFileUpload}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="armoredKey">ASCII Armored Key</Label>
            <textarea
              id="armoredKey"
              value={formData.armoredKey}
              onChange={(e) => setFormData(prev => ({ ...prev, armoredKey: e.target.value }))}
              placeholder="Paste ASCII armored PGP key here..."
              className="w-full h-32 px-3 py-2 border border-border rounded-md bg-background text-foreground font-mono text-sm"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="importDescription">Description (Optional)</Label>
            <Input
              id="importDescription"
              value={formData.description}
              onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
              placeholder="e.g., Customer XYZ public key for file encryption"
            />
          </div>

          <div className="flex items-center space-x-4 pt-4">
            <Button type="submit" className="btn-primary" disabled={isLoading}>
              {isLoading ? 'Importing...' : 'Import Key'}
            </Button>
            <Button type="button" variant="outline" onClick={onCancel}>
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

export default PgpKeys