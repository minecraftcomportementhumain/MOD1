# Changelog - MySubMod

## ⚡ Session du 6 novembre 2025 - Optimisations Performance et Corrections SubMode2

### 🔧 Fix critique: Server hang/timeout (60+ secondes)

**Problème** : Le serveur se figeait complètement avec un timeout de 60+ secondes lors du tick, causant un crash `ServerHangWatchdog`.

**Cause identifiée** :
- Code de désactivation du sprint dans `SubMode2EventHandler.java` (lignes 326-352)
- **CHAQUE tick** (50ms), le code modifiait les attributs de mouvement pour TOUS les joueurs
- Opérations d'ajout/suppression d'`AttributeModifier` à chaque tick = extrêmement coûteux
- Déclenchait des mises à jour d'entités en cascade

**Solution implémentée** :
- Ajout d'une Map `playerSprintState` pour tracker l'état de sprint de chaque joueur
- Modification des attributs **uniquement quand l'état de sprint change**
- UUID constant `SPRINT_MODIFIER_UUID` pour identifier le modificateur
- Cleanup automatique de la Map lors de la déconnexion des joueurs

**Code avant (problématique)** :
```java
for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(event.getServer())) {
    movementSpeed.removeModifier(sprintModifierUUID); // CHAQUE TICK
    if (player.isSprinting()) {
        movementSpeed.addTransientModifier(noSprintModifier); // CHAQUE TICK
    }
}
```

**Code après (optimisé)** :
```java
for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(event.getServer())) {
    boolean isSprinting = player.isSprinting();
    Boolean previousSprintState = playerSprintState.get(player.getUUID());

    // Modification UNIQUEMENT si changement d'état
    if (previousSprintState == null || previousSprintState != isSprinting) {
        playerSprintState.put(player.getUUID(), isSprinting);
        // Modifier attributs une seule fois
    }
}
```

**Impact** :
- ✅ Réduction de ~99% des opérations sur les attributs
- ✅ Serveur stable, plus de timeouts
- ✅ Performance normale restaurée

**Fichier modifié** : `SubMode2EventHandler.java:33-35,302-359`

---

### 🎯 Ajustement pénalité de spécialisation: 50% → 75%

**Changement** : La pénalité pour changement de spécialisation a été ajustée pour un meilleur équilibre de gameplay.

**Avant** :
- Bonbon de l'autre type : +0.5 cœur (50% efficacité)
- Pénalité trop sévère

**Après** :
- Bonbon de l'autre type : +0.75 cœur (75% efficacité)
- Durée de pénalité : 2 minutes 45 secondes (inchangée)

**Modifications** :
1. **SpecializationManager.java** :
   - `PENALTY_HEALTH_MULTIPLIER = 0.75f` (ligne 30)
   - Message: "Toutes les ressources restaurent 75% de santé"
   - Commentaires mis à jour

2. **SubMode2HealthManager.java** :
   - Message feedback: "Pénalité: 75%"
   - Format d'affichage: `%.2f` pour 2 décimales
   - Commentaires mis à jour (lignes 15, 98, 117)

3. **CandyItem.java** :
   - Tooltip: "0.75 cœur si pénalité" (ligne 139)

**Gameplay** :
- Changement de spécialisation moins punitif
- Encourage l'exploration des deux types de ressources
- Meilleur équilibre entre spécialisation et flexibilité

**Fichiers modifiés** :
- `SpecializationManager.java:30,126,148,155`
- `SubMode2HealthManager.java:15,98,117`
- `CandyItem.java:139`

---

### 🐛 Fix: HUD SubMode2 persistent lors changement de mode

**Problème** : Lors du passage de SubMode2 vers SubMode1, tous les éléments HUD du SubMode2 restaient visibles:
- Timer de jeu SubMode2
- Compteur de bonbons SubMode2 (par île ET par type)
- Timer de pénalité de spécialisation

**Cause** : Aucun nettoyage des HUD lors du changement de mode dans `ClientSubModeManager.setCurrentMode()`.

**Solution** :
- Détection du changement de mode (oldMode vs newMode)
- Désactivation automatique de TOUS les HUD lors de la sortie d'un sous-mode
- Symétrique pour SubMode1 et SubMode2

**Code ajouté dans `ClientSubModeManager.java`** :
```java
public static void setCurrentMode(SubMode mode) {
    SubMode oldMode = currentMode;
    currentMode = mode;

    // Clean up HUD elements when leaving SubMode2
    if (oldMode == SubMode.SUB_MODE_2 && mode != SubMode.SUB_MODE_2) {
        ClientGameTimer.deactivate();
        CandyCountHUD.deactivate();
        PenaltyTimerHUD.deactivate();
    }

    // Clean up HUD elements when leaving SubMode1
    if (oldMode == SubMode.SUB_MODE_1 && mode != SubMode.SUB_MODE_1) {
        ClientGameTimer.deactivate();
        CandyCountHUD.deactivate();
    }
}
```

**Avantages** :
- ✅ Transition propre entre tous les modes
- ✅ Pas de HUD fantômes
- ✅ Expérience utilisateur cohérente
- ✅ Code centralisé et maintenable

**Fichier modifié** : `ClientSubModeManager.java:9-33`

---

### 📚 Corrections majeures SUBMODE2_GUIDE.md

Le guide du SubMode2 contenait plusieurs erreurs critiques sur le fonctionnement du système de spécialisation.

**Erreurs corrigées** :

1. **Système de spécialisation (Section 1)** :
   - ❌ **Avant** : "Assignation aléatoire lors de la sélection d'île"
   - ✅ **Après** : "Spécialisation dynamique définie à la première collecte de bonbon"
   - Clarification que la spécialisation peut changer pendant la partie

2. **Durée de pénalité (Tableau comparatif)** :
   - ❌ **Avant** : "30s pour mauvais type"
   - ✅ **Après** : "2min 45s pour changement"

3. **Effet de la pénalité** :
   - ❌ **Avant** : "0.5 cœur au lieu de 1"
   - ✅ **Après** : "0.75 cœur au lieu de 1 cœur"

4. **Déclenchement de la pénalité** :
   - ❌ **Avant** : "Consommer un bonbon du type opposé"
   - ✅ **Après** : "Collecter un bonbon du type opposé + change automatiquement la spécialisation"

5. **Phase de sélection d'île** :
   - Suppression de la mention d'assignation aléatoire de spécialisation
   - Clarification qu'aucune spécialisation n'existe au départ

6. **Dégradation de santé** :
   - ❌ **Avant** : "-1 cœur (2 points) toutes les 10 secondes"
   - ✅ **Après** : "-0.5 cœur (1 point) toutes les 10 secondes"

7. **HUD Compteur de bonbons** :
   - Ajout de la précision "par île ET par type (bleu/rouge)"

8. **Stratégies** :
   - Section complètement réécrite pour refléter le système dynamique
   - Focus sur minimiser les changements de spécialisation
   - Explication du choix stratégique de la première collecte

**Sections ajoutées** :
- Notes de version avec spécifications techniques
- Date de dernière mise à jour: 6 novembre 2025

**Fichier modifié** : `SUBMODE2_GUIDE.md` (15+ sections corrigées)

---

### 📊 Statistiques de la session

**Fichiers modifiés** : 5
- `SubMode2EventHandler.java` : Fix performance critique + cleanup
- `SpecializationManager.java` : Ajustement pénalité 75%
- `SubMode2HealthManager.java` : Messages mis à jour
- `CandyItem.java` : Tooltip corrigé
- `ClientSubModeManager.java` : Nettoyage HUD automatique
- `SUBMODE2_GUIDE.md` : 15+ corrections majeures

**Lignes modifiées** : ~150 lignes

**Impact** :
- 🚀 **Performance** : Serveur stable, plus de timeouts
- ⚖️ **Gameplay** : Pénalité mieux équilibrée (75%)
- 🎨 **UX** : Transition propre entre modes
- 📖 **Documentation** : Guide précis et à jour

---

## 🎮 Session du 30 octobre 2025 - Création du SubMode2 et Corrections

### 🆕 Création complète du SubMode2

**Concept** : Système de spécialisation avec deux types de ressources (bonbons bleus et rouges) et pénalités pour consommation croisée.

#### Nouveaux items (2)

**CandyBlueItem.java** :
- Bonbon bleu pour ressource TYPE_A
- Soigne 1 cœur (pleine efficacité pour TYPE_A)
- Soigne 0.5 cœur avec pénalité de 30s pour TYPE_B
- Texture personnalisée : `candy_blue.png` et `candy_blue_texture.png`

**CandyRedItem.java** :
- Bonbon rouge pour ressource TYPE_B
- Soigne 1 cœur (pleine efficacité pour TYPE_B)
- Soigne 0.5 cœur avec pénalité de 30s pour TYPE_A
- Texture personnalisée : `candy_red.png` et `candy_red_texture.png`

**Enregistrement** : `ModItems.java` avec `CANDY_BLUE` et `CANDY_RED`

#### Système de spécialisation

**SubMode2HealthManager.java** :
- Gère les spécialisations des joueurs (TYPE_A ou TYPE_B)
- Assignation automatique aléatoire lors de la sélection d'île
- Pénalités de 30 secondes pour consommation du mauvais type
- Méthode `handleCandyConsumption()` avec logique de spécialisation
- Synchronisation des pénalités via `PenaltySyncPacket`

