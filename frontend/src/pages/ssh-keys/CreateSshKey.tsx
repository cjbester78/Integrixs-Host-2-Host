import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Key, ChevronRight, Home, Shield, Clock, FileKey, CheckCircle } from 'lucide-react'
import { sshKeyApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { useNotifications } from '@/stores/ui'

interface CreateKeyRequest {
  name: string
  description: string
  keyType: 'RSA' | 'DSA'
  keySize: number
}

const CreateSshKey: React.FC = () => {
  const navigate = useNavigate()
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()

  const [formData, setFormData] = useState<CreateKeyRequest>({
    name: '',
    description: '',
    keyType: 'RSA',
    keySize: 2048,
  })

  const createKeyMutation = useMutation({
    mutationFn: async (keyData: CreateKeyRequest) => {
      console.log('Generating SSH key with data:', keyData)
      const result = keyData.keyType === 'RSA' 
        ? await sshKeyApi.generateRSAKey(keyData)
        : await sshKeyApi.generateDSAKey(keyData)
      console.log('SSH key generation result:', result)
      return result
    },
    onSuccess: (data) => {
      console.log('SSH key generated successfully:', data)
      success('SSH Key Generated', 'SSH key pair created successfully')
      queryClient.invalidateQueries({ queryKey: ['ssh-keys'] })
      navigate('/ssh-keys')
    },
    onError: (err: any) => {
      console.error('SSH key generation failed:', err)
      console.error('Error response:', err.response)
      error('Generation Failed', err.response?.data?.message || err.message || 'Failed to generate SSH key')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    createKeyMutation.mutate(formData)
  }

  const getSecurityLevel = () => {
    if (formData.keyType === 'RSA') {
      if (formData.keySize >= 4096) return { level: 'High', color: 'text-green-500' }
      if (formData.keySize >= 3072) return { level: 'Strong', color: 'text-green-400' }
      return { level: 'Standard', color: 'text-blue-400' }
    }
    return { level: formData.keySize >= 2048 ? 'Standard' : 'Basic', color: 'text-yellow-400' }
  }

  const isFormValid = formData.name.trim() !== '' && formData.description.trim() !== ''

  return (
    <div className="content-spacing">
      {/* Breadcrumb Navigation */}
      <nav className="flex items-center space-x-2 text-sm mb-6">
        <Link 
          to="/dashboard" 
          className="text-muted-foreground hover:text-foreground transition-colors flex items-center"
        >
          <Home className="h-4 w-4" />
        </Link>
        <ChevronRight className="h-4 w-4 text-muted-foreground" />
        <Link 
          to="/ssh-keys" 
          className="text-muted-foreground hover:text-foreground transition-colors"
        >
          SSH Keys
        </Link>
        <ChevronRight className="h-4 w-4 text-muted-foreground" />
        <span className="text-foreground font-medium">Generate New Key</span>
      </nav>

      <div className="mb-6">
        <h1 className="text-3xl font-bold text-foreground mb-2">Generate SSH Key Pair</h1>
        <p className="text-muted-foreground">Create a new SSH key pair for secure SFTP connections</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Form Section */}
        <div className="lg:col-span-2">
          <Card className="app-card border">
            <CardHeader>
              <CardTitle className="text-foreground flex items-center space-x-2">
                <Key className="h-5 w-5" />
                <span>Key Configuration</span>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-6">
                <div className="space-y-2">
                  <Label htmlFor="name">Key Name</Label>
                  <Input
                    id="name"
                    value={formData.name}
                    onChange={(e) => setFormData(prev => ({ ...prev, name: e.target.value }))}
                    placeholder="e.g., production-sftp-key"
                    className="bg-input border-border text-foreground"
                    required
                  />
                  <p className="text-xs text-muted-foreground">A unique name to identify this key</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="description">Description</Label>
                  <Input
                    id="description"
                    value={formData.description}
                    onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                    placeholder="e.g., Key for production SFTP server"
                    className="bg-input border-border text-foreground"
                    required
                  />
                  <p className="text-xs text-muted-foreground">A brief description of what this key is used for</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div className="space-y-2">
                    <Label htmlFor="keyType">Key Type</Label>
                    <select
                      id="keyType"
                      value={formData.keyType}
                      onChange={(e) => setFormData(prev => ({ 
                        ...prev, 
                        keyType: e.target.value as 'RSA' | 'DSA',
                        keySize: e.target.value === 'RSA' ? 2048 : 1024
                      }))}
                      className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                    >
                      <option value="RSA">RSA (Recommended)</option>
                      <option value="DSA">DSA</option>
                    </select>
                    <p className="text-xs text-muted-foreground">RSA is recommended for better compatibility</p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="keySize">Key Size</Label>
                    <select
                      id="keySize"
                      value={formData.keySize}
                      onChange={(e) => setFormData(prev => ({ ...prev, keySize: parseInt(e.target.value) }))}
                      className="w-full px-3 py-2 bg-input border border-border rounded-md text-foreground"
                    >
                      {formData.keyType === 'RSA' ? (
                        <>
                          <option value={2048}>2048 bits (Recommended)</option>
                          <option value={3072}>3072 bits</option>
                          <option value={4096}>4096 bits (High Security)</option>
                        </>
                      ) : (
                        <>
                          <option value={1024}>1024 bits (Standard)</option>
                          <option value={2048}>2048 bits</option>
                        </>
                      )}
                    </select>
                    <p className="text-xs text-muted-foreground">Larger keys are more secure but slower</p>
                  </div>
                </div>

                <div className="bg-secondary/50 rounded-lg p-4 border border-border">
                  <h4 className="font-medium text-foreground mb-2">Security Notice</h4>
                  <p className="text-sm text-muted-foreground">
                    The private key will be stored securely on the server. You can download it after generation 
                    for backup purposes. Keep your private key secure and never share it.
                  </p>
                </div>

                <div className="flex items-center space-x-4 pt-4 border-t border-border">
                  <Button 
                    type="submit" 
                    className="btn-primary" 
                    disabled={createKeyMutation.isPending || !isFormValid}
                  >
                    {createKeyMutation.isPending ? 'Generating...' : 'Generate Key Pair'}
                  </Button>
                  <Button 
                    type="button" 
                    variant="outline" 
                    onClick={() => navigate('/ssh-keys')}
                  >
                    Cancel
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>

        {/* Preview Section */}
        <div className="lg:col-span-1">
          <Card className="app-card border sticky top-6">
            <CardHeader>
              <CardTitle className="text-foreground flex items-center space-x-2">
                <FileKey className="h-5 w-5" />
                <span>Key Preview</span>
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Key Name */}
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground uppercase tracking-wider">Key Name</p>
                <p className="text-foreground font-medium">
                  {formData.name || <span className="text-muted-foreground italic">Not specified</span>}
                </p>
              </div>

              {/* Description */}
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground uppercase tracking-wider">Description</p>
                <p className="text-foreground text-sm">
                  {formData.description || <span className="text-muted-foreground italic">Not specified</span>}
                </p>
              </div>

              <div className="border-t border-border pt-4 space-y-3">
                {/* Key Type */}
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground text-sm">Algorithm</span>
                  <span className="text-foreground font-medium">{formData.keyType}</span>
                </div>

                {/* Key Size */}
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground text-sm">Key Size</span>
                  <span className="text-foreground font-medium">{formData.keySize} bits</span>
                </div>

                {/* Security Level */}
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground text-sm">Security</span>
                  <span className={`font-medium ${getSecurityLevel().color}`}>
                    {getSecurityLevel().level}
                  </span>
                </div>
              </div>

              {/* What will be generated */}
              <div className="border-t border-border pt-4">
                <p className="text-xs text-muted-foreground uppercase tracking-wider mb-3">Will Generate</p>
                <div className="space-y-2">
                  <div className="flex items-center space-x-2 text-sm">
                    <CheckCircle className="h-4 w-4 text-green-500" />
                    <span className="text-foreground">Public Key (.pub)</span>
                  </div>
                  <div className="flex items-center space-x-2 text-sm">
                    <CheckCircle className="h-4 w-4 text-green-500" />
                    <span className="text-foreground">Private Key</span>
                  </div>
                  <div className="flex items-center space-x-2 text-sm">
                    <CheckCircle className="h-4 w-4 text-green-500" />
                    <span className="text-foreground">Fingerprint</span>
                  </div>
                </div>
              </div>

              {/* Estimated time */}
              <div className="bg-secondary/50 rounded-lg p-3 border border-border">
                <div className="flex items-center space-x-2">
                  <Clock className="h-4 w-4 text-muted-foreground" />
                  <span className="text-sm text-muted-foreground">
                    Est. generation time: {formData.keySize >= 4096 ? '5-10' : formData.keySize >= 3072 ? '3-5' : '1-3'} seconds
                  </span>
                </div>
              </div>

              {/* Ready indicator */}
              {isFormValid && (
                <div className="bg-green-500/10 rounded-lg p-3 border border-green-500/20">
                  <div className="flex items-center space-x-2">
                    <Shield className="h-4 w-4 text-green-500" />
                    <span className="text-sm text-green-500 font-medium">Ready to generate</span>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

export default CreateSshKey
