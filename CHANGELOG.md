# Changelog - MySubMod

## Version actuelle (Octobre 2025)

### 🎨 Dernières améliorations (2 octobre 2025 - Session finale)

#### Système de gestion des logs
- **Interface 📊** : Nouvelle interface graphique accessible depuis l'écran admin
- **Liste défilante** : Sélection moderne avec clic sur les dossiers de logs
- **Téléchargement sélectif** : Compression ZIP et sauvegarde dans Downloads Windows
- **Téléchargement en masse** : Option pour télécharger tous les logs en un fichier
- **Suppression sélective** : Supprimer des sessions individuelles
- **Suppression en masse** : Nettoyer tous les logs d'un coup
- **Bouton d'actualisation** : Rafraîchir la liste des logs
- **Sécurité** : Accès admin uniquement via packets réseau

#### Interface de fichiers modernisée
- **Liste défilante** : Remplacement du CycleButton par ObjectSelectionList
- **Sélection par clic** : Interface plus intuitive
- **Fond de sélection** : Highlight au survol des éléments
- **Icônes** : 📄 pour default.txt, 📁 pour les autres
- **Bouton d'actualisation** : Rafraîchir la liste des fichiers
- **Nouveau packet** : CandyFileListRequestPacket pour les requêtes de liste

#### Protection renforcée
- **Blocage items étendu** : Tous les ItemEntity bloqués sur îles/chemins (pas seulement pissenlits)
- **Exception bonbons** : Seuls les bonbons du système (avec glowingTag) sont autorisés
- **Sprint désactivé** : Modificateur d'attribut pour réduire vitesse sprint = vitesse normale
- **Correction HUD** : Les HUD et timer ne persistent plus après déconnexion/reconnexion

#### Monde vide par défaut
- **Configuration serveur** : `level-type=minecraft:flat` avec layers vides
- **Generator settings** : `{"layers":[],"biome":"minecraft:plains"}`
- **Visibilité étendue** : `entity-broadcast-range-percentage=300` pour voir les bonbons de loin

#### Nettoyage du code
- **Code mort supprimé** :
  - Méthode `removeDroppedFlowers()` jamais appelée
  - Variables `flowerCleanupTicks` et `FLOWER_CLEANUP_INTERVAL` inutilisées
- **Documentation mise à jour** : Tous les fichiers .md reflètent l'état actuel

### 🎨 Améliorations visuelles et UX (Session précédente)

#### Système d'hologrammes pour indicateurs directionnels
- **Remplacement des panneaux** : Hologrammes flottants au lieu de panneaux (texte plus stable)
- **Position** : Au-dessus des tours de laine colorées au carré central
- **Format du texte** : Espacement entre les lettres pour une meilleure lisibilité (ex: "P E T I T E  Î L E")
- **Couleurs** :
  - Blanc (Petite) + texte taille gris
  - Vert (Moyenne) + texte taille gris
  - Bleu (Grande) + texte taille gris
  - Orange (Très Grande) + texte taille gris
- **Suppression du code obsolète** :
  - Méthodes de gestion des panneaux (`placeSignOnWool`, `placeSignWithText`, `getRotationFromDirection`)
  - Méthode `createPathHolograms` (indicateurs sur chemins retirés)
  - Protection des panneaux dans les event handlers
  - Renommage `removeSignItems` → `removeHolograms` pour clarté

#### Protection contre les pissenlits
- **Prévention du spawn** : Les pissenlits (dandelions) ne peuvent plus apparaître comme ItemEntity sur les îles et chemins
- **Event handler** : Utilisation de `EntityJoinLevelEvent` pour bloquer avant l'apparition
- **Zone de protection** : Détection via `isNearIslandOrPath()` (îles + chemins)
- **Cleanup supprimé** : Plus besoin de nettoyer après coup, le spawn est bloqué à la source

#### Correction de la dégradation de santé
- **Timing corrigé** : La perte de vie ne s'active PLUS pendant la sélection des îles (30 secondes)
- **Activation** : Uniquement quand `gameActive == true` (après téléportation vers les îles)
- **Vérification** : Ajout de `if (!SubMode1Manager.getInstance().isGameActive())` dans le timer