**ResourceType.java** (enum) :
- `TYPE_A` : Associé aux bonbons bleus
- `TYPE_B` : Associé aux bonbons rouges
- `getDisplayName()` : "Type A" et "Type B"

#### Système de gestion des bonbons

**SubMode2CandyManager.java** :
- Spawn coordonné des bonbons bleus et rouges
- Distribution aléatoire 50/50 entre les deux types
- Parsing des fichiers de spawn identique à SubMode1
- Méthode `spawnCandy()` avec alternance des types
- Nettoyage automatique à la fin de partie

#### Interface utilisateur client

**HUD Timer de jeu** :
- `SubMode2HUD.java` : Affichage du timer de partie (15 minutes)
- Position : Coin supérieur gauche
- Format : "MM:SS" avec couleurs (vert → jaune → rouge)
- Désactivation automatique en mode spectateur

**HUD Compteur de bonbons** :
- `CandyCountHUD.java` : Affichage du nombre de bonbons par île
- Position : Coin supérieur droit
- Couleurs par île : Blanc (petite), Vert (moyenne), Bleu (grande), Orange (très grande)
- Mise à jour en temps réel via `CandyCountUpdatePacket`

**HUD Timer de pénalité** :
- `PenaltyTimerHUD.java` : Affichage du timer de pénalité (30s)
- Position : Centre-haut de l'écran
- Message : "⚠ PÉNALITÉ: XXs" en rouge
- Activé/désactivé via `PenaltySyncPacket`

**Renderer unique** :
- `CandyCountHUDRenderer.java` : Gère l'affichage des 3 HUDs
- Vérification mode spectateur pour cacher les HUDs
- Event `RenderGuiEvent.Post`

#### Gestion des fichiers et logs

**Sélection de fichiers** :
- `CandyFileSelectionScreen.java` : Interface de sélection de fichiers
- Liste déroulante des fichiers disponibles
- Upload de nouveaux fichiers
- Suppression de fichiers (sauf default.txt)

**Logging des données** :
- `SubMode2DataLogger.java` : Enregistrement de toutes les actions
- Format CSV avec timestamps précis
- Logs : spawn bonbons, ramassage, consommation, changements santé, mort, sélection île, pénalités
- Structure : `mysubmod_data/submode2_game_[timestamp]/`

#### Système réseau (9 packets)

**Packets de synchronisation** :
- `GameTimerPacket` : Synchronise le timer de jeu
- `CandyCountUpdatePacket` : Met à jour le compteur de bonbons
- `PenaltySyncPacket` : Synchronise l'état de pénalité

**Packets de fichiers** :
- `CandyFileListRequestPacket` : Demande la liste des fichiers
- `CandyFileListPacket` : Envoie la liste au client
- `CandyFileSelectionPacket` : Sélectionne un fichier
- `CandyFileUploadPacket` : Upload un nouveau fichier
- `CandyFileDeletePacket` : Supprime un fichier

**Packets de jeu** :
- `IslandChoicePacket` : Affiche les choix d'îles
- `IslandSelectionPacket` : Enregistre le choix du joueur
- `GameEndPacket` : Notifie la fin de partie

**Handler client** :
- `ClientPacketHandler.java` : Gère tous les packets côté client

#### Manager principal

**SubMode2Manager.java** (1900+ lignes) :
- Gestion complète du cycle de vie du mode
- Génération de 4 îles + carré central + chemins
- Phase de sélection de fichier (fileSelectionPhase)
- Phase de sélection d'île (selectionPhase)
- Phase de jeu active (gameActive)
- Timer de 15 minutes avec fin automatique
- Téléportation sécurisée avec chargement de chunks
- Nettoyage complet à la désactivation
- Gestion des déconnexions/reconnexions
- Système de spectateurs pour joueurs morts

#### Event Handler

**SubMode2EventHandler.java** :
- Blocage des interactions avec blocs (sauf bonbons)
- Blocage de la casse de blocs
- Blocage du drop d'items (sauf bonbons)
- Prévention du spawn d'entités hostiles
- Cycle jour/nuit bloqué à midi
- Gestion du ramassage de bonbons
- Désactivation du sprint
- Gestion de la santé et de la mort

#### Intégration au système

**SubMode enum** :
- Ajout de `SUB_MODE_2` dans l'énumération

**SubModeManager** :
- Intégration de SubMode2Manager
- Switch case pour activation/désactivation
- Gestion du changement de mode

**SubModeControlScreen** :
- Bouton "Sous-mode 2" dans le menu M
- Interface cohérente avec SubMode1

**NetworkHandler** :
- Enregistrement des 9 nouveaux packets SubMode2

### Corrections critiques de SubMode2

**1. Fix server crash lors de la désactivation**
- **Problème** : ServerHangWatchdog timeout (60+ secondes) lors du nettoyage de SubMode2
- **Cause** : Flag `3` dans `level.setBlock()` déclenchait des mises à jour massives de chunks et redstone
- **Solution** : Changement de tous les flags de `3` à `2` dans les méthodes de nettoyage
  - `clearPath()` : ligne 1005-1007
  - `clearIslandBarriers()` : lignes 1072, 1078, 1087, 1093
  - `clearPathBarriers()` : lignes 1143, 1150
  - Suppression des pissenlits : ligne 1566
- **Impact** : Flag `2` supprime les mises à jour de blocs, évitant les recalculs coûteux de chunks
- **Fichier** : `SubMode2Manager.java`

**2. Fix HUDs SubMode2 persistant dans parking lobby**
- **Problème** : Timer et HUD bonbons de SubMode2 visibles dans parking lobby après déconnexion/reconnexion
- **Cause** : Paquets de désactivation manquants pour SubMode2 (présents uniquement pour SubMode1)
- **Solution** : Ajout de 3 paquets de désactivation dans `ServerEventHandler.java:115-121`
  - `GameTimerPacket(-1)` : Désactive le timer
  - `CandyCountUpdatePacket(empty map)` : Vide le HUD des bonbons
  - `PenaltyTimerPacket(false, UUID)` : Désactive le timer de pénalité
- **Résultat** : HUDs proprement nettoyés lors de l'entrée au parking lobby

**3. Fix messages d'interdiction lors de consommation bonbons rouges/bleus**
- **Problème** : Message "Vous ne pouvez pas interagir avec les blocs en sous-mode 2" apparaissait lors de la consommation des bonbons
- **Cause** : Retour `InteractionResultHolder.pass()` au lieu de `consume()` dans les items
- **Différence clé** :
  - `pass()` : Laisse l'événement continuer → `onPlayerInteractBlock` s'exécute → message affiché
  - `consume()` : Consomme l'item immédiatement côté client → bloque autres gestionnaires d'événements
- **Solution** : Changé `pass()` en `consume()` dans `CandyBlueItem.java` et `CandyRedItem.java` (ligne 75)
- **Résultat** : Comportement identique à `CandyItem` du SubMode1

**4. Fix vérification des bonbons dans SubMode2EventHandler**
- **Problème initial** : Vérification incluait `ModItems.CANDY.get()` (bonbon SubMode1 avec NBT)
- **Solution** : Retrait de `CANDY.get()` de la vérification dans `onPlayerInteractBlock`
- **Code final** : Vérification séparée pour `CANDY_BLUE` et `CANDY_RED` uniquement

### Architecture technique

**Flags de setBlock** :
- Flag `2` : Envoie changement au client, PAS de mises à jour de blocs
- Flag `3` : Envoie changement + mises à jour blocs + recalculs redstone → TRÈS COÛTEUX
- **Règle** : Utiliser flag `2` pour nettoyage en masse, flag `3` uniquement pour placement individuel

**InteractionResultHolder** :
- `success()` : Action réussie, consomme item
- `fail()` : Action échouée, ne consomme pas
- `pass()` : Ne gère pas, laisse continuer (DANGEREUX si événements suivent)
- `consume()` : Consomme immédiatement côté client, bloque propagation

**Packets de désactivation HUD** :
- Envoyés lors de l'entrée au parking lobby
- Valeurs spéciales : `-1` pour timers, map vide pour compteurs, `false` pour flags
- Gérés côté client par les handlers de packets respectifs

### Fichiers modifiés (4)

- `SubMode2Manager.java` :
  - 8 occurrences de flags changés de `3` à `2`
  - Commentaires ajoutés sur l'utilisation des flags

- `ServerEventHandler.java` :
  - Ajout de 3 paquets de désactivation pour SubMode2 (lignes 115-121)
  - Parallèle aux paquets SubMode1 existants

- `CandyBlueItem.java` :
  - `pass()` → `consume()` ligne 75

- `CandyRedItem.java` :
  - `pass()` → `consume()` ligne 75

- `SubMode2EventHandler.java` :
  - Retrait de `ModItems.CANDY.get()` de la vérification
  - Séparation des vérifications `CANDY_BLUE` et `CANDY_RED`

### Impact

- 🔧 **Performance** : Désactivation SubMode2 instantanée (plus de timeout 60s)
- 🎨 **UX** : HUDs proprement nettoyés, pas de messages parasites
- 🐛 **Stabilité** : Résolution des 3 bugs critiques de SubMode2
- ✅ **Cohérence** : Comportement identique entre SubMode1 et SubMode2

