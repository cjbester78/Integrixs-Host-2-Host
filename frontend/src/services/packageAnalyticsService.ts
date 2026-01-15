import { PackageAsset, PackageSummary, AssetType } from '@/types/package';

/**
 * Package Analytics Service - Simplified version with correct types
 */

export interface PackageAnalyticsData {
  totalAssets: number;
  activeAssets: number;
  assetsByType: Record<AssetType, number>;
  packageUtilization: number;
}

export class PackageAnalyticsService {
  
  /**
   * Calculate basic analytics for a package
   */
  static calculatePackageAnalytics(assets: PackageAsset[]): PackageAnalyticsData {
    const totalAssets = assets.length;
    const activeAssets = assets.filter(asset => asset.active).length;
    
    const assetsByType = assets.reduce((acc, asset) => {
      acc[asset.assetType] = (acc[asset.assetType] || 0) + 1;
      return acc;
    }, {} as Record<AssetType, number>);
    
    // Ensure all asset types have a value
    Object.values(AssetType).forEach(type => {
      if (!(type in assetsByType)) {
        assetsByType[type] = 0;
      }
    });
    
    const packageUtilization = totalAssets > 0 ? (activeAssets / totalAssets) * 100 : 0;
    
    return {
      totalAssets,
      activeAssets,
      assetsByType,
      packageUtilization
    };
  }

  /**
   * Get package summary statistics
   */
  static getPackageSummaryStats(packages: PackageSummary[]) {
    const totalPackages = packages.length;
    const activePackages = packages.filter(pkg => pkg.hasActiveAssets).length;
    const totalAssets = packages.reduce((sum, pkg) => sum + pkg.totalAssetCount, 0);
    const activeAssets = packages.reduce((sum, pkg) => sum + pkg.totalActiveAssetCount, 0);
    
    return {
      totalPackages,
      activePackages,
      totalAssets,
      activeAssets,
      averageAssetsPerPackage: totalPackages > 0 ? totalAssets / totalPackages : 0,
      utilizationRate: totalAssets > 0 ? (activeAssets / totalAssets) * 100 : 0
    };
  }
}

export default PackageAnalyticsService;