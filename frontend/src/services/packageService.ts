/**
 * Package Management API Service
 * 
 * Service layer for package management operations following OOP principles.
 * Provides abstracted API calls with error handling and type safety.
 * 
 * @author Claude Code
 * @since Package Management Frontend V1.0
 */

import { api } from '@/lib/api'
import {
  IntegrationPackage,
  PackageSummary,
  PackageContainer,
  PackageAssetsSummary,
  PackageDependencyValidation,
  PackageDeploymentReadiness,
  CreatePackageRequest,
  UpdatePackageRequest,
  ArchivePackageRequest,
  PackageSearchCriteria,
  PackageSearchResult,
  AssetSearchCriteria,
  PackageAsset,
  MoveAssetRequest,
  PackageStatistics,
  ApiResponse,
  PaginatedResponse,
  AssetType,
  PackageError,
  PackageErrorType
} from '@/types/package'

/**
 * Package management service class following service layer pattern.
 * Encapsulates all package-related API operations with error handling.
 */
export class PackageService {
  private readonly baseUrl = '/api/packages'
  
  // Package CRUD Operations following repository pattern
  
  /**
   * Retrieve all packages with optional filtering.
   * 
   * @param criteria Search and filter criteria
   * @returns Promise<PackageSearchResult>
   */
  public async getPackages(criteria?: PackageSearchCriteria): Promise<PackageSearchResult> {
    try {
      const params = this.buildSearchParams(criteria)
      const response = await api.get<ApiResponse<PaginatedResponse<PackageSummary>>>(
        `${this.baseUrl}`, 
        { params }
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.NETWORK_ERROR, response.data.message || 'Failed to fetch packages')
      }
      
      const paginatedData = response.data.data
      return {
        packages: paginatedData.items,
        totalCount: paginatedData.totalCount,
        hasNextPage: paginatedData.hasNextPage,
        hasPreviousPage: paginatedData.hasPreviousPage
      }
    } catch (error) {
      throw this.handleApiError(error, 'Failed to retrieve packages')
    }
  }
  
  /**
   * Retrieve package by ID.
   * 
   * @param packageId Package UUID
   * @returns Promise<IntegrationPackage>
   */
  public async getPackageById(packageId: string): Promise<IntegrationPackage> {
    try {
      this.validatePackageId(packageId)
      
      const response = await api.get<ApiResponse<IntegrationPackage>>(
        `${this.baseUrl}/${packageId}`
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.NOT_FOUND, `Package not found: ${packageId}`)
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, `Failed to retrieve package: ${packageId}`)
    }
  }
  
  /**
   * Get package summary with asset counts.
   * 
   * @param packageId Package UUID
   * @returns Promise<PackageSummary>
   */
  public async getPackageSummary(packageId: string): Promise<PackageSummary> {
    try {
      this.validatePackageId(packageId)
      
      const response = await api.get<ApiResponse<PackageSummary>>(
        `${this.baseUrl}/${packageId}/summary`
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.NOT_FOUND, `Package summary not found: ${packageId}`)
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, `Failed to retrieve package summary: ${packageId}`)
    }
  }
  
  /**
   * Create new package.
   * 
   * @param request Package creation request
   * @returns Promise<IntegrationPackage>
   */
  public async createPackage(request: CreatePackageRequest): Promise<IntegrationPackage> {
    try {
      this.validateCreateRequest(request)
      
      const response = await api.post<ApiResponse<IntegrationPackage>>(
        this.baseUrl,
        request
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, response.data.message || 'Failed to create package')
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, 'Failed to create package')
    }
  }
  
  /**
   * Update existing package.
   * 
   * @param packageId Package UUID
   * @param request Package update request
   * @returns Promise<IntegrationPackage>
   */
  public async updatePackage(packageId: string, request: UpdatePackageRequest): Promise<IntegrationPackage> {
    try {
      this.validatePackageId(packageId)
      this.validateUpdateRequest(request)
      
      const response = await api.put<ApiResponse<IntegrationPackage>>(
        `${this.baseUrl}/${packageId}`,
        request
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, response.data.message || 'Failed to update package')
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, `Failed to update package: ${packageId}`)
    }
  }
  
  /**
   * Archive package (soft delete).
   * 
   * @param packageId Package UUID
   * @param request Archive request with reason
   * @returns Promise<boolean>
   */
  public async archivePackage(packageId: string, request: ArchivePackageRequest): Promise<boolean> {
    try {
      this.validatePackageId(packageId)
      this.validateArchiveRequest(request)
      
      const response = await api.delete<ApiResponse<void>>(
        `${this.baseUrl}/${packageId}/archive`,
        { data: request }
      )
      
      return response.data.success
    } catch (error) {
      throw this.handleApiError(error, `Failed to archive package: ${packageId}`)
    }
  }
  
  /**
   * Permanently delete package.
   * WARNING: This operation cannot be undone.
   * 
   * @param packageId Package UUID
   * @returns Promise<boolean>
   */
  public async deletePackage(packageId: string): Promise<boolean> {
    try {
      this.validatePackageId(packageId)
      
      const response = await api.delete<ApiResponse<void>>(
        `${this.baseUrl}/${packageId}`
      )
      
      return response.data.success
    } catch (error) {
      throw this.handleApiError(error, `Failed to delete package: ${packageId}`)
    }
  }
  
  // Package Container and Asset Operations
  
  /**
   * Get complete package container with all assets.
   * 
   * @param packageId Package UUID
   * @returns Promise<PackageContainer>
   */
  public async getPackageContainer(packageId: string): Promise<PackageContainer> {
    try {
      this.validatePackageId(packageId)
      
      const response = await api.get<ApiResponse<PackageContainer>>(
        `${this.baseUrl}/${packageId}/container`
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.NOT_FOUND, `Package container not found: ${packageId}`)
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, `Failed to retrieve package container: ${packageId}`)
    }
  }
  
  /**
   * Get package assets summary with statistics.
   * 
   * @param packageId Package UUID
   * @returns Promise<PackageAssetsSummary>
   */
  public async getPackageAssetsSummary(packageId: string): Promise<PackageAssetsSummary> {
    try {
      this.validatePackageId(packageId)
      
      const response = await api.get<ApiResponse<PackageAssetsSummary>>(
        `${this.baseUrl}/${packageId}/assets/summary`
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.NOT_FOUND, `Package assets summary not found: ${packageId}`)
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, `Failed to retrieve package assets summary: ${packageId}`)
    }
  }
  
  /**
   * Search assets across packages.
   * 
   * @param criteria Asset search criteria
   * @returns Promise<PackageAsset[]>
   */
  public async searchAssets(criteria: AssetSearchCriteria): Promise<PackageAsset[]> {
    try {
      const params = this.buildAssetSearchParams(criteria)
      const response = await api.get<ApiResponse<PackageAsset[]>>(
        `${this.baseUrl}/assets/search`,
        { params }
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.NETWORK_ERROR, 'Failed to search assets')
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, 'Failed to search assets')
    }
  }
  
  /**
   * Move asset between packages.
   * 
   * @param packageId Source package UUID
   * @param assetType Asset type
   * @param assetId Asset UUID
   * @param request Move request
   * @returns Promise<boolean>
   */
  public async moveAsset(packageId: string, assetType: AssetType, assetId: string, request: MoveAssetRequest): Promise<boolean> {
    try {
      this.validatePackageId(packageId)
      this.validatePackageId(request.targetPackageId)
      
      const response = await api.put<ApiResponse<void>>(
        `${this.baseUrl}/${packageId}/assets/${assetType}/${assetId}/move`,
        request
      )
      
      return response.data.success
    } catch (error) {
      throw this.handleApiError(error, `Failed to move ${assetType.toLowerCase()}: ${assetId}`)
    }
  }
  
  // Package Analytics and Monitoring
  
  /**
   * Get package dependency validation.
   * 
   * @param packageId Package UUID
   * @returns Promise<PackageDependencyValidation>
   */
  public async validateDependencies(packageId: string): Promise<PackageDependencyValidation> {
    try {
      this.validatePackageId(packageId)
      
      const response = await api.get<ApiResponse<PackageDependencyValidation>>(
        `${this.baseUrl}/${packageId}/dependencies/validate`
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.DEPENDENCY_ERROR, `Failed to validate dependencies: ${packageId}`)
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, `Failed to validate package dependencies: ${packageId}`)
    }
  }
  
  /**
   * Get package deployment readiness.
   * 
   * @param packageId Package UUID
   * @returns Promise<PackageDeploymentReadiness>
   */
  public async getDeploymentReadiness(packageId: string): Promise<PackageDeploymentReadiness> {
    try {
      this.validatePackageId(packageId)
      
      const response = await api.get<ApiResponse<PackageDeploymentReadiness>>(
        `${this.baseUrl}/${packageId}/deployment/readiness`
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.NETWORK_ERROR, `Failed to check deployment readiness: ${packageId}`)
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, `Failed to get deployment readiness: ${packageId}`)
    }
  }
  
  /**
   * Get package statistics for dashboard.
   * 
   * @returns Promise<PackageStatistics>
   */
  public async getPackageStatistics(): Promise<PackageStatistics> {
    try {
      const response = await api.get<ApiResponse<PackageStatistics>>(
        `${this.baseUrl}/statistics`
      )
      
      if (!response.data.success || !response.data.data) {
        throw this.createPackageError(PackageErrorType.NETWORK_ERROR, 'Failed to retrieve package statistics')
      }
      
      return response.data.data
    } catch (error) {
      throw this.handleApiError(error, 'Failed to retrieve package statistics')
    }
  }
  
  // Private helper methods following OOP encapsulation principles
  
  /**
   * Validate package ID format.
   */
  private validatePackageId(packageId: string): void {
    if (!packageId || packageId.trim().length === 0) {
      throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, 'Package ID cannot be empty')
    }
    
    // UUID format validation
    const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    if (!uuidPattern.test(packageId.trim())) {
      throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, 'Invalid package ID format')
    }
  }
  
  /**
   * Validate package creation request.
   */
  private validateCreateRequest(request: CreatePackageRequest): void {
    if (!request.name || request.name.trim().length === 0) {
      throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, 'Package name is required')
    }
    
    if (!request.version || request.version.trim().length === 0) {
      throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, 'Package version is required')
    }
    
    if (!request.packageType) {
      throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, 'Package type is required')
    }
  }
  
  /**
   * Validate package update request.
   */
  private validateUpdateRequest(request: UpdatePackageRequest): void {
    // At least one field should be provided for update
    const hasValidField = request.name || request.version || request.packageType || 
                         request.status || request.description !== undefined
    
    if (!hasValidField) {
      throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, 'At least one field must be provided for update')
    }
  }
  
  /**
   * Validate archive request.
   */
  private validateArchiveRequest(request: ArchivePackageRequest): void {
    if (!request.reason || request.reason.trim().length === 0) {
      throw this.createPackageError(PackageErrorType.VALIDATION_ERROR, 'Archive reason is required')
    }
  }
  
  /**
   * Build search parameters for package queries.
   */
  private buildSearchParams(criteria?: PackageSearchCriteria): Record<string, string> {
    const params: Record<string, string> = {}
    
    if (criteria?.searchTerm) {
      params.search = criteria.searchTerm
    }
    
    if (criteria?.packageTypes && criteria.packageTypes.length > 0) {
      params.types = criteria.packageTypes.join(',')
    }
    
    if (criteria?.statuses && criteria.statuses.length > 0) {
      params.statuses = criteria.statuses.join(',')
    }
    
    if (criteria?.tags && criteria.tags.length > 0) {
      params.tags = criteria.tags.join(',')
    }
    
    if (criteria?.createdBy) {
      params.createdBy = criteria.createdBy
    }
    
    if (criteria?.sortBy) {
      params.sortBy = criteria.sortBy
    }
    
    if (criteria?.sortOrder) {
      params.sortOrder = criteria.sortOrder
    }
    
    if (criteria?.limit) {
      params.limit = criteria.limit.toString()
    }
    
    if (criteria?.offset) {
      params.offset = criteria.offset.toString()
    }
    
    return params
  }
  
  /**
   * Build search parameters for asset queries.
   */
  private buildAssetSearchParams(criteria: AssetSearchCriteria): Record<string, string> {
    const params: Record<string, string> = {}
    
    if (criteria.searchTerm) {
      params.search = criteria.searchTerm
    }
    
    if (criteria.assetTypes && criteria.assetTypes.length > 0) {
      params.assetTypes = criteria.assetTypes.join(',')
    }
    
    if (criteria.packageIds && criteria.packageIds.length > 0) {
      params.packageIds = criteria.packageIds.join(',')
    }
    
    if (criteria.activeOnly !== undefined) {
      params.activeOnly = criteria.activeOnly.toString()
    }
    
    return params
  }
  
  /**
   * Create package error with type information.
   */
  private createPackageError(type: PackageErrorType, message: string, details?: Record<string, unknown>): PackageError {
    return {
      type,
      message,
      details,
      timestamp: new Date().toISOString()
    }
  }
  
  /**
   * Handle API errors with appropriate error conversion.
   */
  private handleApiError(error: unknown, fallbackMessage: string): PackageError {
    console.error('[PackageService] API Error:', error)
    
    // Handle axios errors
    if (error && typeof error === 'object' && 'response' in error) {
      const axiosError = error as { response?: { status?: number; data?: { message?: string } } }
      
      if (axiosError.response?.status === 404) {
        return this.createPackageError(PackageErrorType.NOT_FOUND, 'Package not found')
      }
      
      if (axiosError.response?.status === 400) {
        return this.createPackageError(
          PackageErrorType.VALIDATION_ERROR,
          axiosError.response.data?.message || 'Validation error'
        )
      }
      
      if (axiosError.response?.status === 403) {
        return this.createPackageError(PackageErrorType.PERMISSION_ERROR, 'Insufficient permissions')
      }
      
      if (axiosError.response?.status === 409) {
        return this.createPackageError(PackageErrorType.CONFLICT_ERROR, 'Conflict error')
      }
    }
    
    // Handle package errors
    if (error && typeof error === 'object' && 'type' in error) {
      return error as PackageError
    }
    
    // Default network error
    return this.createPackageError(PackageErrorType.NETWORK_ERROR, fallbackMessage)
  }
}

// Export singleton instance following singleton pattern
export const packageService = new PackageService()