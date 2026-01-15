import React from 'react';
import { Workflow, Plug, FileText, Eye } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface WorkspaceSidebarProps {
  packageId: string;
  onNavigate: (section: string) => void;
  activeSection?: string;
  className?: string;
}

export const WorkspaceSidebar: React.FC<WorkspaceSidebarProps> = ({
  onNavigate,
  activeSection,
  className
}) => {
  const menuItems = [
    { id: 'overview', name: 'Dashboard', icon: Eye },
    { id: 'adapters', name: 'Adapters', icon: Plug },
    { id: 'flows', name: 'Flows', icon: Workflow }
  ];

  return (
    <div className={cn(
      "h-full bg-card border-r border-border w-56 flex-shrink-0",
      className
    )}>
      <div className="flex flex-col h-full">
        {/* Header */}
        <div className="p-4 border-b border-border">
          <div className="flex items-center gap-2">
            <div className="p-2 bg-primary/10 rounded-lg">
              <FileText className="h-4 w-4 text-primary" />
            </div>
            <div className="flex-1">
              <h2 className="text-sm font-medium">Package Workspace</h2>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 p-3 space-y-1">
          {menuItems.map((item) => {
            const isActive = activeSection === item.id;
            return (
              <Button
                key={item.id}
                variant={isActive ? 'secondary' : 'ghost'}
                className={cn(
                  "w-full justify-start gap-3 h-10",
                  isActive && "bg-primary/10 text-primary"
                )}
                onClick={() => onNavigate(item.id)}
              >
                <item.icon className="h-4 w-4" />
                {item.name}
              </Button>
            );
          })}
        </nav>
      </div>
    </div>
  );
};

export default WorkspaceSidebar;
