# Play Store listing

Package `nl.lloydstellar.override`, category **Games > Puzzle**. Tags in the
Play Console: Puzzle, Casual, Single player, Offline, Stylised.

How Google Play search works, in short: there is no keyword field. The words
people type are matched against the title (weighs most), the short
description and the full description. People looking for this kind of game
type "2048", "2048 puzzle", "number puzzle", "merge numbers" and "offline
puzzle". Those words go in the title and the first lines, said once in
normal sentences. Repeating them, or words like "best" or "#1", is against
the metadata policy.

"2048" is not a trademark; the original by Gabriele Cirulli is MIT
licensed and credited in the app (About, Open source licences).

## English (default)

**App name** (28 of 30): `2048 Override: Number Puzzle`

**Short description** (75 of 80):
`Slide and fuse tiles up to 2048 on a retro CRT terminal, against a rogue AI`

**Full description:**

It is the year 2048 and the AI NEXUS-9 owns every network. What it cannot read is analogue hardware. You sit at a hacked military CRT and play the number puzzle that decides the fight: slide the signals on the 4 x 4 matrix, fuse two equal signals into one of double strength, and build a pulse of exactly 2048 bit to overload the AI core.

The rules of 2048 are untouched, with two problems on top. Every move without a fusion raises the AI Trace, and at 100 % NEXUS-9 locks one of your cells for five moves. From the 32 onward, red Corrupted Nodes appear that refuse to fuse upward: purge one with an equal signal, or park it next to a 64 EMP Cell and watch it fry.

Twelve sectors, each with its own twist:
• a trace that climbs three times as fast
• Corrupted Nodes from the first move, or four at once
• a 5 x 5 matrix, a 3 x 3 matrix, burned-out cells
• a satellite window of 380 moves
• the NEXUS core itself, with every rule at full strength
Every sector gives up to three signal bars: one for the clear, one for staying under par, one for never getting locked.

Made a bad move? ROLLBACK undoes it, spawn included. You earn the charges by building big signals, so it helps without making the game trivial.

Every tile is a piece of resistance hardware with its own icon, from a 2 Hz Sub-Pulse to the Quantum Transmitter. The soundtrack is a dark electro loop, generated live, that speeds up as your highest signal grows. Reach the Override Code and the terminal speaks. Bigger signals unlock phosphor colours for the tube and decrypt the files of the resistance.

Also in the app:
• 20 achievements, your top sessions and lifetime stats
• haptics on every slide, fusion and lock
• your session is saved after every move, so leaving costs nothing
• swipe, or use the arrow keys or WASD on a keyboard
• TalkBack reads the board and offers the four slides as actions

No ads, no account, no tracking, no purchases. The app has no internet access at all.

2048 Override is made by Lloyd Stellar, an electro producer from Hilversum, and started as a free browser game on lloydstellar.nl.

## Nederlands

**App-naam** (27 van 30): `2048 Override: nummerpuzzel`

**Korte beschrijving** (78 van 80):
`Schuif en fuseer tegels tot 2048 op een retro CRT-terminal, tegen een kwade AI`

**Volledige beschrijving:**

Het is 2048 en de AI NEXUS-9 beheerst elk netwerk. Wat hij niet kan lezen is analoge hardware. Jij zit achter een gehackte militaire CRT en speelt de nummerpuzzel die het gevecht beslist: schuif de signalen over de 4 x 4-matrix, fuseer twee gelijke signalen tot één van dubbele sterkte en bouw een puls van precies 2048 bit om de kern van de AI te overbelasten.

De regels van 2048 zijn onaangeroerd, met twee problemen erbovenop. Elke zet zonder fusie laat de AI Trace stijgen, en bij 100 % vergrendelt NEXUS-9 vijf zetten lang een van je cellen. Vanaf de 32 verschijnen rode Corrupted Nodes die niet omhoog fuseren: zuiver er een met een gelijk signaal, of zet hem naast een 64 EMP Cell en zie hem doorbranden.

Twaalf sectoren, elk met een eigen twist:
• een trace die drie keer zo snel stijgt
• Corrupted Nodes vanaf de eerste zet, of vier tegelijk
• een 5 x 5-matrix, een 3 x 3-matrix, doorgebrande cellen
• een satellietvenster van 380 zetten
• de NEXUS-kern zelf, met elke regel op volle sterkte
Elke sector geeft tot drie signaalbalken: een voor het halen, een voor binnen par blijven, een voor nooit vergrendeld worden.

Een slechte zet gedaan? ROLLBACK draait hem terug, inclusief de nieuwe tegel. De ladingen verdien je door grote signalen te bouwen, dus het helpt zonder dat het spel te makkelijk wordt.

Elke tegel is een stuk verzetshardware met een eigen icoon, van een 2 Hz Sub-Pulse tot de Quantum Transmitter. De soundtrack is een donkere electroloop die live wordt gegenereerd en sneller gaat naarmate je hoogste signaal groeit. Haal de Override Code en de terminal spreekt. Grotere signalen ontgrendelen fosforkleuren voor de beeldbuis en ontsleutelen de bestanden van het verzet.

Verder in de app:
• 20 achievements, je beste sessies en statistieken
• haptische feedback bij elke schuif, fusie en vergrendeling
• je sessie wordt na elke zet bewaard, dus wegklikken kost niets
• swipen, of de pijltjestoetsen of WASD op een toetsenbord
• TalkBack leest het bord voor en biedt de vier schuifrichtingen als acties

Geen advertenties, geen account, geen tracking, geen aankopen. De app heeft helemaal geen internettoegang.

2048 Override is gemaakt door Lloyd Stellar, electroproducer uit Hilversum, en begon als gratis browsergame op lloydstellar.nl.

## Graphics

- App icon: `tools/store/icon-512.png`
- Feature graphic: `tools/store/feature-1024x500.png`
- Phone screenshots, in this order: `tools/store/screen-01.png` to `screen-07.png` (1080x1920). The captions are in English; Play shows them for every language.

Rebuild them with `tools/make_app_icon.py` (needs Pillow) and
`tools/make_store_graphics.py` (needs Chromium) after a visual change.

## Data safety

- Data collected: none
- Data shared: none
- The app has no INTERNET permission. Scores, settings and saved games stay in the app's private storage (Android backup may copy them to the user's own Google account, which is the system's backup, not ours).

## Privacy policy

Play needs a privacy policy URL, also for apps that collect nothing. Suggested
page, for example at `https://lloydstellar.nl/privacy-apps.html`:

> 2048 Override, Stellar Singularity and Spin Doctor do not collect, store or share any personal data. The apps have no internet access, no ads, no analytics and no accounts. Scores, settings and saved games stay on your phone. Questions: use the contact form on lloydstellar.nl.

## Content rating (IARC questionnaire)

Category: Game. No violence (the "AI" is fought with a number puzzle, nothing is shown being hurt), no fear, no sexuality, no gambling, no drugs, no language, no user interaction, no sharing of location, no purchases. Expected rating: PEGI 3 / Everyone.

## Publishing

1. Create the app in the Play Console (name above, default language English).
2. Turn on Play App Signing and upload `app/build/outputs/bundle/release/app-release.aab`. The key in `signing/` (not in the repository) is the upload key; keep a backup of `signing/` somewhere safe.
3. Personal developer accounts created after November 13, 2023 first need a closed test with at least 12 testers who stay opted in for 14 days in a row before production access.
4. Graphics, Data safety, privacy policy URL, content rating as above. Target audience: 13 and up is the safe choice for a game with a dystopian story; it can be "all ages" content-wise.
5. Bump `versionCode` in `app/build.gradle.kts` for every upload.
