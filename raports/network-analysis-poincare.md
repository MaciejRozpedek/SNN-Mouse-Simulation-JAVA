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
v=0{,}05(L+R),\qquad \Delta\theta=0{,}01(L-R).
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
wynieść 0,4 radiana. To dodatkowy powód, by skanować zysk motoryczny dopiero po
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

Bias Layer2 równy 3,8 leży praktycznie na wyliczonej przez Meitner granicy
stabilności RS 3,7984 dla `dt=1 ms`, więc celowo tworzy słaby firing warstwy
asocjacyjnej. Bias Layer3 wynosi 3,5 i pozostaje podprogowy: akcja wymaga wejścia
synaptycznego, z wyjątkiem jawnego głodu w 002. Waga `Layer1 -> Layer2` wynosi 5,
wagi do właściwych akcji 4, prąd wzroku ma maksimum 6 na obiekt, a zyski wyjścia
to 0,02 rad/spike i 0,05 jednostki/spike. Kolejność wyjść jest ważna: obie
rotacje są przetwarzane przed `Forward`, dlatego jednoczesna aktywność w 003
najpierw ustawia orientację, a potem przesuwa agenta.

### Strojenie konstrukcyjne, nie wynik eksperymentu

Pierwsza wersja rdzenia (`bias=3`, wagi 4 i 3) była zbyt cicha: dla dwóch
20-sekundowych prób dawała końcowo 0,16–0,22 Hz i tylko 2 jednostki drogi.
Krótkie porównanie czułości wskazało bias Layer2=3,8 i wagi 5/4 jako pierwsze
okno bez ciszy i bez saturacji. Po cross-review Meitner bias akcji obniżono z
3,8 do 3,5; bezpośredni 30-sekundowy smoke-test `World`, `step=1 ms`, dał:

| YAML | seedy topologii/świata | nagrody | droga | końcowe Hz |
|---|---|---:|---:|---:|
| 001 | 104729/123456 | 4 | 339,00 | 3,097 |
| 001 | 104730/123457 | 5 | 334,20 | 3,300 |
| 002 | 104729/123456 | 4 | 351,85 | 3,077 |
| 002 | 104730/123457 | 5 | 371,80 | 3,960 |
| 003 | 104729/123456 | 5 | 337,60 | 3,167 |
| 003 | 104730/123457 | 6 | 341,65 | 3,630 |

Te dwie próby służą wyłącznie wykryciu błędu YAML, ciszy i oczywistej saturacji.
Nie wolno na ich podstawie wybierać 002 ani odrzucać 003. Nie używają też funkcji
`mixSeed` benchmarku dla świata. Właściwy następny krok to uruchomić baseline,
001, 002 i 003 na tych samych 16 seedach według protokołu z sekcji 5. Najpierw
porównać 001 z baseline, następnie 002 i 003 z 001. Dopiero zwycięzca przechodzi
na 30 nietkniętych seedów.

Jeśli 001 okaże się nadmiernie aktywny w długim przebiegu, następna iteracja 004
powinna zmienić tylko bias Layer2 3,8 -> 3,5. Jeśli będzie zbyt cichy, najpierw
zwiększyć `Layer1 -> Layer2` 5 -> 6, nie kilka parametrów jednocześnie. Ablacje mapowania
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

Templates Hooke'a 001–003 zostały przeczytane. Jego 002 jest najbliższy 001
Poincaré, ale nie jest duplikatem eksperymentalnym: Hooke bada dwuwarstwowy most
`Sensory -> Motor` przy 200 neuronach, stałym szumie i głodzie we wszystkich
wariantach; Poincaré zachowuje trzy warstwy, używa deterministycznych biasów i
izoluje głód oraz łuk jako osobne czynniki. Seria Poincaré nie powiela zatem
losowego ani antyzgodnego mostu Hooke'a. Jego 002 może służyć jako kontrola, czy
dodatkowa warstwa asocjacyjna Poincaré daje korzyść ponad krótszy routing.

