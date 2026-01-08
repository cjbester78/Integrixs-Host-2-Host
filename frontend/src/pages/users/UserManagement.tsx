import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Users, Shield, Edit, Trash2, Eye, EyeOff, CheckCircle, XCircle, Crown } from 'lucide-react'
import { userApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useNotifications } from '@/stores/ui'
import { usePermissions } from '@/hooks/auth'
import { useAuthStore } from '@/stores/auth'

interface User {
  id: string
  username: string
  email: string
  fullName: string
  role: 'ADMINISTRATOR' | 'VIEWER'
  enabled: boolean
  accountNonExpired: boolean
  accountNonLocked: boolean
  credentialsNonExpired: boolean
  timezone: string
  failedLoginAttempts: number
  createdAt: string
  updatedAt: string
  lastLogin?: string
  displayName: string
  roleDisplayName: string
  administrator: boolean
  integrator: boolean
  viewer: boolean
  authorities: Array<{ authority: string }>
}

interface CreateUserRequest {
  username: string
  email: string
  password: string
  role: 'ADMINISTRATOR' | 'VIEWER'
  enabled: boolean
}

const UserManagement: React.FC = () => {
  const { isAdmin } = usePermissions()
  const { success, error } = useNotifications()
  const queryClient = useQueryClient()
  const { user: currentUser } = useAuthStore()
  
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [formKey, setFormKey] = useState(0)
  const [_showPasswords, _setShowPasswords] = useState<Record<string, boolean>>({})
  const [_editingUser, setEditingUser] = useState<string | null>(null)

  // Fetch users
  const { data: usersResponse, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: userApi.getAllUsers,
  })

  // Extract the users array from the API response and filter out system users
  const users = (usersResponse || []).filter((user: any) => user.role !== 'INTEGRATOR')

  // Mutations
  const createUserMutation = useMutation({
    mutationFn: userApi.createUser,
    onSuccess: () => {
      success('User Created', 'User account created successfully')
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setShowCreateForm(false)
    },
    onError: (err: any) => {
      error('Creation Failed', err.response?.data?.message || 'Failed to create user')
    },
  })

  const updateUserMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: any }) => userApi.updateUser(id, data),
    onSuccess: () => {
      success('User Updated', 'User account updated successfully')
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setEditingUser(null)
    },
    onError: (err: any) => {
      error('Update Failed', err.response?.data?.message || 'Failed to update user')
    },
  })

  const deleteUserMutation = useMutation({
    mutationFn: userApi.deleteUser,
    onSuccess: () => {
      success('User Deleted', 'User account deleted successfully')
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (err: any) => {
      error('Deletion Failed', err.response?.data?.message || 'Failed to delete user')
    },
  })


  const getRoleIcon = (role: string) => {
    return role === 'ADMINISTRATOR' ? <Crown className="h-4 w-4 text-warning" /> : <Shield className="h-4 w-4 text-info" />
  }

  const getRoleBadgeStyle = (role: string) => {
    return role === 'ADMINISTRATOR' 
      ? 'bg-warning/20 text-warning' 
      : 'bg-info/20 text-info'
  }

  const canModifyUser = () => {
    // Only admins can modify users
    return isAdmin
  }

  if (!isAdmin) {
    return (
      <div className="content-spacing">
        <Card className="app-card border">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <Shield className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">Access Denied</h3>
              <p className="text-muted-foreground">Administrator privileges required to manage users</p>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="content-spacing">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading users...</span>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="content-spacing">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-foreground mb-2">User Management</h1>
          <p className="text-muted-foreground">Manage system users, roles, and permissions</p>
        </div>
        <Button 
          className="btn-primary"
          onClick={() => {
            setFormKey(prev => prev + 1)
            setShowCreateForm(true)
          }}
        >
          <Plus className="h-4 w-4 mr-2" />
          Create User
        </Button>
      </div>

      {/* User Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Users className="h-8 w-8 text-primary" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Users</p>
                <p className="text-2xl font-bold text-foreground">{users.length || 0}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <Crown className="h-8 w-8 text-warning" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Administrators</p>
                <p className="text-2xl font-bold text-foreground">
                  {users.filter((u: User) => u.role === 'ADMINISTRATOR').length || 0}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="app-card border">
          <CardContent className="flex items-center p-6">
            <div className="flex items-center space-x-3">
              <CheckCircle className="h-8 w-8 text-success" />
              <div>
                <p className="text-sm font-medium text-muted-foreground">Active Users</p>
                <p className="text-2xl font-bold text-foreground">
                  {users.filter((u: User) => u.enabled).length || 0}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Create User Form */}
      {showCreateForm && (
        <Card className="app-card border mb-6">
          <CardHeader>
            <CardTitle className="text-foreground flex items-center space-x-2">
              <Users className="h-5 w-5" />
              <span>Create New User</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <CreateUserForm
              key={formKey}
              onSubmit={(data) => createUserMutation.mutate(data)}
              onCancel={() => setShowCreateForm(false)}
              isLoading={createUserMutation.isPending}
            />
          </CardContent>
        </Card>
      )}

      {/* Users List */}
      {users && users.length > 0 ? (
        <div className="grid grid-cols-1 gap-6">
          {users.map((user: User) => (
            <Card key={user.id} className="app-card border">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <div className="relative">
                      {getRoleIcon(user.role)}
                      {currentUser?.id === user.id && (
                        <div className="absolute -top-1 -right-1 w-3 h-3 bg-primary rounded-full border-2 border-background" />
                      )}
                    </div>
                    <div>
                      <div className="flex items-center space-x-2">
                        <CardTitle className="text-lg text-foreground">{user.username}</CardTitle>
                        {currentUser?.id === user.id && (
                          <span className="text-xs px-2 py-1 rounded bg-primary/20 text-primary">You</span>
                        )}
                      </div>
                      <p className="text-sm text-muted-foreground">{user.email}</p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className={`text-xs px-2 py-1 rounded ${getRoleBadgeStyle(user.role)}`}>
                      {user.role}
                    </span>
                    <div className={`w-3 h-3 rounded-full ${
                      user.enabled ? 'bg-success animate-glow' : 'bg-muted-foreground'
                    }`} />
                    <span className={`text-xs px-2 py-1 rounded ${
                      user.enabled ? 'bg-success/20 text-success' : 'bg-muted/20 text-muted-foreground'
                    }`}>
                      {user.enabled ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {/* User Details */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">Created:</span>
                      <span className="ml-2 text-foreground">{new Date(user.createdAt).toLocaleDateString()}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Last Login:</span>
                      <span className="ml-2 text-foreground">
                        {user.lastLogin ? new Date(user.lastLogin).toLocaleDateString() : 'Never'}
                      </span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Failed Attempts:</span>
                      <span className="ml-2 text-foreground">{user.failedLoginAttempts}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Status:</span>
                      <span className={`ml-2 ${user.enabled ? 'text-success' : 'text-muted-foreground'}`}>
                        {user.enabled ? 'Active' : 'Inactive'}
                      </span>
                    </div>
                  </div>

                  {/* User ID */}
                  <div>
                    <label className="text-sm font-medium text-foreground">User ID:</label>
                    <div className="mt-1 font-mono text-xs bg-secondary p-2 rounded">
                      {user.id}
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex items-center space-x-2 pt-4 border-t border-border">
                    {canModifyUser() && (
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setEditingUser(user.id)}
                        >
                          <Edit className="h-4 w-4 mr-2" />
                          Edit User
                        </Button>
                        
                        {currentUser?.id !== user.id && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => 
                              updateUserMutation.mutate({ 
                                id: user.id, 
                                data: { ...user, enabled: !user.enabled }
                              })
                            }
                            disabled={updateUserMutation.isPending}
                          >
                            {user.enabled ? (
                              <>
                                <XCircle className="h-4 w-4 mr-2" />
                                Deactivate
                              </>
                            ) : (
                              <>
                                <CheckCircle className="h-4 w-4 mr-2" />
                                Activate
                              </>
                            )}
                          </Button>
                        )}
                        
                        {currentUser?.id !== user.id && (
                          <Button
                            variant="outline"
                            size="sm"
                            className="text-destructive"
                            onClick={() => deleteUserMutation.mutate(user.id)}
                            disabled={deleteUserMutation.isPending}
                          >
                            <Trash2 className="h-4 w-4 mr-2" />
                            Delete
                          </Button>
                        )}
                        
                        {currentUser?.id === user.id && (
                          <div className="text-sm text-muted-foreground">
                            You cannot deactivate or delete your own account
                          </div>
                        )}
                      </>
                    )}
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
              <Users className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-foreground mb-2">No Users Found</h3>
              <p className="text-muted-foreground mb-6">Create your first user account to get started</p>
              <Button 
                className="btn-primary"
                onClick={() => {
                  setFormKey(prev => prev + 1)
                  setShowCreateForm(true)
                }}
              >
                <Plus className="h-4 w-4 mr-2" />
                Create First User
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

const CreateUserForm: React.FC<{
  onSubmit: (data: CreateUserRequest) => void
  onCancel: () => void
  isLoading: boolean
}> = ({ onSubmit, onCancel, isLoading }) => {
  const [formData, setFormData] = useState<CreateUserRequest>({
    username: '',
    email: '',
    password: '',
    role: 'VIEWER',
    enabled: true,
  })

  const [showPassword, setShowPassword] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit(formData)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" autoComplete="off">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="username">Username</Label>
          <Input
            id="username"
            value={formData.username}
            onChange={(e) => setFormData(prev => ({ ...prev, username: e.target.value }))}
            placeholder="e.g., john.doe"
            className="bg-input border-border text-foreground"
            autoComplete="off"
            required
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            value={formData.email}
            onChange={(e) => setFormData(prev => ({ ...prev, email: e.target.value }))}
            placeholder="e.g., john.doe@company.com"
            className="bg-input border-border text-foreground"
            autoComplete="off"
            required
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <div className="relative">
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              value={formData.password}
              onChange={(e) => setFormData(prev => ({ ...prev, password: e.target.value }))}
              placeholder="Enter password"
              className="bg-input border-border text-foreground pr-10"
              autoComplete="new-password"
              required
              minLength={8}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-3 text-muted-foreground"
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="role">Role</Label>
          <Select
            value={formData.role}
            onValueChange={(value) => setFormData(prev => ({ ...prev, role: value as 'ADMINISTRATOR' | 'VIEWER' }))}
          >
            <SelectTrigger className="w-full">
              <SelectValue placeholder="Select role" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="VIEWER">Viewer (Read-only access)</SelectItem>
              <SelectItem value="ADMINISTRATOR">Administrator (Full access)</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="flex items-center space-x-2">
        <input
          type="checkbox"
          id="enabled"
          checked={formData.enabled}
          onChange={(e) => setFormData(prev => ({ ...prev, enabled: e.target.checked }))}
          className="w-4 h-4 text-primary bg-background border-border rounded focus:ring-primary"
        />
        <Label htmlFor="enabled">Enable user account</Label>
      </div>

      <div className="flex items-center space-x-4 pt-4">
        <Button type="submit" className="btn-primary" disabled={isLoading}>
          {isLoading ? 'Creating...' : 'Create User'}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

export default UserManagement