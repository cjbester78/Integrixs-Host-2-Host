/**
 * Package Quick Navigation Component
 * 
 * Quick navigation component for switching between packages with search and
 * recent packages functionality. Implements Navigation Pattern, Command Pattern,
 * and Observer Pattern for efficient package navigation.
 * 
 * @author Claude Code
 * @since Package Management Frontend V2.0
 */

import React, { useState, useCallback, useMemo, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Package,
  Search,
  Clock,
  Star,
  ChevronDown,
  ArrowRight,
  Plus,
  Loader2,
  Bookmark,
  BookmarkPlus,
  Grid3x3,
  Zap
} from 'lucide-react'

import { packageService } from '@/services/packageService'
import type { IntegrationPackage } from '@/types/package'

// UI Components
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'

import { cn } from '@/lib/utils'

/**
 * Props interface following interface segregation principle
 */
interface PackageQuickNavProps {
  className?: string
  variant?: 'dropdown' | 'compact' | 'full'
  showCreateButton?: boolean
  onPackageChange?: (packageId: string) => void
}

/**
 * Package item interface for navigation
 */
interface PackageNavItem {
  id: string
  name: string
  description?: string
  status: string
  assetCount: number
  lastAccessed?: Date
  isFavorite?: boolean
}

/**
 * Recent packages manager following singleton pattern
 */
class RecentPackagesManager {
  private static readonly STORAGE_KEY = 'h2h_recent_packages'
  private static readonly MAX_RECENT = 5

  /**
   * Gets recent packages from localStorage
   */
  static getRecentPackages(): string[] {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY)
      return stored ? JSON.parse(stored) : []
    } catch {
      return []
    }
  }

  /**
   * Adds package to recent list
   */
  static addRecentPackage(packageId: string): void {
    try {
      const recent = this.getRecentPackages()
      const filtered = recent.filter(id => id !== packageId)
      const updated = [packageId, ...filtered].slice(0, this.MAX_RECENT)
      
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(updated))
    } catch (error) {
      console.warn('Failed to update recent packages:', error)
    }
  }
}

/**
 * Package favorites manager following singleton pattern
 */
class PackageFavoritesManager {
  private static readonly STORAGE_KEY = 'h2h_favorite_packages'

  /**
   * Gets favorite packages from localStorage
   */
  static getFavoritePackages(): string[] {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY)
      return stored ? JSON.parse(stored) : []
    } catch {
      return []
    }
  }

  /**
   * Toggles package favorite status
   */
  static toggleFavorite(packageId: string): boolean {
    try {
      const favorites = this.getFavoritePackages()
      const isFavorite = favorites.includes(packageId)
      
      const updated = isFavorite
        ? favorites.filter(id => id !== packageId)
        : [...favorites, packageId]
      
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(updated))
      return !isFavorite
    } catch (error) {
      console.warn('Failed to toggle package favorite:', error)
      return false
    }
  }
}

/**
 * Package data transformer following transformer pattern
 */
class PackageNavTransformer {
  /**
   * Transforms packages to navigation items
   */
  static transformToNavItems(packages: IntegrationPackage[], recentIds: string[], favoriteIds: string[]): PackageNavItem[] {
    return packages.map(pkg => ({
      id: pkg.id,
      name: pkg.name,
      description: pkg.description,
      status: pkg.status,
      assetCount: pkg.adapterCount + pkg.flowCount,
      lastAccessed: recentIds.includes(pkg.id) ? new Date(pkg.updatedAt) : undefined,
      isFavorite: favoriteIds.includes(pkg.id)
    }))
  }

  /**
   * Sorts packages by relevance (favorites, recent, then alphabetical)
   */
  static sortByRelevance(items: PackageNavItem[], recentIds: string[]): PackageNavItem[] {
    return items.sort((a, b) => {
      // Favorites first
      if (a.isFavorite && !b.isFavorite) return -1
      if (!a.isFavorite && b.isFavorite) return 1
      
      // Then recent packages
      const aRecentIndex = recentIds.indexOf(a.id)
      const bRecentIndex = recentIds.indexOf(b.id)
      
      if (aRecentIndex !== -1 && bRecentIndex === -1) return -1
      if (aRecentIndex === -1 && bRecentIndex !== -1) return 1
      if (aRecentIndex !== -1 && bRecentIndex !== -1) return aRecentIndex - bRecentIndex
      
      // Finally alphabetical
      return a.name.localeCompare(b.name)
    })
  }
}

/**
 * Package navigation item component following component composition pattern
 */
interface PackageNavItemComponentProps {
  item: PackageNavItem
  isActive: boolean
  onSelect: (item: PackageNavItem) => void
  onToggleFavorite: (item: PackageNavItem) => void
  showFavoriteButton?: boolean
}

