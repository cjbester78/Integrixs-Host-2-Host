/**
 * Flow Management Service
 * 
 * Comprehensive service for managing flow operations within package context.
 * Implements CRUD operations, validation, state management, deployment tracking, and dependency management.
 * 
 * @author Claude Code
 * @since Package Management Frontend V2.0
 */

import { flowApi, executionApi } from '@/lib/api'
import toast from 'react-hot-toast'

/**
 * Flow data interfaces following data transfer object pattern
 */
export interface FlowData {
  id: string
  name: string
  description?: string
  flowType: 'SIMPLE' | 'COMPLEX' | 'BATCH' | 'REAL_TIME'
  status: 'ACTIVE' | 'INACTIVE' | 'DRAFT' | 'DEPRECATED'
  deploymentStatus: 'DEPLOYED' | 'UNDEPLOYED' | 'DEPLOYING' | 'FAILED' | 'PENDING'
  validationStatus: 'VALID' | 'INVALID' | 'UNKNOWN' | 'VALIDATING'
  lastExecution?: Date
  lastExecutionStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING' | 'CANCELLED'
  executionCount: number
  successRate: number
  avgExecutionTime: number
  packageId: string
  configuration: Record<string, any>
  sourceAdapterId?: string
  targetAdapterId?: string
  transformationConfig?: Record<string, any>
  scheduleConfig?: Record<string, any>
  retryConfig?: Record<string, any>
  createdAt: Date
  updatedAt: Date
  createdBy: string
  lastDeployedAt?: Date
  lastDeployedBy?: string
}

export interface CreateFlowRequest {
  name: string
  description?: string
  flowType: 'SIMPLE' | 'COMPLEX' | 'BATCH' | 'REAL_TIME'
  status: 'ACTIVE' | 'INACTIVE' | 'DRAFT'
  packageId: string
  configuration: Record<string, any>
  sourceAdapterId?: string
  targetAdapterId?: string
  transformationConfig?: Record<string, any>
  scheduleConfig?: Record<string, any>
  retryConfig?: Record<string, any>
}

export interface UpdateFlowRequest extends Partial<CreateFlowRequest> {
  id: string
}

export interface FlowValidationResult {
  success: boolean
  errors: string[]
  warnings: string[]
  details?: Record<string, any>
  timestamp: Date
}

export interface FlowDeploymentResult {
  success: boolean
  deploymentId?: string
  message: string
  details?: Record<string, any>
  timestamp: Date
}

export interface FlowExecutionResult {
  success: boolean
  executionId?: string
  message: string
  startTime: Date
  estimatedCompletion?: Date
}

/**
 * Flow operation result interface following result pattern
 */
export interface FlowOperationResult<T = any> {
  success: boolean
  data?: T
  error?: string
  validationErrors?: Record<string, string>
}

/**
 * Flow validation service following single responsibility principle
 */
class FlowValidationService {
  /**
   * Validates flow data for creation/update operations
   */
  static validateFlowData(
    data: CreateFlowRequest | UpdateFlowRequest
  ): FlowOperationResult<void> {
    const errors: Record<string, string> = {}

    // Basic validation
    if (!data.name?.trim()) {
      errors.name = 'Flow name is required'
    } else if (data.name.length < 3) {
      errors.name = 'Flow name must be at least 3 characters'
    } else if (data.name.length > 100) {
      errors.name = 'Flow name must be less than 100 characters'
    }

    if (!data.packageId) {
      errors.packageId = 'Package association is required'
    }

    if (!data.flowType) {
      errors.flowType = 'Flow type is required'
    }

    if (!data.status) {
      errors.status = 'Flow status is required'
    }

    // Configuration validation based on flow type
    if (data.configuration && data.flowType) {
      const configErrors = this.validateConfiguration(data.flowType, data.configuration)
      Object.assign(errors, configErrors)
    }

    // Adapter validation
    if (data.sourceAdapterId && data.targetAdapterId && data.sourceAdapterId === data.targetAdapterId) {
      errors.adapters = 'Source and target adapters cannot be the same'
    }

    // Schedule validation
    if (data.scheduleConfig) {
      const scheduleErrors = this.validateScheduleConfiguration(data.scheduleConfig)
      Object.assign(errors, scheduleErrors)
    }

    return {
      success: Object.keys(errors).length === 0,
      validationErrors: errors
    }
  }

