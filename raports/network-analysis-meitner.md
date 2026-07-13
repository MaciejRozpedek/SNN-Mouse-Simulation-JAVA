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

## 16. Iteracja 2 — lokalny balans E/I, seria 007–009

### 16.1. Aktualny cross-review przed projektem

Ponownie przeczytano pełne raporty oraz wszystkie szablony Hooke'a i Poincarégo.

Hooke przeprowadził screening 60 s × 8 sparowanych seedów dla jawnych akcji. Jego wariant zgodnego routingu 002 miał średnią różnicę rewards (+0.25) względem losowego 001 i (+0.50) względem antyzgodnego 003, ale przy (n=8) przedziały obejmują zero. Szczególnie informacyjny jest negatywny routing 003: przebył o około 28% większą drogę od 002, a zdobył mniej nagród. Potwierdza to, że sama droga nie jest substytutem jakości polityki. Seria Hooke'a zmienia routing przy stałym lokalnym E/I, więc nie dubluje poniższego testu.

Poincaré zaktualizował rdzeń topograficzny: `AssociationBias=3.8`, `ActionBias=3.5`, one-to-one o wadze 5 i wejścia akcji o wadze 4. Pierwsza wartość nie jest bezpiecznie podprogowa dla implementowanego kroku: w analizie Meitner lokalna granica RS to około 3.7984, a bezpośrednia iteracja daje około 5.4 Hz dla stałego (I=3.8). Warstwa asocjacyjna może więc strzelać autonomicznie bez impulsu sensorycznego; należy to traktować jako jawny napęd, nie tylko „bias ułatwiający propagację”. Jego seria bada topografię, Hunger i kolaterale Forward, również nie dublując lokalnego skanu I.

Wynik iteracji 1 Meitner był punktem startowym: obniżanie pełnej projekcji L1→L2 poniżej mean 4.8 silnie ograniczało motor throughput, a wariant 005 nie pokonał baseline. Dlatego w iteracji 2 wszystkie feed-forward, prądy i wyjścia wróciły dokładnie do baseline; zmieniano tylko lokalne FS→RS.

### 16.2. Czysta rodzina parametru

Seria składa się z trzech pełnych YAML-i:

| plik | (w_I) | lokalny moduł E | lokalny moduł I | (r_I/r_E) dla równowagi |
|---|---:|---:|---:|---:|
| baseline | -2 | 11.85 | 7.50 | 1.58 |
| `007-lokalne-hamowanie-minus3.yaml` | -3 | 11.85 | 11.25 | 1.053 |
| `008-lokalne-hamowanie-minus4.yaml` | -4 | 11.85 | 15.00 | 0.790 |
| `009-lokalne-hamowanie-minus6.yaml` | -6 | 11.85 | 22.50 | 0.527 |

Rachunek używa oczekiwanego lokalnego in-degree RS: (7.9) wejścia E o średniej wadze 1.5 oraz (3.75) wejścia I. Zmieniono wyłącznie wartość `fixed` istniejącej reguły FS→RS. Liczba neuronów i krawędzi, cele synaps, wszystkie dodatnie wagi, realizacja losowej topologii, vision, feed-forward, output i parametry DA-STDP pozostały identyczne.

Jest to wyjątkowo ścisłe sparowanie: generator wagi `fixed` nie pobiera losowej wartości przy `generate()`, więc zmiana (-2\to-3/-4/-6) nie przesuwa strumienia RNG. Dla danego seeda kandydaci mają te same losowe krawędzie i te same wagi E co baseline; różni się tylko amplituda 900 lokalnych synaps I.

Hipoteza dynamiczna: pierwszy wspólny volley feed-forward nie jest blokowany, ponieważ hamowanie dociera dopiero po spike'u FS z opóźnieniem jednego kroku. Większe (|w_I|) powinno przede wszystkim skracać aktywność wtórną RS i korelacje lokalne. Ponieważ I nie jest plastyczne, ewentualna zmiana `averageWeightDelta` pochodzi pośrednio ze zmienionych spike'ów/eligibility dodatnich synaps.

### 16.3. Protokół nowego runnera

Użyto lokalnego runnera `.local-snn-experiments/runner.py`, który dla każdego wariantu zachowuje kompletny YAML, uruchamia osobny JVM, wywołuje `POST /api/benchmark` i archiwizuje pełny `benchmark.json`.

Parametry:

- `durationMs=60000`, `burnInMs=10000`, `stepMs=1`;
- `repeats=12`, seedy `104729…104740`;
- 48 runów, łącznie (2.88\cdot10^6) kroków;
- bramka firing: 0.1–50 Hz w co najmniej 80% runów;
- manifest: `.local-snn-experiments/meitner-ei-screen.yaml`;
- surowy batch: `.local-snn-experiments/results/20260713-034742/`.

Wszystkie trzy YAML-e zostały wcześniej wygenerowane przez tryb `--generate-only`, a następnie semantycznie załadowane przez osobne JVM-y właściwego benchmarku. Główny `SNNConfig.yaml` nie był zmieniany.

### 16.4. Wyniki agregatów

| wariant | rewards mean ± SD | trend | final Hz | stabilne runy | (Delta\bar w) | droga | rewards/1000 drogi |
|---|---:|---:|---:|---:|---:|---:|---:|
| baseline | (2.417\pm2.234) | -1.917 | 0.220 | 9/12 = 75% | +0.062161 | 173.508 | 13.93 |
| 007, (w_I=-3) | (2.000\pm2.412) | -1.500 | 0.539 | 10/12 = 83% | +0.057146 | 168.817 | 11.85 |
| 008, (w_I=-4) | (1.417\pm1.084) | -1.083 | 0.206 | 9/12 = 75% | +0.016948 | 176.412 | 8.03 |
| 009, (w_I=-6) | (1.500\pm1.977) | -1.500 | 0.206 | 8/12 = 67% | +0.028762 | 165.329 | 9.07 |