Raport i templates Meitner 001–006 również zostały przeczytane. Ich zakres to
bisekcja gainu w nadal pełnej, mieszającej projekcji baseline oraz kompensacja
Layer2->Layer3; nie pokrywa się z zachowującą indeks mapą Poincaré. Wyliczona
granica 3,7984 spowodowała obniżenie biasu akcji z 3,8 do 3,5. Wynik Meitner dla
10 seedów 005 (średnio 2,3 nagrody wobec 2,4 baseline i krótsza droga) jest
ostrzeżeniem, że samo osłabienie pełnego fan-in nie poprawia polityki.

Aktualizacja raportu Hooke'a skorygowała rachunek legacy motoru: `turn_factor`
mnoży już różnicę mocy kół, więc współczynnik przy `L-R` wynosi 0,01, nie 0,1.
Poprawkę przyjęto wyżej. Screening Hooke'a 60 s × 8 również pokazuje małe efekty
między bliskimi wariantami (zgodny most minus losowy: średnia +0,25, mediana 0),
co wzmacnia decyzję, aby nie stroić Poincaré 001/002 przed długim blokiem.

Templates Hooke'a 004–006 podnoszą wyłącznie wagę dwuwarstwowego mostu z 2 do 3
i dodają kontrolowany assay znaku skrętu. Hooke raportuje dla zgodnego mostu w=3
trafność znaku 16/16 przy celu w odległości 100, podczas gdy w=2 dawało 4/16.
Nie dubluje to 004/005 Poincaré: tam zmienia się boczny tor `Forward` albo timing
głodu w sieci trzywarstwowej. Wynik Hooke'a wskazuje jednak, by przed pełnym
screeningiem 004 wykonać tani test znaku i krzywizny dla celów ±45°, zamiast
wnioskować o routingu wyłącznie z liczby nagród.

Nowe templates Meitner 007–009 skanują wyłącznie wagę lokalnego hamowania
FS->RS (-3, -4, -6) przy pełnej topologii baseline. Nie dublują osi Poincaré;
mogą później dostarczyć informację, czy stabilne firing Poincaré wynika z rzadkiej
mapy, czy podobny efekt da się uzyskać samym inhibitory gain.

Aktualizacja Meitner niezależnie przelicza long screen i dochodzi do tego samego
kontraktu: 001 jako oszczędny rdzeń, 002 bez potwierdzonej wartości głodu oraz
`learningEnabled=true/false` na rozłącznych seedach. Zbieżność cross-review jest
argumentem, by nie stroić teraz E/I ani głodu na seedach 104729+.

W repozytorium nadal nie ma raportów Eulera i Noether. Raporty Hooke'a, Meitner
i Volty oraz wszystkie templates Hooke'a i Meitner zostały przeczytane. Narzędzie
`send_input` pozostaje niedostępne, więc identyfikatory Meitner
`019f5915-221f-79b1-8aa4-21e81a330172` i Hooke
`019f5915-22ec-7af0-9fae-75d41ef6587b` nie mogły zostać użyte do bezpośredniej
wymiany; cross-review wykonano na plikach.

## 9. Screening głównego runnera: 30 s × 10 seedów

Parametry: `duration=30 s`, `burn-in=5 s`, 10 sparowanych seedów od 104729.
Okno ewaluacyjne ma 25 s. Wszystkie runy Poincaré miały stabilny firing.

| Wariant | mean rewards | rewards/min ewaluacji | mean delta do baseline | mediana delty | + / = / - do baseline |
|---|---:|---:|---:|---:|---:|
| baseline | 2,4 | 5,76 | — | — | — |
| 001 | 4,0 | 9,60 | +1,6 (+66,7%) | 0 | 4 / 4 / 2 |
| 002 | 4,2 | 10,08 | +1,8 (+75,0%) | +1,5 | 6 / 1 / 3 |
| 003 | 3,4 | 8,16 | +1,0 (+41,7%) | +1 | nie podano |

Porównanie wewnątrz serii: 002−001 ma średnią +0,2 i medianę 0; 003−001
ma średnią -0,6 i medianę 0.

### Interpretacja

1. **002 jest właściwym liderem do eskalacji**, bo jako jedyny łączy dużą średnią
   poprawę z dodatnią medianą i sześcioma zwycięstwami. Stabilny firing usuwa
   najprostsze wyjaśnienie wyniku przez lawinę albo ciszę.
