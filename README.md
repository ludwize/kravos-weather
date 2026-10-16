# Kravos Vreme

Lična Android aplikacija za vremensku prognozu (sideload), po uzoru na Weather Channel sa svim „premium“ funkcijama, bez reklama i pretplate.

## Šta ima

- **Danas**: trenutno vreme, osećaj, poređenje sa jučerašnjim danom, upozorenja (Meteoalarm/RHMZ), padavine narednih 3 h u koracima od 15 min, 48 h po satu, 10 dana, vlažnost/vetar/pritisak (trend)/UV/vidljivost, Sunce i Mesec, indeksi aktivnosti (trčanje, šetnja, bicikl, prskanje bašte, roštilj, veš).
- **Prognoza**: 7 dana po satu sa grafikonima (temp/osećaj, padavine, vetar, vlažnost, pritisak, UV), 16 dana sa opisom delova dana, **dugoročno**: 46 dana (ECMWF ansambl sa rasponom nesigurnosti), nedeljna odstupanja od normale, sezonska prognoza po mesecima (SEAS5).
- **Vazduh**: EAQI, PM2.5/PM10/NO₂/O₃/SO₂, prognoza 5 dana, polen (jova, breza, maslina, trave, pelin, ambrozija) sa prognozom 4 dana, UV.
- **Radar**: RainViewer radar (poslednja 2 h, animacija) + mape prognoze za 48 h: padavine, temperatura, vetar (strelice), oblačnost.
- **Lokacije**: GPS + pretraga i čuvanje mesta.
- **Widget** (3 veličine) i **obaveštenja**: upozorenja, kiša uskoro, grmljavina/jak vetar, jutarnji pregled, mraz, loš vazduh, visok polen.
- Tamna/svetla tema, Material You boje, jedinice °C/°F, km/h · m/s · Bofor.

## Izvori podataka (besplatni, bez API ključa)

Open-Meteo (forecast, air-quality, seasonal, geocoding), Copernicus CAMS, EUMETNET Meteoalarm, RainViewer, OpenStreetMap/CARTO.

## Build

GitHub Actions builduje potpisan APK na svaki push na `main` i kači ga na Releases.
Potpisivanje: `signing/release.jks.enc` (sopstveni ključ ove aplikacije), CI treba repo secret `KP_SIGNING_PASS`.
