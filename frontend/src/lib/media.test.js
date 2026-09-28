import { describe, expect, it } from 'vitest'
import { imageChain, thumbUrl, videoIdOf } from './media.js'

const ex = yt => ({ id: 'wl1', n: 'snatch', yt })

describe('videoIdOf', () => {
  it('reads the id out of every link shape the catalogue and the coaches use', () => {
    const id = 'T11EcgGww-M'
    expect(videoIdOf(ex(`https://www.youtube.com/watch?v=${id}`))).toBe(id)
    expect(videoIdOf(ex(`https://www.youtube.com/watch?list=PL1&v=${id}`))).toBe(id)
    expect(videoIdOf(ex(`https://youtu.be/${id}`))).toBe(id)
    expect(videoIdOf(ex(`https://www.youtube.com/embed/${id}`))).toBe(id)
    expect(videoIdOf(ex(`https://www.youtube.com/shorts/${id}`))).toBe(id)
  })

  it('gives back null for a custom exercise, an empty link or something that is not a video', () => {
    expect(videoIdOf({ id: 'custom-1', n: 'my lift' })).toBe(null)
    expect(videoIdOf(ex(''))).toBe(null)
    expect(videoIdOf(ex('https://example.com/watch?v=T11EcgGww-M'))).toBe(null)
    expect(videoIdOf(null)).toBe(null)
  })

  it('falls back to the catalogue when only the id is handed over (routine targets)', () => {
    // Routine entries carry the id alone; the catalogue entry behind it holds the link.
    expect(videoIdOf({ id: 'wl1' })).toBe(null)
    expect(videoIdOf({ id: 'wl163' })).toBe('T11EcgGww-M')
  })
})

describe('thumbUrl', () => {
  it('builds the img.youtube.com url for the size asked, maxresdefault by default', () => {
    expect(thumbUrl(ex('https://youtu.be/T11EcgGww-M'))).toBe('https://img.youtube.com/vi/T11EcgGww-M/maxresdefault.jpg')
    expect(thumbUrl(ex('https://youtu.be/T11EcgGww-M'), 'mqdefault')).toBe('https://img.youtube.com/vi/T11EcgGww-M/mqdefault.jpg')
    expect(thumbUrl({ id: 'custom' })).toBe(null)
  })
})

describe('imageChain', () => {
  it('walks big to small, so a missing maxresdefault steps down instead of going blank', () => {
    expect(imageChain(ex('https://youtu.be/T11EcgGww-M'))).toEqual([
      'https://img.youtube.com/vi/T11EcgGww-M/maxresdefault.jpg',
      'https://img.youtube.com/vi/T11EcgGww-M/hqdefault.jpg',
    ])
    expect(imageChain(ex('https://youtu.be/T11EcgGww-M'), ['mqdefault'])).toEqual(['https://img.youtube.com/vi/T11EcgGww-M/mqdefault.jpg'])
  })

  it('is empty — not a list of nulls — when there is nothing to show', () => {
    expect(imageChain({ id: 'custom-1' })).toEqual([])
  })

  it('has the poster frame first, which is what the detail views show', () => {
    expect(imageChain(ex('https://youtu.be/T11EcgGww-M'))[0]).toBe('https://img.youtube.com/vi/T11EcgGww-M/maxresdefault.jpg')
  })
})

describe('the catalogue itself', () => {
  it('has a usable YouTube link on every built-in exercise', async () => {
    const { EXDB } = await import('./exercises-data.js')
    const noId = EXDB.filter(e => !videoIdOf(e))
    expect(noId.map(e => e.id)).toEqual([])
  })
})
