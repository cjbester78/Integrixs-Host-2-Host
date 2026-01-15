/**
 * Package Management Type Definitions
 * 
 * Comprehensive type system for package management following OOP principles.
 * Provides strong typing for all package-related operations and data structures.
 * 
 * @author Claude Code  
 * @since Package Management Frontend V1.0
 */

// Core Package Types following domain modeling principles

/**
 * Package status enumeration for type safety.
 */
export enum PackageStatus {
  ACTIVE = 'ACTIVE'
}

/**
 * Package type enumeration for categorization.
 */
export enum PackageType {
  INTEGRATION = 'INTEGRATION',
  UTILITY = 'UTILITY',
  TEMPLATE = 'TEMPLATE',
  CUSTOM = 'CUSTOM'
}

/**
 * Asset type enumeration for package contents.
 */
export enum AssetType {
  ADAPTER = 'ADAPTER',
  FLOW = 'FLOW'
}

/**
 * Core integration package interface.
 * Represents a container for related adapters and flows.
 */
export interface IntegrationPackage {
  id: string
  name: string
  description?: string
  version: string
  packageType: PackageType
  status: PackageStatus
  configuration: Record<string, unknown>
  tags: string[]
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  // Additional properties for compatibility
  type?: string
  adapterCount?: number
  flowCount?: number
}

/**
 * Package summary with asset statistics.
 * Used for dashboard and listing displays.
 */
export interface PackageSummary extends IntegrationPackage {
  adapterCount: number
  activeAdapterCount: number
  flowCount: number
  activeFlowCount: number
  dependencyCount: number
  totalAssetCount: number
  totalActiveAssetCount: number
  hasAssets: boolean
  hasActiveAssets: boolean
}

/**
 * Package asset dependency relationship.
 */
export interface PackageAssetDependency {
  id: string
  packageId: string
  assetType: AssetType
  assetId: string
  dependsOnAssetType: AssetType
  dependsOnAssetId: string
  dependentAssetId?: string  // Alias for backward compatibility
  dependencyType: string
  createdAt: string
  createdBy: string
}

/**
 * Generic package asset representation.
 * Unified interface for adapters and flows within packages.
 */
export interface PackageAsset {
  id?: string
  assetType: AssetType
  assetId: string
  name: string
  description?: string
  packageId: string
  active: boolean
  enabled?: boolean
  status?: string
  createdAt: string
  updatedAt: string
  // Adapter-specific properties
  adapterType?: string
  direction?: string
  // Flow-specific properties
  flowType?: string
  sourceAdapterId?: string
  targetAdapterId?: string
  deploymentStatus?: string
  executionCount?: number
}

/**
 * Package container with all assets and dependencies.
 * Complete package structure for workspace operations.
 */
export interface PackageContainer {
  packageInfo: IntegrationPackage
  package: IntegrationPackage  // Alias for backward compatibility
  adapters: PackageAsset[]
  flows: PackageAsset[]
  dependencies: PackageAssetDependency[]
  totalAssetCount: number
  hasAssets: boolean
}

/**
 * Package asset summary with statistics.
 */
export interface PackageAssetsSummary {
  packageInfo: IntegrationPackage
  totalAdapters: number
  activeAdapters: number
  totalFlows: number
  activeFlows: number
  totalDependencies: number
  totalAssets: number
  totalActiveAssets: number
}

/**
 * Circular dependency detection result.
 */
export interface CircularDependency {
  cycle: string[]
  cycleLength: number
}

/**
 * Package dependency validation result.
 */
export interface PackageDependencyValidation {
  packageId: string
  isValid: boolean
  circularDependencies: CircularDependency[]
  hasCircularDependencies: boolean
}

/**
 * Package deployment readiness assessment.
 */
export interface PackageDeploymentReadiness {
  packageInfo: IntegrationPackage
  isReady: boolean
  issues: string[]
  readinessMetrics: Record<string, unknown>
}

/**
 * Asset dependency information.
 */
export interface AssetDependencyInfo {
  assetType: AssetType
  assetId: string
  dependencies: PackageAssetDependency[]
  dependents: PackageAssetDependency[]
  hasDependencies: boolean
  hasDependents: boolean
}

