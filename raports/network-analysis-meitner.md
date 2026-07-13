# Analiza dynamiki SNN — Meitner

Stan analizy bazowej: 2026-07-13. Przedmiotem jest faktycznie ładowany `src/main/resources/config/SNNConfig.yaml` oraz bieżąca implementacja `SnnEngine`, `NetworkTopologyLoader`, wejść, wyjść i diagnostyki. Wszystkie wartości liczbowe niżej odnoszą się do kroku symulacji (h=1\,\mathrm{ms}), o ile nie zaznaczono inaczej.

## 1. Najważniejsze wnioski

1. Stan ((v,u)=(-70,-14)) jest dokładnym stabilnym punktem spoczynkowym obu typów neuronów przy (I=0). Sieć bez wejścia i bez wcześniejszych impulsów pozostaje deterministycznie cicha.
2. Obecny świat nie realizuje jednak warunku „bez wejścia”: startuje ze 100 porcjami jedzenia, a prądy od wszystkich widocznych obiektów sumują się bez ograniczenia. W niezależnym Monte Carlo geometrii początkowej co najmniej jeden neuron Layer1 RS przekraczał około (3.8) jednostki prądu w (99.92\%\) układów.
3. Dla stałego prądu próg utraty lokalnej stabilności stanu podprogowego leży blisko (I=3.8): około (3.7984) dla RS i (3.9506) dla FS w dyskretnym schemacie z kodu. Skokowe podanie prądu może wywołać cykl graniczny jeszcze przy współistnieniu stabilnego punktu stałego.
4. Nominalny stosunek liczby neuronów wynosi (E:I=4:1), ale oczekiwana suma modułów wag pobudzających do hamujących wynosi globalnie około (32:1). Przyczyną jest przede wszystkim pełna projekcja Layer1 RS→Layer2 all o średniej wadze 6.
5. Pojedynczy impuls z Layer1 daje każdemu neuronowi Layer2 skok napięcia o około (6\,\mathrm{mV}). Jeżeli wszystkie 80 neuronów Layer1 RS wystrzelą w jednym kroku, oczekiwana suma impulsów na jeden neuron Layer2 wynosi (480\pm17.9\,\mathrm{mV}). Jest to miara synchronicznego impulsu, nie zwykłego średniego prądu.
6. Hamowanie jest lokalne, dociera z takim samym opóźnieniem jednego kroku jak pobudzenie i trafia tylko do RS. Neurony FS nie otrzymują żadnego wejścia hamującego. Układ może więc generować silnie skorelowane, opóźnione oscylacje E/I, lecz hamowanie nie równoważy bezpośrednio ogromnych projekcji feed-forward.
7. Krytyczność nie jest obecnie wykazana. Sama liczba synaps, suma wag ani średni firing rate nie wyznaczają parametru rozgałęzienia. Potrzebne są pomiary lawin, wielokrokowy estymator branching ratio, rozkłady aktywności i analiza osobno dla warstw oraz E/I.
8. Plastyczność nie stanowi mechanizmu homeostatycznego: brak normalizacji wag, skalowania synaptycznego i plastyczności hamującej. Dodatnie wagi mogą dojść do 50, a ujemne są zamrożone. Krytyczność, jeśli się pojawi, nie jest przez kod stabilnie regulowana.

## 2. Rzeczywisty model neuronu i kolejność aktualizacji

Model ciągły zapisany w kodzie ma postać

\[
\dot v=0.04v^2+5v+140-u+I,
\qquad
\dot u=a(bv-u).
\]

Po przekroczeniu (v\ge 30) wykonywany jest reset

\[
v\leftarrow c,
\qquad
u\leftarrow u+d.
\]

Parametry konfiguracji:

| typ | (a) | (b) | (c) | (d) | (v_0) | (u_0) |
|---|---:|---:|---:|---:|---:|---:|
| RS | 0.02 | 0.2 | -65 | 8 | -70 | -14 |
| FS | 0.10 | 0.2 | -65 | 2 | -70 | -14 |

Implementacja nie wykonuje jednoczesnego kroku Eulera. Najpierw aktualizuje (u), a potem używa nowego (u_{n+1}) do aktualizacji (v):

\[
u_{n+1}=u_n+h,a(bv_n-u_n),
\]

\[
v_{n+1}=v_n+h\left(0.04v_n^2+5v_n+140-u_{n+1}+I_n\right)+S_n.
\]

(S_n) jest sumą wag impulsów synaptycznych przechowanych z poprzedniego kroku. Po integracji tablice (I) i (S) są zerowane, wykrywane są spike'i, a ich wagi trafiają do (S_{n+1}). Konsekwencje:

