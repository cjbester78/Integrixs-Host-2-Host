import React from 'react';
import { formatDistanceToNow } from 'date-fns';
import {
  Package,
  Boxes,
  Workflow,
  CheckCircle,
  Clock,
  Archive,
  AlertTriangle,
  Edit,
  Trash2,
  FolderOpen,
  Download,
  Eye
} from 'lucide-react';

import { PackageSummary, PackageStatus } from '@/types/package';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface PackageCardProps {
  packageItem: PackageSummary;
  onSelect: (packageItem: PackageSummary) => void;
  isSelected: boolean;
  onToggleSelect: (selected: boolean) => void;
  onEdit?: (packageItem: PackageSummary) => void;
  onArchive?: (packageItem: PackageSummary) => void;
  onDelete?: (packageItem: PackageSummary) => void;
  onExport?: (packageItem: PackageSummary) => void;
}

/**
 * Package card component for displaying package information in grid view
 */
const PackageCard: React.FC<PackageCardProps> = ({
  packageItem: pkg,
  onSelect,
  isSelected,
  onToggleSelect,
  onEdit,
  onArchive,
  onDelete,
  onExport
}) => {
  // Status configuration - all packages are always active
  const statusInfo = {
    icon: CheckCircle,
    variant: 'default' as const,
    color: 'text-green-600',
    label: 'Active'
  };
  const StatusIcon = statusInfo.icon;
  
  // Event handlers
  const handleCardClick = (e: React.MouseEvent) => {
    // Prevent selection toggle when clicking action buttons
    if ((e.target as HTMLElement).closest('[data-action]') ||
        (e.target as HTMLElement).closest('button')) {
      return;
    }

    onSelect(pkg);
  };

  const handleSelectToggle = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.stopPropagation();
    onToggleSelect(e.target.checked);
  };
  
  return (
    <Card
      className={cn(
        "group relative cursor-pointer transition-all duration-200 hover:shadow-md",
        "border border-border hover:border-primary/50 flex flex-col",
        isSelected && "ring-2 ring-primary ring-opacity-50 bg-primary/5"
      )}
      onClick={handleCardClick}
    >
      {/* Selection indicator */}
      <div className="absolute top-3 left-3 z-10">
        <input
          type="checkbox"
          checked={isSelected}
          onChange={handleSelectToggle}
          className="rounded border-gray-300 focus:ring-primary"
          data-action="select"
        />
      </div>

      <CardHeader className="pb-3 pt-10">
        <div className="flex items-center gap-2">
          <Package className="h-5 w-5 text-primary" />
          <div className="flex-1 min-w-0">
            <h3 className="font-semibold truncate">{pkg.name}</h3>
            <div className="flex items-center gap-2 mt-1">
              <Badge variant={statusInfo.variant} className="text-xs">
                <StatusIcon className="h-3 w-3 mr-1" />
                {statusInfo.label}
              </Badge>
            </div>
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-4 flex-1 flex flex-col">
        {/* Description */}
        {pkg.description && (
          <p className="text-sm text-muted-foreground line-clamp-2">
            {pkg.description}
          </p>
        )}

        {/* Asset Statistics */}
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div className="flex items-center gap-2">
            <Boxes className="h-4 w-4 text-blue-500" />
            <div>
              <div className="font-medium">{pkg.adapterCount}</div>
              <div className="text-xs text-muted-foreground">Adapters</div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Workflow className="h-4 w-4 text-purple-500" />
            <div>
              <div className="font-medium">{pkg.flowCount}</div>
              <div className="text-xs text-muted-foreground">Flows</div>
            </div>
          </div>
        </div>

        {/* Active Assets Summary */}
        {pkg.totalActiveAssetCount > 0 && (
          <div className="flex items-center gap-2 text-sm">
            <div className="h-2 w-2 bg-green-500 rounded-full" />
            <span className="text-muted-foreground">
              {pkg.totalActiveAssetCount} active asset{pkg.totalActiveAssetCount !== 1 ? 's' : ''}
            </span>
          </div>
        )}

        {/* Tags */}
        {pkg.tags && pkg.tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {pkg.tags.slice(0, 3).map((tag) => (
              <Badge key={tag} variant="secondary" className="text-xs px-2 py-0.5">
                {tag}
              </Badge>
            ))}
            {pkg.tags.length > 3 && (
              <Badge variant="secondary" className="text-xs px-2 py-0.5">
                +{pkg.tags.length - 3}
              </Badge>
            )}
          </div>
        )}

        {/* Spacer to push footer and actions to bottom */}
        <div className="flex-1" />

        {/* Footer */}
        <div className="pt-2 border-t border-border/50">
          <div className="flex items-center justify-between text-xs text-muted-foreground mb-3">
            <div>v{pkg.version}</div>
            <div>
              {pkg.updatedAt
                ? `Updated ${formatDistanceToNow(new Date(pkg.updatedAt), { addSuffix: true })}`
                : `Created ${formatDistanceToNow(new Date(pkg.createdAt), { addSuffix: true })}`
              }
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex gap-2 flex-wrap justify-end">
            <Button
              variant="outline"
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                onSelect(pkg);
              }}
            >
              <Eye className="h-3 w-3 mr-1" />
              View Details
            </Button>
            {onEdit && (
              <Button
                variant="outline"
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  onEdit(pkg);
                }}
              >
                <Edit className="h-3 w-3 mr-1" />
                Edit
              </Button>
            )}
            {onExport && (
              <Button
                variant="outline"
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  onExport(pkg);
                }}
              >
                <Download className="h-3 w-3 mr-1" />
                Export
              </Button>
            )}
            {onDelete && (
              <Button
                variant="outline"
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(pkg);
                }}
                className="text-red-600 hover:text-red-700 hover:bg-red-50"
              >
                <Trash2 className="h-3 w-3 mr-1" />
                Delete
              </Button>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

export default PackageCard;