---

## 🛡️ Session du 21 octobre 2025 - Protection DoS et Optimisation Queue

### Protection contre Déni de Service (DoS)

**Problème identifié:**
- Un attaquant pouvait créer un nombre illimité de connexions candidates (_Q_) pour surcharger le serveur
- Aucune limite sur le nombre de tentatives parallèles par IP
- Risque de saturation des ressources serveur

**Solution implémentée:**

**1. Limites strictes par IP**
- **4 candidats maximum** par compte depuis la même IP
- **10 candidats maximum** au total depuis la même IP (tous comptes confondus)
- Constantes dans `ParkingLobbyManager.java`:
  ```java
  MAX_CANDIDATES_PER_ACCOUNT_PER_IP = 4
  MAX_CANDIDATES_PER_IP_GLOBAL = 10
  CANDIDATE_MIN_AGE_FOR_EVICTION_MS = 20000  // 20 secondes
  ```

**2. Éviction intelligente basée sur l'âge**
- Quand la limite est atteinte, le système cherche les candidats **≥20 secondes** d'ancienneté
- Le candidat le plus vieux est automatiquement déconnecté (éviction)
- Le nouveau candidat prend sa place
- Si tous les candidats sont récents (< 20s), le nouveau est **refusé**
- Message clair à l'utilisateur avec détails des limites

**3. Nettoyage systématique des candidats**

**Bug corrigé:** Les candidats qui se déconnectaient (timeout ou crash) n'étaient pas retirés des Maps de tracking, causant un comptage inexact.

**Correction:** Nettoyage complet dans **tous les scénarios**:
- ✅ **Timeout** (60s sans action) → `removePlayer()` détecte et nettoie le candidat
- ✅ **Bon mot de passe** (authentification réussie) → `clearQueueForAccount()` nettoie tout
- ✅ **Mauvais mot de passe** (échec) → `removeQueueCandidate()` nettoie immédiatement
- ✅ **Déconnexion manuelle/crash** → `removePlayer()` via `onPlayerLogout()` nettoie

**Modifications dans `ParkingLobbyManager.java`:**
```java
public void removePlayer(UUID playerId, ServerLevel world) {
    // ... (existing code)

    // Clean up candidate tracking if this player was a queue candidate
    boolean wasCandidate = false;
    String accountName = null;

    // Find which account this candidate belongs to
    for (Map.Entry<String, Set<UUID>> entry : queueCandidates.entrySet()) {
        if (entry.getValue().contains(playerId)) {
            accountName = entry.getKey();
            wasCandidate = true;
            break;
        }
    }

    // If player was a queue candidate, clean up all tracking
    if (wasCandidate && accountName != null) {
        removeQueueCandidate(accountName, playerId);
    }
}
```

**4. Comptage précis et fiable**
- Maps `candidateIPs` et `candidateJoinTime` nettoyées dans tous les cas
- Le nombre de candidats en queue est maintenant **toujours exact**
- Logs détaillés avec compteurs précis: `account candidates from IP: X, global candidates from IP: Y`

**5. Méthode d'éviction dédiée**
```java
private void evictCandidate(UUID candidateId, String accountName,
                            net.minecraft.server.MinecraftServer server, String reason) {
    removeQueueCandidate(accountName, candidateId);

    net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(candidateId);
    if (player != null) {
        String message = // Message personnalisé selon raison
        player.connection.disconnect(Component.literal(message));
    }
}
```

### Fichiers modifiés
- `ParkingLobbyManager.java`:
  - Ajout constantes DoS (MAX_CANDIDATES_*)
  - Modification `addQueueCandidate()` avec paramètre `server` pour éviction
  - Nouvelle méthode `evictCandidate()`
  - Fix `removePlayer()` pour nettoyage complet candidats
  - Décrémentation compteurs après éviction pour logs précis
- `ServerEventHandler.java`:
  - Passage paramètre `server` à `addQueueCandidate()`
  - Message d'erreur détaillé si limite dépassée

### Impact
- 🔒 **Sécurité renforcée** : Impossible de surcharger le serveur avec des candidats
- 📊 **Monitoring fiable** : Comptage exact des candidats en temps réel
- ♻️ **Gestion optimale** : Candidats anciens automatiquement remplacés
- 💬 **UX améliorée** : Messages clairs sur les limites et raisons de refus

---

## 🔧 Session du 6 octobre 2025 - Partie 3 (Corrections IP et Queue)

### Corrections critiques

**1. Support complet IPv6 et normalisation IP**
- **Problème** : Formats IP différents non détectés comme identiques
  - Nouvelle connexion: `/[0:0:0:0:0:0:0:1]:50645` (format complet avec brackets)
  - Joueur connecté: `::1` (format court sans brackets/slash)
  - Résultat: même IP créait doublons dans queue
- **Solution** : Méthode `normalizeIP()` dans Mixin
  - Supprime `/` et `[]`
  - Extrait IP sans port (gère IPv4 et IPv6)
  - Normalise `::1` → `0:0:0:0:0:0:0:1`
  - Appliquée dans `ParkingLobbyManager.extractIPWithoutPort()`
- **Tests** : IPv4 (`127.0.0.1:port`), IPv6 complet (`/[0:0:0:0:0:0:0:1]:port`), IPv6 court (`::1`)

**2. Refus connexion même IP sur même compte**
- **Problème** : Même IP se reconnectant pendant auth était ajoutée à queue
- **Solution** : Vérification AVANT `addToQueue()` dans Mixin
  - Compare IP normalisées (nouvelle vs connectée)
  - Si identiques → Refus direct avec message "Vous êtes déjà connecté"
  - Pas d'ajout à la queue, pas de kick
- **Log** : `MIXIN: IP 0:0:0:0:0:0:0:1 denied - same IP already connected on Joueur1`

**3. Affichage fenêtre monopole lors reconnexions**
- **Problème** : Reconnexion avec port différent ne récupérait pas fenêtre stockée
- **Cause** : Comparaison IP incluait port
- **Corrections appliquées** :
  - `getMonopolyWindow()` : compare sans port
  - `getPositionInQueue()` : compare sans port
  - `isAuthorized()` : compare sans port
  - `consumeAuthorization()` : supprime de queue sans port
- **Résultat** : Fenêtre affichée correctement à chaque reconnexion

**4. Formule fenêtre de monopole**
- **Avant** : `(position - 2) * 60s` → position 2 donnait 0ms
- **Après** : `(position - 1) * 60s` → position 2 donne +60s, position 3 donne +120s

### Fichiers modifiés
- `MixinServerLoginPacketListenerImplPlaceNewPlayer.java` : normalisation IP, vérification même IP
- `ParkingLobbyManager.java` : support IPv6 dans extraction IP

---

## 🚦 Session du 6 octobre 2025 - Partie 2 (Système de File d'Attente)

### Nouvelles fonctionnalités majeures

**1. File d'attente pour comptes protégés**
- **Protection anti-monopole** : Maximum 3 positions en file par IP globalement
- **File par compte** : Chaque compte protégé a sa propre file indépendante
- **Détection duplicata** : Une IP ne peut être qu'une seule fois par file
- **Blocage IP en auth** : Une IP en cours d'authentification ne peut pas rejoindre la file du même compte

**2. Fenêtres de monopole garanties**
- **Calcul initial garanti** : Fenêtre basée sur le pire scénario (tout le monde utilise son timeout complet)
- **Affichage horaire exact** : Format HH:MM:SS (ex: "De 15:51:00 à 15:51:30")
- **Promesse tenue** : La fenêtre affichée reste valide quoi qu'il arrive
- **Stockage persistant** : `monopolyStartMs` et `monopolyEndMs` dans chaque `QueueEntry`
- **Mise à jour intelligente** : Fenêtres peuvent s'avancer (jamais reculer)
- **Bonus temps** : Temps non utilisé transféré au suivant (reste + 30s)

**3. Timeouts différenciés**
- **Direct (60s)** : Connexion directe sur compte libre
- **Queue (30s)** : Connexion après autorisation depuis la file
- **Tracking origine** : Map `authorizedIPsFromQueue` avec clé "account:ip"
- **Application automatique** : Détection à l'ajout du joueur au parking lobby

**4. Gestion dynamique des files**
- **Autorisation automatique** : Prochain en file autorisé lors timeout/déconnexion
- **Extension de fenêtre** : Si déconnexion précoce, fenêtre prolongée (jamais raccourcie)
- **Nettoyage automatique** : File vidée en cas d'authentification réussie
- **Expiration entries** : Entrées de file expirées après 5 minutes

### Flux complet de la file d'attente

**Scénario: Individu2 essaie de se connecter pendant qu'Individu1 s'authentifie**

1. **Individu1 s'authentifie** (15:50:00, timeout 60s → 15:51:00)
2. **Individu2 se connecte** (15:50:15)
   - Mixin détecte compte occupé → Appelle `addToQueue()`
   - Calcul fenêtre garantie: 15:51:00 → 15:51:30 (pire cas)
   - `QueueEntry` créé avec cette fenêtre stockée
   - Message affiché: "Fenêtre de monopole: De 15:51:00 à 15:51:30"
