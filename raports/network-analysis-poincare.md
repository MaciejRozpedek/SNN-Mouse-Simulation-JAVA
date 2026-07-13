# Analiza topologii i eksperymentów — Poincaré

Stan bazowy: 2026-07-13. Zakres obejmuje bieżący `SNNConfig.yaml`, loader,
kodowanie wejścia wzrokowego, dekoder motoryczny, dynamikę DA-STDP oraz aktualny
endpoint benchmarkowy. Jest to analiza projektu eksperymentów, nie wynik serii
symulacji: w repozytorium nie ma jeszcze surowych wyników co najmniej 10 powtórzeń.

## Konkluzja bazowa

Obecna architektura ma uporządkowany sensoryczny kod kąta, lecz traci go na
projekcji `Layer1 -> Layer2`: każdy z 80 neuronów wzrokowych RS łączy się z każdym
ze 100 neuronów Layer2. Następna projekcja jest również nietopograficzna. W efekcie
nie istnieje strukturalna droga „bodziec po lewej -> skręt w lewo” ani „bodziec na
wprost -> jazda naprzód”; może ona powstać wyłącznie przypadkowo i przez uczenie.

Wyjście nie składa się z trzech osobnych populacji `left/right/forward`.
`POPULATION_DRIVE` dzieli 80 neuronów RS Layer3 na dwie połówki po indeksie:
pierwsza steruje lewym kołem, druga prawym. Jazda naprzód jest ich składową
wspólną, a skręt — różnicą. Najpilniejsza zmiana eksperymentalna to zatem
zachowanie porządku kątowego i jawne nazwanie dwóch populacji kół. Nie należy
zaczynać od dużego przeszukiwania parametrów uczenia na obecnej, mieszającej
topologii.

## 1. Populacje i oczekiwana liczba połączeń

Kolejność tworzona przez loader jest deterministyczna względem YAML:

| Warstwa | RS | FS | Rola w bieżącej konfiguracji |
|---|---:|---:|---|
| Layer1 | 80, indeksy 0–79 | 20, indeksy 80–99 | wejście wzrokowe tylko do RS |
| Layer2 | 80, indeksy 100–179 | 20, indeksy 180–199 | warstwa mieszająca |
| Layer3 | 80, indeksy 200–279 | 20, indeksy 280–299 | odczyt motoryczny tylko z RS |

Łącznie jest 300 neuronów, w tym 240 RS i 60 FS, czyli nominalnie 80:20.
Ten stosunek nie oznacza jednak bilansu synaptycznego 80:20.

| Reguła | Liczba/oczekiwanie | Średnia waga | Uwagi |
|---|---:|---:|---|
| lokalne RS -> all | 2376, SD ok. 46,2 | 1,5 | trzy warstwy, `p=0,1` |
| lokalne FS -> RS | 900 dokładnie | -2 | 15 wyjść na każdy FS |
| Layer1 RS -> Layer2 all | 8000 dokładnie | 6 | pełna projekcja |
| Layer2 RS -> Layer3 all | 2000, SD ok. 38,7 | 3 | `p=0,25` |
| razem | 13 276, SD ok. 60,3 | ok. 4,20 globalnie | 12 376 E i 900 I |

Oczekiwany udział synaps hamujących to tylko 6,78%. Layer2 każdy neuron, także FS,
otrzymuje dokładnie 80 silnych wejść z Layer1. Layer3 każdy neuron otrzymuje
średnio 20 wejść feed-forward z Layer2. Jest to znacznie istotniejsze dynamicznie
niż sam stosunek liczby neuronów RS:FS.

W loaderze semantyka `allow_autapses` jest odwrócona: warunek odrzuca `src == tgt`
właśnie wtedy, gdy flaga ma wartość `true`. Dlatego rachunek lokalny to

\[
3\,(80\cdot100-80)\,0{,}1=2376,
\]

a nie 2400. Jest to błąd semantyczny względem dokumentacji, który trzeba utrzymywać
w rejestrze zagrożeń przy porównywaniu konfiguracji.

Rozkłady normalne nie są obcięte. Dla obu projekcji średnia jest oddalona od zera
o trzy odchylenia standardowe, więc oczekuje się około 10,8 ujemnych wag w
Layer1->Layer2 i 2,7 w Layer2->Layer3. Kod DA-STDP traktuje każdą aktualnie ujemną
wagę jako nieplastyczną, niezależnie od typu neuronu źródłowego. Powstaje zatem
mała, losowa domieszka zamrożonego hamowania pochodzącego z RS.

