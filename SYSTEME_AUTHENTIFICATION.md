# 🔐 SYSTÈME D'AUTHENTIFICATION

**Version:** 1.1.0
**Date:** 2025-10-21
**Mod:** MySubMod pour Minecraft Forge 1.20.1

---

## 📋 TABLE DES MATIÈRES

1. [Vue d'ensemble](#vue-densemble)
2. [Types de comptes](#types-de-comptes)
3. [Parking Lobby](#parking-lobby)
4. [Système de queue](#système-de-queue)
5. [Protection anti-spam](#protection-anti-spam)
6. [Blacklist et tentatives](#blacklist-et-tentatives)
7. [Architecture technique](#architecture-technique)
8. [Flux d'authentification](#flux-dauthentification)

---

## 🎯 VUE D'ENSEMBLE

Le système d'authentification protège les comptes Minecraft avec :
- **Mots de passe hashés** (SHA-256 + salt)
- **Parking lobby** isolé pour joueurs non-authentifiés
- **Système de queue** pour partage de compte (monopole de 45s)
- **Protection 30s** durant fenêtre de monopole
- **Blacklist** progressive (compte + IP)

### Objectifs
1. Empêcher les connexions non autorisées sur comptes protégés
2. Permettre le partage de compte via système de queue sécurisé
3. Protéger contre brute-force avec blacklist temporaire
4. Isoler joueurs non-authentifiés du monde de jeu

---

## 👥 TYPES DE COMPTES

### 1. FREE_PLAYER
- **Accès:** Immédiat au serveur
- **Authentication:** Aucune
- **Restrictions:** Aucune
- **Usage:** Joueurs occasionnels

### 2. PROTECTED_PLAYER
- **Accès:** Après authentification
- **Authentication:** Mot de passe requis
- **Restrictions:** Parking lobby (60s timeout)
- **Usage:** Joueurs réguliers, comptes partagés

### 3. ADMIN
- **Accès:** Après authentification admin
- **Authentication:** Mot de passe + IP blacklist renforcée
- **Restrictions:** Parking lobby (60s timeout)
- **Usage:** Modérateurs, administrateurs

---

## 🅿️ PARKING LOBBY

### Concept
Zone d'attente isolée à `(10000, 200, 10000)` où les joueurs non-authentifiés :
- Sont **invisibles** aux autres joueurs
- Ne peuvent **pas interagir** avec le monde
- Ont **60 secondes** pour s'authentifier (30s si venant de queue)
- Reçoivent instructions d'authentification

### Timeout
```java
- Joueur initial: 60 secondes
- Candidat queue: 30 secondes
```

Après expiration → **kick automatique**

### Caractéristiques
- Position fixe, toujours chargée
- Joueurs téléportés immédiatement à la connexion
- GUI d'authentification envoyé automatiquement
- Messages personnalisés selon type de compte

---

## 🎫 SYSTÈME DE QUEUE

### Principe
Permet à **plusieurs personnes** d'utiliser le **même compte protégé** avec :
- **Fenêtre de monopole**: 45 secondes d'accès exclusif
- **Token unique**: Code à 6 caractères alphanumériques
- **Autorisation IP**: Seule l'IP autorisée peut se connecter
- **Protection 30s**: Temps garanti pour s'authentifier
- **Protection DoS**: Limites strictes pour éviter surcharge serveur

### Workflow

#### 1. Demande de token
```
Joueur A s'authentifie sur Joueur1
↓
Joueur B veut utiliser Joueur1
↓
Joueur B demande token via commande
↓
Token généré : "A3F9K2"
```

#### 2. Attribution monopole
```
Queue position attribuée
↓
Monopole: T+0s → T+45s
↓
IP de Joueur B autorisée
↓
Protection 30s activée pour Joueur B
```

#### 3. Connexion
```
Joueur B se connecte dans les 45s
↓
Protection mise à jour: temps_connexion + 30s
↓
Mixin bloque autres connexions (message: "X secondes restantes")
↓
Joueur B s'authentifie
↓
Joueur A kické (si encore connecté)
```

### Nom temporaire
Les candidats de queue reçoivent un nom temporaire:
```
Format: _Q_<shortName>_<timestamp>
Exemple: _Q_Joueur1_47523
Limite: 16 caractères (limite Minecraft)
```

### Gestion des candidats
- **Maximum**: 1 candidat en attente par compte
- **Expiration**: 5 minutes
- **Kick automatique**: Si auth réussie ou timeout

### Protection DoS (Denial of Service)

Pour éviter la surcharge du serveur par trop de candidats simultanés:

#### Limites par IP
- **4 candidats max** par compte depuis la même IP
- **10 candidats max** au total depuis la même IP (tous comptes confondus)

#### Éviction intelligente
Quand la limite est atteinte:
1. Le système cherche les candidats **≥20 secondes** d'ancienneté
2. Le plus vieux est automatiquement déconnecté (éviction)
3. Le nouveau candidat prend sa place
4. Si tous les candidats sont < 20s, le nouveau est **refusé**

**Messages:**
```
Connexion refusée:
"Limite de tentatives dépassée
Trop de tentatives de connexion depuis votre IP.
Limite par compte: 4 tentatives parallèles
Limite globale: 10 comptes différents

Tous les candidats actuels sont récents (<20s).
Veuillez réessayer plus tard."
```

#### Nettoyage automatique
Le système nettoie les candidats dans **tous les cas**:
- ✅ Timeout (60s sans action)
- ✅ Bon mot de passe (authentification réussie)
- ✅ Mauvais mot de passe (échec authentification)
- ✅ Déconnexion manuelle ou crash

**Comptage précis:** Le nombre de candidats en queue est toujours exact grâce au nettoyage systématique des Maps de tracking (`candidateIPs`, `candidateJoinTime`).

---

## 🛡️ PROTECTION ANTI-SPAM

### Protection de 30 secondes

#### Ancienne version (supprimée)
❌ Protection activée à chaque connexion initiale

#### Version actuelle
✅ Protection activée **uniquement** durant fenêtre de monopole

**Fonctionnement:**
```java
1. Indiv1 s'authentifie → PAS de protection
2. Indiv2 obtient token → monopole 45s
3. Indiv2 se connecte → protection = connexion_time + 30s
4. Indiv3 essaie → BLOQUÉ avec message
5. Indiv2 s'authentifie → protection effacée
```

**Stockage:**
```java
Map<String, Long> authenticationProtectionByAccount
// Clé: nom du compte
// Valeur: timestamp de fin de protection
```

**Vérification:**
- `MixinServerLoginPacketListenerImplPlaceNewPlayer.java`
- Lignes 185-220 (pas de joueur existant)
- Lignes 124-148 (joueur existant non-authentifié)

---

## ⛔ BLACKLIST ET TENTATIVES

### Système de tentatives

#### Pour tous les comptes protégés
- **Maximum:** 3 tentatives
- **Reset:** 24 heures après dernière tentative
- **Stockage:** Persistant (JSON)

### Blacklist compte
```java
Durée: 3 minutes (fixe)
Déclenchement: 3 tentatives échouées
Format JSON: {
  "playerName": {
    "until": timestamp,
    "lastAttempt": timestamp
  }
}
```

### Blacklist IP (Admins seulement)
```java
Durée: Progressive (escalation)
- 1ère fois: 3 minutes
- 2ème fois: 30 minutes
- 3ème fois: 5 heures
- 4ème fois: 50 heures
Format: durée = 3min × 10^(failureCount-1)

Stockage: {
  "ipAddress": {
    "until": timestamp,
    "failureCount": number,
    "lastAttempt": timestamp
  }
}
```

### Normalisation IP
```java
IPv4: Utilisée telle quelle
IPv6: Normalisée (minuscules, compression zéros)
Exemple: 2001:0DB8:0000:0000:0000:0000:1428:57ab
      → 2001:db8::1428:57ab
```

---

## 🏗️ ARCHITECTURE TECHNIQUE

### Classes principales

#### `AuthManager.java`
- Gestion comptes **PROTECTED_PLAYER**
- Hash/salt mots de passe
- Blacklist comptes (3 min)
- Protection 30s durant monopole
- Transition parking lobby → jeu

**Méthodes clés:**
```java
- getAccountType(String playerName)
- attemptProtectedPlayerLogin(ServerPlayer, String password)
- updateAuthenticationProtection(String accountName)
- getRemainingProtectionTime(String accountName)
- handleAuthenticationTransition(UUID playerId)
```

#### `AdminAuthManager.java`
- Gestion comptes **ADMIN**
- Blacklist IP progressive
- Même système protection 30s
- Vérification renforcée

**Méthodes clés:**
```java
- attemptLogin(ServerPlayer, String password)
- isIPBlacklisted(String ipAddress)
- updateAuthenticationProtection(String accountName)
- handleAuthenticationTransition(ServerPlayer)
```

#### `ParkingLobbyManager.java`
- Gestion sessions parking lobby
- Système de queue (tokens, monopoles)
- Timeouts (60s / 30s)
- Autorisation IP
- Noms temporaires
- **Protection DoS** avec limites IP et éviction

**Constantes DoS:**
```java
MAX_CANDIDATES_PER_ACCOUNT_PER_IP = 4     // 4 max par compte/IP
MAX_CANDIDATES_PER_IP_GLOBAL = 10          // 10 max total/IP
CANDIDATE_MIN_AGE_FOR_EVICTION_MS = 20000  // 20s avant éviction
```

**Méthodes clés:**
```java
- addPlayer(ServerPlayer, String accountType)
- addQueueCandidate(String, UUID, String, Server) // Avec protection DoS
- isAuthorized(String accountName, String IP)
- kickRemainingQueueCandidates(...)
- removePlayer(UUID, ServerLevel)  // Nettoyage complet candidats
- evictCandidate(UUID, String, Server, String)  // Éviction automatique
```

#### `CredentialsStore.java`
- Chargement/sauvegarde JSON
- Stockage credentials:
  ```json
  {
    "admins": {...},
    "protected_players": {...},
    "blacklist": {...},
    "account_blacklist": {...},
    "ipBlacklist": {...}
  }
  ```

### Mixins

#### `MixinServerLoginPacketListenerImplPlaceNewPlayer`
**Rôle:** Intercepte connexions AVANT vanilla kick

**Logique:**
1. Vérifie type de compte
2. Détecte joueur existant avec même nom
3. Applique règles:
   - Authentifié → refuse nouvelle connexion
   - Non-authentifié + IP autorisée → permet (+ protection 30s)
   - Non-authentifié + pas d'autorisation → candidat queue OU refus
4. Gère noms temporaires queue
5. Bloque tentatives avec protection active

**Fichier:** `MixinServerLoginPacketListenerImplPlaceNewPlayer.java`

#### `MixinPlayerListServerFull`
**Rôle:** Permet comptes protégés même si serveur plein

**Logique:**
- Laisse passer PROTECTED_PLAYER et ADMIN
- Kick d'un FREE_PLAYER après connexion

#### `MixinPlayerListJoinLeaveMessages`
**Rôle:** Cache messages join/leave pour joueurs non-authentifiés

---

## 🔄 FLUX D'AUTHENTIFICATION

### Scénario 1: Connexion normale (compte protégé)

```mermaid
Joueur → Connexion
    ↓
Mixin détecte PROTECTED_PLAYER
    ↓
Pas de joueur avec ce nom connecté ?
    ↓ Oui
Téléportation parking lobby (10000, 200, 10000)
    ↓
Invisibilité activée
    ↓
GUI authentification envoyé
    ↓
Joueur entre mot de passe
    ↓
Vérification (SHA-256 + salt)
    ↓ Correct
Protection effacée
    ↓
Téléportation monde spawn
    ↓
Visibilité restaurée
    ↓
Authentifié ✓
```

### Scénario 2: Connexion avec queue

```mermaid
Joueur A authentifié sur Compte1
    ↓
Joueur B demande token
    ↓
Token généré: "X7K9P2"
Monopole: T+0 → T+45s
IP de B autorisée
    ↓
Joueur B se connecte (t=10s)
    ↓
Mixin détecte:
- Joueur A existe (non-auth? non, authentifié)
- IP de B autorisée? Oui
    ↓
Protection: t=10s + 30s = t=40s
    ↓
Nom temporaire: _Q_Compte1_12345
    ↓
Parking lobby
    ↓
Joueur B s'authentifie
    ↓
Vanilla kick Joueur A
Joueur B → Compte1
UUID migration
Restauration position
    ↓
Authentifié ✓
```

### Scénario 3: Tentative pendant protection

```mermaid
Protection active (Joueur B: t=40s)
    ↓
Joueur C essaie se connecter (t=15s)
    ↓
Mixin vérifie protection:
Reste: 40s - 15s = 25s
    ↓
IP de C autorisée? Non
    ↓
BLOCAGE
Message: "Compte en cours d'utilisation
Temps restant: 25 seconde(s)"
    ↓
Connexion refusée ✗
```

---

## 📝 COMMANDES

### Gestion des comptes
```
/auth add <player> <password>           - Ajoute joueur protégé
/auth remove <player>                   - Retire protection
/auth setpassword <player> <password>   - Change mot de passe
/auth list                              - Liste joueurs protégés
```

### Système admin
```
/admin add <player> <password>          - Ajoute admin
/admin remove <player>                  - Retire admin
/admin resetblacklist <player>          - Reset blacklist compte
/admin resetip <ip>                     - Reset blacklist IP
```

### Queue (joueur)
```
/queue request <account>                - Demande token
/queue verify <token>                   - Vérifie token avant connexion
```

---

## 🔒 SÉCURITÉ

### Hashing
- **Algorithme:** SHA-256
- **Salt:** 16 bytes aléatoires (SecureRandom)
- **Stockage:** Base64 (hash + salt)

### Tokens
- **Génération:** Random (6 alphanumériques)
- **Unicité:** Par compte + timestamp
- **Expiration:** Avec monopole (45s) ou 5min en queue

### Protection réseau
- **Normalisation IP** pour éviter contournement
- **Blacklist progressive** sur IP
- **Rate limiting** via tentatives max

---

## 🐛 BUGS CORRIGÉS

Voir `BUGS_CORRIGES.md` pour détails complets.

### BUG #1: Protection 30s jamais vérifiée
✅ **CORRIGÉ** - Protection active durant monopole uniquement

### BUG #2: Inventaire joueur mort donné
✅ **CORRIGÉ** - Flag `isDead` empêche restauration inventaire

### BUG #3: Race condition UUID migration
✅ **CORRIGÉ** - Synchronisation avec `reconnectionLock`

---

## 📚 RÉFÉRENCES

- **Credentials:** `credentials.json` (gitignored)
- **Packets:** `AdminAuthPacket.java`, `QueueToken*.java`
- **Utils:** `PlayerFilterUtil.java`, `IPNormalizer.java`
- **Config:** `mysubmod.mixins.json`

---

**Dernière mise à jour:** 2025-10-21
**Auteur:** Claude Code
**Contact:** Voir GitHub pour issues/PR

---

## 📈 CHANGELOG

### v1.1.0 (2025-10-21)
- ✨ **Protection DoS**: Limites de 4 candidats/compte/IP et 10 candidats/IP global
- ✨ **Éviction intelligente**: Candidats ≥20s automatiquement remplacés
- 🐛 **Fix nettoyage**: Tracking précis des candidats dans tous les scénarios de déconnexion
- 📊 **Comptage fiable**: Maps candidateIPs et candidateJoinTime nettoyées systématiquement

### v1.0.0 (2025-10-09)
- 🎉 Version initiale du système d'authentification
- 🔐 Authentification par mot de passe (SHA-256 + salt)
- 🎫 Système de queue avec monopoles de 45s
- 🛡️ Protection 30s durant fenêtre de monopole
- ⛔ Blacklist compte (3 min) et IP progressive (admins)
