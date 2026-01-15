import React from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

interface ConfirmationModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title?: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: 'default' | 'destructive'
  onConfirm: () => void
}

export const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  open,
  onOpenChange,
  title = 'Confirm Action',
  message,
  confirmLabel = 'OK',
  cancelLabel = 'Cancel',
  variant = 'default',
  onConfirm,
}) => {
  const handleConfirm = () => {
    onConfirm()
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="text-xl font-semibold">
            {title}
          </DialogTitle>
        </DialogHeader>

        <div className="py-2">
          <p className="text-sm text-muted-foreground leading-relaxed">{message}</p>
        </div>

        <DialogFooter className="flex gap-2 sm:justify-end">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            className="px-6"
          >
            {cancelLabel}
          </Button>
          <Button
            variant={variant}
            onClick={handleConfirm}
            className="px-6"
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
