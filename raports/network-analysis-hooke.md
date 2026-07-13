# Hooke — matematyczna analiza DA-STDP

## Status i zakres

Analiza bazowa, 2026-07-13. Źródłem prawdy jest aktualny kod roboczy, przede
wszystkim `SnnEngine`, `Agent`, `World`, strategie wejścia/wyjścia, bieżący
`SNNConfig.yaml` oraz headless benchmark. Ten plik jest notatnikiem Hooke'a;
nie zawiera propozycji już zaimplementowanych zmian.

Najważniejszy wniosek: obecny mechanizm rzeczywiście implementuje lokalny,
trójczynnikowy DA-STDP, ale dowód uczenia wymaga czegoś więcej niż wzrost liczby
nagród i średniej wagi. Krytyczne są: dwufazowy sygnał `dopamineLevel -
dopamineBaseLevel`, bardzo krótka bramka STDP (5 ms), długa pamięć eligibility
(1000 ms), globalna nagroda oraz brak deterministycznej symetrii lewo-prawo w
konkretnym losowaniu sieci.

## 1. Reguła faktycznie wykonywana przez kod

Niech \(s_i[n]\in\{0,1\}\) oznacza wyładowanie neuronu \(i\) w kroku \(n\),
\(x_i,y_i\) — ślady pre- i postsynaptyczne, a \(c_{ij}\) — eligibility synapsy
\(i\to j\). Dla każdego kroku kod wykonuje, w tej właśnie kolejności:

1. integrację neuronów i propagację impulsów do `spikeI` następnego kroku;
2. aktualizację eligibility na podstawie śladów istniejących przed bieżącymi
   wyładowaniami;
3. dodanie bieżących wyładowań do \(x,y\), a następnie zanik \(x,y\);
4. zmianę wag przez iloczyn eligibility i sygnału dopaminowego;
5. zanik eligibility, aktualizację baseline dopaminy i zanik dopaminy.

W zapisie skróconym część STDP ma postać

\[
c_{ij}\leftarrow c_{ij}
  + A_+s_jx_i-A_-s_iy_j,
\qquad
A_+=0{,}01,\quad A_-=0{,}012,
\]

po czym

\[
x_i\leftarrow(x_i+s_i)e^{-\Delta t/\tau_+},\qquad
y_i\leftarrow(y_i+s_i)e^{-\Delta t/\tau_-},
\quad \tau_+=\tau_-=5\text{ ms}.
\]

Wniosek techniczny: para równoczesna nie tworzy wkładu od swoich bieżących
spike'ów, ponieważ ślady są inkrementowane dopiero po obliczeniu STDP. Dla pary
pre-przed-post o odstępie \(\delta>0\) wkład wynosi w przybliżeniu
\(+A_+e^{-\delta/5\text{ ms}}\); dla post-przed-pre wynosi
\(-A_-e^{-\delta/5\text{ ms}}\). Jest to addytywne, all-to-all pair STDP, a nie
reguła nearest-neighbour.

Dla kroku 1 ms:

| Odstęp pary | pre przed post | post przed pre |
|---:|---:|---:|
| 1 ms | \(+0{,}008187\) | \(-0{,}009825\) |
| 5 ms | \(+0{,}003679\) | \(-0{,}004415\) |

Depresja ma o 20% większą amplitudę niż potencjacja przy tym samym odstępie.
Przesunięcie transmisji synaptycznej o jeden krok dobrze mieści się w oknie 5 ms,
ale korelacje między bardziej odległymi etapami zachowania muszą zostać
przeniesione przez eligibility, nie przez samo STDP.

Zmiana dodatniej lub zerowej wagi to

\[
\Delta w_{ij}[n]=\Delta t\,c_{ij}[n]\bigl(D[n]-B[n]\bigr),
\qquad
w_{ij}\leftarrow\operatorname{clip}(w_{ij}+\Delta w_{ij},0,50),
\]

a potem

\[
c_{ij}\leftarrow c_{ij}e^{-\Delta t/\tau_c},
\qquad \tau_c=1000\text{ ms}.
\]

Synapsy z \(w<0\) nigdy nie zmieniają wagi, chociaż ich eligibility jest nadal
obliczane i wygaszane. Kod rozpoznaje hamowanie wyłącznie po aktualnym znaku wagi,
nie po typie neuronu. Dodatnia waga dociśnięta do zera pozostaje plastyczna;
ujemna waga wylosowana przypadkiem z rozkładu normalnego pozostaje zamrożona.

## 2. Skale czasu i pamięć credit assignment

| Wielkość | Stała | Funkcja |
|---|---:|---|
| ślad pre/post | 5 ms | wykrycie lokalnej kolejności spike'ów |
| eligibility \(c\) | 1000 ms | most między aktywnością a późną nagrodą |
| dopamina \(D\) | 20 ms | szybki dodatni impuls po nagrodzie |
| baseline \(B\) | 200 ms | wolny filtr, tworzący ujemny ogon \(D-B\) |

Retencja pojedynczego wpisu eligibility po opóźnieniu nagrody \(L\) wynosi
\(e^{-L/1000\text{ ms}}\):

| \(L\) | 50 ms | 100 ms | 500 ms | 1000 ms | 3000 ms |
|---:|---:|---:|---:|---:|---:|
| retencja | 0,951 | 0,905 | 0,607 | 0,368 | 0,050 |

Okno jest więc wystarczająco długie, by objąć setki milisekund sterowania przed
kolizją z jedzeniem, ale szerokie okno przypisuje nagrodę także wielu przypadkowym
korelacjom. To kompromis bias–variance: krótsze \(\tau_c\) poprawia lokalność
czasową, dłuższe zwiększa szansę objęcia faktycznej przyczyny.

Nagroda jest wstrzykiwana dopiero po wykonaniu kroku mózgu, ruchu i sprawdzeniu
kolizji. Eligibility ostatniej akcji jest więc już zapisane, a impuls \(+5\) jest
użyty po raz pierwszy w następnym kroku. To jest właściwy porządek dla
pre-reward credit assignment.

## 3. Dopamina jako filtr dwufazowy

Aktualizacja kodu jest dyskretna:

\[
B\leftarrow B+(D-B)(1-e^{-\Delta t/200}),\qquad
D\leftarrow De^{-\Delta t/20}.
\]

Dla izolowanego impulsu \(D_0=5, B_0=0\), przybliżenie ciągłe sygnału uczącego
\(S(t)=D(t)-B(t)\) ma postać

\[
S(t)=\frac{D_0}{\tau_B-\tau_D}
\left(\tau_Be^{-t/\tau_D}-\tau_De^{-t/\tau_B}\right).
\]

