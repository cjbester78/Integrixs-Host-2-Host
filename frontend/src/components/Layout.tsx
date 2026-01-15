import React from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'
import { SidebarProvider } from '@/contexts/SidebarContext'

const Layout: React.FC = () => {
  return (
    <SidebarProvider>
      <div className="h-screen w-full bg-background flex flex-col overflow-hidden">
        <Header />
        <div className="flex flex-1 w-full overflow-hidden">
          <Sidebar />
          <div className="flex-1 flex flex-col min-w-0 w-full max-w-none overflow-hidden">
            <main className="flex-1 overflow-auto p-6 w-full">
              <Outlet />
            </main>
          </div>
        </div>
      </div>
    </SidebarProvider>
  )
}

export default Layout