# Guide du Sous-mode 1 - Survie sur Îles

## Vue d'ensemble

Le sous-mode 1 est un mode de survie de 15 minutes où les joueurs doivent survivre sur des îles en collectant et consommant des bonbons pour maintenir leur santé. Le système gère automatiquement les déconnexions/reconnexions et offre un contrôle précis du spawn des bonbons via fichiers de configuration.

## Fonctionnalités principales

### 🏝️ **Système d'îles carrées**
- **4 îles carrées générées automatiquement** autour d'un carré central (20x20) :
  - **Petite île** (60x60 blocs)
  - **Île moyenne** (90x90 blocs)
  - **Grande île** (120x120 blocs)
  - **Très grande île** (150x150 blocs)
- **Carré central de spawn** (20x20) : Point de départ où tous les joueurs apparaissent
- **Distance** : 360 blocs entre le centre et chaque île
- **Barrières invisibles** : Empêchent la chute dans l'eau avec ouvertures pour les chemins
- **Chemins de pierre** : Relient chaque île au carré central (360 blocs de long)
- **Indicateurs directionnels** : Hologrammes flottants colorés au-dessus des tours de laine au carré central
- **Décoration naturelle** : Arbres, fleurs, herbe haute (pissenlits bloqués sur les îles et chemins)
- **Génération procédurale** avec variations de terrain et bordures naturelles

### 🎯 **Phase de sélection (30 secondes)**
- **Téléportation au carré central** pour tous les joueurs non-admin
- **Interface automatique** s'ouvre pour choisir l'île
- **4 options d'îles** avec dimensions affichées
- **Attribution automatique** si pas de sélection dans les 30 secondes
- **Téléportation simultanée** vers les îles choisies
- **Logging automatique** du choix d'île de chaque joueur

### 🎯 **Système de spawn par coordonnées exactes**
- **Coordonnées précises** : Spawn au bloc exact spécifié (plus de spawn points aléatoires)
- **Configuration par fichier** :
  - Format : `temps,quantité,x,y,z`
  - Exemple : `60,5,0,101,-360` (5 bonbons à 60s au centre de l'île SMALL)
  - Validation automatique du format et des valeurs
- **Dispersion naturelle** : Les bonbons sont dispersés dans un rayon de 3 blocs autour de la position

### 📁 **Gestion des fichiers de configuration**
- **Interface moderne** avec liste défilante et sélection par clic
- **Sélection manuelle** : Touche **N** pour ouvrir le menu de sélection de fichier
- **Upload de fichiers** personnalisés via interface graphique (bouton 📁 dans le menu admin)
- **Lancement automatique** : Sélectionner un fichier démarre la phase de sélection d'îles
- **Protection en partie** : Impossible de sélectionner un fichier quand une partie est en cours
- **Validation stricte** :
  - Format à 5 champs obligatoire : `temps,quantité,x,y,z`
  - Temps entre 0-900 secondes
  - Quantité entre 1-100 bonbons
  - Y (hauteur) entre 100-120
  - X et Z doivent être sur une des 4 îles (validation carrée)
- **Suppression sélective** des fichiers personnalisés (default.txt protégé)
- **Actualisation** via bouton pour rafraîchir la liste
- **Fichier par défaut** : `default.txt` toujours disponible
- **Accès upload** : Bouton 📁 dans l'interface admin (touche M)
- **Accès sélection** : Touche N (requête serveur automatique pour liste fraîche)

### ⏱️ **Timer de jeu (15 minutes)**
- **Affichage non-invasif** en haut à droite de l'écran
- **Alertes temporelles** :
  - 5 minutes restantes
  - 2 minutes restantes
  - 1 minute restante
  - Compte à rebours final (30s, 10s, 9s...1s)
- **Couleurs d'alerte** : Jaune → Orange → Rouge

### 📊 **HUD des ressources en temps réel**
- **Position** : Coin supérieur droit (non-invasif)
- **Affichage** : Nombre de bonbons disponibles par île
- **Couleurs** :
  - Petite : Blanc
  - Moyenne : Vert
  - Grande : Bleu
  - Très Grande : Orange
- **Mise à jour** : Toutes les 2 secondes
- **Désactivation automatique** : À la fin de la partie

### 💔 **Système de santé**
- **Santé initiale** : 100% santé, 50% faim au démarrage
- **Dégradation automatique** : -0.5 cœur toutes les 10 secondes (UNIQUEMENT pendant la partie, pas pendant la sélection des îles)
- **Alerte santé critique** quand ≤ 1 cœur restant
- **Mort automatique** à 0 cœur → téléportation vers zone spectateur
- **Fin de partie automatique** si tous les joueurs meurent

### 🍬 **Système de bonbons**
- **Seul moyen de récupérer de la santé** (+2 cœurs par bonbon)
- **Spawn selon configuration** : Défini par le fichier de configuration sélectionné (touche N)
- **Spawn par coordonnées** : Les bonbons apparaissent aux coordonnées exactes spécifiées dans le fichier
- **Dispersion naturelle** : Dispersés dans un rayon de 3 blocs autour de la position pour éviter superposition
- **Persistance** : Les bonbons restent jusqu'à collecte (pas d'expiration)
- **Seul objet autorisé** dans l'inventaire
- **Visibilité améliorée** : Effet lumineux (glowing) pour les voir de loin
- **Tracking en temps réel** : HUD affiche le nombre de bonbons disponibles par île