Niskie końcowe okno (<0.1) Hz wystąpiło odpowiednio w 3, 2, 3 i 4 runach. Jeden run 007 miał drogę równą zero; pozostałe warianty i baseline nie miały zerowej drogi. Baseline sam nie przechodzi przyjętej absolutnej bramki 80%, więc `finalFiringRateHz` jest niestabilnym i bardzo słabym wskaźnikiem stanu całego przebiegu.

Droga względem baseline wynosiła 97.3% dla 007, 101.7% dla 008 i 95.3% dla 009. Silniejsze I nie wyłączyło zatem ogólnie ruchu, lecz obniżyło efektywność nagród na drogę o około 15%, 42% i 35%.

### 16.5. Sparowane różnice seed-po-seedzie

Różnice rewards zapisano jako kandydat minus baseline w porządku seedów `104729…104740`:

- 007: ([1,1,0,4,-2,-1,0,-5,4,0,0,-7]);
- 008: ([1,-1,0,1,-3,0,0,-4,-2,0,0,-4]);
- 009: ([0,-1,0,5,0,-1,-1,-5,-2,0,-1,-5]).

| wariant | mean (Delta R) | mediana | SD par | przybl. 95% CI mean | exact sign-flip (p) | mean (Delta path) | mediana (Delta path) |
|---|---:|---:|---:|---:|---:|---:|---:|
| 007 | -0.417 | 0 | 3.175 | [-2.434, 1.601] | 0.7188 | -4.692 | +2.100 |
| 008 | -1.000 | 0 | 1.809 | [-2.149, 0.149] | 0.1250 | +2.904 | +2.050 |
| 009 | -0.917 | -1 | 2.575 | [-2.553, 0.719] | 0.2656 | -8.179 | -13.175 |

Przedziały są klasycznymi przedziałami t dla średniej 12 sparowanych różnic; (p) pochodzi z pełnej enumeracji (2^{12}=4096) zmian znaków. Screening nie ma mocy do dowodu małego efektu, ale żaden punkt nie ma nawet dodatniej średniej różnicy. Ujemny efekt 008 jest najbardziej spójny, choć nadal nie przekracza progu 0.05.

### 16.6. Wpływ na firing i plastyczność

Zmiana średniego końcowego firing względem baseline wyniosła:

- 007: (+0.319) Hz;
- 008: (-0.014) Hz;
- 009: (-0.014) Hz.

Nie ma monotonicznego spadku globalnego final rate. To nie falsyfikuje lokalnego hamowania RS: FS nie dostaje I→I, może nadal strzelać szybko, a globalna metryka miesza RS i FS wszystkich warstw oraz obejmuje tylko ostatnią sekundę. Wynik 007 pokazuje wręcz, że większe I może zmienić trajektorię i końcowy stan tak, iż globalna średnia rośnie.

Średnia zmiana wag dodatnich/mieszanych spadła względem baseline o około:

- 007: 0.005015, czyli 8.1%;
- 008: 0.045213, czyli 72.7%;
- 009: 0.033399, czyli 53.7%.

Najsilniejszy spadek dla 008 wraz z podobną drogą sugeruje, że lokalne I może silnie ograniczać korelacje generujące eligibility albo timing względem nagród. Nie jest to jednak bezpośredni pomiar synchronii ani dowód korzystnej stabilizacji: 008 zdobywa mniej nagród na podobnej drodze.

### 16.7. Decyzje

- **007 odrzucony jako kandydat behawioralny.** Przechodzi bramkę firing 10/12 i zachowuje 97.3% drogi, ale nie ma reward gain, obniża rewards/1000 drogi i nie daje oczekiwanego spadku globalnego final rate. Może pozostać technicznym punktem prawie równego lokalnego E/I.
- **008 odrzucony.** Zachowuje drogę, ale traci średnio 1 nagrodę, ma tylko 75% stabilnych końcowych okien i obniża efektywność drogi o około 42%. Mechanistycznie jest cennym przykładem silnego ograniczenia dryfu wag bez poprawy zachowania.
- **009 odrzucony jako over-inhibited.** Ma medianę (Delta R=-1), tylko 67% stabilnych okien i gorszą efektywność drogi. Wyznacza górną granicę tej rodziny.
- **Żaden wariant 007–009 nie przechodzi do screeningu potwierdzającego.** Dalsza interpolacja (-2.5) nie jest uzasadniona obecnymi danymi: 007 już nie wykazał korzyści, a bez bezpośredniej miary burstów optymalizowalibyśmy szum końcowego firing rate.

### 16.8. Co zostało, a czego nie zostało wykazane

Wykazano, że zwiększenie amplitudy istniejącego lokalnego FS→RS do (-3,-4,-6) nie poprawia nagród w 12 sparowanych runach i może silnie ograniczyć średnią zmianę wag bez dużego spadku drogi. Nie wykazano redukcji synchronii. Endpoint nie eksportuje szeregu (A_t), rates per populacja, czasu burstu ani lagów E-I; `finalFiringRateHz` nie może ich zastąpić.

Następny czysty eksperyment synchronii powinien zostać wykonany dopiero po dodaniu diagnostyki (A_t) lub kontrolowanego protokołu impulsowego. Wtedy można zmieniać jedną rodzinę, np. lokalne (p_E) przy kompensacji wagi zachowującej średni (K_Ew_E), i rozdzielić wpływ średniej od wariancji oraz wspólnego inputu. Bez tych metryk nie należy stroić E/I na podstawie samej liczby przypadkowych kolizji.

### 16.9. Cross-review aktualizacji powstałych podczas screeningu

W czasie wykonywania 48 runów Hooke dopisał 004–006, a Poincaré 004–005 i wyniki własnego 10-seedowego screeningu. Wszystkie nowe YAML-e i aktualizacje raportów zostały przeczytane przed zamknięciem iteracji.

