//! Horizon: the Vulkan pipeline cache kept on disk between runs, and a hook
//! that runs after each pipeline is compiled.
//!
//! Horizon creates its pipelines with no `VkPipelineCache`, so every start
//! compiles all of them again. On a slow driver (Adreno 540 on the Quest 1)
//! that is 7-9 s of the main thread before the first frame, and Android
//! shows "not responding" for an app that does not read its input for 5 s.
//!
//! The application names one file with [`set_file`]. Every device then
//! creates one `VkPipelineCache` from that file and compiles each pipeline
//! that names no cache of its own through it; [`super::Device::save_pipeline_disk_cache`]
//! writes the cache back, and so does dropping the device.
//!
//! The driver checks its own header in the data (vendor, device, driver,
//! cache UUID) and ignores data from another GPU or driver. On top of that the
//! file carries its own header with the same identity, the data's length and
//! a checksum: a file cut short or damaged (a run killed mid-write) is never
//! handed to the driver, since an old driver may not survive bad data. The
//! file is written to a temporary name and renamed over the old one.
//!
//! File: `MAGIC`, `vendor: u32 LE`, `device: u32 LE`, `driver: u32 LE`,
//! `uuid: [u8; 16]`, `len: u64 LE`, `check: u64 LE` (FNV-1a 64 of the data),
//! then the data from `vkGetPipelineCacheData`.

use alloc::vec::Vec;
use core::sync::atomic::{AtomicBool, Ordering};
use std::{fs, io::Write as _, path::PathBuf, sync::OnceLock};

use ash::vk;

const MAGIC: &[u8; 8] = b"HZVKPC1\n";
/// Vendor, device, driver and the cache UUID.
const IDENTITY: usize = 4 + 4 + 4 + vk::UUID_SIZE;
const HEADER: usize = MAGIC.len() + IDENTITY + 8 + 8;
/// A file past this is not read: Horizon's pipelines take a few MB.
pub const MAX_BYTES: u64 = 64 << 20;

static FILE: OnceLock<PathBuf> = OnceLock::new();
static HOOK: OnceLock<fn()> = OnceLock::new();

/// Keep the pipeline cache in `path` for the rest of the run. Only the first
/// call counts; without one nothing is kept on disk.
pub fn set_file(path: PathBuf) {
    let _ = FILE.set(path);
}

/// Call `hook` on the compiling thread after each pipeline is compiled. Only
/// the first call counts. Android uses it to keep reading the app's input
/// while the main thread compiles for seconds.
pub fn set_pipeline_hook(hook: fn()) {
    let _ = HOOK.set(hook);
}

/// After one pipeline is compiled.
pub(super) fn pipeline_compiled() {
    if let Some(hook) = HOOK.get() {
        hook();
    }
}

/// One device's pipeline cache and the file it came from.
pub(super) struct DiskCache {
    raw: vk::PipelineCache,
    path: PathBuf,
    identity: [u8; IDENTITY],
    /// A pipeline was compiled since the last save.
    dirty: AtomicBool,
}

fn identity(properties: &vk::PhysicalDeviceProperties) -> [u8; IDENTITY] {
    let mut identity = [0; IDENTITY];
    identity[0..4].copy_from_slice(&properties.vendor_id.to_le_bytes());
    identity[4..8].copy_from_slice(&properties.device_id.to_le_bytes());
    identity[8..12].copy_from_slice(&properties.driver_version.to_le_bytes());
    identity[12..].copy_from_slice(&properties.pipeline_cache_uuid);
    identity
}

/// The cache data in `bytes` when its header names this GPU and driver and
/// its checksum matches.
fn parse<'a>(bytes: &'a [u8], identity: &[u8; IDENTITY]) -> Option<&'a [u8]> {
    let header = bytes.get(..HEADER)?;
    if &header[..MAGIC.len()] != MAGIC || &header[MAGIC.len()..MAGIC.len() + IDENTITY] != identity
    {
        return None;
    }
    let at = MAGIC.len() + IDENTITY;
    let len = u64::from_le_bytes(header[at..at + 8].try_into().unwrap());
    let check = u64::from_le_bytes(header[at + 8..at + 16].try_into().unwrap());
    let data = bytes.get(HEADER..)?;
    (data.len() as u64 == len && fnv64(data) == check).then_some(data)
}

fn encode(identity: &[u8; IDENTITY], data: &[u8]) -> Vec<u8> {
    let mut file = Vec::with_capacity(HEADER + data.len());
    file.extend_from_slice(MAGIC);
    file.extend_from_slice(identity);
    file.extend_from_slice(&(data.len() as u64).to_le_bytes());
    file.extend_from_slice(&fnv64(data).to_le_bytes());
    file.extend_from_slice(data);
    file
}