2. **001 daje mocny, lecz heterogeniczny efekt.** Cztery remisy i mediana 0
   oznaczają, że średnia +1,6 jest skupiona w części seedów. Należy szukać cechy
   seedów odpowiadającej za sukces: początkowej geometrii, biasu skrętu albo
   stopnia konkretnych neuronów akcji.
3. **Nie wykazano jeszcze dodatkowej wartości głodu.** Efekt 002−001 to tylko
   +0,2 nagrody i mediana 0. Głód może ratować niektóre zastoje, a szkodzić innym
   trajektoriom; potrzebne są surowe pary i droga per seed.
4. **003 nie przechodzi względem 001.** Boczny tor do `Forward` obniża średnią o
   0,6. Jest to zgodne z hipotezą nadmiernego ruchu podczas korekty kąta albo
   szerszego credit assignment, lecz bez `pathLength` nie można rozstrzygnąć
   mechanizmu. Medianowe 0 sugeruje szkodę skoncentrowaną w części seedów.

Eksploracyjny dokładny test znaków, ignorujący remisy, daje dla 001 vs baseline
`n=6`, 4 zwycięstwa, dwustronnie `p=0,6875`; dla 002 vs baseline `n=9`,
6 zwycięstw, `p=0,5078`. Test nie używa wielkości różnic i przy tej mocy nie
stanowi podstawy odrzucenia efektu. Z samych agregatów nie da się policzyć
sparowanego SD, przedziału ufności ani testu permutacyjnego — kolejny eksport
powinien zachować dziesięć surowych różnic.

Blok baseline/001/002 na 180 s × 16 został następnie ukończony bez zmiany YAML-i;
wyniki są w sekcji 10. Ponieważ ponownie użył seedów 104729+, pierwszych 10 seedów
uczestniczyło wcześniej w selekcji. Jest to test trwałości w dłuższym horyzoncie,
nie niezależny holdout. Formalne potwierdzenie nadal wymaga rozłącznego `baseSeed`.

### Następna iteracja oczekująca, bez blokowania testu głównego

- `004-delikatny-luk.yaml`: zmniejsza dodatkowy boczny fan-in z 2 do 1 i wagę
  z 1,5 do 1,0. Testuje, czy niepowodzenie 003 jest efektem dawki, a nie samej
  idei skręt+ruch.
- `005-glod-awaryjny.yaml`: zachowuje 002, ale przesuwa aktywację głodu z 5 s do
  15 s i skraca rampę z 20 s do 10 s. Ma działać dopiero jako ratunek dla zastoju,
  ograniczając ingerencję w skuteczne wczesne trajektorie.
- `006-slabszy-prior-learning.yaml`: zachowuje 001, lecz zmniejsza trzy wagi do
  akcji z 4 do 3, aby kontrola `learningEnabled=false` miała słabszą politykę
  początkową i zostawiła DA-STDP mierzalny headroom.

Po long screen priorytetem nie jest 004–006, lecz kontrola `learningEnabled`
true/false na niezmienionych 001/002. Nowe warianty pozostają kolejką późniejszą.

## 10. Long screen: 180 s × 16 seedów

Parametry: `duration=180 s`, `burn-in=30 s`, 16 sparowanych seedów od 104729.
Okno ewaluacyjne ma 150 s.

| Wariant | rewards mean ± SD | rewards/min | trend | stable firing | path | rewards/1000 path |
|---|---:|---:|---:|---:|---:|---:|
| baseline | 0,75 ± 1,00 | 0,300 | -0,375 | 0,44 | 69,09 | 10,86* |
| 001 | 22,6875 ± 4,701 | 9,075 | +2,9375 | 1,00 | 1739,54 | 13,04 |
| 002 | 22,875 ± 4,924 | 9,150 | +0,750 | 1,00 | 1959,38 | 11,67 |

`*` Efektywność baseline jest niestabilnym ilorazem dwóch małych wartości i nie
kompensuje faktu, że większość runów prawie się nie poruszała.

Sparowane wyniki:

- 001−baseline: dodatnie 16/16, mean +21,9375, mediana +21;
- 002−baseline: dodatnie 16/16, mean +22,125, mediana +22;
- 002−001: mean +0,1875, mediana +1, znaki 10/6.