3. **Individu2 réessaie** (15:50:25)
   - IP déjà en queue → Retourne même position
   - `getMonopolyWindow()` retourne fenêtre stockée (inchangée)
   - Message identique: "De 15:51:00 à 15:51:30" ✅
4. **Individu1 déconnecte** (15:50:30, reste 30s)
   - `ServerEventHandler` obtient `remainingTime = 30000ms`
   - `authorizeNextInQueue("joueur1", 30000)` appelé
   - Whitelist jusqu'à: 15:51:30 (fenêtre garantie honorée)
   - Temps réel: 15:50:30 + 30s + 30s = 15:51:30 ✅ Promesse tenue
5. **Individu2 se connecte** (15:51:00)
   - IP whitelistée jusqu'à 15:51:30 → Autorisé
   - `consumeAuthorization()` marque "joueur1:ip2" comme fromQueue
   - Timeout appliqué: 30s (détecté via map)

**Garantie absolue**: Peu importe les déconnexions, la fenêtre "15:51:00 → 15:51:30" reste valide.

### Architecture technique

**Classes modifiées**:
- `QueueEntry` : Ajout `monopolyStartMs` et `monopolyEndMs` (non-final, mutables)
- `AuthSession` : Ajout `timeoutMs` pour stocker durée exacte (60s ou 30s)

**Nouvelles méthodes**:
- `calculateGuaranteedMonopolyWindow(accountName, position)` : Calcul pire cas
- `getMonopolyWindow(accountName, ipAddress)` : Retourne fenêtre stockée
- `updateQueueWindowsAfterAuthorization(queue, newBaseTime)` : Avance fenêtres si possible
- `getRemainingTimeForAccount(accountName)` : Temps restant session active

**Méthodes modifiées**:
- `addToQueue()` : Calcule et stocke fenêtre garantie lors de l'ajout
- `authorizeNextInQueue()` : Accepte `remainingTimeMs`, prolonge fenêtre si bonus
- `addPlayer()` : Détecte origine queue via map, applique timeout approprié

### Messages affichés

**En file d'attente**:
```
§c§lCe compte est occupé

§eVous êtes en file d'attente
§7Position: §f1

§eFenêtre de monopole:
§7De §f15:51:00 §7à §f15:51:30

§7Vous aurez §e30 secondes§7 pour vous connecter pendant cette fenêtre.
```

**IP déjà en auth sur compte**:
```
§c§lConnexion refusée

§eVotre IP est déjà en cours d'authentification sur ce compte.
```

**Trop de files**:
```
§c§lConnexion refusée

§eTrop de tentatives de connexion simultanées.
§7Limite: 3 comptes en attente par IP.
```

### Corrections de bugs

**1. Fix ajout immédiat à la queue**
- **Problème**: Individus pas ajoutés à la queue quand quelqu'un s'authentifie
- **Cause**: Check `isProtectedDuringAuth` bloquait l'ajout avec message "Veuillez patienter 30 secondes"
- **Solution**: Suppression du check, ajout direct à la queue si compte occupé
- **Résultat**: Ajout immédiat en file d'attente, fenêtre de monopole affichée
- **Code nettoyé**: Méthodes `isProtectedDuringAuth()` supprimées (AuthManager + AdminAuthManager)

**2. Fix calcul fenêtre garantie**
- **Problème**: Fenêtre changeait si déconnexion précoce
- **Solution**: Stockage dans `QueueEntry`, jamais raccourcie, seulement prolongée
- **Résultat**: Promesse toujours tenue

**3. Fix obtention temps restant**
- **Problème**: `getRemainingTimeForAccount()` appelé après `removePlayer()`
- **Solution**: Obtention du temps AVANT suppression de la session
- **Fichier**: `ServerEventHandler.java:124`

**4. Fix tracking origine queue**
- **Problème**: UUID n'existe pas encore au moment du Mixin
- **Solution**: Clé "accountname:ipaddress" au lieu de UUID
- **Timing**: Ajout lors `consumeAuthorization()`, lecture lors `addPlayer()`

### Fichiers modifiés (6)

- `ParkingLobbyManager.java` :
  - `QueueEntry` avec fenêtres garanties
  - `AuthSession` avec timeout stocké
  - Nouvelles méthodes de calcul et mise à jour
  - Gestion du temps restant

- `ServerEventHandler.java` :
  - Obtention temps restant AVANT cleanup
  - Passage temps à `authorizeNextInQueue()`

- `MixinServerLoginPacketListenerImplPlaceNewPlayer.java` :
  - Suppression check `isProtectedDuringAuth`
  - Ajout direct à queue si compte occupé
  - Appel `getMonopolyWindow()` pour affichage
  - Format HH:MM:SS avec `SimpleDateFormat`

- `AuthManager.java` :
  - Suppression méthode `isProtectedDuringAuth()` (inutilisée)

- `AdminAuthManager.java` :
  - Suppression méthode `isProtectedDuringAuth()` (inutilisée)

### Exemples de scénarios garantis

**Timeout normal (60s)**:
- Individu1 timeout → Individu2 obtient 0s + 30s = **30s**

**Déconnexion précoce (40s restant)**:
- Individu1 déconnecte → Individu2 obtient 40s + 30s = **70s**
- Fenêtre garantie: 15:51:00 → 15:51:30 (durée 30s)
- Fenêtre réelle: 15:50:20 → 15:51:30 (durée 70s) ✅ Prolongée

**Multiple personnes**:
- Position 1: Fenêtre basée sur timeout session active
- Position 2: Fenêtre basée sur position 1 + 60s (pire cas)
- Position 3: Fenêtre basée sur position 2 + 60s (pire cas)

**Déconnexion en cascade**:
- Chaque déconnexion avance les fenêtres (jamais reculer)
- Durée de fenêtre préservée (toujours 30s minimum)

---

## 🔐 Session du 6 octobre 2025 - Partie 1 (Système de Joueurs Protégés et Priorité d'Accès)

### Nouvelles fonctionnalités majeures

**1. Système de joueurs protégés (10 comptes max)**
- **Nouveau type de compte** : PROTECTED_PLAYER (entre ADMIN et FREE_PLAYER)
- **Authentification obligatoire** : Mot de passe requis pour se connecter
- **Commandes dédiées** :
  - `/submode player add <joueur> <mdp>` : Ajouter un joueur protégé
  - `/submode player remove <joueur>` : Retirer un joueur protégé
  - `/submode player list` : Lister les 10 joueurs protégés
  - `/submode player setpassword <joueur> <mdp>` : Changer le mot de passe
- **Persistance** : Données sauvegardées dans `auth_credentials.json`
- **Sécurité** : SHA-256 + salt unique par joueur, comme pour les admins

**2. Parking Lobby avec timeout**
- **Zone d'attente** : Joueurs protégés gelés en spectateur jusqu'à authentification
- **Timer 60 secondes** : Kick automatique si pas d'authentification dans les 60s
- **Message clair** : "Temps d'authentification écoulé - Vous aviez 60 secondes"
- **Support des deux types** : Admins et joueurs protégés dans le même lobby
- **Cleanup automatique** : Timer annulé lors de la déconnexion ou succès auth

**3. Système de priorité d'accès**
- **Accès prioritaire** : Les comptes protégés peuvent se connecter même si serveur plein
- **Mixin PlayerList** : Injection dans `canPlayerLogin` pour contourner vérification vanilla
- **Kick intelligent** : Sélection aléatoire d'un FREE_PLAYER pour faire de la place
- **Protection complète** : Si tous les joueurs sont protégés, refuse connexion (message "serveur plein")
- **Limite dynamique** : Utilise `max-players` du server.properties au lieu de valeur hardcodée 10
- **Message kick** : "Vous avez été déconnecté pour faire de la place à un joueur prioritaire"

**4. Blacklist unifiée (comptes uniquement)**
- **3 tentatives = 3 minutes de blacklist** : Fixe pour tous les comptes protégés
- **Suppression IP blacklist** : Système d'IP blacklist complètement retiré du code
- **Tracking persistant** : Tentatives sauvegardées dans `account_blacklist` du JSON
- **Réinitialisation 24h** : Compteur remis à zéro après 24h d'inactivité
- **Section dédiée** : `account_blacklist` sépare des blacklists admins

**5. CredentialsStore - Gestionnaire centralisé**
- **Singleton unique** : Une seule instance pour tous les managers
- **Fichier unifié** : `auth_credentials.json` remplace `admin_credentials.json`
- **Synchronisation garantie** : Même objet JsonObject partagé entre AdminAuthManager et AuthManager
- **Sections structurées** :
  - `admins` : Comptes administrateurs
  - `protected_players` : 10 joueurs protégés
  - `blacklist` : Blacklist admins (3min fixe)
  - `account_blacklist` : Blacklist joueurs protégés (3min fixe)
  - `ipBlacklist` : Vide (legacy, inutilisé)

### Corrections de bugs

**1. Fix synchronisation credentials**
- **Problème** : Changements de mot de passe non persistants (deux fichiers séparés)
- **Solution** : CredentialsStore singleton avec un seul fichier auth_credentials.json
- **Méthodes retirées** : loadCredentials, saveCredentials, reloadCredentials dans les managers

