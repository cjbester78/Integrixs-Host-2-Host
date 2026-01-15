/**
 * Global Search Modal Component
 * 
 * Advanced search interface with comprehensive filtering, autocomplete, and result
 * presentation. Implements Search UI Pattern, Command Pattern, and Observer Pattern
 * for optimal search experience and performance.
 * 
 * @author Claude Code
 * @since Package Management Frontend V2.0
 */

import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  Search,
  Filter,
  X,
  Clock,
  Package,
  Settings,
  Workflow,
  GitBranch,
  ArrowRight,
  Loader2,
  TrendingUp,
  Calendar,
  Star,
  ChevronDown,
  ChevronUp,
  MoreHorizontal
} from 'lucide-react'
import toast from 'react-hot-toast'

import { GlobalSearchService } from '@/services/globalSearchService'
import type { 
  SearchQuery, 
  SearchResult, 
  SearchFilters, 
  SearchResponse 
} from '@/services/globalSearchService'

// UI Components
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'

import { cn } from '@/lib/utils'

/**
 * Props interface following interface segregation principle
 */
interface GlobalSearchModalProps {
  isOpen: boolean
  onClose: () => void
  initialQuery?: string
  onNavigate?: (path: string) => void
}

/**
 * Search state interface following state management pattern
 */
interface SearchState {
  query: string
  filters: SearchFilters
  sortBy: string
  sortOrder: 'asc' | 'desc'
  showAdvanced: boolean
  selectedResult: string | null
}

/**
 * Result type configuration following configuration pattern
 */
const RESULT_TYPE_CONFIG = {
  package: {
    icon: Package,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    label: 'Package'
  },
  adapter: {
    icon: Settings,
    color: 'text-green-600',
    bgColor: 'bg-green-50',
    label: 'Adapter'
  },
  flow: {
    icon: Workflow,
    color: 'text-purple-600',
    bgColor: 'bg-purple-50',
    label: 'Flow'
  },
  dependency: {
    icon: GitBranch,
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
    label: 'Dependency'
  }
}

/**
 * Debounce hook for search optimization
 */
function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value)

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value)
    }, delay)

    return () => {
      clearTimeout(handler)
    }
  }, [value, delay])

  return debouncedValue
}

/**
 * Search result item component following component composition pattern
 */
interface SearchResultItemProps {
  result: SearchResult
  isSelected: boolean
  onSelect: (result: SearchResult) => void
  onNavigate: (result: SearchResult) => void
}

