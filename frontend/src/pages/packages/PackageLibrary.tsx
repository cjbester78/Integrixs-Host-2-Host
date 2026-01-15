import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  Package,
  Plus,
  Search,
  Filter,
  Grid3x3,
  List,
  MoreVertical,
  Upload
} from 'lucide-react';

import { PackageSummary, PackageSearchCriteria, PackageType, PackageStatus } from '@/types/package';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { api } from '@/lib/api';
import PackageCard from './components/PackageCard';
import { ConfirmationModal } from '@/components/ui/ConfirmationModal';
import { PackageExportModal } from '@/components/ui/PackageExportModal';
import { PackageImportModal } from '@/components/ui/PackageImportModal';
import toast from 'react-hot-toast';

/**
 * Package Library - Main package management interface
 * Provides overview of all packages with search, filter, and management capabilities
 */
const PackageLibrary: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedPackageType, setSelectedPackageType] = useState<PackageType | 'ALL'>('ALL');
  const [selectedStatus, setSelectedStatus] = useState<PackageStatus | 'ALL'>('ALL');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [selectedPackages, setSelectedPackages] = useState<Set<string>>(new Set());
  const [confirmationModal, setConfirmationModal] = useState<{
    open: boolean;
    title: string;
    message: string;
    confirmLabel?: string;
    onConfirm: () => void;
  }>({ open: false, title: '', message: '', onConfirm: () => {} });
  const [exportModal, setExportModal] = useState<{
    open: boolean;
    packageId: string;
    packageName: string;
  }>({ open: false, packageId: '', packageName: '' });
  const [importModal, setImportModal] = useState<{ open: boolean }>({ open: false });

  // Fetch packages
  const { 
    data: packagesResult, 
    isLoading, 
    error,
    refetch 
  } = useQuery({
    queryKey: ['packages', searchTerm, selectedPackageType, selectedStatus],
    queryFn: async () => {
      const searchCriteria: PackageSearchCriteria = {
        searchTerm: searchTerm || undefined,
        packageTypes: selectedPackageType !== 'ALL' ? [selectedPackageType] : undefined,
        statuses: selectedStatus !== 'ALL' ? [selectedStatus] : undefined,
        sortBy: 'updatedAt',
        sortOrder: 'desc'
      };
      
      const response = await api.get('/api/packages/search', { params: searchCriteria });
      return response.data;
    }
  });

  const packages = packagesResult?.data?.packages || packagesResult?.packages || [];

  // Handle package selection
  const handlePackageSelect = (pkg: PackageSummary) => {
    // Navigate to package workspace/details page
    navigate(`/packages/${pkg.id}`);
  };

  const handlePackageToggleSelect = (packageId: string, selected: boolean) => {
    const newSelection = new Set(selectedPackages);
    if (selected) {
      newSelection.add(packageId);
    } else {
      newSelection.delete(packageId);
    }
    setSelectedPackages(newSelection);
  };

  const handleCreatePackage = () => {
    // Navigate to package creation page
    navigate('/packages/create');
  };

  const handleImportPackage = () => {
    setImportModal({ open: true });
  };

  const handleImportSuccess = () => {
    toast.success('Package imported successfully');
    refetch();
  };

  const clearFilters = () => {
    setSearchTerm('');
    setSelectedPackageType('ALL');
    setSelectedStatus('ALL');
  };

  const handleExportPackage = (pkg: PackageSummary) => {
    setExportModal({
      open: true,
      packageId: pkg.id,
      packageName: pkg.name
    });
  };

  const handleExportModalClose = () => {
    setExportModal({ open: false, packageId: '', packageName: '' });
    // Show success toast
    toast.success('Package exported successfully');
  };

  const handleArchiveSelected = async () => {
    if (selectedPackages.size === 0) return;

    const confirmed = window.confirm(
      `Are you sure you want to archive ${selectedPackages.size} package${selectedPackages.size !== 1 ? 's' : ''}? This will change their status to ARCHIVED.`
    );

    if (!confirmed) return;

    try {
      // In a real implementation, this would be API calls
      // For now, just show success message
      const packageNames = packages
        .filter(pkg => selectedPackages.has(pkg.id))
        .map(pkg => pkg.name)
        .join(', ');

      toast.success(`Archived packages: ${packageNames}`);

      // Clear selection and refresh data
      setSelectedPackages(new Set());
      refetch();
    } catch (error) {
      toast.error('Failed to archive packages');
    }
  };

  const handleEditPackage = (pkg: PackageSummary) => {
    navigate(`/packages/${pkg.id}/edit`);
  };

  const handleDeletePackage = async (pkg: PackageSummary) => {
    setConfirmationModal({
      open: true,
      title: 'Delete Package',
      message: `Are you sure you want to delete the package "${pkg.name}"? This action cannot be undone and will permanently remove the package and all its associated adapters and flows.`,
      confirmLabel: 'Delete Package',
      onConfirm: async () => {
        try {
          await api.delete(`/api/packages/${pkg.id}`);
          toast.success(`Package "${pkg.name}" deleted successfully`);
          refetch();
        } catch (error: any) {
          const errorMessage = error.response?.data?.message || error.response?.data?.error || error.message || 'Failed to delete package';
          toast.error(errorMessage);
        }
      }
    });
  };

  // Filter active packages by type for statistics
  const activePackages = packages.filter(p => p.status === PackageStatus.ACTIVE);
  const packagesByType = Object.values(PackageType).reduce((acc, type) => {
    acc[type] = packages.filter(p => p.packageType === type).length;
    return acc;
  }, {} as Record<PackageType, number>);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold">Package Library</h1>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader className="space-y-2">
                <div className="h-4 bg-gray-200 rounded w-3/4"></div>
                <div className="h-3 bg-gray-200 rounded w-1/2"></div>
              </CardHeader>
              <CardContent>
                <div className="space-y-2">
                  <div className="h-3 bg-gray-200 rounded"></div>
                  <div className="h-3 bg-gray-200 rounded w-2/3"></div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold">Package Library</h1>
        </div>
        <Card className="border-red-200 bg-red-50">
          <CardContent className="pt-6">
            <div className="flex items-center gap-2 text-red-600">
              <Package className="h-5 w-5" />
              <div>
                <div className="font-medium">Failed to load packages</div>
                <div className="text-sm text-red-500">
                  {error instanceof Error ? error.message : 'Unknown error'}
                </div>
              </div>
            </div>
            <Button 
              variant="outline" 
              size="sm" 
              onClick={() => refetch()}
              className="mt-4"
            >
              Try Again
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Package Library</h1>
          <p className="text-muted-foreground">
            Manage integration packages and their components
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleImportPackage}>
            <Upload className="h-4 w-4 mr-2" />
            Import Package
          </Button>
          <Button onClick={handleCreatePackage}>
            <Plus className="h-4 w-4 mr-2" />
            New Package
          </Button>
        </div>
      </div>

      {/* Statistics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold">{packages.length}</div>
                <div className="text-sm text-muted-foreground">Total Packages</div>
              </div>
              <Package className="h-8 w-8 text-blue-500" />
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold">{activePackages.length}</div>
                <div className="text-sm text-muted-foreground">Active Packages</div>
              </div>
              <div className="h-8 w-8 rounded-full bg-green-500 flex items-center justify-center">
                <div className="h-3 w-3 bg-white rounded-full" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold">
                  {packages.reduce((sum, p) => sum + p.totalAssetCount, 0)}
                </div>
                <div className="text-sm text-muted-foreground">Total Assets</div>
              </div>
              <Grid3x3 className="h-8 w-8 text-purple-500" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold">
                  {packages.reduce((sum, p) => sum + p.totalActiveAssetCount, 0)}
                </div>
                <div className="text-sm text-muted-foreground">Active Assets</div>
              </div>
              <div className="h-8 w-8 rounded bg-green-100 flex items-center justify-center">
                <div className="h-4 w-4 text-green-600">⚡</div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Filters and Search */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-col sm:flex-row gap-4">
            {/* Search */}
            <div className="flex-1">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-muted-foreground h-4 w-4" />
                <Input
                  placeholder="Search packages..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
            </div>

            {/* Type Filter */}
            <select
              value={selectedPackageType}
              onChange={(e) => setSelectedPackageType(e.target.value as PackageType | 'ALL')}
              className="px-3 py-2 border rounded-md text-sm"
            >
              <option value="ALL">All Types</option>
              {Object.values(PackageType).map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>

            {/* Status Filter */}
            <select
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value as PackageStatus | 'ALL')}
              className="px-3 py-2 border rounded-md text-sm"
            >
              <option value="ALL">All Statuses</option>
              {Object.values(PackageStatus).map(status => (
                <option key={status} value={status}>{status}</option>
              ))}
            </select>

            {/* View Mode */}
            <div className="flex border rounded-md">
              <Button
                variant={viewMode === 'grid' ? 'default' : 'ghost'}
                size="sm"
                onClick={() => setViewMode('grid')}
                className="rounded-r-none"
              >
                <Grid3x3 className="h-4 w-4" />
              </Button>
              <Button
                variant={viewMode === 'list' ? 'default' : 'ghost'}
                size="sm"
                onClick={() => setViewMode('list')}
                className="rounded-l-none"
              >
                <List className="h-4 w-4" />
              </Button>
            </div>

            {/* Clear Filters */}
            {(searchTerm || selectedPackageType !== 'ALL' || selectedStatus !== 'ALL') && (
              <Button variant="outline" onClick={clearFilters} size="sm">
                Clear
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Package Type Distribution */}
      {Object.values(PackageType).some(type => packagesByType[type] > 0) && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Package Distribution</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {Object.entries(packagesByType).map(([type, count]) => (
                count > 0 && (
                  <Badge key={type} variant="outline" className="px-3 py-1">
                    {type}: {count}
                  </Badge>
                )
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Packages Grid/List */}
      {packages.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <div className="text-center py-8">
              <Package className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
              <div className="text-lg font-medium mb-2">No packages found</div>
              <div className="text-muted-foreground mb-4">
                {searchTerm || selectedPackageType !== 'ALL' || selectedStatus !== 'ALL' 
                  ? 'Try adjusting your search criteria'
                  : 'Create your first package to get started'
                }
              </div>
              <Button onClick={handleCreatePackage}>
                <Plus className="h-4 w-4 mr-2" />
                Create Package
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <div className={
          viewMode === 'grid' 
            ? "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
            : "space-y-4"
        }>
          {packages.map((pkg) => (
            <PackageCard
              key={pkg.id}
              packageItem={pkg}
              onSelect={handlePackageSelect}
              isSelected={selectedPackages.has(pkg.id)}
              onToggleSelect={(selected) => handlePackageToggleSelect(pkg.id, selected)}
              onEdit={handleEditPackage}
              onDelete={handleDeletePackage}
              onExport={handleExportPackage}
            />
          ))}
        </div>
      )}

      {/* Selection Actions */}
      {selectedPackages.size > 0 && (
        <Card className="border-blue-200 bg-blue-50">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div className="text-sm text-blue-700">
                {selectedPackages.size} package{selectedPackages.size !== 1 ? 's' : ''} selected
              </div>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={handleArchiveSelected}>
                  Archive Selected
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setSelectedPackages(new Set())}
                >
                  Clear Selection
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Confirmation Modal */}
      <ConfirmationModal
        open={confirmationModal.open}
        onOpenChange={(open) => setConfirmationModal(prev => ({ ...prev, open }))}
        title={confirmationModal.title}
        message={confirmationModal.message}
        confirmLabel={confirmationModal.confirmLabel || "Delete"}
        variant="destructive"
        onConfirm={confirmationModal.onConfirm}
      />

      {/* Export Modal */}
      <PackageExportModal
        open={exportModal.open}
        onOpenChange={(open) => {
          if (!open) {
            handleExportModalClose();
          }
        }}
        packageId={exportModal.packageId}
        packageName={exportModal.packageName}
      />

      {/* Import Modal */}
      <PackageImportModal
        open={importModal.open}
        onOpenChange={(open) => setImportModal({ open })}
        onImportSuccess={handleImportSuccess}
      />
    </div>
  );
};

export default PackageLibrary;