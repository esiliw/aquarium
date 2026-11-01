"""One-off: fetch Beck et al. (2023) Koppen-Geiger maps, write a small present-day class raster for the mod jar."""
import io, json, os, sys, urllib.request, zipfile
import numpy as np
from PIL import Image

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
meta = json.load(urllib.request.urlopen("https://api.figshare.com/v2/articles/21789074"))
info = {"license": meta.get("license"), "citation": meta.get("citation"), "doi": meta.get("doi"),
        "files": [(f["name"], f["size"]) for f in meta["files"]]}
url = next(f["download_url"] for f in meta["files"] if f["name"] == "koppen_geiger_tif.zip")
data = urllib.request.urlopen(url).read()
z = zipfile.ZipFile(io.BytesIO(data))
names = z.namelist()
info["zip"] = [n for n in names if not n.endswith("/")][:80]
legend = next((n for n in names if n.endswith("legend.txt")), None)
if legend:
    info["legend"] = z.read(legend).decode("utf-8", "replace")
present = [n for n in names if "1991_2020" in n and n.endswith(".tif")]
info["present"] = present
# prefer an existing ~0.1 degree file, else the finest one downsampled by majority vote
pick = next((n for n in present if "0p1" in n), None) or sorted(present)[0]
info["picked"] = pick
import rasterio
with rasterio.open(io.BytesIO(z.read(pick))) as src:
    arr = src.read(1)
    info["shape"] = arr.shape
    info["transform"] = list(src.transform)[:6]
H, W = 1800, 3600
if arr.shape != (H, W):
    fy, fx = arr.shape[0] // H, arr.shape[1] // W
    a = arr[:H * fy, :W * fx].reshape(H, fy, W, fx).transpose(0, 2, 1, 3).reshape(H, W, fy * fx)
    out_arr = np.zeros((H, W), np.uint8)
    for y in range(H):
        row = a[y]
        counts = np.zeros((W, 32), np.int32)
        for k in range(row.shape[1]):
            v = np.clip(row[:, k], 0, 31)
            counts[np.arange(W), v] += 1
        counts[:, 0] = np.where(counts[:, 1:].sum(1) > 0, 0, counts[:, 0])   # land beats no-data at coasts
        out_arr[y] = counts.argmax(1)
    arr = out_arr
Image.fromarray(arr.astype(np.uint8), "L").save(os.path.join(out, "koppen.png"), optimize=True)
info["classes"] = {int(k): int(v) for k, v in zip(*np.unique(arr, return_counts=True))}
json.dump(info, open(os.path.join(out, "info.json"), "w"), indent=1, default=str)
print(json.dumps({k: v for k, v in info.items() if k not in ("zip",)}, indent=1, default=str)[:4000])
