// The video layer, deliberately separate from the poster frame (lib/media.js): the picture is an
// <img> from img.youtube.com, the video is an <iframe> from YouTube. Nothing is downloaded, and
// nothing is requested from YouTube until the reader actually asks for the video — the poster is
// what a list or a sheet shows by default.
import { videoIdOf } from './media.js'

// youtube-nocookie is the embed host that sets no tracking cookie before play. `autoplay=1`
// because the iframe only exists when someone tapped for it, `playsinline` keeps iOS from taking
// the video full-screen on its own, `rel=0` keeps the end screen on this channel.
export function embedUrl(ex) {
  const id = videoIdOf(ex)
  if (!id) return null
  return `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&playsinline=1&rel=0`
}

// 'off' | 'button' | 'inline'; anything else (undefined, a legacy value) means the default.
export const videoMode = value => (value === 'off' || value === 'inline' ? value : 'button')