#### Message de chargement
- **Notification** : Message "§e§lChargement du sous-mode 1..." affiché à tous les joueurs au démarrage
- **Timing** : Affiché dès le début de `activate()` avant toute génération

### 🎮 Changements majeurs du gameplay

#### Extension du système d'îles (4 îles au lieu de 3)
- **Nouvelle disposition** : 4 îles carrées autour d'un carré central (20x20)
- **Carré central** : Point de spawn initial pour tous les joueurs
- **Nouvelles tailles** :
  - Petite île (60x60) : 1 spawn point
  - Île moyenne (90x90) : 2 spawn points
  - Grande île (120x120) : 3 spawn points
  - **Très grande île (150x150)** : 4 spawn points (NOUVEAU)
- **Distance** : 360 blocs entre le centre et chaque île
- **Chemins** : 4 chemins de 360 blocs reliant chaque île au carré central

#### Système de spawn points aléatoires
- **Génération dynamique** : Nouveaux spawn points générés à chaque partie
- **Contraintes** : Minimum 40 blocs entre chaque spawn point
- **Configuration flexible** : Format de fichier à 4 champs (`temps,quantité,île,spawn_point`)
- **Validation stricte** : Vérification du numéro de spawn point selon l'île

#### Gestion des fichiers de configuration
- **Interfaces graphiques** :
  - Sélection de fichier avant chaque partie (admins)
  - Upload de fichiers personnalisés
  - Suppression de fichiers (default.txt protégé)