Hooke zwiększa w 004–006 wyłącznie wagę rzadkiego mostu sensoryczno-motorycznego z 2 do 3, zachowując losowy/zgodny/antyzgodny routing. W kontrolowanym assay celu odległego o 100 zgodny most (w=2) dawał poprawny znak skrętu 4/16, a (w=3) 16/16; większe wagi nie poprawiały wyniku. To ważne uzupełnienie progu Meitner: idealny rachunek (8\cdot2=16) mV przekracza teoretyczną lukę około 14.14 mV przy tonicznym (I=2), lecz w realnym układzie nie gwarantuje propagacji z powodu niepełnej synchronii i stanu neuronu. Jest to ten sam typ różnicy między progiem idealnego volley a granicą behawioralną, który ujawniła seria Meitner 001–005. Nowa seria Hooke'a pozostaje ortogonalna wobec 007–009, bo utrzymuje lokalne I na (-2.5) i bada gain mostu oraz znak routingu.

Poincaré raportuje screening 30 s × 10 seedów: baseline 2.4 nagrody, topograficzny 001 4.0, 002 z głodem 4.2 i łukowy 003 3.4. Wariant 002 ma względem baseline mean (+1.8), medianę (+1.5) i bilans 6/1/3 zwycięstwa/remisy/przegrane. Wewnętrznie 002−001 to jednak tylko mean (+0.2), mediana 0: większość poprawy pochodzi z rzadkiej topografii i jawnych akcji, nie z samego głodu. Jest to znacznie lepszy kierunek niż zwiększanie lokalnego I w pełnym mikserze baseline, gdzie wszystkie średnie (Delta R) były ujemne.

Nowy Poincaré 004 osłabia nieskuteczny boczny tor Forward, a 005 opóźnia głód awaryjny. Nie dublują one E/I Meitner i zgodnie z kontraktem nie powinny zastępować zaplanowanego długiego testu 001/002. Szczególnie trafne jest zalecenie taniego assay znaku/krzywizny przed dalszym rankingiem nagród: dla synchronii analogicznym warunkiem wejścia powinien być kontrolowany impuls i bezpośredni pomiar (A_t), zanim powstanie kolejna seria lokalnych wag.

Wspólny wniosek cross-review: wynik 007–009 nie uzasadnia „naprawiania” pełnej topologii samą amplitudą I. Rzadka, zgodna struktura sensoryczno-motoryczna daje obecnie silniejszy sygnał behawioralny, a lokalny E/I należy wrócić stroić dopiero na takiej strukturze i z diagnostyką populacyjną, nie na podstawie globalnego końcowego firing rate.

## 17. Główny long screen Poincaré — 180 s × 16 seedów

Wynik przekazany po zakończeniu głównego bloku: `durationMs=180000`, `burnInMs=30000`, `stepMs=1`, 16 sparowanych seedów `104729…104744`.

| wariant | rewards mean ± SD | trend | stable firing ratio | droga | mean (Delta R) vs baseline | mediana (Delta R) | dodatni (Delta R) |
|---|---:|---:|---:|---:|---:|---:|---:|
| baseline | (0.750\pm1.000) | -0.375 | 0.44 (7/16) | 69.09 | — | — | — |
| Poincaré 001 | (22.6875\pm4.701) | +2.9375 | 1.00 (16/16) | 1739.54 | +21.9375 | +21 | 16/16 |
| Poincaré 002 | (22.875\pm4.924) | +0.7500 | 1.00 (16/16) | 1959.38 | +22.1250 | +22 | 16/16 |

### 17.1. Siła wyniku topologii

Oba warianty topograficzne pokonują baseline na każdym z 16 seedów. Dwustronny dokładny test znaków dla 16/16 ma (p=2/2^{16}\approx3.05\cdot10^{-5}). Efekt jest nie tylko statystycznie jednoznaczny w tym bloku, ale również bardzo duży praktycznie:

- 001 zdobywa około 30.25 razy więcej nagród niż baseline;
- 002 zdobywa około 30.50 razy więcej;
- mediana sparowanej przewagi wynosi odpowiednio 21 i 22 nagrody;
- stabilność końcowego firing rośnie z 7/16 do 16/16.

To rozstrzyga wcześniejszą niepewność screeningu 30 s: rzadka topografia zachowująca znak kąta i jawne akcje są behawioralnie zdecydowanie lepsze od pełnego, nietopograficznego miksera baseline. Wynik jest również silniejszy niż jakikolwiek efekt skanów gainu Meitner 001–009.

### 17.2. Droga i efektywność

Surowe rewards na 1000 jednostek drogi:

| wariant | rewards/1000 drogi | względem baseline |
|---|---:|---:|
| baseline | 10.86 | — |
| Poincaré 001 | 13.04 | +20.1% |
| Poincaré 002 | 11.67 | +7.5% |

Ogromna przewaga nagród nie wynika wyłącznie z dłuższej drogi: 001 poprawia także efektywność drogi o około 20%. Jednocześnie ruch jest głównym kosztem 002. Względem 001 głód zwiększa średnią drogę o 219.84, czyli 12.6%, a nagrody tylko o 0.1875, czyli 0.83%. Efektywność 002 jest przez to około 10.5% niższa niż 001.

### 17.3. Czy Hunger w 002 pomaga?

Dla 002−001:

- mean (Delta R=+0.1875), mediana (+1);
- 10 seedów wygrywa 002, 6 wygrywa 001;
- dwustronny test znaków dla 10/6 daje (p\approx0.4545);
- trend jest wyraźnie słabszy: +0.75 wobec +2.9375;
- droga jest wyraźnie większa.

Nie ma więc wyraźnego dowodu, że Hunger zwiększa liczbę nagród. Najprostsza interpretacja jest zgodna z hipotezą użytkownika: głód zwiększa eksplorację i drogę, ale jego marginalny zysk nagród jest mały i niepewny. Przy obecnych danych 001 jest wariantem bardziej oszczędnym, natomiast 002 może ratować część zastojów kosztem nadmiarowego ruchu.

