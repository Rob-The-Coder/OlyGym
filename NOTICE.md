# Third-party notices

openGym — Copyright (C) 2026 Duarte Santos. OlyGym is a fork of openGym.
openGym's own code is licensed under the **GNU AGPL v3.0** (see [LICENSE](LICENSE)).

## App store exception

As an additional permission under section 7 of the AGPL v3.0, the copyright holder permits
distribution of the openGym mobile application through app store platforms (such as the
Apple App Store and Google Play) whose terms of service would otherwise be incompatible
with the AGPL, provided the corresponding source code remains available under the AGPL at
the project repository. This permission applies to the distribution channel only and does
not otherwise limit the license.

## Body diagram geometry

The muscle outlines the body maps are drawn from (`frontend/src/lib/body-paths.js`) are derived
from [**MuscleMap**](https://github.com/melihcolpan/MuscleMap) by Melih Colpan, used under the
**MIT License** and reproduced below. MuscleMap ships its path data as Swift source rather than
`.svg` files; the paths were converted to a JSON module, its sub-group shapes were dropped, and
nothing else about the artwork was changed.

```
MIT License

Copyright (c) 2026 Melih Colpan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Exercise data & media

### OlyGym's catalogue — Catalyst Athletics, and YouTube for the video

The catalogue in this fork (`frontend/src/lib/exercises-data.js`, rebuilt from
`scripts/oly-catalogue/catalyst-exercises.csv` by `scripts/oly-catalogue/build-oly-catalogue.mjs`)
comes from [**Catalyst Athletics**](https://www.catalystathletics.com/exercises/): the exercise
names, movement categories, equipment, muscle tags, descriptions and the demo video of each lift,
taken from their public exercise pages. Their text and their videos are **not** covered by
openGym's AGPL, and every entry cites the page it came from (`src`).

**No media is redistributed by this fork.** Nothing is downloaded: each entry links one YouTube
video, and the app hotlinks that video's poster frame from `img.youtube.com` at runtime
(1280×720 in the exercise detail views, 320×180 in list rows). YouTube's terms permit hotlinking
and embedding but not bulk downloading, so the frames are fetched by the reader's browser and
never stored. The videos themselves belong to Catalyst Athletics.
