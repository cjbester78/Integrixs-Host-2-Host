import React from 'react'
import { CheckCircle, AlertTriangle, XCircle, Info } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

export type NotificationType = 'success' | 'error' | 'warning' | 'info'

interface NotificationModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  type: NotificationType
  title?: string
  message: string
}

const iconMap = {
  success: { icon: CheckCircle, className: 'text-success' },
  error: { icon: XCircle, className: 'text-destructive' },
  warning: { icon: AlertTriangle, className: 'text-warning' },
  info: { icon: Info, className: 'text-info' },
}

const defaultTitles = {
  success: 'Success',
  error: 'Error',
  warning: 'Warning',
  info: 'Information',
}

export const NotificationModal: React.FC<NotificationModalProps> = ({
  open,
  onOpenChange,
  type,
  title,
  message,
}) => {
  const { icon: Icon, className } = iconMap[type]
  const displayTitle = title || defaultTitles[type]

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader className="pb-0">
          <DialogTitle className="flex items-center space-x-2 text-lg">
            <Icon className={`h-5 w-5 ${className}`} />
            <span>{displayTitle}</span>
          </DialogTitle>
        </DialogHeader>

        <div className="py-4">
          <p className="text-foreground leading-relaxed whitespace-pre-wrap">{message}</p>
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