- opóźnienie każdej synapsy wynosi dokładnie jeden krok, obecnie (1\,\mathrm{ms});
- prąd zewnętrzny wnosi do napięcia (hI), natomiast waga synaptyczna wnosi bezpośredni skok (w);
- przy (h=1) wartości liczbowe mogą wyglądać podobnie, lecz ich jednostki i skalowanie są różne;
- neuron może wygenerować najwyżej jeden spike na krok, czyli formalnie najwyżej (1000\,\mathrm{Hz}) przy (h=1\,\mathrm{ms});
- nadmiar ponad próg jest tracony przez pojedynczy reset, więc bardzo silny impuls i impuls tylko nieznacznie nadprogowy dają w danym kroku ten sam binarny wynik.

## 3. Punkty stałe i stabilność

### 3.1. Model ciągły

W punkcie stałym (u=bv). Dla (b=0.2):

\[
0.04v^2+4.8v+140+I=0,
\]

stąd dla (I\le4)

\[
v_\pm=-60\pm5\sqrt{4-I},
\qquad u_\pm=0.2v_\pm.
\]

Przy (I=0) są to (v_-=-70) i (v_+=-50). Dolna gałąź jest stanem spoczynkowym, górna separatorem/siodłem. Jakobian modelu ciągłego wynosi

\[
J=
\begin{pmatrix}
0.08v+5 & -1\\
ab & -a
\end{pmatrix}.
\]

Oznaczając (alpha=0.08v+5), warunek stabilności dolnego punktu wymaga w szczególności (alpha<a). Daje to w przybliżeniu:

\[
I_{\mathrm{loc,RS}}=3.7975,
\qquad
I_{\mathrm{loc,FS}}=3.9375.
\]

Przy (I=4) obie gałęzie zlewają się w (v=-60). Powyżej 4 nie ma punktu stałego równania podprogowego.

### 3.2. Stabilność dokładnego kroku z kodu

Linearyzacja mapy dyskretnej ma macierz

\[
M_h=
\begin{pmatrix}
1+h\alpha-h^2ab & -h(1-ha)\\
hab & 1-ha
\end{pmatrix},
\]

z wyznacznikiem

\[
\det M_h=(1-ha)(1+h\alpha).
\]

Dla (h=1) warunki Jury'ego prowadzą do granicy (alpha=a/(1-a)), zanim dolna i górna gałąź zleją się przy (I=4). Otrzymujemy:

| typ | granica lokalnej stabilności mapy (h=1) |
|---|---:|
| RS | (I\approx3.7984) |
| FS | (I\approx3.9506) |

W spoczynku (I=0) mnożniki mapy wynoszą w przybliżeniu:

- RS: (0.9732) i (0.4028),
- FS: (0.8627) i (0.4173).

RS ma więc wyraźnie wolniejszy dominujący powrót do spoczynku. Lokalna stabilność nie oznacza jednak, że nagły skok prądu zawsze doprowadzi do tego punktu. Dla części zakresu może współistnieć atraktor spikingowy, a skok z początkowego ((-70,-14)) może opuścić basen punktu stałego.

## 4. Krzywe firing rate–current dla implementowanego kroku

Poniższe wartości uzyskałem przez bezpośrednią iterację powyższej mapy: (20\,\mathrm{s}), odrzucenie pierwszych (5\,\mathrm{s}), stały prąd, brak synaps, (h=1\,\mathrm{ms}), start ((-70,-14)).

| (I) | RS [Hz] | FS [Hz] |
|---:|---:|---:|
| 0–3.7 | 0 | 0 |
| 3.8 | 5.4 | 0 |
| 3.9 | 6.4 | 17.3 |
| 4.0 | 7.0 | 22.3 |
| 5.0 | 10.4 | 39.2 |
| 6.0 | 12.9 | 52.1 |
| 8.0 | 17.1 | 77.7 |
| 10.0 | 21.7 | 105.8 |
| 15.0 | 31.2 | 166.7 |
| 20.0 | 41.7 | 250.0 |

Wartość FS przy (I=3.9), mimo lokalnej stabilności punktu podprogowego aż do około (3.95), jest przykładem zależności od basenu i sposobu włączenia prądu. Z tego powodu próg należy raportować wraz z protokołem: skok, rampa rosnąca, rampa malejąca albo inicjalizacja w punkcie stałym.

## 5. Topologia i bilans E/I

### 5.1. Populacje

Każda z trzech warstw zawiera 80 RS i 20 FS:

\[
N=300,
\qquad N_E=240,
\qquad N_I=60,
\qquad E:I=4:1.
\]

„RS” i „FS” określają dynamikę neuronu, a nie znak wszystkich jego synaps. Dale'owska interpretacja wynika dopiero z wag połączeń.

### 5.2. Liczba synaps

W loaderze występuje odwrócony warunek autaps: `allow_autapses: true` faktycznie odrzuca (src=tgt), natomiast `false` je dopuszcza. W obecnej konfiguracji zmienia to lokalną regułę RS→all: z każdej warstwy odpada 80 autaps RS→RS.

