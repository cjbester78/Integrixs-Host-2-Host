/**
 * Global Search Service
 * 
 * Comprehensive search service for package assets using advanced search algorithms
 * and OOP design patterns. Implements Search Strategy Pattern, Indexer Pattern,
 * and Result Ranking Pattern for efficient and flexible search functionality.
 * 
 * @author Claude Code
 * @since Package Management Frontend V2.0
 */

import { packageService } from '@/services/packageService'
import type { IntegrationPackage, PackageContainer } from '@/types/package'

/**
 * Search result interface following data transfer object pattern
 */
export interface SearchResult {
  id: string
  type: 'package' | 'adapter' | 'flow' | 'dependency'
  title: string
  subtitle?: string
  description?: string
  packageId: string
  packageName: string
  score: number
  path: string
  metadata: Record<string, any>
  tags: string[]
  status?: string
  lastModified?: Date
}

/**
 * Search filter criteria interface
 */
export interface SearchFilters {
  types: string[]
  packages: string[]
  status: string[]
  dateRange?: {
    start: Date
    end: Date
  }
  minScore?: number
}

/**
 * Search query interface following command pattern
 */
export interface SearchQuery {
  term: string
  filters: SearchFilters
  limit?: number
  offset?: number
  sortBy?: 'relevance' | 'name' | 'date' | 'package'
  sortOrder?: 'asc' | 'desc'
}

/**
 * Search response interface
 */
export interface SearchResponse {
  results: SearchResult[]
  totalCount: number
  searchTime: number
  suggestions: string[]
  facets: {
    types: { [key: string]: number }
    packages: { [key: string]: number }
    status: { [key: string]: number }
  }
}

/**
 * Searchable item interface for internal indexing
 */
interface SearchableItem {
  id: string
  type: string
  title: string
  content: string[]
  tags: string[]
  metadata: Record<string, any>
  packageId: string
  packageName: string
  status?: string
  lastModified?: Date
}

/**
 * Search index builder following builder pattern
 */
class SearchIndexBuilder {
  private items: SearchableItem[] = []

  /**
   * Adds package items to search index
   */
  addPackageItems(packages: IntegrationPackage[]): SearchIndexBuilder {
    packages.forEach(pkg => {
      this.items.push({
        id: pkg.id,
        type: 'package',
        title: pkg.name,
        content: [
          pkg.name,
          pkg.description || '',
          pkg.version,
          ...(pkg.tags || [])
        ],
        tags: pkg.tags || [],
        metadata: {
          version: pkg.version,
          type: pkg.type,
          status: pkg.status
        },
        packageId: pkg.id,
        packageName: pkg.name,
        status: pkg.status,
        lastModified: new Date(pkg.updatedAt)
      })
    })
    return this
  }

  /**
   * Adds adapter items to search index
   */
  addAdapterItems(container: PackageContainer): SearchIndexBuilder {
    container.adapters.forEach(adapter => {
      this.items.push({
        id: adapter.id,
        type: 'adapter',
        title: adapter.name,
        content: [
          adapter.name,
          adapter.adapterType,
          adapter.description || '',
          adapter.direction || ''
        ],
        tags: [`type:${adapter.adapterType}`, `direction:${adapter.direction}`],
        metadata: {
          adapterType: adapter.adapterType,
          direction: adapter.direction,
          enabled: adapter.enabled,
          status: adapter.status
        },
        packageId: container.package.id,
        packageName: container.package.name,
        status: adapter.enabled ? 'active' : 'inactive',
        lastModified: new Date(adapter.updatedAt)
      })
    })
    return this
  }

  /**
   * Adds flow items to search index
   */
  addFlowItems(container: PackageContainer): SearchIndexBuilder {
    container.flows.forEach(flow => {
      this.items.push({
        id: flow.id,
        type: 'flow',
        title: flow.name,
        content: [
          flow.name,
          flow.description || '',
          flow.flowType || '',
          flow.sourceAdapterId || '',
          flow.targetAdapterId || ''
        ],
        tags: [`type:${flow.flowType}`, `status:${flow.deploymentStatus}`],
        metadata: {
          flowType: flow.flowType,
          deploymentStatus: flow.deploymentStatus,
          sourceAdapterId: flow.sourceAdapterId,
          targetAdapterId: flow.targetAdapterId,
          executionCount: flow.executionCount
        },
        packageId: container.package.id,
        packageName: container.package.name,
        status: flow.deploymentStatus || 'draft',
        lastModified: new Date(flow.updatedAt)
      })
    })
    return this
  }

