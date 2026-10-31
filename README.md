# Aquarium

A Fabric **1.20.1** port of [Terrarium](https://github.com/gegy1000/Terrarium) by gegy: a world type that generates a replica of the Earth from real elevation and land-cover data, downloaded as you explore from the Terrarium geo3 data server and cached locally.

## Status (0.1.0, first milestone)
- World presets (Create World → More → World Type): **Earth 1:40**, **1:10**, **1:4**, **1:1**
  - 1:40 uses the normal overworld height; the others use a tall dimension (y -512 to 2031)
  - vertical scale: 40 m/block (1:40), 10 (1:10), 5 (1:4 and 1:1, so Everest still fits)
- Terrain from NASADEM + GEBCO elevation, oceans, inland water from land cover
- Biomes from ESA CCI land cover, latitude and altitude (vanilla biomes, so vanilla trees, grass, mobs and villages appear)
- `/geotp <lat> <lon>` and `/geotp <place name>` (OpenStreetMap Nominatim), op level 2
- `/geotool` - where am I (lat/lon with an OpenStreetMap link, elevation, land cover)
- F3 shows lat/lon and land cover

Not yet ported: customization GUI, `/geotool` map UI and preview, climate/soil data, ocean polygons, caves/ores tuning, OSM roads.

## Data attribution
NASADEM (NASA/JPL-Caltech), GEBCO 2019, ESA CCI Land Cover, served by the Terrarium geo3 server by gegy. Geocoding: OpenStreetMap Nominatim (© OpenStreetMap contributors).

## License
LGPL-3.0, like the original Terrarium.
