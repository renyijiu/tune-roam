# Natural Earth

`app/androidApp/src/main/kotlin/app/tuneroam/radio/ui/fx/LandDots.kt` is generated from
`ne_110m_land.geojson` in [Natural Earth](https://www.naturalearthdata.com/) (1:110m physical
vectors, via github.com/nvkelso/natural-earth-vector). Natural Earth is in the public domain.

The land polygons are sampled on an equal-area grid with 1.2° spacing: each latitude row holds
`round(360 · cos(lat) / 1.2)` points, and each point is tested against the polygons (holes
excluded). Rows are stored as run lengths of sea and land.