## 2. Kod sensoryczny

Dla 80 neuronów RS preferowany kąt wynosi

\[
\theta_i=-60^\circ+\frac{120^\circ i}{79},\qquad i=0,\ldots,79.
\]

Szerokość Gaussa to `sigma = (120°/80) * 1,5 = 2,25°`, podczas gdy odstęp między
środkami wynosi około 1,52°. Sąsiednie pola silnie się nakładają; pojedynczy
punktowy bodziec pobudza efektywnie około 3,71 kanału. Amplituda pojedynczego
pokarmu w odległości `r` to

\[
I(r)=10(1-r/500)^2.
\]

Prądy wszystkich widocznych obiektów są sumowane bez normalizacji i bez wyboru
najbliższego celu. Przy 100 obiektach w świecie 1000x800 w polu widzenia może być
ich jednocześnie około 30. W przybliżeniu jednorodnego wnętrza świata oczekiwany
prąd tła jednego kanału wewnętrznego wynosi około 2,56 jeszcze przed wkładem
wyjątkowo bliskiego pokarmu. `max_current=10` jest więc maksimum wkładu jednego
obiektu, nie maksimum całego wejścia. Samo zmniejszenie tej wartości skaluje
problem, ale nie usuwa zależności od liczby widocznych obiektów.

Kod zawiera informację o znaku kąta i odległości (przez amplitudę), ale nie koduje
tożsamości celu. Superpozycja dwóch obiektów może stworzyć profil podobny do
jednego celu pośrodku. Wysoka gęstość jedzenia utrudnia zatem interpretację, czy
agent nauczył się śledzić cel, czy reaguje na sumaryczny gradient.

## 3. Left, right i forward w faktycznym dekoderze

Dla liczby spike'ów `L` w pierwszych 40 komórkach i `R` w kolejnych 40:

\[
v=0{,}05(L+R),\qquad \Delta\theta=0{,}1(L-R).
\]

Pierwsza połowa jest lewym kołem, a druga prawym. Pobudzenie lewego koła obraca
agenta w dodatnim kierunku kąta, czyli daje skręt w stronę przeciwną do tego koła;
pobudzenie prawego koła analogicznie daje skręt ujemny. Ruch naprzód nie ma
własnych neuronów: wymaga podobnej aktywności obu połówek.

Praktyczne mapowanie powinno więc być skrzyżowane:

| Bodziec | Pożądana aktywność | Efekt |
|---|---|---|
| lewa część pola, kąt ujemny | prawe koło > lewe koło | skręt ujemny/lewy |
| centrum | prawe koło ~= lewe koło | naprzód |
| prawa część pola, kąt dodatni | lewe koło > prawe koło | skręt dodatni/prawy |

`PopulationDriveStrategy` ignoruje `deltaTime`; ruch i obrót są naliczane na spike,
nie na sekundę. Przy maksymalnej różnicy 40 spike'ów obrót jednego kroku może
wynieść 4 radiany. To dodatkowy powód, by skanować zysk motoryczny dopiero po
ustabilizowaniu aktywności i zawsze trzymać `stepMs=1,0` podczas porównań.

## 4. Dynamika i uczenie: ryzyka interpretacyjne

1. Silna pełna projekcja do Layer2 może wywołać zarówno lawinę RS, jak i silne
   feed-forward inhibition, bo jej celem są również FS. Globalna częstość końcowa
   nie rozróżni tych stanów pomiędzy warstwami.
2. DA-STDP zmienia tylko wagi nieujemne. Ślad kwalifikowalności zanika z czasem
   1000 ms, dopamina z czasem 20 ms, a jej poziom bazowy z czasem 200 ms. Nagroda
   po kolizji może więc przypisać zasługę głównie spike'om z ostatniej około
   sekundy, nie całej trajektorii podejścia.
3. `averageWeightDelta` jest średnią po wszystkich około 13 tys. synapsach, w tym
   zamrożonych hamujących. Może być bliska zeru mimo dużej, przeciwnej reorganizacji
   dwóch projekcji. Potrzebne są statystyki wag per reguła/projekcja.
