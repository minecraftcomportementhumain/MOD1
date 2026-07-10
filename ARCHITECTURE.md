# Architecture du mod `mysubmod` (Forge 1.20.1)

Ce document décrit les modules, les flux principaux et — surtout — les **invariants
qu'aucune modification ne doit casser**. Il trace aussi le plan de robustesse par
phases. À maintenir à chaque changement structurel.

## Modules

| Paquet | Responsabilité |
|---|---|
| `authentification` | Comptes admin/joueur, mots de passe, file d'attente, jetons |
| `sousmodes` | Aiguillage des modes (`GestionnaireSousModes`), salle d'attente |
| `sousmodes.sousmode3` | La partie : cycle de vie, bonbons, santé, zones, spécialisation, journalisation CSV |
| `cartes` | Domaine des cartes : modèle (`CarteDonnees`, `BlocCarte`, `ZoneCarte`), persistance JSON, gestionnaire serveur |
| `cartes.client` | Éditeur visuel (`EcranEditeurCarte`), écrans de liste/import, barre de progression |
| `cartes.jeu` | `PiloteChargementCarte` : génération étalée sur les ticks |
| `sousmodes.sousmode3.GenerateurCarteSousMode3` | Écriture/effacement en masse de la carte dans le monde |
| `sousmodes.sousmode3.EffaceurCarteSousMode3` | Effacement étalé sur les ticks à la désactivation |
| `reseau` | Canal `SimpleChannel` unique, enregistrement de tous les paquets |
| `miseajour` | Auto-mise à jour du serveur depuis la release GitHub « latest » |

## Flux principaux

1. **Édition** : menu M → Cartes → verrou mono-admin serveur → `EcranEditeurCarte` ;
   sauvegarde = JSON v2 gzippé en morceaux de 30 Ko (≤ 1024) → réassemblage,
   validation, `recalculerZones`, écriture fichier. Chargement = envoi du fichier brut
   gzippé, décodage côté client.
2. **Partie SM3** : sélection carte → activation (plateforme spectateur + téléportations)
   → génération étalée (`Tache` par chunks, préchargement asynchrone, contre-pression)
   → `terminerActivation` (bonbons, zones, HUD) → menu N (config) → partie → fin/retour
   → désactivation : arrêts + **effacement étalé** (`EffaceurCarteSousMode3`) + salle
   d'attente.
3. **Déploiement** : push sur `main` → CI (`release.yml`) : `gradlew build`
   (**les tests bloquent la publication**) → release « latest » → le serveur se met à
   jour tout seul (sondage 2 min, redémarrage par `run-serveur.bat`).

## Invariants critiques

- **Réseau** : tous les paquets s'enregistrent dans l'unique `GestionnaireReseau.init()`
  avec des IDs auto-incrémentés ; la **version de protocole = version exacte du build**,
  donc un client d'un autre build est rejeté au handshake — l'ordre n'a besoin d'être
  stable qu'au sein d'un même fichier. Ne jamais enregistrer un paquet ailleurs.
- **Bande de la cage** : tout le contenu généré vit en Y 84..116
  (`GenerateurCarteSousMode3.Y_PLANCHER_BARRIER..Y_PLAFOND_BARRIER`). **La plateforme de
  la salle d'attente (0, 100) vit DANS cette bande** : tout effacement de la bande doit
  exclure `GestionnaireSalleAttente.obtenirEmpriseProtegee()` (bug corrigé en `1b0d91d`).
  Toute nouvelle structure persistante placée dans la bande doit être exclue de même.
- **Formats de carte** : l'écriture est toujours v2 (terrain en plages « n:TÉ », bonbons
  en tableaux de 10 entiers, zones en plages « z,x0,len ») ; la lecture accepte v1 et v2.
  Les cellules de zones ne sont **jamais développées** (plages triées par (z, x0),
  appartenance par recherche binaire — `ZoneCarte.plagesContiennent`). Les décodeurs
  bornent tout (dimensions ≤ `DIMENSION_MAX`, `BLOCS_MAX`, arithmétique en `long` contre
  les débordements, GZIP plafonné à 256 Mio).
