# Aquarium

A Fabric **1.20.1** port of [Terrarium](https://github.com/gegy1000/Terrarium) by gegy: a world type that generates a replica of the Earth from real elevation, land-cover and map data, downloaded as you explore and cached locally.

## Status (0.8.1)
- World type (Create World → More → World Type): **Earth (Aquarium)**, then **Customize** to set
  - scale 1:N (default 1:10) and metres per block for height (default auto: like the scale, but at least 5 so Everest fits)
  - where the world starts: a place name or `lat, lon`, looked up like `/geotp` (empty = Malmö)
  - projection (true shapes / original Terrarium) and data source (open data / Terrarium server)
  - **road & rail guide** on/off: OSM roads drawn on the ground (motorways/trunk black concrete, main roads grey concrete, smaller streets dirt path; minor streets only at 1:12 or larger), railways as gravel beds with a polished-andesite centre line, trams/light rail yellow terracotta, stations as red concrete pads. It follows the terrain as it is (nothing graded) and leaves tunnels out; it's a tracing guide, not a finished network
  - **buildings** on/off: OSM footprints as solid cobblestone boxes with a flat roof, height in true proportion to the footprint (10 m house ≈ 2 blocks at 1:10 in Malmö). Only at about 1:20 or larger (OSM's coarser tiles have no buildings). Meant to be cleared with WorldEdit wherever you build yourself
- Mercator scales are given at the equator: further from it everything comes out larger (1:10 is about 1:5.7 in Malmö). F3 and `/geotool` show the scale where you stand. Nothing beyond 85° N/S.
- Tall world: y -512 to 2031
- **Open data sources** (new worlds): elevation and sea depth from the AWS terrain tiles, 10 m land cover from ESA WorldCover, sea / lakes / rivers / streams from OpenStreetMap (OpenFreeMap vector tiles, pinned to one dated snapshot per world so later OSM edits never cause seams)
- Worlds made with versions before 0.4 keep generating from the Terrarium server exactly as before (`"data_source": "terrarium"`, equirectangular projection)
- Failed downloads are retried until they succeed instead of being treated as "no data", so a server hiccup can't bake holes into the world. Everything downloaded is kept in `.minecraft/aquarium-cache`.
- Biomes from land cover, climate (Köppen-Geiger) and altitude: vanilla biomes plus Aquarium's own Mixed Forest, Mediterranean Woodland, Chaparral, Steppe, Cold Desert, Tundra and Subtropical Forest (vanilla trees and mobs, own grass colours and tree mixes). The ground follows the climate too: dry grass and coarse dirt in steppes, gravel in cold deserts, moss on tundra, red sand in the Outback, black basalt and black sand in volcanic regions. Irrigated desert farmland (the Nile) stays green
- `/geotp <lat> <lon>` and `/geotp <place name>` (OpenStreetMap Nominatim), op level 2
- `/geotool` - where am I (lat/lon with an OpenStreetMap link, elevation, land cover, local scale)
- F3 shows lat/lon, land cover and local scale

Not yet ported: `/geotool` map UI and preview, caves/ores tuning.

## Data attribution
- Elevation: [Terrain Tiles on AWS](https://registry.opendata.aws/terrain-tiles/) (Tilezen/Mapzen): SRTM, GMTED, NED/3DEP, ETOPO1, GEBCO and other sources
- Land cover: © ESA WorldCover project 2021 / Contains modified Copernicus Sentinel data (2021) processed by ESA WorldCover consortium (CC BY 4.0)
- Water: © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) (ODbL), tiles by [OpenFreeMap](https://openfreemap.org) using the [OpenMapTiles](https://openmaptiles.org) schema
- Older worlds: NASADEM (NASA/JPL-Caltech), GEBCO 2019, ESA CCI Land Cover, ocean outlines, served by the Terrarium geo3 server by gegy
- Climate: Beck, H. E. et al. (2023), High-resolution (1 km) Köppen-Geiger maps for 1901–2099 based on constrained CMIP6 projections, Scientific Data 10, 724 (CC BY 4.0), present-day map downsampled to 0.1° and bundled
- Geocoding: OpenStreetMap Nominatim

## License
LGPL-3.0, like the original Terrarium.