const PackageNavItemComponent: React.FC<PackageNavItemComponentProps> = ({
  item,
  isActive,
  onSelect,
  onToggleFavorite,
  showFavoriteButton = true
}) => {
  const handleSelect = useCallback(() => {
    onSelect(item)
  }, [item, onSelect])

  const handleToggleFavorite = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    onToggleFavorite(item)
  }, [item, onToggleFavorite])

  const getStatusColor = (status: string) => {
    switch (status.toLowerCase()) {
      case 'active': return 'bg-green-500'
      case 'draft': return 'bg-yellow-500'
      case 'archived': return 'bg-gray-500'
      default: return 'bg-blue-500'
    }
  }

  return (
    <DropdownMenuItem 
      className={cn(
        "flex items-center justify-between p-3 cursor-pointer",
        isActive && "bg-muted"
      )}
      onSelect={handleSelect}
    >
      <div className="flex items-center gap-3 flex-1 min-w-0">
        <div className="relative">
          <Package className="h-4 w-4 text-muted-foreground" />
          <div className={cn(
            "absolute -top-1 -right-1 w-2 h-2 rounded-full",
            getStatusColor(item.status)
          )} />
        </div>
        
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium truncate">{item.name}</span>
            {item.isFavorite && <Star className="h-3 w-3 text-yellow-500 fill-current" />}
            {item.lastAccessed && <Clock className="h-3 w-3 text-muted-foreground" />}
          </div>
          
          {item.description && (
            <p className="text-xs text-muted-foreground truncate mt-1">
              {item.description}
            </p>
          )}
          
          <div className="flex items-center gap-2 mt-1">
            <Badge variant="outline" className="text-xs">
              {item.assetCount} assets
            </Badge>
            <Badge 
              variant={item.status === 'ACTIVE' ? 'default' : 'secondary'}
              className="text-xs"
            >
              {item.status}
            </Badge>
          </div>
        </div>
      </div>
      
      {showFavoriteButton && (
        <Button
          variant="ghost"
          size="sm"
          onClick={handleToggleFavorite}
          className="h-6 w-6 p-0 ml-2"
        >
          {item.isFavorite ? (
            <Bookmark className="h-3 w-3 text-yellow-500" />
          ) : (
            <BookmarkPlus className="h-3 w-3 text-muted-foreground" />
          )}
        </Button>
      )}
    </DropdownMenuItem>
  )
}

/**
 * Main Package Quick Navigation component following composite pattern
 */
