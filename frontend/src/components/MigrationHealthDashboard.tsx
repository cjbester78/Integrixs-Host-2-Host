import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { 
  Activity, 
  CheckCircle, 
  AlertTriangle, 
  XCircle, 
  Clock,
  TrendingUp,
  TrendingDown,
  Minus,
  Shield,
  Database,
  Zap,
  RefreshCw
} from 'lucide-react';
import { api } from '@/lib/api';

/**
 * Migration Health Dashboard Component
 * Real-time monitoring of migration system health following OOP principles
 * - Observer Pattern: Subscribes to health status updates
 * - State Pattern: Manages different health states and their visual representations
 * - Strategy Pattern: Different rendering strategies for different health statuses
 */



interface HealthReport {
  overallStatus: string;
  overallStatusDescription: string;
  timestamp: string;
  metricsCount: number;
  issuesCount: number;
  severity: number;
}

interface HealthDashboardData {
  overallHealth: HealthReport;
  metricsSummary: {
    total: number;
    excellent: number;
    good: number;
    warning: number;
    critical: number;
    failed: number;
  };
  issuesSummary: {
    total: number;
    recentIssues: string[];
  };
  trends: Record<string, any>;
  quickActions: Record<string, string>;
}

/**
 * Health Status Icon Strategy - Factory Pattern Implementation
 */
class HealthStatusIconFactory {
  static getIcon(status: string, size: string = "h-4 w-4") {
    switch (status.toLowerCase()) {
      case 'excellent':
        return <CheckCircle className={`${size} text-green-600`} />;
      case 'good':
        return <CheckCircle className={`${size} text-blue-600`} />;
      case 'warning':
        return <AlertTriangle className={`${size} text-yellow-600`} />;
      case 'critical':
        return <XCircle className={`${size} text-red-600`} />;
      case 'failed':
        return <XCircle className={`${size} text-red-800`} />;
      default:
        return <Clock className={`${size} text-gray-500`} />;
    }
  }

  static getBadgeVariant(status: string) {
    switch (status.toLowerCase()) {
      case 'excellent':
        return 'default';
      case 'good':
        return 'secondary';
      case 'warning':
        return 'outline';
      case 'critical':
        return 'destructive';
      case 'failed':
        return 'destructive';
      default:
        return 'outline';
    }
  }

  static getCardBorderColor(status: string) {
    switch (status.toLowerCase()) {
      case 'excellent':
        return 'border-green-200';
      case 'good':
        return 'border-blue-200';
      case 'warning':
        return 'border-yellow-200';
      case 'critical':
        return 'border-red-200';
      case 'failed':
        return 'border-red-300';
      default:
        return 'border-gray-200';
    }
  }
}

/**
 * Trend Icon Strategy
 */
class TrendIconFactory {
  static getIcon(trend: string, size: string = "h-4 w-4") {
    switch (trend.toLowerCase()) {
      case 'improving':
        return <TrendingUp className={`${size} text-green-600`} />;
      case 'degrading':
        return <TrendingDown className={`${size} text-red-600`} />;
      case 'stable':
        return <Minus className={`${size} text-gray-600`} />;
      default:
        return <Minus className={`${size} text-gray-500`} />;
    }
  }
}