- **Écriture en masse dans le monde** : jamais de `Level.setBlock` par bloc pour la
  carte — sections de chunks + heightmaps/renvoi par chunk + éclairage par semences
  (`checkBlock` aux transitions d'opacité). Drapeau `2|16` (jamais `1` : pas de
  notifications de voisins, l'eau ne coule pas pendant la génération). Contre-pression :
  ≤ 24 chunks/tick sous budget 30 ms ET croissance des chunks chargés bornée par rapport
  à une référence **relative** (jamais de seuil absolu — gel à 0 % garanti sinon).
- **Effacement** : jamais synchrone sur la région (watchdog 60 s) ; cibler
  `ResultatGeneration.chunksModifies` + anneau ; le fichier
  `donnees_monsubmod/sousmode3_region.json` n'est supprimé qu'à la FIN du balayage
  (reprise après crash) ; la génération suivante attend la fin de l'effacement
  (`EffaceurCarteSousMode3.puisExecuter`).
- **Timers** : tout `java.util.Timer` doit être **daemon** (sinon la JVM ne meurt pas et
  l'auto-mise à jour ne redémarre jamais le serveur).
- **`generationEnCours`** doit TOUJOURS redescendre à `false` (`terminerActivation` en
  `try/finally`) : sinon la garde de `changerSousMode` bloque toute transition humaine,
  et comme le retour manuel en salle d'attente est le seul chemin vers `deactivate`,
  c'est un deadlock jusqu'au redémarrage.
- **Paquets réseau serveur→client** dont le `traiter` touche des classes client :
  toujours enregistrés avec `NetworkDirection.PLAY_TO_CLIENT` (sinon un client peut les
  renvoyer au serveur dédié et y déclencher une erreur de classe absente).
- **Tickets FORCED** de la génération : libérés à la transition CHUNKS→ARBRES en régime
  normal, MAIS aussi dans le `catch` de `PiloteChargementCarte` (exception en pleine
  génération) et à l'arrêt du serveur — sinon des chunks restent chargés jusqu'au reboot.
- **Verrou éditeur** : un seul admin à la fois (`GestionnaireCartes`), libéré à la
  fermeture de l'écran et à la déconnexion.
- **Parcelles** (« zones » dans le code et le format de fichier) : zonage
  **exclusivement manuel** (outil Parcelle de l'éditeur) — aucun recalcul
  automatique, nulle part (`recalculerZones` n'est plus qu'un utilitaire de test).
  `versJson` dérive les plages nommées du champ `zone` des blocs (parcelles vides
  omises, type Île/Pierre dérivé mais conservé pour le format seulement) ;
  `depuisJson` fait l'inverse. Le **choix de parcelle de départ propose toutes les
  parcelles**, telles que définies sur la carte. **Noms de parcelles uniques** :
  imposé à la création/renommage dans l'éditeur ET revalidé à la sauvegarde (le
  runtime en jeu indexe les parcelles par nom — deux homonymes se confondraient).
  **Chaque parcelle est d'un seul tenant** (connexité 8 : côtés et diagonales) :
  la peinture refuse une cellule détachée, le panneau signale ⚠ une parcelle
  coupée (clic droit), et `validerPourSauvegarde` refuse la carte
  (`compterMorceauxParcelles`).
  Le **spawn de parcelle ne considère que les cellules à l'intérieur du périmètre
  Limite** (`calculerSpawnZone` filtre sur `generation.cellulesInterieur`) : une
  parcelle peut déborder de l'anneau, mais hors périmètre le terrain n'existe pas.
  Le champ `BlocCarte.zone`
  n'est jamais sérialisé directement. **Validation bloquante à la sauvegarde :
  chaque bonbon doit appartenir à une parcelle.** Les bonbons hors parcelle d'une
  vieille carte tombent en jeu dans la pseudo-zone HUD « Hors parcelle ».
- **Transitions de mode atomiques** : `changerSousMode` refuse toute demande **humaine**
  tant qu'une activation (génération de carte comprise) ou le nettoyage de la carte
  précédente est en cours ; les transitions du serveur lui-même (démarrage, fin de
  partie) restent permises car elles font partie de ces processus.

## Tests

- `src/test/java/.../CarteDonneesTest.java` : 15 tests du domaine cartes (sans
  Minecraft) — aller-retour v2, compatibilité v1 (encodeur v1 conservé comme oracle),
  **parité** des remplissages BitSet et des zones avec les implémentations d'origine
  (200 cartes aléatoires), recherche binaire vs balayage linéaire, GZIP, bornes et
  malformations, carte pleine à `DIMENSION_MAX`.
- Exécution : `gradlew test` (inclus dans `gradlew build`, donc **bloquant pour la CI
  et le déploiement auto**).
- Ce qui n'est PAS couvert par les tests : tout ce qui touche Minecraft (génération dans
  le monde, éclairage, paquets, écrans) → validation en jeu obligatoire après chaque
  changement dans ces zones.

## Plan de robustesse par phases

- **Phase A — fondations (livrée)** : tests dans le dépôt + CI bloquante, ce document.
- **Phase B — découper `GestionnaireSousMode3` (~1 900 lignes)** en composants à
  responsabilité unique et à état explicite : cycle de vie de partie
  (activation/décompte/fin/désactivation), registre des joueurs (vivants, spectateurs,
  en attente, déconnectés, inventaires), sélection de zone de départ, téléportations.
  Un composant par commit, comportement identique, validation en jeu entre chaque étape.
- **Phase C — cycle de vie des services** : remplacer l'état statique nu
  (`PiloteChargementCarte`, `EffaceurCarteSousMode3`, singletons paresseux) par des
  instances possédées par le serveur, créées au démarrage et réinitialisées à l'arrêt —
  supprime toute la remise à zéro manuelle éparpillée.
- **Phase D — éditeur** : extraire le modèle d'édition (carte en cours, pile d'annulation,
  actions, validation) de `EcranEditeurCarte` (~2 400 lignes) pour le rendre testable ;
  l'écran ne garde que le rendu et les entrées.
- **Hors périmètre assumé** : modèle « à plat » de `carte.blocs` (~120 o/bloc, toléré
  jusqu'à `DIMENSION_MAX` actuel) et worldgen custom de la région (le terrain naturel
  autour de la cage doit rester visible).
