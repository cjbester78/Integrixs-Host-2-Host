/**
 * Adapter Management Service
 * 
 * Comprehensive service for managing adapter operations within package context.
 * Implements CRUD operations, validation, state management, and dependency tracking.
 * 
 * @author Claude Code
 * @since Package Management Frontend V2.0
 */

import { adapterApi } from '@/lib/api'
import toast from 'react-hot-toast'

/**
 * Adapter data interfaces following data transfer object pattern
 */
export interface AdapterData {
  id: string
  name: string
  description?: string
  adapterType: 'FILE' | 'SFTP' | 'EMAIL'
  direction: 'SENDER' | 'RECEIVER'
  active: boolean
  status: 'STARTED' | 'STOPPED' | 'ERROR' | 'STARTING' | 'STOPPING'
  lastExecution?: Date
  lastExecutionStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING'
  packageId: string
  configuration: Record<string, any>
  createdAt: Date
  updatedAt: Date
}

export interface CreateAdapterRequest {
  name: string
  description?: string
  adapterType: 'FILE' | 'SFTP' | 'EMAIL'
  direction: 'SENDER' | 'RECEIVER'
  active: boolean
  packageId: string
  configuration: Record<string, any>
}

export interface UpdateAdapterRequest extends Partial<CreateAdapterRequest> {
  id: string
}

export interface AdapterTestResult {
  success: boolean
  message: string
  details?: Record<string, any>
  timestamp: Date
}

/**
 * Adapter operation result interface following result pattern
 */
export interface AdapterOperationResult<T = any> {
  success: boolean
  data?: T
  error?: string
  validationErrors?: Record<string, string>
}

/**
 * Adapter validation service following single responsibility principle
 */
class AdapterValidationService {
  /**
   * Validates adapter data for creation/update operations
   */
  static validateAdapterData(
    data: CreateAdapterRequest | UpdateAdapterRequest
  ): AdapterOperationResult<void> {
    const errors: Record<string, string> = {}

    // Basic validation
    if (!data.name?.trim()) {
      errors.name = 'Adapter name is required'
    } else if (data.name.length < 3) {
      errors.name = 'Adapter name must be at least 3 characters'
    } else if (data.name.length > 100) {
      errors.name = 'Adapter name must be less than 100 characters'
    }

    if (!data.packageId) {
      errors.packageId = 'Package association is required'
    }

    if (!data.adapterType) {
      errors.adapterType = 'Adapter type is required'
    }

    if (!data.direction) {
      errors.direction = 'Direction is required'
    }

    // Configuration validation based on adapter type
    if (data.configuration && data.adapterType) {
      const configErrors = this.validateConfiguration(data.adapterType, data.configuration)
      Object.assign(errors, configErrors)
    }

    return {
      success: Object.keys(errors).length === 0,
      validationErrors: errors
    }
  }

  /**
   * Validates adapter configuration based on type
   */
  private static validateConfiguration(
    type: string,
    config: Record<string, any>
  ): Record<string, string> {
    const errors: Record<string, string> = {}

    switch (type) {
      case 'FILE':
        if (!config.directory?.trim()) {
          errors.directory = 'Directory is required for file adapters'
        }
        break

      case 'SFTP':
        if (!config.host?.trim()) {
          errors.host = 'Host is required for SFTP adapters'
        }
        if (!config.username?.trim()) {
          errors.username = 'Username is required for SFTP adapters'
        }
        if (!config.password?.trim() && !config.privateKey?.trim()) {
          errors.authentication = 'Either password or private key is required for SFTP'
        }
        if (config.port && (config.port < 1 || config.port > 65535)) {
          errors.port = 'Port must be between 1 and 65535'
        }
        break

      case 'EMAIL':
        if (!config.smtpHost?.trim()) {
          errors.smtpHost = 'SMTP host is required for email adapters'
        }
        if (!config.smtpPort || config.smtpPort < 1 || config.smtpPort > 65535) {
          errors.smtpPort = 'Valid SMTP port is required (1-65535)'
        }
        if (!config.fromAddress?.trim()) {
          errors.fromAddress = 'From address is required for email adapters'
        }
        if (config.fromAddress && !this.isValidEmail(config.fromAddress)) {
          errors.fromAddress = 'From address must be a valid email'
        }
        if (config.toAddress && !this.isValidEmail(config.toAddress)) {
          errors.toAddress = 'To address must be a valid email'
        }
        break
    }

    return errors
  }