**2. Fix case sensitivity**
- **Problème** : "Joueur5" ne pouvait pas se connecter avec nouveau mot de passe
- **Cause** : `.toLowerCase()` dans attemptProtectedPlayerLogin transformait en "joueur5"
- **Solution** : Préservation de la casse originale + fallback pour compatibilité
- **Ligne modifiée** : AuthManager.java:194

**3. Fix condition priority kick**
- **Problème** : FREE_PLAYER non kick quand serveur plein et joueur protégé se connecte
- **Cause** : Condition `<= maxPlayers` au lieu de `< maxPlayers`
- **Solution** : Changement de condition dans ServerEventHandler.java:126
- **Résultat** : Kick correct quand nombre de joueurs atteint la limite

**4. Fix Mixin bypass sans vérification FREE_PLAYER**
- **Problème** : Joueur protégé pouvait bypass même si tous les joueurs étaient protégés
- **Solution** : Ajout de boucle de vérification pour détecter au moins un FREE_PLAYER
- **Comportement** : Si aucun FREE_PLAYER, laisse vanilla gérer "serveur plein"

### Nettoyage de code

**Imports retirés** :
- `Gson`, `GsonBuilder` : AdminAuthManager et AuthManager
- `File`, `FileReader`, `FileWriter` : AdminAuthManager et AuthManager
- `StandardCharsets`, `IOException` : AdminAuthManager et AuthManager

**Méthodes supprimées** :
- `loadCredentials()` : AdminAuthManager et AuthManager
- `saveCredentials()` : AdminAuthManager et AuthManager
- `reloadCredentials()` : AdminAuthManager

**Code redondant éliminé** :
- Gestion des fichiers en double dans les deux managers
- Appels croisés entre managers pour reload

### Fichiers créés (3)

- `ParkingLobbyManager.java` : Gestion lobby d'attente avec timer 60s
- `CredentialsStore.java` : Singleton pour auth_credentials.json
- `MixinPlayerListServerFull.java` : Injection canPlayerLogin pour priorité

### Fichiers modifiés (8)

- `AuthManager.java` : Support joueurs protégés + CredentialsStore
- `AdminAuthManager.java` : Migration vers CredentialsStore + nettoyage
- `ServerEventHandler.java` : Parking lobby + priority kick + fix condition
- `SubModeCommand.java` : 4 nouvelles commandes joueurs protégés
- `AdminAuthPacket.java` : Support joueurs protégés avec blacklist
- `AdminAuthScreen.java` : Support joueurs protégés dans UI
- `mysubmod.mixins.json` : Ajout MixinPlayerListServerFull
- `README_SUBMOD.md` : Documentation complète du nouveau système

### Architecture technique

**Flux d'authentification joueur protégé** :
1. Connexion → AuthManager détecte PROTECTED_PLAYER
2. ParkingLobbyManager ajoute joueur avec timer 60s
3. Client reçoit packet auth request
4. AdminAuthScreen affiche prompt (réutilisé pour joueurs protégés)
5. Joueur entre mot de passe → packet vers serveur
6. AuthManager.attemptProtectedPlayerLogin vérifie et suit tentatives
7. Succès → retire du lobby + authentifie | Échec → compte tentatives | 3 échecs → blacklist 3min

**Flux priorité d'accès** :
1. Mixin intercepte canPlayerLogin quand serveur >= max-players
2. Vérifie AccountType du joueur qui se connecte
3. Si ADMIN ou PROTECTED_PLAYER → cherche FREE_PLAYER disponible
4. Si FREE_PLAYER trouvé → retourne null (autorise connexion)
5. ServerEventHandler détecte dépassement capacité → kick FREE_PLAYER aléatoire
6. Si aucun FREE_PLAYER → laisse vanilla refuser (message "serveur plein")

---

## 🎮 Session du 5 octobre 2025 - Partie 2 (Améliorations UX et Logs)

### Corrections de bugs et améliorations

**1. Affichage du compteur de joueurs dans le menu M**
- **Problème** : Aucune visibilité sur le nombre de joueurs non-admin connectés
- **Solution** :
  - Nouveau système de packets client-serveur pour obtenir le compteur
  - `SubModeControlScreenRequestPacket` : Client → Serveur
  - `SubModeControlScreenPacket` : Serveur → Client avec compteur
  - Affichage en vert sous le mode actuel : "Joueurs connectés: X"
  - Position ajustée pour éviter chevauchement avec bouton "Salle d'attente"

**2. Notification fin de partie pour blocage menu N**
- **Problème** : Menu N (sélection fichier bonbons) restait accessible pendant transition fin de partie
- **Solution** :
  - Nouveau packet `GameEndPacket` envoyé à tous les clients quand partie se termine
  - Flag `gameEnded` dans `ClientGameTimer` activé par packet
  - Vérification dans `ClientEventHandler` avant ouverture menu N
  - Message d'erreur : "Le menu de sélection de fichier est désactivé après la fin de la partie"
  - Réinitialisation du flag lors du changement de mode

**3. Correction format CSV des logs (problème locale française)**
- **Problème** : Coordonnées avec virgules comme séparateurs décimaux (ex: "3,20" au lieu de "3.20")
- **Cause** : `String.format()` utilise la locale système par défaut
- **Solution** :
  - Ajout de `Locale.US` à tous les `String.format()` dans `SubMode1DataLogger`
  - Force l'utilisation du point décimal indépendamment de la locale système
  - Concerne : position, candy pickup/consumption, health change, death, island selection

**4. Gestion intelligente des logs de sélection d'île**
- **Problème 1** : Sélection d'île loggée deux fois si déconnexion avant début partie
- **Problème 2** : Type de sélection (MANUAL/AUTOMATIC) non préservé à la reconnexion
- **Solution** :
  - Map `playerIslandManualSelection` : Tracke si sélection manuelle (true) ou auto (false)
  - Set `playerIslandSelectionLogged` : Tracke quels joueurs ont déjà eu leur sélection loggée
  - Sélection manuelle (`selectIsland`) : Marque comme manual + logged
  - Auto-assignation (`endSelectionPhase`) : Marque comme automatic + logged
  - Reconnexion : Log uniquement si jamais loggé ET (île assignée pendant reconnexion OU déconnexion avant début)
  - Cleanup des Maps lors de la désactivation

**Cas d'usage couverts** :
- ✅ Joueur sélectionne île → Log MANUAL
- ✅ Joueur ne sélectionne pas → Auto-assigné → Log AUTOMATIC
- ✅ Joueur sélectionne, se déconnecte, se reconnecte après début → Pas de re-log
- ✅ Joueur sélectionne, se déconnecte avant début, reconnecte après → Log MANUAL (première téléportation)
- ✅ Joueur déconnecté pendant sélection, reconnecte après → Auto-assigné → Log AUTOMATIC

**5. Amélioration gestion des joueurs rejoignant pendant fileSelectionPhase**
- **Problème** : Joueurs rejoignant pendant sélection du fichier par l'admin étaient spectateurs
- **Solution** :
  - Vérification `isFileSelectionPhase()` dans `SubMode1EventHandler.onPlayerJoin`
  - Joueurs non-admin ajoutés à `playersInSelectionPhase` et téléportés au carré central
  - Admins restent en mode spectateur
  - Lors reconnexion : même logique appliquée

**6. Protection admin pendant authentification (30 secondes)**
- **Problème** : Admin pouvait être kick pour connexion double pendant saisie du mot de passe
- **Solution** :
  - Map `authenticationStartTime` dans `AdminAuthManager`
  - Méthode `startAuthenticationProtection()` appelée quand auth request envoyé
  - Méthode `isProtectedDuringAuth()` vérifie si moins de 30 secondes écoulées
  - Mixin vérifie protection avant bloquer connexion
  - Message spécifique : "Un administrateur est en cours d'authentification sur ce compte. Veuillez patienter 30 secondes."
  - Cleanup automatique après 30 secondes ou déconnexion

**7. Texte bouton confirmation sélection fichier**
- **Modification** : "✓ Confirmer la sélection" → "✓ Confirmer et lancer la partie"
- **Raison** : Clarifier que la sélection lance immédiatement la partie

**Fichiers créés (3)** :
- `SubModeControlScreenRequestPacket.java` : Requête compteur joueurs
- `SubModeControlScreenPacket.java` : Réponse avec compteur
- `GameEndPacket.java` : Notification fin de partie

**Fichiers modifiés (12)** :
- `SubModeControlScreen.java` : Affichage compteur + position ajustée
- `ClientEventHandler.java` : Requête compteur + vérification gameEnded
- `ClientGameTimer.java` : Flag gameEnded + méthode markGameAsEnded()
- `NetworkHandler.java` : Enregistrement 3 nouveaux packets
- `SubMode1Manager.java` : Maps tracking sélection + logique intelligente reconnexion
- `SubMode1DataLogger.java` : Locale.US sur tous les String.format
- `SubMode1EventHandler.java` : Gestion fileSelectionPhase dans onPlayerJoin
- `CandyFileSelectionScreen.java` : Texte bouton modifié
- `AdminAuthManager.java` : Protection 30 secondes
- `ServerEventHandler.java` : Appel startAuthenticationProtection
- `MixinServerLoginPacketListenerImplPlaceNewPlayer.java` : Vérification isProtectedDuringAuth