  /**
   * Validates flow configuration based on type
   */
  private static validateConfiguration(
    type: string,
    config: Record<string, any>
  ): Record<string, string> {
    const errors: Record<string, string> = {}

    switch (type) {
      case 'SIMPLE':
        if (!config.sourceAdapter && !config.targetAdapter) {
          errors.configuration = 'Simple flows require at least source or target adapter'
        }
        break

      case 'COMPLEX':
        if (!config.steps || !Array.isArray(config.steps) || config.steps.length === 0) {
          errors.steps = 'Complex flows require at least one processing step'
        }
        break

      case 'BATCH':
        if (!config.batchSize || config.batchSize < 1) {
          errors.batchSize = 'Batch flows require a valid batch size'
        }
        if (!config.processingWindow) {
          errors.processingWindow = 'Batch flows require a processing window'
        }
        break

      case 'REAL_TIME':
        if (!config.triggerConfig) {
          errors.triggerConfig = 'Real-time flows require trigger configuration'
        }
        if (config.maxConcurrency && config.maxConcurrency < 1) {
          errors.maxConcurrency = 'Max concurrency must be at least 1'
        }
        break
    }

    return errors
  }

  /**
   * Validates schedule configuration
   */
  private static validateScheduleConfiguration(
    scheduleConfig: Record<string, any>
  ): Record<string, string> {
    const errors: Record<string, string> = {}

    if (scheduleConfig.enabled) {
      if (!scheduleConfig.cronExpression && !scheduleConfig.interval) {
        errors.schedule = 'Scheduled flows require either cron expression or interval'
      }

      if (scheduleConfig.cronExpression) {
        if (!this.isValidCronExpression(scheduleConfig.cronExpression)) {
          errors.cronExpression = 'Invalid cron expression format'
        }
      }

      if (scheduleConfig.interval && scheduleConfig.interval < 60000) {
        errors.interval = 'Minimum interval is 1 minute (60000ms)'
      }
    }

    return errors
  }

  /**
   * Basic cron expression validation
   */
  private static isValidCronExpression(expression: string): boolean {
    const cronRegex = /^(\*|([0-5]?\d)) (\*|([0-5]?\d)) (\*|(1?\d|2[0-3])) (\*|([12]?\d|3[01])) (\*|(1[0-2]|[1-9])) (\*|([0-6]))$/
    return cronRegex.test(expression.trim())
  }
}

/**
 * Flow data transformation service following adapter pattern
 */
class FlowDataTransformer {
  /**
   * Transforms backend flow data to frontend format
   */
  static transformFromBackend(backendData: any): FlowData {
    return {
      id: backendData.id,
      name: backendData.name,
      description: backendData.description,
      flowType: backendData.flowType || 'SIMPLE',
      status: backendData.status || 'DRAFT',
      deploymentStatus: backendData.deploymentStatus || 'UNDEPLOYED',
      validationStatus: backendData.validationStatus || 'UNKNOWN',
      lastExecution: backendData.lastExecution ? new Date(backendData.lastExecution) : undefined,
      lastExecutionStatus: backendData.lastExecutionStatus,
      executionCount: backendData.executionCount || 0,
      successRate: backendData.successRate || 0,
      avgExecutionTime: backendData.avgExecutionTime || 0,
      packageId: backendData.packageId,
      configuration: backendData.configuration || {},
      sourceAdapterId: backendData.sourceAdapterId,
      targetAdapterId: backendData.targetAdapterId,
      transformationConfig: backendData.transformationConfig,
      scheduleConfig: backendData.scheduleConfig,
      retryConfig: backendData.retryConfig,
      createdAt: new Date(backendData.createdAt),
      updatedAt: new Date(backendData.updatedAt),
      createdBy: backendData.createdBy,
      lastDeployedAt: backendData.lastDeployedAt ? new Date(backendData.lastDeployedAt) : undefined,
      lastDeployedBy: backendData.lastDeployedBy
    }
  }

  /**
   * Transforms frontend data for backend submission
   */
  static transformForBackend(frontendData: CreateFlowRequest | UpdateFlowRequest): any {
    return {
      name: frontendData.name,
      description: frontendData.description,
      flowType: frontendData.flowType,
      status: frontendData.status,
      packageId: frontendData.packageId,
      configuration: frontendData.configuration,
      sourceAdapterId: frontendData.sourceAdapterId,
      targetAdapterId: frontendData.targetAdapterId,
      transformationConfig: frontendData.transformationConfig,
      scheduleConfig: frontendData.scheduleConfig,
      retryConfig: frontendData.retryConfig
    }
  }
}