const MigrationHealthDashboard: React.FC = () => {
  const [refreshInterval, setRefreshInterval] = useState<number>(30000); // 30 seconds
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());

  // Real-time health dashboard query
  const { 
    data: dashboardData, 
    isLoading, 
    error, 
    refetch 
  } = useQuery<HealthDashboardData>({
    queryKey: ['migration-health-dashboard'],
    queryFn: async () => {
      const response = await api.get('/api/migration/health/dashboard');
      return response.data.dashboard;
    },
    refetchInterval: refreshInterval,
    refetchOnWindowFocus: true,
  });

  // Update last refresh time when data changes
  React.useEffect(() => {
    if (dashboardData) {
      setLastRefresh(new Date());
    }
  }, [dashboardData]);

  // Manual refresh handler
  const handleManualRefresh = async () => {
    await refetch();
    setLastRefresh(new Date());
  };

  // Get overall health score as percentage
  const getHealthScore = () => {
    if (!dashboardData?.metricsSummary) return 0;
    
    const { total, excellent, good, warning, critical, failed } = dashboardData.metricsSummary;
    if (total === 0) return 0;
    
    const score = ((excellent * 100) + (good * 80) + (warning * 60) + (critical * 30) + (failed * 0)) / total;
    return Math.round(score);
  };

  // Get health score color
  const getHealthScoreColor = (score: number) => {
    if (score >= 90) return 'text-green-600';
    if (score >= 70) return 'text-blue-600';
    if (score >= 50) return 'text-yellow-600';
    if (score >= 30) return 'text-orange-600';
    return 'text-red-600';
  };

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <RefreshCw className="h-5 w-5 animate-spin" />
              Loading Migration Health Dashboard...
            </CardTitle>
          </CardHeader>
        </Card>
      </div>
    );
  }

  if (error) {
    return (
      <Alert className="border-red-200 bg-red-50">
        <XCircle className="h-4 w-4" />
        <AlertDescription>
          Failed to load health dashboard: {error instanceof Error ? error.message : 'Unknown error'}
          <Button variant="outline" size="sm" onClick={handleManualRefresh} className="ml-2">
            Retry
          </Button>
        </AlertDescription>
      </Alert>
    );
  }

  if (!dashboardData) {
    return (
      <Alert>
        <AlertTriangle className="h-4 w-4" />
        <AlertDescription>No health data available</AlertDescription>
      </Alert>
    );
  }

  const healthScore = getHealthScore();
  const overallStatus = dashboardData.overallHealth.overallStatus;

  return (
    <div className="space-y-6">
      {/* Header with Overall Status */}
      <Card className={`${HealthStatusIconFactory.getCardBorderColor(overallStatus)} border-2`}>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-muted">
                <Shield className="h-6 w-6" />
              </div>
              <div>
                <CardTitle className="flex items-center gap-2">
                  Migration Health Dashboard
                  {HealthStatusIconFactory.getIcon(overallStatus, "h-5 w-5")}
                </CardTitle>
                <CardDescription>
                  Real-time monitoring of migration system integrity
                </CardDescription>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <Badge variant={HealthStatusIconFactory.getBadgeVariant(overallStatus)} className="px-3 py-1">
                {overallStatus}
              </Badge>
              <Button variant="outline" size="sm" onClick={handleManualRefresh}>
                <RefreshCw className="h-4 w-4 mr-1" />
                Refresh
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Overall Health Score */}
            <div className="space-y-2">
              <div className="text-sm font-medium">Health Score</div>
              <div className="flex items-center gap-2">
                <Progress value={healthScore} className="flex-1" />
                <span className={`text-lg font-bold ${getHealthScoreColor(healthScore)}`}>
                  {healthScore}%
                </span>
              </div>
              <div className="text-xs text-muted-foreground">
                {dashboardData.overallHealth.overallStatusDescription}
              </div>
            </div>

            {/* Last Updated */}
            <div className="space-y-2">
              <div className="text-sm font-medium">Last Updated</div>
              <div className="text-sm">
                {lastRefresh.toLocaleTimeString()}
              </div>
              <div className="text-xs text-muted-foreground">
                Auto-refresh: {refreshInterval / 1000}s
              </div>
            </div>

            {/* Quick Stats */}
            <div className="space-y-2">
              <div className="text-sm font-medium">System Status</div>
              <div className="flex items-center gap-2 text-sm">
                <Database className="h-4 w-4" />
                {dashboardData.metricsSummary.total} metrics monitored
              </div>
              <div className="flex items-center gap-2 text-sm">
                <Activity className="h-4 w-4" />
                {dashboardData.issuesSummary.total} issues detected
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Metrics Summary Grid */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        {[
          { key: 'excellent', label: 'Excellent', color: 'text-green-600', bg: 'bg-green-50' },
          { key: 'good', label: 'Good', color: 'text-blue-600', bg: 'bg-blue-50' },
          { key: 'warning', label: 'Warning', color: 'text-yellow-600', bg: 'bg-yellow-50' },
          { key: 'critical', label: 'Critical', color: 'text-red-600', bg: 'bg-red-50' },
          { key: 'failed', label: 'Failed', color: 'text-red-800', bg: 'bg-red-100' }
        ].map(({ key, label, color, bg }) => (
          <Card key={key}>
            <CardContent className="p-4">
              <div className={`p-2 rounded-lg ${bg} mb-2 w-fit`}>
                {HealthStatusIconFactory.getIcon(key, "h-4 w-4")}
              </div>
              <div className={`text-2xl font-bold ${color}`}>
                {dashboardData.metricsSummary[key as keyof typeof dashboardData.metricsSummary]}
              </div>
              <div className="text-sm text-muted-foreground">{label}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Issues Alert */}
      {dashboardData.issuesSummary.total > 0 && (
        <Alert className="border-yellow-200 bg-yellow-50">
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription>
            <div className="space-y-2">
              <p className="font-medium">
                {dashboardData.issuesSummary.total} issues require attention:
              </p>
              <ul className="list-disc list-inside space-y-1 text-sm">
                {dashboardData.issuesSummary.recentIssues.slice(0, 5).map((issue, index) => (
                  <li key={index}>{issue}</li>
                ))}
                {dashboardData.issuesSummary.recentIssues.length > 5 && (
                  <li className="text-muted-foreground">
                    ...and {dashboardData.issuesSummary.recentIssues.length - 5} more issues
                  </li>
                )}
              </ul>
            </div>
          </AlertDescription>
        </Alert>
      )}

      {/* Trends Analysis */}
      {dashboardData.trends.status === 'success' && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="h-5 w-5" />
              Health Trends
            </CardTitle>
            <CardDescription>
              Analysis based on last {dashboardData.trends.analysisWindow} measurements
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {Object.entries(dashboardData.trends.metricTrends || {}).map(([metric, trend]) => (
                <div key={metric} className="flex items-center justify-between p-3 bg-muted rounded-lg">
                  <div className="space-y-1">
                    <div className="text-sm font-medium capitalize">
                      {metric.replace(/_/g, ' ')}
                    </div>
                    <div className="text-xs text-muted-foreground capitalize">
                      {trend as string}
                    </div>
                  </div>
                  {TrendIconFactory.getIcon(trend as string)}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Quick Actions */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Zap className="h-5 w-5" />
            Quick Actions
          </CardTitle>
          <CardDescription>
            Common health monitoring operations
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={handleManualRefresh}>
              <RefreshCw className="h-4 w-4 mr-1" />
              Refresh Now
            </Button>
            <Button variant="outline" size="sm">
              <Database className="h-4 w-4 mr-1" />
              View Detailed Metrics
            </Button>
            <Button variant="outline" size="sm">
              <Activity className="h-4 w-4 mr-1" />
              Run Health Check
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Settings */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Dashboard Settings</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">Auto-refresh interval</label>
              <select 
                value={refreshInterval}
                onChange={(e) => setRefreshInterval(Number(e.target.value))}
                className="text-sm border rounded px-2 py-1"
              >
                <option value={15000}>15 seconds</option>
                <option value={30000}>30 seconds</option>
                <option value={60000}>1 minute</option>
                <option value={300000}>5 minutes</option>
              </select>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default MigrationHealthDashboard;