**Impact** :
- UX améliorée : Visibilité compteur joueurs, messages clairs
- Logs corrects : Format CSV standard, pas de doublons, type correct (MANUAL/AUTOMATIC)
- Protection robuste : Pas de kick admin pendant auth, menu N bloqué après partie
- Gestion joueurs : Traitement cohérent pendant toutes les phases

---

## 🛡️ Session du 5 octobre 2025 - Partie 1 (Protection Connexions Duplicates)

### Système de Protection contre les Connexions Doubles

**Objectif** : Empêcher les connexions simultanées avec le même compte via Mixins

**Fichier créé** (1 nouveau) :
- **MixinServerLoginPacketListenerImplPlaceNewPlayer.java** : Mixin injectant dans `handleAcceptedLogin`
  - Injection à `@At("HEAD")` pour intercepter AVANT le kick vanilla
  - Détection des duplicatas par nom (UUID null en phase login)
  - Logique personnalisée selon type de compte et état authentification
  - Utilisation de `ClientboundLoginDisconnectPacket` pour messages visibles

**Fichiers modifiés** (3) :
- **build.gradle** : Configuration MixinGradle plugin 0.7.+
  - Annotation processor Mixin 0.8.5
  - Bloc mixin avec refmap configuration
- **mysubmod.mixins.json** : Déclaration du Mixin
  - Package et compatibilité JAVA_17
  - Référence au refmap généré
- **mods.toml** : Déclaration de la config Mixin à Forge

**Fonctionnalités** :
- ✅ Admin authentifié : Bloque nouvelle connexion, garde session existante
- ✅ Admin non-authentifié : Laisse vanilla kicker l'ancienne session
- ✅ Joueur normal : Bloque nouvelle connexion, garde session existante
- ✅ Messages personnalisés selon type de compte et situation
- ✅ Logging détaillé de chaque tentative de connexion

**Logique de protection** :
```java
if (joueur existe déjà) {
  if (est admin) {
    if (authentifié) {
      → Bloquer nouvelle connexion avec message
    } else {
      → Laisser vanilla kicker ancienne session
    }
  } else {
    → Bloquer nouvelle connexion avec message
  }
}
```

**Messages affichés** :
- Admin authentifié : "§c§lConnexion refusée\n\n§eUn administrateur authentifié utilise déjà ce compte."
- Joueur normal : "§c§lConnexion refusée\n\n§eCe compte est déjà utilisé par un autre joueur."

**Technique** :
- Injection point : `ServerLoginPacketListenerImpl.handleAcceptedLogin` (avant kick vanilla)
- Shadow fields : `connection`, `gameProfile`, `server`
- Détection : Itération sur `PlayerList.getPlayers()` avec comparaison par nom
- Callback cancellable : `CallbackInfo ci` avec `ci.cancel()`
- Packet de déconnexion : `ClientboundLoginDisconnectPacket` (phase login)

**Nettoyage** :
- Suppression de `MixinPlayerList.java` (approche abandonnée - trop tard)
- Suppression de `MixinPlayerListPlaceNewPlayer.java` (approche abandonnée - trop tard)

**Résultat** : Protection robuste contre connexions doubles avec logique différenciée selon authentification admin

---

## 🔐 Session du 4 octobre 2025 (Système d'Authentification Admin)

### 🛡️ Système d'Authentification Complet

**Objectif** : Sécuriser l'accès admin avec authentification par mot de passe en mode offline

**Fichiers créés** (5 nouveaux) :
- **AdminAuthManager.java** : Gestionnaire central d'authentification
  - Hachage SHA-256 avec salt unique par admin
  - Blacklist progressive (3min × 10^n)
  - Réinitialisation automatique après 24h
  - Persistance des tentatives dans JSON
- **AdminPasswordScreen.java** : Interface client de saisie
  - Masquage du mot de passe (astérisques)
  - Compteur de tentatives visible
  - Impossible à fermer avec ESC
  - UI correctement espacée
- **AdminAuthPacket.java** : Envoi mot de passe client → serveur
- **AdminAuthRequestPacket.java** : Demande d'authentification serveur → client
- **AdminAuthResponsePacket.java** : Résultat authentification serveur → client

**Fichiers modifiés** (4) :
- **NetworkHandler.java** : Enregistrement des 3 nouveaux packets
- **ServerEventHandler.java** : Prompt automatique à la connexion admin
- **SubModeCommand.java** : Ajout commandes setpassword, resetblacklist, resetfailures
- **SubModeManager.java** : Vérification authentification dans isAdmin()

**Fonctionnalités** :
- ✅ Prompt automatique pour tous les comptes admin (OP 2+ ou liste admin)
- ✅ 3 tentatives par session, persistantes même après déconnexion
- ✅ Blacklist progressive : 3min → 30min → 300min → ... (×10)
- ✅ Kick automatique si blacklisté avec temps restant affiché
- ✅ Réinitialisation auto du compteur d'échecs après 24h
- ✅ Stockage sécurisé dans `admin_credentials.json`
- ✅ Ops peuvent définir leur mot de passe initial sans authentification
- ✅ Synchronisation admin status après authentification réussie

**Sécurité** :
- Mots de passe hashés avec SHA-256 + salt unique (Base64)
- Fichier `admin_credentials.json` avec structure admins/blacklist
- Distinction tentatives tracking vs blacklist active (champ "until")
- Code client/serveur correctement séparé avec DistExecutor

**Correctifs importants** :
- ✅ Fix crash NullPointerException (vérification champ "until" avant lecture)
- ✅ Fix UUID offline mode (utilisation du vrai UUID généré)
- ✅ Fix méthode de hachage (concatenation au lieu de md.update)
- ✅ Fix UI overlapping (espacement correct des éléments)
- ✅ Fix admin status non mis à jour après auth

---

## 🎯 Session du 4 octobre 2025 (Optimisations et Correctifs)

### 🧹 Nettoyage des logs (Réduction de 26%)
**Objectif** : Réduire le bruit dans les logs serveur pour faciliter le débogage

**Logs supprimés (20 total)** :
- **SubMode1Manager.java** (11 suppressions) :
  - Messages de file d'attente d'événements (4x "Queued event for player")
  - Messages de flush rétroactif détaillés (2x dans la boucle)
  - Debug de téléportation avec coordonnées
  - Debug de réinitialisation de santé individuelle
  - Debug de cartes non trouvées
  - Debug de détection d'îles physiques
  - Debug de blocs solides détectés

- **SubMode1CandyManager.java** (7 suppressions) :
  - Debug de planification de spawn avec position détaillée
  - Debug de spawn individuel de bonbon avec coordonnées
  - Debug de suppression de bonbon avec position
  - Debug de comptage de bonbon avec détails entité
  - Info de déchargement de chunk
  - Debug de ramassage de bonbon avec position

- **SubMode1EventHandler.java** (2 suppressions) :
  - Debug de blocage d'items avec position
  - Debug de blocage de spawn de monstre avec position

- **WaitingRoomEventHandler.java** (1 suppression) :
  - Debug de blocage de spawn de monstre près de la plateforme

- **SubMode1DataLogger.java** (1 suppression) :
  - Debug de spawn de bonbon avec coordonnées

**Logs simplifiés (4 total)** :
- Reconnexion des joueurs : 3 logs consolidés en 1 seul message concis
- Spawn de bonbons : Coordonnées détaillées supprimées (temps et quantité conservés)

**Résultat** : 116 log statements → 86 (focus sur les événements critiques uniquement)

---

### 🗑️ Suppression du code inutile (5 éléments)

**Méthodes redondantes** :
- `SubModeManager.isPlayerAdmin()` → Remplacé par `isAdmin()` dans 4 fichiers :
  - SubMode1Manager.java (ligne 138)
  - CandyFileSelectionPacket.java (ligne 31)
  - CandyFileDeletePacket.java (ligne 31)
  - CandyFileUploadPacket.java (ligne 35)

**Méthodes jamais utilisées** :
- `SubMode1CandyManager.getActiveCandyCount()` : 0 appels dans tout le projet
- `IslandType.getSpawnPointCount()` : 0 appels dans tout le projet

**Champs inutilisés** :
- `IslandType.spawnPointCount` : Seulement accédé par le getter inutilisé (supprimé des 4 enums)

**Imports inutilisés** :
- `ClientEventHandler` : Import de CandyFileListManager jamais utilisé

**Impact** : Code plus propre, moins de maintenance, compilation plus rapide

---

### 🐛 Corrections de bugs critiques

#### 1. **Détection de monstres hostiles (Cercle → Carré)**
**Problème** : Les monstres étaient bloqués via détection circulaire (`isWithinRadius()`) alors que les îles sont carrées

**Solution** :
- Nouvelle méthode `isWithinSquare()` utilisant `Math.abs()` pour X et Z
- Zones de protection précises correspondant aux îles :
  - SMALL (60x60) : Protection 35 blocs (30 + buffer 5)
  - MEDIUM (90x90) : Protection 50 blocs (45 + buffer 5)
  - LARGE (120x120) : Protection 65 blocs (60 + buffer 5)
  - EXTRA_LARGE (150x150) : Protection 80 blocs (75 + buffer 5)
  - Central Square (20x20) : Protection 15 blocs (10 + buffer 5)
  - Spectator (30x30) : Protection 20 blocs (15 + buffer 5)

