import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ClipboardModule } from '@angular/cdk/clipboard';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  referenceNumber?: string | null;
  confirmationValue?: string | null;
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, ClipboardModule, FormsModule],
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmDialogComponent, boolean>);
  readonly copied = signal(false);
  confirmationText = '';

  canConfirm(): boolean {
    const expected = (this.data.confirmationValue ?? this.data.referenceNumber ?? '').trim();
    if (!expected) {
      return true;
    }
    return this.confirmationText.trim() === expected;
  }

  markCopied(): void {
    this.copied.set(true);
  }

  close(result: boolean): void {
    this.dialogRef.close(result);
  }
}
