import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, Package, Save, X } from 'lucide-react';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { NotificationModal, NotificationType } from '@/components/ui/NotificationModal';
import { api } from '@/lib/api';

/**
 * Edit Package Page - Form for editing existing integration packages
 */
const EditPackage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
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

  // Fetch existing package data
  const { data: packageData, isLoading: isLoadingPackage } = useQuery({
    queryKey: ['package', id],
    queryFn: async () => {
      const response = await api.get(`/api/packages/${id}`);
      return response.data?.data || response.data;
    },
    enabled: !!id
  });

  // Populate form when package data is loaded
  useEffect(() => {
    if (packageData) {
      setFormData({
        name: packageData.name || '',
        description: packageData.description || '',
        version: packageData.version || '1.0.0'
      });
    }
  }, [packageData]);

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
      const updateData = {
        name: formData.name.trim(),
        description: formData.description.trim(),
        version: formData.version.trim(),
        status: packageData.status // Keep the existing status
      };

      const response = await api.put(`/api/packages/${id}`, updateData);

      if (response.status === 200) {
        showNotification('success', 'Package updated successfully', 'Success');
        // Navigate back after a short delay
        setTimeout(() => {
          navigate('/packages');
        }, 1500);
      }

    } catch (error: any) {
      console.error('Failed to update package:', error);
      showNotification('error', error.response?.data?.message || error.message, 'Failed to Update Package');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCancel = () => {
    navigate('/packages');
  };

  if (isLoadingPackage) {
    return (
      <div className="container mx-auto p-6 max-w-4xl">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center gap-3">
            <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin" />
            <span className="text-muted-foreground">Loading package...</span>
          </div>
        </div>
      </div>
    );
  }

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
            <h1 className="text-3xl font-bold">Edit Integration Package</h1>
            <p className="text-muted-foreground">
              Update the details of your integration package
            </p>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit}>
        <Card>
          <CardHeader>
            <CardTitle>Integration Package Information</CardTitle>
            <CardDescription>
              Update the basic properties of your integration package
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-6">
            {/* Package Name */}
            <div className="space-y-2">
              <Label htmlFor="name" className="required">
                Package Name
              </Label>
              <Input
                id="name"
                placeholder="e.g., Customer Integration Package"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                disabled={isLoading}
                required
              />
              <p className="text-sm text-muted-foreground">
                A unique, descriptive name for your integration package
              </p>
            </div>

            {/* Description */}
            <div className="space-y-2">
              <Label htmlFor="description">
                Description
              </Label>
              <Textarea
                id="description"
                placeholder="Describe the purpose and contents of this package..."
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                disabled={isLoading}
                rows={4}
              />
              <p className="text-sm text-muted-foreground">
                Provide context about what this package contains and its purpose
              </p>
            </div>

            {/* Version */}
            <div className="space-y-2">
              <Label htmlFor="version">
                Version
              </Label>
              <Input
                id="version"
                placeholder="1.0.0"
                value={formData.version}
                onChange={(e) => setFormData({ ...formData, version: e.target.value })}
                disabled={isLoading}
              />
              <p className="text-sm text-muted-foreground">
                Semantic version for tracking package changes
              </p>
            </div>
          </CardContent>
        </Card>

        {/* Action Buttons */}
        <div className="mt-6 flex justify-end gap-3">
          <Button
            type="button"
            variant="outline"
            onClick={handleCancel}
            disabled={isLoading}
          >
            <X className="h-4 w-4 mr-2" />
            Cancel
          </Button>
          <Button
            type="submit"
            disabled={isLoading}
          >
            {isLoading ? (
              <>
                <div className="w-4 h-4 mr-2 border-2 border-background border-t-transparent rounded-full animate-spin" />
                Updating...
              </>
            ) : (
              <>
                <Save className="h-4 w-4 mr-2" />
                Update Package
              </>
            )}
          </Button>
        </div>
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

export default EditPackage;
