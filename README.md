# GlobeLog 1.0

aplikacja na Androida  Wymaga Androida 6.0 lub nowszego.

## Miejsca, mapa i sklep

- Przycisk **+** szuka miejscowości. Dotknij **+** przy wyniku, aby dodać miejsce. Przytrzymanie karty pozwala je usunąć, a dotknięcie otwiera prognozę.
- Mapa odsłania granice miejscowości z [OpenStreetMap](https://www.openstreetmap.org/copyright). Gdy dane nie zawierają granicy, pokazuje obszar oznaczony jako przybliżony. Niezdrapana warstwa ma 25% przezroczystości, a odsłonięte miejsca 100%.
- Pięć różnych miejscowości potwierdzonych lokalizacją telefonu daje jeden punkt. Miejsca z plusa są oznaczone plusem i liczą się po potwierdzeniu wizyty. **Topka znajomych** sortuje według zdobytych punktów.
- Akcent kosztuje 3 punkty, kolor zdrapki 5 punktów, pojedyncza ikona 5 punktów, a pakiet pięciu ikon 25 punktów. Oryginalna ikona GetDaTrip, **OG**, jest darmowa. Zakupione warianty wybierzesz w **Ustawienia → Wygląd**.
- Interfejs ma 12 języków. Prognoza i wyszukiwanie korzystają z [Open-Meteo](https://open-meteo.com/). Ustalenie nazwy miejscowości wykorzystuje Android Geocoder i OpenStreetMap.


## Sprawdzanie aktualizacji

Ustaw `globelog.github.repository=OWNER/REPO` w `gradle.properties` przed kompilacją. Przycisk w ustawieniach korzysta z najnowszej publicznej wersji w GitHub Releases. Używaj tagów w formacie `v1.0`, `v1.1` lub `v1.1.2`.

Inspiracja aplikacją GetDaTrip.