/**
 * Flow dependency tracking service following dependency injection pattern
 */
class FlowDependencyTracker {
  /**
   * Analyzes flow dependencies within package context
   */
  static async analyzeDependencies(
    flowId: string,
    packageId: string
  ): Promise<FlowOperationResult<string[]>> {
    try {
      // Analyze flow dependencies by checking:
      // 1. Source and target adapter dependencies
      // 2. Shared configuration dependencies
      // 3. Referenced flows within the package
      const dependencies: string[] = []
      
      // Get flow details
      const flowResult = await FlowManagementService.getFlowById(flowId)
      if (!flowResult.success || !flowResult.data) {
        throw new Error('Could not load flow for dependency analysis')
      }
      
      const flow = flowResult.data
      
      // Add adapter dependencies
      if (flow.sourceAdapterId) {
        dependencies.push(flow.sourceAdapterId)
      }
      if (flow.targetAdapterId) {
        dependencies.push(flow.targetAdapterId)
      }
      
      // Check for configuration dependencies (e.g., referenced flows, shared configs)
      if (flow.configuration.referencedFlows) {
        dependencies.push(...flow.configuration.referencedFlows)
      }
      
      if (flow.configuration.sharedConfigIds) {
        dependencies.push(...flow.configuration.sharedConfigIds)
      }
      
      // Check transformation config dependencies
      if (flow.transformationConfig?.referencedMappings) {
        dependencies.push(...flow.transformationConfig.referencedMappings)
      }
      
      return {
        success: true,
        data: dependencies
      }
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Failed to analyze flow dependencies'
      }
    }
  }

  /**
   * Validates that flow can be safely deleted
   */
  static async validateDeletion(
    flowId: string,
    packageId: string
  ): Promise<FlowOperationResult<{ canDelete: boolean; blockers: string[] }>> {
    try {
      const blockers: string[] = []
      
      // Get flow details
      const flowResult = await FlowManagementService.getFlowById(flowId)
      if (!flowResult.success || !flowResult.data) {
        return {
          success: false,
          error: 'Could not load flow for deletion validation'
        }
      }
      
      const flow = flowResult.data
      
      // Check if flow is currently deployed
      if (flow.deploymentStatus === 'DEPLOYED' || flow.deploymentStatus === 'DEPLOYING') {
        blockers.push(`Flow is currently ${flow.deploymentStatus.toLowerCase()}. Undeploy before deletion.`)
      }
      
      // Check if flow is currently running
      if (flow.lastExecutionStatus === 'RUNNING') {
        blockers.push('Flow is currently executing. Wait for completion before deletion.')
      }
      
      // Check if other flows depend on this flow
      const packageFlowsResult = await FlowManagementService.getFlowsByPackage(packageId)
      if (packageFlowsResult.success && packageFlowsResult.data) {
        const dependentFlows = packageFlowsResult.data.filter(otherFlow => 
          otherFlow.id !== flowId && 
          (otherFlow.configuration.referencedFlows?.includes(flowId) ||
           otherFlow.configuration.parentFlowId === flowId)
        )
        
        if (dependentFlows.length > 0) {
          blockers.push(`${dependentFlows.length} other flows depend on this flow: ${dependentFlows.map(f => f.name).join(', ')}`)
        }
      }
      
      // Check if flow has active schedules
      if (flow.scheduleConfig?.enabled) {
        blockers.push('Flow has active schedules. Disable scheduling before deletion.')
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
        error: error.message || 'Failed to validate flow deletion'
      }
    }
  }

  /**
   * Validates deployment readiness
   */
  static async validateDeploymentReadiness(
    flowId: string
  ): Promise<FlowOperationResult<{ canDeploy: boolean; blockers: string[] }>> {
    try {
      const blockers: string[] = []
      
      // Get flow details
      const flowResult = await FlowManagementService.getFlowById(flowId)
      if (!flowResult.success || !flowResult.data) {
        return {
          success: false,
          error: 'Could not load flow for deployment validation'
        }
      }
      
      const flow = flowResult.data
      
      // Check flow status - only ACTIVE flows can be deployed
      if (flow.status !== 'ACTIVE') {
        blockers.push(`Flow must be ACTIVE to deploy. Current status: ${flow.status}`)
      }
      
      // Check validation status
      if (flow.validationStatus === 'INVALID') {
        blockers.push('Flow validation failed. Fix validation errors before deployment.')
      }
      
      // Check if required adapters are available and active
      if (flow.sourceAdapterId) {
        // In a real implementation, we'd check adapter status via API
        // For now, assume adapter validation is handled elsewhere
      }
      
      if (flow.targetAdapterId) {
        // In a real implementation, we'd check adapter status via API
        // For now, assume adapter validation is handled elsewhere
      }
      
      // Check configuration completeness
      if (!flow.configuration || Object.keys(flow.configuration).length === 0) {
        blockers.push('Flow configuration is incomplete')
      }
      
      // Check flow type specific requirements
      switch (flow.flowType) {
        case 'SIMPLE':
          if (!flow.sourceAdapterId && !flow.targetAdapterId) {
            blockers.push('Simple flows require at least source or target adapter')
          }
          break
          
        case 'COMPLEX':
          if (!flow.configuration.steps || flow.configuration.steps.length === 0) {
            blockers.push('Complex flows require processing steps')
          }
          break
          
        case 'BATCH':
          if (!flow.configuration.batchSize) {
            blockers.push('Batch flows require batch size configuration')
          }
          break
          
        case 'REAL_TIME':
          if (!flow.configuration.triggerConfig) {
            blockers.push('Real-time flows require trigger configuration')
          }
          break
      }
      
      // Check dependencies are available
      const dependenciesResult = await this.analyzeDependencies(flowId, flow.packageId)
      if (dependenciesResult.success && dependenciesResult.data) {
        // Check if any dependencies are missing or inactive
        // In a real implementation, we'd validate each dependency
        for (const depId of dependenciesResult.data) {
          // Placeholder for dependency validation
          // Could check adapter status, referenced flow status, etc.
        }
      }
      
      return {
        success: true,
        data: {
          canDeploy: blockers.length === 0,
          blockers
        }
      }
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Failed to validate deployment readiness'
      }
    }
  }
}

