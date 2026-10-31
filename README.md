# Aquarium

A Fabric **1.20.1** port of [Terrarium](https://github.com/gegy1000/Terrarium) by gegy: a world type that generates a replica of the Earth from real elevation, land-cover and map data, downloaded as you explore and cached locally.

## Status (0.4.0)
- World presets (Create World → More → World Type): **Earth 1:40**, **1:10**, **1:4**, **1:1**
  - scales are given at the equator; new worlds use a Mercator projection (true shapes everywhere, so places further from the equator come out larger: 1:4 is about 1:3.2 at Niigata and 1:2.5 in Germany). F3 and `/geotool` show the scale where you stand. Nothing beyond 85° N/S.
  - 1:40 uses the normal overworld height; the others use a tall dimension (y -512 to 2031)
  - vertical scale: 40 m/block (1:40), 10 (1:10), 5 (1:4 and 1:1, so Everest still fits)
- **Open data sources** (new worlds): elevation and sea depth from the AWS terrain tiles, 10 m land cover from ESA WorldCover, sea / lakes / rivers / streams from OpenStreetMap (OpenFreeMap vector tiles, pinned to one dated snapshot per world so later OSM edits never cause seams)
- Worlds made with versions before 0.4 keep generating from the Terrarium server exactly as before (`"data_source": "terrarium"`, equirectangular projection)
- Failed downloads are retried until they succeed instead of being treated as "no data", so a server hiccup can't bake holes into the world. Everything downloaded is kept in `.minecraft/aquarium-cache`.
- Biomes from land cover, latitude and altitude (vanilla biomes, so vanilla trees, grass, mobs and villages appear)
- `/geotp <lat> <lon>` and `/geotp <place name>` (OpenStreetMap Nominatim), op level 2
- `/geotool` - where am I (lat/lon with an OpenStreetMap link, elevation, land cover, local scale)
- F3 shows lat/lon, land cover and local scale

Not yet ported: customization GUI, `/geotool` map UI and preview, climate/soil data, caves/ores tuning, OSM roads.

## Data attribution
- Elevation: [Terrain Tiles on AWS](https://registry.opendata.aws/terrain-tiles/) (Tilezen/Mapzen): SRTM, GMTED, NED/3DEP, ETOPO1, GEBCO and other sources
- Land cover: © ESA WorldCover project 2021 / Contains modified Copernicus Sentinel data (2021) processed by ESA WorldCover consortium (CC BY 4.0)
- Water: © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) (ODbL), tiles by [OpenFreeMap](https://openfreemap.org) using the [OpenMapTiles](https://openmaptiles.org) schema
- Older worlds: NASADEM (NASA/JPL-Caltech), GEBCO 2019, ESA CCI Land Cover, ocean outlines, served by the Terrarium geo3 server by gegy
- Geocoding: OpenStreetMap Nominatim

## License
LGPL-3.0, like the original Terrarium.
