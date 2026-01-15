/**
 * Package Management Context Provider
 * 
 * React context for package state management following context pattern.
 * Provides centralized state management for package operations with type safety.
 * 
 * @author Claude Code
 * @since Package Management Frontend V1.0
 */

import React, { createContext, useContext, useReducer, useCallback, ReactNode } from 'react'
import {
  IntegrationPackage,
  PackageSummary,
  PackageContainer,
  PackageWorkspaceState,
  PackageLibraryState,
  PackageSearchCriteria,
  PackageType,
  PackageStatus,
  AssetType,
  PackageError
} from '@/types/package'

// State interface following state management pattern
interface PackageState {
  // Current packages data
  packages: PackageSummary[]
  currentPackage: IntegrationPackage | null
  currentPackageContainer: PackageContainer | null
  
  // UI state
  workspaceState: PackageWorkspaceState
  libraryState: PackageLibraryState
  
  // Loading and error states
  isLoading: boolean
  error: PackageError | null
  
  // Statistics and metadata
  totalCount: number
  hasNextPage: boolean
  hasPreviousPage: boolean
}

// Action types following action pattern
type PackageAction =
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_ERROR'; payload: PackageError | null }
  | { type: 'SET_PACKAGES'; payload: { packages: PackageSummary[]; totalCount: number; hasNextPage: boolean; hasPreviousPage: boolean } }
  | { type: 'ADD_PACKAGE'; payload: PackageSummary }
  | { type: 'UPDATE_PACKAGE'; payload: PackageSummary }
  | { type: 'REMOVE_PACKAGE'; payload: string }
  | { type: 'SET_CURRENT_PACKAGE'; payload: IntegrationPackage | null }
  | { type: 'SET_CURRENT_PACKAGE_CONTAINER'; payload: PackageContainer | null }
  | { type: 'UPDATE_WORKSPACE_STATE'; payload: Partial<PackageWorkspaceState> }
  | { type: 'UPDATE_LIBRARY_STATE'; payload: Partial<PackageLibraryState> }
  | { type: 'SELECT_ASSET'; payload: string }
  | { type: 'DESELECT_ASSET'; payload: string }
  | { type: 'CLEAR_SELECTED_ASSETS' }
  | { type: 'SELECT_PACKAGE'; payload: string }
  | { type: 'DESELECT_PACKAGE'; payload: string }
  | { type: 'CLEAR_SELECTED_PACKAGES' }
  | { type: 'UPDATE_SEARCH_CRITERIA'; payload: Partial<PackageSearchCriteria> }
  | { type: 'SET_ACTIVE_TAB'; payload: 'overview' | 'adapters' | 'flows' | 'dependencies' }
  | { type: 'SET_VIEW_MODE'; payload: 'grid' | 'list' }
  | { type: 'SET_CREATE_MODAL'; payload: boolean }
  | { type: 'RESET_STATE' }

// Context interface following context pattern
interface PackageContextType {
  state: PackageState
  dispatch: React.Dispatch<PackageAction>
  
  // Computed properties following derived state pattern
  selectedPackageCount: number
  selectedAssetCount: number
  hasSelectedPackages: boolean
  hasSelectedAssets: boolean
  isWorkspaceActive: boolean
  currentPackageId: string | null
  
  // Action creators following action creator pattern
  actions: {
    setLoading: (loading: boolean) => void
    setError: (error: PackageError | null) => void
    setPackages: (packages: PackageSummary[], totalCount: number, hasNextPage: boolean, hasPreviousPage: boolean) => void
    addPackage: (package_: PackageSummary) => void
    updatePackage: (package_: PackageSummary) => void
    removePackage: (packageId: string) => void
    setCurrentPackage: (package_: IntegrationPackage | null) => void
    setCurrentPackageContainer: (container: PackageContainer | null) => void
    updateWorkspaceState: (updates: Partial<PackageWorkspaceState>) => void
    updateLibraryState: (updates: Partial<PackageLibraryState>) => void
    selectAsset: (assetId: string) => void
    deselectAsset: (assetId: string) => void
    clearSelectedAssets: () => void
    selectPackage: (packageId: string) => void
    deselectPackage: (packageId: string) => void
    clearSelectedPackages: () => void
    updateSearchCriteria: (criteria: Partial<PackageSearchCriteria>) => void
    setActiveTab: (tab: 'overview' | 'adapters' | 'flows' | 'dependencies') => void
    setViewMode: (mode: 'grid' | 'list') => void
    setCreateModal: (show: boolean) => void
    resetState: () => void
  }
}

