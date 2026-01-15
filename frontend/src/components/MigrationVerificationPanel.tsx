import React, { useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { 
  CheckCircle, 
  XCircle, 
  Clock, 
  Database,
  Package,
  Workflow,
  AlertTriangle
} from 'lucide-react';
import { api } from '@/lib/api';

/**
 * Migration Verification Panel Component
 * Provides UI for testing Phase 7 cleanup and migration integrity
 * Follows OOP principles with proper state management and error handling
 */

interface VerificationResult {
  status: 'success' | 'failed' | 'error';
  message: string;
  details?: any;
  issues?: string[];
}

interface VerificationState {
  loading: boolean;
  results: Record<string, VerificationResult>;
  lastVerified?: Date;
}

const MigrationVerificationPanel: React.FC = () => {
  const [state, setState] = useState<VerificationState>({
    loading: false,
    results: {}
  });

  const verificationChecks = [
    {
      key: 'adapter-packages',
      title: 'Adapter-Package Associations',
      description: 'Verify all adapters are properly associated with packages',
      icon: Package,
      endpoint: '/api/migration/verify/adapter-packages'
    },
    {
      key: 'flow-packages', 
      title: 'Flow-Package Associations',
      description: 'Verify all flows are properly associated with packages',
      icon: Workflow,
      endpoint: '/api/migration/verify/flow-packages'
    },
    {
      key: 'data-integrity',
      title: 'Data Integrity',
      description: 'Verify overall data integrity after migration',
      icon: Database,
      endpoint: '/api/migration/verify/data-integrity'
    }
  ];

  const runIndividualVerification = async (check: typeof verificationChecks[0]) => {
    setState(prev => ({
      ...prev,
      loading: true,
      results: {
        ...prev.results,
        [check.key]: { status: 'error', message: 'Running...', loading: true } as any
      }
    }));

    try {
      const response = await api.get(check.endpoint);
      setState(prev => ({
        ...prev,
        results: {
          ...prev.results,
          [check.key]: response.data
        },
        lastVerified: new Date()
      }));
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        results: {
          ...prev.results,
          [check.key]: {
            status: 'error',
            message: error.response?.data?.message || 'Verification failed',
            details: error.response?.data
          }
        }
      }));
    } finally {
      setState(prev => ({ ...prev, loading: false }));
    }
  };

  const runComprehensiveVerification = async () => {
    setState(prev => ({ ...prev, loading: true, results: {} }));

    try {
      const response = await api.get('/api/migration/verify/comprehensive');
      const comprehensiveResult = response.data;
      
      // Extract individual results from comprehensive response
      const detailedResults = comprehensiveResult.summary?.detailedResults || {};
      const newResults: Record<string, VerificationResult> = {};
      
      verificationChecks.forEach(check => {
        const key = check.key.replace('-', '');
        if (detailedResults[key + 'Associations'] || detailedResults[key]) {
          const result = detailedResults[key + 'Associations'] || detailedResults[key];
          newResults[check.key] = {
            status: result.successful ? 'success' : 'failed',
            message: result.message,
            details: result.details,
            issues: result.issues
          };
        }
      });

      setState(prev => ({
        ...prev,
        results: newResults,
        lastVerified: new Date(),
        loading: false
      }));

    } catch (error: any) {
      console.error('Comprehensive verification failed:', error);
      setState(prev => ({
        ...prev,
        loading: false,
        results: {
          error: {
            status: 'error',
            message: 'Comprehensive verification failed',
            details: error.response?.data
          }
        }
      }));
    }
  };

  const getStatusIcon = (result?: VerificationResult) => {
    if (!result) return <Clock className="h-4 w-4 text-muted-foreground" />;
    
    switch (result.status) {
      case 'success':
        return <CheckCircle className="h-4 w-4 text-green-600" />;
      case 'failed':
        return <XCircle className="h-4 w-4 text-red-600" />;
      case 'error':
        return <AlertTriangle className="h-4 w-4 text-orange-600" />;
      default:
        return <Clock className="h-4 w-4 text-muted-foreground" />;
    }
  };

  const getStatusBadge = (result?: VerificationResult) => {
    if (!result) {
      return <Badge variant="outline">Not Run</Badge>;
    }

    switch (result.status) {
      case 'success':
        return <Badge className="bg-green-100 text-green-800 hover:bg-green-200">Passed</Badge>;
      case 'failed':
        return <Badge variant="destructive">Failed</Badge>;
      case 'error':
        return <Badge className="bg-orange-100 text-orange-800 hover:bg-orange-200">Error</Badge>;
      default:
        return <Badge variant="outline">Unknown</Badge>;
    }
  };

  const calculateOverallProgress = () => {
    const totalChecks = verificationChecks.length;
    const completedChecks = Object.keys(state.results).length;
    return (completedChecks / totalChecks) * 100;
  };

  const allChecksPassed = () => {
    return verificationChecks.every(check => 
      state.results[check.key]?.status === 'success'
    );
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Database className="h-5 w-5" />
            Migration Verification
          </CardTitle>
          <CardDescription>
            Verify Phase 7 cleanup and migration integrity
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Progress Overview */}
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span>Verification Progress</span>
              <span>{Object.keys(state.results).length}/{verificationChecks.length} checks</span>
            </div>
            <Progress value={calculateOverallProgress()} className="w-full" />
          </div>

          {/* Action Buttons */}
          <div className="flex gap-2">
            <Button 
              onClick={runComprehensiveVerification}
              disabled={state.loading}
              className="flex items-center gap-2"
            >
              <Database className="h-4 w-4" />
              Run All Checks
            </Button>
            
            {state.lastVerified && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Clock className="h-4 w-4" />
                Last verified: {state.lastVerified.toLocaleTimeString()}
              </div>
            )}
          </div>

          {/* Overall Status Alert */}
          {Object.keys(state.results).length > 0 && (
            <Alert className={allChecksPassed() ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'}>
              <AlertTriangle className="h-4 w-4" />
              <AlertDescription>
                {allChecksPassed() 
                  ? '✅ All migration verification checks passed!'
                  : '❌ One or more verification checks failed. Please review the issues below.'
                }
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>

      {/* Individual Verification Checks */}
      <div className="grid gap-4">
        {verificationChecks.map((check) => {
          const result = state.results[check.key];
          const Icon = check.icon;
          
          return (
            <Card key={check.key} className="relative">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Icon className="h-4 w-4" />
                    <CardTitle className="text-base">{check.title}</CardTitle>
                    {getStatusIcon(result)}
                  </div>
                  <div className="flex items-center gap-2">
                    {getStatusBadge(result)}
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => runIndividualVerification(check)}
                      disabled={state.loading}
                    >
                      Test
                    </Button>
                  </div>
                </div>
                <CardDescription>{check.description}</CardDescription>
              </CardHeader>

              {result && (
                <CardContent className="pt-0">
                  <div className="space-y-2">
                    <p className="text-sm">{result.message}</p>
                    
                    {result.issues && result.issues.length > 0 && (
                      <div className="space-y-1">
                        <p className="text-sm font-medium text-red-600">Issues Found:</p>
                        <ul className="list-disc list-inside text-sm text-red-600 space-y-1">
                          {result.issues.map((issue, index) => (
                            <li key={index}>{issue}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    
                    {result.details && (
                      <details className="text-xs">
                        <summary className="cursor-pointer font-medium">View Details</summary>
                        <pre className="mt-2 p-2 bg-muted rounded text-xs overflow-auto">
                          {JSON.stringify(result.details, null, 2)}
                        </pre>
                      </details>
                    )}
                  </div>
                </CardContent>
              )}
            </Card>
          );
        })}
      </div>

      {/* Instructions */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">How to Use</CardTitle>
        </CardHeader>
        <CardContent className="text-sm space-y-2">
          <p><strong>Run All Checks:</strong> Executes all verification tests at once</p>
          <p><strong>Individual Tests:</strong> Run specific verification checks to isolate issues</p>
          <p><strong>View Details:</strong> Click "View Details" to see detailed verification data</p>
          <p><strong>Issues:</strong> Any problems found will be listed with specific details</p>
        </CardContent>
      </Card>
    </div>
  );
};

export default MigrationVerificationPanel;