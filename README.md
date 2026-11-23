# GlobeLog 1.0

aplikacja na Androida  Wymaga Androida 6.0 lub nowszego.

## Budowanie w Android Studio

## Miejsca, mapa i sklep

- Przycisk **+** szuka miejscowości. Dotknij **+** przy wyniku, aby dodać miejsce. Przytrzymanie karty pozwala je usunąć, a dotknięcie otwiera prognozę.
- Mapa odsłania granice miejscowości z [OpenStreetMap](https://www.openstreetmap.org/copyright). Gdy dane nie zawierają granicy, pokazuje obszar oznaczony jako przybliżony. Niezdrapana warstwa ma 25% przezroczystości, a odsłonięte miejsca 100%.
- Pięć różnych miejscowości potwierdzonych lokalizacją telefonu daje jeden punkt. Miejsca z plusa są oznaczone plusem i liczą się po potwierdzeniu wizyty. **Topka znajomych** sortuje według zdobytych punktów.
- Akcent kosztuje 3 punkty, kolor zdrapki 5 punktów, pojedyncza ikona 5 punktów, a pakiet pięciu ikon 25 punktów. Oryginalna ikona GetDaTrip, **OG**, jest darmowa. Zakupione warianty wybierzesz w **Ustawienia → Wygląd**.
- Interfejs ma 12 języków. Prognoza i wyszukiwanie korzystają z [Open-Meteo](https://open-meteo.com/). Ustalenie nazwy miejscowości wykorzystuje Android Geocoder, [BigDataCloud](https://www.bigdatacloud.com/) i OpenStreetMap.

## Logowanie Google, znajomi i wiadomości

Udostępnione APK i `app/google-services.json` używają projektu Firebase `globelog-e0c82`. Znajomi i wiadomości wymagają bazy Cloud Firestore z opublikowanymi regułami z `firestore.rules`. Jeśli budujesz aplikację w Android Studio z własnym kluczem do podpisu, dodaj odciski tego klucza w Firebase.

Opublikuj aktualne `firestore.rules` w projekcie Firebase. Reguły pozwalają korzystać z edycji profilu, nowych opcji czatu i udostępniania lokalizacji. Udostępnianie pozycji jest dobrowolne. Przybliżony punkt jest dostępny dla zaakceptowanych znajomych maksymalnie przez 15 minut od udostępnienia. Kody znajomych można też przekazywać przez NFC na zgodnych telefonach.

Aby podłączyć aplikację do innego projektu Firebase:

1. W konsoli Firebase utwórz projekt i zarejestruj aplikację Android z identyfikatorem pakietu `pl.getdatrip`.
2. W ustawieniach tej aplikacji dodaj odcisk SHA-1 i SHA-256 certyfikatu używanego do podpisania APK. Dla własnej kompilacji odciski pokaże `./gradlew signingReport`. Jeżeli chcesz testować udostępniony podpis kompilacji debug, jej SHA-1 to `E1:D9:94:4E:A9:DF:BD:8F:35:30:32:4C:EA:7F:B5:10:C3:FB:A4:9B`, a SHA-256 to `E9:0A:DE:5F:27:D7:C6:40:4A:24:6E:82:35:9C:32:97:19:8A:30:B2:AC:05:1A:22:0A:85:61:C6:79:DC:9C:C8`.
3. W **Authentication → Sign-in method** włącz Google. Utwórz bazę **Cloud Firestore**. W zakładce **Rules** wstaw i opublikuj zawartość dołączonego `firestore.rules`.
4. Pobierz zaktualizowany `google-services.json` dla aplikacji Android i umieść go w `app/google-services.json`. Zsynchronizuj Gradle i ponownie zbuduj APK. Gradle automatycznie wykryje plik oraz identyfikator klienta OAuth.
5. Na urządzeniach z usługami Google Play zaloguj się na dwa różne konta i użyj kodu z zakładki **Profil**, aby wysłać zaproszenie. Po jego zaakceptowaniu znajomi pojawią się w czacie i rankingu.

Profil udostępnia znajomym nazwę, zdjęcie i wynik. Historia odwiedzonych miejsc jest zapisywana na urządzeniu.

## Sprawdzanie aktualizacji

Ustaw `globelog.github.repository=OWNER/REPO` w `gradle.properties` przed kompilacją. Przycisk w ustawieniach korzysta z najnowszej publicznej wersji w GitHub Releases. Używaj tagów w formacie `v1.0`, `v1.1` lub `v1.1.2`.

## Uruchomienie sklepu za pieniądze

Sklep w udostępnionym APK pokazuje planowane pakiety. Aktywacja zakupów wymaga aplikacji `pl.getdatrip` w Google Play Console, jednorazowych produktów oraz opublikowanego weryfikatora `redeemCoins` z katalogu `functions/`.

| Produkt w Play Console | Monety | Cena w Polsce |
| --- | ---: | ---: |
| `globelog_coins_10` | 10 | 1 zł |
| `globelog_coins_50` | 50 | 4 zł |
| `globelog_coins_100` | 100 | 8 zł |
| `globelog_coins_1000` | 1000 | 20 zł |

1. Włącz Google Play Developer API i przyznaj kontu usługi Cloud Functions dostęp do weryfikacji zakupów aplikacji w Play Console.
2. W projekcie Firebase z obsługą Cloud Functions wdroż `redeemCoins` oraz reguły Firestore. Backend weryfikuje zakup w Google Play i zasila portfel tylko raz na potwierdzenie. Saldo monet jest osobne od punktów za wizyty.
3. Ustaw `globelog.play.billing=true` w `gradle.properties` i przygotuj podpisane wydanie dla Google Play. Zaloguj się na konto testera i sprawdź zakupy na ścieżce testów wewnętrznych. Sklep używa cen zwracanych przez Google Play.

## Twórca

Szymon Nawrocki

Inspiracja aplikacją GetDaTrip.
