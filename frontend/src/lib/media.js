// Every catalogue entry links the YouTube demo of the lift (Catalyst Athletics films one per
// exercise). The app shows the video's poster frame: it is hotlinked from img.youtube.com at
// runtime, never downloaded and never committed — YouTube's terms allow hotlinking and embedding,
// bulk downloading does not. The real video is a separate layer (lib/video.js, WS3).
import { exOr } from './exercises.js'

// `ex` is normally a catalogue entry, but the sheets also hand over routine targets by id alone.
const videoLink = ex => (ex?.yt ? ex.yt : ex?.id ? exOr(ex.id)?.yt : null)

const VIDEO_ID = /(?:youtube\.com\/(?:watch\?(?:[^#]*&)?v=|embed\/|shorts\/|live\/)|youtu\.be\/)([A-Za-z0-9_-]{11})/

// null for a custom exercise, or for anything whose link is not a YouTube video.
export function videoIdOf(ex) {
  const m = VIDEO_ID.exec(String(videoLink(ex) || ''))
  return m ? m[1] : null
}

// maxresdefault 1280×720 is the pretty one, hqdefault 480×360 (4:3, letterboxed — cropped away by
// object-fit:cover) always exists, mqdefault 320×180 is the cheap one for the list rows.
export const thumbUrl = (ex, size = 'maxresdefault') => {
  const id = videoIdOf(ex)
  return id ? `https://img.youtube.com/vi/${id}/${size}.jpg` : null
}

// The order an <img> walks on error, best first. Empty means "no media at all": the caller draws
// its neutral tile instead. Object URLs are not used here, so nothing leaks on unmount.
export const imageChain = (ex, sizes = ['maxresdefault', 'hqdefault']) =>
  sizes.map(s => thumbUrl(ex, s)).filter(Boolean)