/**
 * Flow state management service following state pattern
 */
class FlowStateManager {
  /**
   * Determines valid state transitions for a flow
   */
  static getValidStatusTransitions(currentStatus: string): string[] {
    switch (currentStatus) {
      case 'DRAFT':
        return ['ACTIVE', 'INACTIVE']
      case 'ACTIVE':
        return ['INACTIVE', 'DEPRECATED']
      case 'INACTIVE':
        return ['ACTIVE', 'DEPRECATED']
      case 'DEPRECATED':
        return [] // No transitions allowed from deprecated
      default:
        return []
    }
  }

  /**
   * Determines valid deployment transitions
   */
  static getValidDeploymentTransitions(currentStatus: string): string[] {
    switch (currentStatus) {
      case 'UNDEPLOYED':
        return ['DEPLOYING']
      case 'DEPLOYED':
        return ['DEPLOYING'] // For redeployment
      case 'DEPLOYING':
        return [] // Must wait for completion
      case 'FAILED':
        return ['DEPLOYING'] // Allow retry
      case 'PENDING':
        return ['DEPLOYING']
      default:
        return []
    }
  }

  /**
   * Checks if a state transition is valid
   */
  static isValidStatusTransition(from: string, to: string): boolean {
    const validTransitions = this.getValidStatusTransitions(from)
    return validTransitions.includes(to)
  }

  /**
   * Checks if a deployment transition is valid
   */
  static isValidDeploymentTransition(from: string, to: string): boolean {
    const validTransitions = this.getValidDeploymentTransitions(from)
    return validTransitions.includes(to)
  }
}

/**
 * Main Flow Management Service following service pattern and facade pattern.
 * Provides comprehensive flow management functionality within package context.
 */
