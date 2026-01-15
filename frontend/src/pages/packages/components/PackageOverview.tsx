import React from 'react';
import {
  Package,
  Boxes,
  Workflow,
  Activity,
  CheckCircle,
  AlertCircle
} from 'lucide-react';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

interface Adapter {
  id: string;
  status: string;
  active: boolean;
}

interface Flow {
  id: string;
  deployed: boolean;
}

interface PackageOverviewProps {
  pkg: {
    id: string;
    name: string;
    description: string;
    version: string;
    createdAt: string;
    updatedAt: string | null;
  };
  adapters: Adapter[];
  flows: Flow[];
}

const PackageOverview: React.FC<PackageOverviewProps> = ({
  pkg,
  adapters,
  flows
}) => {
  const adapterCount = adapters.length;
  const flowCount = flows.length;
  const activeAdapters = adapters.filter(a => a.active && a.status === 'STARTED').length;
  const deployedFlows = flows.filter(f => f.deployed).length;

  return (
    <div className="space-y-6">
      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold">{adapterCount}</div>
                <div className="text-sm text-muted-foreground">Total Adapters</div>
              </div>
              <Boxes className="h-8 w-8 text-blue-500" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold">{flowCount}</div>
                <div className="text-sm text-muted-foreground">Total Flows</div>
              </div>
              <Workflow className="h-8 w-8 text-purple-500" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold text-green-600">{activeAdapters}</div>
                <div className="text-sm text-muted-foreground">Active Adapters</div>
              </div>
              <CheckCircle className="h-8 w-8 text-green-500" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold text-green-600">{deployedFlows}</div>
                <div className="text-sm text-muted-foreground">Deployed Flows</div>
              </div>
              <Activity className="h-8 w-8 text-green-500" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Package Info */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Package className="h-5 w-5" />
            Package Information
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            <div>
              <div className="text-sm font-medium text-muted-foreground">Name</div>
              <div className="font-medium">{pkg.name}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Version</div>
              <div className="font-medium">v{pkg.version}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Created</div>
              <div>{new Date(pkg.createdAt).toLocaleDateString()}</div>
            </div>
          </div>
          {pkg.description && (
            <div>
              <div className="text-sm font-medium text-muted-foreground">Description</div>
              <div className="text-sm mt-1">{pkg.description}</div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Quick Actions */}
      <Card>
        <CardHeader>
          <CardTitle>Quick Actions</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 border rounded-lg hover:bg-accent cursor-pointer transition-colors">
              <Boxes className="h-6 w-6 text-blue-500 mb-2" />
              <div className="font-medium">Add Adapter</div>
              <div className="text-sm text-muted-foreground">Configure a new adapter</div>
            </div>
            <div className="p-4 border rounded-lg hover:bg-accent cursor-pointer transition-colors">
              <Workflow className="h-6 w-6 text-purple-500 mb-2" />
              <div className="font-medium">Create Flow</div>
              <div className="text-sm text-muted-foreground">Design a new integration flow</div>
            </div>
            <div className="p-4 border rounded-lg hover:bg-accent cursor-pointer transition-colors">
              <Activity className="h-6 w-6 text-green-500 mb-2" />
              <div className="font-medium">View Monitoring</div>
              <div className="text-sm text-muted-foreground">Check execution status</div>
            </div>
            <div className="p-4 border rounded-lg hover:bg-accent cursor-pointer transition-colors">
              <AlertCircle className="h-6 w-6 text-orange-500 mb-2" />
              <div className="font-medium">View Logs</div>
              <div className="text-sm text-muted-foreground">Check recent activity</div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default PackageOverview;