**Fichier** : `SubMode1EventHandler.java:438-443`

**Résultat** : Protection cohérente avec la forme réelle des îles

#### 2. **Cycle jour/nuit bloqué pendant TOUT le sous-mode**
**Problème** : La nuit pouvait arriver pendant la sélection de fichier si l'admin prenait trop de temps

**Solution** :
- Déplacement du check de daylight AVANT la vérification `isGameActive()`
- Le temps est maintenant bloqué à midi (6000 ticks) dès l'activation jusqu'à la désactivation
- Inclut : phase de sélection de fichier, phase de sélection d'îles, et partie active

**Fichier** : `SubMode1EventHandler.java:270-277`

**Résultat** : Toujours jour pendant toute la durée du SubMode1

---

### 🛡️ Protection contre les changements de sous-mode trop rapides

**Problème** : Les admins pouvaient cliquer trop rapidement sur les boutons de changement de mode

**Solution** :
- Cooldown de **5 secondes** entre chaque changement de mode
- Variables ajoutées dans `SubModeManager` :
  - `lastModeChangeTime` : Timestamp du dernier changement
  - `MODE_CHANGE_COOLDOWN_MS = 5000` : Constante de cooldown
- Message d'erreur avec temps restant : "§cChangement de sous-mode trop rapide ! Veuillez attendre X seconde(s)..."
- Vérification avant le lock `isChangingMode`

**Fichiers modifiés** :
- `SubModeManager.java:20-21` (variables)
- `SubModeManager.java:66-78` (vérification cooldown)
- `SubModeManager.java:137` (mise à jour timestamp)

**Résultat** : Protection du serveur contre les changements de mode trop fréquents

---

### 📊 Logging rétroactif des déconnexions/reconnexions

**Problème** : Les joueurs qui se déconnectaient AVANT la sélection du fichier de spawn n'étaient pas loggés car le dataLogger n'existait pas encore

**Solution** : Système de file d'attente d'événements
- **Classe `PendingLogEvent`** : Stocke player, action, timestamp
- **Liste `pendingLogEvents`** : File d'attente des événements avant création du logger
- **Mécanisme** :
  1. Déconnexion/reconnexion avant sélection fichier → Événement mis en file
  2. Sélection du fichier → dataLogger créé dans `startIslandSelection()`
  3. Tous les événements en file sont flushés rétroactivement
  4. Liste nettoyée

**Cas couverts** :
- ✅ Déconnexion pendant `fileSelectionPhase` → Mis en file → Loggé rétroactivement
- ✅ Reconnexion pendant `fileSelectionPhase` → Mis en file → Loggé rétroactivement
- ✅ Reconnexion pendant `selectionPhase` → Loggé immédiatement (dataLogger existe)
- ✅ Déconnexion/reconnexion pendant partie → Loggé immédiatement (dataLogger existe)

**Fichiers modifiés** :
- `SubMode1Manager.java:37` (liste pendingLogEvents)
- `SubMode1Manager.java:2003-2013` (classe PendingLogEvent)
- `SubMode1Manager.java:1668-1670` (handlePlayerDisconnection)
- `SubMode1Manager.java:1821-1823,1847-1848,1883-1885` (handlePlayerReconnection - 3 cas)
- `SubMode1Manager.java:1125-1134` (flush des événements)

**Résultat** : Couverture complète du logging, aucun événement perdu

---

### 📝 Documentation mise à jour

**CHANGELOG.md** :
- Nouvelle section "Session du 4 octobre 2025" au début
- Détails complets de tous les changements
- Impact et résultats pour chaque modification

**SUBMODE1_GUIDE.md** :
- Mise à jour "Protection de l'environnement" avec détection carrée
- Ajout mention du cycle jour/nuit bloqué pendant TOUT le sous-mode
- Suppression des références aux spawn points (remplacé par coordonnées)

**README_SUBMOD.md** :
- Nouvelle section "Optimisations et Correctifs (4 octobre 2025)"
- Liste des 6 améliorations principales
- Mise à jour des fonctionnalités avec monstres bloqués et jour permanent

---

## 🍬 Session du 3 octobre 2025 (Refonte système de spawn)

### Nouveau format de fichiers de spawn

**Changement majeur** : Format 4 paramètres → 5 paramètres
- **Ancien** : `temps,quantité,île,spawn_point`
- **Nouveau** : `temps,quantité,x,y,z`

**Avantages** :
- Spawn au bloc exact spécifié (plus de randomisation)
- Contrôle total sur les positions
- Validation précise des coordonnées

**Validation renforcée** :
- Temps : 0-900 secondes (15 minutes)
- Quantité : 1-100 bonbons (augmenté de 50 → 100)
- Y (hauteur) : 100-120 strictement
- X et Z : Vérification carrée dans les limites des îles

**Coordonnées des îles** :
- SMALL (60x60) : Centre (0, -360), X: -30 à 30, Z: -390 à -330
- MEDIUM (90x90) : Centre (360, 0), X: 315 à 405, Z: -45 à 45
- LARGE (120x120) : Centre (0, 360), X: -60 à 60, Z: 300 à 420
- EXTRA_LARGE (150x150) : Centre (-360, 0), X: -435 à -285, Z: -75 à 75

**Fichiers modifiés** :
- `CandySpawnEntry.java` : Champ `spawnPointNumber` → `BlockPos position`
- `CandySpawnFileManager.java` : Parsing 5 paramètres + validation carrée
- `SubMode1CandyManager.java` : Spawn aux coordonnées exactes

---

### Système de sélection de fichiers amélioré

**Touche N** : Ouvre le menu de sélection de fichiers (admins uniquement)
- **Requête serveur** : `CandyFileListRequestPacket` pour liste fraîche à chaque ouverture
- **Blocage intelligent** : Impossible de sélectionner pendant une partie active
- **Lancement automatique** : Sélectionner un fichier démarre la phase de sélection d'îles immédiatement
- **Timer supprimé** : Plus de sélection automatique après 30 secondes

**Menus modernisés** :
- Liste défilante (`ObjectSelectionList`) au lieu de CycleButton
- Sélection par clic sur les entrées
- Highlight au survol
- Icônes distinctifs (📄 pour default.txt, 📁 pour les autres)
- Bouton actualiser pour rafraîchir la liste

**Fichiers créés/modifiés** :
- `CandyFileSelectionScreen.java` : Interface complète avec liste défilante
- `CandyFileUploadScreen.java` : Upload avec validation
- `CandyFileListRequestPacket.java` : Requête de rafraîchissement
- `ClientEventHandler.java` : Gestion touche N

---

### Nettoyage du code

**SpawnPointManager.java** : Complètement supprimé (plus nécessaire)

**Code redondant supprimé** :
- Génération de spawn points aléatoires
- Méthodes `generateSpawnPoints()`, `getRandomSpawnPoint()`, etc.
- Map `candyIslands` (tracking par île supprimé car pas nécessaire)
- Vérification de distance minimum entre spawn points

**Simplification** :
- Code de spawn direct et clair
- Moins de couches d'abstraction
- Validation plus stricte

---

### Amélioration de l'UX

**Fermeture automatique des menus** :
- Tous les menus (sélection île, fichiers, logs, admin) se ferment automatiquement lors de la désactivation du sous-mode
- Ajouté dans `WaitingRoomManager.deactivate()` et `SubMode1Manager.deactivate()`

**Hologrammes nettoyés** :
- Tracking des hologrammes dans une `ArrayList` lors de la création
- Suppression directe de la liste à la désactivation
- Tag "SubMode1Hologram" ajouté à tous les hologrammes
- Cleanup des orphelins au premier joueur connecté (via `WaitingRoomEventHandler`)

**Tooltips actualisés** :
- Bouton upload : "Charger un fichier de spawn de bonbons depuis le disque"

---

### Documentation complète

**CANDY_SPAWN_GUIDE.md** : Nouveau fichier
- Format détaillé avec exemples
- Carte des îles avec coordonnées exactes
- 4 exemples de fichiers :
  1. Simple (spawn basique)
  2. Test rapide (10 premières secondes)
  3. Périmètre (validation des limites)
  4. Distribution équilibrée
- Conseils et bonnes pratiques
- Troubleshooting

**Fichiers de test inclus** :
- `default.txt` : Configuration par défaut
- `test_simple.txt` : Test rapide
- `test_perimetre.txt` : Validation des limites

---

## 🎨 Session du 2 octobre 2025 (Interface et protection)

### Système de gestion des logs

**Interface 📊 complète** :
- Liste défilante moderne avec `ObjectSelectionList`
- Sélection par clic sur les dossiers de logs
- **Téléchargement sélectif** : Compression ZIP d'une session → Dossier Downloads Windows
- **Téléchargement en masse** : Tous les logs en un seul fichier ZIP
- **Suppression sélective** : Supprimer une session individuelle
- **Suppression en masse** : Nettoyer tous les logs d'un coup
- **Actualisation** : Bouton pour rafraîchir la liste
- **Sécurité** : Validation admin côté serveur via packets

