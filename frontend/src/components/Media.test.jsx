// @vitest-environment happy-dom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Media, { Thumb } from './Media.jsx'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

const mocks = vi.hoisted(() => {
  const state = { S: { gifSize: 'full' } }
  state.snapshot = () => ({
    S: state.S,
    update: mut => {
      const next = structuredClone(state.S)
      mut(next)
      state.S = next
    },
  })
  return state
})
vi.mock('../store/useStore.js', () => {
  const useStore = selector => selector(mocks.snapshot())
  useStore.getState = mocks.snapshot
  return { useStore }
})
// The embed URL itself is lib/video.test.js's job — a pure function, no DOM needed. Here it is
// stubbed to about:blank, because what these tests are about is *when* the iframe exists, and
// happy-dom would otherwise fetch YouTube for real and log a stack trace on every mount.
vi.mock('../lib/video.js', async () => ({
  ...(await vi.importActual('../lib/video.js')),
  embedUrl: ex => (ex?.yt ? 'about:blank' : null),
}))

// A real catalogue entry: the media is the poster frame of its YouTube video.
const EX = { id: 'wl163', n: '2 position power snatch', yt: 'https://www.youtube.com/watch?v=T11EcgGww-M' }
const MAXRES = 'https://img.youtube.com/vi/T11EcgGww-M/maxresdefault.jpg'
const HQ = 'https://img.youtube.com/vi/T11EcgGww-M/hqdefault.jpg'
const THUMB = 'https://img.youtube.com/vi/T11EcgGww-M/mqdefault.jpg'

let host, root
beforeEach(() => {
  mocks.S = { gifSize: 'full' }
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
})

const mount = props => act(() => root.render(<Media ex={EX} {...props} />))
const img = () => host.querySelector('.exmedia img')
const fail = () => act(() => { img().dispatchEvent(new Event('error')) })

describe('Media gifSize', () => {
  it('renders the poster frame by default and toggles to mini in the workout', () => {
    mount({ minimizable: true })
    expect(img().getAttribute('src')).toBe(MAXRES)
    expect(host.querySelector('.exmedia.mini')).toBeFalsy()
    act(() => { host.querySelector('.giftoggle').click() })
    expect(mocks.S.gifSize).toBe('mini')
    mount({ minimizable: true })
    expect(host.querySelector('.exmedia.mini')).toBeTruthy()
  })

  it("renders nothing at all in the workout when gifSize is 'off'", () => {
    mocks.S = { gifSize: 'off' }
    mount({ minimizable: true })
    expect(host.querySelector('.exmedia')).toBeFalsy()
    expect(host.querySelector('img')).toBeFalsy()
    expect(host.innerHTML).toBe('')
  })

  it("'off' only applies to the workout — the detail sheet (not minimizable) still shows media", () => {
    mocks.S = { gifSize: 'off' }
    mount({})
    expect(img().getAttribute('src')).toBe(MAXRES)
  })

  it('treats a legacy/unknown value as full', () => {
    mocks.S = { gifSize: 'huge' }
    mount({ minimizable: true })
    expect(img()).toBeTruthy()
    expect(host.querySelector('.exmedia.mini')).toBeFalsy()
  })
})

describe('Media fallbacks', () => {
  it('says what the picture is, and keeps the minimize control', () => {
    mount({ minimizable: true })
    expect(host.querySelector('.gifhint').textContent).toContain('video')
    expect(host.querySelector('.giftoggle')).toBeTruthy()
    mount({})
    expect(host.querySelector('.giftoggle')).toBeFalsy()
  })

  it('steps down to hqdefault when the maxres frame is missing (older uploads)', () => {
    mount({})
    fail()
    expect(img().getAttribute('src')).toBe(HQ)
  })

  it('shows a neutral tile — not a broken image — once every frame failed, and a tap retries', () => {
    mount({})
    fail()
    fail()
    expect(host.querySelector('.exmedia img')).toBeFalsy()
    expect(host.querySelector('.exmedia.broken .exmedia-x')).toBeTruthy()
    act(() => { host.querySelector('.exmedia').click() })
    expect(img().getAttribute('src')).toBe(MAXRES)
  })

  it('renders nothing for a custom exercise with no video', () => {
    act(() => root.render(<Media ex={{ id: 'custom-1', n: 'my lift' }} />))
    expect(host.innerHTML).toBe('')
  })
})

describe('Media video', () => {
  const EMBED = 'about:blank' // see the embedUrl mock above
  const badge = () => host.querySelector('button.gifhint')

  it('keeps the poster until the badge is tapped, then mounts the embed in its place', () => {
    mount({})
    expect(host.querySelector('iframe')).toBeFalsy()
    expect(img()).toBeTruthy()
    expect(badge().getAttribute('aria-label')).toBe('Play the demo video')
    act(() => { badge().click() })
    expect(host.querySelector('iframe').getAttribute('src')).toBe(EMBED)
    expect(host.querySelector('.exmedia img')).toBeFalsy()
    expect(badge()).toBeFalsy()
  })

  it("'off' never embeds anything from YouTube", () => {
    mocks.S = { gifSize: 'full', video: 'off' }
    mount({})
    expect(badge()).toBeFalsy()
    expect(host.querySelector('iframe')).toBeFalsy()
    expect(img()).toBeTruthy()
  })

  it("'inline' loads the player with the exercise", () => {
    mocks.S = { gifSize: 'full', video: 'inline' }
    mount({})
    expect(host.querySelector('iframe').getAttribute('src')).toBe(EMBED)
    expect(badge()).toBeFalsy()
  })

  it('shows no badge in the minimised strip, and no badge without a video', () => {
    mocks.S = { gifSize: 'mini' }
    mount({ minimizable: true })
    expect(badge()).toBeFalsy()
    act(() => root.render(<Media ex={{ id: 'wl999', n: 'custom', yt: '' }} />))
    expect(badge()).toBeFalsy()
  })

  it('does not let the tap reach the card behind it', () => {
    const onClick = vi.fn()
    act(() => root.render(<div onClick={onClick}><Media ex={EX} /></div>))
    act(() => { badge().click() })
    expect(onClick).not.toHaveBeenCalled()
  })
})

describe('Thumb', () => {
  it('uses the cheap frame and steps down on error, then draws the tile', () => {
    act(() => root.render(<Thumb ex={EX} />))
    expect(host.querySelector('.thumb').getAttribute('src')).toBe(THUMB)
    act(() => { host.querySelector('.thumb').dispatchEvent(new Event('error')) })
    expect(host.querySelector('.thumb').getAttribute('src')).toBe(HQ)
    act(() => { host.querySelector('.thumb').dispatchEvent(new Event('error')) })
    expect(host.querySelector('.thumb.thumb-x')).toBeTruthy()
  })

  it('draws the tile straight away for a custom exercise', () => {
    act(() => root.render(<Thumb ex={{ id: 'custom-1' }} />))
    expect(host.querySelector('.thumb.thumb-x')).toBeTruthy()
  })
})
