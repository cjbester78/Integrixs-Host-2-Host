import React from 'react'
import { CheckCircle, AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

interface TestResultData {
  success: boolean
  error?: string
  testResult?: string
  testType?: string
  host?: string
  port?: number
  username?: string
  homeDirectory?: string
  homeDirectoryAccessible?: boolean
  remoteDirectory?: string
  connectionEstablished?: boolean
  sftpChannelOpened?: boolean
  directoryAccessible?: boolean
  readPermission?: boolean
  writePermission?: boolean
  deletePermission?: boolean
  fileCount?: number
  smtpHost?: string
  smtpPort?: number
  smtpConnectionEstablished?: boolean
}

interface AdapterTestResultModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  testResult: {
    data: TestResultData
  } | null
}

export const AdapterTestResultModal: React.FC<AdapterTestResultModalProps> = ({
  open,
  onOpenChange,
  testResult,
}) => {
  if (!testResult?.data) return null

  const data = testResult.data

  const StatusIcon = ({ success }: { success: boolean }) => (
    success ? (
      <CheckCircle className="h-4 w-4 text-success" />
    ) : (
      <AlertTriangle className="h-4 w-4 text-destructive" />
    )
  )

  const StatusText = ({ success, successText = "Success", failText = "Failed" }: { 
    success: boolean
    successText?: string
    failText?: string 
  }) => (
    <span className={success ? 'text-success' : 'text-destructive'}>
      {success ? successText : failText}
    </span>
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader className="pb-0">
          <DialogTitle className="flex items-center space-x-2 text-lg">
            {data.success ? (
              <>
                <CheckCircle className="h-5 w-5 text-success" />
                <span>Test Successful</span>
              </>
            ) : (
              <>
                <AlertTriangle className="h-5 w-5 text-destructive" />
                <span>Test Failed</span>
              </>
            )}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {/* Error/Success Message */}
          {!data.success && data.error && (
            <div className="text-foreground leading-relaxed">
              {data.error}
            </div>
          )}
          
          {data.success && data.testResult && (
            <div className="text-foreground leading-relaxed">
              {data.testResult}
            </div>
          )}

          {/* Connection Details */}
          <div>
            <h3 className="text-lg font-semibold text-foreground mb-4">Connection Details</h3>
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-x-8 gap-y-3">
                {data.host && (
                  <div>
                    <span className="text-muted-foreground">Host: </span>
                    <span className="text-foreground font-medium">
                      {data.host}:{data.port || 22}
                    </span>
                  </div>
                )}
                {data.username && (
                  <div>
                    <span className="text-muted-foreground">Username: </span>
                    <span className="text-foreground font-medium">{data.username}</span>
                  </div>
                )}
              </div>

              {data.homeDirectory && (
                <div>
                  <span className="text-muted-foreground">Home Directory: </span>
                  <span className="text-foreground font-medium">{data.homeDirectory}</span>
                  {data.homeDirectoryAccessible && (
                    <span className="ml-2 text-success">(Accessible)</span>
                  )}
                </div>
              )}

              {data.remoteDirectory && (
                <div>
                  <span className="text-muted-foreground">Target Directory: </span>
                  <span className="text-foreground font-medium">{data.remoteDirectory}</span>
                </div>
              )}
            </div>
          </div>

          {/* SFTP Connection Status and Permissions */}
          {data.testType === 'SFTP_CONNECTION' && (
            <div className="grid grid-cols-2 gap-8">
              {/* Connection Status */}
              <div>
                <h3 className="text-lg font-semibold text-foreground mb-4">Connection Status</h3>
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Connection:</span>
                    <div className="flex items-center space-x-2">
                      <StatusIcon success={data.connectionEstablished || false} />
                      <StatusText success={data.connectionEstablished || false} />
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">SFTP Channel:</span>
                    <div className="flex items-center space-x-2">
                      <StatusIcon success={data.sftpChannelOpened || false} />
                      <StatusText 
                        success={data.sftpChannelOpened || false} 
                        successText="Opened" 
                        failText="Failed"
                      />
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Directory Access:</span>
                    <div className="flex items-center space-x-2">
                      <StatusIcon success={data.directoryAccessible || false} />
                      <StatusText 
                        success={data.directoryAccessible || false} 
                        successText="Accessible" 
                        failText="Failed"
                      />
                    </div>
                  </div>
                </div>
              </div>

              {/* Permissions */}
              <div>
                <h3 className="text-lg font-semibold text-foreground mb-4">Permissions</h3>
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Read:</span>
                    <div className="flex items-center space-x-2">
                      <StatusIcon success={data.readPermission || false} />
                      <StatusText 
                        success={data.readPermission || false} 
                        successText="Allowed" 
                        failText="Denied"
                      />
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Write:</span>
                    <div className="flex items-center space-x-2">
                      <StatusIcon success={data.writePermission || false} />
                      <StatusText 
                        success={data.writePermission || false} 
                        successText="Allowed" 
                        failText="Denied"
                      />
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Delete:</span>
                    <div className="flex items-center space-x-2">
                      <StatusIcon success={data.deletePermission || false} />
                      <StatusText 
                        success={data.deletePermission || false} 
                        successText="Allowed" 
                        failText="Denied"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* File Count for SFTP */}
          {data.testType === 'SFTP_CONNECTION' && data.fileCount !== undefined && (
            <div className="text-foreground">
              <strong>Files in directory: {data.fileCount} files found</strong>
            </div>
          )}

          {/* Email/SMTP Details */}
          {data.testType === 'EMAIL_CONNECTION' && (
            <div className="grid grid-cols-2 gap-6">
              {data.smtpHost && (
                <div>
                  <span className="text-muted-foreground">SMTP Host:</span>
                  <span className="ml-2 text-foreground font-medium">{data.smtpHost}</span>
                </div>
              )}
              {data.smtpPort && (
                <div>
                  <span className="text-muted-foreground">SMTP Port:</span>
                  <span className="ml-2 text-foreground font-medium">{data.smtpPort}</span>
                </div>
              )}
              {data.smtpConnectionEstablished !== undefined && (
                <div>
                  <span className="text-muted-foreground">Connection:</span>
                  <span className={`ml-2 ${data.smtpConnectionEstablished ? 'text-success' : 'text-destructive'}`}>
                    {data.smtpConnectionEstablished ? 'Established' : 'Failed'}
                  </span>
                </div>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button 
            onClick={() => onOpenChange(false)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-8 py-2 font-medium"
          >
            OK
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}