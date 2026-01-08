import React, { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { 
  LayoutDashboard,
  Settings,
  Activity,
  ChevronLeft,
  ChevronRight,
  Boxes,
  Workflow,
  BarChart3,
  Database
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'

const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: LayoutDashboard, section: 'main' },
  { name: 'Adapters', href: '/adapters', icon: Boxes, adminOnly: true, section: 'integration' },
  { name: 'Flow Management', href: '/flows', icon: Workflow, adminOnly: true, section: 'integration' },
  { name: 'Adapter Monitoring', href: '/adapter-monitoring', icon: Activity, section: 'monitoring' },
  { name: 'Flow Monitoring', href: '/flow-monitoring', icon: BarChart3, section: 'monitoring' },
  { name: 'Administration', href: '/admin', icon: Database, adminOnly: true, section: 'system' },
  { name: 'Settings', href: '/settings', icon: Settings, section: 'system' },
]

const Sidebar: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false)
  const { user } = useAuthStore()

  const isAdmin = user?.role === 'ADMINISTRATOR'

  const filteredNavigation = navigation.filter(item => 
    !item.adminOnly || isAdmin
  )

  return (
    <div className={cn(
      "h-full min-h-full bg-card/80 backdrop-blur-md border-r border-border transition-all duration-300 ease-in-out",
      collapsed ? "w-16" : "w-64"
    )}>
      <div className="flex flex-col h-full min-h-full">
        <div className="p-4 border-b border-border">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setCollapsed(!collapsed)}
            className="ml-auto flex transition-all duration-300 hover:scale-110 hover:bg-accent/50"
          >
            {collapsed ? (
              <ChevronRight className="h-4 w-4 transition-transform duration-300" />
            ) : (
              <ChevronLeft className="h-4 w-4 transition-transform duration-300" />
            )}
          </Button>
        </div>

        <nav className="flex-1 p-4 space-y-1">
          {!collapsed && (
            <div className="px-3 py-2 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Main
            </div>
          )}
          {filteredNavigation
            .filter(item => item.section === 'main')
            .map((item) => (
            <NavLink
              key={item.name}
              to={item.href}
              className={({ isActive }) =>
                cn(
                  "flex items-center px-3 py-2 rounded-lg text-sm font-medium transition-all duration-300 group",
                  "hover:bg-primary hover:text-primary-foreground hover:scale-[1.02] hover:shadow-soft",
                  isActive
                    ? "bg-primary text-primary-foreground shadow-elegant"
                    : "text-muted-foreground"
                )
              }
            >
              <item.icon className={cn(
                "h-4 w-4 transition-all duration-300 group-hover:scale-110",
                !collapsed && "mr-3"
              )} />
              {!collapsed && (
                <span className="transition-all duration-300">{item.name}</span>
              )}
            </NavLink>
          ))}

          {!collapsed && filteredNavigation.some(item => item.section === 'integration') && (
            <div className="px-3 py-2 mt-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Integration
            </div>
          )}
          {filteredNavigation
            .filter(item => item.section === 'integration')
            .map((item) => (
            <NavLink
              key={item.name}
              to={item.href}
              className={({ isActive }) =>
                cn(
                  "flex items-center px-3 py-2 rounded-lg text-sm font-medium transition-all duration-300 group",
                  "hover:bg-primary hover:text-primary-foreground hover:scale-[1.02] hover:shadow-soft",
                  isActive
                    ? "bg-primary text-primary-foreground shadow-elegant"
                    : "text-muted-foreground"
                )
              }
            >
              <item.icon className={cn(
                "h-4 w-4 transition-all duration-300 group-hover:scale-110",
                !collapsed && "mr-3"
              )} />
              {!collapsed && (
                <span className="transition-all duration-300">{item.name}</span>
              )}
            </NavLink>
          ))}

          {!collapsed && filteredNavigation.some(item => item.section === 'monitoring') && (
            <div className="px-3 py-2 mt-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Monitoring
            </div>
          )}
          {filteredNavigation
            .filter(item => item.section === 'monitoring')
            .map((item) => (
            <NavLink
              key={item.name}
              to={item.href}
              className={({ isActive }) =>
                cn(
                  "flex items-center px-3 py-2 rounded-lg text-sm font-medium transition-all duration-300 group",
                  "hover:bg-primary hover:text-primary-foreground hover:scale-[1.02] hover:shadow-soft",
                  isActive
                    ? "bg-primary text-primary-foreground shadow-elegant"
                    : "text-muted-foreground"
                )
              }
            >
              <item.icon className={cn(
                "h-4 w-4 transition-all duration-300 group-hover:scale-110",
                !collapsed && "mr-3"
              )} />
              {!collapsed && (
                <span className="transition-all duration-300">{item.name}</span>
              )}
            </NavLink>
          ))}

          {!collapsed && filteredNavigation.some(item => item.section === 'system') && (
            <div className="px-3 py-2 mt-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              System
            </div>
          )}
          {filteredNavigation
            .filter(item => item.section === 'system')
            .map((item) => (
            <NavLink
              key={item.name}
              to={item.href}
              className={({ isActive }) =>
                cn(
                  "flex items-center px-3 py-2 rounded-lg text-sm font-medium transition-all duration-300 group",
                  "hover:bg-primary hover:text-primary-foreground hover:scale-[1.02] hover:shadow-soft",
                  isActive
                    ? "bg-primary text-primary-foreground shadow-elegant"
                    : "text-muted-foreground"
                )
              }
            >
              <item.icon className={cn(
                "h-4 w-4 transition-all duration-300 group-hover:scale-110",
                !collapsed && "mr-3"
              )} />
              {!collapsed && (
                <span className="transition-all duration-300">{item.name}</span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="p-4 border-t border-border">
          <div className={cn(
            "text-xs text-muted-foreground",
            collapsed ? "text-center" : "space-y-1"
          )}>
            {!collapsed && (
              <>
                <div>Version 1.0.0</div>
                <div>© 2024 Integrix Host 2 Host</div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default Sidebar