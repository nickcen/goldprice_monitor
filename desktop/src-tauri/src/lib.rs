use tauri::{Manager, WindowEvent};
use tauri::tray::{TrayIconBuilder, TrayIconEvent};
use rusqlite::{params, Connection};
use serde::Serialize;

const SERVICE: &str = "com.goldpricemonitor.desktop";
const ACCOUNT: &str = "access-token";

#[derive(Serialize)]
struct PricePoint { minute: String, price: String }

fn history_db(app: &tauri::AppHandle) -> Result<Connection, String> {
    let dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let db = Connection::open(dir.join("prices.sqlite3")).map_err(|e| e.to_string())?;
    db.execute_batch("CREATE TABLE IF NOT EXISTS price_history (day TEXT NOT NULL, minute TEXT NOT NULL, price TEXT NOT NULL, currency TEXT NOT NULL, unit TEXT NOT NULL, precision INTEGER NOT NULL, PRIMARY KEY(day, minute, currency, unit, precision)); CREATE INDEX IF NOT EXISTS price_history_day ON price_history(day);").map_err(|e| e.to_string())?;
    Ok(db)
}

#[tauri::command]
fn save_price_point(app: tauri::AppHandle, day: String, minute: String, price: String, currency: String, unit: String, precision: i64) -> Result<(), String> {
    if day.len() != 10 || minute.len() != 5 || price.parse::<f64>().map_or(true, |v| !v.is_finite() || v < 0.0) || currency.is_empty() || unit.is_empty() || precision < 0 { return Err("无效金价记录".into()); }
    history_db(&app)?.execute("INSERT INTO price_history(day, minute, price, currency, unit, precision) VALUES (?1, ?2, ?3, ?4, ?5, ?6) ON CONFLICT(day, minute, currency, unit, precision) DO UPDATE SET price=excluded.price", params![day, minute, price, currency, unit, precision]).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
fn load_price_points(app: tauri::AppHandle, day: String, currency: String, unit: String, precision: i64) -> Result<Vec<PricePoint>, String> {
    let db = history_db(&app)?;
    let mut stmt = db.prepare("SELECT minute, price FROM price_history WHERE day=?1 AND currency=?2 AND unit=?3 AND precision=?4 ORDER BY minute").map_err(|e| e.to_string())?;
    let rows = stmt.query_map(params![day, currency, unit, precision], |row| Ok(PricePoint { minute: row.get(0)?, price: row.get(1)? })).map_err(|e| e.to_string())?;
    rows.collect::<Result<Vec<_>, _>>().map_err(|e| e.to_string())
}

#[tauri::command]
fn prune_price_points(app: tauri::AppHandle, day: String) -> Result<usize, String> {
    history_db(&app)?.execute("DELETE FROM price_history WHERE day <> ?1", params![day]).map_err(|e| e.to_string())
}

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
fn tray_status(app: tauri::AppHandle, status: String, trend: Option<String>) -> Result<(), String> {
    let tray = app.tray_by_id("main").ok_or("托盘未就绪")?;
    tray.set_tooltip(Some(format!("金价监控 · {}", status))).map_err(|e| e.to_string())?;
    #[cfg(target_os = "macos")]
    {
        tray.set_title(Some(status.clone())).map_err(|e| e.to_string())?;
        if matches!(trend.as_deref(), Some("up" | "down")) {
            tray.with_inner_tray_icon(move |inner| {
                use objc2::{runtime::AnyObject, AnyThread, MainThreadMarker};
                use objc2_app_kit::{NSColor, NSForegroundColorAttributeName};
                use objc2_foundation::{NSAttributedString, NSDictionary, NSString};
                if let Some(item) = inner.ns_status_item() {
                    let color = if trend.as_deref() == Some("up") { NSColor::systemRedColor() } else { NSColor::systemGreenColor() };
                    let attrs: objc2::rc::Retained<NSDictionary<objc2_foundation::NSAttributedStringKey, AnyObject>> =
                        NSDictionary::from_slices(&[unsafe { NSForegroundColorAttributeName }], &[color.as_ref()]);
                    let title = unsafe { NSAttributedString::initWithString_attributes(NSAttributedString::alloc(), &NSString::from_str(&status), Some(&attrs)) };
                    if let Some(button) = item.button(MainThreadMarker::new().expect("tray callback runs on the main thread")) {
                        button.setAttributedTitle(&title);
                    }
                }
            }).map_err(|e| e.to_string())?;
        }
    }
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
        .invoke_handler(tauri::generate_handler![read_token, save_token, clear_token, tray_status, fetch_gold_price, save_price_point, load_price_points, prune_price_points])
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
