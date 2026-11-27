import { ElMessage } from 'element-plus';

export function showAlert(message: string): void {
  ElMessage({ message, type: 'warning', duration: 10_000, showClose: true });
}