4. `finalFiringRateHz` obejmuje wyłącznie ostatnie okno 1 s i wszystkie 300
   neuronów. Nie wykrywa wcześniejszej saturacji ani ciszy konkretnej populacji.
5. Świat ma 100 pokarmów i promień zjedzenia 30. Już przy pozycji startowej
   prawdopodobieństwo co najmniej jednego losowego pokarmu w tym promieniu jest
   w przybliżeniu 30%. Burn-in ogranicza ten artefakt w metryce głównej, ale nie
   usuwa wysokiego tła przypadkowych nagród.
6. Świat sprawdza po ruchu kolizję tylko z obiektem, który był najbliższy przed
   ruchem. Nagrody nie są więc idealną funkcją całej przebytej trajektorii.

## 5. Protokół seedów i co najmniej 10 powtórzeń

Jednostką statystyczną jest niezależny seed/run, nie krok symulacji ani spike.
Każda konfiguracja w danym bloku musi używać identycznego `baseSeed`, liczby
powtórzeń, czasu i burn-in. Aktualny benchmark poprawnie rozdziela seed sieci od
zdeterminowanego nim seedu świata, dzięki czemu pozycje jedzenia są sparowane
między konfiguracjami. Zmiana topologii zmienia jednak liczbę wywołań generatora
sieci, więc nie daje pełnego sparowania pojedynczych krawędzi i wag.

Proponowane dwa niezależne bloki:

| Etap | Parametry | Cel |
|---|---|---|
| screening | `durationMs=180000`, `burnInMs=30000`, `stepMs=1`, `repeats=16`, `baseSeed=104729` | odrzucenie ciszy, saturacji i złej orientacji |
| potwierdzenie | te same czasy, `repeats=30`, `baseSeed=1000003` | ocena wybranego wariantu na nietkniętych seedach |

Nie wolno wybierać konfiguracji i raportować jej istotności na tych samych 16
seedach. Jeśli używany jest `TONIC_NOISE`, brak `seed` niszczy powtarzalność, a
stały seed w YAML daje identyczny przebieg szumu we wszystkich runach. Obecny
endpoint nie wyprowadza seedu szumu z seedu runu; do czasu rozszerzenia harnessu
najczystszy screening używa `noise_std=0` albo osobno zarządzanej listy seedów
szumu.

Dla każdego runu należy zachować surowy rekord, nie tylko summary. Główna zmienna
to `evaluationRewards`. Porównanie konfiguracji ma być sparowane po seedzie:

\[
d_s=R_{candidate,s}-R_{baseline,s}.
\]

Raportować trzeba średnią i medianę `d_s`, SD, IQR, 95% bootstrap CI średniej
różnicy oraz sparowany test permutacyjny znaków. Dla wielu kandydatów screening
jest etapem rankingowym; formalny test dotyczy tylko wcześniej wybranego wariantu
na 30 nowych seedach. `rewardTrend` jest metryką wtórną — różnica dwóch małych
liczb zdarzeń jest bardzo szumna. `pathLength` należy interpretować razem z
nagrodami, najlepiej również jako rewards/1000 jednostek drogi.

## 6. Kryteria selekcji

Kandydat przechodzi dalej tylko wtedy, gdy spełnia wszystkie bramki:

1. **Powtarzalność:** ponowienie identycznego żądania daje identyczne wyniki
   naukowe per seed; czas ścienny może się różnić.
2. **Aktywność:** nie ma runów całkowicie cichych ani trwale nasyconych. Do czasu
   dodania przebiegów warstwowych roboczy zakres końcowej średniej to 1–20 Hz;
   wartości poniżej 0,1 Hz lub powyżej 50 Hz są sygnałem odrzucenia, nie dowodem
   samym w sobie.
3. **Sterowanie:** agent porusza się w co najmniej 90% runów, a testy z pojedynczym
   lustrzanym bodźcem dają przeciwne skręty o podobnej wartości bezwzględnej.
4. **Screening:** średnia sparowana różnica nagród jest dodatnia, mediana nie jest
   ujemna, a wynik nie pochodzi z jednego odstającego seedu.
5. **Potwierdzenie:** dolna granica 95% CI dla średniej sparowanej różnicy nagród
   jest powyżej zera i poprawa jest praktycznie istotna — roboczo co najmniej 20%
   względem baseline. Próg absolutny należy ustalić po pierwszym pomiarze tła.