**Fichiers créés** :
- `LogManager.java` : Gestionnaire serveur (compression ZIP, suppression)
- `LogManagementScreen.java` : Interface client avec liste
- `LogPacketHandler.java` : Gestion client des packets
- `LogListRequestPacket.java` : Demande de liste
- `LogListPacket.java` : Envoi de liste au client
- `LogDownloadPacket.java` : Téléchargement
- `LogDeletePacket.java` : Suppression

---

### Protection renforcée

**Blocage items étendu** :
- **Avant** : Seuls les pissenlits (dandelions) bloqués
- **Après** : TOUS les ItemEntity bloqués sur îles et chemins
- **Exception** : Seuls les bonbons du système (avec `glowingTag`) autorisés
- **Mécanisme** : `EntityJoinLevelEvent` vérifie `isNearIslandOrPath()`

**Sprint désactivé** :
- Modificateur d'attribut `SPRINT_SPEED_REDUCTION` appliqué à tous les joueurs vivants
- `AttributeModifier.Operation.MULTIPLY_TOTAL` avec valeur -1.0
- Vitesse de sprint = vitesse de marche normale
- Appliqué dans `SubMode1EventHandler` à chaque tick

**Correction HUD** :
- Le HUD des bonbons et le timer ne persistent plus après déconnexion/reconnexion
- Désactivation explicite dans les event handlers de déconnexion
- Réactivation contrôlée à la reconnexion si approprié

---

### Monde vide par défaut

**Configuration serveur** (`server.properties`) :
- `level-type=minecraft:flat` : Type monde plat
- `generator-settings={"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:plains"}` : Couche d'air vide
- `entity-broadcast-range-percentage=300` : Visibilité étendue pour voir les bonbons de loin
- `allow-flight=true` : Permet aux admins de voler en mode spectateur

**Avantages** :
- Performance améliorée (pas de génération de terrain)
- Focus sur les îles générées par le mod
- Pas d'exploration inutile

---

### Nettoyage du code (Session 2 octobre)

**Code mort supprimé** :
- Méthode `removeDroppedFlowers()` jamais appelée
- Variables `flowerCleanupTicks` et `FLOWER_CLEANUP_INTERVAL` inutilisées

**Documentation mise à jour** :
- Tous les fichiers .md reflètent l'état actuel
- Suppression des références obsolètes

---

## 🎨 Sessions précédentes (Septembre 2025)

### Système d'hologrammes pour indicateurs directionnels

**Remplacement des panneaux** :
- Hologrammes flottants au lieu de panneaux (texte plus stable sans distorsion)
- Position : Au-dessus des tours de laine colorées au carré central
- Format : Espacement entre lettres (ex: "P E T I T E  Î L E")

**Couleurs** :
- Blanc : Petite île + texte taille gris clair
- Vert : Île moyenne + texte taille gris clair
- Bleu : Grande île + texte taille gris clair
- Orange : Très grande île + texte taille gris clair

**Code obsolète supprimé** :
- `placeSignOnWool()`, `placeSignWithText()`, `getRotationFromDirection()`
- `createPathHolograms()` (indicateurs sur chemins retirés)
- Protection des panneaux dans event handlers
- Renommage `removeSignItems` → `removeHolograms`

---

### Protection contre les pissenlits

**Prévention du spawn** :
- Les pissenlits (dandelions) bloqués comme ItemEntity sur îles/chemins
- Event handler : `EntityJoinLevelEvent` pour bloquer avant apparition
- Zone : Détection via `isNearIslandOrPath()`

**Cleanup supprimé** :
- Plus besoin de nettoyer après coup
- Spawn bloqué à la source

---

### Correction de la dégradation de santé

**Timing corrigé** :
- La perte de vie ne s'active PLUS pendant sélection des îles (30 secondes)
- Activation uniquement quand `gameActive == true`
- Vérification : `if (!SubMode1Manager.getInstance().isGameActive())`

---

### Message de chargement

**Notification** :
- Message "§e§lChargement du sous-mode 1..." affiché à tous
- Timing : Dès le début de `activate()` avant génération

---

### Extension du système d'îles (4 îles)

**Nouvelle disposition** :
- 4 îles carrées autour d'un carré central (20x20)
- Carré central : Point de spawn initial

**Tailles** :
- Petite (60x60) : 1 spawn point → Coordonnées directes maintenant
- Moyenne (90x90) : 2 spawn points → Coordonnées directes maintenant
- Grande (120x120) : 3 spawn points → Coordonnées directes maintenant
- **Très grande (150x150)** : 4 spawn points → Coordonnées directes maintenant

**Distance** : 360 blocs entre centre et chaque île

**Chemins** : 4 chemins de 360 blocs reliant îles au centre

---

### HUD des ressources en temps réel

**Affichage** :
- Position : Coin supérieur droit (non-invasif)
- Contenu : Nombre de bonbons disponibles par île
- Mise à jour : Toutes les 2 secondes via `CandyCountUpdatePacket`

**Couleurs** :
- Petite : Blanc
- Moyenne : Vert
- Grande : Bleu
- Très Grande : Orange

**Désactivation** : Automatique à la fin de partie

**Fichiers** :
- `CandyCountHUD.java` : Logique affichage
- `CandyCountHUDRenderer.java` : Event handler rendu
- `CandyCountUpdatePacket.java` : Synchronisation réseau

---

### Fin de partie automatique

**Double condition** :
- Timer 15 minutes écoulé, OU
- Tous les joueurs morts

**Messages appropriés** selon condition

**Nettoyage complet** :
- Carré central, 4 îles, chemins, barrières
- Désactivation HUD et timer

---

### Système de logging amélioré

**Nouvelles données** :
- Choix d'île : Manuel ou automatique pour chaque joueur
- Spawn de bonbons : Position exacte (x,y,z) maintenant
- Timestamps au milliseconde pour toutes les actions

**Structure** :
- `mysubmod_data/submode1_game_[timestamp]/`
- Logs individuels par joueur
- Fichier `game_events.txt` global

---

### Téléportation sécurisée

**Chargement de chunks** :
- Forcer chargement avant téléportation
- Évite déconnexions pour distances de 360 blocs

**Méthode `safeTeleport`** :
- `getChunkAt()` pour charger chunk
- `moveTo()` puis `teleportTo()` pour positionnement
- Logging de debug pour suivi

---

### Tracking des ressources

**Map `candyIslands`** : Association bonbon ↔ île (SUPPRIMÉE dans refonte 3 octobre)

**Méthode `getAvailableCandiesPerIsland()`** : Comptage en temps réel

**Nettoyage** : À la collecte et fin de partie

---

### Écrans d'interface

**Sélection d'île** :
- 4 options au lieu de 3
- Affichage dimensions dans noms
- `getDisplayName()` pour cohérence

**Gestion fichiers** :
- Sélection : Liste déroulante moderne
- Upload : Nom + contenu avec validation
- Suppression : Protection default.txt

---

### Barrières et chemins

**Carré central** :
- Barrières avec ouvertures pour 4 chemins
- `isPathConnectionPoint()` mis à jour

**Connexions** :
- Chemins radiaux propres entre centre et îles

---

### Synchronisation réseau

**Packets ajoutés** :
- `CandyCountUpdatePacket` : HUD
- `CandyFileListPacket` : Liste fichiers
- `CandyFileSelectionPacket` : Sélection
- `CandyFileUploadPacket` : Upload
- `CandyFileDeletePacket` : Suppression
- `CandyFileListRequestPacket` : Requête rafraîchissement
- `LogListRequestPacket` : Liste logs
- `LogListPacket` : Envoi liste
- `LogDownloadPacket` : Téléchargement
- `LogDeletePacket` : Suppression

**Enregistrement** : Tous dans `NetworkHandler`

**Timing** : Envoi selon besoins (2s pour HUD, on-demand pour autres)

---

### Nettoyage variables inutilisées (Sessions précédentes)

**Supprimé** :
- `CANDIES_PER_PLAYER` (obsolète avec fichiers config)
- `LARGE_ISLAND_RATIO`, `MEDIUM_ISLAND_RATIO`, `SMALL_ISLAND_RATIO` (obsolètes)
- `totalCandiesTarget`, `largeCandiesTarget`, etc. (jamais utilisées)
- Méthode `getSpawnedCount()` (jamais appelée)

---

### Changements initiaux (Septembre 2025)

**Îles carrées** :
- Conversion de circulaires → carrées
- Tailles originales : 15x15, 25x25, 35x35
- Barrières invisibles avec ouvertures

**Système de santé** :
- Santé initiale : 100%
- Faim initiale : 50% (au lieu de 100%)
- Dégradation : -0.5 cœur / 10s

**Bonbons améliorés** :
- Expiration supprimée (persistance)
- Effet lumineux et glowing
- Distribution selon fichier config

**Logging** :
- Structure dans `mysubmod_data/`
- Positions, actions, événements détaillés

---

## 🎯 Prochaines améliorations prévues

- Texture personnalisée pour l'item bonbon (modèle JSON prêt)
- Implémentation du Sous-mode 2
- Interface d'administration avancée
- Outils d'analyse des données collectées
- Système de replay des parties

---

*Dernière mise à jour : 5 octobre 2025 - 19h05*
