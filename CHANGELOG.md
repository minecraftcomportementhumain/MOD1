# Changelog - MySubMod

## 🔐 Session du 6 octobre 2025 - Système de Joueurs Protégés et Priorité d'Accès

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