### 17.4. Czego long screen nadal nie dowodzi

Long screen dowodzi przewagi końcowego systemu/topologii, lecz nie izoluje uczenia. Warianty różnią się od baseline architekturą, napędami, wyjściami i liczbą synaps, a DA-STDP pozostaje aktywne przez cały przebieg. Dodatni trend 001 jest zgodny z uczeniem, ale może także wynikać z przejściowej dynamiki polityki, zmieniającej się geometrii świata lub dryfu wag niekontyngentnego z nagrodą.

Konieczna kontrola to `learningEnabled=true/false` dla tej samej topologii i tych samych początkowych wag. Czynnik nie powinien być kodowany przez duplikowanie YAML-i, jeśli jest parametrem runnera/endpointu; pełne YAML-e 001 i 002 muszą pozostać byte-for-byte identyczne między ramionami.

### 17.5. Następny kontrakt eksperymentalny

Minimalny układ czynnikowy:

| topologia | learningEnabled=true | learningEnabled=false |
|---|---|---|
| Poincaré 001 | DA-STDP aktywne | identyczna sieć, zamrożone wagi |
| Poincaré 002 | DA-STDP aktywne | identyczna sieć, zamrożone wagi |

W każdym ramieniu należy użyć identycznych seedów topologii i świata. Ponieważ `104729+` służyło już do konstrukcji i głównego screeningu, potwierdzenie efektu uczenia powinno użyć rozłącznej puli, np. `baseSeed=1000003`, najlepiej co najmniej 30 powtórzeń. Pierwszorzędowy kontrast:

\[
\delta_{learn}=
(R_{late}-R_{early})_{learning=true}
-(R_{late}-R_{early})_{learning=false}.
\]

Same końcowe nagrody `true-false` są pomocnicze, ponieważ zamrożona, dobrze zaprojektowana topologia może od początku działać bardzo dobrze. Trzeba raportować również:

1. rewards i drogę w równych oknach czasowych, nie tylko dwie połowy;
2. rewards/1000 drogi;
3. sparowane różnice per seed i 95% CI;
4. final firing oraz, gdy dostępne, firing per populacja;
5. (Delta w) per projekcja tylko w ramieniu uczącym;
6. ocenę po treningu z zamrożonymi wagami, jeśli runner będzie obsługiwał checkpoint/evaluation.

### 17.6. Wpływ na kolejne hipotezy i template'y Meitner

- Pełny mikser baseline nie będzie już rdzeniem kandydatów behawioralnych Meitner; pozostaje wyłącznie kontrolą historyczną.
- Kolejne testy E/I, synchronii lub skalowania powinny bazować na Poincaré 001 jako stabilnym rdzeniu i zmieniać jedną rodzinę parametrów naraz.
- Poincaré 002 nie jest domyślnym rdzeniem, dopóki kontrola nie pokaże wartości głodu ponad zwiększenie drogi.
- Każdy kolejny nagłówek YAML Meitner ma jawnie wskazywać, czy hipoteza dotyczy architektury, dynamiki czy uczenia, oraz wymagać porównania `learningEnabled=true/false`, gdy interpretacja obejmuje DA-STDP/credit assignment.
- Nie należy stroić nowych YAML-i na seedach `104729+`; są one zbiorem rozwojowym. Nowe hipotezy wymagają osobnego screeningu i późniejszego holdoutu.

Wynik nie blokuje implementacji kontroli `learningEnabled`; matematyczny kontrakt jest gotowy do użycia, gdy parametr pojawi się w headless runnerze/endpointcie.

## 18. Kontrola `learningEnabled=true/frozen` na archiwalnych Poincaré 001/002

Kontrolę wykonano na dokładnych archiwalnych YAML-ach użytych w long screenie: 180 s, burn-in 30 s, 16 sparowanych seedów `104729+`. W ramieniu frozen wagi pozostały niezmienione w 16/16 runów, co potwierdza działanie przełącznika na poziomie obserwowanej (Delta w=0).

### 18.1. Poincaré 001

| miara true − frozen | wynik |
|---|---:|
| frozen mean rewards | 18.625 |
| true mean rewards | 22.6875 |
| paired mean | +4.0625 |
| względna poprawa | +21.8% względem frozen |
| mediana par | +4.5 |
| dodatnie pary | 14/16 |
| bootstrap 95% CI mean | [+0.0625, +7.3125] |
| exact sign-flip, one-sided | 0.02779 |
| exact sign-flip, two-sided | 0.05557 |
| sparowane (d_z) | 0.53 |

Efekt jest kierunkowo spójny, praktycznie istotny i umiarkowany w skali standaryzowanej. Nie należy jednak przedstawiać go jeszcze jako niezależnie potwierdzonego dowodu uczenia:

1. seedy `104729+` były już używane do konstrukcji i selekcji architektury;
2. przedział bootstrap ledwo wyklucza zero, natomiast dokładny test dwustronny jest nieznacznie powyżej 0.05;
3. test jednostronny jest uzasadniony wcześniej postawioną hipotezą kierunkową, ale test holdout powinien raportować również wynik dwustronny.

Rozbieżność „CI powyżej zera, lecz two-sided (p=0.05557)” nie jest błędem: bootstrap percentylowy i dokładna randomizacja znaków mają inne rozkłady odniesienia oraz zachowanie przy małym, dyskretnym (n=16). Wniosek roboczy to umiarkowane, sugestywne evidence za korzyścią DA-STDP, wymagające replikacji.

### 18.2. Dekompozycja przewagi Poincaré 001

W long screenie baseline miał mean 0.75, frozen P001 18.625, a true P001 22.6875. Zatem:

\[
\Delta_{architecture}=18.625-0.75=17.875,
\]

\[
\Delta_{learning}=22.6875-18.625=4.0625.
\]

