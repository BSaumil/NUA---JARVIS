use std::{fs, path::Path};

fn push_u16(out: &mut Vec<u8>, value: u16) {
    out.extend_from_slice(&value.to_le_bytes());
}

fn push_u32(out: &mut Vec<u8>, value: u32) {
    out.extend_from_slice(&value.to_le_bytes());
}

/// Generate one classic 32-bit DIB-backed ICO frame. Windows rc.exe accepts this format
/// reliably, unlike PNG-compressed ICO frames on some runner/toolchain combinations.
fn write_brand_icon(path: &Path) -> std::io::Result<()> {
    const W: usize = 64;
    const H: usize = 64;

    let mut dib = Vec::new();
    push_u32(&mut dib, 40); // BITMAPINFOHEADER
    push_u32(&mut dib, W as u32);
    push_u32(&mut dib, (H * 2) as u32); // XOR + AND mask height
    push_u16(&mut dib, 1);
    push_u16(&mut dib, 32);
    push_u32(&mut dib, 0); // BI_RGB
    push_u32(&mut dib, (W * H * 4) as u32);
    push_u32(&mut dib, 0);
    push_u32(&mut dib, 0);
    push_u32(&mut dib, 0);
    push_u32(&mut dib, 0);

    // Bottom-up BGRA pixels: Warm Ivory background with the NUA Burgundy sovereign N.
    for y_bottom in 0..H {
        let y = H - 1 - y_bottom;
        for x in 0..W {
            let left = (12..=20).contains(&x) && (12..=52).contains(&y);
            let right = (44..=52).contains(&x) && (12..=52).contains(&y);
            let diagonal_center = 16.0 + ((y.saturating_sub(12)) as f32 * 0.8);
            let diagonal = (12..=52).contains(&y) && ((x as f32) - diagonal_center).abs() <= 5.0;
            let node = {
                let dx = x as i32 - 32;
                let dy = y as i32 - 32;
                dx * dx + dy * dy <= 16
            };
            let (r, g, b) = if node {
                (139u8, 92u8, 246u8) // Intelligence Violet node
            } else if left || right || diagonal {
                (107u8, 23u8, 56u8) // NUA Burgundy
            } else {
                (246u8, 240u8, 229u8) // Warm Ivory
            };
            dib.extend_from_slice(&[b, g, r, 255]);
        }
    }

    // AND mask: all zero = fully opaque.
    let mask_row = ((W + 31) / 32) * 4;
    dib.extend(std::iter::repeat(0u8).take(mask_row * H));

    let mut ico = Vec::new();
    push_u16(&mut ico, 0);
    push_u16(&mut ico, 1); // icon
    push_u16(&mut ico, 1); // one frame
    ico.push(W as u8);
    ico.push(H as u8);
    ico.push(0);
    ico.push(0);
    push_u16(&mut ico, 1);
    push_u16(&mut ico, 32);
    push_u32(&mut ico, dib.len() as u32);
    push_u32(&mut ico, 22); // ICONDIR(6) + ICONDIRENTRY(16)
    ico.extend_from_slice(&dib);

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, ico)
}

fn main() {
    let icon = Path::new("icons/icon.ico");
    write_brand_icon(icon).expect("failed to generate NUA Windows icon");
    tauri_build::build()
}
