use tauri::{Manager, WindowEvent};
use tauri::tray::{TrayIconBuilder, TrayIconEvent};

const SERVICE: &str = "com.goldpricemonitor.desktop";
const ACCOUNT: &str = "access-token";

#[tauri::command]
fn read_token() -> Result<Option<String>, String> {
    let entry = keyring::Entry::new(SERVICE, ACCOUNT).map_err(|e| e.to_string())?;
    match entry.get_password() {
        Ok(token) => Ok(Some(token)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(e) => Err(e.to_string()),
    }
}
#[tauri::command]
fn save_token(token: String) -> Result<(), String> {
    keyring::Entry::new(SERVICE, ACCOUNT).map_err(|e| e.to_string())?
        .set_password(&token).map_err(|e| e.to_string())
}
#[tauri::command]
fn clear_token() -> Result<(), String> {
    let entry = keyring::Entry::new(SERVICE, ACCOUNT).map_err(|e| e.to_string())?;
    match entry.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}
#[tauri::command]
fn tray_status(app: tauri::AppHandle, status: String) -> Result<(), String> {
    let tray = app.tray_by_id("main").ok_or("托盘未就绪")?;
    tray.set_tooltip(Some(format!("金价监控 · {}", status))).map_err(|e| e.to_string())?;
    #[cfg(target_os = "macos")]
    tray.set_title(Some(status)).map_err(|e| e.to_string())?;
    Ok(())
}
#[tauri::command]
async fn fetch_gold_price(url: String) -> Result<String, String> {
    let parsed = reqwest::Url::parse(&url).map_err(|_| "金价数据接口地址无效".to_string())?;
    if !matches!(parsed.scheme(), "http" | "https") {
        return Err("金价数据接口必须使用 HTTP 或 HTTPS".to_string());
    }
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(8))
        .build()
        .map_err(|e| e.to_string())?;
    let response = client.get(parsed).send().await.map_err(|e| format!("获取金价失败：{e}"))?;
    let status = response.status();
    if !status.is_success() {
        return Err(format!("金价数据接口返回 {status}"));
    }
    response.text().await.map_err(|e| format!("读取金价失败：{e}"))
}
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_notification::init())
        .invoke_handler(tauri::generate_handler![read_token, save_token, clear_token, tray_status, fetch_gold_price])
        .setup(|app| {
            TrayIconBuilder::with_id("main")
                .icon(app.default_window_icon().expect("icon").clone())
                .tooltip("金价监控 · 未登录")
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click { position, .. } = event {
                        if let Some(window) = tray.app_handle().get_webview_window("main") {
                            if !window.is_visible().unwrap_or(false) {
                                let _ = window.set_position(tauri::PhysicalPosition::new(position.x as i32, position.y as i32));
                                let _ = window.show();
                            }
                            let _ = window.set_focus();
                        }
                    }
                })
                .build(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            match event {
                WindowEvent::CloseRequested { api, .. } => { api.prevent_close(); let _ = window.hide(); }
                _ => {}
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running desktop application");
}
