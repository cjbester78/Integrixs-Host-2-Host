import React from 'react'
import { useNavigate } from 'react-router-dom'
import { 
  Bell, 
  Settings, 
  User, 
  LogOut, 
  Menu,
  Building2
} from 'lucide-react'
import { useAuthStore } from '@/stores/auth'
import { useNotifications } from '@/stores/ui'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/lib/api'
import { sessionManager } from '@/lib/session'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

const Header: React.FC = () => {
  const navigate = useNavigate()
  const { user, clearAuth } = useAuthStore()
  const { success, error } = useNotifications()

  const logoutMutation = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      sessionManager.clearTokens()
      clearAuth()
      success('Logged out', 'Successfully logged out')
      navigate('/login', { replace: true })
    },
    onError: () => {
      // Still clear auth even if logout API fails
      sessionManager.clearTokens()
      clearAuth()
      error('Logout Error', 'There was an issue logging out, but you have been signed out locally')
      navigate('/login', { replace: true })
    },
  })

  const handleLogout = () => {
    logoutMutation.mutate()
  }

  return (
    <header className="h-16 border-b border-border bg-card/80 backdrop-blur-md transition-all duration-300">
      <div className="flex h-full items-center justify-between px-6">
        <div className="flex items-center space-x-4">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            aria-label="Toggle sidebar"
          >
            <Menu className="h-5 w-5" />
          </Button>
          <div className="flex items-center space-x-3">
            <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center">
              <Building2 className="h-4 w-4 text-primary-foreground" />
            </div>
            <h1 className="text-xl font-semibold app-name-gradient">
              Integrixs Host 2 Host
            </h1>
          </div>
        </div>

        <div className="flex items-center space-x-4">
          <Button
            variant="ghost"
            size="sm"
            className="relative transition-all duration-300 hover:scale-110 hover:bg-accent/50"
          >
            <Bell className="h-4 w-4" />
            <Badge
              variant="destructive"
              className="absolute -top-1 -right-1 h-5 w-5 p-0 text-xs animate-pulse"
            >
              0
            </Badge>
          </Button>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="sm" className="flex items-center space-x-2">
                <User className="h-4 w-4" />
                <span className="text-sm">
                  {user?.firstName} {user?.lastName}
                </span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuItem>
                <User className="mr-2 h-4 w-4" />
                Profile
              </DropdownMenuItem>
              <DropdownMenuItem>
                <Settings className="mr-2 h-4 w-4" />
                Settings
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem 
                onClick={handleLogout}
                disabled={logoutMutation.isPending}
                className="text-destructive"
              >
                <LogOut className="mr-2 h-4 w-4" />
                {logoutMutation.isPending ? 'Logging out...' : 'Logout'}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  )
}

export default Header