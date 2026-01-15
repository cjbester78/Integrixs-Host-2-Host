package com.integrixs.core.service;

import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.IntegrationPackage;
import com.integrixs.shared.model.PackageAssetDependency;
import com.integrixs.core.repository.IntegrationPackageRepository;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.core.repository.PackageAssetDependencyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for unified access to package assets and container operations.
 * Provides high-level operations across all asset types within packages.
 * Follows SOLID principles with separation of concerns and encapsulation.
 * 
 * @author Claude Code
 * @since Package Management V1.0
 */
@Service
@Transactional(readOnly = true)
public class PackageContainerService {
    
    private final IntegrationPackageRepository packageRepository;
    private final AdapterRepository adapterRepository;
    private final IntegrationFlowRepository flowRepository;
    private final PackageAssetDependencyRepository dependencyRepository;
    
    @Autowired
    public PackageContainerService(
            IntegrationPackageRepository packageRepository,
            AdapterRepository adapterRepository,
            IntegrationFlowRepository flowRepository,
            PackageAssetDependencyRepository dependencyRepository) {
        this.packageRepository = Objects.requireNonNull(packageRepository, "Package repository cannot be null");
        this.adapterRepository = Objects.requireNonNull(adapterRepository, "Adapter repository cannot be null");
        this.flowRepository = Objects.requireNonNull(flowRepository, "Flow repository cannot be null");
        this.dependencyRepository = Objects.requireNonNull(dependencyRepository, "Dependency repository cannot be null");
    }
    
    /**
     * Get complete package container with all assets.
     * 
     * @param packageId Package UUID
     * @return Package container with all assets and dependencies
     * @throws IllegalStateException if package not found
     */
    public PackageContainer getPackageContainer(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        IntegrationPackage pkg = getPackageOrThrow(packageId);
        List<Adapter> adapters = adapterRepository.findByPackageId(packageId);
        List<IntegrationFlow> flows = flowRepository.findByPackageId(packageId);
        List<PackageAssetDependency> dependencies = dependencyRepository.findByPackageId(packageId);
        
        return new PackageContainer(pkg, adapters, flows, dependencies);
    }
    
    /**
     * Get package assets summary with counts and status information.
     * 
     * @param packageId Package UUID
     * @return Assets summary for the package
     */
    public PackageAssetsSummary getPackageAssetsSummary(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        IntegrationPackage pkg = getPackageOrThrow(packageId);
        
        List<Adapter> adapters = adapterRepository.findByPackageId(packageId);
        List<IntegrationFlow> flows = flowRepository.findByPackageId(packageId);
        List<PackageAssetDependency> dependencies = dependencyRepository.findByPackageId(packageId);
        
        return new PackageAssetsSummary(pkg, adapters, flows, dependencies);
    }
    