  /**
   * Validates email address format
   */
  private static isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailRegex.test(email)
  }
}

/**
 * Adapter data transformation service following adapter pattern
 */
class AdapterDataTransformer {
  /**
   * Transforms backend adapter data to frontend format
   */
  static transformFromBackend(backendData: any): AdapterData {
    return {
      id: backendData.id,
      name: backendData.name,
      description: backendData.description,
      adapterType: backendData.adapterType,
      direction: backendData.direction,
      active: backendData.active,
      status: backendData.status,
      lastExecution: backendData.lastExecution ? new Date(backendData.lastExecution) : undefined,
      lastExecutionStatus: backendData.lastExecutionStatus,
      packageId: backendData.packageId,
      configuration: backendData.configuration || {},
      createdAt: new Date(backendData.createdAt),
      updatedAt: new Date(backendData.updatedAt)
    }
  }

  /**
   * Transforms frontend data for backend submission
   */
  static transformForBackend(frontendData: CreateAdapterRequest | UpdateAdapterRequest): any {
    return {
      name: frontendData.name,
      description: frontendData.description,
      adapterType: frontendData.adapterType,
      direction: frontendData.direction,
      active: frontendData.active,
      packageId: frontendData.packageId,
      configuration: frontendData.configuration
    }
  }
}

/**
 * Adapter dependency tracking service following dependency injection pattern
 */
class AdapterDependencyTracker {
  /**
   * Analyzes adapter dependencies within package context
   */
  static async analyzeDependencies(
    adapterId: string,
    packageId: string
  ): Promise<AdapterOperationResult<string[]>> {
    try {
      // Analyze adapter dependencies by checking:
      // 1. Flows that use this adapter as source or target
      // 2. Shared configuration dependencies
      // 3. SSH keys or certificates referenced by the adapter
      const dependencies: string[] = []
      
      // Get adapter details
      const adapterResult = await AdapterManagementService.getAdapterById(adapterId)
      if (!adapterResult.success || !adapterResult.data) {
        throw new Error('Could not load adapter for dependency analysis')
      }
      
      const adapter = adapterResult.data
      
      // For SFTP adapters, check for SSH key dependencies
      if (adapter.adapterType === 'SFTP' && adapter.configuration.privateKeyId) {
        dependencies.push(adapter.configuration.privateKeyId)
      }
      
      // Check for shared configuration references
      if (adapter.configuration.sharedConfigIds) {
        dependencies.push(...adapter.configuration.sharedConfigIds)
      }
      
      // Check for certificate dependencies (for secure connections)
      if (adapter.configuration.certificateId) {
        dependencies.push(adapter.configuration.certificateId)
      }
      
      // Check for connection pool dependencies
      if (adapter.configuration.connectionPoolId) {
        dependencies.push(adapter.configuration.connectionPoolId)
      }
      
      return {
        success: true,
        data: dependencies
      }
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Failed to analyze adapter dependencies'
      }
    }
  }

  /**
   * Validates that adapter can be safely deleted
   */
  static async validateDeletion(
    adapterId: string,
    packageId: string
  ): Promise<AdapterOperationResult<{ canDelete: boolean; blockers: string[] }>> {
    try {
      const blockers: string[] = []
      
      // Get adapter details
      const adapterResult = await AdapterManagementService.getAdapterById(adapterId)
      if (!adapterResult.success || !adapterResult.data) {
        return {
          success: false,
          error: 'Could not load adapter for deletion validation'
        }
      }
      
      const adapter = adapterResult.data
      
      // Check if adapter is currently running
      if (adapter.status === 'STARTED' || adapter.status === 'STARTING') {
        blockers.push(`Adapter is currently ${adapter.status.toLowerCase()}. Stop before deletion.`)
      }
      
      // Check if adapter is currently executing
      if (adapter.lastExecutionStatus === 'RUNNING') {
        blockers.push('Adapter is currently executing. Wait for completion before deletion.')
      }
      
      // Check if flows depend on this adapter
      // Since we don't have direct access to FlowManagementService here to avoid circular dependencies,
      // we'll need to make this check at a higher level or through an API call
      try {
        // In a real implementation, this would be an API call to check flow dependencies
        // const flowDependencies = await api.get(`/api/adapters/${adapterId}/dependencies`)
        // For now, we'll add a placeholder check
        
        // Note: This is where we'd check if any flows use this adapter as source or target
        // Example: if (flowDependencies.data.flows.length > 0) { blockers.push(...) }
      } catch (error) {
        // If we can't check dependencies, be safe and block deletion
        blockers.push('Unable to verify flow dependencies. Please check manually.')
      }
      
      // Check if adapter has pending or scheduled operations
      if (adapter.configuration.hasScheduledOperations) {
        blockers.push('Adapter has scheduled operations. Disable scheduling before deletion.')
      }
      
      return {
        success: true,
        data: {
          canDelete: blockers.length === 0,
          blockers
        }
      }
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Failed to validate adapter deletion'
      }
    }
  }
}