Konsekwencje:

- \(S(t)\) przechodzi przez zero po około 51,2 ms;
- później występuje ujemny ogon, z minimum około \(-0{,}30\) przy 102 ms;
- całka \(\int_0^\infty S(t)\,dt=0\): dla stałego eligibility dodatnia i ujemna
  część dokładnie by się zniosły;
- zanik eligibility łamie to znoszenie na korzyść wcześniejszej dodatniej części.

Dla jednostkowego eligibility obecnego w chwili nagrody efektywny impuls w
przybliżeniu ciągłym wynosi

\[
K_0=\int_0^\infty e^{-t/\tau_c}S(t)dt
=\frac{D_0}{\tau_B-\tau_D}
\left[
\frac{\tau_B}{1/\tau_c+1/\tau_D}
-\frac{\tau_D}{1/\tau_c+1/\tau_B}
\right]
\approx16{,}34.
\]

Dokładna iteracja kodu przy \(\Delta t=1\) ms daje około 16,80: dodatnia część
ważona eligibility to \(+78{,}60\), ujemna \(-61{,}80\). Izolowana przyczynowa
para spike'ów oddalona o 1 ms, której eligibility jest obecne przy nagrodzie,
dałaby zatem około (0{,}008187\cdot16{,}80=+0{,}138) zmiany wagi przed
uwzględnieniem wcześniejszego zaniku i clippingu. Para antyprzyczynowa dałaby
około \(-0{,}165\).

Szczególnie ważny jest efekt dla nowego eligibility utworzonego już po nagrodzie.
Jego przyszły, zintegrowany wpływ zmienia znak po zaledwie około 3,6 ms. Dla kroku
1 ms jednostkowe eligibility utworzone 4 ms po nagrodzie ma łączny mnożnik około
\(-1{,}62\), po 10 ms około \(-22{,}63\), a najbardziej ujemny mnożnik, około
\(-65{,}16\), pojawia się dla opóźnienia około 55 ms. Oznacza to silną
anty-plastyczność post-reward, nie tylko „powrót dopaminy do zera”.

Interpretacja: baseline nie jest kontekstową estymatą oczekiwanej nagrody, lecz
filtrem górnoprzepustowym każdego impulsu. Może użytecznie oddzielać aktywność
przed nagrodą od aktywności po nagrodzie, ale może też depresjonować reset,
zawracanie po zjedzeniu pokarmu i reakcję na świeżo zrespawnowany cel. Ten efekt
jest pierwszym kandydatem do testu mechanistycznego.

Przy \(\Delta t=0{,}5\) i \(0{,}1\) ms mnożnik \(K_0\) wynosi odpowiednio około
16,57 i 16,39, więc sama całka dopaminowa jest dość zbieżna. Nie oznacza to
niezmienniczości całej symulacji: `PopulationDriveStrategy` nie skaluje ruchu ani
obrotu przez `deltaTime`, zatem zmiana kroku zmienia liczbę akcji na jednostkę
czasu symulowanego. Porównania uczenia muszą używać identycznego kroku, a test
zbieżności behawioralnej należy traktować osobno.

## 4. Symetria i lokalność credit assignment

Równania DA-STDP są symetryczne względem permutacji neuronów i globalna dopamina
nie preferuje lewej ani prawej strony. To jednak tylko symetria prawa i rozkładu,
nie konkretnej realizacji sieci.

Wejście wzrokowe ma naturalne odbicie \(k\mapsto79-k\), a wyjście motoryczne dzieli
80 neuronów RS warstwy trzeciej na dwie równe połowy. Dokładna symetria zamkniętej
pętli wymagałaby jednocześnie:

\[
\phi\mapsto-\phi,\qquad k\mapsto79-k,
\qquad L\leftrightarrow R,
\]

oraz lustrzanego odwzorowania wszystkich krawędzi i identycznych wag. Losowe
połączenia lokalne i Layer2→Layer3 nie spełniają tego dla pojedynczego seeda.
Obecna sieć jest co najwyżej symetryczna zespołowo: średni bias może znikać po
wielu seedach, podczas gdy pojedynczy agent ma arbitralny bias, który globalna
nagroda może następnie utrwalić.

Pełna projekcja 80 neuronów wzrokowych na wszystkie 100 neuronów Layer2 nie
zachowuje porządku kątowego. Następna projekcja jest również losowa i
nietopograficzna. Symetryczna reguła uczenia musi więc odtwarzać od zera mapowanie
„znak kąta → akcja”, zamiast tylko dostrajać istniejącą drogę.

Lokalne eligibility jest jedynym selektorem kredytu. Ten sam sygnał \(D-B\)
moduluje jednocześnie wszystkie dodatnie synapsy: sensoryczne, rekurencyjne i
motoryczne. Nie ma osobnego krytyka dla lewego i prawego motoru ani informacji,
który składnik akcji zmniejszył dystans. Kolizja w promieniu 30 może ponadto
nagrodzić akcję pogarszającą położenie, jeśli agent nadal pozostał w promieniu.

Minimalne mierniki symetrii dla bodźców \(+\phi\) i \(-\phi\):

\[
b_0=\frac{L-R}{L+R+\varepsilon}
\]

dla bodźca centralnego oraz

\[
E_{\rm mirror}=
\frac{|r(+\phi)+r(-\phi)|}
{|r(+\phi)|+|r(-\phi)|+\varepsilon}
\]

dla prędkości obrotowej \(r\). Należy też raportować znak i moduł steering gain
\([r(+\phi)-r(-\phi)]/(2\phi)\). Test powinien używać pary dokładnie lustrzanych
topologii i światów; dwa niezależne losowania nie są testem symetrii.

## 5. Co obecny benchmark mierzy — i czego nie dowodzi

| Obecna metryka | Co rzeczywiście pokazuje | Dlaczego nie wystarcza jako dowód uczenia |
|---|---|---|
| `evaluationRewards` | liczbę pokarmów po burn-in | miesza eksplorację, geometrię, aktywność i uczenie |
| `secondHalf-firstHalf` | surowy trend nagród w jednym przebiegu | duża wariancja zliczeń; brak kontroli bez plastyczności |
| `pathLength` | ilość ruchu | szybki ruch losowy może zwiększać wynik bez polityki celu |
| końcowy firing rate | aktywność w ostatnim oknie 1 s | nie pokazuje stabilności ani aktywności warstw/motorów |
| średnia zmiana wagi | średnią po wszystkich synapsach | miesza zamrożone hamujące, LTP z LTD i różne projekcje |