- **Validation automatique** :
  - Format à 4 champs obligatoire
  - Temps 0-900 secondes
  - Quantité 1-50 bonbons
  - Île valide (SMALL, MEDIUM, LARGE, EXTRA_LARGE)
  - Spawn point valide (1-4 selon l'île)

#### HUD des ressources en temps réel
- **Affichage non-invasif** : Coin supérieur droit
- **Comptage par île** : Nombre de bonbons disponibles
- **Mise à jour automatique** : Toutes les 2 secondes
- **Couleurs distinctives** :
  - Petite : Blanc
  - Moyenne : Vert
  - Grande : Bleu
  - Très Grande : Orange
- **Désactivation automatique** : À la fin de la partie

#### Fin de partie automatique
- **Double condition** :
  - Timer de 15 minutes écoulé, OU
  - Tous les joueurs sont morts
- **Message approprié** selon la condition
- **Nettoyage complet** : Carré central, 4 îles, chemins, barrières

### 📊 Améliorations du système de logging

#### Nouvelles données enregistrées
- **Choix d'île** : Logging du choix (manuel ou automatique) de chaque joueur
- **Spawn de bonbons** : Position, île ET numéro de spawn point
- **Format amélioré** : Timestamps au milliseconde pour toutes les actions

### 🔧 Améliorations techniques

#### Téléportation sécurisée
- **Chargement de chunks** : Forcer le chargement avant téléportation
- **Évite les déconnexions** : Particulièrement pour les distances de 360 blocs
- **Méthode `safeTeleport` améliorée** :
  - `getChunkAt()` pour charger le chunk
  - `moveTo()` puis `teleportTo()` pour positionnement double
  - Logging de debug pour le suivi

#### Nettoyage du code
- **Suppression des variables inutilisées** :
  - `CANDIES_PER_PLAYER` (obsolète avec fichiers de config)
  - `LARGE_ISLAND_RATIO`, `MEDIUM_ISLAND_RATIO`, `SMALL_ISLAND_RATIO` (obsolètes)
  - `totalCandiesTarget`, `largeCandiesTarget`, etc. (jamais utilisées)
  - Méthode `getSpawnedCount()` (jamais appelée)

#### Tracking des ressources
- **Map `candyIslands`** : Association bonbon ↔ île
- **Méthode `getAvailableCandiesPerIsland()`** : Comptage en temps réel
- **Nettoyage automatique** : À la collecte et fin de partie

### 🎨 Interface utilisateur

#### Écran de sélection d'île
- **4 options** au lieu de 3
- **Affichage des dimensions** dans les noms d'îles
- **Utilisation de `getDisplayName()`** pour cohérence

#### Écrans de gestion de fichiers
- **Sélection** : Liste déroulante avec boutons
- **Upload** : Nom + contenu avec validation
- **Suppression** : Bouton actif seulement pour fichiers personnalisés
- **Interface simplifiée** : Textes redondants supprimés

### 🐛 Corrections de bugs

#### Barrières et chemins
- **Carré central** : Barrières avec ouvertures pour les 4 chemins
- **Connexions** : Méthode `isPathConnectionPoint()` mise à jour pour 4 îles
- **Chemins radiaux** : Connexion propre entre centre et îles

#### Synchronisation réseau
- **Nouveau packet** : `CandyCountUpdatePacket` pour le HUD
- **Enregistrement** : Ajouté au `NetworkHandler`
- **Timing** : Envoi toutes les 2 secondes pendant les parties

#### Validation de fichiers
- **Upload** : Vérification complète avant sauvegarde
- **Rejet** : Fichier entier rejeté si une ligne est invalide
- **Logs clairs** : Messages d'erreur explicites

### 📦 Nouveaux fichiers créés

#### Classes de gestion
- `SpawnPointManager.java` : Génération et gestion des spawn points
- `CandyCountUpdatePacket.java` : Synchronisation HUD serveur→client
- `CandyCountHUD.java` : Affichage côté client des ressources
- `CandyCountHUDRenderer.java` : Event handler pour le rendu

#### Écrans d'interface
- `CandyFileSelectionScreen.java` : Sélection/suppression de fichiers
- `CandyFileUploadScreen.java` : Upload de nouveaux fichiers

#### Packets réseau
- `CandyFileListPacket.java` : Liste des fichiers disponibles
- `CandyFileSelectionPacket.java` : Sélection du fichier de config
- `CandyFileUploadPacket.java` : Upload de fichier
- `CandyFileDeletePacket.java` : Suppression de fichier

#### Data
- `CandySpawnEntry.java` : Ajout du champ `spawnPointNumber`
- `CandySpawnFileManager.java` : Parsing et validation du 4ème champ

### 📦 Nouveaux fichiers (Session finale)

#### Gestion des logs
- `LogManager.java` : Gestionnaire serveur pour ZIP et suppression
- `LogManagementScreen.java` : Interface client avec liste défilante
- `LogPacketHandler.java` : Gestion client des packets de logs
- `LogListRequestPacket.java` : Demande de liste des logs
- `LogListPacket.java` : Envoi de la liste au client
- `LogDownloadPacket.java` : Téléchargement de logs
- `LogDeletePacket.java` : Suppression de logs

#### Gestion des fichiers candy
- `CandyFileListRequestPacket.java` : Demande de rafraîchissement de liste

### 🎯 Prochaines améliorations prévues
- Texture personnalisée pour l'item bonbon (modèle JSON créé)
- Implémentation du Sous-mode 2
- Interface d'administration avancée
- Outils d'analyse des données collectées
- Système de replay des parties

---

*Dernière mise à jour : 2 octobre 2025*

---

## Versions précédentes

### Changements initiaux (Septembre 2025)

#### Îles carrées au lieu de circulaires
- **Forme** : Conversion de toutes les îles de circulaires à carrées
- **Tailles originales** : Petite (15x15), Moyenne (25x25), Grande (35x35)
- **Barrières** : Système de barrières invisibles avec ouvertures pour les chemins
- **Chemins** : Chemins en pierre reliant les îles entre elles

#### Système de santé ajusté
- **Santé initiale** : 100% santé au début de partie
- **Faim initiale** : 50% faim au lieu de 100%
- **Dégradation** : -0.5 cœur toutes les 10 secondes

#### Amélioration des bonbons
- **Expiration** : Supprimée (persistance jusqu'à collecte)
- **Visibilité** : Effet lumineux et glowing
- **Distribution** : Selon fichier de configuration

#### Système de logging
- **Structure** : Organisation dans `mysubmod_data/submode1_game_[timestamp]/`
- **Données** : Positions, actions, événements détaillés
