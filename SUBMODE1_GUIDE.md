# Guide du Sous-mode 1 - Survie sur Îles

## Vue d'ensemble

Le sous-mode 1 est un mode de survie de 15 minutes où les joueurs doivent survivre sur des îles en collectant et consommant des bonbons pour maintenir leur santé.

## Fonctionnalités principales

### 🏝️ **Système d'îles carrées**
- **4 îles carrées générées automatiquement** autour d'un carré central (20x20) :
  - **Petite île** (60x60 blocs) : 1 spawn point
  - **Île moyenne** (90x90 blocs) : 2 spawn points
  - **Grande île** (120x120 blocs) : 3 spawn points
  - **Très grande île** (150x150 blocs) : 4 spawn points
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

### 🎯 **Système de spawn points aléatoires**
- **Génération à chaque partie** : Nouveaux spawn points aléatoires
- **Distance minimale** : 40 blocs entre chaque spawn point
- **Configuration par fichier** :
  - Format : `temps,quantité,île,spawn_point`
  - Exemple : `60,5,EXTRA_LARGE,3` (5 bonbons à 60s sur la très grande île au spawn point 3)
  - Validation automatique du format et des valeurs

### 📁 **Gestion des fichiers de configuration**
- **Interface moderne** avec liste défilante et sélection par clic
- **Upload de fichiers** personnalisés via interface graphique
- **Validation stricte** :
  - Format à 4 champs obligatoire
  - Temps entre 0-900 secondes
  - Quantité entre 1-50 bonbons
  - Île valide (SMALL, MEDIUM, LARGE, EXTRA_LARGE)
  - Spawn point valide selon l'île (1-4)
- **Suppression sélective** des fichiers personnalisés (default.txt protégé)
- **Actualisation** via bouton pour rafraîchir la liste
- **Fichier par défaut** : `default.txt` toujours disponible
- **Accès** : Bouton 📁 dans l'interface admin (touche M)

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
- **Spawn selon configuration** : Défini par le fichier de configuration sélectionné
- **Spawn aux points désignés** : Les bonbons apparaissent uniquement aux spawn points aléatoires
- **Persistance** : Les bonbons restent jusqu'à collecte (pas d'expiration)
- **Seul objet autorisé** dans l'inventaire
- **Visibilité améliorée** : Effet lumineux (glowing) pour les voir de loin

#### Propriétés des bonbons :
- **Récupération** : +2 cœurs de santé
- **Partageables** : Peuvent être jetés et ramassés par d'autres
- **Destructibles** : Peuvent être détruits
- **Effet lumineux** : Brillent avec un contour visible à travers les blocs
- **Tracking** : Associés à leur île pour le HUD des ressources

### 👥 **Gestion des joueurs**

#### **Joueurs vivants**
- Téléportés sur l'île choisie
- Commencent avec 100% santé et 50% faim
- Subissent la dégradation de santé
- Peuvent collecter et consommer des bonbons
- Peuvent se déplacer entre les îles via les chemins
- Suivis par le système de logging

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
- ❌ Casser des blocs (sauf admins peuvent casser panneaux)
- ❌ Placer des blocs
- ❌ Fabriquer des objets
- ❌ Ramasser d'autres objets que les bonbons
- ❌ Sprinter (vitesse de sprint = vitesse normale)

**Protection supplémentaire** :
- 🚫 Tous les items au sol (sauf bonbons du système) bloqués sur îles et chemins
- 🚫 Les mobs hostiles ne peuvent pas spawner près des îles
- 🚫 Les joueurs ne peuvent pas jeter de bonbons
- 🚫 Distance de rendu des entités augmentée à 300% pour meilleure visibilité

### 📊 **Système de logging et gestion**
Toutes les actions sont enregistrées dans `mysubmod_data/submode1_game_[timestamp]/` :

#### **Logs par joueur** (`[nom_joueur]_log.txt`) :
- **Sélection d'île** : Île choisie (manuelle ou automatique) au début
- **Positions** : Enregistrées toutes les 5 secondes
- **Consommation de bonbons** : Moment, position et santé après consommation
- **Ramassage de bonbons** : Position du bonbon et du joueur
- **Changements de santé** : Ancienne → nouvelle valeur avec position
- **Mort** : Position et moment

#### **Logs globaux** (`game_events.txt`) :
- Début/fin de partie
- Spawn de bonbons (position, île et spawn point)
- Événements système

#### **Gestion des logs** (Interface 📊)
- **Téléchargement sélectif** : Télécharger une session spécifique en ZIP
- **Téléchargement en masse** : Tous les logs en un seul fichier ZIP
- **Suppression sélective** : Supprimer une session spécifique
- **Suppression en masse** : Nettoyer tous les logs
- **Liste défilante** : Interface moderne avec sélection par clic
- **Destination** : Dossier Downloads de Windows
- **Accès** : Bouton 📊 dans l'interface admin (touche M)

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
- `SubMode1Manager` : Gestion principale du mode (4 îles, carré central)
- `IslandGenerator` : Génération procédurale des îles
- `SpawnPointManager` : Génération et gestion des spawn points aléatoires
- `SubMode1HealthManager` : Système de dégradation de santé
- `SubMode1CandyManager` : Gestion des bonbons (spawn selon configuration)
- `SubMode1DataLogger` : Système de logging complet
- `GameTimer` : Gestion du timer de 15 minutes
- `CandySpawnFileManager` : Gestion et validation des fichiers de configuration

### **Interface utilisateur**
- `IslandSelectionScreen` : Interface de sélection d'île (4 options)
- `CandyFileSelectionScreen` : Interface moderne avec liste défilante pour fichiers
- `CandyFileUploadScreen` : Interface d'upload de fichiers
- `LogManagementScreen` : Interface de gestion des logs avec liste défilante
- `SubMode1HUD` : Affichage du timer en jeu
- `CandyCountHUD` : Affichage des ressources disponibles par île
- `ClientGameTimer` : Gestion côté client du timer

### **Réseau**
- `IslandSelectionPacket` : Ouverture de l'interface de sélection
- `IslandChoicePacket` : Envoi du choix d'île au serveur
- `GameTimerPacket` : Synchronisation du timer
- `CandyFileListPacket` : Liste des fichiers disponibles
- `CandyFileListRequestPacket` : Demande de rafraîchissement de la liste
- `CandyFileSelectionPacket` : Sélection du fichier de configuration
- `CandyFileUploadPacket` : Upload de nouveaux fichiers
- `CandyFileDeletePacket` : Suppression de fichiers
- `CandyCountUpdatePacket` : Mise à jour du HUD des ressources
- `LogListRequestPacket` : Demande de liste des logs
- `LogListPacket` : Liste des logs disponibles
- `LogDownloadPacket` : Téléchargement de logs
- `LogDeletePacket` : Suppression de logs

## Données collectées

Le système collecte des données précieuses pour l'analyse comportementale :
- **Patterns de mouvement** des joueurs
- **Choix d'îles** au début de la partie
- **Stratégies de collecte** de bonbons (par spawn point)
- **Gestion des ressources** (timing de consommation)
- **Zones de survie préférées** sur chaque île
- **Durée de survie** par joueur
- **Distribution des bonbons** par île et spawn point

Cette implémentation complète offre une expérience de jeu équilibrée, hautement configurable et entièrement trackée pour l'analyse de données comportementales.
