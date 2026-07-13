# Wnioski matematyczne dla obecnego `SNNConfig`

## Rozmiar i bilans sieci

Konfiguracja ma trzy warstwy po 100 neuronów: w każdej jest 80 RS i 20 FS. Łącznie

\[
N=3(80+20)=300,
\qquad N_E=240,
\qquad N_I=60.
\]

Stosunek pobudzających do hamujących wynosi więc \(E:I=4:1\), czyli 80%:20%.
W każdej warstwie wejście wzrokowe pobudza tylko 80 neuronów RS warstwy pierwszej,
a wyjście motoryczne obserwuje 80 neuronów RS warstwy trzeciej. Sama konfiguracja
nie rozdziela neuronów Layer3 na jawne populacje lewego i prawego motoru; ewentualna
symetria zależy od strategii wyjścia i indeksowania tych 80 komórek, nie od osobnych
parametrów w YAML.

## Liczba synaps i E/I

Dla reguły lokalnej RS→all każda z trzech warstw ma \(80\cdot100=8000\) możliwych
par. Przy \(p=0{,}1\) daje to średnio 800 synaps na warstwę, a około 2400 łącznie.
Kod wyklucza autopołączenie mimo deklaracji `allow_autapses: true` (warunek walidacji
jest odwrócony), więc dokładniej oczekiwanie wynosi \(80\cdot99\cdot0{,}1=792\)
na warstwę, czyli 2376. Losowanie oznacza, że rzeczywista liczba może się różnić.

FS→RS ma stały stopień wyjściowy 15, zatem wnosi dokładnie
\(3\cdot20\cdot15=900\) synaps hamujących o wadze −2. Projekcja Layer1→Layer2
jest pełna: \(80\cdot100=8000\) połączeń, a Layer2→Layer3 ma oczekiwane
\(80\cdot100\cdot0{,}25=2000\). Łącznie oczekiwana liczba krawędzi wynosi około

\[
S\approx2376+900+8000+2000=13\,276.
\]

Nominalnie jest to 12 376 synaps pobudzających i 900 hamujących, a więc około
\(E:S=93{,}2\%\), \(I:S=6{,}8\%\). Jednak normalne rozkłady wag (średnie 6 i 3)
mają niezerowe prawdopodobieństwo wartości ujemnych, więc znak części projekcji
„pobudzających” nie jest formalnie gwarantowany.

## Cisza, nasycenie i stabilność

Stan początkowy \(v=-70\) mV jest o 100 mV niższy od progu 30 mV. Bez wejścia
zewnętrznego sieć powinna pozostawać w dużej mierze cicha: brak `TONIC_NOISE`, a
jedyny sensor działa tylko na Layer1 RS. Z drugiej strony pełne projekcje oraz
średnie dodatnie wagi 6 i 3 mogą łatwo uruchomić lawinę. Dla pojedynczego neuronu
Layer2 średni napływ z Layer1 to około \(80\cdot6=480\), zanim uwzględni się
hamowanie; w Layer3 oczekiwany napływ to około \(20\cdot3=60\) przy aktywnym
Layer2. To sugeruje wąskie okno między ciszą a saturacją, szczególnie przy dużym
prądzie wizualnym 10.

STDP ma \(A_+=0{,}01\), \(A_-=0{,}012\), więc przewaga depresji wynosi 20%, ale
dopamina i długie ograniczenie \(w\le50\) mogą z czasem przesuwać wagi ku granicom.
W praktyce należy mierzyć frakcję aktywnych neuronów i średnią liczbę wyładowań,
bo sama liczba synaps nie rozstrzyga, czy sieć jest cicha, czy nasycona.

## Pięć kolejnych eksperymentów

1. **Zmniejszenie pobudzenia:** `max_current: 10.0 → 5.0`; sprawdzić, czy Layer1
   wychodzi z ciszy bez lawiny.
2. **Ograniczenie projekcji pełnej:** Layer1→Layer2 zmienić z `all_to_all` na
   `probabilistic`, `probability: 0.25`; oczekiwane 2000 synaps zamiast 8000.
3. **Silniejsze hamowanie:** FS→RS `fixed: -2.0 → -4.0`; porównać aktywność i
   odsetek wag osiągających 50.
4. **Zmiana bilansu E/I:** w każdej warstwie `RS: 80 → 60`, `FS: 20 → 40`,
   zachowując 100 neuronów; ocenić, czy spada saturacja.
5. **Test symetrii motorów:** rozdzielić Layer3 RS na dwie równe grupy po 40,
   ustawić identyczne parametry oraz osobne wyjścia; podać lustrzane bodźce i
   zmierzyć \(\Delta=(L-R)/(L+R)\). Dla symetrii oczekuje się \(\Delta\approx0\).