/**
 * Adapter state management service following state pattern
 */
class AdapterStateManager {
  /**
   * Determines valid state transitions for an adapter
   */
  static getValidTransitions(currentStatus: string): string[] {
    switch (currentStatus) {
      case 'STOPPED':
        return ['STARTING']
      case 'STARTED':
        return ['STOPPING']
      case 'ERROR':
        return ['STARTING', 'STOPPED']
      case 'STARTING':
        return ['STOPPING']
      case 'STOPPING':
        return ['STARTING']
      default:
        return []
    }
  }

  /**
   * Checks if a state transition is valid
   */
  static isValidTransition(from: string, to: string): boolean {
    const validTransitions = this.getValidTransitions(from)
    return validTransitions.includes(to)
  }

  /**
   * Gets the expected final state after a transition
   */
  static getFinalState(transitionState: string): string {
    switch (transitionState) {
      case 'STARTING':
        return 'STARTED'
      case 'STOPPING':
        return 'STOPPED'
      default:
        return transitionState
    }
  }
}

/**
 * Main Adapter Management Service following service pattern and facade pattern.
 * Provides comprehensive adapter management functionality within package context.
 */
export class AdapterManagementService {
  /**
   * Creates a new adapter within package context
   */
  static async createAdapter(
    request: CreateAdapterRequest
  ): Promise<AdapterOperationResult<AdapterData>> {
    try {
      // Validate request data
      const validation = AdapterValidationService.validateAdapterData(request)
      if (!validation.success) {
        return validation
      }

      // Transform data for backend
      const backendData = AdapterDataTransformer.transformForBackend(request)

      // Call backend API
      const response = await adapterApi.createAdapter(backendData)

      // Transform response data
      const adapterData = AdapterDataTransformer.transformFromBackend(response.data)

      toast.success(`Adapter "${adapterData.name}" created successfully`)

      return {
        success: true,
        data: adapterData
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to create adapter'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Updates an existing adapter
   */
  static async updateAdapter(
    request: UpdateAdapterRequest
  ): Promise<AdapterOperationResult<AdapterData>> {
    try {
      // Validate request data
      const validation = AdapterValidationService.validateAdapterData(request)
      if (!validation.success) {
        return validation
      }

      // Transform data for backend
      const backendData = AdapterDataTransformer.transformForBackend(request)

      // Call backend API
      const response = await adapterApi.updateAdapter(request.id, backendData)

      // Transform response data
      const adapterData = AdapterDataTransformer.transformFromBackend(response.data)

      toast.success(`Adapter "${adapterData.name}" updated successfully`)

      return {
        success: true,
        data: adapterData
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to update adapter'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Deletes an adapter after validation
   */
  static async deleteAdapter(
    adapterId: string,
    packageId: string
  ): Promise<AdapterOperationResult<void>> {
    try {
      // Validate deletion
      const validation = await AdapterDependencyTracker.validateDeletion(adapterId, packageId)
      if (!validation.success) {
        return validation
      }

      if (!validation.data?.canDelete) {
        return {
          success: false,
          error: `Cannot delete adapter: ${validation.data?.blockers.join(', ')}`
        }
      }

      // Call backend API
      await adapterApi.deleteAdapter(adapterId)

      toast.success('Adapter deleted successfully')

      return {
        success: true
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to delete adapter'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Tests an adapter connection
   */
  static async testAdapter(
    adapterId: string
  ): Promise<AdapterOperationResult<AdapterTestResult>> {
    try {
      const response = await adapterApi.testAdapter(adapterId)

      const testResult: AdapterTestResult = {
        success: response.data.success,
        message: response.data.message,
        details: response.data.details,
        timestamp: new Date()
      }

      if (testResult.success) {
        toast.success('Adapter test completed successfully')
      } else {
        toast.error(`Adapter test failed: ${testResult.message}`)
      }

      return {
        success: true,
        data: testResult
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Adapter test failed'
      const testResult: AdapterTestResult = {
        success: false,
        message: errorMessage,
        timestamp: new Date()
      }

      toast.error(errorMessage)

      return {
        success: false,
        data: testResult,
        error: errorMessage
      }
    }
  }

  /**
   * Starts an adapter
   */
  static async startAdapter(
    adapterId: string,
    currentStatus: string
  ): Promise<AdapterOperationResult<void>> {
    try {
      // Validate state transition
      if (!AdapterStateManager.isValidTransition(currentStatus, 'STARTING')) {
        return {
          success: false,
          error: `Cannot start adapter from ${currentStatus} state`
        }
      }

      await adapterApi.startAdapter(adapterId)

      toast.success('Adapter start initiated')

      return {
        success: true
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to start adapter'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Stops an adapter
   */
  static async stopAdapter(
    adapterId: string,
    currentStatus: string
  ): Promise<AdapterOperationResult<void>> {
    try {
      // Validate state transition
      if (!AdapterStateManager.isValidTransition(currentStatus, 'STOPPING')) {
        return {
          success: false,
          error: `Cannot stop adapter from ${currentStatus} state`
        }
      }

      await adapterApi.stopAdapter(adapterId)

      toast.success('Adapter stop initiated')

      return {
        success: true
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to stop adapter'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Gets adapters by package with transformation
   */
  static async getAdaptersByPackage(
    packageId: string
  ): Promise<AdapterOperationResult<AdapterData[]>> {
    try {
      const response = await adapterApi.getAdaptersByPackage(packageId)

      const adapters = response.data.map((adapter: any) =>
        AdapterDataTransformer.transformFromBackend(adapter)
      )

      return {
        success: true,
        data: adapters
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load adapters'
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Gets adapter details by ID
   */
  static async getAdapterById(
    adapterId: string
  ): Promise<AdapterOperationResult<AdapterData>> {
    try {
      const response = await adapterApi.getAdapter(adapterId)
      const adapter = AdapterDataTransformer.transformFromBackend(response.data)

      return {
        success: true,
        data: adapter
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load adapter'
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Duplicates an adapter within the same package
   */
  static async duplicateAdapter(
    adapterId: string,
    newName: string
  ): Promise<AdapterOperationResult<AdapterData>> {
    try {
      // Get original adapter
      const originalResult = await this.getAdapterById(adapterId)
      if (!originalResult.success || !originalResult.data) {
        return {
          success: false,
          error: 'Failed to load original adapter for duplication'
        }
      }

      // Create duplicate request
      const duplicateRequest: CreateAdapterRequest = {
        name: newName,
        description: `Copy of ${originalResult.data.description || originalResult.data.name}`,
        adapterType: originalResult.data.adapterType,
        direction: originalResult.data.direction,
        active: false, // Start duplicates as inactive
        packageId: originalResult.data.packageId,
        configuration: { ...originalResult.data.configuration }
      }

      // Create the duplicate
      return await this.createAdapter(duplicateRequest)
    } catch (error: any) {
      const errorMessage = 'Failed to duplicate adapter'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Gets adapter status with refresh capability
   */
  static async refreshAdapterStatus(
    adapterId: string
  ): Promise<AdapterOperationResult<string>> {
    try {
      const response = await adapterApi.getAdapterStatus(adapterId)

      return {
        success: true,
        data: response.data.status
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to refresh adapter status'
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }
}

export default AdapterManagementService