  /**
   * Adds dependency items to search index
   */
  addDependencyItems(container: PackageContainer): SearchIndexBuilder {
    container.dependencies.forEach(dependency => {
      this.items.push({
        id: dependency.id,
        type: 'dependency',
        title: `${dependency.dependentAssetId} → ${dependency.dependsOnAssetId}`,
        content: [
          dependency.dependentAssetId,
          dependency.dependsOnAssetId,
          dependency.dependencyType || ''
        ],
        tags: [`type:${dependency.dependencyType}`],
        metadata: {
          dependencyType: dependency.dependencyType,
          dependentAssetId: dependency.dependentAssetId,
          dependsOnAssetId: dependency.dependsOnAssetId
        },
        packageId: container.package.id,
        packageName: container.package.name,
        lastModified: new Date(dependency.createdAt)
      })
    })
    return this
  }

  /**
   * Builds the final search index
   */
  build(): SearchableItem[] {
    return this.items
  }
}

/**
 * Search scorer following strategy pattern
 */
class SearchScorer {
  /**
   * Calculates relevance score for search result
   */
  static calculateScore(item: SearchableItem, searchTerm: string): number {
    const term = searchTerm.toLowerCase()
    let score = 0

    // Exact title match gets highest score
    if (item.title.toLowerCase() === term) {
      score += 100
    }
    // Title starts with term
    else if (item.title.toLowerCase().startsWith(term)) {
      score += 80
    }
    // Title contains term
    else if (item.title.toLowerCase().includes(term)) {
      score += 60
    }

    // Content matches
    item.content.forEach(content => {
      const contentLower = content.toLowerCase()
      if (contentLower === term) {
        score += 40
      } else if (contentLower.includes(term)) {
        score += 20
      }
    })

    // Tag matches
    item.tags.forEach(tag => {
      if (tag.toLowerCase().includes(term)) {
        score += 30
      }
    })

    // Boost score based on item type priority
    switch (item.type) {
      case 'package':
        score *= 1.2
        break
      case 'adapter':
      case 'flow':
        score *= 1.1
        break
      default:
        score *= 1.0
    }

    // Boost recent items
    if (item.lastModified) {
      const daysSinceUpdate = (Date.now() - item.lastModified.getTime()) / (1000 * 60 * 60 * 24)
      if (daysSinceUpdate < 7) {
        score *= 1.1
      }
    }

    return Math.round(score)
  }
}

/**
 * Search filter processor following processor pattern
 */
class SearchFilterProcessor {
  /**
   * Applies filters to search results
   */
  static applyFilters(items: SearchableItem[], filters: SearchFilters): SearchableItem[] {
    let filtered = items

    // Type filter
    if (filters.types.length > 0 && !filters.types.includes('all')) {
      filtered = filtered.filter(item => filters.types.includes(item.type))
    }

    // Package filter
    if (filters.packages.length > 0 && !filters.packages.includes('all')) {
      filtered = filtered.filter(item => filters.packages.includes(item.packageId))
    }

    // Status filter
    if (filters.status.length > 0 && !filters.status.includes('all')) {
      filtered = filtered.filter(item => 
        item.status && filters.status.includes(item.status)
      )
    }

    // Date range filter
    if (filters.dateRange && filters.dateRange.start && filters.dateRange.end) {
      filtered = filtered.filter(item => {
        if (!item.lastModified) return false
        return item.lastModified >= filters.dateRange!.start && 
               item.lastModified <= filters.dateRange!.end
      })
    }

    // Minimum score filter
    if (filters.minScore) {
      filtered = filtered.filter(item => {
        const score = SearchScorer.calculateScore(item, '')
        return score >= filters.minScore!
      })
    }

    return filtered
  }
}

/**
 * Search result transformer following transformer pattern
 */