#### Propriétés des bonbons :
- **Récupération** : +2 cœurs de santé
- **Partageables** : Peuvent être jetés et ramassés par d'autres
- **Destructibles** : Peuvent être détruits
- **Effet lumineux** : Brillent avec un contour visible à travers les blocs
- **Tracking** : Associés à leur île pour le HUD des ressources

### 👥 **Gestion des joueurs**

#### **Joueurs vivants**
- Téléportés sur l'île choisie (ou assignation aléatoire si pas de sélection)
- Commencent avec 100% santé et 50% faim
- Subissent la dégradation de santé (uniquement après la phase de sélection)
- Peuvent collecter et consommer des bonbons
- Peuvent se déplacer entre les îles via les chemins
- Suivis par le système de logging

#### **Gestion des déconnexions/reconnexions**
- **Pendant phase de sélection** : Le joueur est réintégré au carré central pour sélectionner son île
- **Pendant la partie (joueur vivant)** :
  - Pénalité de santé : -4 cœurs (2 points de vie)
  - Téléportation sur une île aléatoire parmi les 4 îles
  - Inventaire préservé : Les bonbons possédés sont conservés
  - État de santé sauvegardé : La santé est restaurée (moins la pénalité)
- **Après la mort** : Le joueur reste en mode spectateur (zone spectateur)
- **Tracking UUID** : Utilisation des UUID pour identifier les joueurs à travers les reconnexions
- **Logging automatique** : Déconnexions et reconnexions enregistrées dans les logs

#### **Joueurs morts**
- Téléportés vers la plateforme spectateur
- Santé restaurée à 100%
- Soumis aux mêmes restrictions que la salle d'attente
- Annonce publique de mort

#### **Administrateurs**
- Téléportés automatiquement vers la plateforme spectateur
- Peuvent observer le jeu
- Gardent leurs privilèges pour changer de mode
- Peuvent sélectionner le fichier de configuration

### 🚫 **Restrictions strictes**
Les joueurs vivants **NE PEUVENT PAS** :
- ❌ Attaquer d'autres joueurs ou entités
- ❌ Interagir avec des blocs (clic droit)
- ❌ Casser des blocs
- ❌ Placer des blocs
- ❌ Fabriquer des objets (crafting)
- ❌ Ramasser d'autres objets que les bonbons
- ❌ Sprinter (vitesse de sprint = vitesse normale via attribut modifier)
- ❌ Jeter des bonbons (possibilité désactivée)

**Protection de l'environnement** :
- 🚫 Tous les items au sol (sauf bonbons du système avec glowingTag) bloqués sur îles et chemins
- 🚫 Les mobs hostiles ne peuvent pas spawner près des îles (détection carrée correspondant aux îles)
- 🚫 Barrières invisibles empêchent la chute dans l'eau (avec ouvertures pour les chemins)
- 🚫 Protection contre le spawn de pissenlits (ItemEntity) via EntityJoinLevelEvent
- 🚫 Distance de rendu des entités augmentée à 150% pour meilleure visibilité (server.properties)
- ☀️ Cycle jour/nuit bloqué : Toujours jour pendant TOUT le sous-mode (pas seulement pendant le jeu)

### 📊 **Système de logging et gestion**
Toutes les actions sont enregistrées dans `mysubmod_data/submode1_game_[timestamp]/` :

#### **Logs par joueur** (`[nom_joueur]_log.txt`) :
- **Sélection d'île** : Île choisie (manuelle ou automatique) au début
- **Positions** : Enregistrées toutes les 5 secondes avec timestamp milliseconde
- **Consommation de bonbons** : Moment, position et santé après consommation
- **Ramassage de bonbons** : Position exacte du bonbon et du joueur
- **Changements de santé** : Ancienne → nouvelle valeur avec position
- **Mort** : Position et moment exact
- **Déconnexions/Reconnexions** : Horodatage, état du joueur, pénalités appliquées
- **Téléportations** : Anciennes et nouvelles positions (sélection, reconnexion)

#### **Logs globaux** (`game_events.txt`) :
- Début/fin de partie avec timestamps
- Spawn de bonbons (coordonnées exactes x,y,z du fichier de configuration)
- Événements système (activation, désactivation)
- Fichier de configuration sélectionné
- Statistiques de fin de partie

#### **Gestion des logs** (Interface 📊)
- **Téléchargement sélectif** : Télécharger une session spécifique en ZIP
- **Téléchargement en masse** : Tous les logs en un seul fichier ZIP
- **Suppression sélective** : Supprimer une session spécifique
- **Suppression en masse** : Nettoyer tous les logs
- **Liste défilante** : Interface moderne avec sélection par clic
- **Destination** : Dossier Downloads de Windows
- **Accès** : Bouton 📊 dans l'interface admin (touche M)
- **Sécurité** : Accès admin uniquement via validation de packets réseau

