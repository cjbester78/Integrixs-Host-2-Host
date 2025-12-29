import React, { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Lock, User as UserIcon } from 'lucide-react'
import { authApi } from '@/lib/api'
import { useAuthStore, User } from '@/stores/auth'
import { useNotifications } from '@/stores/ui'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

interface LoginForm {
  username: string
  password: string
  rememberMe: boolean
}

const LoginPage: React.FC = () => {
  const navigate = useNavigate()
  const [showPassword, setShowPassword] = React.useState(false)
  const { setAuth, isAuthenticated, hasInitialized } = useAuthStore()
  const { error } = useNotifications()
  
  const { register, handleSubmit, formState: { errors } } = useForm<LoginForm>({
    defaultValues: {
      username: '',
      password: '',
      rememberMe: false
    },
  })

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (response, variables) => {
      if (response.accessToken && response.username) {
        // Decode JWT token to get actual user ID
        const payload = JSON.parse(atob(response.accessToken.split('.')[1]))
        
        // Create user object from the response
        const user: User = {
          id: payload.sub, // Get actual user ID from JWT token
          username: response.username,
          firstName: response.fullName?.split(' ')[0] || response.username,
          lastName: response.fullName?.split(' ').slice(1).join(' ') || '',
          email: payload.email || `${response.username}@integrixs.com`, // Use email from token or default
          role: response.role as 'ADMINISTRATOR' | 'VIEWER',
          enabled: payload.enabled || true,
          createdAt: new Date().toISOString(),
          lastLogin: new Date().toISOString()
        }
        // Set auth with remember me flag from form
        setAuth(user, response.accessToken, variables.rememberMe)
      } else {
        error('Login Failed', 'Invalid response from server')
      }
    },
    onError: (err: any) => {
      const message = err.response?.data?.message || 'Login failed. Please check your credentials.'
      error('Login Failed', message)
    },
  })

  const onSubmit = (data: LoginForm) => {
    loginMutation.mutate({
      username: data.username,
      password: data.password,
      rememberMe: data.rememberMe
    })
  }

  // Redirect if already authenticated (after initialization)
  useEffect(() => {
    if (hasInitialized && isAuthenticated) {
      navigate('/dashboard', { replace: true })
    }
  }, [hasInitialized, isAuthenticated, navigate])

  // Show loading while checking auth status
  if (!hasInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    )
  }

  // Don't render login form if already authenticated
  if (isAuthenticated) {
    return null
  }

  return (
    <div className="min-h-screen app-background flex items-center justify-center p-4">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-foreground mb-2 app-name-gradient">
            Integrixs Host 2 Host
          </h1>
          <p className="text-muted-foreground">
            Secure Host-to-Host File Transfer System
          </p>
        </div>

        <Card className="app-card shadow-xl border">
          <CardHeader className="space-y-1">
            <CardTitle className="text-2xl text-center text-foreground">Welcome back</CardTitle>
            <CardDescription className="text-center text-muted-foreground">
              Enter your credentials to access the system
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
              <div className="space-y-2">
                <Label htmlFor="username">Username</Label>
                <div className="relative">
                  <UserIcon className="absolute left-3 top-1/2 transform -translate-y-1/2 text-muted-foreground h-4 w-4" />
                  <Input
                    id="username"
                    type="text"
                    placeholder="Enter your username"
                    className={cn(
                      "pl-10 bg-input border-border text-foreground",
                      errors.username && "border-destructive focus-visible:ring-destructive"
                    )}
                    {...register('username', { 
                      required: 'Username is required',
                      minLength: {
                        value: 3,
                        message: 'Username must be at least 3 characters'
                      }
                    })}
                  />
                  {errors.username && (
                    <p className="text-sm text-destructive mt-1">{errors.username.message}</p>
                  )}
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-muted-foreground h-4 w-4" />
                  <Input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Enter your password"
                    className={cn(
                      "pl-10 pr-10 bg-input border-border text-foreground",
                      errors.password && "border-destructive focus-visible:ring-destructive"
                    )}
                    {...register('password', { 
                      required: 'Password is required',
                      minLength: {
                        value: 6,
                        message: 'Password must be at least 6 characters'
                      }
                    })}
                  />
                  <button
                    type="button"
                    className="absolute right-3 top-1/2 transform -translate-y-1/2 text-muted-foreground"
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                  {errors.password && (
                    <p className="text-sm text-destructive mt-1">{errors.password.message}</p>
                  )}
                </div>
              </div>

              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="rememberMe"
                  className="rounded border-border text-primary focus:ring-primary focus:ring-offset-0"
                  {...register('rememberMe')}
                />
                <Label 
                  htmlFor="rememberMe" 
                  className="text-sm text-muted-foreground cursor-pointer"
                >
                  Keep me signed in
                </Label>
              </div>

              <Button
                type="submit"
                className="w-full btn-primary"
                disabled={loginMutation.isPending}
              >
                {loginMutation.isPending ? (
                  <div className="flex items-center gap-2">
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    Signing in...
                  </div>
                ) : (
                  'Sign in'
                )}
              </Button>

              {loginMutation.isError && (
                <div className="text-center text-sm text-destructive bg-destructive/10 border border-destructive/20 p-3 rounded-md">
                  {loginMutation.error?.response?.data?.message || 'Login failed. Please try again.'}
                </div>
              )}
            </form>
          </CardContent>
        </Card>

        <div className="text-center text-sm text-muted-foreground">
          © 2024 Integrix Host 2 Host System
        </div>
      </div>
    </div>
  )
}

export default LoginPage