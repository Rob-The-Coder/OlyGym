import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { checkForUpdate, sha256, resetUpdateCheck } from './update.js'

// __APP_VERSION__ is defined at build time by vite.config.js (reads package.json).
// In the test environment vitest applies the same define, so it's available here.

describe('sha256', () => {
  it('computes the correct hash for a known input', async () => {
    const input = new TextEncoder().encode('hello world')
    const hash = await sha256(input.buffer)
    // Well-known SHA-256 of "hello world"
    expect(hash).toBe('b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9')
  })

  it('computes a different hash for different input', async () => {
    const a = await sha256(new TextEncoder().encode('aaa').buffer)
    const b = await sha256(new TextEncoder().encode('bbb').buffer)
    expect(a).not.toBe(b)
  })

  it('returns a 64-character hex string', async () => {
    const hash = await sha256(new TextEncoder().encode('test').buffer)
    expect(hash).toHaveLength(64)
    expect(hash).toMatch(/^[0-9a-f]{64}$/)
  })
})

const SHA = 'bf484160250db1bfb86baf99d585ef0d5c2306a6b364846ced8848bfa4820fb1'

// A release asset exactly as the GitHub API returns it: the file name, the browser download
// URL, and the digest GitHub computed for the upload.
const asset = (name, digest = null) => ({
  name,
  browser_download_url: `https://github.com/Rob-The-Coder/OlyGym/releases/download/v99.0.0/${name}`,
  digest,
})
const rel = (tag, assets = []) => [{ tag_name: tag, assets }]

describe('checkForUpdate', () => {
  let originalFetch

  beforeEach(() => { originalFetch = globalThis.fetch; resetUpdateCheck() })
  afterEach(() => { globalThis.fetch = originalFetch })

  function mockFetch(body, status = 200) {
    globalThis.fetch = vi.fn(() => Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve(body),
    }))
  }

  it('reports no update when the latest release matches the current version', async () => {
    mockFetch(rel('v' + __APP_VERSION__))
    const result = await checkForUpdate()
    expect(result.hasUpdate).toBe(false)
    expect(result.latestVersion).toBe(__APP_VERSION__)
    expect(result.apkUrl).toBe(null)
    expect(result.hashUrl).toBe(null)
    expect(result.apkSha256).toBe(null)
  })

  it('reports no update when the latest release is older than current', async () => {
    mockFetch(rel('v0.0.1'))
    const result = await checkForUpdate()
    expect(result.hasUpdate).toBe(false)
    expect(result.latestVersion).toBe('0.0.1')
  })

  it('reports an update when the latest release is newer', async () => {
    mockFetch(rel('v99.0.0'))
    const result = await checkForUpdate()
    expect(result.hasUpdate).toBe(true)
    expect(result.latestVersion).toBe('99.0.0')
  })

  it('strips the v prefix from the tag name', async () => {
    mockFetch(rel('v99.1.2'))
    const result = await checkForUpdate()
    expect(result.latestVersion).toBe('99.1.2')
  })

  it('handles tag names without a v prefix', async () => {
    mockFetch(rel('99.0.0'))
    const result = await checkForUpdate()
    expect(result.hasUpdate).toBe(true)
    expect(result.latestVersion).toBe('99.0.0')
  })

  it('finds the APK download URL from the release assets', async () => {
    mockFetch(rel('v99.0.0', [asset('OlyGym-99.0.0.apk', 'sha256:' + SHA)]))
    const result = await checkForUpdate()
    expect(result.apkUrl).toBe('https://github.com/Rob-The-Coder/OlyGym/releases/download/v99.0.0/OlyGym-99.0.0.apk')
  })

  it('uses the digest GitHub computed for the APK as the expected checksum', async () => {
    mockFetch(rel('v99.0.0', [asset('OlyGym-99.0.0.apk', 'sha256:' + SHA)]))
    const result = await checkForUpdate()
    expect(result.apkSha256).toBe(SHA)
  })

  it('reports no checksum when the digest is not a sha256', async () => {
    mockFetch(rel('v99.0.0', [asset('OlyGym-99.0.0.apk', 'sha512:deadbeef')]))
    const result = await checkForUpdate()
    expect(result.apkSha256).toBe(null)
  })

  it('returns null apkUrl when no .apk asset exists', async () => {
    mockFetch(rel('v99.0.0', [asset('changelog.md')]))
    const result = await checkForUpdate()
    expect(result.hasUpdate).toBe(true)
    expect(result.apkUrl).toBe(null)
  })

  it('finds the .sha256 asset published beside the APK', async () => {
    mockFetch(rel('v99.0.0', [asset('OlyGym-99.0.0.apk', 'sha256:' + SHA), asset('OlyGym-99.0.0.apk.sha256')]))
    const result = await checkForUpdate()
    expect(result.hashUrl).toBe('https://github.com/Rob-The-Coder/OlyGym/releases/download/v99.0.0/OlyGym-99.0.0.apk.sha256')
  })

  // The same payload shape GitHub returns, trimmed: the checksum file is listed FIRST, so the
  // APK detection must not settle for it (a `.apk.sha256` is not an `.apk`).
  const REAL_RELEASE = [{
    tag_name: 'v1.3.1',
    name: 'OlyGym v1.3.1',
    assets: [
      {
        name: 'OlyGym-1.3.1.apk.sha256',
        browser_download_url: 'https://github.com/Rob-The-Coder/OlyGym/releases/download/v1.3.1/OlyGym-1.3.1.apk.sha256',
        digest: 'sha256:a5cd85416d9affe1a1d3f6158f7bd70961a10bc42f78a509384d14b78381be13',
      },
      {
        name: 'OlyGym-1.3.1.apk',
        browser_download_url: 'https://github.com/Rob-The-Coder/OlyGym/releases/download/v1.3.1/OlyGym-1.3.1.apk',
        digest: 'sha256:955a70e8a55a8540bbdc3071634bffff7048ef461028ea225c892a5d9b17796d',
      },
    ],
  }]

  it('finds the APK and its checksum in a real GitHub release payload', async () => {
    mockFetch(REAL_RELEASE)
    const result = await checkForUpdate()
    expect(result.latestVersion).toBe('1.3.1')
    expect(result.apkUrl).toBe('https://github.com/Rob-The-Coder/OlyGym/releases/download/v1.3.1/OlyGym-1.3.1.apk')
    expect(result.hashUrl).toBe('https://github.com/Rob-The-Coder/OlyGym/releases/download/v1.3.1/OlyGym-1.3.1.apk.sha256')
    expect(result.apkSha256).toBe('955a70e8a55a8540bbdc3071634bffff7048ef461028ea225c892a5d9b17796d')
  })

  it('returns null hashUrl when no hash asset exists', async () => {
    mockFetch(rel('v99.0.0', [asset('OlyGym-99.0.0.apk', 'sha256:' + SHA)]))
    const result = await checkForUpdate()
    expect(result.hashUrl).toBe(null)
  })

  it('returns no update when the releases array is empty', async () => {
    mockFetch([])
    const result = await checkForUpdate()
    expect(result.hasUpdate).toBe(false)
    expect(result.latestVersion).toBe(__APP_VERSION__)
    expect(result.hashUrl).toBe(null)
  })

  // A repo with no releases answers 200 []; a renamed or private one answers 404. Neither is
  // worth an error toast on every Settings visit.
  it('returns no update when the repo answers 404', async () => {
    mockFetch(null, 404)
    const result = await checkForUpdate()
    expect(result.hasUpdate).toBe(false)
    expect(result.latestVersion).toBe(__APP_VERSION__)
  })

  it('throws when the API responds with an error status', async () => {
    mockFetch(null, 500)
    await expect(checkForUpdate()).rejects.toThrow('GitHub API 500')
  })

  it('throws on network failure', async () => {
    globalThis.fetch = vi.fn(() => Promise.reject(new Error('Network error')))
    await expect(checkForUpdate()).rejects.toThrow('Network error')
  })
})