### 🎉 **Fin de partie**
- **Conditions** :
  - Timer de 15 minutes écoulé, OU
  - Tous les joueurs sont morts
- **Message de félicitations** affiché à tous
- **Retour automatique** vers la salle d'attente après 5 secondes
- **Sauvegarde** de toutes les données de la session
- **Nettoyage complet** de la carte (îles, chemins, barrières)
- **Désactivation du HUD** des ressources

## Commandes administratives

```bash
# Lancer le sous-mode 1
/submode set 1

# Vérifier le mode actuel
/submode current

# Retourner à la salle d'attente
/submode set waiting
```

## Architecture technique

### **Classes principales**
- `SubMode1Manager` : Gestion principale du mode (4 îles, carré central, hologrammes)
- `IslandGenerator` : Génération procédurale des îles carrées
- `SubMode1HealthManager` : Système de dégradation de santé (uniquement pendant partie active)
- `SubMode1CandyManager` : Gestion des bonbons (spawn par coordonnées exactes)
- `SubMode1DataLogger` : Système de logging complet avec timestamps milliseconde
- `SubMode1EventHandler` : Gestion des événements (restrictions, protection environnement)
- `GameTimer` : Gestion du timer de 15 minutes côté serveur
- `CandySpawnFileManager` : Gestion et validation des fichiers (format 5 paramètres)
- `LogManager` : Compression ZIP et gestion des logs côté serveur
- `WaitingRoomManager` : Gestion de la salle d'attente (fermeture menus sur désactivation)

### **Interface utilisateur**
- `IslandSelectionScreen` : Interface de sélection d'île (4 options avec dimensions)
- `CandyFileSelectionScreen` : Interface moderne avec liste défilante (touche N)
- `CandyFileUploadScreen` : Interface d'upload de fichiers avec validation
- `LogManagementScreen` : Interface de gestion des logs avec liste défilante
- `SubModeControlScreen` : Interface admin principale (touche M)
- `SubMode1HUD` : Affichage du timer en jeu (coin supérieur droit)
- `CandyCountHUD` : Affichage des ressources disponibles par île avec couleurs
- `CandyCountHUDRenderer` : Rendu du HUD des bonbons
- `ClientGameTimer` : Gestion côté client du timer avec alertes
- `ClientEventHandler` : Gestion des touches M et N

### **Réseau**
- `IslandSelectionPacket` : Ouverture de l'interface de sélection
- `IslandChoicePacket` : Envoi du choix d'île au serveur
- `GameTimerPacket` : Synchronisation du timer serveur→client
- `CandyFileListPacket` : Liste des fichiers disponibles (avec paramètre openScreen)
- `CandyFileListRequestPacket` : Demande de rafraîchissement de la liste (touche N)
- `CandyFileSelectionPacket` : Sélection du fichier + lancement partie (validation état)
- `CandyFileUploadPacket` : Upload de nouveaux fichiers avec validation complète
- `CandyFileDeletePacket` : Suppression de fichiers (default.txt protégé)
- `CandyCountUpdatePacket` : Mise à jour du HUD des ressources (toutes les 2s)
- `LogListRequestPacket` : Demande de liste des logs
- `LogListPacket` : Liste des logs disponibles
- `LogDownloadPacket` : Téléchargement de logs en ZIP
- `LogDeletePacket` : Suppression de logs sélective/masse
- `ClientPacketHandler` : Gestion client des packets (file list, logs, screens)
- `LogPacketHandler` : Gestion client spécifique aux logs

## Données collectées

Le système collecte des données précieuses pour l'analyse comportementale :
- **Patterns de mouvement** des joueurs (enregistrés toutes les 5 secondes)
- **Choix d'îles** au début de la partie (manuel ou automatique)
- **Stratégies de collecte** de bonbons (par coordonnées exactes)
- **Gestion des ressources** (timing de consommation, santé avant/après)
- **Zones de survie préférées** sur chaque île
- **Durée de survie** par joueur avec timestamps précis
- **Distribution des bonbons** par coordonnées x,y,z
- **Comportement en déconnexion** : Fréquence, timing, impact sur performance
- **Déplacements inter-îles** : Utilisation des chemins, timing des migrations

## Configuration serveur recommandée

Pour une expérience optimale, les paramètres suivants sont recommandés dans `server.properties` :

```properties
# Monde vide par défaut (les îles sont générées par le mod)
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:air","height"\:1}],"biome"\:"minecraft\:plains"}

# Visibilité améliorée des bonbons à distance
entity-broadcast-range-percentage=300

# Permettre le vol pour les admins en mode spectateur
allow-flight=true
```

Cette implémentation complète offre une expérience de jeu équilibrée, hautement configurable et entièrement trackée pour l'analyse de données comportementales. Le système gère automatiquement les déconnexions/reconnexions, applique des restrictions strictes pour garantir l'équité, et collecte des données détaillées pour l'analyse post-partie.