Sama niezerowa zmiana wag dowodzi plastyczności, nie uczenia zadania. Sam wzrost
nagród może wynikać z losowego ruchu. Ich współwystępowanie nadal nie dowodzi
reward-contingent credit assignment, jeśli brakuje kontroli z nagrodą przesuniętą
lub losową.

Średnia waga jest wyjątkowo słaba: około 900 ujemnych synaps jest zamrożonych, a
potencjacja jednej projekcji może anulować depresję innej. Potrzebne są co najmniej
rozkłady \(\Delta w\) per projekcja, mediany \(|\Delta w|\), frakcje wag przy 0 i 50
oraz udział wag zmienionych ponad ustalony próg numeryczny.

## 6. Minimalny standard dowodu uczenia

Dowód powinien mieć cztery niezależne warstwy:

1. **Plastyczność:** wagi zmieniają się bez masowego nasycenia przy 0 lub 50.
2. **Kontyngencja nagrody:** zmiany zależą od zgodności eligibility z czasem
   nagrody, nie tylko od aktywności albo samej dopaminy.
3. **Poprawa zachowania:** nauczona polityka uzyskuje więcej nagród lub krótszy
   czas do celu niż sparowana kontrola.
4. **Retencja:** przewaga pozostaje po zamrożeniu wag i ocenie na niewidzianych
   rozkładach jedzenia.

Preferowany estymator dla seeda \(i\) to sparowana difference-in-differences:

\[
\delta_i=
(R^{\rm post}_{\rm DA}-R^{\rm pre}_{\rm DA})
-(R^{\rm post}_{\rm frozen}-R^{\rm pre}_{\rm frozen}),
\]

gdzie wszystkie oceny są wykonywane z zamrożonymi wagami na tych samych seedach
świata, a `post` używa kopii wag po treningu. Główny wynik to reward rate na stałą
jednostkę czasu; przy rzadkich nagrodach dodatkowo czas do pierwszej nagrody i
krzywa przeżycia. Raportować należy surowe pary seedów, średnią i medianę efektu,
95% przedział dla efektu sparowanego oraz estymację wariancji, nie tylko wartość
\(p\). Dziesięć seedów wystarcza do screeningu; potwierdzenie powinno używać
większej, z góry odseparowanej puli, typowo co najmniej 30 seedów, jeśli wariancja
nie okaże się mała.

Pomocnicze mierniki zachowania:

- nagrody na jednostkę drogi i czas między kolejnymi nagrodami;
- udział kroków zmniejszających dystans do wybranego celu;
- \(d(t)-d(t+\Delta t)\) oraz \(\cos(\text{kąt do celu})\);
- krzywe uczenia w równych oknach czasu, nie tylko dwie połowy;
- odpowiedź skrętna na kontrolowane kąty celu po zamrożeniu wag.

Mierniki mechanizmu:

- \(c_{ij}\) tuż przed nagrodą i korelacja z późniejszym \(\Delta w_{ij}\);
- zgodność zmierzonego \(\Delta w\) z całką
  \(\sum_n c_{ij}[n](D[n]-B[n])\Delta t\);
- osobne rozkłady dla Layer1→Layer2, Layer2→Layer3 i połączeń lokalnych;
- udział nagrody przypisany do eligibility starszego niż 0,1 s, 0,5 s i 1 s;
- asymetria zmian wag prowadzących do lewego i prawego motoru.

## 7. Eksperymenty kontrolne, w kolejności priorytetu

### H0 — testy mechanistyczne małej skali

1. Pojedyncza dodatnia synapsa, kontrolowane pary spike'ów: odtworzyć znak i
   wykładniczą krzywą STDP dla (±1, ±5, ±10) ms oraz brak wkładu pary 0 ms.
2. Ustalone \(c=1\), impuls dopaminy \(+5\): potwierdzić zero sygnału około 51 ms,
   ujemne minimum około 102 ms i całkę około 16,80 dla kroku 1 ms.
3. Tworzyć \(c\) po nagrodzie z opóźnieniem 0–200 ms: odtworzyć zmianę znaku
   około 4 ms i ujemne minimum około 55 ms.
4. Powtórzyć dla \(\Delta t=1,0{,}5,0{,}1\) ms oraz dla wag ujemnej, zerowej i
   bliskiej obu ograniczeniom.

Bez tych testów nie wiadomo, czy wynik behawioralny wykorzystuje zamierzony
mechanizm, czy przypadkową własność kolejności aktualizacji.

### H1 — pełny eksperyment czynnikowy credit assignment

Na identycznych seedach topologii, wag i świata porównać:

- DA-STDP włączone, prawdziwa nagroda;
- wagi zamrożone;
- STDP/eligibility włączone, ale dopamina równa zero;
- dopamina obecna, ale eligibility wyzerowane;
- nagrody yoked lub permutowane w czasie względem trajektorii;
- stałe opóźnienia nagrody: 0, 50, 250, 500, 1000 i 2000 ms.

Dla przednagrodowego eligibility krzywa efektu opóźnienia powinna w pierwszym
przybliżeniu maleć jak \(e^{-L/1000\text{ ms}}\). Najmocniejszym testem
trójczynnikowej reguły jest interakcja „eligibility obecne × nagroda
kontyngentna”, nie efekt główny któregokolwiek czynnika.

### H2 — trening i zamrożona ewaluacja

Trenować na jednej puli seedów świata, zapisywać checkpointy, a następnie klonować
wagi i oceniać je bez dalszej plastyczności na drugiej, niewidzianej puli. Dla
każdego seeda ewaluacyjnego porównać dokładnie te same pozycje pokarmu i stan
początkowy z siecią przed treningiem oraz kontrolą frozen. Zapobiega to sytuacji,
w której nagrody zdobyte podczas treningu są jednocześnie bodźcem uczącym i
metryką końcową.

### H3 — symetria lustrzana

Zbudować dokładnie lustrzaną parę topologii/wag, podawać cele pod (±\phi), a
świat również odbijać. Porównać tor, liczbę nagród, \(b_0\), \(E_{\rm mirror}\) i
zmiany wag. Następnie wykonać test zespołowy po seedach i sprawdzić, czy rozkład
biasu jest skupiony wokół zera, a nie tylko czy średnia przypadkiem wynosi zero.

### H4 — ablacje stałych czasowych

Po ustaleniu poprawnej kontroli porównać \(\tau_c\) rzędu 100, 300, 1000 i
3000 ms oraz wariant bez baseline \(B\) albo z baseline pełniącym rolę prawdziwej
estymaty oczekiwanej nagrody. Celem nie jest maksymalizacja samej liczby nagród,
lecz znalezienie zakresu, w którym efekt znika zgodnie z przewidywaniem czasowym
i nie wynika z nasycenia wag.