| projekcja | oczekiwana/dokładna liczba | rozkład wag | oczekiwana suma wag |
|---|---:|---:|---:|
| lokalna RS→all, 3 warstwy | (3\cdot80\cdot99\cdot0.1=2376) | (U(1,2)) | (3564) |
| lokalna FS→RS, 3 warstwy | (3\cdot20\cdot15=900) | (-2) | (-1800) |
| Layer1 RS→Layer2 all | (80\cdot100=8000) | (N(6,2^2)) | (48000) |
| Layer2 RS→Layer3 all | (80\cdot100\cdot0.25=2000) średnio | (N(3,1)) | (6000) średnio |

Łącznie:

\[
\mathbb E[S]=13276,
\qquad
\operatorname{sd}(S)\approx60.3.
\]

Nominalnie 12376 krawędzi jest pobudzających, a 900 hamujących, czyli (93.2\%:6.8\%\). Jeszcze silniejsza jest asymetria ważona:

\[
\frac{\mathbb E\sum w_+}{\sum|w_-|}
=\frac{57564}{1800}
\approx31.98.
\]

Ta wartość globalna jest zdominowana przez projekcje feed-forward; nie należy interpretować jej jako bezpośredniego współczynnika rozgałęzienia.

Rozkłady normalne są nieograniczone. Dla obu projekcji (P(w<0)=\Phi(-3)\approx0.00135), więc oczekujemy około 10.8 ujemnych wag Layer1→Layer2 i 2.7 ujemnych wag Layer2→Layer3. Są to synapsy wychodzące z RS, lecz działające hamująco, co narusza prawo Dale'a. Kod traktuje je również jako nieplastyczne.

### 5.3. Konwergencja na pojedynczy neuron

Wartości w tabeli oznaczają sumę impulsów, jeżeli każdy neuron danej populacji presynaptycznej wystrzeli dokładnie raz w tym samym binie (1\,\mathrm{ms}).

| cel | lokalne E | lokalne I | feed-forward |
|---|---:|---:|---:|
| RS dowolnej warstwy | (7.9\cdot1.5=11.85) | (3.75\cdot(-2)=-7.5) | zależne od warstwy |
| FS dowolnej warstwy | (8\cdot1.5=12) | (0) | zależne od warstwy |
| Layer2 RS/FS | jak wyżej | jak wyżej | (80\cdot6=480) |
| Layer3 RS/FS | jak wyżej | jak wyżej | (80\cdot0.25\cdot3=60) |

Dla Layer2 odchylenie standardowe sumy 80 wag feed-forward wynosi (\sqrt{80}\cdot2\approx17.9). Dla Layer3, po uwzględnieniu losowej obecności krawędzi i rozkładu wag, wynosi około (12.45).

Lokalne pobudzenie i hamowanie wejścia do RS równoważą się w średniej dopiero wtedy, gdy

\[
11.85\,r_E\approx7.5\,r_I,
\qquad
r_I\approx1.58\,r_E.
\]

FS rzeczywiście ma bardziej stromy (f-I), ale nie otrzymuje hamowania I→I. Bilans zależy więc silnie od względnych firing rates i synchronii, nie tylko od liczebności 80/20.

## 6. Skala wejścia wzrokowego

Dla 80 neuronów Layer1 RS:

\[
\sigma=\frac{120^\circ}{80}\cdot1.5=2.25^\circ,
\]

podczas gdy odstęp preferowanych kątów wynosi

\[
\Delta\theta=\frac{120^\circ}{79}\approx1.519^\circ.
\]

Aktywacja sąsiedniego kanału dla bodźca leżącego dokładnie w centrum jednego kanału wynosi około (0.796). Jeden obiekt pobudza zatem kilka sąsiednich neuronów. Prąd od jednego obiektu ma amplitudę

\[
I_{ij}=10\left(1-\frac{r_j}{500}\right)^2
\exp\left[-\frac{(\theta_j-\theta_i)^2}{2\sigma^2}\right],
\]

a wkłady wszystkich widocznych obiektów są sumowane. `max_current: 10` nie jest więc limitem całkowitego prądu neuronu.

Wykonałem osobne Monte Carlo 10000 początkowych układów: 100 obiektów równomiernie w świecie (1000\times800), agent w ((500,400)), kierunek (0), bez ruchu i bez dynamiki sieci. Wyniki geometrycznego prądu w pierwszej klatce:

| wielkość | średnia | mediana | 5–95 percentyl |
|---|---:|---:|---:|
| suma prądu po 80 kanałach | 201.38 | 198.47 | 123.69–289.87 |
| średni prąd na kanał | 2.52 | 2.48 | 1.55–3.62 |
| maksimum po kanałach | 10.55 | 10.14 | 6.42–15.95 |
| liczba kanałów z (I\ge3.8) | 19.56 | 19 | 8–32 |

Prawdopodobieństwo, że co najmniej jeden kanał miał (I\ge3.8), wyniosło (99.92\%\). Nie jest to pełna symulacja sieci, lecz wystarcza do odrzucenia założenia, że brak `TONIC_NOISE` oznacza typowy cichy start. Obecny sensor stanowi niemal zawsze aktywny, przestrzennie skorelowany napęd.

