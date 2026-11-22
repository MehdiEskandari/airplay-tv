# App icon

`icon.svg` and the native Android vectors use the unchanged AirPlay Video path from [Simple Icons](https://github.com/simple-icons/simple-icons/blob/develop/icons/airplayvideo.svg), distributed under [CC0](https://github.com/simple-icons/simple-icons/blob/develop/LICENSE.md).

The symbol is white on a solid black background. `icon.png` is a 512 x 512 render of the SVG for the store listings. The launcher, notification and in-app brand mark use the same symbol geometry.

Native Android paths expand the SVG's compact arc flags into separate values for VectorDrawable compatibility. This changes the path syntax only; the geometry is preserved. `scripts/verify-release.py` checks the native arc format before release builds.