### Interpretacja long screen

1. **Topologia jest jednoznacznie lepsza behawioralnie.** Oba warianty mają ponad
   30 razy większy reward rate od baseline i wygrywają we wszystkich 16 parach.
   Dla samych znaków 16/16 dokładne dwustronne `p=0,0000305`. Efekt obejmuje
   zarówno routing, jak i stabilizację: baseline ma stable ratio tylko 0,44,
   podczas gdy 001/002 mają 1,0.
2. **002 nie ma wyraźnej przewagi nagród nad 001.** Dodaje 12,64% drogi, ale tylko
   0,83% nagród. Efektywność spada o 10,49%. Znaki 10/6 dają dwustronny test
   znaków `p≈0,4545`; średnia +0,1875 jest praktycznie mała.
3. **001 jest obecnie oszczędniejszym kandydatem.** Ma mniejszą drogę, prawie tę
   samą liczbę nagród i trend +2,9375 wobec +0,75 w 002. Z mean i trendu wynika
   około 9,875 nagrody w pierwszej i 12,8125 w drugiej połowie dla 001, wobec
   11,0625 i 11,8125 dla 002. Głód przyspiesza eksplorację, lecz nie poprawia
   wyraźnie wyniku końcowego i może zmniejszać względną poprawę w czasie.
4. **To nadal nie jest dowód uczenia.** Jawny prior kąt→akcja może sam wykonywać
   zadanie przy zamrożonych wagach. Dodatni trend 001 jest zgodny z uczeniem, ale
   może też wynikać z geometrii trajektorii, kolejnych respawnów albo zmian stanu
   niezwiązanych z DA-STDP.

### Wymagana kontrola `learningEnabled`

Następny eksperyment to macierz 2×2:

| Topologia | plastyczność wyłączona | plastyczność włączona |
|---|---|---|
| 001 | `learningEnabled=false` | `learningEnabled=true` |
| 002 | `learningEnabled=false` | `learningEnabled=true` |

Każde cztery warunki muszą używać identycznych seedów świata i topologii oraz
tych samych parametrów 180/30 s. `learningEnabled` jest parametrem runnera, nie
sekcją obecnego YAML; nie należy tworzyć nieobsługiwanego klucza konfiguracyjnego.

Flaga jest już dostępna w aktualnym kodzie endpointu i domyślnie ma wartość
`true`. Przy `false` silnik pomija tworzenie STDP eligibility, aktualizację śladów
pre/post oraz zmianę wag; dynamika dopaminy i jej baseline nadal biegnie. Jest to
więc właściwa kontrola zamrożonej polityki przy zachowaniu nagród, resetu głodu i
pozostałej dynamiki świata, a nie kontrola „bez sygnału nagrody”.

Dla seeda `s` główne efekty to

\[
A_{001,s}=R^{true}_{001,s}-R^{false}_{001,s},\qquad
A_{002,s}=R^{true}_{002,s}-R^{false}_{002,s},
\]

a wpływ głodu na uczenie to interakcja

\[
H_s=A_{002,s}-A_{001,s}.
\]

Analogicznie liczyć różnicę trendów i rewards/1000 drogi. Warunek techniczny:
`averageWeightDelta` w każdym runie `false` musi być dokładnie 0; inaczej flaga
nie zamraża wag. Dowód pozytywnego uczenia wymaga dodatniej średniej i mediany
`A`, przedziału sparowanego powyżej zera oraz efektu niewyjaśnionego samym
zwiększeniem drogi. Jeśli true≈false, wynik 001/002 jest sukcesem projektu
topologii, ale nie DA-STDP. Jeśli true<false, plastyczność pogarsza gotowy prior.

Kontrolę można wykonać na bieżących 16 seedach jako analizę mechanizmu, lecz
ostateczny test powinien użyć rozłącznego bloku, np. 30 seedów od 1000003, ponieważ
104729+ uczestniczyły już w wyborze topologii. Zachować surowe rekordy per seed;
same mean±SD nie wystarczą do sparowanego CI i interakcji.

### Wynik kontroli archiwalnej true/frozen

Kontrolę wykonano na dokładnie archiwalnym YAML Poincaré, 180 s, burn-in 30 s,
16 seedów 104729+:

| Topologia | frozen mean | true mean | mean true−frozen | mediana | dodatnie | bootstrap 95% CI | test |
|---|---:|---:|---:|---:|---:|---:|---:|
| 001 | 18,625 | 22,6875 | +4,0625 (+21,8%) | +4,5 | 14/16 | [0,0625; 7,3125] | sign-flip: one-sided 0,02779; two-sided 0,05557 |
| 002 | 21,1875* | 22,875 | +1,6875 (+8,0%) | +1 | 9/16 | [-0,4375; 4,0] | one-sided 0,0947 |

`*` Frozen mean 002 wynika arytmetycznie z true mean i przekazanej średniej
różnicy. W 001 `averageWeightDelta=0` w 16/16 frozen runów, więc kontrola flagi
przeszła test techniczny.

W 001 kierunkowa, wcześniej postawiona hipoteza `true>frozen` ma dodatni wynik,
CI bootstrap ledwo powyżej zera i umiarkowany efekt standaryzowany `d_z=0,53`.
Test dwustronny jest graniczny (`p=0,05557`), co nie przeczy bootstrapowi: są to
inne małopróbkowe procedury, a rozkład permutacyjny jest dyskretny. Wniosek brzmi
„obiecujący efekt rozwojowy”, nie ostateczny dowód.

Frozen 001 zachowuje około 82,1% bezwzględnego wyniku true. Większość sprawności
jest zatem zakodowana w topologii i prądach, natomiast DA-STDP dodaje mierzalną,
umiarkowaną poprawę. Nie należy interpretować +21,8% jako udziału wyjaśnionej
przyczynowo wariancji — to zwykły względny wzrost rewards.

W 002 CI obejmuje zero, a wynik jednostronny 0,0947 nie potwierdza dodatkowej
wartości uczenia. Z samych średnich interakcja

\[
\bar H=(1{,}6875)-(4{,}0625)=-2{,}375
\]

sugeruje, że głód zastępuje część korzyści plastyczności: frozen 002 jest o 2,5625
nagrody wyżej od frozen 001, ale po włączeniu uczenia różnica topologii maleje do
0,1875. Bez surowych par, CI interakcji i drogi true/frozen jest to hipoteza, nie
rozstrzygnięcie.

Cross-review Hooke'a i Meitner wcześniej wymagał dokładnie tej kontroli; wynik 001
potwierdza jej sens, a niezerowy frozen pokazuje, dlaczego sam long screen nie był
dowodem uczenia.

### Niezależna replikacja 001 true/frozen

Replikacja użyła 30 niewidzianych seedów 1000003+, 180 s i burn-in 30 s:

| Ramię | rewards | trend | path | rewards/1000 path | Delta w |
|---|---:|---:|---:|---:|---:|
| frozen | 19,4000 | +0,733 | 1690,8 | 11,474 | 0 w 30/30 |
| learning | 21,4667 | +2,400 | 1744,5 | 12,305 | >0 w 30/30 |

Sparowane `learning−frozen`: mean +2,0667 (+10,65%), mediana +1,
bilans +/=/- = 17/3/10, bootstrap 95% CI [0,2; 3,933], `d_z=0,388`.
Test t dla par daje dwustronnie `p=0,04235`; Wilcoxon dwustronnie 0,0632,
jednostronnie 0,0316; test znaków 0,248. Trend rośnie średnio o 1,667, lecz jego
bootstrap CI [-0,667; 4] obejmuje zero.

Efekt replikuje kierunek na nowej puli, ale jest o około 49% mniejszy niż mean
z seedów archiwalnych. To mały–umiarkowany, wiarygodny efekt średniej, napędzany
bardziej wielkością dodatnich różnic niż samą przewagą liczby zwycięstw. Wzrost
nagród 10,65% jest większy niż wzrost drogi 3,18%; efektywność drogi poprawia się
o około 7,25%. Nie jest to więc wyłącznie skutek większej eksploracji.

Zgodność dodatniego bootstrap CI i testu t z granicznym Wilcoxonem oraz słabym
testem znaków oznacza heterogeniczny rozkład efektu, nie sprzeczność. Główny
wniosek: DA-STDP daje replikowany efekt ponad prior P001, lecz nie pomaga w każdym
seedzie i nie potwierdzono jeszcze retencji po zakończeniu uczenia.