## 7. Oczekiwane reżimy dynamiczne

### Reżim cichy

Przy braku widocznego jedzenia, zerowym prądzie i wyzerowanym (S) stan spoczynkowy jest absorpcyjny: nie ma szumu wewnętrznego ani spontanicznego źródła spike'ów. Aktywność nie odrodzi się sama po całkowitym wygaśnięciu.

### Reżim sensorycznie podtrzymywany

Prądy około 4–10 dają izolowanym RS około 7–22 Hz. Wspólne, nakładające się pola Gaussa korelują sąsiednie neurony Layer1. To tworzy paczki współdzielonego wejścia dla wszystkich neuronów Layer2.

### Reżim lawinowo-burstowy

Jeden spike Layer1 daje niemal wszystkim neuronom Layer2 skok około (6\,\mathrm{mV}). Około 17 idealnie synchronicznych spike'ów Layer1 daje w przybliżeniu (102\,\mathrm{mV}), czyli wystarcza do przeniesienia neuronu od (-70) do progu bez uwzględniania dynamiki własnej. Mniejsza liczba może zadziałać, jeżeli neuron jest już zdepolaryzowany; większa asynchroniczna liczba może wygasnąć przez relaksację.

Jeśli Layer2 RS wystrzeli synchronicznie, typowy neuron Layer3 dostaje około (60\,\mathrm{mV}). Nie zawsze przekracza próg natychmiast, ale przenosi (v) do obszaru, gdzie składnik kwadratowy może doprowadzić do spike'u w następnym kroku. Oczekiwanym podpisem są zatem opóźnione o 1–kilka ms fale Layer1→Layer2→Layer3.

### Reżim nasycony

Przy silnej synchronii pełna projekcja Layer1→Layer2 daje setki jednostek skoku napięcia. Reset ogranicza odpowiedź do jednego spike'u na neuron i krok, przez co obserwacja samej liczby spike'ów nie pokazuje, jak daleko ponad próg znalazł się układ. Nasycenie należy mierzyć także frakcją kroków z dużym udziałem populacji, rozkładem (v) przed resetem lub marginesem wejścia, nie tylko średnim firing rate.

## 8. Krytyczność: czego można i nie można obecnie stwierdzić

Nie ma podstaw do nazwania bieżącej sieci krytyczną. Krytyczność wymaga co najmniej zgodności kilku niezależnych sygnałów:

1. efektywny parametr rozgałęzienia bliski 1;
2. szerokie, stabilne względem progu i binowania rozkłady rozmiaru oraz czasu lawin;
3. zgodność relacji wykładników i collapse kształtu lawin;
4. wzrost zakresu dynamicznego/podatności bez trwałej saturacji;
5. najlepiej analiza skalowania z rozmiarem sieci.

Surowa macierz wag (W) nie wystarcza. Dla linearyzacji odpowiednia jest raczej macierz efektywna

\[
W_{\mathrm{eff}}=G W,
\]

gdzie (G=\operatorname{diag}(g_i)) zawiera lokalne podatności neuronów zależne od (v,u,I), historii resetów i firing rate. Feed-forward może mieć ogromną normę, nie zwiększając promienia spektralnego części rekurencyjnej, ale może powodować bardzo duże przejściowe wzmocnienie i synchronizację.

Zalecany estymator aktywności w binie (t):

\[
A_t=\sum_i s_i(t).
\]

Prosty iloraz

\[
\hat m=\frac{\sum_t A_{t+1}}{\sum_t A_t}
\]

jest obciążony przez stały napęd sensoryczny. Należy użyć regresji wielokrokowej lub protokołu z krótkim bodźcem i okresem bez zewnętrznej „imigracji”. Pomiary trzeba wykonywać także osobno dla E/I i przejść między warstwami, np. (m_{L1\to L2}), (m_{L2\to L3}) oraz lokalnego (m_{Lk\to Lk}).

## 9. STDP, dopamina i długookresowa stabilność

Ślady pre- i postsynaptyczne mają (	au_+=	au_-=5\,\mathrm{ms}). Przy spike'u postsynaptycznym eligibility synapsy rośnie o

\[
A_+x_{pre},\qquad A_+=0.01,
\]

a przy spike'u presynaptycznym maleje o

\[
A_-x_{post},\qquad A_-=0.012.
\]

Eligibility zanika z (	au_c=1000\,\mathrm{ms}), a waga zmienia się według

\[
\dot w=e(t)\,[D(t)-B(t)].
\]

Dla nieskorelowanych procesów i równych stałych czasowych okno ma ujemne pole:

\[
A_+\tau_+-A_-\tau_- = 0.05-0.06=-0.01,
\]

czyli samo eligibility jest lekko przesunięte ku depresji. Faktyczny znak zmiany zależy jednak od czasu nagrody i sygnału dopaminowego.

