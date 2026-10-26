"""Open the live site in a real browser and save screenshots + console errors (runs on a GitHub runner)."""
import asyncio, sys
from playwright.async_api import async_playwright

URL, OUT = sys.argv[1].rstrip("/") + "/", sys.argv[2]

async def main():
    errs, bad = [], []
    async with async_playwright() as p:
        b = await p.chromium.launch()
        for name, w, h, scheme in [("desk", 1366, 900, "light"), ("mob", 390, 844, "dark")]:
            ctx = await b.new_context(viewport={"width": w, "height": h}, color_scheme=scheme,
                                      geolocation={"latitude": 44.8176, "longitude": 20.4569}, permissions=["geolocation"])
            pg = await ctx.new_page()
            pg.on("pageerror", lambda e: errs.append(str(e)))
            pg.on("response", lambda r: bad.append(f"{r.status} {r.url[:90]}") if r.status >= 400 else None)
            await pg.goto(URL, wait_until="networkidle"); await pg.wait_for_timeout(1500)
            for i, t in enumerate(["danas", "prognoza", "vazduh", "radar", "dugorocno"], 1):
                await pg.evaluate(f"location.hash='{t}'"); await pg.wait_for_timeout(3500 if t == "radar" else 1800)
                await pg.screenshot(path=f"{OUT}/{name}-{i}-{t}.png", full_page=(t != "radar"))
            await ctx.close()
        await b.close()
    with open(f"{OUT}/stats.txt", "w") as f:
        f.write(f"page errors: {errs}\nbad responses: {bad}\n")

asyncio.run(main())