Spośród całkowitej przewagi true P001 nad baseline równej 21.9375 około 81.5% przypada liczbowo na zamrożoną architekturę/politykę początkową, a 18.5% na dodatkowy efekt aktywnego uczenia. To dekompozycja opisowa, nie addytywny model przyczynowy — uczenie działa w kontekście tej architektury — lecz jasno pokazuje, że większość sukcesu pochodzi z topograficznego prioru, a DA-STDP daje dalszą, potencjalnie wartościową poprawę.

### 18.3. Poincaré 002

| miara true − frozen | wynik |
|---|---:|
| paired mean | +1.6875 |
| względna poprawa | +8% |
| mediana par | +1 |
| dodatnie pary | 9/16 |
| bootstrap 95% CI mean | [-0.4375, +4.0000] |
| exact sign-flip, one-sided | 0.0947 |

Efekt P002 nie jest potwierdzony: CI obejmuje zero, tylko 9/16 par jest dodatnich, a nawet test jednostronny nie osiąga 0.05. W połączeniu z wynikiem long screenu — większa droga bez wyraźnej przewagi nagród nad 001 — wskazuje to, że Hunger może rozcieńczać credit assignment albo zastępować wyuczoną politykę stałym napędem eksploracyjnym. P002 pozostaje wartościową kontrolą wpływu napędu, ale nie jest głównym kandydatem do dowodu uczenia.

### 18.4. Cross-review względem kryteriów Hooke'a i Poincarégo

Kontrola spełnia dwa kluczowe postulaty Hooke'a: porównuje prawdziwą nagrodę i eligibility przy aktywnych wagach z identyczną zamrożoną architekturą oraz potwierdza brak zmian wag w frozen. Nadal nie zapewnia retencji po treningu ani rozdzielenia zmian wag per projekcja, więc dowód pełnego reward-contingent credit assignment nie jest kompletny.

Względem programu Poincarégo wynik potwierdza wybór 001 jako oszczędniejszego rdzenia. P002 nie pokazuje dodatkowej, powtarzalnej korzyści uczenia, mimo dodatniej mediany, i nie powinien zastępować 001 w niezależnym teście.

### 18.5. Niezależny test 30 seedów — kontrakt bez dostrajania

Uruchomiona główna ścieżka porównuje Poincaré 001 true/frozen przy:

- `durationMs=180000`, `burnInMs=30000`, `stepMs=1`;
- `repeats=30`, `baseSeed=1000003`;
- dokładnie tym samym archiwalnym YAML-u w obu ramionach;
- identycznych seedach topologii i świata w każdej parze.

Nie należy zmieniać konfiguracji, metryk ani puli seedów na podstawie bieżącego wyniku. Główną estymandą pozostaje sparowana różnica evaluation rewards true−frozen. Raport końcowy powinien zawierać mean, medianę, SD różnic, bootstrap 95% CI, dokładny test sign-flip jedno- i dwustronny, (d_z), liczbę par dodatnich/remisów/ujemnych oraz efektywność nagród na drogę.

Robocze kryterium potwierdzenia, ustalone przed wynikiem holdout:

1. mean i mediana (Delta R) są dodatnie;
2. dolna granica 95% CI jest (>0);
3. dokładny test dwustronny daje (p<0.05), z jednostronnym raportowanym pomocniczo;
4. efekt nie wynika wyłącznie z większej drogi i ma zgodny znak dla rewards/1000 drogi;
5. frozen zachowuje (Delta w=0) we wszystkich runach.

Jeśli CI i test randomizacyjny ponownie dadzą granicznie różne decyzje, należy raportować oba bez wybierania korzystniejszego i traktować wielkość efektu oraz zgodność seedów jako ważniejsze od binarnej etykiety. Test już trwa i analiza Meitner go nie blokuje.

### 18.6. Wpływ na przyszłe hipotezy

- Poincaré 001 staje się domyślnym rdzeniem testów uczenia i późniejszych, jednoczynnikowych zmian dynamiki.
- Każdy przyszły template Meitner dotyczący DA-STDP musi mieć frozen jako obowiązkowe ramię tego samego YAML-u, nie osobny wariant konfiguracji.
- P002/Hunger należy badać jako interakcję napędu z uczeniem, nie jako domyślną poprawę 001.
- Strojenie E/I lub synchronii powinno najpierw zachować potwierdzony efekt true−frozen P001, a dopiero potem optymalizować firing lub stabilność.

## 19. Niezależne potwierdzenie Poincaré 001 true/frozen — 30 unseen seeds

Protokół: dokładny archiwalny P001, 180 s, 30 niezależnych seedów `1000003+`, ramiona aktywnego uczenia i frozen sparowane po seedzie.

| miara | frozen | learning | learning − frozen |
|---|---:|---:|---:|
| mean rewards | 19.4000 | 21.4667 | +2.0667 |
| względna różnica rewards | — | — | +10.65% |
| mean reward trend | +0.733 | +2.400 | +1.667 |
| mean path | 1690.8 | 1744.5 | +53.7 (+3.18%) |
| zmiana wag | 0 w 30/30 | (>0) w 30/30 | zgodna z przełącznikiem |

Statystyki sparowanego efektu rewards:

| miara | wynik |
|---|---:|
| mean | +2.0667 |
| mediana | +1 |
| dodatnie / remisy / ujemne | 17 / 3 / 10 |
| bootstrap 95% CI mean | [+0.200, +3.933] |
| sparowane (d_z) | 0.388 |
| paired t, two-sided | (p=0.04235) |
| Wilcoxon, two-sided | (p=0.0632) |
| Wilcoxon, one-sided | (p=0.0316) |
| sign test | (p=0.248) |

### 19.1. Interpretacja replikacji

Efekt uczenia z puli rozwojowej wynosił +4.0625 nagrody, +21.8% i (d_z=0.53). Na niezależnej puli zmalał do +2.0667, +10.65% i (d_z=0.388). Amplituda spadła mniej więcej o połowę, ale kierunek, dodatnia średnia, dodatnia mediana i bootstrap CI powyżej zera zostały zreplikowane.

