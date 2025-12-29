import { create } from 'zustand'

interface Notification {
  id: string
  type: 'success' | 'error' | 'warning' | 'info'
  title: string
  message: string
  timestamp: Date
  duration?: number
}

interface UIState {
  // Sidebar
  sidebarOpen: boolean
  
  // Notifications
  notifications: Notification[]
  
  // Loading states
  globalLoading: boolean
  
  // Modals
  modals: Record<string, boolean>
  
  // Actions
  toggleSidebar: () => void
  setSidebarOpen: (open: boolean) => void
  
  addNotification: (notification: Omit<Notification, 'id' | 'timestamp'>) => void
  removeNotification: (id: string) => void
  clearNotifications: () => void
  
  setGlobalLoading: (loading: boolean) => void
  
  openModal: (modalId: string) => void
  closeModal: (modalId: string) => void
  toggleModal: (modalId: string) => void
}

export const useUIStore = create<UIState>((set, get) => ({
  // Initial state
  sidebarOpen: true,
  notifications: [],
  globalLoading: false,
  modals: {},
  
  // Sidebar actions
  toggleSidebar: () => {
    set((state) => ({ sidebarOpen: !state.sidebarOpen }))
  },
  
  setSidebarOpen: (open: boolean) => {
    set({ sidebarOpen: open })
  },
  
  // Notification actions
  addNotification: (notification) => {
    const id = Math.random().toString(36).substr(2, 9)
    const newNotification: Notification = {
      ...notification,
      id,
      timestamp: new Date(),
      duration: notification.duration || 5000,
    }
    
    set((state) => ({
      notifications: [newNotification, ...state.notifications].slice(0, 10), // Keep max 10
    }))
    
    // Auto-remove after duration
    if ((newNotification.duration ?? 4000) > 0) {
      setTimeout(() => {
        get().removeNotification(id)
      }, newNotification.duration ?? 4000)
    }
  },
  
  removeNotification: (id: string) => {
    set((state) => ({
      notifications: state.notifications.filter((n) => n.id !== id),
    }))
  },
  
  clearNotifications: () => {
    set({ notifications: [] })
  },
  
  // Loading actions
  setGlobalLoading: (loading: boolean) => {
    set({ globalLoading: loading })
  },
  
  // Modal actions
  openModal: (modalId: string) => {
    set((state) => ({
      modals: { ...state.modals, [modalId]: true },
    }))
  },
  
  closeModal: (modalId: string) => {
    set((state) => ({
      modals: { ...state.modals, [modalId]: false },
    }))
  },
  
  toggleModal: (modalId: string) => {
    set((state) => ({
      modals: { ...state.modals, [modalId]: !state.modals[modalId] },
    }))
  },
}))

// Utility hook for notifications
export const useNotifications = () => {
  const { addNotification, removeNotification, clearNotifications } = useUIStore()
  
  return {
    success: (title: string, message: string, duration?: number) =>
      addNotification({ type: 'success', title, message, duration }),
    
    error: (title: string, message: string, duration?: number) =>
      addNotification({ type: 'error', title, message, duration }),
    
    warning: (title: string, message: string, duration?: number) =>
      addNotification({ type: 'warning', title, message, duration }),
    
    info: (title: string, message: string, duration?: number) =>
      addNotification({ type: 'info', title, message, duration }),
    
    remove: removeNotification,
    clear: clearNotifications,
  }
}