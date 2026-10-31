# Aquarium

A Fabric **1.20.1** port of [Terrarium](https://github.com/gegy1000/Terrarium) by gegy: a world type that generates a replica of the Earth from real elevation, land-cover and map data, downloaded as you explore and cached locally.

## Status (0.5.0)
- World type (Create World → More → World Type): **Earth (Aquarium)**, then **Customize** to set
  - scale 1:N (default 1:10) and metres per block for height (default auto: like the scale, but at least 5 so Everest fits)
  - where the world starts: a place name or `lat, lon`, looked up like `/geotp` (empty = Malmö)
  - projection (true shapes / original Terrarium) and data source (open data / Terrarium server)
- Mercator scales are given at the equator: further from it everything comes out larger (1:10 is about 1:5.7 in Malmö). F3 and `/geotool` show the scale where you stand. Nothing beyond 85° N/S.
- Tall world: y -512 to 2031
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
