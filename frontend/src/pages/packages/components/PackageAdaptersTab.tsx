import React from 'react';
import {
  FileText,
  Server,
  Mail,
  Plus,
  Settings,
  Play,
  Trash2,
  Eye
} from 'lucide-react';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

interface Adapter {
  id: string;
  name: string;
  type: string;
  direction: string;
  status: string;
  active: boolean;
  description?: string;
  adapterType?: string;
}

interface PackageAdaptersTabProps {
  packageId: string;
  adapters: Adapter[];
  onAddAdapter?: () => void;
  onViewAdapter?: (adapter: Adapter) => void;
  onEditAdapter?: (adapter: Adapter) => void;
  onDeleteAdapter?: (adapter: Adapter) => void;
  onTestAdapter?: (adapter: Adapter) => void;
}

const PackageAdaptersTab: React.FC<PackageAdaptersTabProps> = ({
  packageId,
  adapters,
  onAddAdapter,
  onViewAdapter,
  onEditAdapter,
  onDeleteAdapter,
  onTestAdapter
}) => {
  const getAdapterIcon = (adapterType: string) => {
    switch (adapterType) {
      case 'FILE':
        return <FileText className="h-5 w-5" />;
      case 'SFTP':
        return <Server className="h-5 w-5" />;
      case 'EMAIL':
        return <Mail className="h-5 w-5" />;
      default:
        return <Settings className="h-5 w-5" />;
    }
  };

  // Get adapter type - handle both 'type' and 'adapterType' properties
  const getType = (adapter: Adapter) => adapter.adapterType || adapter.type;

  // Count adapters by type
  const fileAdaptersCount = adapters.filter(a => getType(a) === 'FILE').length;
  const sftpAdaptersCount = adapters.filter(a => getType(a) === 'SFTP').length;
  const emailAdaptersCount = adapters.filter(a => getType(a) === 'EMAIL').length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Adapter Configuration</h2>
          <p className="text-sm text-muted-foreground">
            Configure and manage your file transfer adapters
          </p>
        </div>
        <Button size="sm" onClick={onAddAdapter}>
          <Plus className="h-4 w-4 mr-2" />
          Create Adapter
        </Button>
      </div>

      {/* Statistics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center space-x-3 mb-3">
            <FileText className="h-8 w-8 text-primary" />
            <div>
              <h3 className="font-semibold text-foreground">File Adapters</h3>
              <p className="text-sm text-muted-foreground">Local file system operations</p>
            </div>
          </div>
          <div className="text-2xl font-bold text-foreground">
            {fileAdaptersCount}
          </div>
        </div>

        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center space-x-3 mb-3">
            <Server className="h-8 w-8 text-info" />
            <div>
              <h3 className="font-semibold text-foreground">SFTP Adapters</h3>
              <p className="text-sm text-muted-foreground">Secure file transfer protocol</p>
            </div>
          </div>
          <div className="text-2xl font-bold text-foreground">
            {sftpAdaptersCount}
          </div>
        </div>

        <div className="app-card rounded-lg p-6 border">
          <div className="flex items-center space-x-3 mb-3">
            <Mail className="h-8 w-8 text-success" />
            <div>
              <h3 className="font-semibold text-foreground">Email Adapters</h3>
              <p className="text-sm text-muted-foreground">Email-based file transfer</p>
            </div>
          </div>
          <div className="text-2xl font-bold text-foreground">
            {emailAdaptersCount}
          </div>
        </div>
      </div>

      {/* Empty State */}
      {adapters.length === 0 ? (
        <div className="app-card rounded-lg p-12 border text-center">
          <Settings className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
          <h3 className="text-xl font-semibold text-foreground mb-2">No Adapters Configured</h3>
          <p className="text-muted-foreground mb-6">Get started by creating your first adapter interface</p>
          <Button onClick={onAddAdapter}>
            <Plus className="h-4 w-4 mr-2" />
            Create Your First Adapter
          </Button>
        </div>
      ) : (
        /* Adapter Grid */
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {adapters.map((adapter) => (
            <Card key={adapter.id} className="app-card border">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    {getAdapterIcon(getType(adapter))}
                    <div>
                      <CardTitle className="text-lg text-foreground">{adapter.name}</CardTitle>
                      {adapter.description && (
                        <p className="text-sm text-muted-foreground">{adapter.description}</p>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center space-x-2">
                    <div className={`w-3 h-3 rounded-full ${
                      adapter.active ? 'bg-success animate-glow' : 'bg-muted-foreground'
                    }`} />
                    <span className={`text-xs px-2 py-1 rounded ${
                      adapter.active ? 'bg-success/20 text-success' : 'bg-muted/20 text-muted-foreground'
                    }`}>
                      {adapter.active ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">Type:</span>
                      <span className="ml-2 text-foreground">{getType(adapter)}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">Direction:</span>
                      <span className="ml-2 text-foreground">{adapter.direction}</span>
                    </div>
                  </div>

                  <div className="flex items-center space-x-2 pt-4">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onViewAdapter?.(adapter)}
                    >
                      <Eye className="h-4 w-4 mr-2" />
                      View
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onEditAdapter?.(adapter)}
                    >
                      <Settings className="h-4 w-4 mr-2" />
                      Configure
                    </Button>
                    {/* Only show Test button for adapters with external connection settings (SFTP, EMAIL) */}
                    {getType(adapter) !== 'FILE' && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onTestAdapter?.(adapter)}
                      >
                        <Play className="h-4 w-4 mr-2" />
                        Test
                      </Button>
                    )}
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-destructive hover:bg-destructive/10"
                      onClick={() => onDeleteAdapter?.(adapter)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};

export default PackageAdaptersTab;