Nagroda dodaje (5) do (D). Dopamina zanika z (	au_D=20\,\mathrm{ms}), a baza śledzi ją z (	au_B=200\,\mathrm{ms}). W przybliżeniu ciągłym po izolowanej nagrodzie

\[
D-B=5\left(
\frac{200}{180}e^{-t/20}
-\frac{20}{180}e^{-t/200}
\right).
\]

Sygnał zmienia znak po około (51\,\mathrm{ms}): najpierw jest dodatni, potem ma długi ujemny ogon. Jego niezważone pole w czasie wynosi zero. Ponieważ eligibility zanika wolniej i stale powstają nowe ślady, dodatnia i ujemna faza nie muszą się skasować. Interpretacja „nagroda zawsze wzmacnia niedawne synapsy” nie jest zatem poprawna bez analizy czasowej.

Dalsze ograniczenia stabilności:

- wagi dodatnie są ograniczone do ([0,50]), ale nie są normalizowane względem liczby wejść;
- wagi ujemne nie zmieniają się wcale;
- synapsy o ujemnej wartości wylosowane z normalnego rozkładu pozostają na zawsze ujemne;
- nie ma plastyczności hamującej, regulacji firing rate ani skalowania homeostatycznego;
- jedna globalna dopamina moduluje wszystkie dodatnie synapsy z niezerowym eligibility.

W konsekwencji rozkład wag może polaryzować się ku 0 i 50, a pełna projekcja feed-forward może z czasem zmienić wzmocnienie o rząd wielkości. Średnia wszystkich wag nie jest wystarczającą diagnostyką; trzeba śledzić kwantyle osobno dla każdej projekcji oraz frakcje wag przy 0 i 50.

## 10. Skalowanie prądów, wag, rozmiaru i kroku czasu

### 10.1. Skalowanie z liczbą wejść (K)

Dla asynchronicznego wejścia średnia suma synaptyczna skaluje się jak

\[
\mu_{syn}\propto Kwr.
\]

Jeśli celem jest zachowanie średniego napędu bez subtelnego balansu, należy przy zwiększaniu (K) skalować (w\propto K^{-1}). W klasycznym reżimie zbalansowanym używa się często (w\propto K^{-1/2}), ale wtedy dodatnie i ujemne średnie muszą się wzajemnie znosić do rzędu (\sqrt K).

Obecna konfiguracja nie zachowuje skali przy powiększaniu sieci:

- lokalne E ma stałe (p=0.1), więc (K_E\propto N_E);
- lokalne I ma stały stopień wyjściowy 15; przy zachowaniu proporcji E/I średni stopień wejściowy I pozostaje około stały;
- Layer1→Layer2 jest all-to-all, więc (K_{FF}=N_{L1,E});
- Layer2→Layer3 ma stałe (p=0.25), więc również (K_{FF}\propto N_{L2,E}).

Przy zwiększeniu liczby neuronów pobudzenie rośnie więc szybciej niż hamowanie, o ile wagi lub stopnie nie zostaną przeskalowane.

### 10.2. Skalowanie sensora z liczbą kanałów

Ponieważ (sigma\propto1/N_{channels}), a odstęp preferencji również jest proporcjonalny do (1/N_{channels}), suma odpowiedzi populacji na pojedynczy obiekt jest w przybliżeniu

\[
\sum_i I_i\approx I_{max}\sqrt{2\pi}\frac{\sigma}{\Delta\theta}
\approx3.7 I_{max},
\]

niemal niezależnie od liczby kanałów. To przybliżenie zachowuje całkowity napęd populacji, ale nie średni napęd jednego neuronu. Jeżeli celem ma być stały średni prąd na neuron przy rosnącym (N), trzeba zmienić skalę amplitudy lub utrzymać fizyczną szerokość pól zamiast kurczyć ją jak (1/N).

### 10.3. Skalowanie z (dt)

- Dla prądu zewnętrznego reprezentującego ciągłe (I(t)) wartość (I) powinna pozostać stała, ponieważ kod mnoży ją przez (dt).
- Waga reprezentuje skok napięcia wywołany spike'iem, więc przy takiej interpretacji nie powinna być mnożona przez (dt).
- `TONIC_NOISE` losuje nową wartość prądu w każdym kroku. Jeśli ma przybliżać biały szum o stałej dyfuzji napięcia, jego `noise_std` powinno skalować się jak (dt^{-1/2}).
- Opóźnienie synaptyczne wynosi jeden krok, więc zmiana (dt) zmienia również fizyczne opóźnienie.
- Zmiana `speedMultiplier` nie zmienia (dt); modyfikuje tylko tempo ścienne symulacji, zatem nie powinna zmieniać dynamiki w czasie symulowanym.

## 11. Firing rates i ograniczenia obecnej diagnostyki

`meanFiringRateHz` jest liczony jako

