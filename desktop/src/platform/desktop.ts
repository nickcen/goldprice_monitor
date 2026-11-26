import { invoke } from '@tauri-apps/api/core';
import { isTauri } from '@tauri-apps/api/core';
import { sendNotification, isPermissionGranted, requestPermission } from '@tauri-apps/plugin-notification';

export async function readToken(): Promise<string | null> { return isTauri() ? invoke<string | null>('read_token') : null; }
export async function saveToken(token: string): Promise<void> { if (isTauri()) await invoke('save_token', { token }); }
export async function clearToken(): Promise<void> { if (isTauri()) await invoke('clear_token'); }
export async function trayStatus(status: string): Promise<void> { if (isTauri()) await invoke('tray_status', { status }); }
export async function notify(title: string, body: string): Promise<void> {
  if (!isTauri()) return;
  let allowed = await isPermissionGranted();
  if (!allowed) allowed = (await requestPermission()) === 'granted';
  if (!allowed) throw new Error('系统通知权限未授予');
  sendNotification({ title, body });
}
