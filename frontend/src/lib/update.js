// Update check — compares the installed version (__APP_VERSION__) against the newest release
// tag on GitHub and optionally downloads + installs the APK.
//
// The GitHub Releases API is public, needs no token, and answers with CORS headers, so the
// check runs from the app's WebView. The APK *file* does not: github.com redirects to
// release-assets.githubusercontent.com, which sends no Access-Control-Allow-Origin, so a
// fetch() of the binary is blocked in a WebView. downloadAndInstall() hands the URL to the
// system browser when that happens — a browser is not bound by CORS, downloads the file and
// lets Android install it.

import { MOBILE } from './mobile.js'

const REPO = 'Rob-The-Coder/OlyGym'
const RELEASES_API = `https://api.github.com/repos/${REPO}/releases?per_page=1`
export const RELEASES_PAGE = `https://github.com/${REPO}/releases/latest`

/**
 * Compares two semver strings (e.g. "1.2.11" vs "1.3.0").
 * Returns  1 if a > b, -1 if a < b, 0 if equal.
 */
function compareSemver(a, b) {
  const pa = a.replace(/^v/, '').split('.').map(Number)
  const pb = b.replace(/^v/, '').split('.').map(Number)
  for (let i = 0; i < 3; i++) {
    const diff = (pa[i] || 0) - (pb[i] || 0)
    if (diff > 0) return 1
    if (diff < 0) return -1
  }
  return 0
}

function noUpdate() {
  return { hasUpdate: false, latestVersion: __APP_VERSION__, apkUrl: null, hashUrl: null, apkSha256: null }
}

/**
 * Checks the GitHub releases API for a newer version.
 * Returns { hasUpdate, latestVersion, apkUrl, hashUrl, apkSha256 } or throws on network failure.
 *   - hasUpdate: true if the latest release tag is newer than the running build
 *   - latestVersion: the semver string of the latest release (without "v" prefix)
 *   - apkUrl: direct download URL of the first .apk asset, or null
 *   - hashUrl: download URL of a .sha256 asset published beside it, or null
 *   - apkSha256: the checksum GitHub computed for the APK upload, or null
 */
// One request per app session: Settings is opened often, github.com does not need to hear
// about it every time. The promise is cached, a failure is not.
let cached = null
export function resetUpdateCheck() { cached = null }
export async function checkForUpdate() {
  if (!cached) cached = fetchLatest().catch(e => { cached = null; throw e })
  return cached
}
async function fetchLatest() {
  const res = await fetch(RELEASES_API)
  // 404 is a repo that is gone or not public; an empty array is one with no releases yet.
  // Neither is an error worth showing anyone, and both mean "nothing to offer".
  if (res.status === 404) return noUpdate()
  if (!res.ok) throw new Error(`GitHub API ${res.status}`)
  const releases = await res.json()
  if (!releases.length) return noUpdate()

  const latest = releases[0]
  const latestVersion = latest.tag_name.replace(/^v/, '')
  const assets = latest.assets || []

  // Asset names are file names. `.apk.sha256` must not be mistaken for the APK itself.
  const apk = assets.find(a => /\.apk$/i.test(a.name || ''))
  const hash = assets.find(a => /\.apk\.sha256$/i.test(a.name || '') || /sha256/i.test(a.name || ''))

  return {
    hasUpdate: compareSemver(latestVersion, __APP_VERSION__) > 0,
    latestVersion,
    apkUrl: apk?.browser_download_url || null,
    hashUrl: hash?.browser_download_url || null,
    // GitHub hashes every uploaded asset, so the checksum arrives with the release JSON —
    // no second request, and none of the CORS trouble of fetching a .sha256 file.
    apkSha256: apk?.digest?.startsWith('sha256:') ? apk.digest.slice(7) : null,
  }
}

/**
 * Computes the SHA-256 hash of an ArrayBuffer using the Web Crypto API.
 * Returns the hex-encoded digest string.
 */
export async function sha256(buffer) {
  const hash = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('')
}

/**
 * Downloads the APK from `url`, verifies its SHA-256 hash against `expectedHash`
 * (if provided), and triggers the Android installer.
 * Only works on the MOBILE (Capacitor) build with Android.
 *
 * @param {string} url - Direct download URL for the APK
 * @param {string|null} expectedHash - Expected SHA-256 hex string, or null to skip verification
 * @param {function|null} onProgress - Called with (received, total) bytes during download, or null
 */
export async function downloadAndInstall(url, expectedHash = null, onProgress = null) {
  if (!MOBILE) {
    // On web, just open the release page
    window.open(RELEASES_PAGE, '_blank', 'noopener')
    return
  }

  const { Filesystem, Directory } = await import('@capacitor/filesystem')

  // Download with progress tracking via ReadableStream
  let res
  try {
    res = await fetch(url)
  } catch (e) {
    // A host that serves its files without CORS headers (GitHub's release CDN does) leaves the
    // WebView unable to read the body at all. The system browser has no such limit: it downloads
    // the APK and the notification/tap hands it to the installer — the same file, one tap later.
    window.open(url, '_blank', 'noopener')
    return
  }
  if (!res.ok) throw new Error(`Download failed: ${res.status}`)

  const total = parseInt(res.headers.get('content-length') || '0', 10)
  const reader = res.body.getReader()
  const chunks = []
  let received = 0

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
    received += value.length
    if (onProgress) onProgress(received, total)
  }

  // Reassemble into a single blob
  const blob = new Blob(chunks)

  // Size check: an APK should be at least 100 KB
  if (blob.size < 100_000) {
    throw new Error('Downloaded file is too small to be a valid APK (' + blob.size + ' bytes)')
  }

  // SHA-256 integrity check
  if (expectedHash) {
    const buffer = await blob.arrayBuffer()
    const actualHash = await sha256(buffer)
    if (actualHash !== expectedHash.toLowerCase().trim()) {
      throw new Error('SHA-256 mismatch — download may be corrupted or tampered with')
    }
  }

  // Convert blob to base64
  const base64 = await new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result.split(',')[1])
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })

  const fileName = 'olygym-update.apk'
  await Filesystem.writeFile({
    path: fileName,
    directory: Directory.Cache,
    data: base64,
  })

  // Use the local InstallPlugin to trigger the Android package installer
  const { registerPlugin } = await import('@capacitor/core')
  const Install = registerPlugin('Install')
  await Install.installApk({ fileName })
}
