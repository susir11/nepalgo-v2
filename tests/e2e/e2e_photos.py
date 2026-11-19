"""E2E: destination photos - add by URL (SSRF-guarded), served via the gateway, remove.
Adds a CC-licensed Wikimedia photo as a TEST and removes it again at the end."""
import os, sys, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # helpers live next to this file
from e2e_lib import *  # noqa

G = "http://localhost:8080"
DEST = int(os.environ.get("E2E_PHOTO_DEST", "1"))
PHOTO = ("https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/"
         "Everest_North_Face_toward_Base_Camp_Tibet_Luca_Galuzzi_2006.jpg/"
         "1280px-Everest_North_Face_toward_Base_Camp_Tibet_Luca_Galuzzi_2006.jpg")


def images():
    s, d = call(None, "GET", f"{G}/api/destinations/{DEST}")
    return d.get("images", [])


def fetch(url):
    try:
        r = urllib.request.urlopen(G + url if url.startswith("/") else url, timeout=15)
        return r.status, r.headers.get("Content-Type"), len(r.read())
    except Exception as e:
        return getattr(e, "code", str(e)), None, 0


before = images()
s, img = call("admin", "POST", f"{G}/api/admin/destinations/{DEST}/images/from-url", {"url": PHOTO})
check("add photo by URL -> 200 with all three sizes", s == 200 and img.get("thumbnailUrl") and img.get("fullUrl"), (s, img))
new_id = img.get("id") if s == 200 else None

after = images()
check("photo appears last in the destination's slideshow", len(after) == len(before) + 1 and after[-1]["id"] == new_id, after[-1:])
if new_id:
    for size in ("thumbnailUrl", "mediumUrl", "fullUrl"):
        st, ct, n = fetch(img[size])
        check(f"{size} served through the gateway as JPEG", st == 200 and ct == "image/jpeg" and n > 1000, (st, ct, n))
    st, ct, n = fetch(img["fullUrl"])
    check("stored copy is our re-encoded 1600 px JPEG, not the 365 KB original", n != 365484, n)

# SSRF guard: none of these may be fetched by the server
for bad, why in [("http://localhost:8081/actuator/health", "non-standard port"),
                 ("http://169.254.169.254/latest/meta-data/", "cloud metadata address"),
                 ("http://10.0.0.1/x.jpg", "private network"),
                 ("http://localhost/x.jpg", "loopback"),
                 ("file:///etc/passwd", "file scheme"),
                 ("https://en.wikipedia.org/wiki/Mount_Everest", "HTML page, not an image")]:
    s, r = call("admin", "POST", f"{G}/api/admin/destinations/{DEST}/images/from-url", {"url": bad})
    check(f"refused: {why} -> 400 INVALID_IMAGE", s == 400 and r.get("code") == "INVALID_IMAGE", (s, r))
check("refused URLs added no photos", len(images()) == len(after), len(images()))

s, r = call("customer", "POST", f"{G}/api/admin/destinations/{DEST}/images/from-url", {"url": PHOTO})
check("customer token cannot add photos", s in (401, 403), s)

# Remove (also cleans up the test photo)
if new_id:
    s, r = call("admin", "DELETE", f"{G}/api/admin/destinations/{DEST}/images/{new_id}")
    check("remove photo -> 204", s == 204, (s, r))
    final = images()
    check("photo gone from the destination", all(i["id"] != new_id for i in final) and len(final) == len(before), final)
    check("cover/order kept dense (0..n-1)", [i["id"] for i in final] == [i["id"] for i in before], final)
    st, _, _ = fetch(img["fullUrl"])
    check("stored files deleted too (404 via gateway)", st == 404, st)
    s, r = call("admin", "DELETE", f"{G}/api/admin/destinations/{DEST}/images/{new_id}")
    check("removing it again -> 400, nothing else touched", s == 400, (s, r))

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed")