impl DiskCache {
    /// The device's cache, from the file when it holds this GPU's: `None`
    /// without [`set_file`] or when the driver can't make a cache at all.
    pub(super) fn open(
        device: &ash::Device,
        properties: &vk::PhysicalDeviceProperties,
    ) -> Option<Self> {
        let path = FILE.get()?.clone();
        let identity = identity(properties);
        let bytes = match fs::metadata(&path) {
            Ok(meta) if meta.len() <= MAX_BYTES => fs::read(&path).unwrap_or_default(),
            _ => Vec::new(),
        };
        let data = parse(&bytes, &identity);
        if !bytes.is_empty() && data.is_none() {
            log::info!(
                "Vulkan pipeline cache {}: not this GPU's or damaged, starting a new one",
                path.display()
            );
        }
        let create = |data: Option<&[u8]>| {
            let mut info = vk::PipelineCacheCreateInfo::default();
            if let Some(data) = data {
                info = info.initial_data(data);
            }
            unsafe { device.create_pipeline_cache(&info, None) }
        };
        let raw = match create(data) {
            Ok(raw) => raw,
            // The driver refused the data: an empty cache instead.
            Err(_) if data.is_some() => create(None).ok()?,
            Err(_) => return None,
        };
        log::info!(
            "Vulkan pipeline cache {}: {} bytes from the last run",
            path.display(),
            data.map_or(0, <[u8]>::len)
        );
        Some(Self {
            raw,
            path,
            identity,
            dirty: AtomicBool::new(false),
        })
    }

    pub(super) fn raw(&self) -> vk::PipelineCache {
        self.raw
    }

    /// A pipeline was compiled through this cache.
    pub(super) fn note_compiled(&self) {
        self.dirty.store(true, Ordering::Relaxed);
    }

    /// Write the cache to its file if a pipeline was compiled since the last
    /// save. Returns the bytes written.
    pub(super) fn save(&self, device: &ash::Device) -> Option<usize> {
        if !self.dirty.swap(false, Ordering::Relaxed) {
            return None;
        }
        let data = match unsafe { device.get_pipeline_cache_data(self.raw) } {
            Ok(data) => data,
            Err(error) => {
                log::warn!("Vulkan pipeline cache: can't read it back: {error}");
                return None;
            }
        };
        let file = encode(&self.identity, &data);
        let written = (|| {
            if let Some(dir) = self.path.parent() {
                fs::create_dir_all(dir)?;
            }
            let temp = self.path.with_extension("tmp");
            let mut out = fs::File::create(&temp)?;
            out.write_all(&file)?;
            out.sync_all()?;
            drop(out);
            fs::rename(&temp, &self.path)
        })();
        match written {
            Ok(()) => Some(file.len()),
            Err(error) => {
                log::warn!("Vulkan pipeline cache {}: {error}", self.path.display());
                None
            }
        }
    }

    /// Save, then destroy the cache (before its device is destroyed).
    pub(super) fn close(&self, device: &ash::Device) {
        self.save(device);
        unsafe { device.destroy_pipeline_cache(self.raw, None) };
    }
}

/// FNV-1a 64.
fn fnv64(bytes: &[u8]) -> u64 {
    let mut hash = 0xcbf29ce484222325u64;
    for &b in bytes {
        hash ^= u64::from(b);
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

#[cfg(test)]
mod tests {
    use super::*;

    fn props() -> [u8; IDENTITY] {
        let mut identity = [7; IDENTITY];
        identity[0] = 1;
        identity
    }

    /// A written file reads back to the same data; another GPU's, one cut
    /// short and one with a changed byte are refused.
    #[test]
    fn only_a_whole_file_of_this_gpu_is_read() {
        let identity = props();
        let data = b"driver cache data".to_vec();
        let file = encode(&identity, &data);
        assert_eq!(parse(&file, &identity), Some(data.as_slice()));

        let mut other = identity;
        other[4] ^= 1;
        assert_eq!(parse(&file, &other), None);

        assert_eq!(parse(&file[..file.len() - 1], &identity), None);
        assert_eq!(parse(&file[..HEADER - 1], &identity), None);

        let mut damaged = file.clone();
        *damaged.last_mut().unwrap() ^= 0xff;
        assert_eq!(parse(&damaged, &identity), None);

        assert_eq!(parse(b"", &identity), None);
    }
}