## 8. Ryzyka i konfuzje bieżącej konfiguracji

- Realizacja ma oczekiwane około 13 276 synaps, z czego tylko około 900 to jawnie
  hamujące FS→RS. Silne projekcje feed-forward mogą wąsko oddzielać ciszę od
  lawiny; firing rate trzeba raportować per warstwa i populacja.
- Wagi z rozkładów \(N(6,2)\) i \(N(3,1)\) mają po około 0,135% szansy na znak
  ujemny. Oczekiwane około 13–14 takich krawędzi jest przez mechanizm znaku
  przypadkowo zamrożone jako „hamujące”.
- Bieżący YAML nie używa jeszcze `TONIC_NOISE` ani `HUNGER_DRIVE`. Eksploracja
  zależy od aktywności wywołanej widzeniem i od losowej asymetrii topologii.
- `GaussianVisionStrategy` sumuje wkład wszystkich pokarmów bez normalizacji ani
  limitu całkowitego prądu; `max_current` jest maksimum na jeden pokarm, nie na
  neuron.
- Przy 100 losowych pokarmach prawdopodobieństwo co najmniej jednego pokarmu już
  w promieniu zjedzenia 30 od pozycji startowej wynosi w przybliżeniu 30%.
  Burn-in usuwa zdarzenie z głównego zliczenia, lecz nie cofa wstrzykniętej
  dopaminy ani jej wpływu na późniejszą politykę.
- Ruch i obrót nie są mnożone przez `deltaTime`; sweep kroku symulacji miesza
  numerykę DA-STDP ze zmianą szybkości zachowania.
- `World` wybiera najbliższy pokarm przed ruchem i po ruchu sprawdza kolizję tylko
  z nim. To może zaniżać niektóre kolizje i wiązać nagrodę z geometrią kroku.
- Ograniczenia 0 i 50 tworzą nieliniowość; średnia zmiana wagi może wyglądać
  stabilnie mimo polaryzacji rozkładu do obu granic.

## 9. Cross-review

Raport Volty (`network-analysis-volta.md`) trafnie wskazuje oczekiwaną liczbę
synaps, przewagę pobudzenia, ryzyko ciszy/lawiny oraz potrzebę rozdzielenia
motorów. Potwierdziłem też nietypową semantykę autaps w loaderze: warunek
`allowAutapses && src == tgt` odrzuca autapsę, więc wartość `true` faktycznie ją
blokuje, a `false` jej nie blokuje. To jest ryzyko topologii, ale nie zmienia
wyprowadzonych wyżej równań DA-STDP.

Cross-review Poincarégo (`network-analysis-poincare.md`) potwierdza utratę
topografii kąta, potrzebę statystyk per projekcja, około 30% początkowego tła
kolizji oraz protokół screeningu i potwierdzenia na rozłącznych seedach. Przyjmuję
zalecenie, aby przed szerokim sweepem parametrów sprawdzić kontrolowane mapowanie
sensoryczno-motoryczne. Koryguję natomiast podany tam zysk legacy motoru: dla
bieżącego YAML

\[
v=0{,}05(L+R),\qquad
\Delta\theta=0{,}01(L-R),
\]

ponieważ zarówno `speed_per_spike`, jak i `turn_factor` wynoszą 0,1. Maksymalny
obrót kroku to 0,4 rad, nie 4 rad. Nie zmienia to problemu braku skalowania przez
`deltaTime`.

Cross-review Meitner (`network-analysis-meitner.md`) doprecyzowuje próg dynamiki:
dla RS przy kroku 1 ms lokalna granica stabilności jest blisko \(I=3{,}7984\), a
geometria baseline daje średnio około 2,52 prądu vision na kanał i maksimum
przekraczające próg w niemal każdym świecie. Przyjmuję więc korektę, że brak
`TONIC_NOISE` nie oznacza typowo cichego startu. Jej seria YAML skanuje wagę
pełnego Layer1→Layer2 (0,6; 1,2; 2,4); seria Hooke'a jest ortogonalna, bo utrzymuje
stały fan-in i testuje informację routingu oraz jawne akcje.

Wciąż nie są obecne osobne pliki Eulera ani Gaussa. Kanał `send_input` nie jest
udostępniony w tej sesji, więc bezpośrednia wymiana po podanych ID nie była
możliwa. Cross-review Meitner i Poincarégo wykonano przez ich raporty oraz oba
katalogi szablonów.

Szablony Poincarégo współdzielą jawne trzy akcje, ale badają inną oś: trzywarstwową
mapę one-to-one, potem osobno Hunger i boczne kolaterale do Forward. Hooke używa
bezpośredniego mostu o stałym fan-in i porównuje losowy, zgodny oraz antyzgodny
routing. Z tego powodu Hunger w serii Hooke'a jest stałym nuisance factor, nie
estymowanym efektem; jego wpływ należy brać z porównania Poincaré 002–001.
Nie należy mieszać obu serii w jednym porównaniu jednoczynnikowym.

W `templates-poincare` warto skontrolować `AssociationBias: 3.8`: granica lokalnej
stabilności RS wyliczona przez Meitner to około 3,7984, a bezpośrednia iteracja
daje już firing około 5,4 Hz przy prądzie 3,8. Założenie „tuż poniżej reobazy, bez
stałego firingu” jest więc numerycznie zbyt optymistyczne. Poincaré obniżył
`ActionBias` do 3,5, więc ryzyko dotyczy obecnie autonomicznej aktywności warstwy
asocjacyjnej, nie bezpośrednio tonicznych akcji.

Najnowsza seria Meitner 007–009 zmienia wyłącznie lokalne FS→RS z -2 na -3/-4/-6.
Żaden punkt nie poprawił nagród względem baseline; 008 silnie zmniejszył dryf wag
przy podobnej drodze, lecz kosztem około jednej nagrody, a 009 był over-inhibited.
Wniosek przyjęty do dalszych hipotez Hooke'a: nie stroić teraz lokalnego E/I na
podstawie globalnego końcowego firingu. Najpierw potrzebne są rates per populacja,
synchronia i test impulsowy. Poincaré 004/005 badają natomiast dawkę toru łukowego
i opóźniony Hunger, więc nie dublują osi routingu Hooke'a.

### Cross-review głównego long screen Poincaré

Otrzymany blok: 180 s, burn-in 30 s, 16 sparowanych seedów od 104729:

| Wariant | rewards mean ± SD | trend | stable firing | droga | różnica rewards do baseline |
|---|---:|---:|---:|---:|---:|
| baseline | 0,75 ± 1,00 | -0,375 | 0,44 | 69,09 | — |
| Poincaré 001 | 22,6875 ± 4,701 | +2,9375 | 1,00 | 1739,54 | +21,9375 mean, +21 median, 16/16 dodatnie |
| Poincaré 002 | 22,875 ± 4,924 | +0,75 | 1,00 | 1959,38 | +22,125 mean, +22 median, 16/16 dodatnie |

To jednoznaczny dowód, że rzadka topografia z jawnymi akcjami jest behawioralnie
lepszym reżimem niż bieżący baseline i usuwa jego niestabilność końcowego firingu.
Nie izoluje jednak DA-STDP: jednocześnie zmienia topologię, fan-in, wagi, biasy,
dekoder i politykę wrodzoną.

Wewnętrzne 002−001 wynosi tylko \(+0{,}1875\) nagrody, mediana \(+1\), bilans
10/6. Hunger zwiększa drogę o \(219{,}84\), czyli 12,6%, przy wzroście nagród
zaledwie o 0,8%. Efektywność wynosi około 13,04 nagrody/1000 drogi dla 001 i 11,67
dla 002. Nie ma więc wyraźnej korzyści Hunger w nagrodach; jego głównym skutkiem
jest większa eksploracja. Dodatkowo trend 001 jest wyższy niż 002, czego nie wolno
interpretować jako uczenia bez kontroli plastyczności.

Następny eksperyment ma utrzymać każdy YAML bez zmian i dodać czynnik
`learningEnabled ∈ {false,true}` w runnerze. Dla konfiguracji \(c\) i seeda \(i\):

\[
L_{c,i}=R_{c,\mathrm{on},i}-R_{c,\mathrm{off},i},
\]

a wpływ Hunger na uczenie, nie tylko ruch, to interakcja

\[
H_i=L_{002,i}-L_{001,i}.
\]

`false` powinno blokować wyłącznie zmianę wag; topologia, wagi początkowe,
dopamina, szum, Hunger i świat muszą pozostać identyczne. Raportować należy surowe
pary rewards, trend, drogę, rewards/1000 drogi, firing oraz kontrolę
\(\Delta w=0\) w trybie off. Screening może użyć dotychczasowych seedów dla
ciągłości mechanistycznej, ale potwierdzenie powinno użyć rozłącznego
`baseSeed=1000003`. Najmocniejszy późniejszy dowód nadal wymaga treningu on i
zamrożonej ewaluacji pre/post na held-out światach.

### Wynik kontroli true/frozen

Kontrolę wykonano na dokładnych archiwalnych YAML-ach, 180 s × 16 seedów 104729+,
bez zmiany topologii ani parametrów. Audyt kodu potwierdza właściwą semantykę:
`learningEnabled=false` pomija tworzenie STDP, aktualizację śladów pre/post,
eligibility i wag, ale nadal aktualizuje \(D\) i \(B\). Zmiana trybu zeruje
wszystkie transient learning traces. Zaobserwowane \(\Delta w=0\) w 16/16 frozen
runów domyka kontrolę techniczną.

| Konfiguracja | frozen mean | true mean | paired mean | mediana | dodatnie pary | bootstrap 95% CI | exact sign-flip | \(d_z\) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Poincaré 001 | 18,625 | 22,6875 | +4,0625 (+21,8%) | +4,5 | 14/16 | [+0,0625; +7,3125] | one-sided 0,02779; two-sided 0,05557 | 0,53 |
| Poincaré 002 | 21,1875* | 22,875 | +1,6875 (+8,0%) | +1 | 9/16 | [-0,4375; +4,0] | one-sided 0,0947 | — |

\(*\) Wartość frozen 002 wynika arytmetycznie z true 22,875 i podanej różnicy
1,6875.

Poincaré 001 daje pierwszy umiarkowany dowód, że DA-STDP poprawia zachowanie ponad
gotową politykę frozen. Kierunek był wcześniej określony, więc jednostronny test
ma interpretację, ale dwustronne \(p=0{,}05557\) pozostaje graniczne. Dolna granica
bootstrap jest dodatnia tylko o 0,0625 nagrody, a \(d_z=0{,}53\) oznacza efekt
umiarkowany, nie rozstrzygający. Ponadto seedy 104729+ uczestniczyły w wyborze
topologii. Trwające niezależne P001 true/frozen, 180 s × 30,
`baseSeed=1000003`, jest więc właściwym testem potwierdzającym i nie powinno być
blokowane ani poprzedzane nowym tuningiem.

Poincaré 002 nie potwierdza dodatkowego efektu uczenia: CI obejmuje zero,
jednostronne \(p=0{,}0947\), a dodatnich jest tylko 9/16 par. Rozkład średnich
ujawnia ważną dekompozycję:

- Hunger podnosi politykę frozen 002−001 o \(21{,}1875-18{,}625=+2{,}5625\);
- przyrost uczeniowy spada z 4,0625 do 1,6875, czyli punktowa interakcja
  \(H=-2{,}375\);
- po włączeniu uczenia całkowita różnica 002−001 kurczy się do +0,1875.

Nie można przypisać istotności interakcji bez surowych, wspólnie sparowanych
\(H_i\). Mechanistyczna hipoteza jest jednak jasna: Hunger zastępuje część korzyści
uczenia przez bezpośrednią eksplorację, zmniejsza headroom i/lub rozmywa
kontyngencję eligibility–reward. Następne analizy powinny sprawdzić różnice drogi,
reward efficiency, liczby zdarzeń przed burn-in i rozkładu czasów nagród pomiędzy
true/frozen. Dopiero potem ma sens Poincaré 005 z opóźnionym Hunger.

Planowany słabszy prior Poincaré 006 może zbadać zależność „headroom kontra
wydajność frozen”, lecz nie zastępuje niezależnego potwierdzenia 001. Dla takiego
sweepu trzeba raportować jednocześnie bezwzględny wynik true, wynik frozen i ich
różnicę; sam większy procent improvement przy słabszej polityce początkowej byłby
mylący.

### Niezależne potwierdzenie Poincaré 001

Test 180 s × 30 na niewidzianych seedach 1000003+ potwierdził mniejszy, lecz nadal
dodatni efekt:

| Metryka | frozen | learning | paired learning−frozen |
|---|---:|---:|---:|
| rewards | 19,4 | 21,4667 | +2,0667 (+10,65%), mediana +1 |
| reward trend | +0,733 | +2,4 | +1,667 |
| path | 1690,8 | 1744,5 | +53,7 (+3,18%) |
| rewards/1000 path | 11,47 | 12,31 | +7,25% |
| zmiana wag | 0 w 30/30 | >0 w 30/30 | kontrola techniczna spełniona |