To umiarkowane potwierdzenie korzyści aktywnej plastyczności, nie efekt uniwersalny dla każdego seeda. Różne testy odpowiadają na różne pytania:

- paired t i bootstrap wykrywają dodatnią średnią wielkość efektu;
- Wilcoxon pyta o systematyczne przesunięcie rang i jest dwustronnie graniczny;
- sign test ignoruje amplitudy i widzi tylko 17 zwycięstw wobec 10 porażek po usunięciu remisów, co nie jest wystarczająco asymetryczne.

Wynik sugeruje, że korzyść jest częściowo niesiona przez kilka większych popraw, a nie przez niemal pewną małą poprawę każdego seeda. Należy raportować tę heterogeniczność, zamiast streszczać test jednym (p).

### 19.2. Efekt nie jest tylko skutkiem większej drogi

Learning zwiększa drogę o około 3.18%, a rewards o 10.65%. Agregat rewards na 1000 jednostek drogi wynosi w przybliżeniu:

\[
\eta_{frozen}=1000\frac{19.4}{1690.8}\approx11.47,
\]

\[
\eta_{learning}=1000\frac{21.4667}{1744.5}\approx12.31.
\]

Jest to wzrost efektywności drogi o około 7.3%. Aktywne uczenie nie tylko wydłuża trajektorię; średnio uzyskuje więcej nagród z jednostki ruchu. Do ścisłego wniosku potrzebne są jednak sparowane per-run różnice efektywności, nie wyłącznie iloraz agregatów.

Trend learning−frozen ma mean +1.667, lecz bootstrap CI [-0.667, +4.000] obejmuje zero. Nie potwierdzono więc osobno przyspieszania uczenia w drugiej połowie przebiegu. Głównym potwierdzonym efektem pozostaje całkowita liczba nagród.

### 19.3. Co potwierdza kontrola wag

Frozen ma (Delta w=0) w 30/30 runów, a learning (Delta w>0) w 30/30. Potwierdza to separację techniczną ramion i obecność plastyczności. Dodatnia średnia zmiana wag sama nie dowodzi poprawnego credit assignment, ale w połączeniu z dodatnim efektem behawioralnym na niezależnych seedach wspiera hipotezę, że aktywna DA-STDP wnosi umiarkowaną wartość ponad topograficzny prior.

Nadal brakuje testu retencji: wynik learning był mierzony podczas dalszego zmieniania wag. Lepsza polityka może wymagać ciągłej adaptacji albo być przejściowym skutkiem dynamiki wag. Następny eksperyment musi rozdzielić trening od zamrożonej ewaluacji.

## 20. Protokół fazowy `freezeLearningAtMs`

### 20.1. Rdzeń i zasada template'u

Używać byte-for-byte dokładnego archiwalnego `templates-poincare/001-topograficzne-akcje.yaml`. Nie tworzyć kopii YAML Meitner z inną nazwą: `learningEnabled` i `freezeLearningAtMs` są czynnikami wykonania, nie parametrami sieci. Manifest runnera ma archiwizować ten sam hash YAML-u w każdym ramieniu.

Każdy przyszły nagłówek template'u Meitner oparty na P001 powinien zawierać kontrakt: najpierw wykazać retencję true→frozen na niezmienionym rdzeniu, a dopiero potem testować jedną zmianę dynamiki.

### 20.2. Wykonalny protokół trzyramienny

Proponowane ramiona na identycznych parach seedów:

| ramię | 0–180 s | 180–300 s | rola |
|---|---|---|---|
| `always-frozen` | learning off | learning off | polityka początkowa/topograficzny prior |
| `learn-then-freeze` | learning on | learning off | retencja tego, czego nauczono się do 180 s |
| `continuous-learning` | learning on | learning on | kontrola wartości dalszej adaptacji online |

Parametry runnera:

- `durationMs=300000`;
- `freezeLearningAtMs=180000` dla learn-then-freeze;
- `postFreezeEvaluationStartMs=180000`;
- post-freeze evaluation: 120 s, czyli `180000…300000`;
- krok 1 ms;
- co najmniej 30 sparowanych seedów z nowej puli, np. `baseSeed=2000003`;
- ten sam seed topologii, wag i świata dla wszystkich trzech ramion.

Przy 30 repeats jedno ramię ma (300000\cdot30=9\cdot10^6) kroków, czyli mieści się pod dotychczasowym limitem 10 milionów kroków na żądanie. Trzy ramiona powinny być uruchamiane sekwencyjnie przez runner.

Okres 180 s treningu jest celowo taki sam jak potwierdzony horyzont true/frozen. 120 s ewaluacji powinno przy obserwowanym reward rate dostarczyć kilkanaście nagród na run, a jednocześnie utrzymać limit kroków.

### 20.3. Metryki fazowe wymagane od endpointu

Endpoint/runner powinien zwracać osobno dla faz:

1. `trainingRewards`: najlepiej po początkowych 30 s, czyli 30–180 s;
2. `postFreezeRewards`: 180–300 s — metryka główna;
3. `postFreezeFirstHalfRewards` i `postFreezeSecondHalfRewards` dla retencji w czasie;
4. path length 30–180 s oraz 180–300 s;
5. firing rate/statystyki aktywności osobno przed i po freeze;
6. średnią wagę na starcie, w chwili freeze i na końcu;
7. `postFreezeWeightDelta`, które musi być dokładnie 0 dla learn-then-freeze i always-frozen;
8. najlepiej kwantyle wag per projekcja w chwili freeze.

Nie należy używać dotychczasowego jednego `evaluationRewards` obejmującego jednocześnie trening i ewaluację, ponieważ miesza ono online learning z retencją.

### 20.4. Kontrasty i hipotezy

Główny kontrast retencji:

\[
\Delta_{retention}=
R^{post}_{learn\to freeze}-R^{post}_{always\ frozen}.
\]