class SearchResultTransformer {
  /**
   * Transforms searchable items to search results
   */
  static transformToResults(items: SearchableItem[], searchTerm: string): SearchResult[] {
    return items.map(item => ({
      id: item.id,
      type: item.type as SearchResult['type'],
      title: item.title,
      subtitle: this.generateSubtitle(item),
      description: this.generateDescription(item),
      packageId: item.packageId,
      packageName: item.packageName,
      score: SearchScorer.calculateScore(item, searchTerm),
      path: this.generatePath(item),
      metadata: item.metadata,
      tags: item.tags,
      status: item.status,
      lastModified: item.lastModified
    }))
  }

  /**
   * Generates subtitle for search result
   */
  private static generateSubtitle(item: SearchableItem): string {
    switch (item.type) {
      case 'package':
        return `Package • ${item.metadata.type || 'Integration'}`
      case 'adapter':
        return `${item.metadata.adapterType} Adapter • ${item.metadata.direction || 'Bidirectional'}`
      case 'flow':
        return `${item.metadata.flowType || 'Integration'} Flow • ${item.status}`
      case 'dependency':
        return `${item.metadata.dependencyType} Dependency`
      default:
        return item.type
    }
  }

  /**
   * Generates description for search result
   */
  private static generateDescription(item: SearchableItem): string {
    const content = item.content.filter(c => c && c !== item.title).join(' • ')
    return content.substring(0, 150) + (content.length > 150 ? '...' : '')
  }

  /**
   * Generates navigation path for search result
   */
  private static generatePath(item: SearchableItem): string {
    const basePath = `/packages/${item.packageId}/workspace`
    
    switch (item.type) {
      case 'package':
        return basePath
      case 'adapter':
        return `${basePath}#adapters`
      case 'flow':
        return `${basePath}#flows`
      case 'dependency':
        return `${basePath}#dependencies`
      default:
        return basePath
    }
  }
}

/**
 * Search suggestion generator following generator pattern
 */
class SearchSuggestionGenerator {
  /**
   * Generates search suggestions based on query and results
   */
  static generateSuggestions(searchTerm: string, allItems: SearchableItem[]): string[] {
    const suggestions = new Set<string>()
    const term = searchTerm.toLowerCase()

    // Add partial matches from titles
    allItems.forEach(item => {
      const title = item.title.toLowerCase()
      if (title.includes(term) && title !== term) {
        suggestions.add(item.title)
      }
      
      // Add content suggestions
      item.content.forEach(content => {
        const contentLower = content.toLowerCase()
        if (contentLower.includes(term) && contentLower !== term && contentLower.length < 50) {
          suggestions.add(content)
        }
      })
      
      // Add tag suggestions
      item.tags.forEach(tag => {
        if (tag.toLowerCase().includes(term)) {
          suggestions.add(tag.replace(':', ': '))
        }
      })
    })

    return Array.from(suggestions).slice(0, 5)
  }
}

/**
 * Facet calculator following calculator pattern
 */
class FacetCalculator {
  /**
   * Calculates search facets for filtering
   */
  static calculateFacets(items: SearchResult[]): SearchResponse['facets'] {
    const facets = {
      types: {} as { [key: string]: number },
      packages: {} as { [key: string]: number },
      status: {} as { [key: string]: number }
    }

    items.forEach(item => {
      // Type facets
      facets.types[item.type] = (facets.types[item.type] || 0) + 1
      
      // Package facets
      facets.packages[item.packageName] = (facets.packages[item.packageName] || 0) + 1
      
      // Status facets
      if (item.status) {
        facets.status[item.status] = (facets.status[item.status] || 0) + 1
      }
    })

    return facets
  }
}

/**
 * Main Global Search Service following service pattern and facade pattern.
 * Provides comprehensive search functionality across all package assets.
 */
export class GlobalSearchService {
  private static searchIndex: SearchableItem[] = []
  private static lastIndexUpdate = 0
  private static readonly INDEX_TTL = 5 * 60 * 1000 // 5 minutes

  /**
   * Builds or updates the search index
   */
  private static async buildSearchIndex(): Promise<void> {
    const now = Date.now()
    if (now - this.lastIndexUpdate < this.INDEX_TTL && this.searchIndex.length > 0) {
      return
    }

    try {
      const packagesResult = await packageService.getPackages()
      const packages = packagesResult.packages
      const builder = new SearchIndexBuilder()
      
      // Add packages to index
      builder.addPackageItems(packages)
      
      // Add assets from each package container
      for (const pkg of packages) {
        try {
          const container = await packageService.getPackageContainer(pkg.id)
          builder
            .addAdapterItems(container)
            .addFlowItems(container)
            .addDependencyItems(container)
        } catch (error) {
          console.warn(`Failed to index package ${pkg.id}:`, error)
        }
      }
      
      this.searchIndex = builder.build()
      this.lastIndexUpdate = now
    } catch (error) {
      console.error('Failed to build search index:', error)
      throw new Error('Search index building failed')
    }
  }