    /**
     * Find assets across all packages with optional type filtering.
     * 
     * @param searchTerm Optional search term for asset names
     * @param assetTypes Optional list of asset types to include
     * @return List of package assets matching criteria
     */
    public List<PackageAsset> searchAssetsAcrossPackages(String searchTerm, Set<AssetType> assetTypes) {
        List<PackageAsset> allAssets = new ArrayList<>();
        
        // Include adapters if requested or no filter specified
        if (assetTypes == null || assetTypes.contains(AssetType.ADAPTER)) {
            List<Adapter> adapters = adapterRepository.findAll();
            allAssets.addAll(adapters.stream()
                .filter(adapter -> matchesSearchTerm(adapter.getName(), searchTerm))
                .map(adapter -> new PackageAsset(AssetType.ADAPTER, adapter.getId(), 
                    adapter.getName(), adapter.getPackageId(), adapter.getActive(),
                    adapter.getCreatedAt(), adapter.getUpdatedAt()))
                .collect(Collectors.toList()));
        }
        
        // Include flows if requested or no filter specified
        if (assetTypes == null || assetTypes.contains(AssetType.FLOW)) {
            List<IntegrationFlow> flows = flowRepository.findAll();
            allAssets.addAll(flows.stream()
                .filter(flow -> matchesSearchTerm(flow.getName(), searchTerm))
                .map(flow -> new PackageAsset(AssetType.FLOW, flow.getId(), 
                    flow.getName(), flow.getPackageId(), flow.getActive(),
                    flow.getCreatedAt(), flow.getUpdatedAt()))
                .collect(Collectors.toList()));
        }
        
        return allAssets.stream()
            .sorted(Comparator.comparing(PackageAsset::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * Get asset dependencies for a specific asset.
     * 
     * @param packageId Package UUID
     * @param assetType Type of asset
     * @param assetId Asset UUID
     * @return Asset dependency information
     */
    public AssetDependencyInfo getAssetDependencies(UUID packageId, AssetType assetType, UUID assetId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(assetType, "Asset type cannot be null");
        Objects.requireNonNull(assetId, "Asset ID cannot be null");
        
        PackageAssetDependency.AssetType depAssetType = mapToRepositoryAssetType(assetType);
        
        List<PackageAssetDependency> dependencies = dependencyRepository
            .findDependenciesForAsset(packageId, depAssetType, assetId);
        
        List<PackageAssetDependency> dependents = dependencyRepository
            .findAssetsDependingOn(packageId, depAssetType, assetId);
        
        return new AssetDependencyInfo(assetType, assetId, dependencies, dependents);
    }
    
    /**
     * Validate package has no circular dependencies.
     * 
     * @param packageId Package UUID
     * @return Validation result with any circular dependency issues
     */
    public PackageDependencyValidation validatePackageDependencies(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        List<PackageAssetDependency> allDependencies = dependencyRepository.findByPackageId(packageId);
        List<CircularDependency> circularDependencies = findCircularDependencies(allDependencies);
        
        boolean isValid = circularDependencies.isEmpty();
        
        return new PackageDependencyValidation(packageId, isValid, circularDependencies);
    }
    
    /**
     * Get package deployment readiness status.
     * 
     * @param packageId Package UUID
     * @return Deployment readiness information
     */
    public PackageDeploymentReadiness getDeploymentReadiness(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        IntegrationPackage pkg = getPackageOrThrow(packageId);
        List<Adapter> adapters = adapterRepository.findByPackageId(packageId);
        List<IntegrationFlow> flows = flowRepository.findByPackageId(packageId);
        
        return new PackageDeploymentReadiness(pkg, adapters, flows);
    }
    
    /**
     * Move asset between packages.
     * 
     * @param assetType Type of asset to move
     * @param assetId Asset UUID to move
     * @param fromPackageId Source package UUID
     * @param toPackageId Target package UUID
     * @param movedBy User performing the move
     * @return true if asset was moved successfully
     */
    @Transactional
    public boolean moveAssetBetweenPackages(AssetType assetType, UUID assetId, 
            UUID fromPackageId, UUID toPackageId, UUID movedBy) {
        Objects.requireNonNull(assetType, "Asset type cannot be null");
        Objects.requireNonNull(assetId, "Asset ID cannot be null");
        Objects.requireNonNull(fromPackageId, "From package ID cannot be null");
        Objects.requireNonNull(toPackageId, "To package ID cannot be null");
        Objects.requireNonNull(movedBy, "Moved by cannot be null");
        
        // Validate both packages exist
        getPackageOrThrow(fromPackageId);
        getPackageOrThrow(toPackageId);
        
        // Validate asset exists in source package
        validateAssetExistsInPackage(assetType, assetId, fromPackageId);
        
        try {
            switch (assetType) {
                case ADAPTER:
                    adapterRepository.updatePackageAssociation(assetId, toPackageId, movedBy);
                    break;
                case FLOW:
                    flowRepository.updatePackageAssociation(assetId, toPackageId, movedBy);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported asset type for move: " + assetType);
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to move asset between packages", e);
        }
    }
    
    // Private helper methods following OOP encapsulation principles
    
    /**
     * Get package or throw exception if not found.
     */
    private IntegrationPackage getPackageOrThrow(UUID packageId) {
        return packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageId));
    }
    
    /**
     * Check if search term matches asset name.
     */
    private boolean matchesSearchTerm(String assetName, String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return true;
        }
        return assetName != null && assetName.toLowerCase()
                .contains(searchTerm.toLowerCase());
    }
    
    /**
     * Map service asset type to repository asset type.
     */
    private PackageAssetDependency.AssetType mapToRepositoryAssetType(AssetType assetType) {
        switch (assetType) {
            case ADAPTER:
                return PackageAssetDependency.AssetType.ADAPTER;
            case FLOW:
                return PackageAssetDependency.AssetType.FLOW;
            default:
                throw new IllegalArgumentException("Unknown asset type: " + assetType);
        }
    }
    
    /**
     * Find circular dependencies in the dependency graph.
     */
    private List<CircularDependency> findCircularDependencies(List<PackageAssetDependency> dependencies) {
        Map<String, Set<String>> dependencyGraph = buildDependencyGraph(dependencies);
        List<CircularDependency> circularDeps = new ArrayList<>();
        
        for (String asset : dependencyGraph.keySet()) {
            List<String> cycle = findCycleFromAsset(asset, dependencyGraph, new HashSet<>(), new ArrayList<>());
            if (cycle != null && !cycle.isEmpty()) {
                circularDeps.add(new CircularDependency(cycle));
            }
        }
        
        return circularDeps;
    }
    
    /**
     * Build dependency graph from dependency list.
     */
    private Map<String, Set<String>> buildDependencyGraph(List<PackageAssetDependency> dependencies) {
        Map<String, Set<String>> graph = new HashMap<>();
        
        for (PackageAssetDependency dep : dependencies) {
            String asset = dep.getAssetType() + ":" + dep.getAssetId();
            String dependsOn = dep.getDependsOnAssetType() + ":" + dep.getDependsOnAssetId();
            
            graph.computeIfAbsent(asset, k -> new HashSet<>()).add(dependsOn);
        }
        
        return graph;
    }
    
    /**
     * Find cycle starting from a specific asset using DFS.
     */
    private List<String> findCycleFromAsset(String asset, Map<String, Set<String>> graph, 
            Set<String> visited, List<String> path) {
        if (path.contains(asset)) {
            // Found a cycle
            int cycleStart = path.indexOf(asset);
            return new ArrayList<>(path.subList(cycleStart, path.size()));
        }
        
        if (visited.contains(asset)) {
            return null; // Already processed this path
        }
        
        visited.add(asset);
        path.add(asset);
        
        Set<String> dependencies = graph.get(asset);
        if (dependencies != null) {
            for (String dependency : dependencies) {
                List<String> cycle = findCycleFromAsset(dependency, graph, visited, path);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        
        path.remove(path.size() - 1);
        return null;
    }
    
    /**
     * Validate asset exists in specified package.
     */
    private void validateAssetExistsInPackage(AssetType assetType, UUID assetId, UUID packageId) {
        switch (assetType) {
            case ADAPTER:
                Optional<Adapter> adapter = adapterRepository.findById(assetId);
                if (adapter.isEmpty() || !Objects.equals(adapter.get().getPackageId(), packageId)) {
                    throw new IllegalStateException("Adapter not found in package: " + assetId);
                }
                break;
            case FLOW:
                Optional<IntegrationFlow> flow = flowRepository.findById(assetId);
                if (flow.isEmpty() || !Objects.equals(flow.get().getPackageId(), packageId)) {
                    throw new IllegalStateException("Flow not found in package: " + assetId);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported asset type: " + assetType);
        }
    }
    
    // Enums and inner classes for response DTOs following encapsulation principles
    
    /**
     * Asset types supported by the container service.
     */
    public enum AssetType {
        ADAPTER, FLOW
    }
    
    /**
     * Immutable container for complete package information.
     */
    public static class PackageContainer {
        private final IntegrationPackage packageInfo;
        private final List<Adapter> adapters;
        private final List<IntegrationFlow> flows;
        private final List<PackageAssetDependency> dependencies;
        
        public PackageContainer(IntegrationPackage packageInfo, List<Adapter> adapters, 
                              List<IntegrationFlow> flows, List<PackageAssetDependency> dependencies) {
            this.packageInfo = Objects.requireNonNull(packageInfo, "Package info cannot be null");
            this.adapters = Collections.unmodifiableList(new ArrayList<>(adapters));
            this.flows = Collections.unmodifiableList(new ArrayList<>(flows));
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
        }
        
        public IntegrationPackage getPackageInfo() { return packageInfo; }
        public List<Adapter> getAdapters() { return adapters; }
        public List<IntegrationFlow> getFlows() { return flows; }
        public List<PackageAssetDependency> getDependencies() { return dependencies; }
        
        public int getTotalAssetCount() { return adapters.size() + flows.size(); }
        public boolean hasAssets() { return getTotalAssetCount() > 0; }
    }
    
    /**
     * Package assets summary with statistics.
     */
    public static class PackageAssetsSummary {
        private final IntegrationPackage packageInfo;
        private final long totalAdapters;
        private final long activeAdapters;
        private final long totalFlows;
        private final long activeFlows;
        private final long totalDependencies;
        
        public PackageAssetsSummary(IntegrationPackage packageInfo, List<Adapter> adapters, 
                                  List<IntegrationFlow> flows, List<PackageAssetDependency> dependencies) {
            this.packageInfo = Objects.requireNonNull(packageInfo, "Package info cannot be null");
            this.totalAdapters = adapters.size();
            this.activeAdapters = adapters.stream().mapToLong(a -> a.getActive() ? 1 : 0).sum();
            this.totalFlows = flows.size();
            this.activeFlows = flows.stream().mapToLong(f -> f.getActive() ? 1 : 0).sum();
            this.totalDependencies = dependencies.size();
        }
        
        public IntegrationPackage getPackageInfo() { return packageInfo; }
        public long getTotalAdapters() { return totalAdapters; }
        public long getActiveAdapters() { return activeAdapters; }
        public long getTotalFlows() { return totalFlows; }
        public long getActiveFlows() { return activeFlows; }
        public long getTotalDependencies() { return totalDependencies; }
        public long getTotalAssets() { return totalAdapters + totalFlows; }
        public long getTotalActiveAssets() { return activeAdapters + activeFlows; }
    }
    
    /**
     * Generic package asset representation.
     */
    public static class PackageAsset {
        private final AssetType assetType;
        private final UUID assetId;
        private final String name;
        private final UUID packageId;
        private final Boolean active;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
        
        public PackageAsset(AssetType assetType, UUID assetId, String name, UUID packageId, 
                          Boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.assetType = Objects.requireNonNull(assetType, "Asset type cannot be null");
            this.assetId = Objects.requireNonNull(assetId, "Asset ID cannot be null");
            this.name = Objects.requireNonNull(name, "Name cannot be null");
            this.packageId = Objects.requireNonNull(packageId, "Package ID cannot be null");
            this.active = active;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
        
        public AssetType getAssetType() { return assetType; }
        public UUID getAssetId() { return assetId; }
        public String getName() { return name; }
        public UUID getPackageId() { return packageId; }
        public Boolean getActive() { return active; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }
    
    /**
     * Asset dependency information.
     */
    public static class AssetDependencyInfo {
        private final AssetType assetType;
        private final UUID assetId;
        private final List<PackageAssetDependency> dependencies;
        private final List<PackageAssetDependency> dependents;
        
        public AssetDependencyInfo(AssetType assetType, UUID assetId, 
                                 List<PackageAssetDependency> dependencies, 
                                 List<PackageAssetDependency> dependents) {
            this.assetType = Objects.requireNonNull(assetType, "Asset type cannot be null");
            this.assetId = Objects.requireNonNull(assetId, "Asset ID cannot be null");
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
            this.dependents = Collections.unmodifiableList(new ArrayList<>(dependents));
        }
        
        public AssetType getAssetType() { return assetType; }
        public UUID getAssetId() { return assetId; }
        public List<PackageAssetDependency> getDependencies() { return dependencies; }
        public List<PackageAssetDependency> getDependents() { return dependents; }
        public boolean hasDependencies() { return !dependencies.isEmpty(); }
        public boolean hasDependents() { return !dependents.isEmpty(); }
    }
    
    /**
     * Circular dependency representation.
     */
    public static class CircularDependency {
        private final List<String> cycle;
        
        public CircularDependency(List<String> cycle) {
            this.cycle = Collections.unmodifiableList(new ArrayList<>(cycle));
        }
        
        public List<String> getCycle() { return cycle; }
        public int getCycleLength() { return cycle.size(); }
    }
    
    /**
     * Package dependency validation result.
     */
    public static class PackageDependencyValidation {
        private final UUID packageId;
        private final boolean isValid;
        private final List<CircularDependency> circularDependencies;
        
        public PackageDependencyValidation(UUID packageId, boolean isValid, 
                                         List<CircularDependency> circularDependencies) {
            this.packageId = Objects.requireNonNull(packageId, "Package ID cannot be null");
            this.isValid = isValid;
            this.circularDependencies = Collections.unmodifiableList(new ArrayList<>(circularDependencies));
        }
        
        public UUID getPackageId() { return packageId; }
        public boolean isValid() { return isValid; }
        public List<CircularDependency> getCircularDependencies() { return circularDependencies; }
        public boolean hasCircularDependencies() { return !circularDependencies.isEmpty(); }
    }
    
    /**
     * Package deployment readiness assessment.
     */
    public static class PackageDeploymentReadiness {
        private final IntegrationPackage packageInfo;
        private final boolean isReady;
        private final List<String> issues;
        private final Map<String, Object> readinessMetrics;
        
        public PackageDeploymentReadiness(IntegrationPackage packageInfo, 
                                        List<Adapter> adapters, List<IntegrationFlow> flows) {
            this.packageInfo = Objects.requireNonNull(packageInfo, "Package info cannot be null");
            this.issues = new ArrayList<>();
            this.readinessMetrics = new HashMap<>();
            
            // Assess readiness
            assessReadiness(adapters, flows);
            this.isReady = issues.isEmpty();
        }
        
        private void assessReadiness(List<Adapter> adapters, List<IntegrationFlow> flows) {
            long validatedAdapters = adapters.stream().mapToLong(a -> a.getConnectionValidated() ? 1 : 0).sum();
            long activeFlows = flows.stream().mapToLong(f -> f.getActive() ? 1 : 0).sum();
            
            readinessMetrics.put("totalAdapters", adapters.size());
            readinessMetrics.put("validatedAdapters", validatedAdapters);
            readinessMetrics.put("totalFlows", flows.size());
            readinessMetrics.put("activeFlows", activeFlows);

            if (adapters.isEmpty() && flows.isEmpty()) {
                issues.add("Package has no assets to deploy");
            }
            
            if (validatedAdapters < adapters.size()) {
                issues.add(String.format("Only %d/%d adapters have validated connections", 
                    validatedAdapters, adapters.size()));
            }
            
            if (activeFlows == 0 && !flows.isEmpty()) {
                issues.add("No active flows available for deployment");
            }
        }
        
        public IntegrationPackage getPackageInfo() { return packageInfo; }
        public boolean isReady() { return isReady; }
        public List<String> getIssues() { return Collections.unmodifiableList(issues); }
        public Map<String, Object> getReadinessMetrics() { return Collections.unmodifiableMap(readinessMetrics); }
    }
}