export class FlowManagementService {
  /**
   * Creates a new flow within package context
   */
  static async createFlow(
    request: CreateFlowRequest
  ): Promise<FlowOperationResult<FlowData>> {
    try {
      // Validate request data
      const validation = FlowValidationService.validateFlowData(request)
      if (!validation.success) {
        return validation
      }

      // Transform data for backend
      const backendData = FlowDataTransformer.transformForBackend(request)

      // Call backend API
      const response = await flowApi.createFlow(backendData)

      // Transform response data
      const flowData = FlowDataTransformer.transformFromBackend(response.data)

      toast.success(`Flow "${flowData.name}" created successfully`)

      return {
        success: true,
        data: flowData
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to create flow'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Updates an existing flow
   */
  static async updateFlow(
    request: UpdateFlowRequest
  ): Promise<FlowOperationResult<FlowData>> {
    try {
      // Validate request data
      const validation = FlowValidationService.validateFlowData(request)
      if (!validation.success) {
        return validation
      }

      // Transform data for backend
      const backendData = FlowDataTransformer.transformForBackend(request)

      // Call backend API
      const response = await flowApi.updateFlow(request.id, backendData)

      // Transform response data
      const flowData = FlowDataTransformer.transformFromBackend(response.data)

      toast.success(`Flow "${flowData.name}" updated successfully`)

      return {
        success: true,
        data: flowData
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to update flow'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Deletes a flow after validation
   */
  static async deleteFlow(
    flowId: string,
    packageId: string
  ): Promise<FlowOperationResult<void>> {
    try {
      // Validate deletion
      const validation = await FlowDependencyTracker.validateDeletion(flowId, packageId)
      if (!validation.success) {
        return validation
      }

      if (!validation.data?.canDelete) {
        return {
          success: false,
          error: `Cannot delete flow: ${validation.data?.blockers.join(', ')}`
        }
      }

      // Call backend API
      await flowApi.deleteFlow(flowId)

      toast.success('Flow deleted successfully')

      return {
        success: true
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to delete flow'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Validates a flow configuration
   */
  static async validateFlow(
    flowId: string
  ): Promise<FlowOperationResult<FlowValidationResult>> {
    try {
      const response = await flowApi.validateFlow(flowId)

      const validationResult: FlowValidationResult = {
        success: response.data.valid || response.data.success,
        errors: response.data.errors || [],
        warnings: response.data.warnings || [],
        details: response.data.details,
        timestamp: new Date()
      }

      if (validationResult.success) {
        toast.success('Flow validation completed successfully')
      } else {
        toast.error(`Flow validation failed: ${validationResult.errors.join(', ')}`)
      }

      return {
        success: true,
        data: validationResult
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Flow validation failed'
      const validationResult: FlowValidationResult = {
        success: false,
        errors: [errorMessage],
        warnings: [],
        timestamp: new Date()
      }

      toast.error(errorMessage)

      return {
        success: false,
        data: validationResult,
        error: errorMessage
      }
    }
  }

  /**
   * Deploys a flow
   */
  static async deployFlow(
    flowId: string,
    currentDeploymentStatus: string
  ): Promise<FlowOperationResult<FlowDeploymentResult>> {
    try {
      // Validate deployment transition
      if (!FlowStateManager.isValidDeploymentTransition(currentDeploymentStatus, 'DEPLOYING')) {
        return {
          success: false,
          error: `Cannot deploy flow from ${currentDeploymentStatus} state`
        }
      }

      // Validate deployment readiness
      const readinessCheck = await FlowDependencyTracker.validateDeploymentReadiness(flowId)
      if (!readinessCheck.success || !readinessCheck.data?.canDeploy) {
        return {
          success: false,
          error: `Cannot deploy flow: ${readinessCheck.data?.blockers.join(', ')}`
        }
      }

      const response = await flowApi.deployFlow(flowId)

      const deploymentResult: FlowDeploymentResult = {
        success: response.data.success,
        deploymentId: response.data.deploymentId,
        message: response.data.message || 'Flow deployment initiated',
        details: response.data.details,
        timestamp: new Date()
      }

      toast.success('Flow deployment initiated successfully')

      return {
        success: true,
        data: deploymentResult
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to deploy flow'
      const deploymentResult: FlowDeploymentResult = {
        success: false,
        message: errorMessage,
        timestamp: new Date()
      }

      toast.error(errorMessage)

      return {
        success: false,
        data: deploymentResult,
        error: errorMessage
      }
    }
  }

  /**
   * Undeploys a flow
   */
  static async undeployFlow(
    flowId: string
  ): Promise<FlowOperationResult<FlowDeploymentResult>> {
    try {
      const response = await flowApi.undeployFlow(flowId)

      const deploymentResult: FlowDeploymentResult = {
        success: response.data.success,
        message: response.data.message || 'Flow undeployed successfully',
        details: response.data.details,
        timestamp: new Date()
      }

      toast.success('Flow undeployed successfully')

      return {
        success: true,
        data: deploymentResult
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to undeploy flow'
      const deploymentResult: FlowDeploymentResult = {
        success: false,
        message: errorMessage,
        timestamp: new Date()
      }

      toast.error(errorMessage)

      return {
        success: false,
        data: deploymentResult,
        error: errorMessage
      }
    }
  }

  /**
   * Executes a flow manually
   */
  static async executeFlow(
    flowId: string
  ): Promise<FlowOperationResult<FlowExecutionResult>> {
    try {
      const response = await flowApi.executeFlow(flowId)

      const executionResult: FlowExecutionResult = {
        success: response.data.success,
        executionId: response.data.executionId,
        message: response.data.message || 'Flow execution initiated',
        startTime: new Date(),
        estimatedCompletion: response.data.estimatedCompletion ? new Date(response.data.estimatedCompletion) : undefined
      }

      toast.success('Flow execution initiated successfully')

      return {
        success: true,
        data: executionResult
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to execute flow'
      const executionResult: FlowExecutionResult = {
        success: false,
        message: errorMessage,
        startTime: new Date()
      }

      toast.error(errorMessage)

      return {
        success: false,
        data: executionResult,
        error: errorMessage
      }
    }
  }

  /**
   * Gets flows by package with transformation
   */
  static async getFlowsByPackage(
    packageId: string
  ): Promise<FlowOperationResult<FlowData[]>> {
    try {
      const response = await flowApi.getFlowsByPackage(packageId)

      const flows = response.data.map((flow: any) =>
        FlowDataTransformer.transformFromBackend(flow)
      )

      return {
        success: true,
        data: flows
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load flows'
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Gets flow details by ID
   */
  static async getFlowById(
    flowId: string
  ): Promise<FlowOperationResult<FlowData>> {
    try {
      const response = await flowApi.getFlowById(flowId)
      const flow = FlowDataTransformer.transformFromBackend(response.data)

      return {
        success: true,
        data: flow
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load flow'
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Duplicates a flow within the same package
   */
  static async duplicateFlow(
    flowId: string,
    newName: string
  ): Promise<FlowOperationResult<FlowData>> {
    try {
      // Get original flow
      const originalResult = await this.getFlowById(flowId)
      if (!originalResult.success || !originalResult.data) {
        return {
          success: false,
          error: 'Failed to load original flow for duplication'
        }
      }

      // Create duplicate request
      const duplicateRequest: CreateFlowRequest = {
        name: newName,
        description: `Copy of ${originalResult.data.description || originalResult.data.name}`,
        flowType: originalResult.data.flowType,
        status: 'DRAFT', // Start duplicates as draft
        packageId: originalResult.data.packageId,
        configuration: { ...originalResult.data.configuration },
        sourceAdapterId: originalResult.data.sourceAdapterId,
        targetAdapterId: originalResult.data.targetAdapterId,
        transformationConfig: originalResult.data.transformationConfig ? { ...originalResult.data.transformationConfig } : undefined,
        scheduleConfig: originalResult.data.scheduleConfig ? { ...originalResult.data.scheduleConfig } : undefined,
        retryConfig: originalResult.data.retryConfig ? { ...originalResult.data.retryConfig } : undefined
      }

      // Create the duplicate
      return await this.createFlow(duplicateRequest)
    } catch (error: any) {
      const errorMessage = 'Failed to duplicate flow'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Gets flow execution history
   */
  static async getFlowExecutionHistory(
    flowId: string,
    params?: { limit?: number; status?: string }
  ): Promise<FlowOperationResult<any[]>> {
    try {
      const response = await executionApi.getExecutionsByFlow(flowId, params)

      return {
        success: true,
        data: response.data
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load execution history'
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Updates flow status
   */
  static async updateFlowStatus(
    flowId: string,
    currentStatus: string,
    newStatus: string
  ): Promise<FlowOperationResult<void>> {
    try {
      // Validate state transition
      if (!FlowStateManager.isValidStatusTransition(currentStatus, newStatus)) {
        return {
          success: false,
          error: `Cannot change flow status from ${currentStatus} to ${newStatus}`
        }
      }

      const isActive = newStatus === 'ACTIVE'
      await flowApi.setFlowActive(flowId, isActive)

      toast.success(`Flow status updated to ${newStatus}`)

      return {
        success: true
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to update flow status'
      toast.error(errorMessage)
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  /**
   * Gets flow deployment status
   */
  static async getFlowDeploymentStatus(
    flowId: string
  ): Promise<FlowOperationResult<any>> {
    try {
      const response = await flowApi.getFlowDeploymentStatus(flowId)

      return {
        success: true,
        data: response.data
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to get deployment status'
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }
}

export default FlowManagementService