Jeśli jest dodatni, wagi wyuczone w fazie treningu zachowują wartość po wyłączeniu plastyczności.

Wartość dalszej adaptacji online:

\[
\Delta_{online}=
R^{post}_{continuous}-R^{post}_{learn\to freeze}.
\]

Wynik bliski zero oznacza, że polityka po 180 s jest już stabilna; dodatni wynik wskazuje korzyść dalszego uczenia, a ujemny — że dalsza plastyczność destabilizuje nabytą politykę.

Efekt treningu przed freeze można kontrolnie porównać między learn-then-freeze i continuous; do chwili 180 s ich trajektorie powinny być bitowo identyczne dla tego samego seeda. Jeśli nie są, implementacja faz nie izoluje czynnika.

### 20.5. Kryteria decyzji ustalone przed runem

Learn-then-freeze potwierdza retencję, jeśli:

1. mean i mediana sparowanego (Delta_{retention}) są dodatnie;
2. bootstrap 95% CI średniej nie obejmuje zera;
3. dwustronny paired test/randomization osiąga (p<0.05), z Wilcoxonem i sign testem raportowanymi niezależnie;
4. rewards/1000 drogi ma zgodny dodatni efekt;
5. `postFreezeWeightDelta=0` w 30/30 runów;
6. efekt nie zanika całkowicie w drugiej połowie fazy post-freeze.

Brak spełnienia jednego testu przy zgodnych wielkościach efektu nie powinien być ukrywany przez wybór korzystniejszej procedury. Raportować należy pełny zestaw: różnice per seed, mean, medianę, bootstrap CI, (d_z), paired t, Wilcoxon, sign test oraz +/=/-.

### 20.6. Ograniczenie protokołu bez resetu świata

Samo `freezeLearningAtMs` w kontynuowanym świecie testuje trwałość zamkniętej pętli po treningu, ale nie izoluje czysto zapisanych wag. W chwili freeze ramiona learn-then-freeze i always-frozen znajdują się w innych pozycjach, orientacjach i światach po innych sekwencjach zjedzonego pokarmu. Post-freeze różnica zawiera więc zarówno efekt wag, jak i stan osiągnięty podczas treningu.

Najsilniejszy docelowy protokół to `train → checkpoint weights → fresh matched evaluation`:

1. trenować P001 przez 180 s;
2. zapisać wagi;
3. utworzyć świeży świat ewaluacyjny z niezależnego seedu i standardowym stanem agenta;
4. uruchomić 120 s z zamrożonymi wyuczonymi wagami;
5. porównać z tym samym światem i identyczną początkową topologią/wagami przed treningiem.

To rozdziela retencję w synapsach od korzystnej pozycji uzyskanej podczas treningu. `freezeLearningAtMs` jest właściwym etapem pośrednim i nie należy blokować jego implementacji oczekiwaniem na checkpoint/reset.

## 21. Cross-review po niezależnym potwierdzeniu i wspólna seria bramki Layer2

### 21.1. Rozdział osi eksperymentalnych

Najnowsze raporty i wszystkie aktualne szablony Hooke'a/Poincarégo zostały ponownie przeczytane.

Poincaré 006 już testuje headroom przez zmianę wyłącznie wag trzech projekcji do akcji z 4 do 3. Nie należy tego duplikować. Jego hipoteza jest postsynaptycznie „późna”: osłabia przejście Layer2→Layer3, zachowując autonomiczny AssociationBias 3.8.

Hooke 004–006 testuje z kolei próg rzadkiego bezpośredniego mostu sensoryczno-motorycznego. Wynik (w=2:4/16) poprawnych znaków i (w=3:16/16) pokazuje, że minimalny idealny rachunek napięcia nie przewiduje niezawodnej propagacji w dynamicznej sieci. Ta oś także jest już zajęta.

Meitner 010/011 i utworzony równolegle Poincaré 007 badają wcześniejszy etap credit assignment: wyłącznie toniczny prąd RS w Layer2. Celem jest odróżnienie autonomicznej aktywności asocjacyjnej od spike'ów sensorycznie bramkowanych. Nie zmieniają topologii, liczby synaps, E/I, wag ani akcji. Pierwotny Meitner 011 o biasie 3.6 został usunięty po wykryciu dokładnego duplikatu Poincaré 007; Poincaré dostarcza punkt środkowy wspólnej osi.

### 21.2. Matematyka bramki AssociationBias

Dla RS z (b=0.2) odległość napięciowa między dolnym stabilnym punktem a górnym separatorem przy stałym (I<4) wynosi

\[
\Delta v_{sep}=v_+-v_-=10\sqrt{4-I}.
\]

| konfiguracja | AssociationBias (I) | (Delta v_{sep}) | (w_{L1\to L2}/\Delta v_{sep}) | stan izolowanego RS |
|---|---:|---:|---:|---|
| P001 | 3.8 | 4.472 | 1.118 | przy/nieznacznie ponad granicą firing |
| 010 | 3.7 | 5.477 | 0.913 | stabilny podprogowo |
| Poincaré 007 | 3.6 | 6.325 | 0.791 | stabilny podprogowo |
| Meitner 011 | 3.5 | 7.071 | 0.707 | stabilny podprogowo |

P001 ma one-to-one (w=5), więc pojedynczy idealny EPSP jest większy od luki separatrix przy biasie 3.8. Dodatkowo sam prąd 3.8 leży minimalnie ponad wyliczoną dyskretną granicą około 3.7984 i daje izolowanemu RS około 5.4 Hz w protokole skokowym.

W trzech punktach 3.7/3.6/3.5 pojedynczy EPSP z idealnego spoczynku nie powinien sam przekroczyć separatrix. Spike Layer2 wymaga współdziałania bieżącego stanu (v,u), lokalnego E, skorelowanych bodźców lub późniejszej potencjacji. Hipoteza nie brzmi zatem „mniej firing jest lepiej”, lecz:

\[
\frac{\text{eligibility sensorycznie causal}}{\text{eligibility z autonomicznego tła}}
\quad\text{rośnie przy umiarkowanym obniżeniu biasu.}
\]

Zbyt niski bias może jednak zmniejszyć zarówno licznik, jak i mianownik: brak spike'ów oznacza brak eligibility i brak propagacji do akcji. Oczekiwane optimum, jeśli istnieje, powinno leżeć blisko 3.7, a 3.5 pełni rolę granicy silnego gatingu.

### 21.3. Nowe kompletne YAML-e

| plik | jedyna zmiana względem archiwalnego P001 | rola |
|---|---|---|
| `010-bramka-asocjacyjna-3p7.yaml` | AssociationBias 3.8→3.7 | minimalne usunięcie autonomicznego progu |
| `templates-poincare/007-nizszy-bias-layer2.yaml` | AssociationBias 3.8→3.6 | wspólny punkt środkowy Poincaré |
| `011-bramka-asocjacyjna-3p5.yaml` | AssociationBias 3.8→3.5 | silna bramka/kontrola ciszy |

Każdy nagłówek jawnie wymaga ramion true/frozen i learn→freeze, ale nie umieszcza parametrów faz w YAML. Zestaw P001/Meitner010/Poincaré007/Meitner011 przeszedł wspólną generację i smoke w osobnych JVM-ach; batch walidacyjny: `.local-snn-experiments/results/20260713-042534/`. Wynik behawioralny 1-sekundowego smoke'a nie jest używany do selekcji.

### 21.4. Hierarchia testów 010/Poincaré007/011

#### Etap A — kontrolowany assay mechanistyczny

Na co najmniej 16 nowych seedach topologii:

1. brak jedzenia/vision przez 1 s: mierzyć spontaniczny firing Layer2 RS;
2. pojedynczy cel przy kątach (-45^\circ,0,+45^\circ), odległość 100 i 250;
3. learning frozen, aby odpowiedź była własnością początkowej dynamiki;
4. mierzyć latency i liczbę spike'ów Layer1, Layer2 oraz odpowiedniej akcji w oknie 0–500 ms;
5. mierzyć poprawny znak skrętu i ruch dla centrum.

P001 powinien być dodatnią kontrolą propagacji. Kandydat przechodzi, jeżeli usuwa większość spontanicznego Layer2 firing, zachowuje poprawny znak dla bliskiego celu w co najmniej 14/16 prób na stronę i nie zwiększa mirror error.

#### Etap B — tani frozen screen

Tylko kandydaci przechodzący assay: 60 s, burn-in 10 s, 12 sparowanych seedów z nowej puli rozwojowej, np. `baseSeed=3000017`. Porównywać frozen kandydata z frozen P001. Odrzucić warianty z:

- ruchem w mniej niż 90% runów;
- niestabilnym firing;
- rewards lub drogą poniżej 90% P001;
- gorszą efektywnością nagród na drogę bez wyraźnego ograniczenia autonomicznego Layer2 firing.

#### Etap C — true/frozen i interakcja headroom

Dla maksymalnie dwóch ocalałych wariantów: 180 s × 16 nowych seedów, np. `baseSeed=4000037`, dokładnie ten sam YAML w true i frozen. Dwa równoległe wymagania:

\[
A_c=R^{true}_c-R^{frozen}_c>0,
\]

\[
H_c=A_c-A_{P001}>0.
\]

Nie wystarczy większy procent true/frozen. Kandydat może sztucznie zwiększyć procent przez pogorszenie frozen. Musi jednocześnie:

1. utrzymać bezwzględne true rewards co najmniej na 90% P001;
2. mieć dodatnią mean i medianę (H_c);
3. nie pogarszać rewards/1000 drogi;
4. zachować stabilny firing i poprawny znak sterowania.

#### Etap D — train→freeze→evaluate

Tylko jeden wcześniej wybrany zwycięzca przechodzi do protokołu z sekcji 20: 180 s uczenia, freeze, 120 s ewaluacji; trzy ramiona always-frozen, learn-then-freeze i continuous. Użyć kolejnej nietkniętej puli, np. `baseSeed=5000011`, najlepiej 30 powtórzeń.

### 21.5. Metryki rozstrzygające mechanizm

Obecne rewards/firing/path nie wystarczą do oceny hipotezy gatingu. Minimalne dodatkowe metryki:

- Layer2 RS firing przy braku vision;
- frakcja spike'ów Layer2 w 5 ms po spike'u odpowiadającego neuronu Layer1;
- cross-correlation L1→L2 z lagiem 1–5 ms;
- eligibility i (Delta w) osobno dla L1→L2, lokalnych Layer2 oraz Layer2→Layer3;
- post-freeze retention w dwóch oknach 60 s;
- rozkład latency sensoryczny spike→akcja.

Jeżeli metryki per projekcja nie będą jeszcze dostępne, seria może przejść tylko screening behawioralny; nie wolno wtedy twierdzić, że poprawiła lokalność credit assignment.

### 21.6. Wpływ niezależnego potwierdzenia P001

P001 true/frozen na 30 nowych seedach dał replikowany efekt +2.0667 nagrody, +10.65%, bootstrap CI powyżej zera i wzrost agregatowej efektywności drogi o około 7.3%. To ustala wysoki próg dla nowych konfiguracji. Celem wspólnej osi 3.7/3.6/3.5 nie jest maksymalizacja procentowego headroomu, lecz zachowanie tej bezwzględnej wydajności i retencji przy bardziej sensorycznie zależnej dynamice.

Poincaré 006 oraz zwycięzca wspólnej osi bramki tworzą później sensowny, ale dopiero wtórny projekt (2\times2): action weight 4/3 × AssociationBias 3.8/zwycięski bias. Nie należy uruchamiać tej macierzy przed jednoczynnikowym rozstrzygnięciem obu osi, aby nie mieszać dwóch rodzajów headroomu.