const PackageQuickNav: React.FC<PackageQuickNavProps> = ({
  className,
  variant = 'dropdown',
  showCreateButton = true,
  onPackageChange
}) => {
  const navigate = useNavigate()
  const { packageId: currentPackageId } = useParams()
  
  const [searchQuery, setSearchQuery] = useState('')
  const [recentPackages, setRecentPackages] = useState<string[]>([])
  const [favoritePackages, setFavoritePackages] = useState<string[]>([])

  // Load recent and favorite packages on mount
  useEffect(() => {
    setRecentPackages(RecentPackagesManager.getRecentPackages())
    setFavoritePackages(PackageFavoritesManager.getFavoritePackages())
  }, [])

  // Fetch all packages
  const { data: packages = [], isLoading } = useQuery({
    queryKey: ['packages'],
    queryFn: () => packageService.getAllPackages(),
    staleTime: 2 * 60 * 1000 // 2 minutes
  })

  // Get current package info
  const currentPackage = useMemo(() => {
    return packages.find(pkg => pkg.id === currentPackageId)
  }, [packages, currentPackageId])

  // Transform and filter packages
  const navItems = useMemo(() => {
    const items = PackageNavTransformer.transformToNavItems(packages, recentPackages, favoritePackages)
    
    // Filter by search query if provided
    const filtered = searchQuery
      ? items.filter(item =>
          item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          item.description?.toLowerCase().includes(searchQuery.toLowerCase())
        )
      : items

    return PackageNavTransformer.sortByRelevance(filtered, recentPackages)
  }, [packages, recentPackages, favoritePackages, searchQuery])

  // Get categorized items
  const categorizedItems = useMemo(() => {
    const favorites = navItems.filter(item => item.isFavorite)
    const recent = navItems.filter(item => item.lastAccessed && !item.isFavorite).slice(0, 3)
    const others = navItems.filter(item => !item.isFavorite && !item.lastAccessed)

    return { favorites, recent, others }
  }, [navItems])

  // Event handlers
  const handlePackageSelect = useCallback((item: PackageNavItem) => {
    RecentPackagesManager.addRecentPackage(item.id)
    setRecentPackages(RecentPackagesManager.getRecentPackages())
    
    const path = `/packages/${item.id}/workspace`
    navigate(path)
    
    if (onPackageChange) {
      onPackageChange(item.id)
    }
  }, [navigate, onPackageChange])

  const handleToggleFavorite = useCallback((item: PackageNavItem) => {
    const newFavoriteState = PackageFavoritesManager.toggleFavorite(item.id)
    setFavoritePackages(PackageFavoritesManager.getFavoritePackages())
  }, [])

  const handleCreatePackage = useCallback(() => {
    navigate('/packages/new')
  }, [navigate])

  const handleViewAllPackages = useCallback(() => {
    navigate('/packages')
  }, [navigate])

  const renderTrigger = () => {
    if (variant === 'compact') {
      return (
        <Button variant="outline" size="sm" className={className}>
          <Package className="h-4 w-4 mr-2" />
          {currentPackage?.name || 'Select Package'}
          <ChevronDown className="h-4 w-4 ml-2" />
        </Button>
      )
    }

    return (
      <Button variant="outline" className={cn("justify-between min-w-48", className)}>
        <div className="flex items-center gap-2 truncate">
          <Package className="h-4 w-4" />
          <span className="truncate">
            {currentPackage?.name || 'Select Package'}
          </span>
        </div>
        <ChevronDown className="h-4 w-4 ml-2 flex-shrink-0" />
      </Button>
    )
  }

  const renderContent = () => (
    <DropdownMenuContent className="w-80 max-h-96 overflow-y-auto">
      <DropdownMenuLabel className="flex items-center justify-between">
        <span>Switch Package</span>
        {showCreateButton && (
          <Button variant="ghost" size="sm" onClick={handleCreatePackage}>
            <Plus className="h-3 w-3 mr-1" />
            New
          </Button>
        )}
      </DropdownMenuLabel>
      
      <div className="px-2 pb-2">
        <div className="relative">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search packages..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-8 h-8"
          />
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center p-4">
          <Loader2 className="h-4 w-4 animate-spin mr-2" />
          <span className="text-sm text-muted-foreground">Loading packages...</span>
        </div>
      ) : (
        <>
          {/* Favorites */}
          {categorizedItems.favorites.length > 0 && (
            <>
              <DropdownMenuLabel className="text-xs text-muted-foreground flex items-center gap-1">
                <Star className="h-3 w-3" />
                Favorites
              </DropdownMenuLabel>
              {categorizedItems.favorites.map(item => (
                <PackageNavItemComponent
                  key={item.id}
                  item={item}
                  isActive={item.id === currentPackageId}
                  onSelect={handlePackageSelect}
                  onToggleFavorite={handleToggleFavorite}
                />
              ))}
              <DropdownMenuSeparator />
            </>
          )}

          {/* Recent */}
          {categorizedItems.recent.length > 0 && (
            <>
              <DropdownMenuLabel className="text-xs text-muted-foreground flex items-center gap-1">
                <Clock className="h-3 w-3" />
                Recent
              </DropdownMenuLabel>
              {categorizedItems.recent.map(item => (
                <PackageNavItemComponent
                  key={item.id}
                  item={item}
                  isActive={item.id === currentPackageId}
                  onSelect={handlePackageSelect}
                  onToggleFavorite={handleToggleFavorite}
                />
              ))}
              <DropdownMenuSeparator />
            </>
          )}

          {/* All packages */}
          {categorizedItems.others.length > 0 && (
            <>
              <DropdownMenuLabel className="text-xs text-muted-foreground flex items-center gap-1">
                <Package className="h-3 w-3" />
                All Packages
              </DropdownMenuLabel>
              {categorizedItems.others.slice(0, 8).map(item => (
                <PackageNavItemComponent
                  key={item.id}
                  item={item}
                  isActive={item.id === currentPackageId}
                  onSelect={handlePackageSelect}
                  onToggleFavorite={handleToggleFavorite}
                />
              ))}
              
              {categorizedItems.others.length > 8 && (
                <DropdownMenuItem onSelect={handleViewAllPackages}>
                  <Grid3x3 className="h-4 w-4 mr-2" />
                  View All Packages
                  <ArrowRight className="h-4 w-4 ml-auto" />
                </DropdownMenuItem>
              )}
            </>
          )}

          {navItems.length === 0 && searchQuery && (
            <div className="p-4 text-center text-sm text-muted-foreground">
              No packages found matching "{searchQuery}"
            </div>
          )}

          {navItems.length === 0 && !searchQuery && !isLoading && (
            <div className="p-4 text-center">
              <Package className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
              <p className="text-sm text-muted-foreground mb-2">No packages found</p>
              {showCreateButton && (
                <Button variant="outline" size="sm" onClick={handleCreatePackage}>
                  <Plus className="h-4 w-4 mr-2" />
                  Create First Package
                </Button>
              )}
            </div>
          )}
        </>
      )}

      <DropdownMenuSeparator />
      <DropdownMenuItem onSelect={handleViewAllPackages}>
        <Zap className="h-4 w-4 mr-2" />
        Package Library
        <ArrowRight className="h-4 w-4 ml-auto" />
      </DropdownMenuItem>
    </DropdownMenuContent>
  )

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        {renderTrigger()}
      </DropdownMenuTrigger>
      {renderContent()}
    </DropdownMenu>
  )
}

export default PackageQuickNav