  /**
   * Performs comprehensive search across all package assets
   */
  static async search(query: SearchQuery): Promise<SearchResponse> {
    const startTime = Date.now()
    
    try {
      // Ensure search index is up to date
      await this.buildSearchIndex()
      
      let results: SearchableItem[] = []
      
      // If no search term, return all items (filtered)
      if (!query.term.trim()) {
        results = this.searchIndex
      } else {
        // Perform text search
        results = this.searchIndex.filter(item => {
          const score = SearchScorer.calculateScore(item, query.term)
          return score > 0
        })
      }
      
      // Apply filters
      results = SearchFilterProcessor.applyFilters(results, query.filters)
      
      // Transform to search results with scores
      const searchResults = SearchResultTransformer.transformToResults(results, query.term)
      
      // Sort results
      this.sortResults(searchResults, query.sortBy || 'relevance', query.sortOrder || 'desc')
      
      // Apply pagination
      const offset = query.offset || 0
      const limit = query.limit || 50
      const paginatedResults = searchResults.slice(offset, offset + limit)
      
      // Generate suggestions and facets
      const suggestions = SearchSuggestionGenerator.generateSuggestions(query.term, results)
      const facets = FacetCalculator.calculateFacets(searchResults)
      
      const searchTime = Date.now() - startTime
      
      return {
        results: paginatedResults,
        totalCount: searchResults.length,
        searchTime,
        suggestions,
        facets
      }
    } catch (error) {
      console.error('Search failed:', error)
      throw new Error('Search operation failed')
    }
  }

  /**
   * Sorts search results based on criteria
   */
  private static sortResults(
    results: SearchResult[], 
    sortBy: string, 
    sortOrder: string
  ): void {
    results.sort((a, b) => {
      let comparison = 0
      
      switch (sortBy) {
        case 'relevance':
          comparison = b.score - a.score
          break
        case 'name':
          comparison = a.title.localeCompare(b.title)
          break
        case 'date': {
          const dateA = a.lastModified?.getTime() || 0
          const dateB = b.lastModified?.getTime() || 0
          comparison = dateB - dateA
          break
        }
        case 'package':
          comparison = a.packageName.localeCompare(b.packageName)
          break
        default:
          comparison = b.score - a.score
      }
      
      return sortOrder === 'asc' ? comparison : -comparison
    })
  }

  /**
   * Quick search for autocomplete functionality
   */
  static async quickSearch(term: string, limit = 10): Promise<SearchResult[]> {
    if (!term.trim()) return []
    
    try {
      await this.buildSearchIndex()
      
      const results = this.searchIndex
        .map(item => ({
          item,
          score: SearchScorer.calculateScore(item, term)
        }))
        .filter(({ score }) => score > 20)
        .sort((a, b) => b.score - a.score)
        .slice(0, limit)
        .map(({ item }) => SearchResultTransformer.transformToResults([item], term)[0])
      
      return results
    } catch (error) {
      console.error('Quick search failed:', error)
      return []
    }
  }

  /**
   * Invalidates search index to force rebuild
   */
  static invalidateIndex(): void {
    this.searchIndex = []
    this.lastIndexUpdate = 0
  }

  /**
   * Gets search statistics
   */
  static async getSearchStats(): Promise<{
    totalItems: number
    byType: { [key: string]: number }
    byPackage: { [key: string]: number }
    lastUpdated: Date
  }> {
    await this.buildSearchIndex()
    
    const stats = {
      totalItems: this.searchIndex.length,
      byType: {} as { [key: string]: number },
      byPackage: {} as { [key: string]: number },
      lastUpdated: new Date(this.lastIndexUpdate)
    }
    
    this.searchIndex.forEach(item => {
      stats.byType[item.type] = (stats.byType[item.type] || 0) + 1
      stats.byPackage[item.packageName] = (stats.byPackage[item.packageName] || 0) + 1
    })
    
    return stats
  }
}

export default GlobalSearchService