6. **Uczenie, nie tylko napęd:** poprawie nagród towarzyszy dodatni trend lub lepsza
   efektywność drogi oraz przewaga nad kontrolą bez plastyczności/nagrody. Obecny
   YAML nie pozwala wyłączyć DA-STDP, więc ta kontrola wymaga wsparcia harnessu.

Bez testu lustrzanego i kontroli bez uczenia większa liczba nagród oznacza jedynie
lepszą politykę końcową, a nie dowód nauczenia relacji sensoryczno-motorycznej.

## 7. Seria konfiguracji Poincaré — iteracja 001–003

W bieżącym drzewie roboczym są już dostępne strategie `TURN_LEFT`, `TURN_RIGHT`
i `FORWARD_DRIVE`. Pozwala to zastąpić wcześniejszy plan dwóch kół trzema jawnymi
populacjami akcji. Utworzona mała seria znajduje się w `raports/templates-poincare/`:

| YAML | Jedyna hipoteza różnicująca |
|---|---|
| `001-topograficzne-akcje.yaml` | rzadka mapa „obróć do centrum, potem jedź” |
| `002-glod-do-przodu.yaml` | 001 + resetowany nagrodą głód pobudza tylko `Forward` |
| `003-lukowe-podejscie.yaml` | 001 + słabe boczne kolaterale do `Forward`, czyli podejście po łuku |

Wspólny rdzeń zachowuje 300 neuronów i bilans liczebności 80:20 w każdej warstwie.
Layer2 ma kolejno `VisualNegative` 20 RS + 5 FS, `VisualCenter` 40 + 10 i
`VisualPositive` 20 + 5. Projekcja `one_to_one` z 80 RS Layer1 zachowuje kolejność
kąta. Layer3 ma odpowiadające populacje `NegativeTurn`, `Forward` i
`PositiveTurn`; każda komórka akcji dostaje dokładnie cztery wejścia ze swojego
kanału. Znak ujemny jest dekodowany przez `TURN_RIGHT` (ujemna zmiana kąta), a
dodatni przez `TURN_LEFT`.

Przy `p=0,08` oczekiwana liczba lokalnych krawędzi pobudzających wynosi 1100,8.
Do tego dochodzi dokładnie 600 krawędzi FS->RS, 80 połączeń `one_to_one` i 320
połączeń do akcji. W 001/002 jest więc oczekiwane 2100,8 synaps, z czego 28,6%
jest jawnie hamujących. Dla seeda 104729 loader utworzył 2089 krawędzi. W 003
dwa boczne tory dodają dokładnie 160 krawędzi; loader utworzył 2249. Jest to
około sześciokrotnie mniej niż oczekiwane 13 276 synaps baseline.

Toniczny bias 3,8 leży tuż pod przybliżoną reobazą RS równą 4. Ma umożliwiać
propagację wejścia bez autonomicznej lawiny. Waga `Layer1 -> Layer2` wynosi 5,
wagi do właściwych akcji 4, prąd wzroku ma maksimum 6 na obiekt, a zyski wyjścia
to 0,02 rad/spike i 0,05 jednostki/spike. Kolejność wyjść jest ważna: obie
rotacje są przetwarzane przed `Forward`, dlatego jednoczesna aktywność w 003
najpierw ustawia orientację, a potem przesuwa agenta.

### Strojenie konstrukcyjne, nie wynik eksperymentu

Pierwsza wersja rdzenia (`bias=3`, wagi 4 i 3) była zbyt cicha: dla dwóch
20-sekundowych prób dawała końcowo 0,16–0,22 Hz i tylko 2 jednostki drogi.
Krótkie porównanie czułości wskazało `bias=3,8`, wagi 5 i 4 jako pierwsze okno
bez ciszy i bez saturacji. Po zapisaniu tych wartości wykonano bezpośredni
30-sekundowy smoke-test `World`, `step=1 ms`:

| YAML | seedy topologii/świata | nagrody | droga | końcowe Hz |
|---|---|---:|---:|---:|
| 001 | 104729/123456 | 4 | 391,25 | 3,197 |
| 001 | 104730/123457 | 7 | 387,80 | 4,100 |
| 002 | 104729/123456 | 5 | 433,65 | 3,340 |
| 002 | 104730/123457 | 7 | 397,65 | 3,823 |
| 003 | 104729/123456 | 4 | 410,00 | 3,583 |
| 003 | 104730/123457 | 6 | 404,10 | 4,127 |