Rozkład rewards: 17 zwycięstw learning, 3 remisy, 10 porażek; bootstrap 95% CI
średniej \([+0{,}2,+3{,}933]\), \(d_z=0{,}388\), paired \(t\) two-sided
\(p=0{,}04235\), Wilcoxon two-sided \(p=0{,}0632\) i one-sided \(p=0{,}0316\),
sign test \(p=0{,}248\). Trend difference ma bootstrap CI
\([-0{,}667,+4]\), więc nie jest osobno potwierdzony.

To umiarkowany, replikowany efekt średni DA-STDP, nie efekt powszechny po seedach.
Zgodność bootstrap CI i paired \(t\) wspiera dodatnią średnią, podczas gdy słaby
sign test i graniczny Wilcoxon pokazują heterogeniczność: korzyść zależy bardziej
od wielkości poprawy w części seedów niż od samej przewagi liczby zwycięstw.
Wzrost nagród o 10,65% przy wzroście drogi tylko o 3,18% oraz poprawa efektywności
o 7,25% przemawiają przeciw wyjaśnieniu „learning tylko zwiększa ruch”.

Efekt zmalał z archiwalnych +4,0625/\(d_z=0{,}53\) do +2,0667/\(d_z=0{,}388\),
co jest zgodne z regresją po selekcji i podkreśla wagę holdoutu. Nie należy łączyć
obu pul w jeden „większy” test po fakcie; niezależny blok sam spełnia kryterium
dodatniej średniej, ale nadal mierzy politykę podczas ciągłej plastyczności.

## 10. Pierwsza seria konfiguracji Hooke'a

Seria `templates-hooke/001–003` izoluje strukturalny credit assignment. Wszystkie
warianty mają 200 neuronów (160 RS, 40 FS), oczekiwane 584 lokalne synapsy
pobudzające, dokładnie 200 hamujących oraz dokładnie 640 dodatnich synaps mostu.
Każdy motorowy RS ma osiem wejść o wadze 2.0. Różni się wyłącznie routing:

| Plik | Routing | Rola |
|---|---|---|
| `001-jawne-akcje-losowy-most.yaml` | Sensory→Motor losowo | kontrola bez znaku kąta |
| `002-jawne-akcje-zgodny-most.yaml` | left→right turn, center→forward, right→left turn | kandydat z symetrycznym priorem |
| `003-jawne-akcje-antyzgodny-most.yaml` | odwrócone drogi skrętu | kontrola znaku |

Vision przy `max_current=8` daje około \(0{,}8\cdot2{,}52=2{,}02\) średniego
prądu geometrycznego na kanał baseline; z tonicznym 1,5 daje około 3,52, czyli
celowo blisko progu RS Meitner. Motorowe tonic 2,0 pozostaje średnio podprogowe.
Dla stałego \(I=2\) odległość od dolnego punktu stałego do separatora wynosi około
14,14 mV, a osiem synchronicznych wejść po 2,0 daje 16 mV. Most powinien zatem
przenosić skorelowane paczki, ale nie pojedyncze spike'i. Hunger podnosi tylko
Forward do maksymalnego łącznego prądu średniego 5,0 po 25 s bez pokarmu.

Kolejność outputów to TurnLeft, TurnRight, Forward: skręty aktualizują kąt, a
ostatni kanał wykonuje translację po wypadkowym obrocie. Jest to świadoma własność
obecnej sekwencyjnej semantyki `OutputSystem`.

Loader dla seeda 104729 potwierdził w każdym pliku: 200 neuronów, 1381 faktycznie
wylosowanych krawędzi (541 lokalnych E + 200 I + 640 mostu), cztery wejścia i trzy
wyjścia. Konstrukcja `Agent` potwierdziła rejestrację wszystkich strategii.

Smoke run 30 s, pojedynczy seed sieci/świata, dał odpowiednio:

| YAML | nagrody | droga | końcowy firing | średnia waga |
|---|---:|---:|---:|---:|
| 001 | 1 | 65,4 | 3,975 Hz | 0,8621 |
| 002 | 1 | 79,9 | 4,415 Hz | 0,8630 |
| 003 | 2 | 54,1 | 2,795 Hz | 0,8679 |

To wyłącznie test wykonania i braku natychmiastowej ciszy/lawiny. Lepszy surowy
wynik 003 w jednym seedzie jest wręcz demonstracją, dlaczego nie wolno wybierać
konfiguracji bez sparowanej serii co najmniej 16 runów i testu znaku sterowania.

Następnie wykonano wstępny screening: 8 sparowanych seedów, 60 s symulacji,
burn-in 10 s, krok 1 ms, `baseSeed=104729`. Wyniki:

| YAML | nagrody mean ± SD | trend mean | droga mean | firing mean | \(\Delta\bar w\) mean | nagrody / 1000 drogi |
|---|---:|---:|---:|---:|---:|---:|
| 001 | 2,375 ± 0,518 | -0,125 | 287,48 | 1,571 Hz | 0,00605 | 8,26 |
| 002 | 2,625 ± 0,744 | +0,125 | 285,35 | 1,225 Hz | 0,00785 | 9,20 |
| 003 | 2,125 ± 0,641 | -0,125 | 365,88 | 2,203 Hz | 0,00895 | 5,81 |

Sparowane różnice nagród:

- 002−001: \([0,0,-1,0,0,0,1,2]\), średnia \(+0{,}25\), mediana 0;
- 002−003: \([0,0,-1,0,1,1,1,2]\), średnia \(+0{,}50\), mediana \(+0{,}5\).

Przy \(n=8\) oba przybliżone 95% przedziały średniej obejmują zero. Seria nie
wykazuje saturacji; jeden run 002 miał firing 0 w końcowym oknie 1 s, lecz
niezerową drogę. Najciekawszy sygnał to 003: przebywa średnio o 28% większą drogę
niż 002, a mimo to zdobywa o 19% mniej nagród i ma o 37% gorszą efektywność
drogi. Jest to zgodne z rolą znaku routingu, choć jeszcze nie stanowi dowodu.

Ta decyzja była warunkowa. Następny test pojedynczego celu wykazał, że 002 nie
spełnia kryterium niezawodnego sterowania, dlatego 001–003 pozostają słabą serią
referencyjną i nie przechodzą bezpośrednio do kosztownego bloku 16 × 180 s.

### Iteracja progu propagacji: seria 004–006

Kontrolowany assay używał jednego pokarmu, braku możliwości kolizji podczas testu,
kątów \(\pm45^\circ\), identycznego szumu i topologii oraz okna przed aktywacją
Hunger. Dla celu w odległości 250 wszystkie warianty \(w=2\) pozostawały
nieruchome przez 500 ms. Dla celu w odległości 100 i okna 1 s:

- losowy 001 prawie nie reagował;
- zgodny 002 miał poprawny znak tylko w 4/16 prób;
- antyzgodny 003 reagował rzadko, ale średni znak był zgodnie przeciwny.

Przyczyna jest zgodna z dynamiką Meitner. Dla motorowego tonic \(I=2\) dwa punkty
stałe dzieli około \(10\sqrt2=14{,}14\) mV. Most \(w=2\) przy kilku aktywnych
kanałach zbyt rzadko przekracza tę lukę. In-memory sweep wyłącznie wagi mostu dał:

| Waga mostu | poprawny znak / 16 | aktywna odpowiedź / 16 | mean signed turn |
|---:|---:|---:|---:|
| 2 | 4 | 4 | 0,0063 rad |
| 3 | 16 | 16 | 0,0988 rad |
| 4 | 16 | 16 | 0,2775 rad |
| 5 | 16 | 16 | 0,3688 rad |
| 6 | 16 | 16 | 0,3875 rad |
| 8 | 16 | 16 | 0,3963 rad |

Wybrano minimalną skuteczną wartość 3; większe wagi zwiększały amplitudę, ale nie
trafność znaku, więc tylko podnosiły ryzyko saturacji. Powstała czysta seria:

| Plik | Routing | Jedyna zmiana względem odpowiednika \(w=2\) |
|---|---|---|
| `004-jawne-akcje-losowy-most-w3.yaml` | losowy | bridge 2→3 |
| `005-jawne-akcje-zgodny-most-w3.yaml` | zgodny | bridge 2→3 |
| `006-jawne-akcje-antyzgodny-most-w3.yaml` | antyzgodny | bridge 2→3 |

Rzeczywiste YAML-e ponownie przeszły loader i konstrukcję `Agent`: po 200
neuronów, 1381 krawędzi dla seeda kontrolnego, cztery wejścia i trzy wyjścia.
Assay 16 seedów × dwie strony dał:

| Odległość | 004 | 005 | 006 |
|---:|---|---|---|
| 100 | 7 ku / 4 od / 21 bez reakcji | 32 ku / 0 od | 0 ku / 31 od / 1 bez |
| 250 | 1 ku / 31 bez reakcji | 9 ku / 23 bez | 16 od / 16 bez |

Przy 250 pojedynczy cel daje \(8(1-250/500)^2=2\) jednostki prądu, a wraz z
tonicznym 1,5 nadal pozostaje średnio pod progiem 3,8. Nie zwiększono vision po
tym teście, ponieważ przy 100 pokarmach podniosłoby to również wieloobiektowe tło.

### Screening i replikacja serii 004–006

Pierwszy blok: 8 sparowanych seedów od 104729, 60 s, burn-in 10 s:

| YAML | nagrody | trend | droga | firing | \(\Delta\bar w\) | nagrody/1000 drogi |
|---|---:|---:|---:|---:|---:|---:|
| 004 | 2,375 | +0,125 | 335,59 | 1,628 Hz | 0,00577 | 7,08 |
| 005 | 3,375 | -0,125 | 201,50 | 2,228 Hz | 0,00952 | 16,75 |
| 006 | 2,375 | -0,375 | 351,15 | 2,027 Hz | 0,00417 | 6,76 |

Ponieważ waga 3 została wybrana z użyciem nakładającej się puli topologii,
wykonano bez dalszego strojenia replikację na ośmiu nietkniętych seedach od
1000003:

| YAML | nagrody | trend | droga | firing | \(\Delta\bar w\) | nagrody/1000 drogi |
|---|---:|---:|---:|---:|---:|---:|
| 004 | 2,125 | -0,125 | 419,27 | 2,376 Hz | 0,00745 | 5,07 |
| 005 | 3,250 | 0,000 | 255,28 | 2,636 Hz | 0,01141 | 12,73 |
| 006 | 2,250 | -0,250 | 397,44 | 1,954 Hz | 0,00477 | 5,66 |

Po połączeniu dwóch z góry rozdzielonych bloków \(n=16\):

- 005−004: średnio \(+1{,}0625\) nagrody, mediana \(+1\), 12 wygranych,
  4 remisy, 0 porażek; orientacyjny sparowany 95% CI
  \([0{,}65,\ 1{,}47]\);
- 005−006: średnio \(+1{,}0\), mediana \(+1\), bilans 11/4/1; orientacyjny
  95% CI \([0{,}42,\ 1{,}58]\);
- połączona efektywność nagród na 1000 drogi: 004 około 5,96, 005 około 14,50,
  006 około 6,18.

005 jest zatem najlepszą architekturą startową tej iteracji: poprawa replikuje się
i nie wynika tylko z większej drogi ani globalnego firingu. Nie jest to jeszcze
dowód uczenia DA-STDP. Połączony mean `rewardTrend` 005 wynosi około \(-0{,}06\),
a polityka ma jawny poprawny prior przed treningiem. Niezerowe \(\Delta\bar w\)
dowodzi jedynie plastyczności. Następny rozstrzygający etap powinien porównać
pre/post przy zamrożonych wagach i kontrolę no-plasticity/yoked reward; jeżeli
harness nadal tego nie umożliwia, dopiero wtedy wykonać 16 × 180 s bez dalszego
strojenia parametrów.

Po niezależnym potwierdzeniu priorytetem jest retencja P001 w protokole
train→freeze→evaluate. Nie należy zastępować go tuningiem Poincaré 004–006 ani
gainu Hooke'a. P002 pozostaje użyteczną kontrolą hipotezy, że bezpośredni napęd
Hunger może zastępować część uczenia.

## 11. Protokół train→freeze→evaluate

Cross-review Meitner prowadzi do tego samego projektu i parametrów. Należy użyć
byte-for-byte archiwalnego `templates-poincare/001-topograficzne-akcje.yaml`;
`learningEnabled` i `freezeLearningAtMs` są parametrami wykonania, nie YAML.
Nie tworzyć kopii P001 w katalogu Hooke'a.

### 11.1. Wykonalny protokół trzyramienny

| Ramię | 0–180 s | 180–300 s | Rola |
|---|---|---|---|
| FF: always-frozen | off | off | poziom polityki początkowej |
| TF: learn-then-freeze | on | off | retencja wag wyuczonych do 180 s |
| TT: continuous-learning | on | on | wartość dalszej adaptacji online |

Parametry:

- `durationMs=300000`;
- `freezeLearningAtMs=180000` tylko w TF;
- `postFreezeEvaluationStartMs=180000` i 120 s ewaluacji;
- `stepMs=1`, `repeats=30`, nowa pula, proponowany `baseSeed=2000003`;
- dokładnie ten sam hash YAML oraz te same seedy topologii, wag i świata w trzech
  ramionach;
- każde ramię to osobne żądanie 9 mln kroków, poniżej limitu 10 mln.

180 s treningu zachowuje potwierdzony horyzont, a 120 s oceny powinno dać około
15–17 nagród/run przy obserwowanym reward rate. Krótsze 60 s zmniejsza koszt, ale
obniża moc dla efektu \(d_z\approx0{,}39\); nie jest zalecane jako test końcowy.

### 11.2. Semantyka granicy faz

Fazy powinny być półotwarte: trening \([0,180000)\), ewaluacja
\([180000,300000]\). Freeze musi nastąpić przed pierwszym krokiem zaczynającym się
w \(t=180000\). Zdarzenie dokładnie na granicy należy przypisać jawnie do jednej
fazy i stosować tę regułę we wszystkich ramionach. `setLearningEnabled(false)`
zeruje ślady i eligibility, a dopamina nadal zanika; nie ma więc resztkowego
credit assignment po freeze.

TF i TT muszą mieć identyczne do ostatniego bitu trajektorie do chwili freeze:
czasy nagród, pozycję, kąt, wagi oraz najlepiej hash stanu. Jakakolwiek różnica
przed 180 s oznacza, że runner nie izoluje przełączenia fazy.

### 11.3. Metryki i kontrasty

Endpoint powinien zwracać osobno:

1. rewards, trend, path i firing dla treningu 30–180 s;
2. `postFreezeRewards`, dwie połowy 180–240 i 240–300 s oraz post-freeze path;
3. wagi initial/at-freeze/final i `postFreezeWeightDelta`;
4. rewards/1000 drogi, kwantyle wag per projekcja oraz frakcje 0/50;
5. surowe rekordy per seed, nie tylko summary.

Główne kontrasty:

\[
\Delta_F=R^{post}_{TF}-R^{post}_{FF}
\]

— retencja wyuczonej polityki,

\[
\Delta_O=R^{post}_{TT}-R^{post}_{TF}
\]

— wartość lub koszt dalszego online learning, oraz

\[
\rho=\frac{\Delta_F}{R^{post}_{TT}-R^{post}_{FF}}
\]

— udział efektu online zachowany po freeze, raportowany tylko gdy mianownik jest
dodatni i stabilny. Dodatkowo różnica trendów post-freeze sprawdza, czy korzyść
TF zanika między dwiema połowami mimo stałych wag.

Pierwszorzędowy estymand to średnia sparowana \(\Delta_F\). Raportować bootstrap
CI, \(d_z\), paired \(t\)/randomization oraz Wilcoxon i sign test jako diagnostykę
heterogeniczności. Po wyniku 17/3/10 nie należy wymagać istotności sign testu jako
osobnej twardej bramki: testuje on inną własność niż średnia i ma małą moc dla
efektów zależnych od wielkości. Mediana, liczba znaków i wszystkie procedury nadal
muszą być pokazane bez wybierania najkorzystniejszej.

Warunki techniczne: `postFreezeWeightDelta=0` w TF 30/30, całkowite
\(\Delta w=0\) w FF 30/30 i identyczne weights-at-freeze w TF/TT. Warunek
behawioralny: dodatnia średnia i mediana \(\Delta_F\), CI średniej powyżej zera,
zgodna poprawa rewards/1000 drogi oraz brak całkowitego zaniku efektu w drugiej
połowie.

### 11.4. Ograniczenie kontynuowanego świata

TF i FF znajdują się przy freeze w innych pozycjach, orientacjach i stanach świata,
bo wcześniejsze uczenie zmieniło trajektorię i respawny. \(\Delta_F\) mierzy więc
retencję całej wyuczonej zamkniętej pętli, ale nie izoluje wyłącznie zapisanych
wag. To właściwy etap pośredni i nie powinien blokować implementacji.

Docelowy dowód to:

1. trenować P001 180 s i zapisać wagi;
2. utworzyć świeży świat i standardowy stan agenta;
3. ocenić 120 s frozen z wyuczonymi wagami;
4. na tym samym świecie ocenić frozen z wagami początkowymi;
5. użyć np. trzech światów ewaluacyjnych na każdą z 30 topologii, uśredniając
   najpierw wewnątrz topologii, aby nie tworzyć pseudoreplikacji.

To rozdzieli pamięć synaptyczną od korzystnego stanu świata. Późniejsza kontrola
permutująca \(\Delta w\) wewnątrz projekcji przy zachowaniu rozkładu wag może
dodatkowo sprawdzić, czy korzyść wynika ze specyficznego credit assignment, a nie
tylko globalnego wzrostu gainu.

### 11.5. Kolejność kolejnych testów i template 007

1. P001 FF/TF/TT, 300 s × 30, nowa pula — bez zmian YAML.
2. P001 checkpoint→fresh-world frozen evaluation.
3. Dopiero po retencji: Poincaré 006 (action weights 4→3) jako test headroom;
   porównywać absolute true, frozen i true−frozen, nie sam procent improvement.
4. P002/Poincaré005 dopiero jako test timing Hunger, z interakcją learning×Hunger.
5. Hooke005 kontra nowy
   `007-zgodny-most-w3-bez-glodu.yaml`: w krótkiej, dwuwarstwowej sieci 007 usuwa
   wyłącznie Hunger. Hipoteza mówi, że frozen/path spadną, ale przyrost uczenia i
   retencja wzrosną. Nie dubluje P001 ani Poincaré006 i nie ma pierwszeństwa przed
   głównym protokołem.

## 12. Kryterium decyzji

Nie uznawać „uczenia” na podstawie pojedynczego przebiegu, dodatniego
`rewardTrend` ani niezerowego `averageWeightDelta`. Robocze kryterium akceptacji:

1. dodatni, powtarzalny efekt sparowany w zamrożonej ewaluacji na held-out
   światach;
2. przewaga nad frozen, no-dopamine i shuffled/yoked reward;
3. zgodność zmian wag z mierzonym eligibility i przebiegiem \(D-B\);
4. brak dominacji clippingu oraz brak patologicznej asymetrii lustrzanej;
5. zachowanie kierunku i przybliżonej skali efektu przy niezależnej puli seedów.

Spełnienie wszystkich pięciu punktów byłoby dowodem, że poprawa jest skutkiem
reward-contingent DA-STDP, a nie jedynie aktywności, losowego przeszukiwania albo
doboru korzystnego seeda.
