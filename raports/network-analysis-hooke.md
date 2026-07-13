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
możliwa. Cross-review Meitner i Poincarégo wykonano przez ich raporty i katalog
`templates-meitner`; katalog Poincarégo nie był jeszcze obecny.

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

## 11. Kryterium decyzji

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
