import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, Package, Save, X } from 'lucide-react';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { NotificationModal, NotificationType } from '@/components/ui/NotificationModal';
import { api } from '@/lib/api';

/**
 * Create Package Page - Form for creating new integration packages
 */
const CreatePackage: React.FC = () => {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    version: '1.0.0'
  });
  const [notificationModal, setNotificationModal] = useState<{
    open: boolean;
    type: NotificationType;
    title?: string;
    message: string;
  }>({ open: false, type: 'info', message: '' });

  const showNotification = (type: NotificationType, message: string, title?: string) => {
    setNotificationModal({ open: true, type, title, message });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name) {
      showNotification('warning', 'Please fill in the package name.', 'Validation Error');
      return;
    }

    setIsLoading(true);

    try {
      const packageData = {
        name: formData.name.trim(),
        description: formData.description.trim(),
        version: formData.version.trim()
      };

      const response = await api.post('/api/packages', packageData);

      if (response.status === 201 || response.status === 200) {
        // Navigate back to package library
        navigate('/packages');
      }

    } catch (error: any) {
      console.error('Failed to create package:', error);
      showNotification('error', error.response?.data?.message || error.message, 'Failed to Create Package');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCancel = () => {
    navigate('/packages');
  };

  return (
    <div className="container mx-auto p-6 max-w-4xl">
      {/* Header */}
      <div className="mb-6">
        <Button 
          variant="ghost" 
          onClick={handleCancel}
          className="mb-4 p-0 h-auto text-muted-foreground hover:text-foreground"
        >
          <ChevronLeft className="h-4 w-4 mr-1" />
          Back to Package Library
        </Button>
        
        <div className="flex items-center gap-3">
          <Package className="h-8 w-8 text-primary" />
          <div>
            <h1 className="text-3xl font-bold">Create New Integration Package</h1>
            <p className="text-muted-foreground">
              Create a new integration package to organize your adapters and flows
            </p>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit}>
        <Card>
          <CardHeader>
            <CardTitle>Integration Package Information</CardTitle>
            <CardDescription>
              Define the basic properties of your integration package
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            
            {/* Package Name */}
            <div className="space-y-2">
              <Label htmlFor="name">Package Name *</Label>
              <Input
                id="name"
                value={formData.name}
                onChange={(e) => setFormData(prev => ({ ...prev, name: e.target.value }))}
                placeholder="Enter package name"
                required
              />
            </div>

            {/* Description */}
            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                value={formData.description}
                onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                placeholder="Describe the purpose of this package"
                rows={3}
              />
            </div>

            {/* Version */}
            <div className="space-y-2">
              <Label htmlFor="version">Version</Label>
              <Input
                id="version"
                value={formData.version}
                onChange={(e) => setFormData(prev => ({ ...prev, version: e.target.value }))}
                placeholder="1.0.0"
              />
            </div>

            {/* Actions */}
            <div className="flex justify-end space-x-3 pt-6 border-t">
              <Button type="button" variant="outline" onClick={handleCancel}>
                <X className="h-4 w-4 mr-2" />
                Cancel
              </Button>
              <Button type="submit" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <div className="animate-spin h-4 w-4 mr-2 border-2 border-current border-t-transparent rounded-full" />
                    Creating...
                  </>
                ) : (
                  <>
                    <Save className="h-4 w-4 mr-2" />
                    Create Package
                  </>
                )}
              </Button>
            </div>
          </CardContent>
        </Card>
      </form>

      {/* Notification Modal */}
      <NotificationModal
        open={notificationModal.open}
        onOpenChange={(open) => setNotificationModal(prev => ({ ...prev, open }))}
        type={notificationModal.type}
        title={notificationModal.title}
        message={notificationModal.message}
      />
    </div>
  );
};

export default CreatePackage;