// Request/Response DTOs following API contract patterns

/**
 * Package creation request DTO.
 */
export interface CreatePackageRequest {
  name: string
  description?: string
  version: string
  packageType: PackageType
  configuration?: Record<string, unknown>
  tags?: string[]
}

/**
 * Package update request DTO.
 */
export interface UpdatePackageRequest {
  name?: string
  description?: string
  version?: string
  packageType?: PackageType
  status?: PackageStatus
  configuration?: Record<string, unknown>
  tags?: string[]
}


/**
 * Package search and filter criteria.
 */
export interface PackageSearchCriteria {
  searchTerm?: string
  packageTypes?: PackageType[]
  statuses?: PackageStatus[]
  tags?: string[]
  createdBy?: string
  sortBy?: 'name' | 'createdAt' | 'updatedAt' | 'totalAssetCount'
  sortOrder?: 'asc' | 'desc'
  limit?: number
  offset?: number
}

/**
 * Package search result with metadata.
 */
export interface PackageSearchResult {
  packages: PackageSummary[]
  totalCount: number
  hasNextPage: boolean
  hasPreviousPage: boolean
}

/**
 * Asset search criteria within packages.
 */
export interface AssetSearchCriteria {
  searchTerm?: string
  assetTypes?: AssetType[]
  packageIds?: string[]
  activeOnly?: boolean
}

/**
 * Move asset between packages request.
 */
export interface MoveAssetRequest {
  targetPackageId: string
  reason?: string
}

// UI State Management Types following state pattern

/**
 * Package workspace UI state.
 */
export interface PackageWorkspaceState {
  currentPackageId?: string
  selectedAssets: Set<string>
  activeTab: 'overview' | 'adapters' | 'flows' | 'dependencies'
  isLoading: boolean
  error?: string
}

/**
 * Package library UI state.
 */
export interface PackageLibraryState {
  searchCriteria: PackageSearchCriteria
  selectedPackages: Set<string>
  viewMode: 'grid' | 'list'
  isCreating: boolean
  showCreateModal: boolean
}

/**
 * Package statistics for dashboard display.
 */
export interface PackageStatistics {
  totalPackages: number
  activePackages: number
  totalAssets: number
  averageAssetsPerPackage: number
  packagesByType: Record<PackageType, number>
  recentActivity: PackageActivity[]
}

/**
 * Package activity for audit trail.
 */
export interface PackageActivity {
  id: string
  packageId: string
  packageName: string
  action: 'CREATED' | 'UPDATED' | 'ASSET_ADDED' | 'ASSET_REMOVED' | 'ASSET_MOVED'
  description: string
  userId: string
  userDisplayName: string
  timestamp: string
  metadata?: Record<string, unknown>
}

// Error Types following exception hierarchy

/**
 * Package-related error types.
 */
export enum PackageErrorType {
  NOT_FOUND = 'NOT_FOUND',
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  DEPENDENCY_ERROR = 'DEPENDENCY_ERROR',
  PERMISSION_ERROR = 'PERMISSION_ERROR',
  CONFLICT_ERROR = 'CONFLICT_ERROR',
  NETWORK_ERROR = 'NETWORK_ERROR'
}

/**
 * Package operation error.
 */
export interface PackageError {
  type: PackageErrorType
  message: string
  details?: Record<string, unknown>
  packageId?: string
  timestamp: string
}

/**
 * Package validation result.
 */
export interface PackageValidationResult {
  isValid: boolean
  errors: string[]
  warnings: string[]
}

// API Response Types following consistent response pattern

/**
 * Standard API response wrapper.
 */
export interface ApiResponse<T> {
  success: boolean
  message: string
  data?: T
  error?: string
  timestamp?: string
}

/**
 * Paginated response wrapper.
 */
export interface PaginatedResponse<T> {
  items: T[]
  totalCount: number
  pageSize: number
  currentPage: number
  totalPages: number
  hasNextPage: boolean
  hasPreviousPage: boolean
}

/**
 * Package operation result for async operations.
 */
export interface PackageOperationResult {
  operationId: string
  packageId: string
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
  startedAt: string
  completedAt?: string
  result?: unknown
  error?: string
}