const SearchResultItem: React.FC<SearchResultItemProps> = ({ 
  result, 
  isSelected, 
  onSelect, 
  onNavigate 
}) => {
  const config = RESULT_TYPE_CONFIG[result.type] || RESULT_TYPE_CONFIG.package
  const IconComponent = config.icon

  const handleClick = useCallback(() => {
    onSelect(result)
  }, [result, onSelect])

  const handleNavigate = useCallback((e: React.MouseEvent) => {
    e.stopPropagation()
    onNavigate(result)
  }, [result, onNavigate])

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onNavigate(result)
    }
  }, [result, onNavigate])

  return (
    <div
      className={cn(
        "p-4 border-l-4 cursor-pointer transition-all duration-200",
        "hover:bg-muted/50 focus:bg-muted/50 focus:outline-none",
        isSelected ? 'bg-muted border-l-primary' : 'border-l-transparent'
      )}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      tabIndex={0}
      role="button"
    >
      <div className="flex items-start gap-3">
        <div className={cn(
          "p-2 rounded-lg",
          config.bgColor
        )}>
          <IconComponent className={cn("h-4 w-4", config.color)} />
        </div>
        
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div className="flex-1 min-w-0">
              <h4 className="font-medium text-foreground truncate">
                {result.title}
              </h4>
              {result.subtitle && (
                <p className="text-sm text-muted-foreground mt-1">
                  {result.subtitle}
                </p>
              )}
            </div>
            
            <div className="flex items-center gap-2 flex-shrink-0">
              <Badge variant="outline" className="text-xs">
                {result.score}
              </Badge>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleNavigate}
                className="h-6 w-6 p-0"
              >
                <ArrowRight className="h-3 w-3" />
              </Button>
            </div>
          </div>
          
          {result.description && (
            <p className="text-sm text-muted-foreground mt-2 line-clamp-2">
              {result.description}
            </p>
          )}
          
          <div className="flex items-center gap-2 mt-2">
            <Badge variant="secondary" className="text-xs">
              {result.packageName}
            </Badge>
            
            {result.status && (
              <Badge 
                variant={result.status === 'active' || result.status === 'deployed' ? 'default' : 'secondary'}
                className="text-xs"
              >
                {result.status}
              </Badge>
            )}
            
            {result.lastModified && (
              <span className="text-xs text-muted-foreground flex items-center gap-1">
                <Calendar className="h-3 w-3" />
                {result.lastModified.toLocaleDateString()}
              </span>
            )}
          </div>
          
          {result.tags.length > 0 && (
            <div className="flex items-center gap-1 mt-2 flex-wrap">
              {result.tags.slice(0, 3).map((tag, index) => (
                <Badge key={index} variant="outline" className="text-xs">
                  {tag}
                </Badge>
              ))}
              {result.tags.length > 3 && (
                <span className="text-xs text-muted-foreground">
                  +{result.tags.length - 3} more
                </span>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/**
 * Search filters panel component following component composition pattern
 */
interface SearchFiltersPanelProps {
  filters: SearchFilters
  facets: SearchResponse['facets']
  onFiltersChange: (filters: SearchFilters) => void
}

const SearchFiltersPanel: React.FC<SearchFiltersPanelProps> = ({
  filters,
  facets,
  onFiltersChange
}) => {
  const [isExpanded, setIsExpanded] = useState(false)

  const handleTypeChange = useCallback((type: string, checked: boolean) => {
    const newTypes = checked 
      ? [...filters.types, type]
      : filters.types.filter(t => t !== type)
    
    onFiltersChange({ ...filters, types: newTypes })
  }, [filters, onFiltersChange])

  const handleStatusChange = useCallback((status: string, checked: boolean) => {
    const newStatus = checked 
      ? [...filters.status, status]
      : filters.status.filter(s => s !== status)
    
    onFiltersChange({ ...filters, status: newStatus })
  }, [filters, onFiltersChange])

  const clearFilters = useCallback(() => {
    onFiltersChange({
      types: [],
      packages: [],
      status: [],
      minScore: undefined,
      dateRange: undefined
    })
  }, [onFiltersChange])

  const hasActiveFilters = filters.types.length > 0 || 
                          filters.packages.length > 0 || 
                          filters.status.length > 0 ||
                          filters.minScore !== undefined ||
                          filters.dateRange !== undefined

  return (
    <Collapsible open={isExpanded} onOpenChange={setIsExpanded}>
      <div className="border-t">
        <CollapsibleTrigger asChild>
          <Button variant="ghost" className="w-full justify-between p-4">
            <div className="flex items-center gap-2">
              <Filter className="h-4 w-4" />
              <span>Advanced Filters</span>
              {hasActiveFilters && (
                <Badge variant="secondary" className="text-xs">
                  Active
                </Badge>
              )}
            </div>
            {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </Button>
        </CollapsibleTrigger>
        
        <CollapsibleContent className="p-4 pt-0 space-y-4">
          {/* Type filters */}
          <div>
            <h4 className="font-medium text-sm mb-2">Asset Types</h4>
            <div className="space-y-2">
              {Object.entries(facets.types).map(([type, count]) => (
                <label key={type} className="flex items-center space-x-2 text-sm">
                  <Checkbox
                    checked={filters.types.includes(type)}
                    onCheckedChange={(checked) => handleTypeChange(type, !!checked)}
                  />
                  <span>{RESULT_TYPE_CONFIG[type as keyof typeof RESULT_TYPE_CONFIG]?.label || type}</span>
                  <Badge variant="outline" className="text-xs">
                    {count}
                  </Badge>
                </label>
              ))}
            </div>
          </div>
          
          {/* Status filters */}
          {Object.keys(facets.status).length > 0 && (
            <div>
              <h4 className="font-medium text-sm mb-2">Status</h4>
              <div className="space-y-2">
                {Object.entries(facets.status).map(([status, count]) => (
                  <label key={status} className="flex items-center space-x-2 text-sm">
                    <Checkbox
                      checked={filters.status.includes(status)}
                      onCheckedChange={(checked) => handleStatusChange(status, !!checked)}
                    />
                    <span className="capitalize">{status}</span>
                    <Badge variant="outline" className="text-xs">
                      {count}
                    </Badge>
                  </label>
                ))}
              </div>
            </div>
          )}
          
          {/* Clear filters */}
          {hasActiveFilters && (
            <Button variant="outline" size="sm" onClick={clearFilters} className="w-full">
              Clear All Filters
            </Button>
          )}
        </CollapsibleContent>
      </div>
    </Collapsible>
  )
}

/**
 * Main Global Search Modal component following composite pattern
 */
const GlobalSearchModal: React.FC<GlobalSearchModalProps> = ({
  isOpen,
  onClose,
  initialQuery = '',
  onNavigate
}) => {
  const navigate = useNavigate()
  const searchInputRef = useRef<HTMLInputElement>(null)
  
  // Search state management
  const [searchState, setSearchState] = useState<SearchState>({
    query: initialQuery,
    filters: {
      types: [],
      packages: [],
      status: []
    },
    sortBy: 'relevance',
    sortOrder: 'desc',
    showAdvanced: false,
    selectedResult: null
  })

  // Debounced search query for performance
  const debouncedQuery = useDebounce(searchState.query, 300)

  // Search API call with React Query
  const { data: searchResponse, isLoading, error } = useQuery({
    queryKey: ['global-search', debouncedQuery, searchState.filters, searchState.sortBy, searchState.sortOrder],
    queryFn: async () => {
      if (!debouncedQuery.trim()) return null
      
      const query: SearchQuery = {
        term: debouncedQuery,
        filters: searchState.filters,
        sortBy: searchState.sortBy as any,
        sortOrder: searchState.sortOrder,
        limit: 50
      }
      
      return GlobalSearchService.search(query)
    },
    enabled: debouncedQuery.trim().length > 0,
    staleTime: 30000
  })

  // Quick search for autocomplete
  const { data: quickResults } = useQuery({
    queryKey: ['quick-search', debouncedQuery],
    queryFn: () => GlobalSearchService.quickSearch(debouncedQuery, 5),
    enabled: debouncedQuery.trim().length > 0 && debouncedQuery.trim().length < 3,
    staleTime: 60000
  })

  // Focus search input when modal opens
  useEffect(() => {
    if (isOpen && searchInputRef.current) {
      setTimeout(() => {
        searchInputRef.current?.focus()
      }, 100)
    }
  }, [isOpen])

  // Update query when initialQuery changes
  useEffect(() => {
    if (initialQuery) {
      setSearchState(prev => ({ ...prev, query: initialQuery }))
    }
  }, [initialQuery])

  // Event handlers
  const handleQueryChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchState(prev => ({ ...prev, query: e.target.value }))
  }, [])

  const handleFiltersChange = useCallback((filters: SearchFilters) => {
    setSearchState(prev => ({ ...prev, filters }))
  }, [])

  const handleSortChange = useCallback((sortBy: string) => {
    setSearchState(prev => ({ ...prev, sortBy }))
  }, [])

  const handleResultSelect = useCallback((result: SearchResult) => {
    setSearchState(prev => ({ ...prev, selectedResult: result.id }))
  }, [])

  const handleResultNavigate = useCallback((result: SearchResult) => {
    if (onNavigate) {
      onNavigate(result.path)
    } else {
      navigate(result.path)
    }
    onClose()
    toast.success(`Navigated to ${result.title}`)
  }, [navigate, onNavigate, onClose])

  const clearSearch = useCallback(() => {
    setSearchState(prev => ({ 
      ...prev, 
      query: '',
      selectedResult: null 
    }))
  }, [])

  // Keyboard navigation
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      onClose()
    } else if (e.key === 'Enter' && searchResponse?.results.length) {
      const selectedIndex = searchResponse.results.findIndex(r => r.id === searchState.selectedResult)
      const targetResult = selectedIndex >= 0 
        ? searchResponse.results[selectedIndex]
        : searchResponse.results[0]
      handleResultNavigate(targetResult)
    }
  }, [onClose, searchResponse, searchState.selectedResult, handleResultNavigate])

  // Memoized results for performance
  const displayResults = useMemo(() => {
    return searchResponse?.results || quickResults || []
  }, [searchResponse, quickResults])

  const showSuggestions = useMemo(() => {
    return searchState.query.length > 0 && searchState.query.length < 3 && quickResults?.length
  }, [searchState.query, quickResults])

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent 
        className="max-w-4xl max-h-[80vh] overflow-hidden p-0"
        onKeyDown={handleKeyDown}
      >
        <DialogHeader className="px-6 py-4 border-b">
          <div className="flex items-center gap-3">
            <Search className="h-5 w-5 text-muted-foreground" />
            <DialogTitle>Global Search</DialogTitle>
          </div>
        </DialogHeader>

        {/* Search input */}
        <div className="px-6 py-4 border-b">
          <div className="relative">
            <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Input
              ref={searchInputRef}
              placeholder="Search packages, adapters, flows, and dependencies..."
              value={searchState.query}
              onChange={handleQueryChange}
              className="pl-10 pr-10"
            />
            {searchState.query && (
              <Button
                variant="ghost"
                size="sm"
                onClick={clearSearch}
                className="absolute right-1 top-1 h-8 w-8 p-0"
              >
                <X className="h-4 w-4" />
              </Button>
            )}
          </div>
          
          {/* Search controls */}
          <div className="flex items-center justify-between mt-3">
            <div className="flex items-center gap-2">
              {isLoading && (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Searching...
                </div>
              )}
              
              {searchResponse && (
                <div className="text-sm text-muted-foreground">
                  {searchResponse.totalCount} results in {searchResponse.searchTime}ms
                </div>
              )}
            </div>
            
            <div className="flex items-center gap-2">
              <Select value={searchState.sortBy} onValueChange={handleSortChange}>
                <SelectTrigger className="w-32">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="relevance">Relevance</SelectItem>
                  <SelectItem value="name">Name</SelectItem>
                  <SelectItem value="date">Date</SelectItem>
                  <SelectItem value="package">Package</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </div>

        <div className="flex flex-1 overflow-hidden">
          {/* Results panel */}
          <div className="flex-1 overflow-y-auto">
            {showSuggestions && (
              <div className="p-4 bg-muted/30">
                <h4 className="text-sm font-medium mb-2">Quick suggestions:</h4>
                <div className="space-y-1">
                  {quickResults?.slice(0, 3).map(result => (
                    <Button
                      key={result.id}
                      variant="ghost"
                      size="sm"
                      onClick={() => handleResultNavigate(result)}
                      className="w-full justify-start text-left"
                    >
                      <Clock className="h-3 w-3 mr-2" />
                      {result.title}
                    </Button>
                  ))}
                </div>
              </div>
            )}

            {displayResults.length > 0 ? (
              <div className="divide-y">
                {displayResults.map(result => (
                  <SearchResultItem
                    key={result.id}
                    result={result}
                    isSelected={result.id === searchState.selectedResult}
                    onSelect={handleResultSelect}
                    onNavigate={handleResultNavigate}
                  />
                ))}
              </div>
            ) : searchState.query.length > 0 && !isLoading ? (
              <div className="flex items-center justify-center p-12">
                <div className="text-center">
                  <Search className="h-12 w-12 text-muted-foreground mx-auto mb-4 opacity-50" />
                  <h4 className="font-medium mb-2">No results found</h4>
                  <p className="text-muted-foreground text-sm">
                    Try adjusting your search terms or filters
                  </p>
                  {searchResponse?.suggestions && searchResponse.suggestions.length > 0 && (
                    <div className="mt-4">
                      <p className="text-sm text-muted-foreground mb-2">Did you mean:</p>
                      <div className="flex flex-wrap gap-1 justify-center">
                        {searchResponse.suggestions.slice(0, 3).map((suggestion, index) => (
                          <Button
                            key={index}
                            variant="outline"
                            size="sm"
                            onClick={() => setSearchState(prev => ({ ...prev, query: suggestion }))}
                          >
                            {suggestion}
                          </Button>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="flex items-center justify-center p-12">
                <div className="text-center">
                  <Search className="h-12 w-12 text-muted-foreground mx-auto mb-4 opacity-50" />
                  <h4 className="font-medium mb-2">Start typing to search</h4>
                  <p className="text-muted-foreground text-sm">
                    Search across packages, adapters, flows, and dependencies
                  </p>
                </div>
              </div>
            )}
          </div>

          {/* Filters sidebar */}
          {searchResponse?.facets && (
            <div className="w-80 border-l bg-muted/20">
              <SearchFiltersPanel
                filters={searchState.filters}
                facets={searchResponse.facets}
                onFiltersChange={handleFiltersChange}
              />
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

export default GlobalSearchModal