### Protokół train -> freeze -> evaluate

Pierwszy phase protocol powinien nadal używać dokładnego archiwalnego P001, bez
strojenia YAML. Proponowane parametry mieszczą się w limicie 10 mln kroków:

| Parametr | Wartość |
|---|---:|
| `durationMs` | 300000 |
| `stepMs` | 1 |
| `freezeLearningAtMs` | 180000 |
| początek pomiaru post-freeze / `burnInMs` | 185000 |
| długość ewaluacji | 115000 |
| `repeats` | 30 |
| `baseSeed` | 4000003 |

Pięć sekund między freeze i pomiarem jest znacznie dłuższe niż `tau_D=20 ms` i
`tau_B=200 ms`. Setter flagi zeruje ślady pre/post i eligibility przy przełączeniu,
więc wagi powinny być dokładnie stałe po 180 s. Cały request ma 9 mln kroków.

Na identycznych seedach wykonać trzy ramiona:

1. **FF — always frozen:** `learningEnabled=false` od startu;
2. **TF — train then freeze:** true do 180 s, potem false;
3. **TT — continuous learning:** true przez całe 300 s, bez freeze.

Główny kontrast retencji to post-freeze `TF−FF`. `TT−TF` mierzy marginalną wartość
dalszej plastyczności w oknie ewaluacji. Dla każdego runu zapisać osobno rewards,
path, rewards/1000 path, trend i firing po 185 s, rewards treningowe do 180 s,
wagę początkową, wagę przy freeze i końcową. Warunki techniczne:

- `Delta w_post-freeze=0` w TF i FF dla 30/30;
- TF ma dodatnią średnią i medianę rewards względem FF, a bootstrap CI średniej
  nie obejmuje zera;
- przewaga nie może być wyjaśniona wyłącznie dłuższą drogą;
- TT−TF nie powinno wskazywać, że polityka natychmiast zanika po freeze.

Ten test mierzy retencję w ciągłej trajektorii, ale ramiona docierają do freeze w
różnych pozycjach, z innym rozkładem pokarmu i zegarem głodu. Nie jest to jeszcze
czysta ewaluacja wyłącznie wag. Mocniejszy etap późniejszy wymaga checkpointu:
zapisać wagi po treningu, uruchomić trained i initial weights w dwóch świeżych,
identycznie seedowanych światach, od środka, zawsze z learning=false. Brak tej
funkcji nie powinien blokować obecnego phase protocol.

Po protokole P001 można użyć tych samych trzech ramion dla 006 (wagi akcji 3) i
007 (niższy bias Layer2), ale na kolejnej rozłącznej puli seedów. Celem nie jest
maksymalizacja samego wyniku frozen, tylko zwiększenie dodatniego `TF−FF` bez
utraty stabilności i efektywności.

Cross-review nowego Hooke 007: usuwa on Hunger z dwuwarstwowego zgodnego mostu w=3
i testuje tę samą hipotezę substytucji głód–plastyczność w innej architekturze.
Nie dubluje Poincaré 007, który zmienia wyłącznie bias Layer2; P001 już pełni rolę
wariantu bez głodu względem P002. Hooke 007 jest więc wartościową replikacją
zewnętrzną. Dla porównywalności powinien jednak przyjąć ten sam 5-sekundowy
washout i początek pomiaru 185 s, zamiast liczyć post-freeze natychmiast od 180 s.

## 11. Rejestr bazowy

- gałąź w chwili analizy: `feature/codex`;
- HEAD: `79066bfb2f2fafe73ff5143705ced15ef6eb8a5f`;
- SHA-256 `SNNConfig.yaml`:
  `2C521EA98F268CAF1469AF219B167A9EEAF721D01742908CDFAC102331566DC0`;
- drzewo robocze zawiera cudze, niezatwierdzone zmiany; Poincaré ich nie edytuje;
- seria Poincaré ma short/long screening, kontrolę archiwalną i niezależną
  replikację uczenia w sekcjach 9–10; następny etap to phase protocol.