describe('semver comparison (via checkForUpdate behavior)', () => {
  let originalFetch
  beforeEach(() => { originalFetch = globalThis.fetch; resetUpdateCheck() })
  afterEach(() => { globalThis.fetch = originalFetch })

  function mockRelease(tag) {
    globalThis.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 200,
      json: () => Promise.resolve(rel(tag)),
    }))
  }

  // Versions are derived from the running __APP_VERSION__ so the suite never breaks
  // when package.json bumps. bump(2, +1) raises the patch; bump(0, +1) raises the major.
  const [MAJ, MIN, PATCH] = __APP_VERSION__.split('.').map(Number)
  const bump = (idx, by) => {
    const parts = [MAJ, MIN, PATCH]
    parts[idx] += by
    return 'v' + parts.join('.')
  }

  it('detects a patch bump as an update', async () => {
    mockRelease(bump(2, 1))
    expect((await checkForUpdate()).hasUpdate).toBe(true)
  })

  it('detects a minor bump as an update', async () => {
    mockRelease(bump(1, 1))
    expect((await checkForUpdate()).hasUpdate).toBe(true)
  })

  it('detects a major bump as an update', async () => {
    mockRelease(bump(0, 1))
    expect((await checkForUpdate()).hasUpdate).toBe(true)
  })

  it('does not flag an older patch as an update', async () => {
    // One patch below current (current patch is always >= our test floor)
    mockRelease('v' + [MAJ, MIN, Math.max(0, PATCH - 1)].join('.'))
    // Only meaningful when we could actually go lower; when patch is 0 this equals current,
    // which correctly reports no update either way.
    expect((await checkForUpdate()).hasUpdate).toBe(false)
  })

  it('does not flag an older minor as an update', async () => {
    // A version guaranteed lower than any 1.x+ release: same major, minor 0, patch 0,
    // minus one on the minor when possible.
    mockRelease('v' + [MAJ, Math.max(0, MIN - 1), 0].join('.'))
    expect((await checkForUpdate()).hasUpdate).toBe(false)
  })
})