Te dwie próby służą wyłącznie wykryciu błędu YAML, ciszy i oczywistej saturacji.
Nie wolno na ich podstawie wybierać 002 ani odrzucać 003. Nie używają też funkcji
`mixSeed` benchmarku dla świata. Właściwy następny krok to uruchomić baseline,
001, 002 i 003 na tych samych 16 seedach według protokołu z sekcji 5. Najpierw
porównać 001 z baseline, następnie 002 i 003 z 001. Dopiero zwycięzca przechodzi
na 30 nietkniętych seedów.

Jeśli 001 okaże się nadmiernie aktywny w długim przebiegu, następna iteracja 004
powinna zmienić tylko bias 3,8 -> 3,5. Jeśli będzie zbyt cichy, najpierw zwiększyć
`Layer1 -> Layer2` 5 -> 6, nie kilka parametrów jednocześnie. Ablacje mapowania
znaku, braku widzenia i zamrożonego DA-STDP pozostają etapem późniejszym.

## 8. Cross-review i stan współpracy

Przeczytany ogólny `raports/network-analysis.md` trafnie wykrywa wąskie okno między
ciszą i saturacją oraz poprawnie liczy 2376 lokalnych synaps po uwzględnieniu błędu
autaps. Wymaga jednak trzech korekt:

1. Layer3 jest faktycznie dzielona przez kod na dwie połówki motoryczne, choć YAML
   ich nie nazywa.
2. `max_current=10` ogranicza wkład jednego pokarmu, nie sumę całego wejścia.
3. Zmiana liczebności RS:FS z 80:20 na 60:40 nie jest pierwszym czystym testem;
   najpierw należy usunąć pełne mieszanie i ograniczyć efektywny fan-in.

Przeczytany raport Volty proponuje głównie osobne zmniejszanie prądu wzroku,
gęstości pełnej projekcji, zmianę E:I i podział legacy motoru. Seria Poincaré nie
duplikuje tych testów: jednocześnie usuwa mieszanie, zachowuje indeks kąta i używa
nowych jawnych akcji. Przyjęto jednak jego ostrożniejszy `max_current` i wymóg
kontroli symetrii; nie przyjęto na pierwszym etapie zmiany liczby RS:FS na 60:40,
bo mieszałaby efekt topologii z efektem hamowania.

Raport Hooke'a doprecyzowuje, że DA-STDP ma okno pre/post 5 ms, eligibility 1000 ms
i dwufazowy sygnał `D-B`, który po około 51 ms staje się ujemny. Wpłynęło to na
003: krótsza droga czasowa od bocznego bodźca do nagrody ma zachować większe
`exp(-L/1000)`, ale może poszerzyć credit assignment przez współaktywację skrętu
i ruchu. Przyjęto też jego wymóg przyszłej kontroli frozen/no-dopamine oraz
potwierdzenia na co najmniej 30 nowych seedach. Tych kontroli nie da się obecnie
zakodować samym YAML-em.

W repozytorium nadal nie ma raportów Eulera, Noether i Meitner ani cudzych
katalogów templates. Raport Hooke'a i raport Volty zostały przeczytane. Narzędzie
`send_input` pozostaje niedostępne, więc identyfikatory Meitner
`019f5915-221f-79b1-8aa4-21e81a330172` i Hooke
`019f5915-22ec-7af0-9fae-75d41ef6587b` nie mogły zostać użyte do bezpośredniej
wymiany; cross-review wykonano na plikach.

## 9. Rejestr bazowy

- gałąź w chwili analizy: `feature/codex`;
- HEAD: `79066bfb2f2fafe73ff5143705ced15ef6eb8a5f`;
- SHA-256 `SNNConfig.yaml`:
  `2C521EA98F268CAF1469AF219B167A9EEAF721D01742908CDFAC102331566DC0`;
- drzewo robocze zawiera cudze, niezatwierdzone zmiany; Poincaré ich nie edytuje;
- brak danych pozwalających obecnie stwierdzić poprawę uczenia w co najmniej
  10 niezależnych powtórzeniach.
