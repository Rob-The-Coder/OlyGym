// Routine glyphs.
//
// This used to carry the picker's group definitions and a legacy emoji map, because routines
// stored a literal emoji in `r.emoji` ('💪', '🦵', …) before the redesign stored an icon key
// instead. The redesign's picker was never wired to anything: glyphPicker had no caller, so
// glyphOf and GLYPH_GROUPS had none either, and the emoji map existed only to feed glyphOf. All
// of it went with the picker (WS22). index.css still has .glyph-grid and .glyph-cell, which is
// upstream and untouched.
//
// What is left is the one thing a day still needs: a glyph to draw when it has none of its own.

export const DEFAULT_GLYPH = 'figureStrength'
