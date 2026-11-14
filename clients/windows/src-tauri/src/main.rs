use serde_json::Value;
use url::Url;

const ALLOWED_PATHS: &[&str] = &["health", "presence"];

fn validate_companion_base(base: &str) -> Result<Url, String> {
    let parsed = Url::parse(base).map_err(|_| "invalid companion URL".to_string())?;
    let host = parsed.host_str().unwrap_or_default();
    if parsed.scheme() != "http" || !matches!(host, "127.0.0.1" | "localhost" | "::1") {
        return Err("Windows shell connects only to the localhost companion".to_string());
    }
    if parsed.port_or_known_default() != Some(8765) {
        return Err("unexpected companion port".to_string());
    }
    Ok(parsed)
}

#[tauri::command]
async fn companion_get(base: String, path: String) -> Result<Value, String> {
    if !ALLOWED_PATHS.contains(&path.as_str()) {
        return Err("operation not exposed to the desktop shell".to_string());
    }
    let mut url = validate_companion_base(&base)?;
    url.set_path(&path);
    let response = reqwest::Client::new()
        .get(url)
        .timeout(std::time::Duration::from_secs(5))
        .send()
        .await
        .map_err(|_| "companion unavailable".to_string())?;
    if !response.status().is_success() {
        return Err(format!("companion returned {}", response.status()));
    }
    response.json::<Value>().await.map_err(|_| "invalid companion response".to_string())
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![companion_get])
        .run(tauri::generate_context!())
        .expect("error while running NUA Sovereign");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn refuses_remote_http_endpoint() {
        assert!(validate_companion_base("http://192.168.1.22:8765").is_err());
    }

    #[test]
    fn accepts_localhost_companion() {
        assert!(validate_companion_base("http://127.0.0.1:8765").is_ok());
    }

    #[test]
    fn refuses_wrong_localhost_port() {
        assert!(validate_companion_base("http://127.0.0.1:9999").is_err());
    }
}