// Initial state following default state pattern
const initialState: PackageState = {
  packages: [],
  currentPackage: null,
  currentPackageContainer: null,
  
  workspaceState: {
    selectedAssets: new Set<string>(),
    activeTab: 'overview',
    isLoading: false,
    error: undefined
  },
  
  libraryState: {
    searchCriteria: {
      searchTerm: '',
      packageTypes: [],
      statuses: [PackageStatus.ACTIVE],
      tags: [],
      sortBy: 'updatedAt',
      sortOrder: 'desc',
      limit: 20,
      offset: 0
    },
    selectedPackages: new Set<string>(),
    viewMode: 'grid',
    isCreating: false,
    showCreateModal: false
  },
  
  isLoading: false,
  error: null,
  totalCount: 0,
  hasNextPage: false,
  hasPreviousPage: false
}

// Reducer function following reducer pattern
const packageReducer = (state: PackageState, action: PackageAction): PackageState => {
  switch (action.type) {
    case 'SET_LOADING':
      return {
        ...state,
        isLoading: action.payload
      }
      
    case 'SET_ERROR':
      return {
        ...state,
        error: action.payload,
        isLoading: false
      }
      
    case 'SET_PACKAGES':
      return {
        ...state,
        packages: action.payload.packages,
        totalCount: action.payload.totalCount,
        hasNextPage: action.payload.hasNextPage,
        hasPreviousPage: action.payload.hasPreviousPage,
        isLoading: false,
        error: null
      }
      
    case 'ADD_PACKAGE':
      return {
        ...state,
        packages: [action.payload, ...state.packages],
        totalCount: state.totalCount + 1
      }
      
    case 'UPDATE_PACKAGE': {
      const updatedPackages = state.packages.map(pkg =>
        pkg.id === action.payload.id ? action.payload : pkg
      )
      
      return {
        ...state,
        packages: updatedPackages,
        currentPackage: state.currentPackage?.id === action.payload.id 
          ? { ...state.currentPackage, ...action.payload } 
          : state.currentPackage
      }
    }
    
    case 'REMOVE_PACKAGE': {
      const filteredPackages = state.packages.filter(pkg => pkg.id !== action.payload)
      
      return {
        ...state,
        packages: filteredPackages,
        totalCount: Math.max(0, state.totalCount - 1),
        currentPackage: state.currentPackage?.id === action.payload ? null : state.currentPackage,
        currentPackageContainer: state.currentPackageContainer?.packageInfo.id === action.payload 
          ? null : state.currentPackageContainer
      }
    }
    
    case 'SET_CURRENT_PACKAGE':
      return {
        ...state,
        currentPackage: action.payload,
        workspaceState: {
          ...state.workspaceState,
          currentPackageId: action.payload?.id
        }
      }
      
    case 'SET_CURRENT_PACKAGE_CONTAINER':
      return {
        ...state,
        currentPackageContainer: action.payload
      }
      
    case 'UPDATE_WORKSPACE_STATE':
      return {
        ...state,
        workspaceState: {
          ...state.workspaceState,
          ...action.payload
        }
      }
      
    case 'UPDATE_LIBRARY_STATE':
      return {
        ...state,
        libraryState: {
          ...state.libraryState,
          ...action.payload
        }
      }
      
    case 'SELECT_ASSET': {
      const newSelectedAssets = new Set(state.workspaceState.selectedAssets)
      newSelectedAssets.add(action.payload)
      
      return {
        ...state,
        workspaceState: {
          ...state.workspaceState,
          selectedAssets: newSelectedAssets
        }
      }
    }
    
    case 'DESELECT_ASSET': {
      const newSelectedAssets = new Set(state.workspaceState.selectedAssets)
      newSelectedAssets.delete(action.payload)
      
      return {
        ...state,
        workspaceState: {
          ...state.workspaceState,
          selectedAssets: newSelectedAssets
        }
      }
    }
    
    case 'CLEAR_SELECTED_ASSETS':
      return {
        ...state,
        workspaceState: {
          ...state.workspaceState,
          selectedAssets: new Set<string>()
        }
      }
      
    case 'SELECT_PACKAGE': {
      const newSelectedPackages = new Set(state.libraryState.selectedPackages)
      newSelectedPackages.add(action.payload)
      
      return {
        ...state,
        libraryState: {
          ...state.libraryState,
          selectedPackages: newSelectedPackages
        }
      }
    }
    
    case 'DESELECT_PACKAGE': {
      const newSelectedPackages = new Set(state.libraryState.selectedPackages)
      newSelectedPackages.delete(action.payload)
      
      return {
        ...state,
        libraryState: {
          ...state.libraryState,
          selectedPackages: newSelectedPackages
        }
      }
    }
    
    case 'CLEAR_SELECTED_PACKAGES':
      return {
        ...state,
        libraryState: {
          ...state.libraryState,
          selectedPackages: new Set<string>()
        }
      }
      
    case 'UPDATE_SEARCH_CRITERIA':
      return {
        ...state,
        libraryState: {
          ...state.libraryState,
          searchCriteria: {
            ...state.libraryState.searchCriteria,
            ...action.payload
          }
        }
      }
      
    case 'SET_ACTIVE_TAB':
      return {
        ...state,
        workspaceState: {
          ...state.workspaceState,
          activeTab: action.payload
        }
      }
      
    case 'SET_VIEW_MODE':
      return {
        ...state,
        libraryState: {
          ...state.libraryState,
          viewMode: action.payload
        }
      }
      
    case 'SET_CREATE_MODAL':
      return {
        ...state,
        libraryState: {
          ...state.libraryState,
          showCreateModal: action.payload
        }
      }
      
    case 'RESET_STATE':
      return initialState
      
    default:
      return state
  }
}

