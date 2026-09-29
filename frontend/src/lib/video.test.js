import { describe, expect, it } from 'vitest'
import { embedUrl, videoMode } from './video.js'

const watch = 'https://www.youtube.com/watch?v=T11EcgGww-M'

describe('embedUrl', () => {
  it('points at the nocookie embed host, with the id and the options that make it behave', () => {
    expect(embedUrl({ yt: watch })).toBe('https://www.youtube-nocookie.com/embed/T11EcgGww-M?autoplay=1&playsinline=1&rel=0')
  })

  it('accepts the other link shapes the catalogue and the coaches use', () => {
    expect(embedUrl({ yt: 'https://youtu.be/T11EcgGww-M' })).toContain('/embed/T11EcgGww-M')
    expect(embedUrl({ yt: 'https://www.youtube.com/shorts/T11EcgGww-M' })).toContain('/embed/T11EcgGww-M')
  })

  it('is null when there is no video to embed', () => {
    expect(embedUrl({ id: 'custom-1', n: 'my lift' })).toBe(null)
    expect(embedUrl({ yt: '' })).toBe(null)
    expect(embedUrl(null)).toBe(null)
  })

  it('never sends the reader to youtube.com for the embed itself', () => {
    expect(embedUrl({ yt: watch })).not.toContain('//www.youtube.com')
  })

  it('can embed every exercise of the catalogue', async () => {
    const { EXDB } = await import('./exercises-data.js')
    expect(EXDB.filter(e => !embedUrl(e)).map(e => e.id)).toEqual([])
  })
})

describe('videoMode', () => {
  it('is "button" by default, and for anything that is not one of the two other values', () => {
    expect(videoMode(undefined)).toBe('button')
    expect(videoMode('button')).toBe('button')
    expect(videoMode('nonsense')).toBe('button')
  })

  it('passes the two explicit values through', () => {
    expect(videoMode('off')).toBe('off')
    expect(videoMode('inline')).toBe('inline')
  })
})
