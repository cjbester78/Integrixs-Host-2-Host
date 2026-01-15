import React, { useState } from 'react';
import { Upload, X, FileJson, Loader2, CheckCircle, AlertTriangle } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { api } from '@/lib/api';

interface PackageImportModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImportSuccess: () => void;
}

/**
 * Package Import Modal
 * Allows users to upload and import encrypted package files
 */
export const PackageImportModal: React.FC<PackageImportModalProps> = ({
  open,
  onOpenChange,
  onImportSuccess
}) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [importResult, setImportResult] = useState<{
    success: boolean;
    message: string;
    details?: any;
  } | null>(null);

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      setImportResult(null);
    }
  };

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const file = event.dataTransfer.files?.[0];
    if (file && (file.name.endsWith('.h2hpkg') || file.name.endsWith('.json'))) {
      setSelectedFile(file);
      setImportResult(null);
    }
  };

  const handleDragOver = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
  };

  const handleImport = async () => {
    if (!selectedFile) return;

    setIsImporting(true);
    setImportResult(null);

    try {
      // Read file content
      const fileContent = await selectedFile.text();
      const packageData = JSON.parse(fileContent);

      // Call backend to import the package
      const response = await api.post('/api/packages/import', packageData);

      const result = response.data?.data || response.data;

      setImportResult({
        success: true,
        message: 'Package imported successfully!',
        details: result
      });

      // Notify parent and close after a delay
      setTimeout(() => {
        onImportSuccess();
        onOpenChange(false);
        // Reset state
        setSelectedFile(null);
        setImportResult(null);
      }, 2000);

    } catch (error: any) {
      console.error('Failed to import package:', error);
      setImportResult({
        success: false,
        message: error.response?.data?.message || error.message || 'Failed to import package'
      });
    } finally {
      setIsImporting(false);
    }
  };

  const handleClose = () => {
    if (!isImporting) {
      setSelectedFile(null);
      setImportResult(null);
      onOpenChange(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Upload className="h-5 w-5 text-primary" />
            Import Package
          </DialogTitle>
          <DialogDescription>
            Upload an encrypted package file (.h2hpkg) to import flows and adapters.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* File Upload Area */}
          {!selectedFile && !importResult && (
            <div
              className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center cursor-pointer hover:border-primary transition-colors"
              onDrop={handleDrop}
              onDragOver={handleDragOver}
              onClick={() => document.getElementById('file-input')?.click()}
            >
              <FileJson className="h-12 w-12 mx-auto mb-3 text-gray-400" />
              <p className="text-sm text-muted-foreground mb-2">
                Drag and drop your package file here, or click to browse
              </p>
              <p className="text-xs text-muted-foreground">
                Supports .h2hpkg and .json files
              </p>
              <input
                id="file-input"
                type="file"
                accept=".h2hpkg,.json"
                onChange={handleFileSelect}
                className="hidden"
              />
            </div>
          )}

          {/* Selected File Display */}
          {selectedFile && !importResult && (
            <div className="border border-gray-300 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <FileJson className="h-8 w-8 text-blue-500" />
                  <div>
                    <p className="font-medium text-sm">{selectedFile.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {(selectedFile.size / 1024).toFixed(2)} KB
                    </p>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setSelectedFile(null)}
                  disabled={isImporting}
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}

          {/* Import Result */}
          {importResult && (
            <div className={`border rounded-lg p-4 ${
              importResult.success ? 'border-green-300 bg-green-50' : 'border-red-300 bg-red-50'
            }`}>
              <div className="flex items-start gap-3">
                {importResult.success ? (
                  <CheckCircle className="h-5 w-5 text-green-600 mt-0.5" />
                ) : (
                  <AlertTriangle className="h-5 w-5 text-red-600 mt-0.5" />
                )}
                <div className="flex-1">
                  <p className={`font-medium text-sm ${
                    importResult.success ? 'text-green-800' : 'text-red-800'
                  }`}>
                    {importResult.message}
                  </p>
                  {importResult.success && importResult.details && (
                    <div className="mt-2 text-xs text-green-700 space-y-1">
                      <p>• Package: {importResult.details.packageName}</p>
                      <p>• Adapters imported: {importResult.details.adaptersImported}</p>
                      <p>• Flows imported: {importResult.details.flowsImported}</p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Action Buttons */}
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="outline"
              onClick={handleClose}
              disabled={isImporting}
            >
              <X className="h-4 w-4 mr-2" />
              Cancel
            </Button>
            <Button
              onClick={handleImport}
              disabled={!selectedFile || isImporting || importResult?.success === true}
            >
              {isImporting ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  Importing...
                </>
              ) : (
                <>
                  <Upload className="h-4 w-4 mr-2" />
                  Import Package
                </>
              )}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default PackageImportModal;