\[
r_{global}=\frac{\text{spike'i z okna}}{1\,\mathrm{s}\cdot300}.
\]

To jedna liczba dla wszystkich warstw i typów. Może maskować skrajne stany. Jeśli aktywne są wyłącznie 80 neuronów Layer3 RS, to ich rzeczywisty średni rate jest (300/80=3.75) razy większy od raportowanego globalnego rate.

Dodatkowe problemy:

- w pierwszej sekundzie mianownik nadal wynosi pełne (1\,\mathrm{s}), więc wynik jest zaniżony proporcjonalnie do czasu od startu;
- rekord dokładnie na lewej granicy okna pozostaje w historii (`< cutoff`, nie `<=`), co może dać około 1001 binów w idealnym przypadku 1 ms;
- firing rate nie rozróżnia tonicznego strzelania od synchronicznych burstów;
- średnia waga miesza dodatnie i ujemne synapsy oraz projekcje o zupełnie różnych skalach.

Minimalny zestaw diagnostyczny do analizy dynamiki:

1. firing rate dla sześciu populacji: L1/L2/L3 × RS/FS;
2. mediany i kwantyle rate per neuron, nie tylko średnia;
3. udział aktywnych neuronów i maksymalna liczba spike'ów w binie 1 ms;
4. CV odstępów między spike'ami oraz Fano factor w oknach 10, 50 i 100 ms;
5. korelacje E–E, E–I i między warstwami z lagami 0–10 ms;
6. rozkład rozmiaru i czasu lawin oraz estymator (m);
7. statystyki wag osobno dla każdej reguły: średnia, odchylenie, kwantyle, frakcja (w=0), (w=50), (w<0).

## 12. Priorytetowy program eksperymentów

### A. Bazowa powtarzalność

Uruchomić co najmniej 20 par seedów topologii i świata. Produkcyjny `SimulationEngine` używa obecnie niezaseedowanego loadera i świata, mimo że klasy mają konstruktory z seedem. Raportować średnią i przedziały międzyseedowe, nie pojedynczy przebieg.

### B. Rozdzielenie źródeł niestabilności

W kolejnych warunkach mierzyć sześć firing rates, synchronię i branching ratio:

1. (I=0), brak bodźców — test stanu absorpcyjnego;
2. stały prąd pojedynczych neuronów — weryfikacja krzywych (f-I);
3. vision bez połączeń rekurencyjnych — charakterystyka napędu;
4. lokalne E/I bez feed-forward — stabilność każdej warstwy;
5. feed-forward bez lokalnych pętli — przejściowe wzmocnienie;
6. pełna sieć bez nagród, następnie z nagrodami.

### C. Skan wzmocnienia

Wprowadzić w analizie trzy niezależne mnożniki:

\[
g_{localE},\qquad g_I,\qquad g_{FF}.
\]

Najpierw skanować (g_{FF}), ponieważ średnia waga 6 na 80 wejściach dominuje nad pozostałymi składnikami. Dla każdego punktu mierzyć:

- (r_E,r_I) per warstwa;
- (P(A_t=0)), (P(A_t/N>0.5)) i maksimum (A_t);
- efektywny (m) i czas zaniku odpowiedzi na krótki bodziec;
- ruch i pokarm dopiero po scharakteryzowaniu samej sieci.

### D. Test balansu

Porównać zwiększanie (|w_I|) ze zwiększaniem stopnia I. Sama zmiana (-2\to-4) zwiększy amplitudę pojedynczego IPSP, lecz nie naprawi braku I→FS ani odmiennego skalowania stopni przy zmianie (N). Kluczowym wskaźnikiem jest prąd warunkowy na spike:

\[
\langle I_E\mid spike\rangle,
\qquad
\langle I_I\mid spike\rangle,
\]

oraz ich opóźnienie, nie globalna suma wag.

### E. Stabilność plastyczności

Po okresie burn-in podać pojedynczą nagrodę i śledzić przez co najmniej (2\,\mathrm{s}): (D), (B), (D-B), eligibility i (Delta w) per projekcja. Następnie powtarzać nagrody i sprawdzić, czy rosną frakcje wag przy 0/50 oraz czy firing rate ma dryf. Bez tego nie można stwierdzić, że STDP poprawia zachowanie zamiast przesuwać sieć ku ciszy lub saturacji.

## 13. Cross-review i punkty do uzgodnienia

### Raport Volty / wcześniejszy raport zbiorczy

Potwierdzam wyliczenie oczekiwanej liczby synaps (13276) i rozpoznanie błędu `allow_autapses`. Doprecyzowania:

- wartość 480 dla Layer2 jest sumą skoków przy jednoczesnym spike'u wszystkich 80 źródeł, nie średnim napływem w zwykłej jednostce czasu;
- brak `TONIC_NOISE` nie oznacza typowej ciszy, ponieważ świat startuje ze 100 obiektami, a vision niemal zawsze przekracza próg części kanałów;
- „pobudzające” rozkłady normalne generują średnio około 13.5 ujemnej krawędzi feed-forward;
- globalny bilans krawędzi jest mniej informatywny niż konwergencja i bilans warunkowy per cel.

### Hooke i Poincaré

Raporty i pierwsze trzy szablony obu matematyków zostały przeczytane po zapisie wersji bazowej.

Z raportu Hooke'a przyjmuję dokładniejsze wyniki dla dyskretnego DA-STDP: całkowity mnożnik izolowanego eligibility obecnego przy nagrodzie wynosi około 16.80 dla kroku 1 ms, a eligibility utworzone już około 4 ms po nagrodzie ma ujemny całkowity wpływ. Wzmacnia to wniosek, że średnia zmiana wagi bez rozdzielenia czasu i projekcji nie jest miernikiem uczenia. Jego szablony 001–003 testują jawne akcje oraz zgodny/losowy/antyzgodny routing; nie dublują skanu gainu Meitner. Stałe seedy `TONIC_NOISE` zapewniają identyczny przebieg szumu między runami, ale nie stanowią niezależnych realizacji szumu — wynik trzeba interpretować jako warunkowy względem jednego śladu.

Z raportu Poincarégo przyjmuję krytykę utraty topografii przez all-to-all oraz plan jawnych populacji akcji. Jego szablony 001–003 testują routing topograficzny, głód Forward i ruch po łuku, podczas gdy seria Meitner celowo zachowuje baseline topology i izoluje wyłącznie gain. Jedna korekta liczbowa: dla legacy `POPULATION_DRIVE`, `speed_per_spike=0.1` i `turn_factor=0.1` kod daje

\[
\Delta\theta=(0.1L-0.1R)0.1=0.01(L-R),
\]

a nie (0.1(L-R)). Maksymalny obrót przy różnicy 40 spike'ów wynosi (0.4) rad, nie 4 rad. Nie dotyczy to jego nowych jawnych wyjść, gdzie `radians_per_spike=0.02` jest stosowane bez dodatkowego mnożnika.

Narzędzie `send_input` nie było udostępnione w tej sesji mimo przekazanych identyfikatorów agentów. Cross-review będzie kontynuowany poprzez ich pliki i komunikację bezpośrednią, gdy kanał stanie się dostępny.

## 14. Hipotezy falsyfikowalne na następną iterację

1. W większości seedów początkowy vision aktywuje co najmniej kilka neuronów L1 RS tonicznie; całkowicie cichy przebieg będzie rzadki.
2. Layer2 będzie miał większą synchronię i bardziej burstowy rozkład (A_t) niż Layer1 z powodu pełnego wspólnego wejścia.
3. Globalny firing rate będzie ukrywał znacznie wyższe rates FS oraz epizodyczne rates Layer2/Layer3.
4. Zmniejszanie (g_{FF}) przesunie układ z saturacji przez wąski obszar odpowiedzi przejściowych do braku propagacji; położenie granicy będzie silnie zależeć od synchronii L1.
5. Powtarzane nagrody bez homeostazy zwiększą wariancję dodatnich wag i udział wag na granicach 0/50, nawet jeśli średnia wszystkich wag zmieni się niewiele.
6. Krytyczny branching ratio, jeśli wystąpi, nie będzie stabilny w czasie przy aktywnej dopaminie i zmieniającym się rozkładzie bodźców.

## 15. Seria konfiguracji Meitner 001–006

Wszystkie pliki są kompletnymi konfiguracjami i zostały załadowane przez aplikację oraz uruchomione przez endpoint benchmarkowy bez modyfikacji głównego `SNNConfig.yaml`.

| plik | (\mu_{L1\to L2}) | (\sigma_{L1\to L2}) | (\mu_{L2\to L3}) | rola |
|---|---:|---:|---:|---|
| `001-slabe-przejscie-l1-l2.yaml` | 0.6 | 0.2 | 3.0 | dolny punkt skanu, około 34 spike'i do separatrix |
| `002-graniczne-przejscie-l1-l2.yaml` | 1.2 | 0.4 | 3.0 | około 17 spike'ów |
| `003-mocne-przejscie-l1-l2.yaml` | 2.4 | 0.8 | 3.0 | około 9 spike'ów |
| `004-bisekcja-l1-l2-3p6.yaml` | 3.6 | 1.2 | 3.0 | dolna bisekcja po braku ruchu 001–003 |
| `005-bisekcja-l1-l2-4p8.yaml` | 4.8 | 1.6 | 3.0 | górna bisekcja, około 5 spike'ów |
| `006-kompensacja-l2-l3.yaml` | 4.8 | 1.6 | 3.75 | próba odzyskania motor throughput bez wzmacniania L2 |

W 001–005 zmienia się tylko rozkład wag pełnej projekcji Layer1→Layer2. Średnia i odchylenie są skalowane tym samym czynnikiem, więc współczynnik zmienności pozostaje (1/3), taki jak w baseline (N(6,2^2)). Wariant 006 zmienia względem 005 tylko Layer2→Layer3 o czynnik 1.25.

### 15.1. Pilot walidacyjny 10 s × 5 seedów

Parametry: `durationMs=10000`, `burnInMs=2000`, `stepMs=1`, `repeats=5`, `baseSeed=104729`.

| wariant | średnie evaluation rewards | końcowy rate [Hz] | średnia droga | runy z drogą 0 |
|---|---:|---:|---:|---:|
| baseline | 1.6 | 3.114 | 144.17 | 0/5 |
| 001 | 0.0 | 0.743 | 0.00 | 5/5 |
| 002 | 0.0 | 0.757 | 0.00 | 5/5 |
| 003 | 0.0 | 0.773 | 0.03 | 4/5 |
| 004 | 0.2 | 1.319 | 16.32 | 1/5 |
| 005 | 0.4 | 2.416 | 76.88 | 0/5 |

Wynik jest zgodny z hipotezą 3 z sekcji 14: globalny firing pozostaje niezerowy nawet wtedy, gdy Layer3 nie wywołuje żadnego ruchu. Wartości 0.6–2.4 są za małe dla praktycznej propagacji w tej topologii. Granica ruchu leży w tym pilocie między 3.6 i 4.8, znacznie wyżej niż oszacowanie oparte wyłącznie na pojedynczym idealnie synchronicznym impulsie. Przyczyną są niepełna synchronia L1, reset/adaptacja i konieczność przejścia przez dwa stopnie do Layer3.

### 15.2. Dłuższy screening 30 s × 10 seedów

Parametry: `durationMs=30000`, `burnInMs=5000`, `stepMs=1`, `repeats=10`, `baseSeed=104729`. To nadal screening, nie test potwierdzający Poincarégo 180 s × 16/30 seedów.

| wariant | rewards mean ± SD | trend | final rate [Hz] | (\Delta\bar w) | droga |
|---|---:|---:|---:|---:|---:|
| baseline | (2.4\pm2.011) | -0.6 | 1.184 | +0.060448 | 218.840 |
| 004 | (0.3\pm0.483) | -0.1 | 1.145 | -0.004780 | 47.415 |
| 005 | (2.3\pm2.359) | +0.1 | 1.169 | +0.055924 | 171.980 |
| 006 | (2.1\pm2.183) | -0.7 | 0.685 | +0.050058 | 174.055 |

Sparowane różnice rewards względem baseline:

| wariant | średnia różnica | mediana różnicy | interpretacja |
|---|---:|---:|---|
| 004 | -2.1 | -2.5 | jednoznacznie odrzucony jako za słaby |
| 005 | -0.1 | -0.5 | brak przewagi; średnią podtrzymuje pojedynczy seed +5 |
| 006 | -0.3 | 0.0 | brak przewagi i brak odzyskania drogi |

Surowy iloraz sumaryczny rewards na 1000 jednostek drogi wynosi około 10.97 dla baseline, 6.33 dla 004, 13.37 dla 005 i 12.07 dla 006. Nie wolno traktować większego ilorazu 005/006 jako zwycięstwa: jest to iloraz dwóch agregatów z małą liczbą zdarzeń, a bezwzględna liczba nagród i sparowana mediana nie poprawiły się.

### 15.3. Decyzja po screeningu

- 001–003: odrzucone jako podprogowe dla ścieżki aż do motoru w bieżącej topologii.
- 004: odrzucony; 21.7% drogi baseline i silny spadek nagród.
- 005: zachowany wyłącznie jako wariant graniczny do ewentualnego dłuższego testu efektywności, nie jako zwycięzca. Ogranicza drogę do 78.6% baseline bez poprawy sparowanych nagród.
- 006: odrzucona hipoteza prostej kompensacji drugiego stopnia; podniesienie L2→L3 nie odzyskało drogi ani nagród.
- baseline pozostaje punktem odniesienia dla tej rodziny all-to-all. Następna wartościowa seria nie powinna dalej zagęszczać jednowymiarowego skanu, lecz testować osobno synchronię/local E-I albo przejść do topograficznych architektur Hooke'a/Poincarégo.

### 15.4. Ograniczenia uzyskanych wyników

1. `finalFiringRateHz` pochodzi tylko z ostatniej sekundy i całej sieci; podobne średnie 004/005/baseline nie oznaczają podobnej dynamiki warstw.
2. Nagrody przed burn-in nadal wstrzykują dopaminę i zmieniają wagi, mimo że nie są liczone jako `evaluationRewards`.
3. `averageWeightDelta` miesza projekcje; dodatnia wartość baseline/005/006 nie dowodzi korzystnego credit assignment.
4. Dziesięć seedów i 25 s ewaluacji daje zbyt mało zdarzeń do formalnej selekcji.
5. Nie ma jeszcze miar synchronii, branching ratio ani saturacji, więc hipoteza o ograniczeniu burstów przez 005 pozostaje niezweryfikowana.