// Context creation following context pattern
const PackageContext = createContext<PackageContextType | undefined>(undefined)

// Provider component props
interface PackageProviderProps {
  children: ReactNode
}

/**
 * Package context provider component.
 * Manages package state and provides actions to child components.
 */
export const PackageProvider: React.FC<PackageProviderProps> = ({ children }) => {
  const [state, dispatch] = useReducer(packageReducer, initialState)
  
  // Computed properties using useMemo equivalent logic
  const selectedPackageCount = state.libraryState.selectedPackages.size
  const selectedAssetCount = state.workspaceState.selectedAssets.size
  const hasSelectedPackages = selectedPackageCount > 0
  const hasSelectedAssets = selectedAssetCount > 0
  const isWorkspaceActive = Boolean(state.currentPackage)
  const currentPackageId = state.currentPackage?.id || null
  
  // Action creators using useCallback for performance
  const actions = {
    setLoading: useCallback((loading: boolean) => {
      dispatch({ type: 'SET_LOADING', payload: loading })
    }, []),
    
    setError: useCallback((error: PackageError | null) => {
      dispatch({ type: 'SET_ERROR', payload: error })
    }, []),
    
    setPackages: useCallback((packages: PackageSummary[], totalCount: number, hasNextPage: boolean, hasPreviousPage: boolean) => {
      dispatch({ type: 'SET_PACKAGES', payload: { packages, totalCount, hasNextPage, hasPreviousPage } })
    }, []),
    
    addPackage: useCallback((package_: PackageSummary) => {
      dispatch({ type: 'ADD_PACKAGE', payload: package_ })
    }, []),
    
    updatePackage: useCallback((package_: PackageSummary) => {
      dispatch({ type: 'UPDATE_PACKAGE', payload: package_ })
    }, []),
    
    removePackage: useCallback((packageId: string) => {
      dispatch({ type: 'REMOVE_PACKAGE', payload: packageId })
    }, []),
    
    setCurrentPackage: useCallback((package_: IntegrationPackage | null) => {
      dispatch({ type: 'SET_CURRENT_PACKAGE', payload: package_ })
    }, []),
    
    setCurrentPackageContainer: useCallback((container: PackageContainer | null) => {
      dispatch({ type: 'SET_CURRENT_PACKAGE_CONTAINER', payload: container })
    }, []),
    
    updateWorkspaceState: useCallback((updates: Partial<PackageWorkspaceState>) => {
      dispatch({ type: 'UPDATE_WORKSPACE_STATE', payload: updates })
    }, []),
    
    updateLibraryState: useCallback((updates: Partial<PackageLibraryState>) => {
      dispatch({ type: 'UPDATE_LIBRARY_STATE', payload: updates })
    }, []),
    
    selectAsset: useCallback((assetId: string) => {
      dispatch({ type: 'SELECT_ASSET', payload: assetId })
    }, []),
    
    deselectAsset: useCallback((assetId: string) => {
      dispatch({ type: 'DESELECT_ASSET', payload: assetId })
    }, []),
    
    clearSelectedAssets: useCallback(() => {
      dispatch({ type: 'CLEAR_SELECTED_ASSETS' })
    }, []),
    
    selectPackage: useCallback((packageId: string) => {
      dispatch({ type: 'SELECT_PACKAGE', payload: packageId })
    }, []),
    
    deselectPackage: useCallback((packageId: string) => {
      dispatch({ type: 'DESELECT_PACKAGE', payload: packageId })
    }, []),
    
    clearSelectedPackages: useCallback(() => {
      dispatch({ type: 'CLEAR_SELECTED_PACKAGES' })
    }, []),
    
    updateSearchCriteria: useCallback((criteria: Partial<PackageSearchCriteria>) => {
      dispatch({ type: 'UPDATE_SEARCH_CRITERIA', payload: criteria })
    }, []),
    
    setActiveTab: useCallback((tab: 'overview' | 'adapters' | 'flows' | 'dependencies') => {
      dispatch({ type: 'SET_ACTIVE_TAB', payload: tab })
    }, []),
    
    setViewMode: useCallback((mode: 'grid' | 'list') => {
      dispatch({ type: 'SET_VIEW_MODE', payload: mode })
    }, []),
    
    setCreateModal: useCallback((show: boolean) => {
      dispatch({ type: 'SET_CREATE_MODAL', payload: show })
    }, []),
    
    resetState: useCallback(() => {
      dispatch({ type: 'RESET_STATE' })
    }, [])
  }
  
  // Context value
  const value: PackageContextType = {
    state,
    dispatch,
    selectedPackageCount,
    selectedAssetCount,
    hasSelectedPackages,
    hasSelectedAssets,
    isWorkspaceActive,
    currentPackageId,
    actions
  }
  
  return (
    <PackageContext.Provider value={value}>
      {children}
    </PackageContext.Provider>
  )
}

/**
 * Custom hook for accessing package context.
 * Provides type-safe access to package state and actions.
 * 
 * @returns PackageContextType
 * @throws Error if used outside PackageProvider
 */
export const usePackageContext = (): PackageContextType => {
  const context = useContext(PackageContext)
  
  if (context === undefined) {
    throw new Error('usePackageContext must be used within a PackageProvider')
  }
  
  return context
}

/**
 * Higher-order component for providing package context.
 * Wraps components with package state management.
 * 
 * @param Component Component to wrap
 * @returns Wrapped component with package context
 */
export const withPackageContext = <P extends object>(
  Component: React.ComponentType<P>
): React.FC<P> => {
  const WrappedComponent: React.FC<P> = (props) => (
    <PackageProvider>
      <Component {...props} />
    </PackageProvider>
  )
  
  WrappedComponent.displayName = `withPackageContext(${Component.displayName || Component.name})`
  
  return WrappedComponent
}